package com.daniel.mood

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodModelTest {
    private fun features(
        bpm: Float, beat: Float, rms: Float, dynamics: Float, centroid: Float,
        rolloff: Float, flux: Float, zcr: Float, minor: Float, bass: Float,
        onset: Float, vocal: Float, sustained: Float
    ) = FeatureVector(
        bpm, beat, rms, dynamics, centroid, rolloff, flux, zcr,
        minor, bass, onset, vocal, sustained
    )

    private val happy = features(125f, .8f, .18f, .05f, 3200f, 6000f, .08f, .05f, .2f, .25f, 3f, .7f, .4f)
    private val sad = features(65f, .25f, .07f, .08f, 1400f, 3500f, .04f, .03f, .85f, .3f, 1.5f, .8f, .8f)
    private val energetic = features(150f, .85f, .25f, .18f, 3800f, 7000f, .18f, .09f, .45f, .35f, 7f, .5f, .2f)
    private val instrumental = features(90f, .3f, .10f, .04f, 2200f, 4500f, .03f, .025f, .5f, .3f, 1f, .05f, .9f)
    private val ambiguous = features(100f, .5f, .12f, .10f, 2300f, 5000f, .09f, .05f, .5f, .3f, 4f, .5f, .5f)
    private val rainy = features(70f, .2f, .08f, .03f, 1500f, 3600f, .05f, .03f, .6f, .3f, 1f, .25f, .85f)

    @Test
    fun `bright danceable major track is happy`() {
        val prediction = MoodModel.classify(happy)
        assertEquals(MoodCatalog.HAPPY, prediction.suggested)
        assertEquals(MoodCatalog.HAPPY, prediction.mood)
        assertTrue(prediction.confidence > MoodModel.REVIEW_THRESHOLD)
    }

    @Test
    fun `slow minor vocal track is sad`() {
        val prediction = MoodModel.classify(sad)
        assertEquals(MoodCatalog.SAD, prediction.suggested)
        assertEquals(MoodCatalog.SAD, prediction.mood)
    }

    @Test
    fun `loud fast track is energetic rather than simply happy`() {
        val prediction = MoodModel.classify(energetic)
        assertEquals(MoodCatalog.ENERGETIC, prediction.suggested)
        assertNotEquals(MoodCatalog.HAPPY, prediction.suggested)
    }

    @Test
    fun `low vocal modulation selects instrumental`() {
        val prediction = MoodModel.classify(instrumental)
        assertEquals(MoodCatalog.INSTRUMENTAL, prediction.suggested)
        assertEquals(MoodCatalog.INSTRUMENTAL, prediction.mood)
    }

    @Test
    fun `fast minor track is not classified as happy`() {
        val fastMinor = features(160f, .7f, .08f, .06f, 1500f, 3600f, .05f, .03f, .9f, .3f, 2f, .8f, .7f)
        assertNotEquals(MoodCatalog.HAPPY, MoodModel.classify(fastMinor).suggested)
    }

    @Test
    fun `ambiguous signal becomes needs review and keeps a suggestion`() {
        val prediction = MoodModel.classify(ambiguous)
        assertEquals(MoodCatalog.REVIEW, prediction.mood)
        assertTrue(prediction.confidence < MoodModel.REVIEW_THRESHOLD)
        assertTrue(prediction.suggested in MoodCatalog.selectable)
        assertNotEquals(prediction.suggested, prediction.secondary)
    }

    @Test
    fun `subjective rainy track is not asserted without evidence`() {
        assertEquals(MoodCatalog.REVIEW, MoodModel.classify(rainy).mood)
    }

    @Test
    fun `manual corrections build prototypes that change future classification`() {
        val baseline = MoodModel.classify(rainy)
        val taught = MoodModel.buildPrototypes(List(5) { MoodCatalog.RAINY to rainy })
        assertEquals(1, taught.size)
        assertEquals(5, taught[0].samples)
        val after = MoodModel.classify(rainy, taught)
        assertEquals(MoodCatalog.RAINY, after.mood)
        assertTrue(after.confidence > baseline.confidence)
    }

    @Test
    fun `feedback boosts only the taught mood`() {
        val prototype = MoodModel.buildPrototypes(List(3) { MoodCatalog.NOSTALGIC to ambiguous })
        val base = MoodModel.baseScores(ambiguous)
        val adjusted = MoodModel.scoresWithFeedback(ambiguous, prototype)
        assertTrue(adjusted[MoodCatalog.NOSTALGIC] > base[MoodCatalog.NOSTALGIC])
        assertEquals(base[MoodCatalog.HAPPY], adjusted[MoodCatalog.HAPPY], 0.0001f)
    }

    @Test
    fun `review is not a learnable mood`() {
        assertTrue(MoodModel.buildPrototypes(listOf(MoodCatalog.REVIEW to happy)).isEmpty())
    }

    @Test
    fun `feature vectors survive a JSON-free round trip`() {
        val encoded = MoodModel.encode(happy)
        assertEquals(happy, MoodModel.decode(encoded))
        assertNotNull(MoodModel.decodeVector(MoodModel.encodeVector(MoodModel.normalized(sad))))
        assertNull(MoodModel.decode("broken"))
        assertNull(MoodModel.decode(null))
    }

    @Test
    fun `normalized vectors are bounded`() {
        MoodModel.normalized(energetic).forEach { assertTrue(it in 0f..1f) }
    }
}
