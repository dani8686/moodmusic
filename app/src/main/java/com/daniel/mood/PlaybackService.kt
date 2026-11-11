package com.daniel.mood

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Media3 foreground playback, reliable listening telemetry and a real two-player DJ crossfade. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var secondary: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { getSharedPreferences("playback", MODE_PRIVATE) }
    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val database by lazy { MoodDatabase.get(applicationContext) }
    private var restoring = true
    private var mixJob: Job? = null
    private var activeListen: ActiveListen? = null
    private var currentGain = 1f
    private var djSessionStarted = 0L

    private data class ActiveListen(
        val songId: Long, val startedAt: Long, val trackDurationMs: Long,
        val source: String, val accumulatedMs: Long, val resumedAt: Long
    )

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(audioAttributes(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        secondary = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(audioAttributes(), false)
            setHandleAudioBecomingNoisy(false)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                finishListening(if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) "complete" else "transition")
                save(player)
                currentGain = 1f
                if (player.isPlaying) startListening(player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startListening(player) else {
                    finishListening("pause")
                    mixJob?.cancel(); mixJob = null
                    secondary?.pause(); secondary?.stop()
                    player.volume = 1f
                }
                save(player)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = save(player)
            override fun onRepeatModeChanged(repeatMode: Int) = save(player)
            override fun onTimelineChanged(timeline: Timeline, reason: Int) { if (!restoring) save(player) }
            override fun onPlayerError(error: PlaybackException) { finishListening("error") }
        })
        session = MediaSession.Builder(this, player).setId("mood_music_session").build()
        restore(player)
        scope.launch {
            while (isActive) {
                delay(250)
                val p = session?.player ?: continue
                if (p.isPlaying) tickDj(p)
                if (System.currentTimeMillis() % 5000 < 300) save(p)
            }
        }
    }

    private fun audioAttributes() = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (session?.player?.isPlaying != true && secondary?.isPlaying != true) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun startListening(player: Player) {
        if (activeListen != null) return
        val item = player.currentMediaItem ?: return
        val id = item.mediaId.toLongOrNull() ?: return
        activeListen = ActiveListen(id, System.currentTimeMillis(), player.duration.coerceAtLeast(0), item.mediaMetadata.extras?.getString("source") ?: "library", 0, System.currentTimeMillis())
    }

    private fun finishListening(interaction: String) {
        val active = activeListen ?: return
        activeListen = null
        val listened = active.accumulatedMs + (System.currentTimeMillis() - active.resumedAt).coerceAtLeast(0)
        if (listened < 1000) return
        val ratio = if (active.trackDurationMs > 0) (listened.toFloat() / active.trackDurationMs).coerceIn(0f, 1.2f) else 0f
        val event = ListeningEvent(
            songId = active.songId, startedAt = active.startedAt, listenedMs = listened,
            trackDurationMs = active.trackDurationMs, completionRatio = ratio,
            skipped = ratio < .2f && interaction != "complete", source = active.source, interaction = interaction
        )
        ioScope.launch {
            val dao = database.intelligence()
            dao.addEvent(event)
            val song = database.songs().get(active.songId)
            if (song != null) dao.putTaste(TasteEngine.applyEvent(dao.taste(), song, event))
            val retention = settings.getInt("historyRetention", 365)
            if (retention > 0) dao.pruneHistory(System.currentTimeMillis() - retention * 86400000L)
        }
    }

    private fun tickDj(player: Player) {
        if (!settings.getBoolean("djEnabled", false) || mixJob?.isActive == true || player.playbackState == Player.STATE_ENDED) return
        if (djSessionStarted == 0L) djSessionStarted = System.currentTimeMillis()
        val duration = player.duration
        if (duration <= 0) return
        val seconds = settings.getInt("djCrossfade", 6).coerceIn(2, 12)
        if (duration - player.currentPosition > seconds * 1000L) return
        val current = player.currentMediaItem ?: return
        mixJob = scope.launch {
            try {
                player.repeatMode = Player.REPEAT_MODE_OFF
                val next = withContext(Dispatchers.IO) { chooseDjNext(current) } ?: return@launch
                val nextIndex = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == next.mediaId }
                    ?: player.mediaItemCount.also { player.addMediaItem(next) }
                val other = secondary ?: return@launch
                other.setMediaItem(next)
                other.volume = 0f
                other.prepare(); other.play()
                val nextSong = withContext(Dispatchers.IO) { database.songs().get(next.mediaId.toLongOrNull() ?: -1) }
                val currentSong = withContext(Dispatchers.IO) { database.songs().get(current.mediaId.toLongOrNull() ?: -1) }
                val baseMs = seconds * 1000L
                // Beat-quantized fade length, not a claim of beat-grid/phase synchronization.
                val bpm = currentSong?.bpm?.takeIf { it > 0 } ?: nextSong?.bpm?.takeIf { it > 0 } ?: 0f
                val fadeMs = if (bpm > 0) {
                    val beat = 60000f / bpm
                    (baseMs / beat).roundToInt().coerceIn(4, 16) * beat
                } else baseMs.toFloat()
                val fade = fadeMs.toLong().coerceIn((baseMs - 750).coerceAtLeast(1500), baseMs + 750)
                val normalize = settings.getBoolean("djNormalize", true)
                currentGain = if (normalize && currentSong != null && currentSong.energy > .001f) (.08f / currentSong.energy).coerceIn(.75f, 1.25f) else 1f
                val nextGain = if (normalize && nextSong != null && nextSong.energy > .001f) (.08f / nextSong.energy).coerceIn(.75f, 1.25f) else 1f
                val started = System.currentTimeMillis()
                while (isActive) {
                    val progress = ((System.currentTimeMillis() - started).toFloat() / fade).coerceIn(0f, 1f)
                    player.volume = currentGain * (1f - progress)
                    other.volume = nextGain * progress
                    if (progress >= 1f) break
                    delay(40)
                }
                player.seekToDefaultPosition(nextIndex)
                player.volume = nextGain
                currentGain = nextGain
                player.play()
                other.stop(); other.clearMediaItems()
            } catch (_: CancellationException) {
                player.volume = 1f
                secondary?.stop()
                throw CancellationException()
            } catch (_: Exception) {
                player.volume = 1f
                secondary?.stop()
            }
        }
    }

    private suspend fun chooseDjNext(currentItem: MediaItem): MediaItem? {
        val songs = database.songs().playlist(-1)
        val current = songs.firstOrNull { it.id == currentItem.mediaId.toLongOrNull() }
        val config = DjConfig(
            mode = settings.getString("djMode", "party") ?: "party",
            bpmMatching = settings.getBoolean("djBpm", true),
            energyMatching = settings.getBoolean("djEnergy", true),
            avoidSimilar = settings.getBoolean("djAvoidSimilar", true),
            preferFamiliar = settings.getBoolean("djFamiliar", true),
            allowDiscovery = settings.getBoolean("djDiscovery", true),
            noRepeatWindow = settings.getInt("djNoRepeat", 30)
        )
        val recent = database.intelligence().recentEvents(100).map { it.songId }
        val next = SmartDjSelector.next(songs, current, database.intelligence().taste(), recent, config) ?: return null
        return next.toPlaybackItem("smart_dj")
    }

    private fun restore(player: Player) {
        try {
            val root = JSONObject(prefs.getString("queue", "{}") ?: "{}")
            val array = root.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<MediaItem>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val metadata = MediaMetadata.Builder().setTitle(item.optString("title")).setArtist(item.optString("artist")).setAlbumTitle(item.optString("album"))
                val artwork = item.optString("artwork")
                if (artwork.isNotBlank()) metadata.setArtworkUri(android.net.Uri.parse(artwork))
                metadata.setExtras(Bundle().apply { putString("source", item.optString("source", "library")) })
                items += MediaItem.Builder().setMediaId(item.optString("id")).setUri(item.getString("uri")).setMediaMetadata(metadata.build()).build()
            }
            if (items.isNotEmpty()) {
                player.setMediaItems(items, root.optInt("index").coerceIn(0, items.lastIndex), root.optLong("position").coerceAtLeast(0))
                player.shuffleModeEnabled = root.optBoolean("shuffle")
                player.repeatMode = root.optInt("repeat").coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
                player.prepare()
            }
        } catch (_: Exception) { prefs.edit().remove("queue").apply() }
        finally { restoring = false }
    }

    private fun save(player: Player) {
        if (restoring) return
        try {
            val array = JSONArray()
            for (i in 0 until player.mediaItemCount) {
                val item = player.getMediaItemAt(i)
                array.put(JSONObject().put("id", item.mediaId).put("uri", item.localConfiguration?.uri?.toString() ?: "")
                    .put("title", item.mediaMetadata.title?.toString() ?: "").put("artist", item.mediaMetadata.artist?.toString() ?: "")
                    .put("album", item.mediaMetadata.albumTitle?.toString() ?: "").put("artwork", item.mediaMetadata.artworkUri?.toString() ?: "")
                    .put("source", item.mediaMetadata.extras?.getString("source") ?: "library"))
            }
            prefs.edit().putString("queue", JSONObject().put("items", array).put("index", player.currentMediaItemIndex.coerceAtLeast(0))
                .put("position", player.currentPosition.coerceAtLeast(0)).put("shuffle", player.shuffleModeEnabled)
                .put("repeat", player.repeatMode).toString()).apply()
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        val player = session?.player
        if (player != null) save(player)
        finishListening("destroy")
        if (djSessionStarted > 0) {
            val duration = System.currentTimeMillis() - djSessionStarted
            ioScope.launch { database.intelligence().addDjSession(DjSession(mode = settings.getString("djMode", "party") ?: "party", startedAt = djSessionStarted, listenedMs = duration)) }
        }
        mixJob?.cancel()
        secondary?.release(); secondary = null
        session?.run { this.player.release(); release() }; session = null
        scope.cancel()
        // Give the last database write a bounded chance to finish without keeping playback alive.
        runBlocking { withTimeoutOrNull(1000) { ioScope.coroutineContext.job.children.forEach { it.join() } } }
        ioScope.cancel()
        super.onDestroy()
    }
}

fun Song.toPlaybackItem(source: String = "library"): MediaItem {
    val metadata = MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
    albumArtwork(albumId)?.let { metadata.setArtworkUri(it) }
    metadata.setExtras(Bundle().apply { putString("source", source) })
    return MediaItem.Builder().setMediaId(id.toString()).setUri(uri).setMediaMetadata(metadata.build()).build()
}
