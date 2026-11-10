package com.daniel.mood

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Measurable properties of three decoded PCM slices of a track.
 *
 * Nothing here comes from file names, folders, ID3 tags or network services.
 * "minor" is a chroma-profile estimate, "vocal" a vocal-band modulation
 * estimate, "sustained" a smoothness estimate - all heuristic approximations.
 */
data class FeatureVector(
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
    val vocal: Float,
    val sustained: Float
)

/** A learned class centroid built from the user's manual corrections. */
data class Prototype(val mood: Int, val centroid: FloatArray, val samples: Int)

data class MoodPrediction(
    val mood: Int,
    val secondary: Int,
    val confidence: Float,
    /** Best candidate even when confidence is too low for a definitive label. */
    val suggested: Int,
    val scores: FloatArray
)

/**
 * On-device mood classifier.
 *
 * Stage 1: hand-designed acoustic scoring over 12 mood families. Deliberately
 * multivariate: tempo alone never selects a mood, and bright/fast/minor
 * combinations separate happy from energetic from sad.
 *
 * Stage 2: a real feedback mechanism. Every manual correction contributes a
 * normalised feature vector to a per-mood centroid (a nearest-prototype model).
 * Classification adds a similarity-weighted boost for moods the user has
 * taught it. Because features are cached in Room, the whole library can be
 * re-scored from those vectors without decoding audio again.
 *
 * Scores are *not* calibrated probabilities. Low-confidence results are
 * reported as "Needs review" instead of being forced into a definitive label.
 */
object MoodModel {
    /** Increment when scoring changes: triggers a cache-only re-score on next scan. */
    const val MODEL_VERSION = 4
    const val REVIEW_THRESHOLD = 0.45f
    private const val DIM = 13

    fun normalized(f: FeatureVector): FloatArray = floatArrayOf(
        (f.bpm / 180f).coerceIn(0f, 1f),
        f.beat.coerceIn(0f, 1f),
        (f.rms * 6f).coerceIn(0f, 1f),
        (f.dynamics * 4f).coerceIn(0f, 1f),
        (f.centroid / 4500f).coerceIn(0f, 1f),
        (f.rolloff / 8000f).coerceIn(0f, 1f),
        (f.flux * 5f).coerceIn(0f, 1f),
        (f.zcr * 12f).coerceIn(0f, 1f),
        f.minor.coerceIn(0f, 1f),
        (f.bass * 2f).coerceIn(0f, 1f),
        (f.onset / 12f).coerceIn(0f, 1f),
        f.vocal.coerceIn(0f, 1f),
        f.sustained.coerceIn(0f, 1f)
    )

    private fun triangular(value: Float, center: Float, width: Float): Float =
        (1f - abs(value - center) / width).coerceIn(0f, 1f)

    /** Baseline acoustic scores, indices are MoodCatalog ids 0..11. */
    fun baseScores(f: FeatureVector): FloatArray {
        val v = normalized(f)
        val tempo = v[0]
        val pulse = v[1]
        val energy = v[2]
        val dynamics = v[3]
        val bright = v[4]
        val flux = v[6]
        val minor = v[8]
        val bass = v[9]
        val onset = v[10]
        val vocal = v[11]
        val sustained = v[12]

        val fast = ((tempo - 0.32f) / 0.48f).coerceIn(0f, 1f)
        val slow = 1f - fast
        val soft = 1f - energy
        val major = 1f - minor
        val dark = 1f - bright
        val steady = 1f - flux
        val warm = (bass * 0.6f + dark * 0.4f).coerceIn(0f, 1f)
        val midTempo = triangular(tempo, 0.50f, 0.38f)
        val smooth = (sustained * 0.6f + steady * 0.4f).coerceIn(0f, 1f)

        return floatArrayOf(
            // 0 Happy: bright, major-leaning, danceable, medium-to-fast, not too dark
            0.23f * energy + 0.21f * major + 0.18f * bright +
                0.17f * triangular(tempo, 0.62f, 0.40f) + 0.15f * pulse +
                0.06f * (1f - dynamics),
            // 1 Sad: strongly minor and slow - deliberately distinct from the
            // other quiet moods so a slow song is not automatically "sad".
            0.40f * minor + 0.28f * slow + 0.12f * soft +
                0.10f * vocal + 0.06f * sustained + 0.04f * dark,
            // 2 Calm: quiet, spectrally steady, sustained, few onsets
            0.27f * soft + 0.23f * steady + 0.18f * sustained +
                0.16f * (1f - onset) + 0.10f * (1f - dynamics) + 0.06f * smooth,
            // 3 Rainy: dark, soft, sustained, weak pulse, textured but not busy
            0.24f * dark + 0.20f * soft + 0.18f * sustained +
                0.14f * (1f - pulse) + 0.12f * minor + 0.12f * (1f - dynamics),
            // 4 Nostalgic: warm low-mid, moderate tempo, vocal, slightly dark
            0.22f * warm + 0.18f * midTempo + 0.17f * vocal +
                0.15f * sustained + 0.14f * dark + 0.14f * minor,
            // 5 Romantic: voice-forward, warm, sustained, gentle tempo
            0.26f * vocal + 0.20f * warm + 0.18f * sustained +
                0.18f * midTempo + 0.10f * soft + 0.08f * minor,
            // 6 Sport: strong regular pulse, fast, loud, bass-driven, many onsets
            0.29f * pulse + 0.23f * fast + 0.19f * energy +
                0.15f * bass + 0.14f * onset,
            // 7 Energetic: loud, wide dynamics, bright, high spectral change
            0.27f * energy + 0.21f * dynamics + 0.18f * bright +
                0.18f * flux + 0.16f * fast,
            // 8 Emotional: wide dynamics, voice, minor-leaning, expressive
            0.25f * dynamics + 0.22f * vocal + 0.18f * minor +
                0.18f * sustained + 0.17f * energy,
            // 9 Instrumental: little vocal-band modulation, tonal/sustained, steady
            0.36f * (1f - vocal) + 0.20f * sustained + 0.16f * (1f - onset) +
                0.14f * steady + 0.14f * smooth,
            // 10 Focus: steady, low vocal, quiet, sustained, little change
            0.27f * steady + 0.22f * (1f - vocal) + 0.19f * sustained +
                0.17f * soft + 0.15f * (1f - dynamics),
            // 11 Night: dark, slow, soft, minor-leaning, sustained
            0.26f * dark + 0.22f * soft + 0.20f * slow +
                0.17f * sustained + 0.15f * minor
        )
    }

    /** Build class centroids from (corrected mood, features) pairs. */
    fun buildPrototypes(samples: List<Pair<Int, FeatureVector>>): List<Prototype> {
        val groups = LinkedHashMap<Int, MutableList<FloatArray>>()
        for ((mood, features) in samples) {
            if (mood !in MoodCatalog.HAPPY..MoodCatalog.NIGHT) continue
            groups.getOrPut(mood) { ArrayList() }.add(normalized(features))
        }
        return groups.map { (mood, vectors) ->
            val centroid = FloatArray(DIM)
            for (vector in vectors) {
                for (i in 0 until DIM) centroid[i] += vector[i] / vectors.size
            }
            Prototype(mood, centroid, vectors.size)
        }
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in 0 until DIM) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum / DIM)
    }

    /** Acoustic baseline + learned centroid boost. */
    fun scoresWithFeedback(f: FeatureVector, prototypes: List<Prototype>): FloatArray {
        val scores = baseScores(f)
        if (prototypes.isEmpty()) return scores
        val vector = normalized(f)
        for (p in prototypes) {
            if (p.mood !in scores.indices || p.samples <= 0 || p.centroid.size != DIM) continue
            val similarity = (1f - distance(vector, p.centroid) / 1.15f).coerceIn(0f, 1f)
            val weight = (0.065f * p.samples).coerceAtMost(0.32f)
            scores[p.mood] += weight * similarity
        }
        return scores
    }

    fun classify(f: FeatureVector, prototypes: List<Prototype> = emptyList()): MoodPrediction {
        val scores = scoresWithFeedback(f, prototypes)
        val order = scores.indices.sortedByDescending { scores[it] }
        val top = order[0]
        val runnerUp = order[1]
        val margin = (scores[top] - scores[runnerUp]).coerceAtLeast(0f)
        val close = scores.count { scores[top] - it < 0.08f }
        val ambiguity = ((close - 1).coerceAtLeast(0)) / 11f
        val learnedForTop = prototypes.firstOrNull { it.mood == top }?.samples ?: 0
        var confidence = 0.36f + margin * 2.60f - ambiguity * 0.40f +
            (f.beat.coerceIn(0f, 1f)) * 0.09f
        if (MoodCatalog.isSubjective(top) && learnedForTop < 2) confidence -= 0.07f
        confidence = confidence.coerceIn(0.05f, 0.95f)
        val mood = if (confidence < REVIEW_THRESHOLD) MoodCatalog.REVIEW else top
        return MoodPrediction(mood, runnerUp, confidence, top, scores)
    }

    // ---- Compact, dependency-free persistence of feature vectors -------------

    fun encode(f: FeatureVector): String = join(
        floatArrayOf(
            f.bpm, f.beat, f.rms, f.dynamics, f.centroid, f.rolloff, f.flux,
            f.zcr, f.minor, f.bass, f.onset, f.vocal, f.sustained
        )
    )

    fun decode(text: String?): FeatureVector? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(',')
        if (parts.size != DIM) return null
        val values = FloatArray(DIM)
        for (i in 0 until DIM) {
            values[i] = parts[i].toFloatOrNull() ?: return null
        }
        return FeatureVector(
            values[0], values[1], values[2], values[3], values[4], values[5],
            values[6], values[7], values[8], values[9], values[10], values[11], values[12]
        )
    }

    fun encodeVector(v: FloatArray): String = join(v)

    fun decodeVector(text: String?): FloatArray? {
        if (text.isNullOrBlank()) return null
        val parts = text.split(',')
        if (parts.size != DIM) return null
        val values = FloatArray(DIM)
        for (i in 0 until DIM) values[i] = parts[i].toFloatOrNull() ?: return null
        return values
    }

    private fun join(v: FloatArray): String = v.joinToString(",") { it.toString() }
}
