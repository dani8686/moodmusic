package com.daniel.mood

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import kotlinx.coroutines.*
import java.util.Date

@Composable internal fun V3Header(title: String, subtitle: String? = null, icon: ImageVector) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(12.dp))
        Column { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}
@Composable internal fun V3Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
@Composable internal fun V3Action(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}
@Composable internal fun LoadingLine() { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

@Composable
fun DashboardScreen(
    songs: SongDao, intelligence: IntelligenceDao, fa: Boolean, pd: Boolean,
    onLibrary: () -> Unit, onDna: () -> Unit, onJourney: () -> Unit, onDj: () -> Unit,
    onRadar: () -> Unit, onHistory: () -> Unit, onPlaylists: () -> Unit, onQueue: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit
) {
    var recent by remember { mutableStateOf<List<Song>>(emptyList()) }
    var week by remember { mutableStateOf<PeriodListening?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val events = intelligence.recentEvents(50)
            recent = events.mapNotNull { songs.get(it.songId) }.distinctBy { it.id }.take(8)
            val range = TimeRanges.daysBack(7)
            week = intelligence.period(range.first, range.second)
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mood Music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(tr(fa, "Your personal music intelligence", "هوش شخصی موسیقی شما"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            V3Card {
                Text(tr(fa, "This week", "این هفته"), style = MaterialTheme.typography.titleMedium)
                Text(clock(week?.listenedMs ?: 0, pd), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(tr(fa, "${week?.events ?: 0} listening sessions • ${week?.skips ?: 0} early skips", "${week?.events ?: 0} جلسه شنیدن • ${week?.skips ?: 0} ردکردن زودهنگام"), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onHistory) { Text(tr(fa, "Open Time Machine", "ورود به ماشین زمان")) }
            }
        }
        if (recent.isNotEmpty()) item {
            Text(tr(fa, "Recently played", "اخیراً پخش‌شده"), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recent) { song ->
                    ElevatedCard(onClick = { onPlay(recent, recent.indexOf(song)) }, modifier = Modifier.width(150.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Artwork(albumArtwork(song.albumId), Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { V3Action(tr(fa, "Your Music DNA", "DNA موسیقی شما"), tr(fa, "Explainable taste from real listening", "سلایق قابل توضیح از شنیدن واقعی"), Icons.Default.Insights, onDna) }
        item { V3Action(tr(fa, "Mood Journey", "سفر حال‌وهوا"), tr(fa, "Move gradually between two moods", "حرکت تدریجی بین دو حال‌وهوا"), Icons.Default.Route, onJourney) }
        item { V3Action(tr(fa, "Smart DJ", "دی‌جِی هوشمند"), tr(fa, "Real crossfade, BPM and energy matching", "کراس‌فید واقعی و تطبیق BPM و انرژی"), Icons.Default.GraphicEq, onDj) }
        item { V3Action(tr(fa, "Music Radar", "رادار موسیقی"), tr(fa, "Local and online discoveries with reasons", "کشف محلی و آنلاین همراه دلیل"), Icons.Default.TravelExplore, onRadar) }
        item { V3Action(tr(fa, "Your library", "کتابخانه شما"), tr(fa, "Moods, songs and all categories", "حال‌وهواها، آهنگ‌ها و دسته‌ها"), Icons.Default.LibraryMusic, onLibrary) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPlaylists, modifier = Modifier.weight(1f)) { Text(tr(fa, "Playlists", "پلی‌لیست‌ها")) }
                OutlinedButton(onClick = onQueue, modifier = Modifier.weight(1f)) { Text(tr(fa, "Queue", "صف پخش")) }
            }
        }
    }
}

@Composable
fun MusicDnaScreen(intelligence: IntelligenceDao, songs: SongDao, fa: Boolean, pd: Boolean, onReset: () -> Unit) {
    var profile by remember { mutableStateOf<TasteProfile?>(null) }
    var moods by remember { mutableStateOf<List<MoodListening>>(emptyList()) }
    var top by remember { mutableStateOf<List<SongListening>>(emptyList()) }
    var thisMonth by remember { mutableStateOf<PeriodListening?>(null) }
    var lastMonth by remember { mutableStateOf<PeriodListening?>(null) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            profile = intelligence.taste()
            val current = TimeRanges.thisMonth(); val previous = TimeRanges.lastMonth()
            thisMonth = intelligence.period(current.first, current.second)
            lastMonth = intelligence.period(previous.first, previous.second)
            moods = intelligence.moods(current.first, current.second)
            top = intelligence.topSongs(current.first, current.second, 5)
        }
    }
    val snapshot = TasteEngine.snapshot(profile)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { V3Header(tr(fa, "Music DNA", "DNA موسیقی"), tr(fa, "Built from listening time, skips and explicit feedback", "ساخته‌شده از زمان شنیدن، ردکردن و بازخورد مستقیم"), Icons.Default.Insights) }
        if (snapshot.totalWeight <= 0) item { EmptyView(tr(fa, "Listen to music to start building your Music DNA", "برای ساخت DNA، به موسیقی گوش کنید"), Icons.Default.Insights) }
        else {
            item { V3Card {
                Text(tr(fa, "Favorite audio characteristics", "ویژگی‌های صوتی محبوب"), style = MaterialTheme.typography.titleMedium)
                DnaBar(tr(fa, "Energy", "انرژی"), snapshot.averageEnergy)
                DnaBar(tr(fa, "Fast rhythm preference", "علاقه به ریتم تند"), snapshot.features[1])
                DnaBar(tr(fa, "Vocal preference", "علاقه به آهنگ با صدا"), snapshot.vocalPreference)
                DnaBar(tr(fa, "Minor / emotional color", "رنگ مینور / احساسی"), snapshot.features[6])
                Text(tr(fa, "Estimated preferred tempo: ${snapshot.averageTempo.toInt()} BPM", "تمپوی ترجیحی تخمینی: ${snapshot.averageTempo.toInt()} BPM"), style = MaterialTheme.typography.bodySmall)
            } }
            item { V3Card {
                Text(tr(fa, "Favorite mood distribution", "توزیع حال‌وهوای محبوب"), style = MaterialTheme.typography.titleMedium)
                val max = snapshot.moods.maxOrNull()?.coerceAtLeast(.001f) ?: 1f
                Moods.all.forEach { mood -> if (snapshot.moods[mood] > 0) DnaBar(Moods.name(mood, fa), snapshot.moods[mood] / max) }
            } }
            item { V3Card {
                Text(tr(fa, "Taste evolution", "تحول سلایق"), style = MaterialTheme.typography.titleMedium)
                Text(tr(fa, "This month: ${thisMonth?.events ?: 0} sessions / ${((thisMonth?.listenedMs ?: 0) / 60000)} min", "این ماه: ${thisMonth?.events ?: 0} جلسه / ${(thisMonth?.listenedMs ?: 0) / 60000} دقیقه"))
                Text(tr(fa, "Last month: ${lastMonth?.events ?: 0} sessions / ${((lastMonth?.listenedMs ?: 0) / 60000)} min", "ماه قبل: ${lastMonth?.events ?: 0} جلسه / ${(lastMonth?.listenedMs ?: 0) / 60000} دقیقه"))
                if (top.isNotEmpty()) Text(tr(fa, "Current top: ${top.first().title}", "برترین فعلی: ${top.first().title}"), style = MaterialTheme.typography.bodySmall)
            } }
        }
        item { OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text(tr(fa, "Reset Music DNA", "بازنشانی DNA موسیقی")) } }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(tr(fa, "Reset Music DNA?", "DNA بازنشانی شود؟")) },
        text = { Text(tr(fa, "This deletes the learned preference profile. Listening history and songs remain.", "فقط پروفایل سلایق حذف می‌شود؛ تاریخچه و آهنگ‌ها باقی می‌مانند.")) },
        confirmButton = { TextButton(onClick = { onReset(); profile = null; confirm = false }) { Text(tr(fa, "Reset", "بازنشانی")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr(fa, "Cancel", "لغو")) } })
}

@Composable private fun DnaBar(label: String, value: Float) {
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodySmall); Text("${(value.coerceIn(0f,1f)*100).toInt()}%") }
        LinearProgressIndicator(progress = { value.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
}

@Composable
fun RadarScreen(
    songs: SongDao, intelligence: IntelligenceDao, currentSongId: Long?, fa: Boolean, pd: Boolean,
    onPlay: (List<Song>, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var local by remember { mutableStateOf<List<ScoredSong>>(emptyList()) }
    var contextSong by remember { mutableStateOf<Song?>(null) }
    var online by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val wishlist by intelligence.wishlist().collectAsState(initial = emptyList())

    suspend fun refresh(similar: Song? = contextSong, force: Boolean = false) {
        loading = true; error = ""
        withContext(Dispatchers.IO) {
            val all = songs.playlist(-1); val profile = intelligence.taste(); val events = intelligence.recentEvents(500)
            val key = if (similar != null) "song:${similar.id}:${similar.featureData.hashCode()}" else "dna:${profile?.updatedAt ?: 0}"
            val cached = if (!force) intelligence.recommendations(key) else emptyList()
            if (cached.isNotEmpty()) {
                local = cached.mapNotNull { rec -> songs.get(rec.songId)?.let { ScoredSong(it, rec.score, rec.reason) } }
            } else {
                val result = RadarEngine.recommend(all, profile, similar, events, 40, discover = similar == null)
                local = result
                intelligence.replaceRecommendations(key, result.map { Recommendation(songId = it.song.id, contextKey = key, reason = it.reason, score = it.score, generatedAt = System.currentTimeMillis()) })
            }
        }
        loading = false
    }
    LaunchedEffect(Unit) { contextSong = withContext(Dispatchers.IO) { currentSongId?.let { songs.get(it) } }; refresh() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        V3Header(tr(fa, "Music Radar", "رادار موسیقی"), tr(fa, "Every suggestion includes acoustic evidence", "هر پیشنهاد با شواهد صوتی"), Icons.Default.TravelExplore)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr(fa, "Local", "محلی")) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr(fa, "Online", "آنلاین")) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(tr(fa, "Wishlist", "علاقه‌مندی")) })
        }
        when (tab) {
            0 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { contextSong = null; refresh(null, true) } }) { Text(tr(fa, "Match my DNA", "مطابق DNA من")) }
                    TextButton(enabled = contextSong != null, onClick = { scope.launch { refresh(contextSong, true) } }) { Text(tr(fa, "Similar to current", "مشابه آهنگ فعلی")) }
                    IconButton(onClick = { scope.launch { refresh(contextSong, true) } }) { Icon(Icons.Default.Refresh, null) }
                }
                if (loading) LoadingLine()
                if (local.isEmpty() && !loading) EmptyView(tr(fa, "Analyze your library to unlock recommendations", "برای پیشنهادها، کتابخانه را تحلیل کنید"), Icons.Default.TravelExplore)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(local, key = { it.song.id }) { scored ->
                        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(albumArtwork(scored.song.albumId), Modifier.size(54.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(scored.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(recommendationReason(scored.reason, fa), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row {
                                        TextButton(onClick = { onPlay(local.map { it.song }, local.indexOf(scored)) }) { Text(tr(fa, "Play", "پخش")) }
                                        TextButton(onClick = { contextSong = scored.song; scope.launch { refresh(scored.song, true) } }) { Text(tr(fa, "Similar", "مشابه")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(tr(fa, "Artist, track or mood", "هنرمند، آهنگ یا حال‌وهوا")) }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Row {
                    Button(onClick = {
                        if (!AiSettings.enabled(context) || !AiSettings.catalogEnabled(context)) { error = tr(fa, "Enable online services and catalog provider first", "ابتدا سرویس آنلاین و Provider کاتالوگ را فعال کنید"); return@Button }
                        scope.launch {
                            loading = true; error = ""
                            OnlineGate(context).check()?.let { error = it; loading = false; return@launch }
                            try {
                                OnlineGate(context).record()
                                online = withContext(Dispatchers.IO) { ProviderRegistry(context).catalog.search(query) }
                            } catch (e: Exception) { error = e.message ?: "Online error" }
                            loading = false
                        }
                    }, modifier = Modifier.padding(vertical = 8.dp)) { Text(tr(fa, "Search catalog", "جستجوی کاتالوگ")) }
                    TextButton(onClick = { query = contextSong?.artist ?: query }) { Text(tr(fa, "Use current artist", "هنرمند فعلی")) }
                }
                Text(tr(fa, "Catalog metadata only. No free download is implied; external store/listening links are provider-controlled.", "فقط متادیتای کاتالوگ؛ دانلود رایگان تلقی نشود و لینک شنیدن تابع قوانین Provider است."), style = MaterialTheme.typography.bodySmall)
                if (loading) LoadingLine()
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(online, key = { it.id }) { track -> OnlineTrackRow(track, fa, onWishlist = {
                        scope.launch(Dispatchers.IO) { intelligence.addWishlist(OnlineWishlist(track.id, track.title, track.artist, track.artwork, track.externalUrl, System.currentTimeMillis())) }
                    }) }
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(wishlist, key = { it.trackId }) { item ->
                    OnlineTrackRow(OnlineTrack(item.trackId, item.title, item.artist, "", item.artwork, item.externalUrl, ""), fa, onWishlist = {
                        scope.launch(Dispatchers.IO) { intelligence.removeWishlist(item.trackId) }
                    }, saved = true)
                }
            }
        }
    }
}

@Composable private fun OnlineTrackRow(track: OnlineTrack, fa: Boolean, onWishlist: () -> Unit, saved: Boolean = false) {
    val context = LocalContext.current
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artwork.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }, Modifier.size(56.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
            IconButton(onClick = onWishlist) { Icon(if (saved) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, null) }
            IconButton(enabled = track.externalUrl.isNotBlank(), onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.externalUrl))) } catch (_: Exception) {} }) { Icon(Icons.Default.OpenInNew, null) }
        }
    }
}
