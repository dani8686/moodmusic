@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.daniel.mood.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.mood.AlbumArtLoader
import com.daniel.mood.MoodCatalog
import com.daniel.mood.PlayerSnapshot
import com.daniel.mood.ScanProgress
import com.daniel.mood.Song
import kotlin.math.max

data class SongActions(
    val onPlay: (Song) -> Unit,
    val onAddQueue: (Song) -> Unit,
    val onMore: (Song) -> Unit
)

@Composable
fun CoverArt(song: Song, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var art by remember(song.albumId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song.albumId) {
        art = AlbumArtLoader.load(context, song.albumId)
    }
    val accent = moodColor(song.mood)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.65f), Navy, SurfaceDarkAlt)))
    ) {
        val bitmap = art
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            WaveformBars(song.id, accent)
        }
    }
}

@Composable
private fun WaveformBars(id: Long, accent: Color) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0..6) {
            val fraction = (0.28f + (((kotlin.math.abs(id) * (i + 3)) % 9) / 14f)).coerceIn(0.25f, 0.92f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i % 2 == 0) Color.White.copy(alpha = 0.88f) else accent)
            )
        }
    }
}

@Composable
fun LabelChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun MoodCard(id: Int, count: Int, fa: Boolean, pd: Boolean, onClick: () -> Unit) {
    val accent = if (id == MoodCatalog.ALL) AccentPrimary else moodColor(id)
    val label = if (id == MoodCatalog.ALL) {
        MoodCatalog.tr(fa, "All songs", "همه آهنگ\u200cها")
    } else MoodCatalog.name(id, fa)
    val symbol = if (id == MoodCatalog.ALL) "♫" else MoodCatalog.glyph(id)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(112.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkAlt)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), SurfaceDarkAlt.copy(alpha = 0.9f))))
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Text(symbol, color = accent, fontSize = 24.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    MoodCatalog.tr(fa, "songs", "آهنگ") + " " + MoodCatalog.digits(count, pd),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(38.dp, 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
fun SongRow(song: Song, fa: Boolean, pd: Boolean, actions: SongActions) {
    val accent = moodColor(song.mood)
    val manual = song.manualMood != null
    ElevatedCard(
        onClick = { actions.onPlay(song) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(song, 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val artist = song.artist.ifBlank { MoodCatalog.tr(fa, "Unknown artist", "خواننده ناشناس") }
                Text(
                    "$artist  •  ${MoodCatalog.clock(song.duration, pd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabelChip(
                        text = if (manual) MoodCatalog.tr(fa, "Manual", "دستی")
                        else MoodCatalog.tr(fa, "Auto", "خودکار"),
                        color = if (manual) AccentCyan else accent
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (song.mood == MoodCatalog.REVIEW) {
                            MoodCatalog.tr(fa, "Needs review", "نیازمند بررسی") +
                                " (" + MoodCatalog.name(song.secondary, fa) + ")"
                        } else MoodCatalog.name(song.mood, fa),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (song.reason.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            MoodCatalog.tr(fa, "unreadable", "ناخوانا"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB4A2)
                        )
                    }
                }
            }
            IconButton(onClick = { actions.onAddQueue(song) }) {
                Icon(Icons.Filled.Add, contentDescription = MoodCatalog.tr(fa, "Add to queue", "افزودن به صف"))
            }
            IconButton(onClick = { actions.onMore(song) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = MoodCatalog.tr(fa, "More actions", "گزینه\u200cها"))
            }
        }
    }
}

@Composable
fun SongList(
    songs: List<Song>,
    fa: Boolean,
    pd: Boolean,
    actions: SongActions,
    modifier: Modifier = Modifier,
    footer: LazyListScope.() -> Unit = {}
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            SongRow(song, fa, pd, actions)
        }
        footer(this)
    }
}

@Composable
fun SectionHeader(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        trailing()
    }
}

@Composable
fun MiniPlayer(
    snapshot: PlayerSnapshot,
    fa: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    if (!snapshot.hasItem) return
    Surface(color = SurfaceDarkAlt, tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(AccentPrimary, AccentCyan))),
                contentAlignment = Alignment.Center
            ) {
                Text("♫", color = Color.White, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot.title.ifBlank { MoodCatalog.tr(fa, "Now playing", "در حال پخش") },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    snapshot.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (snapshot.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = MoodCatalog.tr(fa, "Play / pause", "پخش / مکث")
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = MoodCatalog.tr(fa, "Next", "بعدی"))
            }
            TextButton(onClick = onOpen) {
                Text(MoodCatalog.tr(fa, "Player", "پخش\u200cکننده"))
            }
        }
    }
}

@Composable
fun ScanProgressCard(
    progress: ScanProgress,
    fa: Boolean,
    pd: Boolean,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onRescan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkAlt)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (progress.running) MoodCatalog.tr(fa, "Analyzing audio", "در حال تحلیل صدا")
                    else MoodCatalog.tr(fa, "Library up to date", "کتابخانه به\u200cروز است"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRescan, enabled = !progress.running) {
                    Text(MoodCatalog.tr(fa, "Rescan", "بررسی دوباره"))
                }
            }
            if (progress.running) {
                val fraction = if (progress.discovered > 0) {
                    (progress.done.toFloat() / progress.discovered).coerceIn(0f, 1f)
                } else 0f
                if (progress.discovered > 0) {
                    LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${MoodCatalog.digits(progress.done, pd)} / ${MoodCatalog.digits(progress.discovered, pd)}" +
                        "  •  ${progress.current}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val elapsed = ((System.currentTimeMillis() - progress.started) / 1000.0).coerceAtLeast(1.0)
                val rate = progress.done / elapsed
                val remaining = if (rate > 0.01) {
                    ((max(0, progress.discovered - progress.done)) / rate).toLong()
                } else 0L
                Text(
                    MoodCatalog.tr(fa, "ETA", "زمان تقریبی") + ": " + MoodCatalog.clock(remaining * 1000, pd) +
                        "  •  " + MoodCatalog.tr(fa, "cached re-scores", "امتیازدهی از کش") + ": " +
                        MoodCatalog.digits(progress.rescored, pd),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Row {
                    TextButton(onClick = onPause) {
                        Text(if (progress.paused) MoodCatalog.tr(fa, "Resume", "ادامه") else MoodCatalog.tr(fa, "Pause", "مکث"))
                    }
                    TextButton(onClick = onCancel) { Text(MoodCatalog.tr(fa, "Cancel", "لغو")) }
                }
            } else {
                Text(
                    MoodCatalog.tr(
                        fa,
                        "Only new or changed files are decoded. Analysis runs offline on your device.",
                        "فقط فایل\u200cهای جدید یا تغییرکرده تحلیل می\u200cشوند؛ پردازش کاملاً محلی و آفلاین است."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun EmptyState(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("♫", fontSize = 40.sp, color = AccentPrimary)
        Spacer(Modifier.height(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        if (actionLabel != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
