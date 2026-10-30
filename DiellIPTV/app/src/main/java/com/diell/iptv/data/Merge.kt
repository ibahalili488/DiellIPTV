package com.diell.iptv.data

import com.diell.iptv.model.Channel
import com.diell.iptv.model.Playlist

/**
 * Combines a fresh channel list from the provider with the user's own changes, so managing
 * a playlist in the app survives every update:
 *  - channels the user removed stay removed
 *  - channels the user added stay
 *  - the user's names, groups, favorites and order are kept for channels that still exist
 *  - channels the provider dropped disappear, new provider channels are added at the end
 */
object Merge {

    fun refresh(old: Playlist, fresh: List<Channel>): List<Channel> {
        val freshByKey = LinkedHashMap<String, Channel>()
        fresh.forEach { freshByKey.putIfAbsent(it.key, it) }

        val result = mutableListOf<Channel>()
        val seen = HashSet<String>()
        for (c in old.channels) {
            if (c.custom) {
                result += c
                continue
            }
            val f = freshByKey[c.key] ?: continue
            if (!seen.add(c.key)) continue
            result += c.copy(
                logo = c.logo.ifBlank { f.logo },
                tvgId = c.tvgId.ifBlank { f.tvgId },
            )
        }
        for ((key, f) in freshByKey) {
            if (key in seen || key in old.removedKeys) continue
            if (old.channels.any { !it.custom && it.key == key }) continue
            seen += key
            result += f
        }
        return result
    }
}
