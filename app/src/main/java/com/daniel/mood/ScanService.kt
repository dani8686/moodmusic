package com.daniel.mood

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

data class ScanProgress(
    val running: Boolean = false,
    val paused: Boolean = false,
    val done: Int = 0,
    val discovered: Int = 0,
    val current: String = "",
    val error: String = "",
    val started: Long = 0,
    val rescored: Int = 0
)

object ScanState {
    private val mutable = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = mutable

    @Synchronized
    fun update(change: (ScanProgress) -> ScanProgress) {
        mutable.value = change(mutable.value)
    }

    @Volatile
    var paused = false
}

fun cleanFilename(name: String): String = name.substringBeforeLast('.', name)
    .replace(Regex("(?i)(https?://)?(www\\.)?[a-z0-9-]+\\.(com|ir|net|org)"), " ")
    .replace(Regex("(?i)\\b\\d{2,4}\\s*(kbps|kb|k)\\b"), " ")
    .replace(Regex("[_\\-\\[\\]()]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .ifBlank { name }

private data class Input(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val size: Long,
    val modified: Long,
    val added: Long
)

/** Foreground, incremental, bounded-concurrency library analysis. */
class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private val channelId = "scan"

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, getString(R.string.scan_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                ScanState.paused = true
                ScanState.update { it.copy(paused = true) }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                ScanState.paused = false
                ScanState.update { it.copy(paused = false) }
                return START_NOT_STICKY
            }
            ACTION_CANCEL -> {
                job?.cancel()
                return START_NOT_STICKY
            }
        }
        if (job?.isActive == true) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, notification(""))
        ScanState.paused = false
        ScanState.update { ScanProgress(running = true, started = System.currentTimeMillis()) }
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MoodMusic:scan")
            .apply { acquire(2 * 60 * 60 * 1000L) }

        job = scope.launch {
            try {
                scan()
            } catch (_: CancellationException) {
                // Cancelled by the user: no partial prune, no data loss.
            } catch (e: Exception) {
                ScanState.update { it.copy(error = e.javaClass.simpleName) }
            } finally {
                ScanState.update { it.copy(running = false, paused = false) }
                try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) { }
                wake = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): android.app.Notification {
        val fa = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("fa", true)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(MoodCatalog.tr(fa, "Analyzing music", "در حال تحلیل موسیقی"))
            .setContentText(text.ifBlank { MoodCatalog.tr(fa, "Checking your library", "در حال بررسی کتابخانه") })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private suspend fun scan() = coroutineScope {
        val repository = MusicRepository(applicationContext)
        val dao = repository.songs
        val prototypes = repository.prototypes()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val minDuration = prefs.getInt("minSeconds", 30) * 1000L
        val accurate = prefs.getBoolean("accurate", false)
        val generation = System.currentTimeMillis()
        val finished = AtomicInteger(0)
        val found = AtomicInteger(0)
        val rescored = AtomicInteger(0)
        val queue = Channel<Input>(capacity = 12)

        // Hardware decoders are the bottleneck: 2-3 workers is faster and cooler
        // than one worker per core on Helio G95 class devices.
        val workers = min(3, max(1, Runtime.getRuntime().availableProcessors() - 1))
        val consumers = List(workers) {
            launch {
                for (input in queue) {
                    ensureActive()
                    while (ScanState.paused) {
                        delay(250)
                        ensureActive()
                    }
                    ScanState.update { it.copy(current = input.title) }
                    val existing = dao.get(input.id)
                    val song = try {
                        val features = AudioAnalysis.analyze(
                            applicationContext, Uri.parse(input.uri), input.duration, accurate
                        )
                        val prediction = MoodModel.classify(features, prototypes)
                        successSong(input, existing, prediction, features, generation)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val reason = when (e.message) {
                            "silent" -> "silent"
                            "short" -> "short"
                            "unsupported" -> "unsupported"
                            else -> "decode"
                        }
                        failedSong(input, existing, reason, generation)
                    }
                    dao.put(song)
                    val count = finished.incrementAndGet()
                    if (count % 5 == 0) {
                        ScanState.update { it.copy(done = count, rescored = rescored.get()) }
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification("$count / ${found.get()}"))
                    }
                }
            }
        }

        try {
            val columns = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATE_ADDED
            )
            val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = contentResolver.query(
                base, columns, "${MediaStore.Audio.Media.DURATION} >= ?",
                arrayOf(minDuration.toString()), null
            ) ?: throw IllegalStateException("MediaStore")
            cursor.use { c ->
                ScanState.update { it.copy(discovered = c.count) }
                val idIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val fileIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val modifiedIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val addedIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (c.moveToNext()) {
                    ensureActive()
                    while (ScanState.paused) {
                        delay(250)
                        ensureActive()
                    }
                    fun str(index: Int) = c.getString(index) ?: ""
                    val id = c.getLong(idIndex)
                    val file = str(fileIndex)
                    val rawTitle = str(titleIndex)
                    val title = if (
                        rawTitle.isBlank() || rawTitle == "<unknown>" ||
                        rawTitle.substringBeforeLast('.') == file.substringBeforeLast('.')
                    ) cleanFilename(file) else rawTitle
                    val input = Input(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id).toString(),
                        title = title,
                        artist = str(artistIndex).takeUnless { it == "<unknown>" } ?: "",
                        album = str(albumIndex).takeUnless { it == "<unknown>" } ?: "",
                        albumId = c.getLong(albumIdIndex),
                        duration = c.getLong(durationIndex),
                        size = c.getLong(sizeIndex),
                        modified = c.getLong(modifiedIndex),
                        added = c.getLong(addedIndex)
                    )
                    val existing = dao.get(id)
                    found.incrementAndGet()
                    val unchanged = existing != null &&
                        existing.size == input.size &&
                        existing.modified == input.modified &&
                        existing.duration == input.duration

                    if (unchanged && existing != null) {
                        val cached = MoodModel.decode(existing.featuresJson)
                        if (cached != null && existing.analysisVersion != MoodModel.MODEL_VERSION) {
                            // Model changed (new corrections / new version): re-score
                            // from the cached vector, without decoding audio again.
                            val prediction = MoodModel.classify(cached, prototypes)
                            dao.put(successSong(input, existing, prediction, cached, generation))
                            rescored.incrementAndGet()
                        } else if (existing.featuresJson.isBlank() && existing.reason.isBlank()) {
                            // Upgraded V1 row: decode once to populate the feature cache.
                            queue.send(input)
                        } else {
                            dao.touch(id, generation)
                        }
                        finished.incrementAndGet()
                    } else {
                        queue.send(input)
                    }
                    if (found.get() % 10 == 0) {
                        ScanState.update { it.copy(done = finished.get(), rescored = rescored.get()) }
                    }
                }
            }
            queue.close()
            consumers.joinAll()
            ensureActive()
            dao.prune(generation)
            ScanState.update {
                it.copy(discovered = found.get(), done = finished.get(), rescored = rescored.get())
            }
        } finally {
            queue.close()
        }
    }

    private fun successSong(
        input: Input,
        existing: Song?,
        prediction: MoodPrediction,
        features: FeatureVector,
        generation: Long
    ): Song {
        val manual = existing?.manualMood
        val secondary = if (prediction.mood == MoodCatalog.REVIEW) {
            prediction.suggested
        } else {
            prediction.secondary
        }
        return Song(
            id = input.id,
            uri = input.uri,
            title = input.title,
            artist = input.artist,
            album = input.album,
            albumId = input.albumId,
            duration = input.duration,
            size = input.size,
            modified = input.modified,
            added = input.added,
            mood = manual ?: prediction.mood,
            predictedMood = prediction.mood,
            manualMood = manual,
            secondary = secondary,
            confidence = prediction.confidence,
            bpm = features.bpm,
            energy = features.rms,
            reason = "",
            manual = manual != null,
            seen = generation,
            featuresJson = MoodModel.encode(features),
            analysisVersion = MoodModel.MODEL_VERSION
        )
    }

    private fun failedSong(input: Input, existing: Song?, reason: String, generation: Long): Song {
        val manual = existing?.manualMood
        return Song(
            id = input.id,
            uri = input.uri,
            title = input.title,
            artist = input.artist,
            album = input.album,
            albumId = input.albumId,
            duration = input.duration,
            size = input.size,
            modified = input.modified,
            added = input.added,
            mood = manual ?: MoodCatalog.REVIEW,
            predictedMood = MoodCatalog.REVIEW,
            manualMood = manual,
            secondary = MoodCatalog.REVIEW,
            confidence = 0f,
            bpm = 0f,
            energy = 0f,
            reason = reason,
            manual = manual != null,
            seen = generation,
            featuresJson = "",
            analysisVersion = 0
        )
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "com.daniel.mood.SCAN_PAUSE"
        const val ACTION_RESUME = "com.daniel.mood.SCAN_RESUME"
        const val ACTION_CANCEL = "com.daniel.mood.SCAN_CANCEL"
        private const val NOTIFICATION_ID = 41
    }
}
