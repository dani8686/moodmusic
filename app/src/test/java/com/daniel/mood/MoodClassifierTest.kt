package com.daniel.mood

import org.junit.Assert.*
import org.junit.Test

class MoodClassifierTest {
    private fun features(
        bpm: Float = 100f, rms: Float = .08f, beat: Float = .5f,
        minor: Float = .5f, centroid: Float = 2200f, flux: Float = .3f,
        voice: Float = .5f
    ) = Features(
        bpm = bpm, beat = beat, rms = rms, dynamics = .08f, centroid = centroid,
        rolloff = 3000f, flux = flux, zcr = .1f, minor = minor, bass = .3f,
        onset = 3f, voice = voice, tonal = .6f
    )

    @Test fun predictionsAreFiniteAndInRequiredCategories() {
        val result = MoodClassifier.classify(features())
        assertTrue(result.mood in Moods.all)
        assertTrue(result.secondary in Moods.all)
        assertNotEquals(result.mood, result.secondary)
        assertTrue(result.confidence in .25f.. .94f)
    }

    @Test fun acousticFeaturesChangePredictionWithoutMetadata() {
        val softMinor = MoodClassifier.classify(features(bpm = 62f, rms = .025f, beat = .15f, minor = .9f, centroid = 1000f, flux = .08f))
        val loudFast = MoodClassifier.classify(features(bpm = 160f, rms = .19f, beat = .9f, minor = .1f, centroid = 4100f, flux = .65f))
        assertNotEquals(softMinor.mood, loudFast.mood)
        assertTrue(loudFast.mood == Moods.SPORT || loudFast.mood == Moods.ENERGETIC || loudFast.mood == Moods.HAPPY)
    }

    @Test fun subjectiveMoodsNeverLookCertain() {
        val rainyVector = features().learningVector()
        val rainy = MoodClassifier.classify(features(), listOf(LearningProfile(Moods.RAINY, 1000, FloatArray(rainyVector.size) { rainyVector[it] * 1000f })))
        if (rainy.mood == Moods.RAINY) assertTrue(rainy.confidence < MoodClassifier.REVIEW_THRESHOLD)
        val nostalgicVector = features().learningVector()
        val nostalgic = MoodClassifier.classify(features(), listOf(LearningProfile(Moods.NOSTALGIC, 1000, FloatArray(nostalgicVector.size) { nostalgicVector[it] * 1000f })))
        if (nostalgic.mood == Moods.NOSTALGIC) assertTrue(nostalgic.confidence < MoodClassifier.REVIEW_THRESHOLD)
    }

    @Test fun learnedCentroidChangesAutomaticClassification() {
        val f = features()
        val baseline = MoodClassifier.classify(f)
        val sums = FloatArray(f.learningVector().size) { f.learningVector()[it] * 1000f }
        val learned = MoodClassifier.classify(f, listOf(LearningProfile(Moods.INSTRUMENTAL, 1000, sums)))
        assertEquals(Moods.INSTRUMENTAL, learned.mood)
        assertTrue(baseline.mood in Moods.all)
    }

    @Test fun featureVectorRoundTripIsStable() {
        val original = features()
        val restored = Features.vector(original.serialize())
        assertNotNull(restored)
        assertArrayEquals(original.learningVector(), restored!!, .0001f)
    }

    @Test fun invalidLearningDataDoesNotCrashClassifier() {
        val result = MoodClassifier.classify(features(), listOf(LearningProfile(Moods.HAPPY, 2, floatArrayOf(1f))))
        assertTrue(result.mood in Moods.all)
    }
}
