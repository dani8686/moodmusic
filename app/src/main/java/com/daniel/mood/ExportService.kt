package com.daniel.mood

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ExportProgress(
    val running: Boolean = false, val done: Int = 0, val total: Int = 0,
    val copied: Int = 0, val failed: Int = 0, val finished: Boolean = false,
    val operation: String = "", val errors: List<String> = emptyList(), val cancelled: Boolean = false
)
object ExportState {
    private val mutable = MutableStateFlow(ExportProgress())
    val progress: StateFlow<ExportProgress> = mutable
    @Synchronized fun update(change: (ExportProgress) -> ExportProgress) { mutable.value = change(mutable.value) }
}

class ExportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val channel = "export"

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(channel, "Export / خروجی", NotificationManager.IMPORTANCE_LOW)
        )
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { job?.cancel(); return START_NOT_STICKY }
        if (job?.isActive == true || intent == null) return START_NOT_STICKY
        val destination = intent.getParcelableExtra<Uri>("destination") ?: return START_NOT_STICKY
        val category = intent.getIntExtra("category", -1)
        val zip = intent.action == ACTION_ZIP
        startForeground(73, notification(0, 0))
        ExportState.update { ExportProgress(running = true, operation = if (zip) "zip" else "folder") }
        job = scope.launch {
            var completed = false
            try {
                val songs = MoodDatabase.get(applicationContext).songs().playlist(category)
                ExportState.update { it.copy(total = songs.size) }
                val exporter = CategoryExporter(applicationContext)
                val callback: (Int, Int, Int) -> Unit = { done, copied, failed ->
                    ExportState.update { it.copy(done = done, copied = copied, failed = failed) }
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(73, notification(done, songs.size))
                }
                val result = if (zip) exporter.writeZip(destination, songs, callback)
                else exporter.copyCategory(destination, Moods.persian.getOrElse(category) { "Mood Music" }, songs, callback)
                ExportState.update { it.copy(running = false, done = songs.size, copied = result.copied, failed = result.failed, errors = result.errors, finished = true) }
                completed = true
            } catch (_: CancellationException) {
                if (zip) try { contentResolver.delete(destination, null, null) } catch (_: Exception) { }
                ExportState.update { it.copy(running = false, cancelled = true, finished = true) }
            } catch (e: Exception) {
                if (zip) try { contentResolver.delete(destination, null, null) } catch (_: Exception) { }
                ExportState.update { it.copy(running = false, failed = it.total - it.copied, errors = it.errors + (e.message ?: e.javaClass.simpleName), finished = true) }
            } finally {
                if (!completed) ExportState.update { it.copy(running = false) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(done: Int, total: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_stat_mood)
            .setContentTitle("Mood Music — Export").setContentText("$done / $total")
            .setProgress(total, done, total == 0).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(open).build()
    }

    override fun onDestroy() { job?.cancel(); scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_COPY = "com.daniel.mood.export.COPY"
        const val ACTION_ZIP = "com.daniel.mood.export.ZIP"
        const val ACTION_CANCEL = "com.daniel.mood.export.CANCEL"
    }
}
