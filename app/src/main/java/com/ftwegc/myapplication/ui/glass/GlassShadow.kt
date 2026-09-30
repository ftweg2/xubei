package com.ftwegc.myapplication.ui.glass

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The soft drop shadow under glass (mockup `.glass`: `0 14px 34px rgba(0,0,0,.28)`), drawn from
 * a small cached nine-patch.
 *
 * Why not the glass library's shadow: it blurs the shape with a mask filter on every draw, which
 * the renderer rasterizes on the CPU and uploads as a new texture ("slow bitmap uploads"); with a
 * dozen tiles scrolling that alone dropped the subscription page to about 15 frames per second on
 * the Android 13 emulator. Here the blurred corner is rendered once per corner radius, blur and
 * color, and every frame only stretches it (nine image draws). The shadow is kept outside the
 * shape, so it never darkens the glass itself.
 */
fun Modifier.glassShadow(shape: RoundedCornerShape, radius: Dp, offsetY: Dp, color: Color): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val corner = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
        val blur = radius.toPx()
        val patch = ShadowPatches.get(corner.roundToInt(), blur.roundToInt(), color.toArgb())
        val clip = Path().apply {
            when (outline) {
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val dy = offsetY.toPx()
        onDrawBehind {
            clipPath(clip, ClipOp.Difference) {
                drawNinePatch(patch, Offset(0f, dy))
            }
        }
    }

/** A blurred rounded square cut into nine: [edge] px corners around a 1 px stretchable middle. */
internal class ShadowPatch(val image: ImageBitmap, val edge: Int, val pad: Int)

private fun DrawScope.drawNinePatch(patch: ShadowPatch, offset: Offset) {
    val e = patch.edge
    val pad = patch.pad
    val left = (offset.x - pad).roundToInt()
    val top = (offset.y - pad).roundToInt()
    val w = (size.width + pad * 2).roundToInt()
    val h = (size.height + pad * 2).roundToInt()
    // Shapes smaller than two corners: shrink the corners.
    val ex = minOf(e, w / 2)
    val ey = minOf(e, h / 2)
    val midW = (w - ex * 2).coerceAtLeast(0)
    val midH = (h - ey * 2).coerceAtLeast(0)
    val src = patch.image
    val srcMid = e // the 1 px middle column / row
    val cols = listOf(Triple(0, ex, 0), Triple(srcMid, midW, ex), Triple(src.width - ex, ex, ex + midW))
    val rows = listOf(Triple(0, ey, 0), Triple(srcMid, midH, ey), Triple(src.height - ey, ey, ey + midH))
    for ((sx, dw, dx) in cols) {
        if (dw <= 0) continue
        val sw = if (sx == srcMid) 1 else ex
        for ((sy, dh, dyy) in rows) {
            if (dh <= 0) continue
            val sh = if (sy == srcMid) 1 else ey
            drawImage(
                image = src,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(left + dx, top + dyy),
                dstSize = IntSize(dw, dh),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** Nine-patches by (corner, blur, color); only a handful exist (panels, tiles, the dock). */
internal object ShadowPatches {
    private data class Key(val corner: Int, val blur: Int, val argb: Int)

    private val cache = object : LinkedHashMap<Key, ShadowPatch>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ShadowPatch>?): Boolean = size > MAX_ENTRIES
    }

    private const val MAX_ENTRIES = 24

    /** Mask blur radius per shadow radius: matches the glass library's shadow softness. */
    private const val BLUR_SCALE = 0.8f

    fun get(corner: Int, blur: Int, argb: Int): ShadowPatch = synchronized(cache) {
        cache.getOrPut(Key(corner, blur, argb)) { build(corner, blur, argb) }
    }

    private fun build(corner: Int, blur: Int, argb: Int): ShadowPatch {
        // A mask blur of radius r reaches about r beyond the shape.
        val pad = ceil(blur * 1.1f).toInt().coerceAtLeast(1)
        val edge = pad + corner.coerceAtLeast(1)
        val side = edge * 2 + 1
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            if (blur > 0) maskFilter = BlurMaskFilter(blur * BLUR_SCALE, BlurMaskFilter.Blur.NORMAL)
        }
        val inner = RectF(pad.toFloat(), pad.toFloat(), (side - pad).toFloat(), (side - pad).toFloat())
        canvas.drawRoundRect(inner, corner.toFloat(), corner.toFloat(), paint)
        return ShadowPatch(bitmap.asImageBitmap().also { it.prepareToDraw() }, edge, pad)
    }
}
