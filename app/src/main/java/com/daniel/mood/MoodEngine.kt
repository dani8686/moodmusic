package com.daniel.mood

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Stable IDs stored in the database. No title, artist, path or filename is used by the classifier. */
object Moods {
    const val HAPPY = 0
    const val SAD = 1
    const val CALM = 2
    const val RAINY = 3
    const val NOSTALGIC = 4
    const val ROMANTIC = 5
    const val SPORT = 6
    const val ENERGETIC = 7
    const val EMOTIONAL = 8
    const val INSTRUMENTAL = 9
    const val UNSORTED = 10
    val all = listOf(HAPPY, SAD, CALM, RAINY, NOSTALGIC, ROMANTIC, SPORT, ENERGETIC, EMOTIONAL, INSTRUMENTAL)
    val english = listOf("Happy", "Sad", "Calm", "Rainy", "Nostalgic", "Romantic", "Sport", "Energetic", "Emotional", "Instrumental", "Needs analysis")
    val persian = listOf("شاد", "غمگین", "آرام", "بارونی", "نوستالژیک", "عاشقانه", "ورزشی", "پرانرژی", "احساسی", "بی‌کلام", "نیازمند تحلیل")
    val symbols = listOf("☀", "☂", "≈", "☔", "◷", "♥", "⚡", "✦", "♫", "𝄞", "?")
    fun name(id: Int, fa: Boolean) = (if (fa) persian else english).getOrElse(id) { "?" }
}

/** Measurable, normalized audio features. [voice] is a vocal-likelihood proxy, not speech recognition. */
data class Features(
    val bpm: Float,
    val beat: Float,
    val rms: Float,
    val dynamics: Float,
    val centroid: Float,
    val rolloff: Float,
    val flux: Float,
    val zcr: Float,
    val minor: Float,
    val bass: Float,
    val onset: Float,
    val voice: Float = 0.5f,
    val tonal: Float = 0.5f
) {
    fun learningVector(): FloatArray {
        val energy = (rms * 5f).coerceIn(0f, 1f)
        val speed = ((bpm - 55f) / 125f).coerceIn(0f, 1f)
        return floatArrayOf(
            energy, speed, beat.coerceIn(0f, 1f), (dynamics * 5f).coerceIn(0f, 1f),
            (centroid / 4500f).coerceIn(0f, 1f), flux.coerceIn(0f, 1f),
            minor.coerceIn(0f, 1f), bass.coerceIn(0f, 1f),
            (onset / 8f).coerceIn(0f, 1f), voice.coerceIn(0f, 1f), tonal.coerceIn(0f, 1f)
        )
    }

    fun serialize(): String = learningVector().joinToString(",") { "%.5f".format(java.util.Locale.US, it) }

    companion object {
        fun vector(value: String): FloatArray? = try {
            value.split(',').map { it.toFloat() }.toFloatArray().takeIf { it.size == 11 }
        } catch (_: Exception) { null }
    }
}

data class LearningProfile(val mood: Int, val count: Int, val sums: FloatArray)
data class Result(val mood: Int, val secondary: Int, val confidence: Float, val features: Features)

/**
 * Conservative signal model plus an online, persistent centroid model. Every manual correction
 * updates one centroid. This is genuine adaptive learning, deliberately bounded to avoid a few
 * corrections overwhelming acoustic evidence. Rainy/nostalgic remain intrinsically uncertain.
 */
object MoodClassifier {
    const val REVIEW_THRESHOLD = 0.58f

    fun classify(f: Features, profiles: List<LearningProfile> = emptyList()): Result {
        val e = (f.rms * 5f).coerceIn(0f, 1f)
        val fast = ((f.bpm - 55f) / 125f).coerceIn(0f, 1f)
        val bright = (f.centroid / 4500f).coerceIn(0f, 1f)
        val pulse = f.beat.coerceIn(0f, 1f)
        val soft = 1f - e
        val slow = 1f - fast
        val minor = f.minor.coerceIn(0f, 1f)
        val major = 1f - minor
        val smooth = 1f - f.flux.coerceIn(0f, 1f)
        val fewOnsets = 1f - (f.onset / 8f).coerceIn(0f, 1f)
        val dynamic = (f.dynamics * 5f).coerceIn(0f, 1f)
        val steadyDynamics = 1f - dynamic
        val tonal = f.tonal.coerceIn(0f, 1f)
        val voice = f.voice.coerceIn(0f, 1f)
        val midTempo = 1f - (abs(fast - 0.42f) * 2.2f).coerceIn(0f, 1f)
        val scores = floatArrayOf(
            .23f*e + .19f*fast + .15f*bright + .15f*major + .13f*pulse + .15f*(1f-fewOnsets),
            .22f*soft + .18f*slow + .25f*minor + .12f*steadyDynamics + .13f*(1f-bright) + .10f*fewOnsets,
            .28f*soft + .20f*slow + .17f*smooth + .15f*fewOnsets + .10f*steadyDynamics + .10f*tonal,
            .20f*soft + .15f*slow + .15f*minor + .15f*(1f-bright) + .15f*fewOnsets + .10f*midTempo + .10f*(1f-f.bass.coerceIn(0f,1f)),
            .18f*soft + .16f*slow + .18f*minor + .15f*(1f-bright) + .12f*smooth + .11f*tonal + .10f*dynamic,
            .24f*soft + .15f*midTempo + .15f*major + .12f*smooth + .14f*tonal + .10f*voice + .10f*fewOnsets,
            .24f*e + .25f*fast + .20f*pulse + .15f*(1f-fewOnsets) + .10f*f.bass.coerceIn(0f,1f) + .06f*bright,
            .30f*e + .20f*fast + .18f*bright + .12f*f.flux.coerceIn(0f,1f) + .10f*pulse + .10f*f.bass.coerceIn(0f,1f),
            .20f*dynamic + .20f*voice + .16f*minor + .12f*midTempo + .12f*soft + .10f*tonal + .10f*smooth,
            .25f*(1f-voice) + .20f*tonal + .15f*smooth + .15f*dynamic + .15f*midTempo + .10f*soft
        )
        // Subjective labels should not win merely because of one slow/minor song.
        scores[Moods.RAINY] *= .91f
        scores[Moods.NOSTALGIC] *= .94f
        scores[Moods.INSTRUMENTAL] *= if (voice < .3f) 1f else .82f

        val vector = f.learningVector()
        for (profile in profiles) {
            if (profile.mood !in Moods.all || profile.count <= 0 || profile.sums.size != vector.size) continue
            var distance = 0f
            for (i in vector.indices) {
                val delta = vector[i] - profile.sums[i] / profile.count
                distance += delta * delta
            }
            val similarity = exp(-2.2f * sqrt(distance)).coerceIn(0f, 1f)
            val trust = profile.count.toFloat() / (profile.count + 5f)
            val weight = .12f + .32f * trust
            scores[profile.mood] = scores[profile.mood] * (1f - weight) + similarity * weight
        }
        val order = scores.indices.sortedByDescending { scores[it] }
        val winner = order[0]
        val margin = (scores[winner] - scores[order[1]]).coerceAtLeast(0f)
        var confidence = (.35f + margin * 1.9f + scores[winner] * .25f + pulse * .05f).coerceIn(.25f, .94f)
        if (winner == Moods.RAINY || winner == Moods.NOSTALGIC) confidence = confidence.coerceAtMost(.57f)
        if (winner == Moods.INSTRUMENTAL && voice in .3f.. .65f) confidence = confidence.coerceAtMost(.56f)
        return Result(winner, order[1], confidence, f)
    }
}
