package com.diell.iptv.model

import java.util.UUID

/** Where a playlist's channels come from. */
enum class SourceKind { FILE, M3U_LINK, XTREAM, PORTAL }

/** One TV channel. */
data class Channel(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val group: String = "",
    val logo: String = "",
    val tvgId: String = "",
    val duration: String = "-1",
    /** Any other #EXTINF attributes (tvg-name, catchup, ...) kept as-is. */
    val attrs: Map<String, String> = emptyMap(),
    /** Extra lines between #EXTINF and the URL (#EXTVLCOPT, #KODIPROP, ...). */
    val extra: List<String> = emptyList(),
    val favorite: Boolean = false,
    /** Portal channels: the command the portal turns into a stream link when you press play. */
    val portalCmd: String = "",
    /** Added by the user in the app, not by the provider. Kept when the playlist updates. */
    val custom: Boolean = false,
) {
    /** Identifies the same channel across updates from the provider. */
    val key: String get() = portalCmd.ifBlank { url }
}

/** A saved playlist: its channels plus where it came from. */
data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceUrl: String? = null,
    val header: String = "#EXTM3U",
    val channels: List<Channel> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val kind: SourceKind = if (sourceUrl != null) SourceKind.M3U_LINK else SourceKind.FILE,
    /** Xtream server address or portal address. */
    val server: String = "",
    val username: String = "",
    val password: String = "",
    /** MAC address this device uses with a portal. */
    val mac: String = "",
    /** Provider channels the user removed; they stay removed when the playlist updates. */
    val removedKeys: Set<String> = emptySet(),
    /** Short account note shown on the card, e.g. "Expires 12 Mar 2027". */
    val info: String = "",
) {
    /** Groups in the order they first appear in the playlist. */
    val groups: List<String>
        get() = channels.map { it.group }.filter { it.isNotBlank() }.distinct()

    val updatesOnline: Boolean get() = kind != SourceKind.FILE
}
