package com.daniel.mood

import org.junit.Assert.*
import org.junit.Test

class IntelligenceTest {
    private fun song(id: Long, mood: Int, energy: Float, bpm: Float = 120f) = Song(id = id, uri = "content://$id", title = "Track $id", artist = "Artist", album = "Album", duration = 180000, size = 1000, modified = 1, added = id,
        mood = mood, secondary = mood, confidence = .8f, bpm = bpm, energy = energy, reason = "", manual = false, seen = 1,
        autoMood = mood, analysisVersion = 2, featureData = Features(bpm, .5f, energy / 5f, .1f, 2200f, 3000f, .3f, .1f, .5f, .5f, 3f, .5f).serialize())

    @Test fun listeningDnaIsIncrementalAndSkipsHaveNoPositiveWeight() {
        val calm = song(1, Moods.CALM, .1f, 75f)
        val energetic = song(2, Moods.ENERGETIC, .9f, 160f)
        var dna = TasteEngine.applyEvent(null, calm, ListeningEvent(songId = 1, startedAt = 1, listenedMs = 170000, trackDurationMs = 180000, completionRatio = .95f, skipped = false, source = "test"))
        val initialEnergy = TasteEngine.snapshot(dna).averageEnergy
        assertTrue(dna.eventCount == 1L && dna.totalWeight > 0)
        dna = TasteEngine.applyEvent(dna, energetic, ListeningEvent(songId = 2, startedAt = 2, listenedMs = 3000, trackDurationMs = 180000, completionRatio = .02f, skipped = true, source = "test"))
        assertEquals(2, dna.eventCount)
        assertEquals(initialEnergy, TasteEngine.snapshot(dna).averageEnergy, .002f)
    }

    @Test fun radarExcludesSeedAndGivesAuditableReasons() {
        val all = listOf(song(1, Moods.CALM, .1f, 75f), song(2, Moods.CALM, .12f, 78f), song(3, Moods.ENERGETIC, .95f, 170f))
        val result = RadarEngine.recommend(all, null, all.first(), emptyList(), 5)
        assertFalse(result.any { it.song.id == 1L })
        assertTrue(result.first().reason in setOf("similar_audio", "liked_characteristics", "unheard_dna_match", "preferred_energy_rhythm", "mood_audio_match"))
        assertTrue(result.all { it.score >= 0 && it.score <= 1 })
    }

    @Test fun journeyRespectsMoodEndpointsAndAvoidsDuplicates() {
        val library = (0..20).map { song(it.toLong(), if (it < 10) Moods.SAD else Moods.HAPPY, it / 20f, 70f + it * 4) }
        val journey = JourneyEngine.generate(library, null, JourneyConfig(Moods.SAD, Moods.HAPPY, 8, .7f, .5f))
        assertTrue(journey.isNotEmpty())
        assertEquals(journey.size, journey.map { it.id }.distinct().size)
        assertEquals(Moods.SAD, journey.first().mood)
        assertEquals(Moods.HAPPY, journey.last().mood)
    }

    @Test fun djExcludesRecentlyPlayedAndPrefersCompatibleTempo() {
        val library = listOf(song(1, Moods.HAPPY, .6f, 120f), song(2, Moods.HAPPY, .62f, 122f), song(3, Moods.SAD, .1f, 60f))
        val current = library[0]
        val next = SmartDjSelector.next(library, current, null, listOf(2L), DjConfig(mode = "party", bpmMatching = true, energyMatching = true))
        assertNotEquals(2L, next?.id)
        val available = SmartDjSelector.next(library, current, null, emptyList(), DjConfig(mode = "party", bpmMatching = true, energyMatching = true))
        assertEquals(2L, available?.id)
    }
}

class MusicTwinTest {
    @Test fun controlledPersianAndEnglishRequests() {
        assertTrue(TwinParser.parse("یه آهنگ آروم پخش کن", null) is TwinAction.PlayMood)
        assertEquals(Moods.CALM, (TwinParser.parse("play something calm", null) as TwinAction.PlayMood).mood)
        assertTrue(TwinParser.parse("find forgotten songs", null) is TwinAction.Discover)
        val journey = TwinParser.parse("build a 45 minute journey from sad to energetic", null) as TwinAction.Journey
        assertEquals(45, journey.minutes)
        assertEquals(Moods.SAD, journey.start)
        assertEquals(Moods.ENERGETIC, journey.end)
        assertTrue(TwinParser.parse("delete listening history", null) is TwinAction.DeleteHistory)
        assertTrue(TwinParser.parse("ارز دیجیتال بخر", null) is TwinAction.Unknown)
    }

    @Test fun timeRangesAreRealAndBounded() {
        val current = TimeRanges.thisMonth()
        val previous = TimeRanges.lastMonth()
        assertTrue(current.first <= current.second)
        assertTrue(previous.second < current.first)
        assertTrue(TimeRanges.daysBack(7).second >= TimeRanges.daysBack(7).first)
    }
}
