package com.ftwegc.myapplication.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.ui.glass.glassShadow
import com.ftwegc.myapplication.ui.preview.GlassPreview

/**
 * The mark's drop shadow. Drawn from the cached nine-patch (see glassShadow): an elevation
 * shadow per mark cost the statistics page (twenty-odd marks) a lot of render time per frame.
 */
private val MarkShadow = Color.Black.copy(alpha = 0.28f)

/**
 * Mark sizes (mockup `.mark.s`, `.mark`, `.mark.l`): box, corner, icon, letter, flag badge.
 */
enum class MarkSize(
    val box: Dp,
    val corner: Dp,
    val glyph: Dp,
    val letter: TextUnit,
    val flag: Dp,
    val flagText: TextUnit,
    val flagOffset: Dp,
    val shadow: Dp,
) {
    Small(28.dp, 10.dp, 16.dp, 12.sp, 16.dp, 8.sp, 5.dp, 3.dp),
    Medium(44.dp, 15.dp, 24.dp, 18.sp, 21.dp, 11.sp, 6.dp, 6.dp),
    Large(78.dp, 26.dp, 42.dp, 32.sp, 30.dp, 16.sp, 8.dp, 10.dp),
}

/**
 * A subscription's mark: its service icon (or letter) in white on a small glass pedestal washed
 * with the brand color (mockup `.mark`: 135° gradient, a bright top edge, a darker inner bottom,
 * a soft shadow). VPS marks carry the node's flag in the corner (mockup `.flag`).
 *
 * @param contentDescription `null` when the name is shown next to the mark.
 */
@Composable
fun BrandMark(
    source: MarkSource,
    modifier: Modifier = Modifier,
    size: MarkSize = MarkSize.Medium,
    contentDescription: String? = null,
) {
    val spec = remember(source.name, source.iconKey, source.iconColor) {
        BrandCatalog.resolve(source.name, source.iconKey, source.iconColor)
    }
    val region = source.region?.takeIf { source.category == Category.VPS && it.isNotBlank() }
    val imageName = BrandCatalog.imageName(source.iconKey)
    val context = LocalContext.current
    // Only a picked picture loads anything (off the main thread, cached).
    val picture = if (imageName != null) {
        produceState<ImageBitmap?>(null, imageName) { value = IconImages.load(context, imageName) }.value
    } else {
        null
    }
    Box(modifier.size(size.box)) {
        if (picture != null) {
            MarkPicture(picture, size)
        } else {
            MarkBase(spec, markSize = size)
        }
        if (region != null) FlagBadge(region, size, Modifier.align(Alignment.BottomEnd))
        if (contentDescription != null) {
            Box(Modifier.matchParentSize().semantics { this.contentDescription = contentDescription })
        }
    }
}

@Composable
private fun MarkBase(spec: MarkSpec, markSize: MarkSize) {
    val shape = RoundedCornerShape(markSize.corner)
    val start = Color(spec.start)
    val end = Color(spec.end)
    Box(
        Modifier
            .size(markSize.box)
            .glassShadow(shape, markSize.shadow * 1.5f, markSize.shadow / 2, MarkShadow)
            .clip(shape)
            .drawWithCache {
                val base = Brush.linearGradient(listOf(start, end), start = Offset.Zero, end = Offset(size.width, size.height))
                // inset 0 -8px 14px rgba(0,0,0,.18): a darker inner bottom.
                val shade = Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.18f))
                val sheen = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = Offset(size.width * 0.2f, 0f),
                    radius = size.width * 0.9f,
                )
                // inset 0 1px 0 rgba(255,255,255,.5): the bright top edge.
                val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.5f), 0.35f to Color.White.copy(alpha = 0.06f))
                val stroke = 1.dp.toPx()
                val corner = androidx.compose.ui.geometry.CornerRadius(size.width * (15f / 44f))
                onDrawBehind {
                    drawRect(base)
                    drawRect(sheen)
                    drawRect(shade)
                    drawRoundRect(
                        edge,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                        cornerRadius = corner,
                        style = Stroke(stroke),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val vector = spec.brandId?.let { BrandCatalog.vector(it) }
        if (vector != null) {
            Image(rememberVectorPainter(vector), contentDescription = null, modifier = Modifier.size(markSize.glyph))
        } else {
            Text(
                text = spec.letter,
                color = Color.White,
                fontSize = markSize.letter,
                lineHeight = markSize.letter,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** A picture from the gallery as the mark: rounded like the mark, with its bright top edge and shadow. */
@Composable
private fun MarkPicture(picture: ImageBitmap, markSize: MarkSize) {
    val shape = RoundedCornerShape(markSize.corner)
    Box(
        Modifier
            .size(markSize.box)
            .glassShadow(shape, markSize.shadow * 1.5f, markSize.shadow / 2, MarkShadow)
            .clip(shape)
            .drawWithCache {
                val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.5f), 0.35f to Color.White.copy(alpha = 0.06f))
                val stroke = 1.dp.toPx()
                val corner = androidx.compose.ui.geometry.CornerRadius(size.width * (15f / 44f))
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(
                        edge,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                        cornerRadius = corner,
                        style = Stroke(stroke),
                    )
                }
            },
    ) {
        Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(markSize.box))
    }
}

/** Mockup `.flag`: the region emoji in a dark round badge with a light ring. */
@Composable
private fun FlagBadge(region: String, size: MarkSize, modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(x = size.flagOffset, y = size.flagOffset)
            .size(size.flag)
            .clip(CircleShape)
            .background(Color(0xFF0A0E14).copy(alpha = 0.85f))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(region, fontSize = size.flagText, lineHeight = size.flagText, maxLines = 1)
    }
}

@PreviewLightDark
@Composable
private fun BrandMarkPreview() {
    GlassPreview {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(MarkSource("Claude Max"), size = MarkSize.Small)
            BrandMark(MarkSource("ChatGPT Plus"))
            BrandMark(MarkSource("洛杉矶 9929", Category.VPS, region = "🇺🇸"))
            BrandMark(MarkSource("甲骨文 法兰克福", Category.VPS, region = "🇩🇪"), size = MarkSize.Large)
        }
    }
}
