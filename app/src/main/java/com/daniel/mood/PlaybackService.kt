package com.daniel.mood

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real foreground media-session service.
 *
 * ExoPlayer owns audio focus (setAudioAttributes(..., true)), handles headset
 * unplug / Bluetooth disconnect, notification and lock-screen controls are
 * provided by MediaSessionService, and the queue + position are persisted in
 * Room so playback state survives process recreation.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repository by lazy { MusicRepository(applicationContext) }
    private var saveJob: Job? = null

    private val positionSaver = object : Runnable {
        override fun run() {
            if (::player.isInitialized) persistState()
            mainHandler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // manage audio focus automatically (calls, navigation, other apps)
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
        player.addListener(playerListener)
        mainHandler.post(positionSaver)
        restoreQueue()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = persistState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = persistState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = persistState()
        override fun onRepeatModeChanged(repeatMode: Int) = persistState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = persistState()
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) persistState()
        }
    }

    /** Called only from the main thread (player listeners / main handler). */
    private fun persistState() {
        if (!::player.isInitialized) return
        val items = ArrayList<QueueItem>(player.mediaItemCount)
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val songId = item.mediaId.toLongOrNull() ?: continue
            items.add(
                QueueItem(
                    position = index,
                    songId = songId,
                    uri = item.localConfiguration?.uri?.toString() ?: "",
                    title = item.mediaMetadata.title?.toString() ?: "",
                    artist = item.mediaMetadata.artist?.toString() ?: ""
                )
            )
        }
        val state = QueueState(
            id = 0,
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            updatedAt = System.currentTimeMillis()
        )
        saveJob?.cancel()
        saveJob = serviceScope.launch {
            try {
                repository.saveQueue(items, state)
            } catch (_: Exception) {
                // A failed persistence write must never interrupt playback.
            }
        }
    }

    private fun restoreQueue() {
        serviceScope.launch {
            val snapshot = try {
                repository.restoreQueue()
            } catch (_: Exception) {
                QueueSnapshot(emptyList(), null)
            }
            val items = snapshot.items
            withContext(Dispatchers.Main) {
                // If the UI already supplied a fresh queue, do not overwrite it.
                if (mediaSession == null || player.mediaItemCount > 0) return@withContext
                if (items.isNotEmpty()) {
                    val mediaItems = items.map { entry ->
                        MediaItem.Builder()
                            .setMediaId(entry.songId.toString())
                            .setUri(entry.uri)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(entry.title)
                                    .setArtist(entry.artist)
                                    .build()
                            )
                            .build()
                    }
                    val index = (snapshot.state?.currentIndex ?: 0).coerceIn(0, mediaItems.lastIndex)
                    player.setMediaItems(mediaItems, index, snapshot.state?.positionMs ?: 0L)
                    player.shuffleModeEnabled = snapshot.state?.shuffle ?: false
                    player.repeatMode = snapshot.state?.repeatMode ?: Player.REPEAT_MODE_OFF
                    player.prepare() // restore paused; never auto-start audio
                }
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(positionSaver)
        player.removeListener(playerListener)
        serviceScope.cancel()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
