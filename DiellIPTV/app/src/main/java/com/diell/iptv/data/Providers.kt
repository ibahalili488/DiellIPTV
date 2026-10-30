package com.diell.iptv.data

import android.content.Context
import com.diell.iptv.model.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.text.DateFormat
import java.util.Date

/** An error with a message that can be shown to the user as-is. */
class ProviderException(message: String) : IOException(message)

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** Plain HTTP GET with redirects across http/https. */
object Http {
    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("Broken redirect")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                when (code) {
                    in 200..299 -> return conn.inputStream.bufferedReader().use { it.readText() }
                    401, 403 -> throw ProviderException("The server refused access ($code). Check your login or activation.")
                    404 -> throw ProviderException("Nothing found at that address (404). Check the link.")
                    else -> throw ProviderException("The server answered $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw ProviderException("Too many redirects")
    }
}

/** Adds http:// when missing and removes a trailing slash. */
fun normalizeAddress(input: String): String {
    var s = input.trim()
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
    return s.trimEnd('/')
}

data class ProviderResult(val channels: List<Channel>, val info: String)

/** Xtream Codes compatible servers: server address + username + password. */
object Xtream {

    /** "provider.com:8080/get.php?username=..." → "http://provider.com:8080" */
    fun normalizeServer(input: String): String =
        normalizeAddress(input)
            .substringBefore("/player_api.php")
            .substringBefore("/get.php")
            .substringBefore("?")
            .trimEnd('/')

    fun load(server: String, username: String, password: String): ProviderResult {
        val host = normalizeServer(server)
        val api = "$host/player_api.php?username=${enc(username)}&password=${enc(password)}"

        val login = try {
            JSONObject(Http.get(api))
        } catch (e: ProviderException) {
            throw e
        } catch (e: Exception) {
            throw ProviderException("That server didn't answer like an Xtream server. Check the address.")
        }
        val user = login.optJSONObject("user_info")
            ?: throw ProviderException("The server didn't accept this login")
        if (user.optString("auth") != "1") throw ProviderException("Wrong username or password")
        val status = user.optString("status")
        if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) {
            throw ProviderException("This account is ${status.lowercase()}")
        }

        val formats = user.optJSONArray("allowed_output_formats")
        val ext = if (formats != null && (0 until formats.length()).any { formats.optString(it) == "m3u8" }) "m3u8" else "ts"

        val categories = HashMap<String, String>()
        runCatching { JSONArray(Http.get("$api&action=get_live_categories")) }.getOrNull()?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                categories[o.optString("category_id")] = o.optString("category_name")
            }
        }

        val streams = try {
            JSONArray(Http.get("$api&action=get_live_streams"))
        } catch (e: ProviderException) {
            throw e
        } catch (e: Exception) {
            throw ProviderException("Couldn't read the channel list from the server")
        }
        val userPath = enc(username).replace("+", "%20")
        val passPath = enc(password).replace("+", "%20")
        val channels = (0 until streams.length()).mapNotNull { i ->
            val o = streams.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("stream_id").ifBlank { return@mapNotNull null }
            Channel(
                name = o.optString("name").trim(),
                url = "$host/live/$userPath/$passPath/$id.$ext",
                group = categories[o.optString("category_id")].orEmpty(),
                logo = o.optString("stream_icon"),
                tvgId = o.optString("epg_channel_id").takeUnless { it == "null" }.orEmpty(),
            )
        }

        val exp = user.optString("exp_date").toLongOrNull()
        val info = buildList {
            add(if (exp != null && exp > 0) "Expires " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(exp * 1000)) else "No expiry date")
            user.optString("max_connections").toIntOrNull()?.let { add("$it screen" + if (it == 1) "" else "s") }
        }.joinToString(" · ")
        return ProviderResult(channels, info)
    }
}

/**
 * MAG-style portals (Stalker / Ministra middleware) that activate a device by MAC address.
 * The provider registers the MAC shown in the app; the app then logs in with that MAC.
 */
object Portal {

    private const val STB_AGENT =
        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

    class Session(val endpoint: String, val portal: String, val mac: String, val token: String) {
        val createdAt = System.currentTimeMillis()
    }

    /** Where the portal's API usually lives, for an address like http://host:8080/c/ */
    fun endpoints(portalUrl: String): List<String> {
        val u = URL(normalizeAddress(portalUrl))
        val origin = "${u.protocol}://${u.authority}"
        val path = u.path.trimEnd('/').removeSuffix("/c").removeSuffix("/portal.php").removeSuffix("/server/load.php")
        return listOf(
            "$origin$path/portal.php",
            "$origin$path/server/load.php",
            "$origin/stalker_portal/server/load.php",
            "$origin/portal.php",
        ).distinct()
    }

    private fun call(endpoint: String, portal: String, mac: String, token: String?, query: String): JSONObject {
        val headers = buildMap {
            put("User-Agent", STB_AGENT)
            put("X-User-Agent", "Model: MAG250; Link: WiFi")
            put("Referer", normalizeAddress(portal) + "/")
            put("Cookie", "mac=${enc(mac)}; stb_lang=en; timezone=${enc(java.util.TimeZone.getDefault().id)}")
            if (token != null) put("Authorization", "Bearer $token")
        }
        return JSONObject(Http.get("$endpoint?$query&JsHttpRequest=1-xml", headers))
    }

    fun connect(portalUrl: String, mac: String): Session {
        var lastError: Exception? = null
        for (ep in endpoints(portalUrl)) {
            try {
                val token = call(ep, portalUrl, mac, null, "type=stb&action=handshake&token=")
                    .optJSONObject("js")?.optString("token").orEmpty()
                if (token.isBlank()) continue
                call(ep, portalUrl, mac, token, "type=stb&action=get_profile&hd=1&stb_type=MAG250&auth_second_step=1")
                return Session(ep, portalUrl, mac, token)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError as? ProviderException)
            ?: ProviderException("Couldn't connect to that portal. Check the address and that this MAC is activated.")
    }

    fun channels(s: Session): ProviderResult {
        val genres = HashMap<String, String>()
        runCatching { call(s.endpoint, s.portal, s.mac, s.token, "type=itv&action=get_genres").optJSONArray("js") }
            .getOrNull()?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    genres[o.optString("id")] = o.optString("title")
                }
            }

        val data = runCatching {
            call(s.endpoint, s.portal, s.mac, s.token, "type=itv&action=get_all_channels")
                .optJSONObject("js")?.optJSONArray("data")
        }.getOrNull()
        if (data == null || data.length() == 0) {
            throw ProviderException("The portal sent no channels. Ask your provider to activate MAC ${s.mac}.")
        }

        val channels = (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val cmd = o.optString("cmd").ifBlank { return@mapNotNull null }
            val logo = o.optString("logo")
            Channel(
                name = o.optString("name").trim(),
                url = "",
                portalCmd = cmd,
                group = genres[o.optString("tv_genre_id")].orEmpty(),
                logo = if (logo.startsWith("http")) logo else "",
                tvgId = o.optString("xmltv_id"),
            )
        }
        return ProviderResult(channels, "MAC ${s.mac}")
    }

    /** Asks the portal for a fresh, playable link for a channel. */
    fun streamUrl(s: Session, cmd: String): String {
        val answer = call(
            s.endpoint, s.portal, s.mac, s.token,
            "type=itv&action=create_link&cmd=${enc(cmd)}&series=&forced_storage=undefined&disable_ad=0&download=0",
        ).optJSONObject("js")?.optString("cmd").orEmpty().ifBlank { cmd }
        // Answers look like "ffmpeg http://…" or "auto http://…"
        return answer.trim().split(Regex("\\s+")).lastOrNull { it.contains("://") }
            ?: throw ProviderException("The portal didn't give a link for this channel")
    }
}

/**
 * The MAC address this app shows to portal providers. Android doesn't let apps read the
 * real hardware MAC, so — like other portal players — the app makes one, keeps it forever,
 * and the user gives it to their provider to activate.
 */
object DeviceMac {
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        prefs.getString("mac", null)?.let { return it }
        val r = ByteArray(3).also { SecureRandom().nextBytes(it) }
        val mac = "00:1A:79:%02X:%02X:%02X".format(r[0].toInt() and 0xFF, r[1].toInt() and 0xFF, r[2].toInt() and 0xFF)
        prefs.edit().putString("mac", mac).apply()
        return mac
    }
}
