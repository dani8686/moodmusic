package com.daniel.mood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpersTest {
    @Test
    fun `safeName removes path separators and illegal characters`() {
        assertEquals("a_b_c_.mp3", FileNames.safeName("a/b:c*.mp3"))
        assertEquals("___", FileNames.safeName("///"))
        assertEquals("audio", FileNames.safeName(""))
        assertEquals("shad", FileNames.safeName("  shad. "))
    }

    @Test
    fun `uniqueName avoids collisions and keeps the extension`() {
        assertEquals("song.mp3", FileNames.uniqueName("song.mp3", emptySet()))
        assertEquals("song_3.mp3", FileNames.uniqueName("song.mp3", setOf("song.mp3", "song_2.mp3")))
        assertEquals("track_2", FileNames.uniqueName("track", setOf("track")))
    }

    @Test
    fun `uniqueZipName never reuses an entry name`() {
        val used = HashSet<String>()
        assertEquals("a.mp3", FileNames.uniqueZipName("a.mp3", used))
        assertEquals("a_2.mp3", FileNames.uniqueZipName("a.mp3", used))
        assertEquals("a_3.mp3", FileNames.uniqueZipName("a.mp3", used))
        assertEquals(3, used.size)
    }

    @Test
    fun `withExtension keeps real extensions and adds a missing one`() {
        assertEquals("song.mp3", FileNames.withExtension("song.mp3", "audio/mpeg"))
        assertEquals("song.mp3", FileNames.withExtension("song", "audio/mpeg"))
        assertEquals("song.m4a", FileNames.withExtension("song", "audio/mp4"))
        assertEquals("song.audio", FileNames.withExtension("song", null))
    }

    @Test
    fun `LIKE wildcards are escaped`() {
        assertEquals("%50!%!_!!x%", SearchQuery.pattern("50%_!x"))
        assertEquals("!%", SearchQuery.escapeLike("%"))
        assertEquals("!!", SearchQuery.escapeLike("!"))
    }

    @Test
    fun `repeat mode cycles off one all off`() {
        assertEquals(QueueLogic.REPEAT_ONE, QueueLogic.cycleRepeat(QueueLogic.REPEAT_OFF))
        assertEquals(QueueLogic.REPEAT_ALL, QueueLogic.cycleRepeat(QueueLogic.REPEAT_ONE))
        assertEquals(QueueLogic.REPEAT_OFF, QueueLogic.cycleRepeat(QueueLogic.REPEAT_ALL))
    }

    @Test
    fun `queue reordering is bounded and stable`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), QueueLogic.move(list, 0, 2))
        assertEquals(list, QueueLogic.move(list, 1, 1))
        assertEquals(list, QueueLogic.move(list, 8, 2))
        assertEquals(3, QueueLogic.targetIndex(1, 99, 4))
        assertEquals(0, QueueLogic.targetIndex(1, -4, 4))
    }

    @Test
    fun `persian formatting is deterministic`() {
        assertEquals("۱۲:۰۵", MoodCatalog.clock(725_000L, true))
        assertEquals("12:05", MoodCatalog.clock(725_000L, false))
        assertEquals("خانه", MoodCatalog.tr(true, "Home", "خانه"))
        assertEquals("Home", MoodCatalog.tr(false, "Home", "خانه"))
        assertEquals(12, MoodCatalog.selectable.size)
        assertTrue(MoodCatalog.isSubjective(MoodCatalog.RAINY))
        assertFalse(MoodCatalog.isSubjective(MoodCatalog.HAPPY))
    }
}
