package com.ftwegc.myapplication.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Glass thickness: thicker glass blurs more and is tinted more. */
enum class GlassThickness {
    /** Chips, small buttons, fields. */
    Thin,

    /** Content panels (the bento blocks). */
    Standard,

    /** Navigation bar, add button, sheets and dialogs. */
    Thick,
}

/**
 * The look of one glass thickness in one theme. Numbers come from the mockup
 * (`docs/mockups/vluer-screens.html`, `.glass` / `.thick` / `.chip`): a 135° white sheen, a radial
 * highlight at 12 % / 0 %, a bright top edge, a softer left edge, a faint bottom edge and a soft
 * drop shadow. The fill is weaker than the mockup's in dark mode because body text must keep
 * 4.5:1 over the brightest background colors; the scrim below the fill makes up the rest.
 *
 * @property blurRadius backdrop blur (tiers that blur); capped at [GlassDefaults.MaxBlur].
 * @property fill the 135° sheen: start, middle (at 45 %), end.
 * @property sheen radial highlight at the top left.
 * @property edgeTop / [edgeSide] / [edgeBottom] 1 dp highlight edge, brightest at the top left.
 * @property scrim color that darkens (dark theme) or lightens (light theme) the backdrop under the
 *   fill for text contrast; its alpha is [GlassContrast.minScrimAlpha] for the current background.
 * @property tintedFloor minimum scrim alpha when nothing is blurred (Android 11 and lower): the
 *   background shows through unblurred, so the panel is more opaque.
 * @property solid the panel color with "reduce transparency" on.
 */
@Immutable
data class GlassMaterial(
    val blurRadius: Dp,
    val fill: List<Color>,
    val sheen: Color,
    val edgeTop: Color,
    val edgeSide: Color,
    val edgeBottom: Color,
    val shadowColor: Color,
    val shadowRadius: Dp,
    val shadowOffsetY: Dp,
    val scrim: Color,
    val tintedFloor: Float,
    val solid: Color,
) {
    /** The fill colors as the contrast calculation sees them: the brightest point of the sheen. */
    val contrastFills: List<Color> get() = listOf(fill.maxBy { it.alpha }, sheen)
}

/** Content colors on glass. */
@Immutable
data class GlassInk(
    /** Titles, numbers, body text. */
    val primary: Color,
    /** Labels, captions, axis text: still at least 4.5:1 on glass. */
    val secondary: Color,
    /** Tracks, grid lines, placeholders (not text). */
    val track: Color,
)

object GlassDefaults {
    /** Blur radius cap (larger radii cost more than they add; see docs/oss-research.md). */
    val MaxBlur: Dp = 11.dp

    /**
     * Blur of thick floating glass (the navigation bar, the add button, sheets). Above [MaxBlur]
     * on purpose: page text scrolls under these and must not stay legible through them. Only a
     * few such surfaces are on screen at a time.
     */
    val FloatingBlur: Dp = 18.dp

    /** Bento panels (mockup `.glass`, 26 px). */
    val PanelShape = RoundedCornerShape(26.dp)

    /** Cells inside a panel (mockup `.fld` in the node grid, 16 px). */
    val CellShape = RoundedCornerShape(16.dp)

    /** Chips and capsules. */
    val CapsuleShape = RoundedCornerShape(percent = 50)

    val DarkInk = GlassInk(
        primary = Color(0xFFEEF6F8),
        secondary = Color(0xFFEEF6F8).copy(alpha = 0.86f),
        track = Color.White.copy(alpha = 0.13f),
    )

    val LightInk = GlassInk(
        primary = Color(0xFF0B1A20),
        secondary = Color(0xFF0B1A20).copy(alpha = 0.74f),
        track = Color(0xFF0B1A20).copy(alpha = 0.10f),
    )

    private val DarkScrim = Color(0xFF080C14)
    private val LightScrim = Color(0xFFF6F9FC)

    fun material(thickness: GlassThickness, dark: Boolean): GlassMaterial = if (dark) dark(thickness) else light(thickness)

    private fun dark(thickness: GlassThickness): GlassMaterial = when (thickness) {
        GlassThickness.Thin -> GlassMaterial(
            blurRadius = 4.dp,
            fill = listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.10f)),
            sheen = Color.White.copy(alpha = 0.04f),
            edgeTop = Color.White.copy(alpha = 0.25f),
            edgeSide = Color.White.copy(alpha = 0.10f),
            edgeBottom = Color.White.copy(alpha = 0.04f),
            shadowColor = Color.Transparent,
            shadowRadius = 0.dp,
            shadowOffsetY = 0.dp,
            scrim = DarkScrim,
            tintedFloor = 0.35f,
            solid = Color(0xFF1B2430),
        )
        GlassThickness.Standard -> GlassMaterial(
            blurRadius = 6.dp,
            fill = listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f), Color.White.copy(alpha = 0.06f)),
            sheen = Color.White.copy(alpha = 0.06f),
            edgeTop = Color.White.copy(alpha = 0.42f),
            edgeSide = Color.White.copy(alpha = 0.16f),
            edgeBottom = Color.White.copy(alpha = 0.07f),
            shadowColor = Color.Black.copy(alpha = 0.28f),
            shadowRadius = 22.dp,
            shadowOffsetY = 12.dp,
            scrim = DarkScrim,
            tintedFloor = 0.45f,
            solid = Color(0xFF141C27),
        )
        GlassThickness.Thick -> GlassMaterial(
            blurRadius = MaxBlur,
            fill = listOf(Color(0xFF28344A).copy(alpha = 0.55f), Color(0xFF1C2638).copy(alpha = 0.48f), Color(0xFF141C28).copy(alpha = 0.46f)),
            sheen = Color.White.copy(alpha = 0.10f),
            edgeTop = Color.White.copy(alpha = 0.42f),
            edgeSide = Color.White.copy(alpha = 0.16f),
            edgeBottom = Color.White.copy(alpha = 0.07f),
            shadowColor = Color.Black.copy(alpha = 0.30f),
            shadowRadius = 22.dp,
            shadowOffsetY = 10.dp,
            scrim = DarkScrim,
            tintedFloor = 0.55f,
            solid = Color(0xFF18212D),
        )
    }

    private fun light(thickness: GlassThickness): GlassMaterial = when (thickness) {
        GlassThickness.Thin -> GlassMaterial(
            blurRadius = 4.dp,
            fill = listOf(Color.White.copy(alpha = 0.52f), Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.48f)),
            sheen = Color.White.copy(alpha = 0.20f),
            edgeTop = Color.White.copy(alpha = 0.95f),
            edgeSide = Color.White.copy(alpha = 0.60f),
            edgeBottom = Color(0xFF0B1A20).copy(alpha = 0.05f),
            shadowColor = Color.Transparent,
            shadowRadius = 0.dp,
            shadowOffsetY = 0.dp,
            scrim = LightScrim,
            tintedFloor = 0.35f,
            solid = Color(0xFFF1F5F9),
        )
        GlassThickness.Standard -> GlassMaterial(
            blurRadius = 6.dp,
            fill = listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.46f)),
            sheen = Color.White.copy(alpha = 0.34f),
            edgeTop = Color.White.copy(alpha = 0.95f),
            edgeSide = Color.White.copy(alpha = 0.65f),
            edgeBottom = Color(0xFF0B1A20).copy(alpha = 0.06f),
            shadowColor = Color(0xFF1B3346).copy(alpha = 0.12f),
            shadowRadius = 22.dp,
            shadowOffsetY = 10.dp,
            scrim = LightScrim,
            tintedFloor = 0.40f,
            solid = Color(0xFFF7FAFC),
        )
        GlassThickness.Thick -> GlassMaterial(
            blurRadius = MaxBlur,
            fill = listOf(Color.White.copy(alpha = 0.66f), Color(0xFFF4F8FB).copy(alpha = 0.54f), Color(0xFFEFF4F8).copy(alpha = 0.58f)),
            sheen = Color.White.copy(alpha = 0.30f),
            edgeTop = Color.White.copy(alpha = 0.95f),
            edgeSide = Color.White.copy(alpha = 0.65f),
            edgeBottom = Color(0xFF0B1A20).copy(alpha = 0.06f),
            shadowColor = Color(0xFF1B3346).copy(alpha = 0.16f),
            shadowRadius = 22.dp,
            shadowOffsetY = 10.dp,
            scrim = LightScrim,
            tintedFloor = 0.50f,
            solid = Color(0xFFF7FAFC),
        )
    }
}
