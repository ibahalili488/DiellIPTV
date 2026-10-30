package com.diell.iptv.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.diell.iptv.model.Channel
import com.diell.iptv.model.Playlist
import com.diell.iptv.model.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

const val USER_AGENT = "DiellIPTV/1.0 (Android)"

/** Saves playlists as JSON files in the app's private storage and loads M3U from links or files. */
class PlaylistRepository(private val context: Context) {

    private val dir = File(context.filesDir, "playlists").apply { mkdirs() }

    suspend fun loadAll(): List<Playlist> = withContext(Dispatchers.IO) {
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { file -> runCatching { fromJson(JSONObject(file.readText())) }.getOrNull() }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun save(playlist: Playlist) = withContext(Dispatchers.IO) {
        val tmp = File(dir, "${playlist.id}.tmp")
        tmp.writeText(toJson(playlist).toString())
        if (!tmp.renameTo(File(dir, "${playlist.id}.json"))) throw IOException("Could not save playlist")
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(dir, "$id.json").delete()
    }

    suspend fun download(url: String): String = withContext(Dispatchers.IO) { Http.get(url.trim()) }

    suspend fun readUri(uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("Can't open that file")
    }

    suspend fun writeUri(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write that file")
        stream.bufferedWriter().use { it.write(text) }
    }

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    // ---- JSON ----

    private fun toJson(p: Playlist) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("sourceUrl", p.sourceUrl ?: JSONObject.NULL)
        put("header", p.header)
        put("updatedAt", p.updatedAt)
        put("kind", p.kind.name)
        put("server", p.server)
        put("username", p.username)
        put("password", p.password)
        put("mac", p.mac)
        put("info", p.info)
        put("removedKeys", JSONArray(p.removedKeys.toList()))
        put("channels", JSONArray().apply {
            p.channels.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("url", c.url)
                    put("group", c.group)
                    put("logo", c.logo)
                    put("tvgId", c.tvgId)
                    put("duration", c.duration)
                    put("favorite", c.favorite)
                    put("attrs", JSONObject(c.attrs))
                    put("extra", JSONArray(c.extra))
                    put("portalCmd", c.portalCmd)
                    put("custom", c.custom)
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): Playlist {
        val arr = o.getJSONArray("channels")
        val channels = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val attrsObj = c.optJSONObject("attrs") ?: JSONObject()
            val extraArr = c.optJSONArray("extra") ?: JSONArray()
            Channel(
                id = c.getString("id"),
                name = c.optString("name"),
                url = c.optString("url"),
                group = c.optString("group"),
                logo = c.optString("logo"),
                tvgId = c.optString("tvgId"),
                duration = c.optString("duration", "-1"),
                favorite = c.optBoolean("favorite"),
                attrs = attrsObj.keys().asSequence().associateWith { attrsObj.optString(it) },
                extra = (0 until extraArr.length()).map { extraArr.optString(it) },
                portalCmd = c.optString("portalCmd"),
                custom = c.optBoolean("custom"),
            )
        }
        val removed = o.optJSONArray("removedKeys") ?: JSONArray()
        val sourceUrl = if (o.isNull("sourceUrl")) null else o.optString("sourceUrl")
        return Playlist(
            id = o.getString("id"),
            name = o.optString("name", "Playlist"),
            sourceUrl = sourceUrl,
            header = o.optString("header", "#EXTM3U"),
            channels = channels,
            updatedAt = o.optLong("updatedAt"),
            kind = runCatching { SourceKind.valueOf(o.getString("kind")) }
                .getOrDefault(if (sourceUrl != null) SourceKind.M3U_LINK else SourceKind.FILE),
            server = o.optString("server"),
            username = o.optString("username"),
            password = o.optString("password"),
            mac = o.optString("mac"),
            info = o.optString("info"),
            removedKeys = (0 until removed.length()).map { removed.optString(it) }.toSet(),
        )
    }
}
