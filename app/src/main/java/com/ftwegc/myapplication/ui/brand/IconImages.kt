package com.ftwegc.myapplication.ui.brand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Pictures picked from the gallery as subscription marks. The system photo picker hands out a
 * temporary URI; the picture is cropped to a centered square with rounded corners, scaled down
 * and copied into the app's private storage, so no storage permission is needed and the mark
 * survives the picker's grant.
 */
object IconImages {

    private const val DIR = "icons"

    /** Stored size in pixels (a large mark is 78 dp: about 273 px at 3.5×). */
    private const val SIZE = 288

    /** Corner radius relative to the size (the mark's 26 / 78). */
    private const val CORNER = 26f / 78f

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    private fun dir(context: Context): File = File(context.filesDir, DIR)

    /** The stored file of [name] (see [BrandCatalog.imageName]), `null` when it does not exist. */
    fun file(context: Context, name: String): File? = File(dir(context), name).takeIf { it.isFile }

    /**
     * Copies the picture at [uri] into private storage (cropped, rounded, scaled) and returns its
     * icon key ("image:<file>"), or `null` when it cannot be read.
     */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val side = minOf(source.width, source.height)
            val crop = Rect((source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2)
            val out = createBitmap(SIZE, SIZE)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val r = SIZE * CORNER
            canvas.drawRoundRect(RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), r, r, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(source, crop, Rect(0, 0, SIZE, SIZE), paint)
            source.recycle()
            val name = "${UUID.randomUUID()}.png"
            dir(context).mkdirs()
            File(dir(context), name).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cache[name] = out.asImageBitmap()
            BrandCatalog.IMAGE_PREFIX + name
        }.getOrNull()
    }

    /** The picture of [name], decoded once and cached; `null` when the file is missing. */
    suspend fun load(context: Context, name: String): ImageBitmap? {
        cache[name]?.let { return it }
        return withContext(Dispatchers.IO) {
            val file = file(context, name) ?: return@withContext null
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()?.also { cache[name] = it }
        }
    }

    /** Removes pictures no longer used (a replaced mark, a sheet closed without saving). */
    suspend fun delete(context: Context, keys: Collection<String>) = withContext(Dispatchers.IO) {
        keys.mapNotNull { BrandCatalog.imageName(it) }.forEach { name ->
            cache.remove(name)
            runCatching { File(dir(context), name).delete() }
        }
    }
}
