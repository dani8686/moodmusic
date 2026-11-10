package com.daniel.mood

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** A real Media3 foreground service; the Activity never owns playback. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { getSharedPreferences("playback", MODE_PRIVATE) }
    private var restoring = true

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true // ExoPlayer handles focus loss, transient ducking and focus regain.
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = save(player)
            override fun onIsPlayingChanged(isPlaying: Boolean) = save(player)
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = save(player)
            override fun onRepeatModeChanged(repeatMode: Int) = save(player)
            override fun onTimelineChanged(timeline: Timeline, reason: Int) { if (!restoring) save(player) }
        })
        session = MediaSession.Builder(this, player).setId("mood_music_session").build()
        restore(player)
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) save(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // MediaSessionService keeps the foreground notification alive during active playback.
        if (session?.player?.isPlaying != true) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun restore(player: Player) {
        try {
            val root = JSONObject(prefs.getString("queue", "{}") ?: "{}")
            val array = root.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<MediaItem>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.optString("title"))
                    .setArtist(item.optString("artist"))
                    .setAlbumTitle(item.optString("album"))
                val artwork = item.optString("artwork")
                if (artwork.isNotBlank()) metadata.setArtworkUri(android.net.Uri.parse(artwork))
                items += MediaItem.Builder()
                    .setMediaId(item.optString("id"))
                    .setUri(item.getString("uri"))
                    .setMediaMetadata(metadata.build())
                    .build()
            }
            if (items.isNotEmpty()) {
                player.setMediaItems(items, root.optInt("index").coerceIn(0, items.lastIndex), root.optLong("position").coerceAtLeast(0))
                player.shuffleModeEnabled = root.optBoolean("shuffle")
                player.repeatMode = root.optInt("repeat").coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
                player.prepare() // Restores the queue without unexpectedly starting audio.
            }
        } catch (_: Exception) {
            prefs.edit().remove("queue").apply()
        } finally { restoring = false }
    }

    private fun save(player: Player) {
        if (restoring) return
        try {
            val array = JSONArray()
            for (i in 0 until player.mediaItemCount) {
                val item = player.getMediaItemAt(i)
                array.put(JSONObject()
                    .put("id", item.mediaId)
                    .put("uri", item.localConfiguration?.uri?.toString() ?: "")
                    .put("title", item.mediaMetadata.title?.toString() ?: "")
                    .put("artist", item.mediaMetadata.artist?.toString() ?: "")
                    .put("album", item.mediaMetadata.albumTitle?.toString() ?: "")
                    .put("artwork", item.mediaMetadata.artworkUri?.toString() ?: ""))
            }
            prefs.edit().putString("queue", JSONObject()
                .put("items", array)
                .put("index", player.currentMediaItemIndex.coerceAtLeast(0))
                .put("position", player.currentPosition.coerceAtLeast(0))
                .put("shuffle", player.shuffleModeEnabled)
                .put("repeat", player.repeatMode)
                .toString()).apply()
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        session?.player?.let { save(it) }
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
