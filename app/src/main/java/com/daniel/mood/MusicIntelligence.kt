package com.daniel.mood

import kotlin.math.*

private fun parseVector(value: String, size: Int): FloatArray {
    val result = FloatArray(size)
    if (value.isBlank()) return result
    return try {
        val parts = value.split(',')
        for (i in 0 until minOf(size, parts.size)) result[i] = parts[i].toFloat()
        result
    } catch (_: Exception) { result }
}
private fun FloatArray.storage(): String = joinToString(",") { "%.5f".format(java.util.Locale.US, it) }

data class TasteSnapshot(
    val features: FloatArray, val moods: FloatArray, val totalWeight: Float,
    val eventCount: Long, val updatedAt: Long
) {
    val averageEnergy: Float get() = features.getOrElse(0) { 0f }
    val averageTempo: Float get() = 55f + features.getOrElse(1) { 0f } * 125f
    val vocalPreference: Float get() = features.getOrElse(9) { .5f }
}

/** Incremental, behavior-weighted Music DNA. Short/accidental plays carry almost no weight. */
object TasteEngine {
    fun snapshot(profile: TasteProfile?): TasteSnapshot {
        if (profile == null || profile.totalWeight <= .0001f) return TasteSnapshot(FloatArray(11), FloatArray(10), 0f, 0, 0)
        val features = parseVector(profile.featureSums, 11)
        val moods = parseVector(profile.moodSums, 10)
        for (i in features.indices) features[i] = (features[i] / profile.totalWeight).coerceIn(0f, 1f)
        return TasteSnapshot(features, moods, profile.totalWeight, profile.eventCount, profile.updatedAt)
    }

    fun applyEvent(profile: TasteProfile?, song: Song, event: ListeningEvent): TasteProfile {
        val old = snapshot(profile)
        val vector = Features.vector(song.featureData) ?: return profile ?: TasteProfile(
            featureSums = FloatArray(11).storage(), moodSums = FloatArray(10).storage(),
            totalWeight = 0f, eventCount = 0, version = 1, updatedAt = System.currentTimeMillis()
        )
        val listenedMinutes = (event.listenedMs / 60000f).coerceIn(0f, 10f)
        val completion = event.completionRatio.coerceIn(0f, 1.2f)
        // Listening time is evidence, not proof of liking. Skips under 20% do not add taste weight.
        val weight = if (event.skipped && completion < .2f) 0f else listenedMinutes * completion * completion
        val total = (profile?.totalWeight ?: 0f) * .9995f + weight
        val sums = parseVector(profile?.featureSums ?: "", 11)
        val moods = parseVector(profile?.moodSums ?: "", 10)
        val oldWeight = (profile?.totalWeight ?: 0f) * .9995f
        for (i in sums.indices) sums[i] = sums[i] * .9995f + vector[i] * weight
        if (song.mood in Moods.all) moods[song.mood] = moods[song.mood] * .9995f + weight
        // Explicit likes are stronger evidence; dislikes reduce only accumulated sums, never fabricate plays.
        if (song.feedback != 0) {
            val explicit = song.feedback.coerceIn(-1, 1) * .35f
            for (i in sums.indices) sums[i] = (sums[i] + vector[i] * explicit).coerceAtLeast(0f)
            if (song.mood in Moods.all) moods[song.mood] = (moods[song.mood] + explicit).coerceAtLeast(0f)
        }
        return TasteProfile(
            featureSums = sums.storage(), moodSums = moods.storage(), totalWeight = total.coerceAtLeast(.0001f),
            eventCount = (profile?.eventCount ?: 0) + 1, version = 1, updatedAt = System.currentTimeMillis()
        )
    }
}

data class ScoredSong(val song: Song, val score: Float, val reason: String)

object RadarEngine {
    fun recommend(
        songs: List<Song>, profile: TasteProfile?, context: Song? = null,
        recentEvents: List<ListeningEvent> = emptyList(), limit: Int = 30,
        discover: Boolean = false, excludedIds: Set<Long> = emptySet()
    ): List<ScoredSong> {
        val dna = TasteEngine.snapshot(profile)
        val contextVector = context?.let { Features.vector(it.featureData) }
        val playCounts = recentEvents.groupingBy { it.songId }.eachCount()
        return songs.asSequence().filter { it.id !in excludedIds && it.id != context?.id && Features.vector(it.featureData) != null }
            .map { song ->
                val vector = Features.vector(song.featureData)!!
                var dnaDistance = 0f
                for (i in vector.indices) {
                    val delta = vector[i] - dna.features[i]
                    dnaDistance += delta * delta
                }
                val dnaSimilarity = if (dna.totalWeight > 0) exp(-2.4f * sqrt(dnaDistance)) else .45f
                val contextSimilarity = if (contextVector != null) {
                    var distance = 0f
                    for (i in vector.indices) { val d = vector[i] - contextVector[i]; distance += d * d }
                    exp(-3f * sqrt(distance))
                } else .45f
                val plays = playCounts[song.id] ?: 0
                val forgotten = if (plays == 0) .12f else if (plays <= 2) .05f else 0f
                val familiarity = if (discover) forgotten else (1f - exp(-plays / 3f)) * .08f
                val moodFit = if (context != null && song.mood == context.mood) .08f else 0f
                val explicit = song.feedback * .16f
                val score = (.42f * dnaSimilarity + .38f * contextSimilarity + forgotten + familiarity + moodFit + explicit).coerceIn(0f, 1f)
                val reason = when {
                    context != null && contextSimilarity > .78f -> "similar_audio"
                    song.feedback > 0 -> "liked_characteristics"
                    plays == 0 -> "unheard_dna_match"
                    dnaSimilarity > .75f -> "preferred_energy_rhythm"
                    else -> "mood_audio_match"
                }
                ScoredSong(song, score, reason)
            }
            .sortedByDescending { it.score }.take(limit).toList()
    }
}

object MoodGraph {
    private val edges = mapOf(
        Moods.SAD to listOf(Moods.NOSTALGIC, Moods.EMOTIONAL, Moods.CALM, Moods.RAINY),
        Moods.RAINY to listOf(Moods.SAD, Moods.NOSTALGIC, Moods.CALM),
        Moods.NOSTALGIC to listOf(Moods.EMOTIONAL, Moods.CALM, Moods.ROMANTIC, Moods.SAD),
        Moods.EMOTIONAL to listOf(Moods.SAD, Moods.NOSTALGIC, Moods.ROMANTIC, Moods.CALM),
        Moods.CALM to listOf(Moods.ROMANTIC, Moods.EMOTIONAL, Moods.HAPPY, Moods.INSTRUMENTAL),
        Moods.ROMANTIC to listOf(Moods.CALM, Moods.EMOTIONAL, Moods.HAPPY),
        Moods.HAPPY to listOf(Moods.ENERGETIC, Moods.SPORT, Moods.ROMANTIC, Moods.CALM),
        Moods.ENERGETIC to listOf(Moods.HAPPY, Moods.SPORT, Moods.EMOTIONAL),
        Moods.SPORT to listOf(Moods.ENERGETIC, Moods.HAPPY),
        Moods.INSTRUMENTAL to listOf(Moods.CALM, Moods.NOSTALGIC, Moods.EMOTIONAL)
    )

    fun path(start: Int, end: Int): List<Int> {
        if (start == end) return listOf(start)
        val queue = ArrayDeque<List<Int>>()
        val visited = mutableSetOf(start)
        queue.add(listOf(start))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in edges[current.last()].orEmpty() + Moods.all) {
                if (next in visited) continue
                val path = current + next
                if (next == end) return path
                visited += next; queue.add(path)
            }
        }
        return listOf(start, end)
    }
}

data class JourneyConfig(
    val startMood: Int, val endMood: Int, val minutes: Int,
    val energy: Float, val familiarity: Float, val allowedMoods: Set<Int> = Moods.all.toSet()
)

object JourneyEngine {
    fun generate(songs: List<Song>, profile: TasteProfile?, config: JourneyConfig): List<Song> {
        val path = MoodGraph.path(config.startMood, config.endMood)
        val targetMs = config.minutes * 60000L
        val available = songs.filter { Features.vector(it.featureData) != null && it.duration > 0 }
        if (available.isEmpty()) return emptyList()
        val dna = TasteEngine.snapshot(profile)
        val selected = mutableListOf<Song>()
        val used = mutableSetOf<Long>()
        var total = 0L
        var step = 0
        while (total < targetMs && used.size < available.size) {
            val progress = (total.toFloat() / targetMs).coerceIn(0f, 1f)
            val pathIndex = (progress * (path.size - 1)).roundToInt().coerceIn(0, path.lastIndex)
            val targetMood = path[pathIndex]
            val targetEnergy = config.energy * progress + (1f - config.energy) * (1f - progress)
            val previous = selected.lastOrNull()
            val candidate = available.asSequence().filter { it.id !in used }.map { song ->
                val vector = Features.vector(song.featureData)!!
                val energy = (song.energy * 5f).coerceIn(0f, 1f)
                val moodDistance = if (song.mood == targetMood) 0f else if (song.mood in config.allowedMoods) .22f else .48f
                var dnaDistance = 0f
                for (i in vector.indices) { val d = vector[i] - dna.features[i]; dnaDistance += d * d }
                val dnaFit = if (dna.totalWeight > 0) exp(-2f * sqrt(dnaDistance)) else .5f
                val continuity = previous?.let {
                    abs(energy - (it.energy * 5f).coerceIn(0f, 1f)) * .24f +
                        (if (it.artist.isNotBlank() && it.artist == song.artist) .12f else 0f) +
                        (if (it.bpm > 0 && song.bpm > 0) (abs(it.bpm - song.bpm) / 160f).coerceIn(0f, .15f) else 0f)
                } ?: 0f
                val familiarity = if (config.familiarity >= .5f) song.feedback * .08f else (if (song.feedback == 0) .05f else 0f)
                val score = dnaFit * .24f + familiarity - moodDistance - abs(energy - targetEnergy) * .28f - continuity
                ScoredSong(song, score, "journey")
            }.maxByOrNull { it.score }?.song ?: break
            selected += candidate; used += candidate.id; total += candidate.duration; step++
            // Safety against pathological duration metadata.
            if (step > available.size) break
        }
        if (selected.isNotEmpty()) {
            val first = available.filter { it.mood == config.startMood && it !in selected.drop(1) }.minByOrNull { abs(it.energy - config.energy) }
            if (first != null) { selected.remove(first); selected.add(0, first) }
            val last = available.filter { it.mood == config.endMood && it !in selected.dropLast(1) }.minByOrNull { abs(it.energy - config.energy) }
            if (last != null) { selected.remove(last); selected.add(last) }
        }
        return selected
    }

    fun replace(current: List<Song>, index: Int, songs: List<Song>, profile: TasteProfile?, config: JourneyConfig): List<Song> {
        if (index !in current.indices) return current
        val candidates = songs.filter { candidate -> current.none { it.id == candidate.id } }
        val replacement = generate(candidates, profile, config).firstOrNull() ?: return current
        return current.toMutableList().apply { this[index] = replacement }
    }
}

data class DjConfig(
    val mode: String = "party", val bpmMatching: Boolean = true,
    val energyMatching: Boolean = true, val avoidSimilar: Boolean = true,
    val preferFamiliar: Boolean = true, val allowDiscovery: Boolean = true,
    val noRepeatWindow: Int = 30
)

object SmartDjSelector {
    fun next(
        songs: List<Song>, current: Song?, profile: TasteProfile?,
        recentIds: List<Long>, config: DjConfig
    ): Song? {
        val targetMood = when (config.mode) {
            "chill", "night" -> Moods.CALM; "emotional" -> Moods.EMOTIONAL
            "workout" -> Moods.SPORT; "nostalgic" -> Moods.NOSTALGIC
            "party" -> Moods.ENERGETIC; else -> -1
        }
        val excluded = recentIds.takeLast(config.noRepeatWindow).toSet()
        val candidates = songs.filter { it.id != current?.id && it.id !in excluded && (config.allowDiscovery || it.feedback >= 0) }
        return candidates.maxByOrNull { song ->
            var score = 0f
            if (targetMood >= 0) score += if (song.mood == targetMood) .6f else if (song.secondary == targetMood) .2f else 0f
            if (current != null && config.bpmMatching && current.bpm > 0 && song.bpm > 0)
                score += (1f - (abs(current.bpm - song.bpm) / 80f).coerceIn(0f, 1f)) * .45f
            if (current != null && config.energyMatching)
                score += (1f - (abs(current.energy - song.energy) * 5f).coerceIn(0f, 1f)) * .3f
            if (config.avoidSimilar && current != null) score += if (song.artist == current.artist) -.25f else .08f
            if (config.preferFamiliar) score += song.feedback * .12f
            if (config.mode == "discovery") score += if (song.added < System.currentTimeMillis() / 1000 - 86400 * 30) .15f else 0f
            score
        }
    }
}
