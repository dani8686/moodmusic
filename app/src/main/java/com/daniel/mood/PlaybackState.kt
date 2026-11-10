package com.daniel.mood

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerSnapshot(
    val hasItem: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val songId: Long = 0L,
    val duration: Long = 0L,
    val position: Long = 0L,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = QueueLogic.REPEAT_OFF,
    val speed: Float = 1f
)

data class QueueEntry(
    val index: Int,
    val songId: Long,
    val title: String,
    val artist: String
)

/**
 * A small, shared snapshot lets only the player UI recompose twice a second,
 * instead of recomposing the whole library screen.
 */
object PlaybackState {
    private val mutable = MutableStateFlow(PlayerSnapshot())
    val snapshot: StateFlow<PlayerSnapshot> = mutable

    fun publish(controller: MediaController?) {
        mutable.value = if (controller == null) {
            PlayerSnapshot()
        } else {
            try {
                val metadata = controller.mediaMetadata
                PlayerSnapshot(
                    hasItem = controller.currentMediaItem != null,
                    title = metadata.title?.toString() ?: "",
                    artist = metadata.artist?.toString() ?: "",
                    songId = controller.currentMediaItem?.mediaId?.toLongOrNull() ?: 0L,
                    duration = controller.duration.coerceAtLeast(0L),
                    position = controller.currentPosition.coerceAtLeast(0L),
                    playing = controller.isPlaying,
                    shuffle = controller.shuffleModeEnabled,
                    repeatMode = controller.repeatMode,
                    speed = controller.playbackParameters.speed
                )
            } catch (_: Exception) {
                PlayerSnapshot()
            }
        }
    }

    /** Called on demand (queue dialog), not on every tick, to keep it cheap. */
    fun queue(controller: MediaController?): List<QueueEntry> {
        if (controller == null) return emptyList()
        return try {
            val count = controller.mediaItemCount
            ArrayList<QueueEntry>(count).apply {
                for (index in 0 until count) {
                    val item = controller.getMediaItemAt(index)
                    add(
                        QueueEntry(
                            index = index,
                            songId = item.mediaId.toLongOrNull() ?: 0L,
                            title = item.mediaMetadata.title?.toString() ?: "",
                            artist = item.mediaMetadata.artist?.toString() ?: ""
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(Uri.parse(uri))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()
    )
    .build()
