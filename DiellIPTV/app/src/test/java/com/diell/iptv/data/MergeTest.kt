package com.diell.iptv.data

import com.diell.iptv.model.Channel
import com.diell.iptv.model.Playlist
import com.diell.iptv.model.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {

    private fun ch(name: String, group: String = "News") =
        Channel(name = name, url = "http://example.com/${name.lowercase()}.m3u8", group = group)

    @Test
    fun userChangesSurviveAnUpdate() {
        val a = ch("A"); val b = ch("B"); val c = ch("C"); val d = ch("D")
        val mine = Channel(name = "My Cam", url = "http://example.com/cam.m3u8", custom = true)
        val old = Playlist(
            name = "Home",
            kind = SourceKind.M3U_LINK,
            sourceUrl = "http://example.com/list.m3u",
            // user: removed B, renamed + favorited C, moved C to the top, added My Cam
            channels = listOf(c.copy(name = "C (mine)", favorite = true), a, d, mine),
            removedKeys = setOf(b.key),
        )
        // provider now: A, B, C, E (D was dropped, E is new); fresh copies have new ids
        val fresh = listOf(ch("A"), ch("B"), ch("C"), ch("E"))

        val merged = Merge.refresh(old, fresh)

        assertEquals(listOf("C (mine)", "A", "My Cam", "E"), merged.map { it.name })
        assertTrue(merged.first().favorite)
        assertEquals(c.id, merged.first().id) // same channel, kept its id
    }

    @Test
    fun portalChannelsMatchByCommand() {
        val old = Playlist(
            name = "Portal",
            kind = SourceKind.PORTAL,
            channels = listOf(Channel(name = "News", url = "", portalCmd = "ffrt http://localhost/ch/1", favorite = true)),
        )
        val fresh = listOf(Channel(name = "News HD", url = "", portalCmd = "ffrt http://localhost/ch/1"))
        val merged = Merge.refresh(old, fresh)
        assertEquals(1, merged.size)
        assertTrue(merged[0].favorite)
    }

    @Test
    fun firstLoadTakesEverythingExceptRemoved() {
        val old = Playlist(name = "New", kind = SourceKind.XTREAM, removedKeys = setOf(ch("B").key))
        val merged = Merge.refresh(old, listOf(ch("A"), ch("B"), ch("C")))
        assertEquals(listOf("A", "C"), merged.map { it.name })
    }
}
