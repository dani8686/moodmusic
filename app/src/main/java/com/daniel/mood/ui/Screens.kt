@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.daniel.mood.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.mood.MoodCatalog
import com.daniel.mood.MoodCount
import com.daniel.mood.PlayerSnapshot
import com.daniel.mood.Playlist
import com.daniel.mood.QueueLogic
import com.daniel.mood.ScanProgress
import com.daniel.mood.Song
import kotlin.math.ceil

data class ScreenCallbacks(
    val openCategory: (Int) -> Unit,
    val openLibrary: () -> Unit,
    val openReview: () -> Unit,
    val onSongActions: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val playPage: (List<Song>, Int, Boolean) -> Unit,
    val playAll: (Boolean) -> Unit,
    val exportFolder: () -> Unit,
    val exportZip: () -> Unit,
    val share: () -> Unit,
    val rescan: () -> Unit,
    val pauseScan: () -> Unit,
    val cancelScan: () -> Unit,
    val requestPermission: () -> Unit,
    val openPlayer: () -> Unit,
    val togglePlay: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Long) -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val changeSpeed: () -> Unit,
    val openQueue: () -> Unit,
    val createPlaylist: () -> Unit,
    val renamePlaylist: (Playlist) -> Unit,
    val deletePlaylist: (Playlist) -> Unit,
    val openPlaylist: (Long) -> Unit,
    val addSongsToPlaylist: () -> Unit
)

data class SettingsState(
    val fa: Boolean,
    val pd: Boolean,
    val dark: Boolean,
    val accurate: Boolean,
    val minSeconds: Int,
    val correctionCount: Int,
    val total: Int,
    val benchmark: String,
    val onLanguage: () -> Unit,
    val onDigits: (Boolean) -> Unit,
    val onDark: (Boolean) -> Unit,
    val onAccurate: (Boolean) -> Unit,
    val onMinSeconds: () -> Unit,
    val onReanalyzeAll: () -> Unit,
    val onApplyLearning: () -> Unit,
    val onBenchmark: () -> Unit,
    val onBattery: () -> Unit
)

private fun placeholderSong(): Song = Song(
    id = -1L, uri = "", title = "", artist = "", album = "", albumId = 0L,
    duration = 0L, size = 0L, modified = 0L, added = 0L,
    mood = MoodCatalog.EMOTIONAL, predictedMood = MoodCatalog.EMOTIONAL,
    manualMood = null, secondary = MoodCatalog.CALM, confidence = 0f,
    bpm = 0f, energy = 0f, reason = "", manual = false, seen = 0L
)

@Composable
fun HomeScreen(
    total: Int,
    counts: List<MoodCount>,
    progress: ScanProgress,
    fa: Boolean,
    pd: Boolean,
    permission: Boolean,
    correctionCount: Int,
    callbacks: ScreenCallbacks,
    modifier: Modifier = Modifier
) {
    val countMap = counts.associate { it.mood to it.count }
    val cards = listOf(MoodCatalog.ALL) + (0 until MoodCatalog.COUNT).toList()
    Box(modifier.fillMaxSize().background(moodBrush(MoodCatalog.EMOTIONAL, true))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        MoodCatalog.tr(fa, "Mood Music Sorter", "مرتب\u200cساز مود موزیک"),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        MoodCatalog.tr(fa, "Danial Mousavi  •  on-device audio mood", "دانیال موسوی  •  تشخیص حال\u200cوهوا از خود صدا"),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
            if (!permission) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDarkAlt)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                MoodCatalog.tr(
                                    fa,
                                    "Allow access to audio files so the library can be analysed.",
                                    "برای تحلیل کتابخانه، دسترسی به فایل\u200cهای صوتی را فعال کنید."
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = callbacks.requestPermission) {
                                Text(MoodCatalog.tr(fa, "Grant permission", "اجازه دسترسی"))
                            }
                        }
                    }
                }
            }
            item {
                ScanProgressCard(
                    progress = progress,
                    fa = fa,
                    pd = pd,
                    onPause = callbacks.pauseScan,
                    onCancel = callbacks.cancelScan,
                    onRescan = callbacks.rescan
                )
            }
            item {
                SectionHeader(MoodCatalog.tr(fa, "Moods", "حال\u200cوهوا\u200cها")) {
                    TextButton(onClick = callbacks.openLibrary) {
                        Text(MoodCatalog.tr(fa, "All songs", "همه آهنگ\u200cها"))
                    }
                }
            }
            item {
                BoxWithConstraints {
                    val columns = if (maxWidth >= 640.dp) 3 else 2
                    val rows = ceil(cards.size / columns.toFloat()).toInt()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.height((rows * 122).dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(cards, key = { it }) { id ->
                            MoodCard(
                                id = id,
                                count = if (id == MoodCatalog.ALL) total else (countMap[id] ?: 0),
                                fa = fa,
                                pd = pd,
                                onClick = { if (id == MoodCatalog.ALL) callbacks.openLibrary() else callbacks.openCategory(id) }
                            )
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDarkAlt)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            MoodCatalog.tr(fa, "Learning from your corrections", "یادگیری از اصلاح\u200cهای شما"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            MoodCatalog.tr(fa, "Manual corrections", "اصلاح\u200cهای دستی") + ": " +
                                MoodCatalog.digits(correctionCount, pd) + "  •  " +
                                MoodCatalog.tr(
                                    fa,
                                    "Corrections build mood prototypes and re-score the library.",
                                    "اصلاح\u200cها الگوهای صوتی می\u200cسازند و کتابخانه دوباره امتیازدهی می\u200cشود."
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        TextButton(onClick = callbacks.openReview) {
                            Text(MoodCatalog.tr(fa, "Review low-confidence songs", "بررسی آهنگ\u200cهای کم\u200cاطمینان"))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }
}

@Composable
private fun SongsHeader(
    title: String,
    query: String,
    sort: Int,
    fa: Boolean,
    pd: Boolean,
    offset: Int,
    pageCount: Int,
    callbacks: ScreenCallbacks,
    onQuery: (String) -> Unit,
    onSort: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    vertical: Boolean
) {
    val sortNames = listOf(
        MoodCatalog.tr(fa, "Title", "نام"),
        MoodCatalog.tr(fa, "Artist", "خواننده"),
        MoodCatalog.tr(fa, "Date added", "تاریخ"),
        "BPM",
        MoodCatalog.tr(fa, "Energy", "انرژی"),
        MoodCatalog.tr(fa, "Duration", "مدت")
    )
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(MoodCatalog.tr(fa, "Search title or artist", "جستجوی نام یا خواننده")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        if (vertical) {
            ActionButtonRow(fa, callbacks)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ActionButtons(fa, callbacks)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSort) {
                Text(MoodCatalog.tr(fa, "Sort", "ترتیب") + ": " + sortNames[sort.coerceIn(0, sortNames.lastIndex)])
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onPrev, enabled = offset > 0) {
                Text(MoodCatalog.tr(fa, "Previous", "قبلی"))
            }
            Text(
                MoodCatalog.digits(offset + 1, pd) + "–" + MoodCatalog.digits(offset + pageCount, pd),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            TextButton(onClick = onNext, enabled = pageCount >= com.daniel.mood.SearchQuery.PAGE_SIZE) {
                Text(MoodCatalog.tr(fa, "Next", "بعدی"))
            }
        }
    }
}

@Composable
private fun ActionButtonRow(fa: Boolean, callbacks: ScreenCallbacks) {
    Column {
        TextButton(onClick = { callbacks.playAll(false) }) { Text("▶  " + MoodCatalog.tr(fa, "Play all", "پخش همه")) }
        TextButton(onClick = { callbacks.playAll(true) }) { Text("⤨  " + MoodCatalog.tr(fa, "Shuffle mix", "پخش تصادفی")) }
        TextButton(onClick = callbacks.exportFolder) { Text(MoodCatalog.tr(fa, "Export to folder / OTG", "خروجی به پوشه / فلش")) }
        TextButton(onClick = callbacks.exportZip) { Text(MoodCatalog.tr(fa, "Export ZIP", "خروجی ZIP")) }
        TextButton(onClick = callbacks.share) { Text(MoodCatalog.tr(fa, "Share category", "اشتراک\u200cگذاری دسته")) }
    }
}

@Composable
private fun RowScope.ActionButtons(fa: Boolean, callbacks: ScreenCallbacks) {
    TextButton(onClick = { callbacks.playAll(false) }) { Text("▶ " + MoodCatalog.tr(fa, "Play", "پخش")) }
    TextButton(onClick = { callbacks.playAll(true) }) { Text("⤨ " + MoodCatalog.tr(fa, "Shuffle", "تصادفی")) }
    TextButton(onClick = callbacks.exportFolder) { Text(MoodCatalog.tr(fa, "Folder", "پوشه")) }
    TextButton(onClick = callbacks.exportZip) { Text("ZIP") }
    TextButton(onClick = callbacks.share) { Text(MoodCatalog.tr(fa, "Share", "اشتراک")) }
}

@Composable
fun SongsScreen(
    title: String,
    songs: List<Song>,
    query: String,
    sort: Int,
    offset: Int,
    fa: Boolean,
    pd: Boolean,
    actions: SongActions,
    callbacks: ScreenCallbacks,
    onQuery: (String) -> Unit,
    onSort: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize().background(moodBrush(MoodCatalog.CALM, true))) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize().padding(16.dp)) {
                Column(Modifier.width(260.dp)) {
                    SongsHeader(
                        title, query, sort, fa, pd, offset, songs.size, callbacks,
                        onQuery, onSort, onPrev, onNext, vertical = true
                    )
                }
                Spacer(Modifier.width(16.dp))
                SongList(
                    songs = songs,
                    fa = fa,
                    pd = pd,
                    actions = actions,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                SongsHeader(
                    title, query, sort, fa, pd, offset, songs.size, callbacks,
                    onQuery, onSort, onPrev, onNext, vertical = false
                )
                SongList(
                    songs = songs,
                    fa = fa,
                    pd = pd,
                    actions = actions,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    fa: Boolean,
    pd: Boolean,
    callbacks: ScreenCallbacks,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(moodBrush(MoodCatalog.NOSTALGIC, true)).padding(16.dp)) {
        SectionHeader(MoodCatalog.tr(fa, "Playlists", "پلی\u200cلیست\u200cها")) {
            Button(onClick = callbacks.createPlaylist) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(MoodCatalog.tr(fa, "New", "جدید"))
            }
        }
        if (playlists.isEmpty()) {
            EmptyState(
                MoodCatalog.tr(fa, "No playlists yet. Create one and add songs from any category.", "هنوز پلی\u200cلیستی نساخته\u200cاید. یکی بسازید و از هر دسته آهنگ اضافه کنید."),
                MoodCatalog.tr(fa, "Create playlist", "ساخت پلی\u200cلیست"),
                callbacks.createPlaylist
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkAlt)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("♫", color = AccentCyan, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    MoodCatalog.tr(fa, "Saved on this device", "ذخیره\u200cشده روی دستگاه"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            IconButton(onClick = { callbacks.openPlaylist(playlist.id) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Open")
                            }
                            IconButton(onClick = { callbacks.renamePlaylist(playlist) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { callbacks.deletePlaylist(playlist) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    fa: Boolean,
    pd: Boolean,
    actions: SongActions,
    callbacks: ScreenCallbacks,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(moodBrush(MoodCatalog.ROMANTIC, true)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(playlist.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { callbacks.renamePlaylist(playlist) }) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { callbacks.deletePlaylist(playlist) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
        Text(
            MoodCatalog.tr(fa, "Songs", "آهنگ\u200cها") + ": " + MoodCatalog.digits(songs.size, pd),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { callbacks.playPage(songs, 0, false) }) {
                Text("▶ " + MoodCatalog.tr(fa, "Play playlist", "پخش پلی\u200cلیست"))
            }
            TextButton(onClick = callbacks.addSongsToPlaylist) {
                Text("+ " + MoodCatalog.tr(fa, "Add songs", "افزودن آهنگ"))
            }
        }
        if (songs.isEmpty()) {
            EmptyState(MoodCatalog.tr(fa, "This playlist is empty.", "این پلی\u200cلیست خالی است."))
        } else {
            SongList(songs = songs, fa = fa, pd = pd, actions = actions, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(70.dp))
    }
}

@Composable
fun PlayerScreen(
    song: Song?,
    snapshot: PlayerSnapshot,
    fa: Boolean,
    pd: Boolean,
    callbacks: ScreenCallbacks,
    modifier: Modifier = Modifier
) {
    val display = song ?: placeholderSong()
    val accent = moodColor(display.mood)
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    val position = dragPosition ?: snapshot.position
    val duration = snapshot.duration.coerceAtLeast(0L)
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(modifier.fillMaxSize().background(moodBrush(display.mood, true))) {
        Column(
            Modifier.fillMaxSize().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                MoodCatalog.tr(fa, "Now playing", "در حال پخش"),
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            Spacer(Modifier.height(22.dp))
            CoverArt(display, 230.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                snapshot.title.ifBlank { display.title.ifBlank { MoodCatalog.tr(fa, "Nothing playing", "آهنگی در حال پخش نیست") } },
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                maxLines = 2
            )
            Text(
                snapshot.artist.ifBlank { display.artist },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(Modifier.height(12.dp))
            LabelChip(
                text = if (display.manualMood != null) {
                    MoodCatalog.tr(fa, "Manual", "دستی") + " • " + MoodCatalog.name(display.mood, fa)
                } else {
                    MoodCatalog.tr(fa, "Auto", "خودکار") + " • " + MoodCatalog.name(display.mood, fa)
                },
                color = accent
            )
            Spacer(Modifier.height(20.dp))
            Slider(
                value = fraction,
                onValueChange = { if (duration > 0) dragPosition = (it * duration).toLong() },
                onValueChangeFinished = {
                    dragPosition?.let { callbacks.seek(it) }
                    dragPosition = null
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(MoodCatalog.clock(position, pd), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(MoodCatalog.clock(duration, pd), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = callbacks.previous) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }
                IconButton(
                    onClick = callbacks.togglePlay,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (snapshot.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
                IconButton(onClick = callbacks.next) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = callbacks.toggleShuffle) {
                    Text(
                        MoodCatalog.tr(fa, "Shuffle", "تصادفی") + if (snapshot.shuffle) " ✓" else "",
                        color = if (snapshot.shuffle) AccentCyan else TextMuted
                    )
                }
                TextButton(onClick = callbacks.cycleRepeat) {
                    val label = when (snapshot.repeatMode) {
                        QueueLogic.REPEAT_ONE -> MoodCatalog.tr(fa, "Repeat one", "تکرار یکی")
                        QueueLogic.REPEAT_ALL -> MoodCatalog.tr(fa, "Repeat all", "تکرار همه")
                        else -> MoodCatalog.tr(fa, "Repeat off", "تکرار خاموش")
                    }
                    Text(label, color = if (snapshot.repeatMode != QueueLogic.REPEAT_OFF) AccentCyan else TextMuted)
                }
                TextButton(onClick = callbacks.changeSpeed) {
                    Text(MoodCatalog.tr(fa, "Speed", "سرعت") + " " + MoodCatalog.digits(snapshot.speed, pd) + "×")
                }
            }
            TextButton(onClick = callbacks.openQueue) {
                Text(MoodCatalog.tr(fa, "Play queue", "صف پخش"))
            }
        }
    }
}

@Composable
fun SettingsScreen(state: SettingsState, modifier: Modifier = Modifier) {
    val fa = state.fa
    LazyColumn(
        modifier.fillMaxSize().background(moodBrush(MoodCatalog.FOCUS, true)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { Text(MoodCatalog.tr(fa, "Settings", "تنظیمات"), style = MaterialTheme.typography.headlineSmall) }
        item {
            SettingsToggleRow(
                label = MoodCatalog.tr(fa, "Persian language (RTL)", "زبان فارسی (راست\u200cچین)"),
                checked = fa,
                onChecked = { state.onLanguage() }
            )
        }
        item {
            SettingsToggleRow(
                label = MoodCatalog.tr(fa, "Persian digits", "اعداد فارسی"),
                checked = state.pd,
                onChecked = state.onDigits
            )
        }
        item {
            SettingsToggleRow(
                label = MoodCatalog.tr(fa, "Dark theme", "پوسته تیره"),
                checked = state.dark,
                onChecked = state.onDark
            )
        }
        item {
            SettingsToggleRow(
                label = MoodCatalog.tr(fa, "Deeper analysis (slower)", "تحلیل دقیق\u200cتر (کندتر)"),
                checked = state.accurate,
                onChecked = state.onAccurate
            )
        }
        item {
            TextButton(onClick = state.onMinSeconds) {
                Text(
                    MoodCatalog.tr(fa, "Minimum duration", "حداقل مدت") + ": " +
                        MoodCatalog.digits(state.minSeconds, state.pd) + " " +
                        MoodCatalog.tr(fa, "seconds", "ثانیه")
                )
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { Text(MoodCatalog.tr(fa, "Library & learning", "کتابخانه و یادگیری"), style = MaterialTheme.typography.titleMedium) }
        item {
            Text(
                MoodCatalog.tr(fa, "Songs", "آهنگ\u200cها") + ": " + MoodCatalog.digits(state.total, state.pd) +
                    "  •  " + MoodCatalog.tr(fa, "Corrections", "اصلاح\u200cها") + ": " +
                    MoodCatalog.digits(state.correctionCount, state.pd),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        item { TextButton(onClick = state.onApplyLearning) { Text(MoodCatalog.tr(fa, "Rebuild learned prototypes", "بازسازی الگوهای یادگیری")) } }
        item { TextButton(onClick = state.onReanalyzeAll) { Text(MoodCatalog.tr(fa, "Re-analyze all audio", "تحلیل دوباره همه صداها")) } }
        item { TextButton(onClick = state.onBenchmark) { Text(MoodCatalog.tr(fa, "Benchmark 20 songs", "آزمون سرعت ۲۰ آهنگ")) } }
        if (state.benchmark.isNotBlank()) {
            item { Text(state.benchmark, style = MaterialTheme.typography.bodySmall, color = AccentCyan) }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item { Text(MoodCatalog.tr(fa, "Device & privacy", "دستگاه و حریم خصوصی"), style = MaterialTheme.typography.titleMedium) }
        item { TextButton(onClick = state.onBattery) { Text(MoodCatalog.tr(fa, "Xiaomi battery guidance", "راهنمای باتری شیائومی")) } }
        item {
            Text(
                MoodCatalog.tr(
                    fa,
                    "All analysis is local and offline. No audio leaves your phone and no network permission is requested. Mood labels are acoustic estimates, not verified emotions.",
                    "همه تحلیل\u200cها محلی و آفلاین است. هیچ فایل صوتی از گوشی خارج نمی\u200cشود و مجوز اینترنت درخواست نشده است. برچسب\u200cها تخمین صوتی هستند، نه تشخیص قطعی احساس."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        item {
            Text(
                MoodCatalog.tr(fa, "Developer: Danial Mousavi", "سازنده: دانیال موسوی") +
                    "  •  v2.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        item { Spacer(Modifier.height(70.dp)) }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
