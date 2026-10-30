package com.diell.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {

    private val sample = """
        #EXTM3U x-tvg-url="http://example.com/epg.xml"
        #EXTINF:-1 tvg-id="news.ex" tvg-name="News 24" tvg-logo="http://example.com/n.png" group-title="News",News 24
        http://example.com/live/news.m3u8
        #EXTINF:-1 tvg-id="sport.ex" group-title="Sports, Live",Sport 1
        #EXTVLCOPT:http-user-agent=Test
        http://example.com/live/sport1.ts
        #EXTINF:-1,No Group Channel
        #EXTGRP:Music
        http://example.com/radio.mp3
        http://example.com/bare-url.m3u8
    """.trimIndent()

    @Test
    fun parsesChannelsGroupsAndExtras() {
        val p = M3u.parse(sample)
        assertEquals("#EXTM3U x-tvg-url=\"http://example.com/epg.xml\"", p.header)
        assertEquals(4, p.channels.size)

        val news = p.channels[0]
        assertEquals("News 24", news.name)
        assertEquals("News", news.group)
        assertEquals("news.ex", news.tvgId)
        assertEquals("http://example.com/n.png", news.logo)
        assertEquals("News 24", news.attrs["tvg-name"])

        val sport = p.channels[1]
        assertEquals("Sport 1", sport.name)          // comma inside quotes is not the name separator
        assertEquals("Sports, Live", sport.group)
        assertEquals(listOf("#EXTVLCOPT:http-user-agent=Test"), sport.extra)

        assertEquals("Music", p.channels[2].group)  // from #EXTGRP
        assertEquals("http://example.com/bare-url.m3u8", p.channels[3].url)
    }

    @Test
    fun writeThenParseKeepsEverything() {
        val first = M3u.parse(sample)
        val text = M3u.write(first.header, first.channels)
        val second = M3u.parse(text)
        assertEquals(first.header, second.header)
        assertEquals(first.channels.map { listOf(it.name, it.url, it.group, it.logo, it.tvgId) },
            second.channels.map { listOf(it.name, it.url, it.group, it.logo, it.tvgId) })
        assertTrue(text.contains("#EXTVLCOPT:http-user-agent=Test"))
    }
}
