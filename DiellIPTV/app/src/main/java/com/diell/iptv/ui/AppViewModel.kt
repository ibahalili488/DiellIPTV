package com.diell.iptv.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diell.iptv.data.DeviceMac
import com.diell.iptv.data.M3u
import com.diell.iptv.data.Merge
import com.diell.iptv.data.PlaylistRepository
import com.diell.iptv.data.Portal
import com.diell.iptv.data.Xtream
import com.diell.iptv.data.normalizeAddress
import com.diell.iptv.model.Channel
import com.diell.iptv.model.Playlist
import com.diell.iptv.model.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URL

data class UiMessage(val text: String, val canUndo: Boolean = false)

/** What a provider sent: its channels, the M3U header line, and a short account note. */
private data class Fresh(val channels: List<Channel>, val header: String = "#EXTM3U", val info: String = "")

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository(app)
    private val saveLock = Mutex()

    /** The MAC address this device gives to portal providers. */
    val deviceMac: String by lazy { DeviceMac.get(app) }

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    /** Channels the player can zap through (the list that was on screen when playback started). */
    var playQueue: List<Channel> = emptyList()
    var playPlaylistId: String? = null

    private var undoSnapshot: Playlist? = null
    private val portalSessions = HashMap<String, Portal.Session>()

    init {
        viewModelScope.launch {
            _playlists.value = repo.loadAll()
            _loaded.value = true
            autoUpdate()
        }
    }

    fun playlist(id: String?): Playlist? = _playlists.value.firstOrNull { it.id == id }

    private fun say(text: String, canUndo: Boolean = false) {
        _messages.tryEmit(UiMessage(text, canUndo))
    }

    private fun persist(id: String) {
        viewModelScope.launch {
            saveLock.withLock {
                val latest = playlist(id) ?: return@withLock
                runCatching { repo.save(latest) }.onFailure { say("Couldn't save: ${it.message}") }
            }
        }
    }

    private fun update(id: String, keepForUndo: Boolean = false, touch: Boolean = false, change: (Playlist) -> Playlist) {
        val old = playlist(id) ?: return
        if (keepForUndo) undoSnapshot = old
        val changed = change(old)
        val new = if (touch) changed.copy(updatedAt = System.currentTimeMillis()) else changed
        _playlists.value = _playlists.value.map { if (it.id == id) new else it }
        persist(id)
    }

    private fun insert(p: Playlist) {
        _playlists.value = (_playlists.value + p).sortedBy { it.name.lowercase() }
        persist(p.id)
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                say(e.message ?: "Something went wrong")
            } finally {
                _busy.value = false
            }
        }
    }

    private fun hostOf(address: String) = runCatching { URL(normalizeAddress(address)).host }.getOrNull() ?: "My playlist"

    // ---- Getting channels from each kind of source ----

    private suspend fun fetchFresh(p: Playlist): Fresh = withContext(Dispatchers.IO) {
        when (p.kind) {
            SourceKind.FILE -> Fresh(p.channels, p.header, p.info)
            SourceKind.M3U_LINK -> {
                val parsed = M3u.parse(repo.download(p.sourceUrl.orEmpty()))
                Fresh(parsed.channels, parsed.header)
            }
            SourceKind.XTREAM -> {
                val r = Xtream.load(p.server, p.username, p.password)
                Fresh(r.channels, info = r.info)
            }
            SourceKind.PORTAL -> {
                val session = Portal.connect(p.server, p.mac)
                portalSessions[p.id] = session
                val r = Portal.channels(session)
                Fresh(r.channels, info = r.info)
            }
        }
    }

    // ---- Adding playlists ----

    fun addFromUrl(name: String, url: String) = work {
        val link = url.trim()
        val draft = Playlist(name = name.trim().ifBlank { hostOf(link) }, sourceUrl = link, kind = SourceKind.M3U_LINK)
        val fresh = fetchFresh(draft)
        if (fresh.channels.isEmpty()) {
            say("No channels found at that link")
            return@work
        }
        insert(draft.copy(header = fresh.header, channels = fresh.channels))
        say("Added ${fresh.channels.size} channels")
    }

    fun addXtream(name: String, server: String, username: String, password: String) = work {
        val host = Xtream.normalizeServer(server)
        val draft = Playlist(
            name = name.trim().ifBlank { hostOf(host) },
            kind = SourceKind.XTREAM,
            server = host,
            username = username.trim(),
            password = password,
        )
        val fresh = fetchFresh(draft)
        if (fresh.channels.isEmpty()) {
            say("Logged in, but the account has no live channels")
            return@work
        }
        insert(draft.copy(channels = fresh.channels, info = fresh.info))
        say("Logged in · ${fresh.channels.size} channels")
    }

    fun addPortal(name: String, portalUrl: String) = work {
        val address = normalizeAddress(portalUrl)
        val draft = Playlist(name = name.trim().ifBlank { hostOf(address) }, kind = SourceKind.PORTAL, server = address, mac = deviceMac)
        val fresh = fetchFresh(draft)
        val saved = draft.copy(channels = fresh.channels, info = fresh.info)
        insert(saved) // the portal session was kept under this playlist's id while fetching
        say("Connected · ${fresh.channels.size} channels")
    }

    fun addFromFile(uri: Uri) = work {
        val parsed = M3u.parse(repo.readUri(uri))
        if (parsed.channels.isEmpty()) {
            say("No channels found in that file")
            return@work
        }
        val name = repo.displayName(uri)?.substringBeforeLast('.')?.ifBlank { null } ?: "My playlist"
        insert(Playlist(name = name, header = parsed.header, channels = parsed.channels))
        say("Added ${parsed.channels.size} channels")
    }

    fun createEmpty(name: String) {
        insert(Playlist(name = name.trim().ifBlank { "My channels" }))
    }

    // ---- Updating from the provider ----

    /**
     * Gets the latest channels from the provider and keeps the user's own changes
     * (removed, added, renamed, reordered, favorites).
     */
    fun reload(id: String) = work { refresh(id, quiet = false) }

    private suspend fun refresh(id: String, quiet: Boolean) {
        val p = playlist(id) ?: return
        if (!p.updatesOnline) {
            if (!quiet) say("This playlist came from a file, so there's nothing online to update from")
            return
        }
        val fresh = try {
            fetchFresh(p)
        } catch (e: Exception) {
            if (quiet) say("Couldn't update “${p.name}”: ${e.message}") else throw e
            return
        }
        if (fresh.channels.isEmpty()) {
            if (!quiet) say("The provider sent no channels, so nothing was changed")
            return
        }
        val before = p.channels.size
        update(id, keepForUndo = !quiet, touch = true) {
            it.copy(channels = Merge.refresh(it, fresh.channels), header = fresh.header, info = fresh.info.ifBlank { it.info })
        }
        if (!quiet) {
            val after = playlist(id)?.channels?.size ?: before
            say("Updated · $after channels" + if (after != before) " (was $before)" else "", canUndo = true)
        }
    }

    /** Online playlists update themselves when the app opens, if they're older than 6 hours. */
    private suspend fun autoUpdate() {
        val stale = _playlists.value.filter { it.updatesOnline && System.currentTimeMillis() - it.updatedAt > 6 * 3_600_000L }
        if (stale.isEmpty()) return
        _busy.value = true
        try {
            stale.forEach { refresh(it.id, quiet = true) }
        } finally {
            _busy.value = false
        }
    }

    /** Bring back provider channels the user removed earlier. */
    fun restoreRemoved(id: String) {
        val p = playlist(id) ?: return
        val count = p.removedKeys.size
        update(id) { it.copy(removedKeys = emptySet()) }
        say("Bringing back $count removed channel" + if (count == 1) "" else "s")
        reload(id)
    }

    fun rename(id: String, name: String) {
        if (name.isBlank()) return
        update(id) { it.copy(name = name.trim()) }
        _playlists.value = _playlists.value.sortedBy { it.name.lowercase() }
    }

    fun deletePlaylist(id: String) {
        val p = playlist(id) ?: return
        _playlists.value = _playlists.value.filterNot { it.id == id }
        portalSessions.remove(id)
        viewModelScope.launch { saveLock.withLock { repo.delete(id) } }
        say("Deleted “${p.name}”")
    }

    fun export(id: String, target: Uri) = work {
        val p = playlist(id) ?: return@work
        repo.writeUri(target, M3u.write(p.header, p.channels.filter { it.url.isNotBlank() }))
        say("Saved ${p.channels.size} channels to file")
    }

    // ---- Playing ----

    /** The link to play. Portal channels ask the portal for a fresh link each time. */
    suspend fun streamUrl(channel: Channel): String {
        if (channel.portalCmd.isBlank()) return channel.url
        val p = playlist(playPlaylistId) ?: throw IllegalStateException("Playlist not found")
        return withContext(Dispatchers.IO) {
            val cached = portalSessions[p.id]?.takeIf { System.currentTimeMillis() - it.createdAt < 30 * 60_000L }
            try {
                Portal.streamUrl(cached ?: Portal.connect(p.server, p.mac).also { portalSessions[p.id] = it }, channel.portalCmd)
            } catch (e: Exception) {
                if (cached == null) throw e
                // The portal session may have expired: log in again once.
                val fresh = Portal.connect(p.server, p.mac).also { portalSessions[p.id] = it }
                Portal.streamUrl(fresh, channel.portalCmd)
            }
        }
    }

    // ---- Channels ----

    fun removeChannels(playlistId: String, ids: Set<String>) {
        if (ids.isEmpty()) return
        update(playlistId, keepForUndo = true) { p ->
            val gone = p.channels.filter { it.id in ids }
            p.copy(
                channels = p.channels.filterNot { it.id in ids },
                removedKeys = p.removedKeys + gone.filterNot { it.custom }.map { it.key },
            )
        }
        say(if (ids.size == 1) "Channel removed" else "${ids.size} channels removed", canUndo = true)
    }

    fun undo() {
        val snap = undoSnapshot ?: return
        undoSnapshot = null
        _playlists.value = _playlists.value.map { if (it.id == snap.id) snap else it }
        persist(snap.id)
        say("Restored")
    }

    fun saveChannel(playlistId: String, channel: Channel, atTop: Boolean) {
        update(playlistId) { p ->
            val exists = p.channels.any { it.id == channel.id }
            val list = when {
                exists -> p.channels.map { if (it.id == channel.id) channel else it }
                atTop -> listOf(channel.copy(custom = true)) + p.channels
                else -> p.channels + channel.copy(custom = true)
            }
            p.copy(channels = list)
        }
        say("Saved “${channel.name}”")
    }

    fun moveChannel(playlistId: String, channelId: String, delta: Int) {
        update(playlistId) { p ->
            val list = p.channels.toMutableList()
            val from = list.indexOfFirst { it.id == channelId }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@update p
            list.add(to, list.removeAt(from))
            p.copy(channels = list)
        }
    }

    fun toggleFavorite(playlistId: String, channelId: String) {
        update(playlistId) { p ->
            p.copy(channels = p.channels.map { if (it.id == channelId) it.copy(favorite = !it.favorite) else it })
        }
    }
}
