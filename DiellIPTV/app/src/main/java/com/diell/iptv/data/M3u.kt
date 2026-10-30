package com.diell.iptv.data

import com.diell.iptv.model.Channel

/**
 * Reads and writes extended M3U playlists:
 *
 *   #EXTM3U
 *   #EXTINF:-1 tvg-id="news.example" tvg-logo="http://.../logo.png" group-title="News",News 24
 *   http://example.com/live/news.m3u8
 */
object M3u {

    data class Parsed(val header: String, val channels: List<Channel>)

    private val attrRegex = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    private val durationRegex = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)")
    private val knownAttrs = setOf("tvg-id", "tvg-logo", "group-title")

    fun parse(text: String): Parsed {
        var header = "#EXTM3U"
        val out = mutableListOf<Channel>()
        var pending: Channel? = null
        var extras = mutableListOf<String>()

        for (raw in text.removePrefix("﻿").lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> header = line
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseInf(line)
                    extras = mutableListOf()
                }
                line.startsWith("#") -> if (pending != null) extras.add(line)
                else -> {
                    val base = pending ?: Channel(name = line, url = line)
                    val group = base.group.ifBlank {
                        extras.firstOrNull { it.startsWith("#EXTGRP:", ignoreCase = true) }
                            ?.substringAfter(':')?.trim().orEmpty()
                    }
                    out += base.copy(url = line, group = group, extra = extras.toList())
                    pending = null
                    extras = mutableListOf()
                }
            }
        }
        return Parsed(header, out)
    }

    private fun parseInf(line: String): Channel {
        val body = line.substringAfter(':', "")
        val comma = nameSeparator(body)
        val head = if (comma >= 0) body.substring(0, comma) else body
        val name = if (comma >= 0) body.substring(comma + 1).trim() else ""
        val duration = durationRegex.find(head)?.groupValues?.get(1) ?: "-1"
        val attrs = LinkedHashMap<String, String>()
        attrRegex.findAll(head).forEach { attrs[it.groupValues[1]] = it.groupValues[2] }
        return Channel(
            name = name.ifBlank { attrs["tvg-name"].orEmpty() },
            url = "",
            group = attrs["group-title"].orEmpty(),
            logo = attrs["tvg-logo"].orEmpty(),
            tvgId = attrs["tvg-id"].orEmpty(),
            duration = duration,
            attrs = attrs.filterKeys { it !in knownAttrs },
        )
    }

    /** The comma before the channel name, ignoring commas inside quoted attributes. */
    private fun nameSeparator(s: String): Int {
        var quoted = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return -1
    }

    fun write(header: String, channels: List<Channel>): String = buildString {
        appendLine(header.ifBlank { "#EXTM3U" })
        for (c in channels) {
            append("#EXTINF:").append(c.duration.ifBlank { "-1" })
            val attrs = LinkedHashMap<String, String>()
            if (c.tvgId.isNotBlank()) attrs["tvg-id"] = c.tvgId
            attrs.putAll(c.attrs)
            if (c.logo.isNotBlank()) attrs["tvg-logo"] = c.logo
            if (c.group.isNotBlank()) attrs["group-title"] = c.group
            for ((key, value) in attrs) {
                if (value.isNotEmpty()) {
                    append(' ').append(key).append("=\"").append(value.replace('"', '\'')).append('"')
                }
            }
            append(',').appendLine(c.name)
            c.extra
                .filterNot { it.startsWith("#EXTGRP:", ignoreCase = true) }
                .forEach { appendLine(it) }
            appendLine(c.url)
        }
    }
}
