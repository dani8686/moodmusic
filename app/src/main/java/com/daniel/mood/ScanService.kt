package com.daniel.mood

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

data class ScanProgress(
    val running: Boolean = false, val paused: Boolean = false, val done: Int = 0,
    val discovered: Int = 0, val analyzed: Int = 0, val skipped: Int = 0,
    val current: String = "", val error: String = "", val started: Long = 0
)
object ScanState {
    private val mutable = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = mutable
    @Synchronized fun update(change: (ScanProgress) -> ScanProgress) { mutable.value = change(mutable.value) }
    @Volatile var paused = false
}
private data class Input(
    val id: Long, val uri: String, val title: String, val artist: String, val album: String,
    val albumId: Long, val duration: Long, val size: Long, val modified: Long, val added: Long
)

class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private val channelId = "scan"

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(channelId, "Analysis / تحلیل", NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause" -> { ScanState.paused = true; ScanState.update { it.copy(paused = true) }; return START_NOT_STICKY }
            "resume" -> { ScanState.paused = false; ScanState.update { it.copy(paused = false) }; return START_NOT_STICKY }
            "cancel" -> { job?.cancel(); return START_NOT_STICKY }
        }
        if (job?.isActive == true) return START_NOT_STICKY
        startForeground(41, notification(""))
        ScanState.paused = false
        ScanState.update { ScanProgress(running = true, started = System.currentTimeMillis()) }
        wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MoodMusic:scan").apply { acquire(4 * 60 * 60 * 1000L) }
        job = scope.launch {
            try { scan() }
            catch (_: CancellationException) { }
            catch (e: Exception) { ScanState.update { it.copy(error = e.message ?: e.javaClass.simpleName) } }
            finally {
                ScanState.update { it.copy(running = false, paused = false) }
                try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) { }
                wake = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val fa = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("fa", true)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, channelId).setSmallIcon(R.drawable.ic_stat_mood)
            .setContentTitle(if (fa) "در حال تحلیل موسیقی" else "Analyzing music")
            .setContentText(text.ifBlank { if (fa) "در حال بررسی کتابخانه" else "Scanning your library" })
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
    }

    private suspend fun scan() = coroutineScope {
        val dao = MoodDatabase.get(applicationContext).songs()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val minDuration = prefs.getInt("minSeconds", 30) * 1000L
        val accurate = prefs.getBoolean("accurate", false)
        val generation = System.currentTimeMillis()
        val profiles = dao.profiles().mapNotNull { it.learningProfile() }
        val finished = AtomicInteger(0)
        val analyzed = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val found = AtomicInteger(0)
        val q = Channel<Input>(capacity = 8)
        // Two bounded workers avoid decoder/RAM pressure and excessive heat on Helio G95.
        val workers = min(2, max(1, Runtime.getRuntime().availableProcessors() - 1))
        val consumers = List(workers) {
            launch {
                for (input in q) {
                    ensureActive()
                    while (ScanState.paused) { delay(250); ensureActive() }
                    ScanState.update { it.copy(current = input.title) }
                    val existing = dao.get(input.id)
                    var song = try {
                        val r = AudioAnalysis.analyze(applicationContext, Uri.parse(input.uri), input.duration, accurate, profiles)
                        Song(
                            input.id, input.uri, input.title, input.artist, input.album, input.duration,
                            input.size, input.modified, input.added,
                            if (existing?.manual == true) existing.mood else r.mood,
                            r.secondary, r.confidence, r.features.bpm, r.features.rms, "",
                            existing?.manual == true, generation, r.mood, input.albumId,
                            r.features.serialize(), 2
                        ).copy(
                            feedback = existing?.feedback ?: 0, analysisSource = "local",
                            localMood = r.mood, localConfidence = r.confidence, onlineExplanation = ""
                        )
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        val reason = when (e.message) {
                            "silent" -> "silent"; "short" -> "short"; "unsupported" -> "unsupported"; else -> "decode"
                        }
                        Song(
                            input.id, input.uri, input.title, input.artist, input.album, input.duration,
                            input.size, input.modified, input.added,
                            if (existing?.manual == true) existing.mood else Moods.UNSORTED,
                            Moods.UNSORTED, 0f, 0f, 0f, reason, existing?.manual == true,
                            generation, Moods.UNSORTED, input.albumId, "", 2
                        ).copy(feedback = existing?.feedback ?: 0, localMood = Moods.UNSORTED, localConfidence = 0f)
                    }
                    val automaticOnline = AiSettings.enabled(applicationContext) && AiSettings.automatic(applicationContext) &&
                        !AiSettings.askBeforeUpload(applicationContext) && ProviderRegistry(applicationContext).configured()
                    val needsOnline = AiSettings.mode(applicationContext) == AiSettings.ONLINE ||
                        (AiSettings.mode(applicationContext) == AiSettings.HYBRID && song.confidence < MoodClassifier.REVIEW_THRESHOLD)
                    if (automaticOnline && needsOnline && song.reason.isBlank()) {
                        try { song = OnlineAnalysisCoordinator(applicationContext).analyze(song, confirmed = true) }
                        catch (_: Exception) { /* Local result remains a complete offline fallback. */ }
                    }
                    dao.put(song)
                    val count = finished.incrementAndGet()
                    analyzed.incrementAndGet()
                    if (count % 3 == 0) updateProgress(found, finished, analyzed, skipped)
                    if (count % 3 == 0) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(41, notification("$count / ${found.get()}"))
                }
            }
        }
        try {
            val columns = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.DATE_ADDED
            )
            val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val cursor = contentResolver.query(base, columns, "${MediaStore.Audio.Media.DURATION} >= ?", arrayOf(minDuration.toString()), MediaStore.Audio.Media.DEFAULT_SORT_ORDER)
                ?: throw IllegalStateException("MediaStore")
            cursor.use { c ->
                ScanState.update { it.copy(discovered = c.count) }
                while (c.moveToNext()) {
                    ensureActive()
                    while (ScanState.paused) { delay(250); ensureActive() }
                    fun str(column: String) = c.getString(c.getColumnIndexOrThrow(column)) ?: ""
                    fun long(column: String) = c.getLong(c.getColumnIndexOrThrow(column))
                    val id = long(MediaStore.Audio.Media._ID)
                    val file = str(MediaStore.Audio.Media.DISPLAY_NAME)
                    val rawTitle = str(MediaStore.Audio.Media.TITLE)
                    val title = if (rawTitle.isBlank() || rawTitle == "<unknown>") cleanFilename(file) else rawTitle
                    val input = Input(
                        id, ContentUris.withAppendedId(base, id).toString(), title,
                        str(MediaStore.Audio.Media.ARTIST).takeUnless { it == "<unknown>" } ?: "",
                        str(MediaStore.Audio.Media.ALBUM).takeUnless { it == "<unknown>" } ?: "",
                        long(MediaStore.Audio.Media.ALBUM_ID), long(MediaStore.Audio.Media.DURATION),
                        long(MediaStore.Audio.Media.SIZE), long(MediaStore.Audio.Media.DATE_MODIFIED),
                        long(MediaStore.Audio.Media.DATE_ADDED)
                    )
                    val existing = dao.get(id)
                    found.incrementAndGet()
                    if (existing != null && existing.size == input.size && existing.modified == input.modified && existing.duration == input.duration && existing.analysisVersion == 2) {
                        // Metadata/artwork can change without forcing another expensive decode.
                        if (existing.title != input.title || existing.artist != input.artist || existing.albumId != input.albumId || existing.uri != input.uri) {
                            dao.put(existing.copy(title = input.title, artist = input.artist, album = input.album, albumId = input.albumId, uri = input.uri, seen = generation))
                        } else dao.touch(id, generation)
                        finished.incrementAndGet(); skipped.incrementAndGet()
                    } else q.send(input)
                    if (found.get() % 10 == 0) updateProgress(found, finished, analyzed, skipped)
                }
            }
            q.close(); consumers.joinAll()
            ensureActive()
            dao.prune(generation)
            updateProgress(found, finished, analyzed, skipped)
        } finally { q.close() }
    }

    private fun updateProgress(found: AtomicInteger, finished: AtomicInteger, analyzed: AtomicInteger, skipped: AtomicInteger) {
        ScanState.update { it.copy(discovered = found.get(), done = finished.get(), analyzed = analyzed.get(), skipped = skipped.get()) }
    }

    override fun onDestroy() {
        job?.cancel(); scope.cancel()
        try { if (wake?.isHeld == true) wake?.release() } catch (_: Exception) { }
        super.onDestroy()
    }
}

fun cleanFilename(name: String): String = name.substringBeforeLast('.', name)
    .replace(Regex("(?i)(https?://)?(www\\.)?[a-z0-9-]+\\.(com|ir|net|org)"), " ")
    .replace(Regex("(?i)\\b\\d{2,4}\\s*(kbps|kb|k)\\b"), " ")
    .replace(Regex("[_\\-\\[\\]()]+"), " ")
    .replace(Regex("\\s+"), " ").trim().ifBlank { name }
