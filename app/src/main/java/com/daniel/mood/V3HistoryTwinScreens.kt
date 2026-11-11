package com.daniel.mood

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId

@Composable
fun TimeMachineScreen(songs: SongDao, intelligence: IntelligenceDao, playlists: PlaylistDao, fa: Boolean, pd: Boolean, onPlay: (List<Song>, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableIntStateOf(1) }
    var range by remember { mutableStateOf(TimeRanges.thisMonth()) }
    var summary by remember { mutableStateOf<PeriodListening?>(null) }
    var daily by remember { mutableStateOf<List<DailyListening>>(emptyList()) }
    var topSongs by remember { mutableStateOf<List<SongListening>>(emptyList()) }
    var topArtists by remember { mutableStateOf<List<ArtistListening>>(emptyList()) }
    var moods by remember { mutableStateOf<List<MoodListening>>(emptyList()) }
    var onThisDay by remember { mutableStateOf<List<ListeningEvent>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try { context.contentResolver.openOutputStream(uri)?.use { it.write(exportText.toByteArray()) } } catch (_: Exception) { }
        }
    }
    suspend fun load() {
        withContext(Dispatchers.IO) {
            range = when (period) {
                0 -> TimeRanges.daysBack(7); 2 -> TimeRanges.lastMonth(); 3 -> TimeRanges.daysBack(365 * 5); else -> TimeRanges.thisMonth()
            }
            summary = intelligence.period(range.first, range.second)
            daily = intelligence.daily(range.first, range.second)
            topSongs = intelligence.topSongs(range.first, range.second, 15)
            topArtists = intelligence.topArtists(range.first, range.second, 10)
            moods = intelligence.moods(range.first, range.second)
            if (period == 3) {
                val now = java.time.LocalDate.now()
                onThisDay = intelligence.eventsBetween(range.first, range.second).filter {
                    val date = Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    date.month == now.month && date.dayOfMonth == now.dayOfMonth && date.year < now.year
                }
            }
            val lines = mutableListOf("event_id,song_id,title,started_at,listened_ms,track_duration_ms,completion_ratio,skipped,source")
            intelligence.eventsBetween(range.first, range.second).forEach { event ->
                val title = songs.get(event.songId)?.title?.replace(',', ' ') ?: "deleted"
                lines += "${event.eventId},${event.songId},$title,${event.startedAt},${event.listenedMs},${event.trackDurationMs},${event.completionRatio},${event.skipped},${event.source}"
            }
            exportText = lines.joinToString("\n")
        }
    }
    LaunchedEffect(period) { load() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V3Header(tr(fa, "Time Machine", "ماشین زمان"), tr(fa, "Only real events recorded by V3 and later", "فقط رویدادهای واقعی ثبت‌شده از V3 به بعد"), Icons.Default.History) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(4) { index -> FilterChip(selected = period == index, onClick = { period = index }, label = { Text(listOf(tr(fa, "Week", "هفته"), tr(fa, "This month", "این ماه"), tr(fa, "Last month", "ماه قبل"), tr(fa, "On this day", "در چنین روزی"))[index]) }) }
        } }
        item { V3Card {
            Text(clock(summary?.listenedMs ?: 0, pd), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(tr(fa, "${summary?.events ?: 0} sessions • ${summary?.skips ?: 0} early skips", "${summary?.events ?: 0} جلسه • ${summary?.skips ?: 0} ردکردن زودهنگام"))
            Row {
                TextButton(onClick = { exporter.launch("MoodMusic-history-${System.currentTimeMillis()}.csv") }) { Icon(Icons.Default.FileDownload, null); Text("CSV") }
                TextButton(enabled = topSongs.isNotEmpty(), onClick = {
                    scope.launch(Dispatchers.IO) {
                        val id = playlists.create(Playlist(name = "Time Machine ${TimeRanges.label(System.currentTimeMillis())}", createdAt = System.currentTimeMillis()))
                        topSongs.forEach { playlists.addSong(id, it.id) }
                    }
                }) { Text(tr(fa, "Save historical playlist", "ذخیره پلی‌لیست تاریخی")) }
            }
        } }
        if (period == 3 && onThisDay.isNotEmpty()) item { V3Card {
            Text(tr(fa, "On this day in previous years", "در چنین روزی در سال‌های قبل"), style = MaterialTheme.typography.titleMedium)
            onThisDay.take(10).forEach { event -> Text("${TimeRanges.label(event.startedAt)} • ${clock(event.listenedMs, pd)}") }
        } }
        if (topSongs.isNotEmpty()) item { V3Card {
            Text(tr(fa, "Most-played songs", "پرطرفدار‌ترین آهنگ‌ها"), style = MaterialTheme.typography.titleMedium)
            topSongs.take(8).forEach { song -> Text("${song.title} • ${clock(song.listenedMs, pd)} • ${song.plays}×") }
            TextButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val list = topSongs.mapNotNull { songs.get(it.id) }
                    withContext(Dispatchers.Main) { onPlay(list, "history") }
                }
            }) { Text(tr(fa, "Play this period", "پخش این دوره")) }
        } }
        if (topArtists.isNotEmpty()) item { V3Card {
            Text(tr(fa, "Most-played artists", "پرطرفدار‌ترین هنرمندها"), style = MaterialTheme.typography.titleMedium)
            topArtists.take(6).forEach { Text("${it.artist.ifBlank { "Unknown" }} • ${clock(it.listenedMs, pd)}") }
        } }
        if (moods.isNotEmpty()) item { V3Card {
            Text(tr(fa, "Mood distribution", "توزیع حال‌وهوا"), style = MaterialTheme.typography.titleMedium)
            moods.forEach { Text("${Moods.name(it.mood, fa)} • ${clock(it.listenedMs, pd)}") }
        } }
        item { Text(tr(fa, "Listening calendar", "تقویم شنیدن"), style = MaterialTheme.typography.titleMedium) }
        items(daily, key = { it.day }) { day ->
            V3Card { Row { Text(day.day, Modifier.weight(1f)); Text(clock(day.listenedMs, pd)); Text("  •  ${day.events}") } }
        }
        item { OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteForever, null); Text(tr(fa, "Delete listening history", "حذف تاریخچه شنیدن")) } }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(tr(fa, "Delete all listening history?", "همه تاریخچه حذف شود؟")) },
        text = { Text(tr(fa, "This cannot be undone. Music DNA is managed separately.", "این کار برگشت‌پذیر نیست. DNA جداگانه مدیریت می‌شود.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { withContext(Dispatchers.IO) { intelligence.clearHistory() }; load() } }) { Text(tr(fa, "Delete", "حذف")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr(fa, "Cancel", "لغو")) } })
}

private data class TwinMessage(val user: Boolean, val text: String)

@Composable
fun TwinScreen(
    intelligence: IntelligenceDao, currentSongId: Long?, fa: Boolean,
    onAction: suspend (TwinAction) -> String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingDestructive by remember { mutableStateOf<TwinAction?>(null) }
    val messages = remember { mutableStateListOf(TwinMessage(false, if (fa) "از داده‌های واقعی کتابخانه و تاریخچه تو استفاده می‌کنم." else "I use your real library and listening history.")) }
    val examples = if (fa) listOf("یه آهنگ آروم پخش کن", "آهنگ‌های فراموش‌شده رو پیدا کن", "یه سفر از غمگین به پرانرژی بساز", "سلایقمو توضیح بده")
        else listOf("Play something calm", "Find forgotten songs", "Build a 45 minute journey", "Explain my music taste")

    suspend fun execute(action: TwinAction) {
        val answer = onAction(action)
        messages += TwinMessage(false, answer)
    }
    fun send() {
        if (input.isBlank() || busy) return
        val request = input; input = ""; messages += TwinMessage(true, request)
        scope.launch {
            busy = true
            try {
                var action = TwinParser.parse(request, currentSongId)
                if (action is TwinAction.Unknown && AiSettings.enabled(context) && ProviderRegistry(context).configured()) {
                    OnlineGate(context).check()?.let { messages += TwinMessage(false, it); return@launch }
                    OnlineGate(context).record()
                    val dna = withContext(Dispatchers.IO) { TasteEngine.snapshot(intelligence.taste()).let { "energy=${it.averageEnergy},tempo=${it.averageTempo},vocal=${it.vocalPreference}" } }
                    try {
                        action = TwinParser.interpretOnline(context, request, currentSongId, dna)
                        intelligence.log(AiProcessingLog(provider = "Gemini", model = AiSettings.model(context), processingMode = AiSettings.mode(context), operation = "music_twin", status = "success", createdAt = System.currentTimeMillis()))
                    } catch (e: Exception) {
                        messages += TwinMessage(false, tr(fa, "Online unavailable; local fallback: ${e.message}", "آنلاین در دسترس نیست؛ Fallback محلی: ${e.message}"))
                    }
                }
                if (action is TwinAction.ResetDna || action is TwinAction.DeleteHistory) pendingDestructive = action
                else execute(action)
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        V3Header(tr(fa, "AI Music Twin", "تویین هوشمند موسیقی"), tr(fa, "Controlled actions • Persian + English • offline fallback", "Action کنترل‌شده • فارسی و انگلیسی • Fallback آفلاین"), Icons.Default.SmartToy)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(examples.size) { index -> AssistChip(onClick = { input = examples[index] }, label = { Text(examples[index], maxLines = 1) }) }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(messages.size) { index ->
                val message = messages[index]
                Row(horizontalArrangement = if (message.user) Arrangement.End else Arrangement.Start, modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(18.dp), color = if (message.user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth(.86f)) {
                        Text(message.text, modifier = Modifier.padding(13.dp), color = if (message.user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (busy) item { LoadingLine() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text(tr(fa, "Ask about your music…", "درباره موسیقی‌ات بپرس…")) })
            IconButton(onClick = { send() }, enabled = !busy) { Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
    pendingDestructive?.let { action -> AlertDialog(onDismissRequest = { pendingDestructive = null },
        title = { Text(tr(fa, "Confirm destructive action", "تأیید عمل حذفی")) },
        text = { Text(tr(fa, "This action deletes local learned/history data. Continue?", "این عمل داده محلی یادگیری/تاریخچه را حذف می‌کند. ادامه می‌دهید؟")) },
        confirmButton = { TextButton(onClick = { pendingDestructive = null; scope.launch { execute(action) } }) { Text(tr(fa, "Confirm", "تأیید")) } },
        dismissButton = { TextButton(onClick = { pendingDestructive = null }) { Text(tr(fa, "Cancel", "لغو")) } })
    }
}
