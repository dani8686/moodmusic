package com.daniel.mood

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(
    val success: Int,
    val failed: Int,
    val errors: List<String>,
    val destination: String = ""
)

data class ExportProgress(
    val running: Boolean = false,
    val label: String = "",
    val current: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val result: ExportResult? = null
)

object ExportState {
    private val mutable = MutableStateFlow(ExportProgress())
    val progress: StateFlow<ExportProgress> = mutable

    fun start(label: String, total: Int) {
        mutable.value = ExportProgress(running = true, label = label, total = total)
    }

    fun step(done: Int, current: String) {
        mutable.value = mutable.value.copy(done = done, current = current)
    }

    fun finish(result: ExportResult) {
        mutable.value = mutable.value.copy(
            running = false,
            done = mutable.value.total,
            result = result
        )
    }

    fun failed(message: String) {
        mutable.value = mutable.value.copy(
            running = false,
            result = ExportResult(0, mutable.value.total, listOf(message))
        )
    }

    fun reset() {
        mutable.value = ExportProgress()
    }
}

/**
 * Per-category export of *real audio bytes*.
 *
 * - SAF tree (ACTION_OPEN_DOCUMENT_TREE) also covers USB OTG when the device
 *   exposes it in the system file picker.
 * - ZIP export uses ACTION_CREATE_DOCUMENT.
 * - Sharing uses ACTION_SEND_MULTIPLE for small sets and a FileProvider ZIP
 *   for large sets, avoiding Android's Binder transaction size limit.
 *
 * No broad storage permission is requested.
 */
object Exporter {
    private const val BUFFER = 64 * 1024
    private const val MAX_DIRECT_URIS = 80

    suspend fun copyCategory(
        context: Context,
        treeUri: Uri,
        categoryName: String,
        songs: List<Song>
    ): ExportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val folderName = FileNames.safeName(categoryName)
        ExportState.start("copy", songs.size)
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.isDirectory || !root.canWrite()) {
            return@withContext finish(ExportResult(0, songs.size, listOf("Destination folder is not writable"), folderName))
        }
        val directory = root.findFile(folderName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(folderName)
        if (directory == null) {
            return@withContext finish(ExportResult(0, songs.size, listOf("Could not create category folder"), folderName))
        }
        val existing = directory.listFiles().mapNotNull { it.name }.toMutableSet()
        var success = 0
        val errors = ArrayList<String>()
        for ((index, song) in songs.withIndex()) {
            yield()
            val sourceUri = Uri.parse(song.uri)
            try {
                val displayName = fileNameFor(context, song)
                val name = FileNames.uniqueName(displayName, existing)
                existing.add(name)
                val mime = resolver.getType(sourceUri) ?: "application/octet-stream"
                val target = directory.createFile(mime, name)
                    ?: throw IOException("Could not create destination file")
                val input = resolver.openInputStream(sourceUri)
                    ?: throw IOException("Source file is unavailable")
                val output = resolver.openOutputStream(target.uri, "w")
                    ?: throw IOException("Destination is not writable")
                input.use { source ->
                    output.use { destination ->
                        val buffer = ByteArray(BUFFER)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            destination.write(buffer, 0, read)
                        }
                        destination.flush()
                    }
                }
                success++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "${song.title}: ${e.message ?: e.javaClass.simpleName}"
            }
            ExportState.step(index + 1, song.title)
        }
        finish(ExportResult(success, songs.size - success, errors, folderName))
    }

    suspend fun writeZip(
        context: Context,
        targetUri: Uri,
        songs: List<Song>
    ): ExportResult = withContext(Dispatchers.IO) {
        ExportState.start("zip", songs.size)
        val output = context.contentResolver.openOutputStream(targetUri, "w")
            ?: return@withContext finish(ExportResult(0, songs.size, listOf("Could not open ZIP destination")))
        val used = HashSet<String>()
        var success = 0
        val errors = ArrayList<String>()
        try {
            ZipOutputStream(output.buffered()).use { zip ->
                // Audio is already compressed: storing it avoids wasted CPU/battery.
                zip.setLevel(Deflater.NO_COMPRESSION)
                for ((index, song) in songs.withIndex()) {
                    yield()
                    try {
                        val entryName = FileNames.uniqueZipName(fileNameFor(context, song), used)
                        val input = context.contentResolver.openInputStream(Uri.parse(song.uri))
                            ?: throw IOException("Source file is unavailable")
                        input.use { source ->
                            zip.putNextEntry(ZipEntry(entryName))
                            val buffer = ByteArray(BUFFER)
                            while (true) {
                                val read = source.read(buffer)
                                if (read < 0) break
                                zip.write(buffer, 0, read)
                            }
                            zip.closeEntry()
                        }
                        success++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors += "${song.title}: ${e.message ?: e.javaClass.simpleName}"
                        // A failed entry must not leave the ZIP stream in an open entry.
                        try { zip.closeEntry() } catch (_: Exception) { }
                    }
                    ExportState.step(index + 1, song.title)
                }
                zip.finish()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors += e.message ?: e.javaClass.simpleName
        }
        finish(ExportResult(success, songs.size - success, errors, "zip"))
    }

    /** Creates a ZIP in the app cache used for safe sharing of very large categories. */
    suspend fun createShareZip(
        context: Context,
        categoryName: String,
        songs: List<Song>
    ): File? = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "share")
        if (!folder.exists() && !folder.mkdirs()) return@withContext null
        val estimated = songs.sumOf { it.size.coerceAtLeast(0L) }
        if (context.cacheDir.usableSpace < estimated + 2L * 1024 * 1024) {
            ExportState.failed("Not enough free space for the shared archive")
            return@withContext null
        }
        val file = File(folder, "${FileNames.safeName(categoryName)}.zip")
        if (file.exists() && !file.delete()) return@withContext null
        ExportState.start("share", songs.size)
        val used = HashSet<String>()
        var success = 0
        val errors = ArrayList<String>()
        try {
            ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
                zip.setLevel(Deflater.NO_COMPRESSION)
                for ((index, song) in songs.withIndex()) {
                    yield()
                    try {
                        val entryName = FileNames.uniqueZipName(fileNameFor(context, song), used)
                        val input = context.contentResolver.openInputStream(Uri.parse(song.uri))
                            ?: throw IOException("Source file is unavailable")
                        input.use { source ->
                            zip.putNextEntry(ZipEntry(entryName))
                            val buffer = ByteArray(BUFFER)
                            while (true) {
                                val read = source.read(buffer)
                                if (read < 0) break
                                zip.write(buffer, 0, read)
                            }
                            zip.closeEntry()
                        }
                        success++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errors += "${song.title}: ${e.message ?: e.javaClass.simpleName}"
                        try { zip.closeEntry() } catch (_: Exception) { }
                    }
                    ExportState.step(index + 1, song.title)
                }
                zip.finish()
            }
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } catch (e: Exception) {
            errors += e.message ?: e.javaClass.simpleName
        }
        ExportState.finish(ExportResult(success, songs.size - success, errors, "share"))
        if (success == 0) {
            file.delete()
            null
        } else file
    }

    /** Returns null when the category is too large and must be shared as a ZIP. */
    fun shareSongsIntent(songs: List<Song>): Intent? {
        if (songs.isEmpty() || songs.size > MAX_DIRECT_URIS) return null
        val uris = songs.map { Uri.parse(it.uri) }
        val clip = ClipData.newRawUri("audio", uris.first())
        for (uri in uris.drop(1)) clip.addItem(ClipData.Item(uri))
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareZipIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("zip", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun needsZipForSharing(count: Int): Boolean = count > MAX_DIRECT_URIS

    private fun fileNameFor(context: Context, song: Song): String {
        val uri = Uri.parse(song.uri)
        var displayName: String? = null
        var mime: String? = null
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                    if (mimeIndex >= 0) mime = cursor.getString(mimeIndex)
                }
            }
        } catch (_: Exception) {
            // Some providers do not expose these columns; fall back below.
        }
        if (mime == null) mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
        val base = displayName?.takeIf { it.isNotBlank() } ?: song.title
        return FileNames.withExtension(base, mime)
    }

    private fun finish(result: ExportResult): ExportResult {
        ExportState.finish(result)
        return result
    }
}
