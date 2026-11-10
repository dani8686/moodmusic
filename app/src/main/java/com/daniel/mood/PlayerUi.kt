package com.daniel.mood

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.SubcomposeAsyncImage

fun albumArtwork(albumId: Long): Uri? = if (albumId > 0)
    Uri.parse("content://media/external/audio/albumart/$albumId") else null

@Composable
fun Artwork(uri: Uri?, modifier: Modifier = Modifier, symbol: String = "♫") {
    val shape = RoundedCornerShape(18.dp)
    SubcomposeAsyncImage(
        model = uri, contentDescription = null, modifier = modifier.clip(shape), contentScale = ContentScale.Crop,
        loading = { ArtworkFallback(modifier = Modifier.fillMaxSize(), symbol = symbol) },
        error = { ArtworkFallback(modifier = Modifier.fillMaxSize(), symbol = symbol) }
    )
}

@Composable
fun ArtworkFallback(modifier: Modifier = Modifier, symbol: String = "♫") {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFF353064), Color(0xFF173A4D)))),
        contentAlignment = Alignment.Center
    ) { Text(symbol, style = MaterialTheme.typography.headlineMedium, color = Color(0xFF9DE9F0)) }
}

@Composable
fun MiniPlayer(controller: MediaController?, revision: Int, fa: Boolean, pd: Boolean, onOpen: () -> Unit) {
    val c = controller ?: return
    if (revision < 0 || c.currentMediaItem == null) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 8.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(c.mediaMetadata.artworkUri, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(c.mediaMetadata.title?.toString() ?: "♫", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(c.mediaMetadata.artist?.toString()?.ifBlank { tr(fa, "Unknown artist", "خواننده نامشخص") } ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { if (c.isPlaying) c.pause() else c.play() }) {
                Icon(if (c.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = tr(fa, "Play or pause", "پخش یا توقف"), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { c.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, contentDescription = tr(fa, "Next", "بعدی")) }
        }
    }
}

@Composable
fun PlayerDialog(controller: MediaController, revision: Int, fa: Boolean, pd: Boolean, onDismiss: () -> Unit, onQueue: () -> Unit) {
    val c = controller
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .16f), Color.Transparent)))) {
                Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = tr(fa, "Close", "بستن")) }
                        Text(tr(fa, "Now playing", "در حال پخش"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, contentDescription = tr(fa, "Queue", "صف پخش")) }
                    }
                    Spacer(Modifier.weight(.3f))
                    Artwork(c.mediaMetadata.artworkUri, Modifier.fillMaxWidth(.82f).aspectRatio(1f))
                    Spacer(Modifier.height(28.dp))
                    Text(c.mediaMetadata.title?.toString() ?: "♫", style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(c.mediaMetadata.artist?.toString() ?: "", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.weight(.3f))
                    val position = if (revision >= 0) c.currentPosition.coerceAtLeast(0) else 0L
                    val duration = c.duration.coerceAtLeast(0)
                    Slider(
                        value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                        onValueChange = { if (duration > 0) c.seekTo((it * duration).toLong()) }, modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(clock(position, pd), style = MaterialTheme.typography.labelMedium)
                        Text(clock(duration, pd), style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { c.shuffleModeEnabled = !c.shuffleModeEnabled }) {
                            Icon(Icons.Default.Shuffle, contentDescription = tr(fa, "Shuffle", "پخش تصادفی"), tint = if (c.shuffleModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { c.seekToPreviousMediaItem() }, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipPrevious, contentDescription = tr(fa, "Previous", "قبلی"), modifier = Modifier.size(36.dp)) }
                        FilledIconButton(onClick = { if (c.isPlaying) c.pause() else c.play() }, modifier = Modifier.size(70.dp), shape = CircleShape) {
                            Icon(if (c.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = tr(fa, "Play or pause", "پخش یا توقف"), modifier = Modifier.size(38.dp))
                        }
                        IconButton(onClick = { c.seekToNextMediaItem() }, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipNext, contentDescription = tr(fa, "Next", "بعدی"), modifier = Modifier.size(36.dp)) }
                        IconButton(onClick = { c.repeatMode = (c.repeatMode + 1) % 3 }) {
                            Icon(if (c.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = tr(fa, "Repeat", "تکرار"), tint = if (c.repeatMode == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueView(controller: MediaController?, revision: Int, fa: Boolean, pd: Boolean, onPlay: () -> Unit) {
    val c = controller
    if (c == null || c.mediaItemCount == 0) {
        EmptyView(tr(fa, "Your play queue is empty", "صف پخش خالی است"), Icons.Default.QueueMusic)
        return
    }
    val items = remember(revision, c.mediaItemCount) { List(c.mediaItemCount) { c.getMediaItemAt(it) } }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        items(items.size, key = { "${items[it].mediaId}-$it" }) { index ->
            val item: MediaItem = items[index]
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = if (index == c.currentMediaItemIndex) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { c.seekTo(index, 0); c.play(); onPlay() }) { Icon(if (index == c.currentMediaItemIndex && c.isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    Column(Modifier.weight(1f)) {
                        Text(item.mediaMetadata.title?.toString() ?: "♫", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.mediaMetadata.artist?.toString() ?: "", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(enabled = index > 0, onClick = { c.moveMediaItem(index, index - 1) }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = tr(fa, "Move up", "انتقال بالا")) }
                    IconButton(enabled = index < items.lastIndex, onClick = { c.moveMediaItem(index, index + 1) }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = tr(fa, "Move down", "انتقال پایین")) }
                    IconButton(onClick = { c.removeMediaItem(index) }) { Icon(Icons.Default.Close, contentDescription = tr(fa, "Remove", "حذف")) }
                }
            }
        }
    }
}
