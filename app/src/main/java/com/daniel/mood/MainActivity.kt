package com.daniel.mood

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.max

internal fun tr(fa: Boolean, en: String, persian: String) = if (fa) persian else en
internal fun digits(value: Any, faDigits: Boolean): String = value.toString().let {
    if (!faDigits) it else it.map { c -> if (c in '0'..'9') ('۰'.code + c.code - '0'.code).toChar() else c }.joinToString("")
}
internal fun clock(ms: Long, pd: Boolean): String {
    val seconds = max(0, ms / 1000)
    return digits("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", pd)
}
private data class NameDialog(val playlistId: Long?, val initial: String = "", val addSongId: Long? = null)

class MainActivity : ComponentActivity() {
    private var permissionChanged by mutableIntStateOf(0)
    private var exportCategory = -1
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionChanged++ }
    private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) startExport(uri, false)
    }
    private val zipLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) startExport(uri, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                onRequest = {
                    val permissions = mutableListOf(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
                    if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
                    requestPermissions.launch(permissions.toTypedArray())
                },
                permissionTick = permissionChanged,
                onFolderExport = { category -> exportCategory = category; folderLauncher.launch(null) },
                onZipExport = { category -> exportCategory = category; zipLauncher.launch("MoodMusic-${Moods.english.getOrElse(category) { "Songs" }}.zip") }
            )
        }
    }
    override fun onResume() { super.onResume(); permissionChanged++ }

    private fun startExport(uri: Uri, zip: Boolean) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) { }
        try {
            val intent = Intent(this, ExportService::class.java).apply {
                action = if (zip) ExportService.ACTION_ZIP else ExportService.ACTION_COPY
                putExtra("destination", uri)
                putExtra("category", exportCategory)
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Export unavailable", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(
    onRequest: () -> Unit, permissionTick: Int,
    onFolderExport: (Int) -> Unit, onZipExport: (Int) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var fa by remember { mutableStateOf(prefs.getBoolean("fa", true)) }
    var persianDigits by remember { mutableStateOf(prefs.getBoolean("digits", true)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    val pd = fa && persianDigits
    var screen by remember { mutableStateOf("dashboard") }
    var category by remember { mutableIntStateOf(-1) }
    var openPlaylist by remember { mutableLongStateOf(-1L) }
    var correction by remember { mutableStateOf<Song?>(null) }
    var songMenu by remember { mutableStateOf<Song?>(null) }
    var playlistPicker by remember { mutableStateOf<Song?>(null) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var onboarding by remember { mutableStateOf(!prefs.getBoolean("batterySeen", false)) }
    var playerOpen by remember { mutableStateOf(false) }
    var playerRevision by remember { mutableIntStateOf(0) }
    var showExportResult by remember { mutableStateOf(false) }
    var onlineRequest by remember { mutableStateOf<Song?>(null) }
    var onlineBusy by remember { mutableStateOf(false) }
    var lyricsResult by remember { mutableStateOf<LyricsResult?>(null) }
    val scope = rememberCoroutineScope()
    val database = remember { MoodDatabase.get(context) }
    val dao = remember { database.songs() }
    val playlistDao = remember { database.playlists() }
    val intelligence = remember { database.intelligence() }
    val progress by ScanState.progress.collectAsState()
    val export by ExportState.progress.collectAsState()
    val counts by dao.counts().collectAsState(initial = emptyList())
    val correctionCount by dao.correctionCount().collectAsState(initial = 0)
    val playlists by playlistDao.all().collectAsState(initial = emptyList())
    val total = counts.sumOf { it.count }
    val permission = remember(permissionTick) {
        ContextCompat.checkSelfPermission(context, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        future.addListener({ try { controller = future.get() } catch (_: Exception) { } }, ContextCompat.getMainExecutor(context))
        onDispose { MediaController.releaseFuture(future) }
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { playerRevision++ }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller, controller?.isPlaying) {
        while (controller?.isPlaying == true) { playerRevision++; delay(500) }
    }
    LaunchedEffect(export.finished) { if (export.finished) showExportResult = true }

    fun scan(action: String? = null) {
        try {
            val intent = Intent(context, ScanService::class.java).apply { this.action = action }
            if (action == null) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        } catch (e: Exception) { ScanState.update { it.copy(error = e.message ?: "service") } }
    }
    fun play(songs: List<Song>, first: Int = 0, shuffle: Boolean = false, source: String = "library") {
        val c = controller ?: return
        val tracks = songs.map { it.toPlaybackItem(source) }
        if (tracks.isNotEmpty()) {
            c.setMediaItems(tracks, first.coerceIn(0, tracks.lastIndex), 0)
            c.shuffleModeEnabled = shuffle
            c.prepare(); c.play()
        }
    }
    suspend fun recordTasteFeedback(song: Song, interaction: String) {
        val event = ListeningEvent(songId = song.id, startedAt = System.currentTimeMillis(), listenedMs = 180000,
            trackDurationMs = song.duration, completionRatio = 1f, skipped = false, source = "feedback", interaction = interaction)
        intelligence.putTaste(TasteEngine.applyEvent(intelligence.taste(), song, event))
    }
    suspend fun handleTwin(action: TwinAction): String = withContext(Dispatchers.IO) {
        when (action) {
            is TwinAction.PlayMood -> {
                val list = dao.playlist(action.mood)
                withContext(Dispatchers.Main) { play(list) }
                tr(fa, "Playing ${list.size} ${Moods.name(action.mood, false)} songs", "${list.size} آهنگ ${Moods.name(action.mood, true)} پخش شد")
            }
            is TwinAction.Search -> {
                val list = dao.page(-1, action.query, 0, 20, 0).first()
                list.forEach { withContext(Dispatchers.Main) { controller?.addMediaItem(it.toPlaybackItem("twin")) } }
                tr(fa, "${list.size} matching songs added to queue", "${list.size} آهنگ نتیجه به صف اضافه شد")
            }
            is TwinAction.Similar -> {
                val current = action.songId?.let { dao.get(it) }
                val result = RadarEngine.recommend(dao.playlist(-1), intelligence.taste(), current, intelligence.recentEvents(500), 8)
                result.forEach { withContext(Dispatchers.Main) { controller?.addMediaItem(it.song.toPlaybackItem("twin")) } }
                if (result.isEmpty()) tr(fa, "No analyzed similar songs found", "آهنگ مشابه تحلیل‌شده‌ای یافت نشد")
                else tr(fa, "${result.size} similar songs added: ${recommendationReason(result.first().reason, false)}", "${result.size} آهنگ مشابه اضافه شد: ${recommendationReason(result.first().reason, true)}")
            }
            is TwinAction.Discover -> {
                val events = intelligence.recentEvents(1000)
                var result = RadarEngine.recommend(dao.playlist(-1), intelligence.taste(), null, events, 12, discover = true)
                if (action.unheardOnly) result = result.filter { scored -> events.none { it.songId == scored.song.id } }
                val id = playlistDao.create(Playlist(name = "Twin Discovery ${System.currentTimeMillis()}", createdAt = System.currentTimeMillis()))
                result.forEach { playlistDao.addSong(id, it.song.id) }
                tr(fa, "Created a playlist with ${result.size} discoveries", "پلی‌لیستی با ${result.size} آهنگ کشف‌شده ساخته شد")
            }
            is TwinAction.StartDj -> {
                prefs.edit().putBoolean("djEnabled", true).putString("djMode", action.mode).apply()
                val result = RadarEngine.recommend(dao.playlist(-1), intelligence.taste(), null, intelligence.recentEvents(500), 30)
                withContext(Dispatchers.Main) { play(result.map { it.song }, source = "smart_dj") }
                tr(fa, "Smart DJ started in ${action.mode} mode", "دی‌جِی در حالت ${action.mode} شروع شد")
            }
            is TwinAction.Journey -> {
                val config = JourneyConfig(action.start, action.end, action.minutes, .65f, .6f)
                val list = JourneyEngine.generate(dao.playlist(-1), intelligence.taste(), config)
                intelligence.addJourney(MoodJourney(name = "Twin Journey", startingMood = action.start, targetMood = action.end,
                    requestedMinutes = action.minutes, energy = .65f, familiarity = .6f,
                    songIds = list.joinToString(",") { it.id.toString() }, generatedAt = System.currentTimeMillis()))
                withContext(Dispatchers.Main) { play(list, source = "journey") }
                tr(fa, "Generated and saved a ${action.minutes}-minute journey with ${list.size} songs", "سفر ${action.minutes} دقیقه‌ای با ${list.size} آهنگ ساخته و ذخیره شد")
            }
            is TwinAction.ShowHistory -> { withContext(Dispatchers.Main) { screen = "time" }; tr(fa, "Opening your real listening history", "تاریخچه واقعی شنیدن باز شد") }
            TwinAction.ExplainDna -> {
                val dna = TasteEngine.snapshot(intelligence.taste())
                if (dna.totalWeight <= 0) tr(fa, "Not enough listening data yet", "هنوز داده کافی برای DNA وجود ندارد")
                else tr(fa, "Preferred energy ${(dna.averageEnergy*100).toInt()}%, tempo ${dna.averageTempo.toInt()} BPM, vocal preference ${(dna.vocalPreference*100).toInt()}% from ${dna.eventCount} recorded sessions.",
                    "انرژی ترجیحی ${(dna.averageEnergy*100).toInt()}٪، تمپو ${dna.averageTempo.toInt()} BPM و علاقه به صدا ${(dna.vocalPreference*100).toInt()}٪ از ${dna.eventCount} جلسه واقعی.")
            }
            TwinAction.CompareMonths -> {
                val current = TimeRanges.thisMonth(); val previous = TimeRanges.lastMonth()
                val a = intelligence.period(current.first, current.second); val b = intelligence.period(previous.first, previous.second)
                tr(fa, "This month ${a.listenedMs/60000} min / ${a.events} sessions; last month ${b.listenedMs/60000} min / ${b.events}.",
                    "این ماه ${a.listenedMs/60000} دقیقه و ${a.events} جلسه؛ ماه قبل ${b.listenedMs/60000} دقیقه و ${b.events} جلسه.")
            }
            TwinAction.ResetDna -> { intelligence.resetTaste(); tr(fa, "Music DNA was reset after confirmation", "DNA پس از تأیید بازنشانی شد") }
            TwinAction.DeleteHistory -> { intelligence.clearHistory(); tr(fa, "Listening history was deleted after confirmation", "تاریخچه پس از تأیید حذف شد") }
            is TwinAction.Unknown -> tr(fa, "I could not map that request to a safe music action", "این درخواست به یک عمل امن موسیقی تبدیل نشد")
        }
    }

    fun share(categoryId: Int) {
        scope.launch {
            val all = withContext(Dispatchers.IO) { dao.playlist(categoryId) }
            if (all.isEmpty()) return@launch
            val selected = mutableListOf<Song>()
            var bytes = 0L
            for (song in all) {
                if (selected.size >= 100 || bytes + song.size > 150L * 1024 * 1024) break
                selected += song; bytes += song.size
            }
            try {
                context.startActivity(Intent.createChooser(categoryShareIntent(selected), tr(fa, "Share category", "اشتراک دسته")))
                if (selected.size < all.size) Toast.makeText(context, tr(fa, "Large category: sharing a safe first batch of ${selected.size} songs", "دسته بزرگ است: ${selected.size} آهنگ در یک بسته امن ارسال شد"), Toast.LENGTH_LONG).show()
            } catch (_: Exception) { Toast.makeText(context, tr(fa, "No sharing app is available", "برنامه‌ای برای اشتراک‌گذاری یافت نشد"), Toast.LENGTH_SHORT).show() }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MoodMusicTheme(fa = fa, dark = dark, mood = if (screen == "songs") category else -1) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .08f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))) {
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        Column {
                            MiniPlayer(controller, playerRevision, fa, pd) { playerOpen = true }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
                                NavigationBarItem(selected = screen == "dashboard", onClick = { screen = "dashboard" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(tr(fa, "Home", "خانه")) })
                                NavigationBarItem(selected = screen == "home" || screen == "songs", onClick = { screen = "home" }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text(tr(fa, "Library", "کتابخانه")) })
                                NavigationBarItem(selected = screen == "radar", onClick = { screen = "radar" }, icon = { Icon(Icons.Default.TravelExplore, null) }, label = { Text(tr(fa, "Discover", "کشف")) })
                                NavigationBarItem(selected = screen == "twin", onClick = { screen = "twin" }, icon = { Icon(Icons.Default.SmartToy, null) }, label = { Text("Twin") })
                                NavigationBarItem(selected = screen in listOf("profile", "settings", "aiSettings", "dna", "time"), onClick = { screen = "profile" }, icon = { Icon(Icons.Default.Person, null) }, label = { Text(tr(fa, "Profile", "پروفایل")) })
                            }
                        }
                    }
                ) { padding ->
                    Crossfade(targetState = screen, modifier = Modifier.fillMaxSize().padding(padding), label = "screen") { current ->
                        when (current) {
                            "dashboard" -> DashboardScreen(dao, intelligence, fa, pd,
                                onLibrary = { screen = "home" }, onDna = { screen = "dna" }, onJourney = { screen = "journey" },
                                onDj = { screen = "dj" }, onRadar = { screen = "radar" }, onHistory = { screen = "time" },
                                onPlaylists = { screen = "playlists" }, onQueue = { screen = "queue" },
                                onPlay = { list, index -> play(list, index, source = "recent") })
                            "dna" -> MusicDnaScreen(intelligence, dao, fa, pd, onReset = { scope.launch(Dispatchers.IO) { intelligence.resetTaste() } })
                            "radar" -> RadarScreen(dao, intelligence, controller?.currentMediaItem?.mediaId?.toLongOrNull(), fa, pd,
                                onPlay = { list, index -> play(list, index, source = "radar") })
                            "journey" -> JourneyScreen(dao, intelligence, fa, pd, onPlay = { list, source -> play(list, source = source) })
                            "dj" -> SmartDjScreen(fa, onStart = { mode ->
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { RadarEngine.recommend(dao.playlist(-1), intelligence.taste(), null, intelligence.recentEvents(500), 40) }
                                    play(result.map { it.song }, source = "smart_dj")
                                }
                            })
                            "time" -> TimeMachineScreen(dao, intelligence, playlistDao, fa, pd, onPlay = { list, source -> play(list, source = source) })
                            "twin" -> TwinScreen(intelligence, controller?.currentMediaItem?.mediaId?.toLongOrNull(), fa, onAction = { action -> handleTwin(action) })
                            "profile" -> ProfileScreen(fa, onDna = { screen = "dna" }, onHistory = { screen = "time" },
                                onAiSettings = { screen = "aiSettings" }, onSettings = { screen = "settings" },
                                onPlaylists = { screen = "playlists" }, onQueue = { screen = "queue" })
                            "aiSettings" -> AiSettingsScreen(dao, intelligence, fa)
                            "home" -> HomeView(
                                fa, pd, permission, total, counts, correctionCount, progress,
                                onRequest = onRequest, onScan = { scan() }, onPause = { scan(if (progress.paused) "resume" else "pause") }, onCancel = { scan("cancel") },
                                onAll = { category = -1; screen = "songs" }, onMood = { category = it; screen = "songs" }
                            )
                            "songs" -> SongsView(
                                category, dao, fa, pd,
                                onBack = { screen = "home" },
                                onPlay = { songs, index, shuffle -> play(songs, index, shuffle) },
                                onMenu = { songMenu = it },
                                onFolder = { onFolderExport(category) }, onZip = { onZipExport(category) }, onShare = { share(category) }
                            )
                            "playlists" -> PlaylistsView(
                                playlists, fa, pd,
                                onOpen = { openPlaylist = it; screen = "playlist" },
                                onCreate = { nameDialog = NameDialog(null) },
                                onRename = { id, name -> nameDialog = NameDialog(id, name) },
                                onDelete = { id -> scope.launch { withContext(Dispatchers.IO) { playlistDao.delete(id) } } }
                            )
                            "playlist" -> PlaylistDetailView(
                                openPlaylist, playlistDao, fa, pd, onBack = { screen = "playlists" },
                                onPlay = { songs, index -> play(songs, index) }, onMenu = { songMenu = it }
                            )
                            "queue" -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                                ScreenHeader(tr(fa, "Play queue", "صف پخش"), Icons.Default.QueueMusic)
                                QueueView(controller, playerRevision, fa, pd) { playerOpen = true }
                            }
                            "settings" -> SettingsView(
                                fa, pd, dark, persianDigits, correctionCount, prefs,
                                onLanguage = { fa = !fa; prefs.edit().putBoolean("fa", fa).apply() },
                                onDigits = { persianDigits = it; prefs.edit().putBoolean("digits", it).apply() },
                                onDark = { dark = it; prefs.edit().putBoolean("dark", it).apply() },
                                onReanalyze = { scope.launch { withContext(Dispatchers.IO) { dao.invalidateAll() }; scan() } },
                                onBattery = { onboarding = true }, scanning = progress.running
                            )
                        }
                    }
                }

                if (export.running) ExportRunningCard(export, fa, pd) {
                    context.startService(Intent(context, ExportService::class.java).setAction(ExportService.ACTION_CANCEL))
                }
                if (showExportResult && export.finished) AlertDialog(
                    onDismissRequest = { showExportResult = false; ExportState.update { ExportProgress() } },
                    title = { Text(tr(fa, "Export finished", "پایان خروجی")) },
                    text = { Column {
                        Text(if (export.cancelled) tr(fa, "Cancelled", "لغو شد") else tr(fa, "Successful: ${export.copied}   Failed: ${export.failed}", "موفق: ${export.copied}   ناموفق: ${export.failed}"))
                        export.errors.take(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    } },
                    confirmButton = { TextButton(onClick = { showExportResult = false; ExportState.update { ExportProgress() } }) { Text(tr(fa, "Done", "بستن")) } }
                )
                songMenu?.let { song -> SongActionsDialog(
                    song, fa, onDismiss = { songMenu = null },
                    onMood = { correction = song; songMenu = null },
                    onPlaylist = { playlistPicker = song; songMenu = null },
                    onQueue = {
                        controller?.addMediaItem(song.toMediaItem()); songMenu = null
                        Toast.makeText(context, tr(fa, "Added to queue", "به صف اضافه شد"), Toast.LENGTH_SHORT).show()
                    },
                    onRemovePlaylist = if (screen == "playlist") ({
                        scope.launch { withContext(Dispatchers.IO) { playlistDao.remove(openPlaylist, song.id) } }; songMenu = null
                    }) else null,
                    onReanalyze = { scope.launch { withContext(Dispatchers.IO) { dao.invalidate(song.id) }; scan() }; songMenu = null }
                ) }
                correction?.let { song -> MoodDialog(
                    song, fa, onDismiss = { correction = null },
                    onChoose = { mood -> scope.launch { withContext(Dispatchers.IO) { dao.correct(song.id, mood) } }; correction = null },
                    onRestore = { scope.launch { withContext(Dispatchers.IO) { dao.restoreAutomatic(song.id) } }; correction = null }
                ) }
                playlistPicker?.let { song -> AlertDialog(
                    onDismissRequest = { playlistPicker = null }, title = { Text(tr(fa, "Add to playlist", "افزودن به پلی‌لیست")) },
                    text = { LazyColumn {
                        items(playlists, key = { it.id }) { playlist -> TextButton(onClick = {
                            scope.launch { withContext(Dispatchers.IO) { playlistDao.addSong(playlist.id, song.id) } }; playlistPicker = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(playlist.name) } }
                        item { TextButton(onClick = { playlistPicker = null; nameDialog = NameDialog(null, addSongId = song.id) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr(fa, "New playlist", "پلی‌لیست جدید")) } }
                    } }, confirmButton = { TextButton(onClick = { playlistPicker = null }) { Text(tr(fa, "Cancel", "لغو")) } }
                ) }
                nameDialog?.let { dialog -> PlaylistNameDialog(dialog.initial, fa, onDismiss = { nameDialog = null }) { name ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (dialog.playlistId == null) {
                                val newId = playlistDao.create(Playlist(name = name, createdAt = System.currentTimeMillis()))
                                dialog.addSongId?.let { playlistDao.addSong(newId, it) }
                            } else playlistDao.rename(dialog.playlistId, name)
                        }
                    }; nameDialog = null
                } }
                if (playerOpen && controller != null) PlayerDialog(controller!!, playerRevision, fa, pd, onDismiss = { playerOpen = false }, onQueue = { playerOpen = false; screen = "queue" })
                if (onboarding) AlertDialog(
                    onDismissRequest = { onboarding = false; prefs.edit().putBoolean("batterySeen", true).apply() },
                    title = { Text(tr(fa, "Background playback on Xiaomi", "پخش پس‌زمینه در شیائومی")) },
                    text = { Text(tr(fa, "For uninterrupted playback and analysis, allow unrestricted battery use. Enable Autostart in MIUI/HyperOS if available.", "برای پخش و تحلیل بدون وقفه، مصرف باتری برنامه را بدون محدودیت کنید و در MIUI یا HyperOS گزینه Autostart را در صورت وجود فعال کنید.")) },
                    confirmButton = { TextButton(onClick = {
                        try { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { }
                        onboarding = false; prefs.edit().putBoolean("batterySeen", true).apply()
                    }) { Text(tr(fa, "Battery settings", "تنظیمات باتری")) } },
                    dismissButton = { TextButton(onClick = { onboarding = false; prefs.edit().putBoolean("batterySeen", true).apply() }) { Text(tr(fa, "Later", "بعداً")) } }
                )
            }
        }
    }
}

private fun Song.toMediaItem(): MediaItem = toPlaybackItem("library")

@Composable
private fun HomeView(
    fa: Boolean, pd: Boolean, permission: Boolean, total: Int, counts: List<MoodCount>, corrections: Int,
    progress: ScanProgress, onRequest: () -> Unit, onScan: () -> Unit, onPause: () -> Unit, onCancel: () -> Unit,
    onAll: () -> Unit, onMood: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Text("Mood Music", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(tr(fa, "Your sound. Your mood.", "موسیقی تو، حال‌وهوای تو"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(digits(total, pd), tr(fa, "songs", "آهنگ"), Modifier.weight(1f))
                    StatCard(digits(corrections, pd), tr(fa, "manual corrections", "اصلاح دستی"), Modifier.weight(1f))
                }
            }
        }
        if (!permission) item(span = { GridItemSpan(2) }) {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text(tr(fa, "Find your music", "موسیقی‌هایت را پیدا کن"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                    Text(tr(fa, "Audio permission is used only to read and play your local library.", "دسترسی صدا فقط برای خواندن و پخش کتابخانه محلی استفاده می‌شود."))
                    Button(onClick = onRequest, modifier = Modifier.padding(top = 12.dp)) { Text(tr(fa, "Allow audio access", "اجازه دسترسی به صدا")) }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .13f))) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(tr(fa, "On-device audio intelligence", "هوش صوتی روی دستگاه"), fontWeight = FontWeight.Bold)
                    }
                    Text(tr(fa, "No uploads. No filenames used for classification.", "بدون ارسال فایل؛ نام آهنگ در دسته‌بندی استفاده نمی‌شود."), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    if (progress.running) {
                        LinearProgressIndicator(progress = { if (progress.discovered > 0) (progress.done.toFloat() / progress.discovered).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                        Text("${digits(progress.done, pd)} / ${digits(progress.discovered, pd)}  •  ${digits(progress.analyzed, pd)} ${tr(fa, "analyzed", "تحلیل")}  •  ${digits(progress.skipped, pd)} ${tr(fa, "cached", "کش")}", style = MaterialTheme.typography.bodySmall)
                        Text(progress.current, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = onPause) { Text(if (progress.paused) tr(fa, "Resume", "ادامه") else tr(fa, "Pause", "مکث")) }
                            TextButton(onClick = onCancel) { Text(tr(fa, "Cancel", "لغو")) }
                        }
                    } else {
                        if (progress.error.isNotBlank()) Text(tr(fa, "Scan stopped. Check audio permission and retry.", "بررسی متوقف شد؛ دسترسی را بررسی و دوباره تلاش کنید."), color = MaterialTheme.colorScheme.error)
                        Row {
                            Button(enabled = permission, onClick = onScan) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text(tr(fa, "Scan library", "بررسی کتابخانه")) }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = onAll) { Text(tr(fa, "All songs", "همه آهنگ‌ها")) }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) { Text(tr(fa, "Choose a mood", "یک حال‌وهوا انتخاب کن"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        items(Moods.all.size) { index ->
            val id = Moods.all[index]
            val count = counts.firstOrNull { it.mood == id }?.count ?: 0
            ElevatedCard(onClick = { onMood(id) }, modifier = Modifier.fillMaxWidth().height(132.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(MoodColors[id].copy(alpha = .24f), Color.Transparent)))) {
                    Column(Modifier.padding(16.dp)) {
                        Text(Moods.symbols[id], style = MaterialTheme.typography.headlineMedium, color = MoodColors[id])
                        Spacer(Modifier.weight(1f))
                        Text(Moods.name(id, fa), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tr(fa, "$count songs", "$count آهنگ"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val unsorted = counts.firstOrNull { it.mood == Moods.UNSORTED }?.count ?: 0
        if (unsorted > 0) item(span = { GridItemSpan(2) }) {
            OutlinedCard(onClick = { onMood(Moods.UNSORTED) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) { Icon(Icons.Default.HelpOutline, null); Spacer(Modifier.width(12.dp)); Text(tr(fa, "Needs analysis", "نیازمند تحلیل")); Spacer(Modifier.weight(1f)); Text(digits(unsorted, pd)) }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SongsView(
    category: Int, dao: SongDao, fa: Boolean, pd: Boolean, onBack: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit, onMenu: (Song) -> Unit,
    onFolder: () -> Unit, onZip: () -> Unit, onShare: () -> Unit
) {
    var query by remember(category) { mutableStateOf("") }
    var sort by remember(category) { mutableIntStateOf(0) }
    var offset by remember(category) { mutableIntStateOf(0) }
    val songs by remember(category, query, sort, offset) { dao.page(category, query, sort, 100, offset) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val database = remember { MoodDatabase.get(context) }
    var showAllAi by remember { mutableStateOf(false) }
    var onlineStatus by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, tr(fa, "Back", "بازگشت")) }
            Text(if (category < 0) tr(fa, "All songs", "همه آهنگ‌ها") else Moods.name(category, fa), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { scope.launch { val all = withContext(Dispatchers.IO) { dao.playlist(category) }; onPlay(all, 0, true) } }) { Icon(Icons.Default.Shuffle, tr(fa, "Shuffle category", "پخش تصادفی دسته"), tint = MaterialTheme.colorScheme.primary) }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it; offset = 0 }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(tr(fa, "Search title or artist", "جستجوی آهنگ یا خواننده")) }, shape = RoundedCornerShape(18.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { showAllAi = true }) { Icon(Icons.Default.AutoAwesome, null); Text(tr(fa, "Analyze visible", "تحلیل این صفحه")) }
            TextButton(onClick = { sort = (sort + 1) % 5; offset = 0 }) {
                Icon(Icons.Default.SwapVert, null); Spacer(Modifier.width(4.dp))
                Text(listOf(tr(fa, "Title", "نام"), tr(fa, "Artist", "خواننده"), tr(fa, "Recent", "جدید"), "BPM", tr(fa, "Energy", "انرژی"))[sort])
            }
            if (category >= 0 && category != Moods.UNSORTED) {
                TextButton(onClick = onFolder) { Icon(Icons.Default.Folder, null); Text(tr(fa, "Copy", "کپی")) }
                TextButton(onClick = onZip) { Icon(Icons.Default.Archive, null); Text("ZIP") }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, tr(fa, "Share", "اشتراک")) }
            }
        }
        if (onlineStatus.isNotBlank()) Text(onlineStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp))
        if (songs.isEmpty()) EmptyView(tr(fa, "No songs found", "آهنگی یافت نشد"), Icons.Default.MusicNote)
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(songs, key = { it.id }) { song -> SongRow(song, fa, pd, onClick = { onPlay(songs, songs.indexOf(song), false) }, onMenu = { onMenu(song) }, onFeedback = { feedback -> scope.launch { withContext(Dispatchers.IO) { dao.feedback(song.id, feedback) } } }) }
        }
        if (offset > 0 || songs.size == 100) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = offset > 0, onClick = { offset = max(0, offset - 100) }) { Text(tr(fa, "Previous", "قبلی")) }
            Text("${digits(offset + 1, pd)}–${digits(offset + songs.size, pd)}")
            TextButton(enabled = songs.size == 100, onClick = { offset += 100 }) { Text(tr(fa, "Next", "بعدی")) }
        }
    }
    if (showAllAi) AlertDialog(onDismissRequest = { showAllAi = false }, title = { Text(tr(fa, "Upload visible songs?", "آهنگ‌های این صفحه آپلود شوند؟")) },
        text = { Text(tr(fa, "Each audio file is sent to your configured AI only with consent, Wi-Fi/size limits and a maximum of ${songs.size} files. Your manual corrections remain unchanged. Local analysis stays available.", "هر فایل صوتی فقط با رضایت، محدودیت Wi-Fi/حجم و حداکثر ${songs.size} فایل به AI شما ارسال می‌شود. اصلاح دستی حفظ می‌شود و تحلیل محلی باقی می‌ماند.")) },
        confirmButton = { TextButton(onClick = {
            showAllAi = false
            scope.launch {
                var done = 0; var failed = 0
                songs.forEach { song ->
                    try {
                        val result = withContext(Dispatchers.IO) { OnlineAnalysisCoordinator(context).analyze(song, true) }
                        if (result != null) done++ else failed++
                    } catch (e: Exception) { failed++; onlineStatus = e.message ?: "AI error" }
                }
                onlineStatus = tr(fa, "Online success: $done • failed/skipped: $failed", "موفق آنلاین: $done • ناموفق/رد: $failed")
            }
        }) { Text(tr(fa, "Consent and upload", "رضایت و آپلود")) } }, dismissButton = { TextButton(onClick = { showAllAi = false }) { Text(tr(fa, "Cancel", "لغو")) } })
}

@Composable
private fun SongRow(song: Song, fa: Boolean, pd: Boolean, onClick: () -> Unit, onMenu: () -> Unit, onFeedback: ((Int) -> Unit)? = null) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .86f))) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(albumArtwork(song.albumId), Modifier.size(58.dp), Moods.symbols.getOrElse(song.mood) { "♫" })
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist.ifBlank { tr(fa, "Unknown artist", "خواننده نامشخص") }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MoodColors.getOrElse(song.mood) { Color.Gray }.copy(alpha = .14f)) {
                        Text(Moods.name(song.mood, fa) + if (song.manual) " • " + tr(fa, "manual", "دستی") else if (song.confidence < MoodClassifier.REVIEW_THRESHOLD) " • " + tr(fa, "review", "بازبینی") else "", modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MoodColors.getOrElse(song.mood) { Color.Gray })
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(clock(song.duration, pd), style = MaterialTheme.typography.labelSmall)
                }
                if (song.reason.isNotBlank()) Text(tr(fa, "Audio could not be decoded", "فایل صوتی قابل تحلیل نیست"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Column {
                if (onFeedback != null) Row {
                    IconButton(onClick = { onFeedback(1) }) { Icon(Icons.Default.Favorite, tr(fa, "Like", "پسندیدن"), tint = if (song.feedback == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { onFeedback(-1) }) { Icon(Icons.Default.ThumbDown, tr(fa, "Dislike", "نپسندیدن"), tint = if (song.feedback == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                }
                IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, tr(fa, "Song actions", "عملیات آهنگ")) }
            }
        }
    }
}

@Composable
private fun PlaylistsView(
    playlists: List<PlaylistWithCount>, fa: Boolean, pd: Boolean, onOpen: (Long) -> Unit,
    onCreate: () -> Unit, onRename: (Long, String) -> Unit, onDelete: (Long) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader(tr(fa, "Your playlists", "پلی‌لیست‌های تو"), Icons.Default.LibraryMusic, Modifier.weight(1f))
            FilledIconButton(onClick = onCreate) { Icon(Icons.Default.Add, tr(fa, "Create playlist", "ساخت پلی‌لیست")) }
        }
        if (playlists.isEmpty()) EmptyView(tr(fa, "Create a playlist for every moment", "برای هر لحظه یک پلی‌لیست بساز"), Icons.Default.LibraryMusic)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                ElevatedCard(onClick = { onOpen(playlist.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { Text(playlist.name, style = MaterialTheme.typography.titleMedium); Text(tr(fa, "${playlist.songCount} songs", "${playlist.songCount} آهنگ"), style = MaterialTheme.typography.bodySmall) }
                        IconButton(onClick = { onRename(playlist.id, playlist.name) }) { Icon(Icons.Default.Edit, tr(fa, "Rename", "تغییر نام")) }
                        IconButton(onClick = { onDelete(playlist.id) }) { Icon(Icons.Default.Delete, tr(fa, "Delete", "حذف"), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlistId: Long, dao: PlaylistDao, fa: Boolean, pd: Boolean, onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit, onMenu: (Song) -> Unit
) {
    var query by remember(playlistId) { mutableStateOf("") }
    val songs by remember(playlistId, query) { dao.songs(playlistId, query) }.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, tr(fa, "Back", "بازگشت")) }
            Text(tr(fa, "Playlist", "پلی‌لیست"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { onPlay(songs, 0) }) { Icon(Icons.Default.PlayArrow, tr(fa, "Play", "پخش"), tint = MaterialTheme.colorScheme.primary) }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(tr(fa, "Search this playlist", "جستجو در پلی‌لیست")) })
        Spacer(Modifier.height(12.dp))
        if (songs.isEmpty()) EmptyView(tr(fa, "Add songs using the ⋮ menu on any track", "از منوی ⋮ هر آهنگ، آن را اضافه کنید"), Icons.Default.LibraryMusic)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(songs, key = { it.id }) { song -> SongRow(song, fa, pd, onClick = { onPlay(songs, songs.indexOf(song)) }, onMenu = { onMenu(song) }) }
        }
    }
}

@Composable
private fun SettingsView(
    fa: Boolean, pd: Boolean, dark: Boolean, persianDigits: Boolean, corrections: Int,
    prefs: android.content.SharedPreferences, onLanguage: () -> Unit, onDigits: (Boolean) -> Unit,
    onDark: (Boolean) -> Unit, onReanalyze: () -> Unit, onBattery: () -> Unit, scanning: Boolean
) {
    var accurate by remember { mutableStateOf(prefs.getBoolean("accurate", false)) }
    var minimum by remember { mutableIntStateOf(prefs.getInt("minSeconds", 30)) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader(tr(fa, "Settings", "تنظیمات"), Icons.Default.Settings) }
        item { SettingsCard {
            SettingsRow(tr(fa, "Language / زبان", "Language / زبان"), { TextButton(onClick = onLanguage) { Text(if (fa) "فارسی" else "English") } })
            SettingsRow(tr(fa, "Persian digits", "اعداد فارسی"), { Switch(checked = persianDigits, onCheckedChange = onDigits) })
            SettingsRow(tr(fa, "Dark appearance", "ظاهر تیره"), { Switch(checked = dark, onCheckedChange = onDark) })
        } }
        item { Text(tr(fa, "Audio analysis", "تحلیل صوت"), style = MaterialTheme.typography.titleMedium) }
        item { SettingsCard {
            SettingsRow(tr(fa, "More audio samples (slower)", "نمونه‌های بیشتر (کندتر)"), { Switch(checked = accurate, onCheckedChange = { accurate = it; prefs.edit().putBoolean("accurate", it).apply() }) })
            TextButton(onClick = { minimum = when (minimum) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }; prefs.edit().putInt("minSeconds", minimum).apply() }) { Text(tr(fa, "Minimum duration: $minimum seconds", "حداقل مدت: $minimum ثانیه")) }
            TextButton(enabled = !scanning, onClick = onReanalyze) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(tr(fa, "Reanalyze all audio", "تحلیل دوباره همه فایل‌ها")) }
        } }
        item { SettingsCard {
            Text(tr(fa, "Adaptive learning", "یادگیری تطبیقی"), fontWeight = FontWeight.Bold)
            Text(tr(fa, "$corrections manual corrections update persistent acoustic centroids. Restoring automatic mood removes that correction from its centroid.", "$corrections اصلاح دستی، مرکزهای صوتی پایدار را به‌روز می‌کنند. بازگشت به تشخیص خودکار، اثر آن اصلاح را حذف می‌کند."), style = MaterialTheme.typography.bodySmall)
            Text(tr(fa, "All audio stays on this device. Rainy, nostalgic and instrumental labels are estimates and may need review.", "همه صداها روی دستگاه می‌مانند. برچسب‌های بارونی، نوستالژیک و بی‌کلام تخمینی‌اند و ممکن است نیاز به بازبینی داشته باشند."), style = MaterialTheme.typography.bodySmall)
        } }
        item { OutlinedButton(onClick = onBattery, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.BatterySaver, null); Spacer(Modifier.width(8.dp)); Text(tr(fa, "Xiaomi battery & autostart help", "راهنمای باتری و شروع خودکار شیائومی")) } }
        item { Text(tr(fa, "Mood Music Sorter 3.0\nDeveloper: Danial Mousavi\nOffline by default • Optional online providers", "مرتب‌ساز موزیک ۳٫۰\nتوسعه‌دهنده: دانیال موسوی\nآفلاین پیش‌فرض • سرویس آنلاین اختیاری"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
    }
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}
@Composable private fun SettingsRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.weight(1f)); control() }
}

@Composable
private fun MoodDialog(song: Song, fa: Boolean, onDismiss: () -> Unit, onChoose: (Int) -> Unit, onRestore: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(fa, "Choose a mood", "انتخاب حال‌وهوا")) },
        text = { LazyColumn {
            item { Text(tr(fa, "Automatic: ${Moods.name(song.autoMood, fa)} (${(song.confidence * 100).toInt()}%)", "تشخیص خودکار: ${Moods.name(song.autoMood, fa)} (${digits((song.confidence * 100).toInt(), true)}٪)"), style = MaterialTheme.typography.bodySmall) }
            items(Moods.all) { mood -> TextButton(onClick = { onChoose(mood) }, modifier = Modifier.fillMaxWidth()) { Text("${Moods.symbols[mood]}  ${Moods.name(mood, fa)}", color = MoodColors[mood]) } }
            if (song.manual) item { TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text(tr(fa, "Restore automatic classification", "بازگشت به تشخیص خودکار")) } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr(fa, "Cancel", "لغو")) } }
    )
}

@Composable
private fun PlaylistNameDialog(initial: String, fa: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(tr(fa, "Playlist name", "نام پلی‌لیست")) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(tr(fa, "Save", "ذخیره")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(fa, "Cancel", "لغو")) } }
    )
}

@Composable
private fun ExportRunningCard(export: ExportProgress, fa: Boolean, pd: Boolean, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(tr(fa, "Exporting real audio files…", "در حال خروجی فایل‌های واقعی…"), fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { if (export.total > 0) export.done.toFloat() / export.total else 0f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text("${digits(export.done, pd)} / ${digits(export.total, pd)}")
            TextButton(onClick = onCancel) { Text(tr(fa, "Cancel export", "لغو خروجی")) }
        }
    }
}

@Composable
private fun SongActionsDialog(
    song: Song, fa: Boolean, onDismiss: () -> Unit, onMood: () -> Unit,
    onPlaylist: () -> Unit, onQueue: () -> Unit, onRemovePlaylist: (() -> Unit)?, onReanalyze: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { MoodDatabase.get(context) }
    val scope = rememberCoroutineScope()
    var showExplain by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<LyricsResult?>(null) }
    var lyricsBusy by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(song.title, maxLines = 2) },
        text = { LazyColumn {
            item { Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { database.songs().feedback(song.id, 1) }; withContext(Dispatchers.IO) { database.intelligence().putTaste(TasteEngine.applyEvent(database.intelligence().taste(), song,
                ListeningEvent(songId = song.id, startedAt = System.currentTimeMillis(), listenedMs = song.duration.coerceAtLeast(1), trackDurationMs = song.duration, completionRatio = 1f, skipped = false, source = "feedback", interaction = "like"))) } }; onDismiss() }) { Icon(Icons.Default.Favorite, null); Text(tr(fa, "Like", "پسندیدن")) } }
            item { TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { database.songs().feedback(song.id, -1) } }; onDismiss() }) { Icon(Icons.Default.ThumbDown, null); Text(tr(fa, "Dislike", "نپسندیدن")) } }
            item { TextButton(onClick = onMood) { Icon(Icons.Default.Edit, null); Text(tr(fa, "Correct mood", "اصلاح حال‌وهوا")) } }
            item { TextButton(onClick = { showExplain = true }) { Icon(Icons.Default.Info, null); Text(tr(fa, "Why this classification?", "چرا این دسته‌بندی؟")) } }
            item { TextButton(onClick = onPlaylist) { Icon(Icons.Default.PlaylistAdd, null); Text(tr(fa, "Add to playlist", "افزودن به پلی‌لیست")) } }
            item { TextButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, null); Text(tr(fa, "Add to queue", "افزودن به صف")) } }
            item { TextButton(onClick = onReanalyze) { Icon(Icons.Default.Refresh, null); Text(tr(fa, "Reanalyze locally", "تحلیل دوباره آفلاین")) } }
            if (AiSettings.lyricsEnabled(context)) item { TextButton(onClick = {
                scope.launch {
                    lyricsBusy = true; lyricsError = ""
                    OnlineGate(context).check()?.let { lyricsError = it; lyricsBusy = false; return@launch }
                    try {
                        OnlineGate(context).record()
                        lyrics = withContext(Dispatchers.IO) { ProviderRegistry(context).lyrics.lyrics(song.title, song.artist) }
                    } catch (e: Exception) { lyricsError = e.message ?: "Lyrics error" }
                    lyricsBusy = false
                }
            }) { Icon(Icons.Default.Lyrics, null); Text(tr(fa, "Get online lyrics (consent required)", "دریافت متن آنلاین (با رضایت)")) } }
            if (lyricsBusy) item { LoadingLine() }
            if (lyricsError.isNotBlank()) item { Text(lyricsError, color = MaterialTheme.colorScheme.error) }
            onRemovePlaylist?.let { item { TextButton(onClick = it) { Icon(Icons.Default.RemoveCircle, null); Text(tr(fa, "Remove from playlist", "حذف از پلی‌لیست")) } } }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr(fa, "Close", "بستن")) } })
    if (showExplain) AlertDialog(onDismissRequest = { showExplain = false }, title = { Text(Moods.name(song.mood, fa)) },
        text = { Text(buildString {
            if (song.manual) append(tr(fa, "Manually corrected by you\n", "توسط شما اصلاح شده\n"))
            append(tr(fa, "Provenance: ${song.analysisSource}\nConfidence: ${(song.confidence*100).toInt()}%\n", "منبع: ${song.analysisSource}\nاطمینان: ${(song.confidence*100).toInt()}%\n"))
            append(tr(fa, "Energy ${(song.energy*100).toInt()}%, tempo ${song.bpm} BPM, brightness ${((Features.vector(song.featureData)?.get(4) ?: 0f)*100).toInt()}%, vocal ${((Features.vector(song.featureData)?.get(9) ?: 0f)*100).toInt()}%\n", "انرژی ${(song.energy*100).toInt()}٪، تمپو ${song.bpm}، روشنایی ${((Features.vector(song.featureData)?.get(4) ?: 0f)*100).toInt()}٪، صدا ${((Features.vector(song.featureData)?.get(9) ?: 0f)*100).toInt()}٪\n"))
            song.onlineExplanation?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
            append(tr(fa, "\nAnalysis is probabilistic; your correction takes priority.", "\nتحلیل احتمالی است و اصلاح شما اولویت دارد."))
        }) }, confirmButton = { TextButton(onClick = { showExplain = false }) { Text(tr(fa, "Close", "بستن")) } })
    lyrics?.let { result -> AlertDialog(onDismissRequest = { lyrics = null }, title = { Text(tr(fa, "Lyrics • LRCLIB", "متن آهنگ • LRCLIB")) },
        text = { LazyColumn { item { Text(result.lyrics.ifBlank { null } ?: tr(fa, "No synchronized/plain lyrics available", "متن معمولی/همگام موجود نیست")) }; item { Text(LRCLIBUrl, style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = { lyrics = null }) { Text(tr(fa, "Close", "بستن")) } })
    }
}

@Composable
private fun ScreenHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp)); Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun EmptyView(message: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(54.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .7f)); Spacer(Modifier.height(12.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

