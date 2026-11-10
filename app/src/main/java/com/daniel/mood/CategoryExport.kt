package com.daniel.mood

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportNames {
    fun safe(value: String): String {
        val clean = value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').ifBlank { "audio" }
        if (clean.length <= 120) return clean
        val dot = clean.lastIndexOf('.')
        val extension = if (dot > 0 && clean.length - dot <= 16) clean.substring(dot) else ""
        return clean.take(120 - extension.length) + extension
    }

    /** Case-insensitive collision handling for FAT/exFAT USB drives and ZIP archives. */
    fun unique(requested: String, usedLowercase: MutableSet<String>): String {
        val clean = safe(requested)
        if (usedLowercase.add(clean.lowercase(Locale.ROOT))) return clean
        val dot = clean.lastIndexOf('.')
        val base = if (dot > 0) clean.substring(0, dot) else clean
        val extension = if (dot > 0) clean.substring(dot) else ""
        var number = 2
        while (true) {
            val candidate = "${base}_$number$extension"
            if (usedLowercase.add(candidate.lowercase(Locale.ROOT))) return candidate
            number++
        }
    }
}

data class TransferResult(val copied: Int, val failed: Int, val errors: List<String>)

/** Copies actual bytes via SAF. All methods are blocking and must be called on Dispatchers.IO. */
class CategoryExporter(private val context: Context) {
    private val resolver = context.contentResolver

    fun copyCategory(
        treeUri: Uri, categoryName: String, songs: List<Song>,
        progress: (done: Int, copied: Int, failed: Int) -> Unit
    ): TransferResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return TransferResult(0, songs.size, listOf("Destination folder is not accessible / پوشه مقصد در دسترس نیست"))
        val folderName = ExportNames.safe(categoryName).ifBlank { "MoodMusic" }
        val existing = root.findFile(folderName)
        val directory = if (existing?.isDirectory == true) existing else root.createDirectory(folderName)
            ?: return TransferResult(0, songs.size, listOf("Could not create category folder / ساخت پوشه ناموفق بود"))
        val used = try { directory.listFiles().mapNotNull { it.name?.lowercase(Locale.ROOT) }.toMutableSet() } catch (_: Exception) { mutableSetOf() }
        var copied = 0
        val errors = mutableListOf<String>()
        songs.forEachIndexed { index, song ->
            var destination: DocumentFile? = null
            try {
                val source = Uri.parse(song.uri)
                val displayName = queryDisplayName(resolver, source) ?: song.title
                val name = ExportNames.unique(displayName, used)
                destination = directory.createFile(resolver.getType(source) ?: "application/octet-stream", name)
                    ?: throw IOException("Could not create $name")
                resolver.openInputStream(source)?.use { input ->
                    resolver.openOutputStream(destination!!.uri, "w")?.use { output ->
                        input.copyTo(output, 128 * 1024)
                        output.flush()
                    } ?: throw IOException("Cannot write $name")
                } ?: throw IOException("Cannot read ${song.title}")
                copied++
            } catch (error: Exception) {
                try { destination?.delete() } catch (_: Exception) { }
                errors += "${song.title}: ${error.message ?: error.javaClass.simpleName}"
            }
            progress(index + 1, copied, index + 1 - copied)
        }
        return TransferResult(copied, songs.size - copied, errors)
    }

    fun writeZip(
        targetUri: Uri, songs: List<Song>,
        progress: (done: Int, copied: Int, failed: Int) -> Unit
    ): TransferResult {
        var written = 0
        val errors = mutableListOf<String>()
        val used = mutableSetOf<String>()
        resolver.openOutputStream(targetUri, "w")?.use { raw ->
            ZipOutputStream(raw.buffered(128 * 1024)).use { zip ->
                songs.forEachIndexed { index, song ->
                    try {
                        val source = Uri.parse(song.uri)
                        val requested = queryDisplayName(resolver, source) ?: song.title
                        val entryName = ExportNames.unique(requested, used)
                        resolver.openInputStream(source)?.use { input ->
                            zip.putNextEntry(ZipEntry(entryName))
                            input.copyTo(zip, 128 * 1024)
                            zip.closeEntry()
                        } ?: throw IOException("Cannot read ${song.title}")
                        written++
                    } catch (error: Exception) {
                        try { zip.closeEntry() } catch (_: Exception) { }
                        errors += "${song.title}: ${error.message ?: error.javaClass.simpleName}"
                    }
                    progress(index + 1, written, index + 1 - written)
                }
                zip.finish()
            }
        } ?: throw IOException("Could not create ZIP / ساخت فایل ZIP ممکن نشد")
        return TransferResult(written, songs.size - written, errors)
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}

fun categoryShareIntent(songs: List<Song>): Intent {
    val uris = songs.map { Uri.parse(it.uri) }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        val clip = ClipData("Mood Music", arrayOf("audio/*"), ClipData.Item(uris.first()))
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        this.clipData = clip
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
