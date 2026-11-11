package com.daniel.mood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@Composable
fun DashboardScreen(
    songs: SongDao, intelligence: IntelligenceDao, fa: Boolean, pd: Boolean,
    onLibrary: () -> Unit, onDna: () -> Unit, onJourney: () -> Unit, onDj: () -> Unit,
    onRadar: () -> Unit, onHistory: () -> Unit, onPlaylists: () -> Unit, onQueue: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var counts by remember { mutableStateOf<List<MoodCount>>(emptyList()) }
    var recent by remember { mutableStateOf<List<Song>>(emptyList()) }
    var dna by remember { mutableStateOf(TasteEngine.snapshot(null)) }
    var summary by remember { mutableStateOf<PeriodListening?>(null) }
    var recommendations by remember { mutableStateOf<List<ScoredSong>>(emptyList()) }
    var lastEvent by remember { mutableStateOf<ListeningEvent?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            counts = songs.counts().let { /* flow is observed below through one-shot page fallback */ emptyList() }
            counts = Moods.all.map { mood -> MoodCount(mood, songs.playlist(mood).size) }
            recent = songs.page(-1, "", 0, 20, 0).first()
            dna = TasteEngine.snapshot(intelligence.taste())
            val range = TimeRanges.thisMonth(); summary = intelligence.period(range.first, range.second)
            val events = intelligence.recentEvents(100); lastEvent = events.firstOrNull()
            val all = songs.playlist(-1)
            recommendations = RadarEngine.recommend(all, intelligence.taste(), lastEvent?.songId?.let { id -> songs.get(id) }, events, 8)
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V3Header(tr(fa, "Your music intelligence", "هوش موسیقی تو"), tr(fa, "Home • ${counts.sumOf { it.count }} local songs", "خانه • ${counts.sumOf { it.count }} آهنگ محلی"), Icons.Default.GraphicEq) }
        item { V3Card {
            Text(tr(fa, "Quick actions", "دسترسی سریع"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onLibrary) { Icon(Icons.Default.LibraryMusic, tr(fa, "Library", "کتابخانه")) }
                IconButton(onClick = onJourney) { Icon(Icons.Default.Route, tr(fa, "Journey", "سفر")) }
                IconButton(onClick = onDj) { Icon(Icons.Default.AutoAwesome, tr(fa, "Smart DJ", "دی‌جِی")) }
                IconButton(onClick = onRadar) { Icon(Icons.Default.TravelExplore, tr(fa, "Radar", "رادار")) }
                IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, tr(fa, "Queue", "صف")) }
            }
            OutlinedButton(onClick = { onPlay(recent, 0) }, enabled = recent.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null); Text(tr(fa, "Play recently added", "پخش آهنگ‌های تازه"))
            }
        } }
        item { V3Action(tr(fa, "Music DNA", "DNA موسیقی"), tr(fa, "${dna.eventCount} real sessions • ${(dna.averageEnergy*100).toInt()}% energy", "${dna.eventCount} جلسه واقعی • انرژی ${(dna.averageEnergy*100).toInt()}٪"), Icons.Default.Insights, onDna) }
        item { V3Action(tr(fa, "Time Machine", "ماشین زمان"), tr(fa, "${clock(summary?.listenedMs ?: 0, pd)} this month", "${clock(summary?.listenedMs ?: 0, pd)} در این ماه"), Icons.Default.History, onHistory) }
        item { V3Action(tr(fa, "Saved playlists", "پلی‌لیست‌های ذخیره‌شده"), tr(fa, "Manual, Journey and Twin collections", "مجموعه‌های دستی، Journey و Twin"), Icons.Default.LibraryMusic, onPlaylists) }
        item { Text(tr(fa, "For you", "برای تو"), style = MaterialTheme.typography.titleMedium) }
        if (recommendations.isEmpty()) item { Text(tr(fa, "Listen and analyze your library to unlock local recommendations.", "برای پیشنهاد محلی، کتابخانه را تحلیل کن و موسیقی گوش بده.")) }
        items(recommendations.size) { index ->
            val result = recommendations[index]
            V3Card {
                Text(result.song.title, style = MaterialTheme.typography.titleMedium)
                Text(result.song.artist)
                Text(recommendationReason(result.reason, fa), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onPlay(listOf(result.song), 0) }) { Icon(Icons.Default.PlayArrow, null); Text(tr(fa, "Play", "پخش")) }
            }
        }
        item { Text(tr(fa, "Mood library", "کتابخانه حال‌وهوا"), style = MaterialTheme.typography.titleMedium) }
        items(counts) { count -> TextButton(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) { Text("${Moods.name(count.mood, fa)} • ${count.count}") } }
    }
}
