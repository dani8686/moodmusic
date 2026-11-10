package com.daniel.mood

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.daniel.mood.ui.AddSongsDialog
import com.daniel.mood.ui.BatteryDialog
import com.daniel.mood.ui.ConfirmDialog
import com.daniel.mood.ui.ExportProgressDialog
import com.daniel.mood.ui.HomeScreen
import com.daniel.mood.ui.MiniPlayer
import com.daniel.mood.ui.MoodMusicTheme
import com.daniel.mood.ui.MoodPickerDialog
import com.daniel.mood.ui.NameDialog
import com.daniel.mood.ui.PlayerScreen
import com.daniel.mood.ui.PlaylistDetailScreen
import com.daniel.mood.ui.PlaylistsScreen
import com.daniel.mood.ui.QueueDialog
import com.daniel.mood.ui.ScreenCallbacks
import com.daniel.mood.ui.SettingsScreen
import com.daniel.mood.ui.SettingsState
import com.daniel.mood.ui.SongActions
import com.daniel.mood.ui.SongActionsDialog
import com.daniel.mood.ui.SongsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Holds the category snapshot between the SAF picker launch and its result. */
private class PendingExport {
    var category: Int = MoodCatalog.ALL
    var songs: List<Song> = emptyList()
}

class MainActivity : ComponentActivity() {
    private var permissionTick by mutableStateOf(0)
    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionTick++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoodMusicApp(
                permissionTick = permissionTick,
                onRequestPermissions = { permissions -> requestPermissions.launch(permissions) }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        permissionTick++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoodMusicApp(
    permissionTick: Int,
    onRequestPermissions: (Array<String>) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", MODE_PRIVATE) }
    var fa by remember { mutableStateOf(prefs.getBoolean("fa", true)) }
    var persianDigits by remember { mutableStateOf(prefs.getBoolean("digits", true)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var accurate by remember { mutableStateOf(prefs.getBoolean("accurate", false)) }
    var minSeconds by remember { mutableIntStateOf(prefs.getInt("minSeconds", 30)) }
    val pd = fa && persianDigits

    var page by rememberSaveable { mutableStateOf("home") }
    var category by rememberSaveable { mutableIntStateOf(MoodCatalog.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(SearchQuery.SORT_TITLE) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var selectedPlaylist by remember { mutableStateOf<Long?>(null) }
    var benchmark by remember { mutableStateOf("") }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var songForActions by remember { mutableStateOf<Song?>(null) }
    var songForMood by remember { mutableStateOf<Song?>(null) }
    var playlistForRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistForDelete by remember { mutableStateOf<Playlist?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var showAddSongs by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var queueRevision by remember { mutableIntStateOf(0) }
    var showBattery by remember { mutableStateOf(!prefs.getBoolean("batterySeen", false)) }
    var exportDialogVisible by remember { mutableStateOf(false) }
    var exportJob by remember { mutableStateOf<Job?>(null) }

    val repository = remember { MusicRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pendingExport = remember { PendingExport() }

    val permission = remember(permissionTick) {
        val needed = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        ContextCompat.checkSelfPermission(context, needed) == PackageManager.PERMISSION_GRANTED
    }
    val progress by ScanState.progress.collectAsState()
    val exportProgress by ExportState.progress.collectAsState()
    val counts by repository.songs.counts().collectAsState(initial = emptyList())
    val total by repository.songs.total().collectAsState(initial = 0)
    val correctionCount by repository.corrections.count().collectAsState(initial = 0)
    val playlists by repository.playlists.all().collectAsState(initial = emptyList())
    val snapshot by PlaybackState.snapshot.collectAsState()
    val pageSongs by remember(category, query, sort, offset) {
        repository.songs.page(category, SearchQuery.pattern(query), sort, SearchQuery.PAGE_SIZE, offset)
    }.collectAsState(initial = emptyList())
    val playlistSongs by remember(selectedPlaylist) {
        selectedPlaylist?.let { repository.playlists.songs(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val currentSong by produceState<Song?>(initialValue = null, snapshot.songId) {
        value = if (snapshot.songId != 0L) {
            withContext(Dispatchers.IO) { repository.songs.get(snapshot.songId) }
        } else null
    }
    val queueEntries = remember(showQueue, queueRevision, controller) {
        PlaybackState.queue(controller)
    }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { try { controller = future.get() } catch (_: Exception) { } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    LaunchedEffect(controller) {
        val current = controller
        while (true) {
            PlaybackState.publish(current)
            delay(500)
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            ExportState.reset()
            exportDialogVisible = true
            exportJob?.cancel()
            exportJob = scope.launch {
                Exporter.copyCategory(
                    context,
                    uri,
                    MoodCatalog.name(pendingExport.category, fa),
                    pendingExport.songs
                )
            }
        }
    }
    val createZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            ExportState.reset()
            exportDialogVisible = true
            exportJob?.cancel()
            exportJob = scope.launch {
                Exporter.writeZip(context, uri, pendingExport.songs)
            }
        }
    }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun startScan(action: String? = null) {
        try {
            val intent = Intent(context, ScanService::class.java).apply { this.action = action }
            if (action == null) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {
            ScanState.update { it.copy(error = "service") }
        }
    }

    fun playSongs(songs: List<Song>, first: Int, shuffle: Boolean) {
        val c = controller ?: return
        try {
            val items = songs.map { it.toMediaItem() }
            if (items.isEmpty()) return
            c.setMediaItems(items, first.coerceIn(0, items.lastIndex), 0L)
            c.shuffleModeEnabled = shuffle
            c.prepare()
            c.play()
            PlaybackState.publish(c)
        } catch (_: Exception) { }
    }

    fun addToQueue(song: Song) {
        try { controller?.addMediaItem(song.toMediaItem()) } catch (_: Exception) { }
        message(MoodCatalog.tr(fa, "Added to queue", "به صف اضافه شد"))
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        try {
            val index = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
            c.addMediaItem(index, song.toMediaItem())
        } catch (_: Exception) { }
        message(MoodCatalog.tr(fa, "Playing next", "پخش بعدی تنظیم شد"))
    }

    suspend fun categorySongs(): List<Song> = withContext(Dispatchers.IO) {
        repository.songs.playlist(category)
    }

    fun openCategory(id: Int) {
        category = id
        query = ""
        offset = 0
        page = "songs"
    }

    fun changeMood(song: Song, mood: Int) {
        scope.launch {
            val report = repository.setManualMood(song, mood)
            message(
                MoodCatalog.tr(fa, "Saved. Model updated", "ذخیره شد. مدل به\u200cروز شد") +
                    " • " + MoodCatalog.digits(report.corrections, pd) + " / " +
                    MoodCatalog.digits(report.prototypes, pd) + " / " +
                    MoodCatalog.digits(report.rescored, pd)
            )
        }
    }

    fun restoreAuto(song: Song) {
        scope.launch {
            repository.restoreAutomatic(song)
            message(MoodCatalog.tr(fa, "Automatic mood restored", "تشخیص خودکار بازگشت"))
        }
    }

    fun reanalyze(song: Song) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.songs.invalidate(song.id) }
            startScan()
        }
    }

    val callbacks = ScreenCallbacks(
        openCategory = { openCategory(it) },
        openLibrary = { openCategory(MoodCatalog.ALL) },
        openReview = { openCategory(MoodCatalog.REVIEW) },
        onSongActions = { songForActions = it },
        addToQueue = { addToQueue(it) },
        playPage = { songs, first, shuffle -> playSongs(songs, first, shuffle) },
        playAll = { shuffle ->
            scope.launch {
                val songs = categorySongs()
                if (songs.isNotEmpty()) playSongs(songs, 0, shuffle)
            }
        },
        exportFolder = {
            scope.launch {
                val songs = categorySongs()
                if (songs.isEmpty()) {
                    message(MoodCatalog.tr(fa, "This category is empty", "این دسته خالی است"))
                    return@launch
                }
                pendingExport.category = category
                pendingExport.songs = songs
                pickFolder.launch(null)
            }
        },
        exportZip = {
            scope.launch {
                val songs = categorySongs()
                if (songs.isEmpty()) {
                    message(MoodCatalog.tr(fa, "This category is empty", "این دسته خالی است"))
                    return@launch
                }
                pendingExport.category = category
                pendingExport.songs = songs
                val slug = MoodCatalog.name(category, false)
                    .replace(Regex("[^A-Za-z0-9-]"), "-")
                createZip.launch("MoodMusic-$slug.zip")
            }
        },
        share = {
            scope.launch {
                val songs = categorySongs()
                if (songs.isEmpty()) {
                    message(MoodCatalog.tr(fa, "This category is empty", "این دسته خالی است"))
                    return@launch
                }
                val direct = Exporter.shareSongsIntent(songs)
                if (direct != null) {
                    try {
                        context.startActivity(
                            Intent.createChooser(direct, MoodCatalog.tr(fa, "Share category", "اشتراک\u200cگذاری دسته"))
                        )
                    } catch (_: Exception) {
                        message(MoodCatalog.tr(fa, "No app can share these files", "برنامه\u200cای برای اشتراک پیدا نشد"))
                    }
                } else {
                    ExportState.reset()
                    exportDialogVisible = true
                    exportJob?.cancel()
                    exportJob = scope.launch {
                        val file = Exporter.createShareZip(
                            context, MoodCatalog.name(category, fa), songs
                        )
                        if (file != null) {
                            val intent = Exporter.shareZipIntent(context, file)
                            withContext(Dispatchers.Main) {
                                try {
                                    context.startActivity(
                                        Intent.createChooser(
                                            intent,
                                            MoodCatalog.tr(fa, "Share category ZIP", "اشتراک ZIP دسته")
                                        )
                                    )
                                } catch (_: Exception) {
                                    message(MoodCatalog.tr(fa, "Share failed", "اشتراک\u200cگذاری ناموفق بود"))
                                }
                            }
                        }
                    }
                }
            }
        },
        rescan = { startScan() },
        pauseScan = {
            startScan(if (progress.paused) ScanService.ACTION_RESUME else ScanService.ACTION_PAUSE)
        },
        cancelScan = { startScan(ScanService.ACTION_CANCEL) },
        requestPermission = {
            val permissions = mutableListOf(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            onRequestPermissions(permissions.toTypedArray())
        },
        openPlayer = { page = "player" },
        togglePlay = {
            val c = controller
            if (c != null) {
                try {
                    if (c.isPlaying) c.pause() else c.play()
                    PlaybackState.publish(c)
                } catch (_: Exception) { }
            }
        },
        next = { controller?.seekToNextMediaItem() },
        previous = { controller?.seekToPreviousMediaItem() },
        seek = { position -> controller?.seekTo(position) },
        toggleShuffle = {
            val c = controller
            if (c != null) {
                c.shuffleModeEnabled = !c.shuffleModeEnabled
                PlaybackState.publish(c)
            }
        },
        cycleRepeat = {
            val c = controller
            if (c != null) {
                c.repeatMode = QueueLogic.cycleRepeat(c.repeatMode)
                PlaybackState.publish(c)
            }
        },
        changeSpeed = {
            val c = controller
            if (c != null) {
                val speed = if (c.playbackParameters.speed >= 1.5f) 0.75f else c.playbackParameters.speed + 0.25f
                c.setPlaybackSpeed(speed)
                PlaybackState.publish(c)
            }
        },
        openQueue = { showQueue = true; queueRevision++ },
        createPlaylist = { showCreatePlaylist = true },
        renamePlaylist = { playlistForRename = it },
        deletePlaylist = { playlistForDelete = it },
        openPlaylist = { selectedPlaylist = it; page = "playlistDetail" },
        addSongsToPlaylist = { showAddSongs = true }
    )

    val settingsState = SettingsState(
        fa = fa,
        pd = pd,
        dark = dark,
        accurate = accurate,
        minSeconds = minSeconds,
        correctionCount = correctionCount,
        total = total,
        benchmark = benchmark,
        onLanguage = {
            fa = !fa
            prefs.edit().putBoolean("fa", fa).apply()
        },
        onDigits = {
            persianDigits = it
            prefs.edit().putBoolean("digits", it).apply()
        },
        onDark = {
            dark = it
            prefs.edit().putBoolean("dark", it).apply()
        },
        onAccurate = {
            accurate = it
            prefs.edit().putBoolean("accurate", it).apply()
        },
        onMinSeconds = {
            minSeconds = when (minSeconds) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
            prefs.edit().putInt("minSeconds", minSeconds).apply()
        },
        onReanalyzeAll = {
            scope.launch {
                withContext(Dispatchers.IO) { repository.songs.invalidateAll() }
                startScan()
            }
        },
        onApplyLearning = {
            scope.launch {
                val report = repository.rebuildLearning()
                message(
                    MoodCatalog.tr(fa, "Learning rebuilt", "الگوها بازسازی شد") +
                        " • " + MoodCatalog.digits(report.rescored, pd)
                )
            }
        },
        onBenchmark = {
            scope.launch {
                benchmark = MoodCatalog.tr(fa, "Measuring…", "در حال اندازه\u200cگیری…")
                val result = withContext(Dispatchers.IO) {
                    val list = repository.songs.playlist(MoodCatalog.ALL).take(20)
                    val start = System.nanoTime()
                    var ok = 0
                    for (song in list) {
                        try {
                            AudioAnalysis.analyze(context, Uri.parse(song.uri), song.duration, false)
                            ok++
                        } catch (_: Exception) { }
                    }
                    Pair(ok, (System.nanoTime() - start) / 1_000_000L)
                }
                benchmark = MoodCatalog.tr(fa, "Analyzed", "تحلیل شد") + " " +
                    MoodCatalog.digits(result.first, pd) + " / " +
                    MoodCatalog.digits(if (result.first > 0) result.second / result.first else 0L, pd) + " ms"
            }
        },
        onBattery = { showBattery = true }
    )

    LaunchedEffect(permission, total) {
        if (permission && total == 0 && !progress.running && !prefs.getBoolean("autoScanned", false)) {
            prefs.edit().putBoolean("autoScanned", true).apply()
            startScan()
        }
    }

    BackHandler(enabled = page != "home") { page = "home" }

    MoodMusicTheme(dark = dark, persian = fa) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(
                        snapshot = snapshot,
                        fa = fa,
                        onOpen = { page = "player" },
                        onToggle = callbacks.togglePlay,
                        onNext = callbacks.next
                    )
                    NavigationBar {
                        NavigationBarItem(
                            selected = page == "home",
                            onClick = { page = "home" },
                            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text(MoodCatalog.tr(fa, "Home", "خانه")) }
                        )
                        NavigationBarItem(
                            selected = page == "songs",
                            onClick = { openCategory(MoodCatalog.ALL) },
                            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            label = { Text(MoodCatalog.tr(fa, "Songs", "آهنگ\u200cها")) }
                        )
                        NavigationBarItem(
                            selected = page == "playlists" || page == "playlistDetail",
                            onClick = { page = "playlists" },
                            icon = { Icon(Icons.Filled.List, contentDescription = null) },
                            label = { Text(MoodCatalog.tr(fa, "Playlists", "پلی\u200cلیست")) }
                        )
                        NavigationBarItem(
                            selected = page == "settings",
                            onClick = { page = "settings" },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text(MoodCatalog.tr(fa, "Settings", "تنظیمات")) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (page != "home") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { page = "home" }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = MoodCatalog.tr(fa, "Back", "بازگشت"))
                        }
                        Text(
                            when (page) {
                                "songs" -> MoodCatalog.tr(fa, "Songs", "آهنگ\u200cها")
                                "player" -> MoodCatalog.tr(fa, "Player", "پخش\u200cکننده")
                                "settings" -> MoodCatalog.tr(fa, "Settings", "تنظیمات")
                                else -> MoodCatalog.tr(fa, "Playlists", "پلی\u200cلیست\u200cها")
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (page) {
                        "home" -> HomeScreen(
                            total = total,
                            counts = counts,
                            progress = progress,
                            fa = fa,
                            pd = pd,
                            permission = permission,
                            correctionCount = correctionCount,
                            callbacks = callbacks
                        )
                        "songs" -> SongsScreen(
                            title = if (category == MoodCatalog.ALL) {
                                MoodCatalog.tr(fa, "All songs", "همه آهنگ\u200cها")
                            } else MoodCatalog.name(category, fa),
                            songs = pageSongs,
                            query = query,
                            sort = sort,
                            offset = offset,
                            fa = fa,
                            pd = pd,
                            actions = SongActions(
                                onPlay = { song ->
                                    val index = pageSongs.indexOfFirst { it.id == song.id }
                                    playSongs(pageSongs, index.coerceAtLeast(0), false)
                                },
                                onAddQueue = { addToQueue(it) },
                                onMore = { songForActions = it }
                            ),
                            callbacks = callbacks,
                            onQuery = { query = it; offset = 0 },
                            onSort = { sort = (sort + 1) % SearchQuery.SORT_COUNT; offset = 0 },
                            onPrev = { offset = (offset - SearchQuery.PAGE_SIZE).coerceAtLeast(0) },
                            onNext = { offset = offset + SearchQuery.PAGE_SIZE }
                        )
                        "playlists" -> PlaylistsScreen(playlists, fa, pd, callbacks)
                        "playlistDetail" -> {
                            val playlist = playlists.firstOrNull { it.id == selectedPlaylist }
                            if (playlist != null) {
                                PlaylistDetailScreen(
                                    playlist = playlist,
                                    songs = playlistSongs,
                                    fa = fa,
                                    pd = pd,
                                    actions = SongActions(
                                        onPlay = { song ->
                                            val index = playlistSongs.indexOfFirst { it.id == song.id }
                                            playSongs(playlistSongs, index.coerceAtLeast(0), false)
                                        },
                                        onAddQueue = { addToQueue(it) },
                                        onMore = { songForActions = it }
                                    ),
                                    callbacks = callbacks
                                )
                            } else {
                                PlaylistsScreen(playlists, fa, pd, callbacks)
                            }
                        }
                        "player" -> PlayerScreen(currentSong, snapshot, fa, pd, callbacks)
                        "settings" -> SettingsScreen(settingsState)
                        else -> HomeScreen(total, counts, progress, fa, pd, permission, correctionCount, callbacks)
                    }
                }
            }
        }

        songForActions?.let { song ->
            SongActionsDialog(
                song = song,
                fa = fa,
                playlists = playlists,
                currentPlaylistId = if (page == "playlistDetail") selectedPlaylist else null,
                onDismiss = { songForActions = null },
                onPlayNext = { playNext(song); songForActions = null },
                onAddQueue = { addToQueue(song); songForActions = null },
                onChangeMood = { songForMood = song; songForActions = null },
                onRestoreAuto = { restoreAuto(song); songForActions = null },
                onReanalyze = { reanalyze(song); songForActions = null },
                onAddToPlaylist = { id ->
                    scope.launch { repository.addToPlaylist(id, song.id) }
                    message(MoodCatalog.tr(fa, "Added to playlist", "به پلی\u200cلیست اضافه شد"))
                    songForActions = null
                },
                onRemoveFromPlaylist = {
                    selectedPlaylist?.let { id ->
                        scope.launch { repository.removeFromPlaylist(id, song.id) }
                    }
                    songForActions = null
                }
            )
        }
        songForMood?.let { song ->
            MoodPickerDialog(
                song = song,
                fa = fa,
                onDismiss = { songForMood = null },
                onSelect = { mood -> changeMood(song, mood); songForMood = null },
                onRestoreAuto = { restoreAuto(song); songForMood = null }
            )
        }
        playlistForRename?.let { playlist ->
            NameDialog(
                title = MoodCatalog.tr(fa, "Rename playlist", "تغییر نام پلی\u200cلیست"),
                label = MoodCatalog.tr(fa, "Name", "نام"),
                initial = playlist.name,
                confirmLabel = MoodCatalog.tr(fa, "Save", "ذخیره"),
                fa = fa,
                onConfirm = { name ->
                    scope.launch { repository.renamePlaylist(playlist.id, name) }
                    playlistForRename = null
                },
                onDismiss = { playlistForRename = null }
            )
        }
        playlistForDelete?.let { playlist ->
            ConfirmDialog(
                text = MoodCatalog.tr(fa, "Delete this playlist?", "این پلی\u200cلیست حذف شود؟"),
                fa = fa,
                onConfirm = {
                    scope.launch {
                        repository.deletePlaylist(playlist.id)
                        if (selectedPlaylist == playlist.id) {
                            selectedPlaylist = null
                            page = "playlists"
                        }
                    }
                    playlistForDelete = null
                },
                onDismiss = { playlistForDelete = null }
            )
        }
        if (showCreatePlaylist) {
            NameDialog(
                title = MoodCatalog.tr(fa, "New playlist", "پلی\u200cلیست جدید"),
                label = MoodCatalog.tr(fa, "Name", "نام"),
                initial = "",
                confirmLabel = MoodCatalog.tr(fa, "Create", "ساخت"),
                fa = fa,
                onConfirm = { name ->
                    scope.launch { repository.createPlaylist(name) }
                    showCreatePlaylist = false
                },
                onDismiss = { showCreatePlaylist = false }
            )
        }
        if (showAddSongs) {
            AddSongsDialog(
                fa = fa,
                songsProvider = { term ->
                    repository.songs.page(MoodCatalog.ALL, SearchQuery.pattern(term), SearchQuery.SORT_TITLE, 100, 0)
                },
                onAdd = { song ->
                    selectedPlaylist?.let { id ->
                        scope.launch { repository.addToPlaylist(id, song.id) }
                    }
                    message(MoodCatalog.tr(fa, "Added", "اضافه شد"))
                },
                onDismiss = { showAddSongs = false }
            )
        }
        if (showQueue) {
            QueueDialog(
                entries = queueEntries,
                fa = fa,
                onDismiss = { showQueue = false },
                onMove = { from, to ->
                    val count = controller?.mediaItemCount ?: 0
                    val target = QueueLogic.targetIndex(from, to, count)
                    if (from != target) {
                        try { controller?.moveMediaItem(from, target) } catch (_: Exception) { }
                        queueRevision++
                    }
                },
                onRemove = { index ->
                    try { controller?.removeMediaItems(index, index + 1) } catch (_: Exception) { }
                    queueRevision++
                }
            )
        }
        if (showBattery) {
            BatteryDialog(
                fa = fa,
                onDismiss = {
                    showBattery = false
                    prefs.edit().putBoolean("batterySeen", true).apply()
                },
                onOpenSettings = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) { }
                    showBattery = false
                    prefs.edit().putBoolean("batterySeen", true).apply()
                }
            )
        }
        if (exportDialogVisible) {
            ExportProgressDialog(
                progress = exportProgress,
                fa = fa,
                pd = pd,
                onCancel = {
                    exportJob?.cancel()
                    ExportState.failed(MoodCatalog.tr(fa, "Cancelled", "لغو شد"))
                },
                onDismiss = {
                    exportDialogVisible = false
                    ExportState.reset()
                }
            )
        }
    }
}
