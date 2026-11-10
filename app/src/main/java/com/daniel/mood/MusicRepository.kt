package com.daniel.mood

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

data class LearningReport(
    val corrections: Int,
    val prototypes: Int,
    val rescored: Int
)

data class QueueSnapshot(val items: List<QueueItem>, val state: QueueState?)

/**
 * Single place for database mutations that involve more than one table.
 * Everything runs on Dispatchers.IO; the UI never touches Room synchronously.
 */
class MusicRepository(context: Context) {
    private val app = context.applicationContext
    private val db = MoodDatabase.get(app)

    val songs = db.songs()
    val corrections = db.corrections()
    val prototypeRows = db.prototypes()
    val playlists = db.playlists()
    val queue = db.queue()

    suspend fun prototypes(): List<Prototype> = withContext(Dispatchers.IO) {
        prototypeRows.all().mapNotNull { row ->
            val vector = MoodModel.decodeVector(row.centroidJson) ?: return@mapNotNull null
            Prototype(row.mood, vector, row.samples)
        }
    }

    /** Manual correction: persist it separately, then genuinely update the model. */
    suspend fun setManualMood(song: Song, mood: Int): LearningReport = withContext(Dispatchers.IO) {
        val features = song.featuresJson.takeIf { it.isNotBlank() } ?: run {
            try {
                MoodModel.encode(AudioAnalysis.analyze(app, Uri.parse(song.uri), song.duration, false))
            } catch (_: Exception) {
                ""
            }
        }
        if (features.isNotBlank() && song.featuresJson.isBlank()) {
            songs.setFeatures(song.id, features)
        }
        corrections.upsert(
            MoodCorrection(
                songId = song.id,
                uri = song.uri,
                predictedMood = song.predictedMood,
                correctedMood = mood,
                modelConfidence = song.confidence,
                correctedAt = System.currentTimeMillis(),
                featuresJson = features
            )
        )
        songs.correct(song.id, mood)
        rebuildLearning()
    }

    /** Removes the correction; the automatic prediction becomes visible again. */
    suspend fun restoreAutomatic(song: Song): LearningReport = withContext(Dispatchers.IO) {
        corrections.deleteForSong(song.id)
        songs.restoreAuto(song.id)
        rebuildLearning()
    }

    /**
     * Rebuild nearest-prototype centroids from all corrections and re-score the
     * whole library from cached feature vectors (no audio decoding).
     */
    suspend fun rebuildLearning(): LearningReport = withContext(Dispatchers.IO) {
        val records = corrections.all()
        val samples = records.mapNotNull { record ->
            MoodModel.decode(record.featuresJson)?.let { record.correctedMood to it }
        }
        val prototypes = MoodModel.buildPrototypes(samples)
        val now = System.currentTimeMillis()
        val rows = prototypes.map {
            MoodPrototype(it.mood, MoodModel.encodeVector(it.centroid), it.samples, now)
        }
        db.withTransaction {
            prototypeRows.clear()
            prototypeRows.insertAll(rows)
        }
        val rescored = rescoreCached(prototypes)
        LearningReport(records.size, prototypes.size, rescored)
    }

    suspend fun rescoreCached(): Int = withContext(Dispatchers.IO) {
        rescoreCached(prototypes())
    }

    private suspend fun rescoreCached(prototypes: List<Prototype>): Int {
        val all = songs.withFeatures()
        var changed = 0
        for (chunk in all.chunked(200)) {
            val updated = chunk.mapNotNull { song ->
                val features = MoodModel.decode(song.featuresJson) ?: return@mapNotNull null
                val prediction = MoodModel.classify(features, prototypes)
                val secondary = if (prediction.mood == MoodCatalog.REVIEW) {
                    prediction.suggested
                } else {
                    prediction.secondary
                }
                song.copy(
                    predictedMood = prediction.mood,
                    secondary = secondary,
                    confidence = prediction.confidence,
                    bpm = features.bpm,
                    energy = features.rms,
                    reason = "",
                    analysisVersion = MoodModel.MODEL_VERSION,
                    mood = song.manualMood ?: prediction.mood,
                    manual = song.manualMood != null
                )
            }
            if (updated.isNotEmpty()) {
                songs.updateAll(updated)
                changed += updated.size
            }
            yield()
        }
        return changed
    }

    // ---- Playlists ---------------------------------------------------------

    suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) {
        playlists.create(Playlist(name = name.trim().ifBlank { "Playlist" }, createdAt = System.currentTimeMillis()))
    }

    suspend fun renamePlaylist(id: Long, name: String) = withContext(Dispatchers.IO) {
        playlists.rename(id, name.trim().ifBlank { "Playlist" })
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            playlists.clearSongs(id)
            playlists.delete(id)
        }
    }

    suspend fun addToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlists.add(PlaylistSong(playlistId, songId, System.currentTimeMillis()))
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        playlists.remove(playlistId, songId)
    }

    // ---- Persisted playback queue -----------------------------------------

    suspend fun saveQueue(items: List<QueueItem>, state: QueueState) = withContext(Dispatchers.IO) {
        db.withTransaction {
            queue.clear()
            queue.insertAll(items)
            queue.saveState(state)
        }
    }

    suspend fun restoreQueue(): QueueSnapshot = withContext(Dispatchers.IO) {
        QueueSnapshot(queue.items(), queue.state())
    }
}
