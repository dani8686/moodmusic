package com.daniel.mood

import org.junit.Assert.*
import org.junit.Test

class ExportNamesTest {
    @Test fun duplicateNamesKeepExtensionAndNeverOverwrite() {
        val used = mutableSetOf<String>()
        assertEquals("song.mp3", ExportNames.unique("song.mp3", used))
        assertEquals("song_2.mp3", ExportNames.unique("song.mp3", used))
        assertEquals("song_3.mp3", ExportNames.unique("SONG.MP3", used))
    }

    @Test fun unsafePathsCannotEscapeZipOrFolder() {
        val clean = ExportNames.safe("../bad/na:me?.mp3")
        assertFalse(clean.contains('/'))
        assertFalse(clean.contains(':'))
        assertFalse(clean.contains('?'))
        assertEquals("_bad_na_me_.mp3", clean)
    }

    @Test fun blankNamesHaveSafeFallback() {
        assertEquals("audio", ExportNames.safe("..."))
    }

    @Test fun longNamesKeepPlayableExtension() {
        val result = ExportNames.safe("a".repeat(180) + ".flac")
        assertEquals(120, result.length)
        assertTrue(result.endsWith(".flac"))
    }
}
