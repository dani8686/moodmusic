package com.daniel.mood

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Small, bounded album-art cache. Decoding happens on Dispatchers.IO and every
 * bitmap is downsampled to at most 256 px, so a 1,200+ song library never
 * holds full-size cover images in memory.
 */
object AlbumArtLoader {
    private val cache = LruCache<Long, ImageBitmap>(48)
    private val albumArtBase = Uri.parse("content://media/external/audio/albumart")

    suspend fun load(context: Context, albumId: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        if (albumId <= 0L) return@withContext null
        val cached = cache.get(albumId)
        if (cached != null) return@withContext cached
        val uri = ContentUris.withAppendedId(albumArtBase, albumId)
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val largest = max(bounds.outWidth, bounds.outHeight)
            if (largest <= 0) return@withContext null
            val sample = (largest / 256).coerceAtLeast(1)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null
            val scale = 256f / max(decoded.width, decoded.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else decoded
            val image = scaled.asImageBitmap()
            if (scaled !== decoded) decoded.recycle()
            cache.put(albumId, image)
            image
        } catch (_: Exception) {
            null
        }
    }
}
