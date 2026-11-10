@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.daniel.mood.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daniel.mood.ExportProgress
import com.daniel.mood.MoodCatalog
import com.daniel.mood.Playlist
import com.daniel.mood.QueueEntry
import com.daniel.mood.Song

@Composable
fun MoodPickerDialog(
    song: Song,
    fa: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onRestoreAuto: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MoodCatalog.tr(fa, "Choose a mood", "انتخاب حال\u200cوهوا")) },
        text = {
            Column(Modifier.heightIn(max = 380.dp)) {
                if (song.reason.isBlank()) {
                    Text(
                        MoodCatalog.tr(fa, "Automatic", "تشخیص خودکار") + ": " +
                            MoodCatalog.name(song.predictedMood, fa),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn {
                    items(MoodCatalog.selectable, key = { it }) { id ->
                        TextButton(
                            onClick = { onSelect(id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${MoodCatalog.glyph(id)}  ${MoodCatalog.name(id, fa)}",
                                modifier = Modifier.weight(1f),
                                color = moodColor(id)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (song.manualMood != null) {
                TextButton(onClick = onRestoreAuto) {
                    Text(MoodCatalog.tr(fa, "Restore automatic", "بازگشت به تشخیص خودکار"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Close", "بستن")) }
        }
    )
}

@Composable
fun SongActionsDialog(
    song: Song,
    fa: Boolean,
    playlists: List<Playlist>,
    currentPlaylistId: Long?,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddQueue: () -> Unit,
    onChangeMood: () -> Unit,
    onRestoreAuto: () -> Unit,
    onReanalyze: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onRemoveFromPlaylist: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title, maxLines = 1) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                TextButton(onClick = onPlayNext, modifier = Modifier.fillMaxWidth()) {
                    Text(MoodCatalog.tr(fa, "Play next", "پخش بعدی"), modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onAddQueue, modifier = Modifier.fillMaxWidth()) {
                    Text(MoodCatalog.tr(fa, "Add to queue", "افزودن به صف"), modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onChangeMood, modifier = Modifier.fillMaxWidth()) {
                    Text(MoodCatalog.tr(fa, "Change mood", "تغییر حال\u200cوهوا"), modifier = Modifier.weight(1f))
                }
                if (song.manualMood != null) {
                    TextButton(onClick = onRestoreAuto, modifier = Modifier.fillMaxWidth()) {
                        Text(MoodCatalog.tr(fa, "Restore automatic mood", "بازگشت به تشخیص خودکار"), modifier = Modifier.weight(1f))
                    }
                }
                TextButton(onClick = onReanalyze, modifier = Modifier.fillMaxWidth()) {
                    Text(MoodCatalog.tr(fa, "Re-analyze this song", "تحلیل دوباره این آهنگ"), modifier = Modifier.weight(1f))
                }
                if (currentPlaylistId != null) {
                    TextButton(onClick = onRemoveFromPlaylist, modifier = Modifier.fillMaxWidth()) {
                        Text(MoodCatalog.tr(fa, "Remove from this playlist", "حذف از این پلی\u200cلیست"), modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    MoodCatalog.tr(fa, "Add to playlist", "افزودن به پلی\u200cلیست"),
                    style = MaterialTheme.typography.titleSmall
                )
                if (playlists.isEmpty()) {
                    Text(
                        MoodCatalog.tr(fa, "Create a playlist first.", "ابتدا یک پلی\u200cلیست بسازید."),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 160.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            TextButton(
                                onClick = { onAddToPlaylist(playlist.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(playlist.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Close", "بستن")) }
        }
    )
}

@Composable
fun QueueDialog(
    entries: List<QueueEntry>,
    fa: Boolean,
    onDismiss: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MoodCatalog.tr(fa, "Play queue", "صف پخش")) },
        text = {
            if (entries.isEmpty()) {
                Text(MoodCatalog.tr(fa, "The queue is empty.", "صف پخش خالی است."))
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(entries, key = { it.index }) { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                                Text(entry.artist, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            IconButton(onClick = { onMove(entry.index, entry.index - 1) }) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Up")
                            }
                            IconButton(onClick = { onMove(entry.index, entry.index + 1) }) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Down")
                            }
                            IconButton(onClick = { onRemove(entry.index) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Close", "بستن")) }
        }
    )
}

@Composable
fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    fa: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Cancel", "انصراف")) }
        }
    )
}

@Composable
fun ConfirmDialog(text: String, fa: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MoodCatalog.tr(fa, "Please confirm", "تأیید")) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(MoodCatalog.tr(fa, "Confirm", "تأیید")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Cancel", "لغو")) }
        }
    )
}

@Composable
fun ExportProgressDialog(
    progress: ExportProgress,
    fa: Boolean,
    pd: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!progress.running) onDismiss() },
        title = {
            Text(
                when (progress.label) {
                    "copy" -> MoodCatalog.tr(fa, "Copying to folder", "کپی به پوشه")
                    "zip" -> MoodCatalog.tr(fa, "Creating ZIP", "ساخت ZIP")
                    else -> MoodCatalog.tr(fa, "Preparing share", "آماده\u200cسازی اشتراک")
                }
            )
        },
        text = {
            Column {
                if (progress.running) {
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = (progress.done.toFloat() / progress.total).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${MoodCatalog.digits(progress.done, pd)} / ${MoodCatalog.digits(progress.total, pd)}  •  ${progress.current}",
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val result = progress.result
                    if (result != null) {
                        Text(
                            MoodCatalog.tr(fa, "Successful", "موفق") + ": " + MoodCatalog.digits(result.success, pd) +
                                "   " + MoodCatalog.tr(fa, "Failed", "ناموفق") + ": " + MoodCatalog.digits(result.failed, pd),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (result.errors.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(MoodCatalog.tr(fa, "Errors", "خطا\u200cها"), style = MaterialTheme.typography.titleSmall)
                            LazyColumn(Modifier.heightIn(max = 180.dp)) {
                                items(result.errors.take(12)) { error ->
                                    Text("• $error", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (progress.running) {
                TextButton(onClick = onCancel) { Text(MoodCatalog.tr(fa, "Cancel", "لغو")) }
            } else {
                TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Close", "بستن")) }
            }
        }
    )
}

@Composable
fun AddSongsDialog(
    fa: Boolean,
    songsProvider: (String) -> kotlinx.coroutines.flow.Flow<List<Song>>,
    onAdd: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val songs by remember(query) { songsProvider(query) }
        .collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MoodCatalog.tr(fa, "Add songs", "افزودن آهنگ")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(MoodCatalog.tr(fa, "Search", "جستجو")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(songs, key = { it.id }) { song ->
                        TextButton(
                            onClick = { onAdd(song) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "+  ${song.title}  •  ${song.artist}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Close", "بستن")) }
        }
    )
}

@Composable
fun BatteryDialog(fa: Boolean, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(MoodCatalog.tr(fa, "Background work on Xiaomi", "کار در پس\u200cزمینه در شیائومی")) },
        text = {
            Text(
                MoodCatalog.tr(
                    fa,
                    "MIUI/HyperOS may stop long analysis. Set this app's battery usage to unrestricted and enable autostart if available. Playback still works while the app is closed.",
                    "MIUI یا HyperOS ممکن است تحلیل طولانی را متوقف کند. مصرف باتری برنامه را بدون محدودیت کنید و در صورت وجود شروع خودکار را فعال کنید. پخش موسیقی هنگام بسته\u200cبودن برنامه ادامه دارد."
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(MoodCatalog.tr(fa, "Battery settings", "تنظیمات باتری")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(MoodCatalog.tr(fa, "Skip", "رد کردن")) }
        }
    )
}
