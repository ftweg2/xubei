package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ftwegc.myapplication.ui.components.rememberHapticClick
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.LocalVluerColors
import com.ftwegc.myapplication.ui.theme.Motion
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.InnerShadow
import kotlinx.coroutines.flow.collectLatest

/** The glass environment, or a plain one outside the app shell (previews). */
@Composable
@ReadOnlyComposable
fun currentGlass(): GlassEnvironment = LocalGlass.current ?: GlassEnvironment.fallback(LocalVluerColors.current.isDark)

/**
 * The app's glass. Screens use this (or [glass] for custom layouts), never the glass library
 * directly.
 *
 * - [thickness]: thin for chips and small buttons, standard for panels, thick for bars and sheets.
 * - [tint]: an optional color washed over the glass from the top left (for example amber for a
 *   subscription about to expire); the scrim below gets stronger so text stays readable.
 * - [onClick]: makes the glass pressable: it sinks in a little, brightens under the finger and
 *   springs back on release.
 * - [floating]: glass that hovers over the page (bars, the add button): it samples the page
 *   content as well and, on Android 13+, refracts it at its edges. Never use it inside the page.
 * - [fill]: replaces the material's sheen with other colors (a filled action button).
 * - [exportBackdrop]: this glass hosts other glass; children then sample this glass instead of
 *   punching a hole to the background (the glass library crashes if glass is nested otherwise).
 *
 * How it is drawn depends on [GlassTier]: blur and refraction (Android 13+), blur only
 * (Android 12), a translucent tinted panel (Android 11 and lower) or an almost opaque one with
 * "reduce transparency" on. The highlight edge is drawn on every tier.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = GlassDefaults.PanelShape,
    thickness: GlassThickness = GlassThickness.Standard,
    tint: Color = Color.Unspecified,
    floating: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role = Role.Button,
    exportBackdrop: Boolean = false,
    contentAlignment: Alignment = Alignment.TopStart,
    fill: List<Color>? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val env = currentGlass()
    val interaction = if (onClick != null) remember { MutableInteractionSource() } else null
    val press = if (interaction != null) rememberGlassPress(interaction) else null
    val exported = if (exportBackdrop && env.tier.blurs) rememberLayerBackdrop() else null
    val source = LocalGlassSource.current
    val click = if (onClick != null) rememberHapticClick(onClick) else null
    Box(
        modifier
            .glass(shape, thickness, tint, floating, press, exported, fill = fill)
            .then(
                if (click != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = role,
                        onClickLabel = onClickLabel,
                        onClick = click,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        val childSource = if (exported != null) {
            GlassSource(exported, source.depth + 1, exported = true)
        } else {
            GlassSource(source.backdrop, source.depth + 1)
        }
        CompositionLocalProvider(
            LocalContentColor provides env.ink.primary,
            LocalGlassSource provides childSource,
        ) {
            content()
        }
    }
}

/** Press feedback of a glass: progress 0..1 and where the finger went down. */
@Stable
class GlassPressState internal constructor() {
    internal val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var position: Offset by mutableStateOf(Offset.Unspecified)
        internal set

    val value: Float get() = progress.value
}

/**
 * Follows [interactionSource]: presses in with the mockup's overshooting curve
 * (`transition: transform .25s cubic-bezier(.2,1.4,.4,1)`) and springs back on release. A very
 * short tap still shows a visible press. Jumps with animations off.
 */
@Composable
fun rememberGlassPress(interactionSource: InteractionSource): GlassPressState {
    val reduced = LocalReducedMotion.current
    val state = remember { GlassPressState() }
    LaunchedEffect(interactionSource, reduced) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    state.position = interaction.pressPosition
                    if (reduced) state.progress.snapTo(1f) else state.progress.animateTo(1f, Motion.glassPress())
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (reduced) {
                        state.progress.snapTo(0f)
                    } else {
                        if (state.progress.value < Motion.GlassMinVisiblePress) {
                            state.progress.animateTo(1f, tween(Motion.DurationGlassTap, easing = Motion.Standard))
                        }
                        state.progress.animateTo(0f, Motion.glassPress())
                    }
                }
            }
        }
    }
    return state
}

/** Alpha of the [GlassSurface] tint where it is strongest (mockup: amber .26). */
const val GLASS_TINT_ALPHA: Float = 0.26f

/** "Reduce transparency": panels are this opaque; floating bars fully. */
private const val SOLID_ALPHA = 0.97f
private const val SOLID_FLOATING_ALPHA = 1f

/**
 * The glass as a modifier, for layouts that need their own structure (the navigation bar).
 * See [GlassSurface] for the parameters; [press] adds press feedback, [exportTo] records this
 * glass for glass nested in it. [fill] replaces the material's sheen with other colors (the
 * add button's teal-to-blue glass).
 */
@Composable
fun Modifier.glass(
    shape: RoundedCornerShape,
    thickness: GlassThickness = GlassThickness.Standard,
    tint: Color = Color.Unspecified,
    floating: Boolean = false,
    press: GlassPressState? = null,
    exportTo: LayerBackdrop? = null,
    shadow: Boolean = true,
    fill: List<Color>? = null,
): Modifier {
    val env = currentGlass()
    val tier = env.tier
    val material = env.material(thickness)
    val source = LocalGlassSource.current
    val page = LocalPageBackdrop.current
    val nestedFlat = source.depth > 0 && !source.exported && !floating
    val backdrop: Backdrop = when {
        !tier.blurs || nestedFlat -> emptyBackdrop()
        floating -> page ?: source.backdrop ?: emptyBackdrop()
        else -> source.backdrop ?: emptyBackdrop()
    }
    val sampling = backdrop !== emptyBackdrop()
    val scrimAlpha = remember(env, thickness, floating, tint) {
        when {
            nestedFlat -> 0f
            tint.isSpecified -> env.scrimAlpha(thickness, floating, tint, GLASS_TINT_ALPHA)
            else -> env.scrimAlpha(thickness, floating)
        }
    }.let { alpha ->
        // Blurring tiers that have nothing to sample (a glass outside the shell) behave like tinted glass.
        if (tier.blurs && !sampling && !nestedFlat) maxOf(alpha, material.tintedFloor) else alpha
    }
    val refract = floating && tier.refracts && sampling
    val showShadow = shadow && !nestedFlat && material.shadowColor.alpha > 0f
    return this
        .then(if (showShadow) Modifier.glassShadow(shape, material.shadowRadius, material.shadowOffsetY, material.shadowColor) else Modifier)
        .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (sampling) {
                // Panels in the page sample only the soft aurora: the saturation boost is not
                // visible there, but its color-filter pass cost every panel an extra offscreen
                // render per frame (the statistics page, with a dozen panels, dropped to ~40 fps
                // on the Android 13 emulator).
                if (floating || source.exported) vibrancy()
                // Bars and sheets over moving content blur harder: what passes under them should
                // read as color and shape only, never as text (see docs/design-brief-v2.md).
                val radius = if (floating && thickness == GlassThickness.Thick) GlassDefaults.FloatingBlur else material.blurRadius.coerceAtMost(GlassDefaults.MaxBlur)
                // Glass in the page samples only the aurora, which is soft radial gradients: a blur
                // of it looks the same as the aurora itself, but blurring every panel's area cost
                // the detail page about 13 ms per frame while scrolling on the Android 13 emulator.
                // So only glass over real content (floating bars, glass inside a sheet) blurs.
                if (floating || source.exported) blur(radius.toPx())
                if (refract) {
                    lens(
                        refractionHeight = GlassRefraction.Height.toPx(),
                        refractionAmount = GlassRefraction.Amount.toPx(),
                        depthEffect = true,
                    )
                }
            }
        },
        highlight = null,
        // Drawn by glassShadow above: the library's shadow re-rasterizes a blur on every frame.
        shadow = null,
        innerShadow = if (press != null) {
            {
                val p = press.value
                if (p > 0.01f) InnerShadow(radius = Motion.GlassPressInnerShadow * p, alpha = p * Motion.GlassPressInnerShadowAlpha) else null
            }
        } else {
            null
        },
        layerBlock = if (press != null) {
            {
                val scale = lerp(1f, Motion.GlassPressedScale, press.value)
                scaleX = scale
                scaleY = scale
            }
        } else {
            null
        },
        exportedBackdrop = exportTo,
        onDrawSurface = {
            drawGlassSurface(
                tier = tier,
                material = material,
                scrimAlpha = scrimAlpha,
                tint = tint,
                pressProgress = press?.value ?: 0f,
                pressPosition = press?.position ?: Offset.Unspecified,
                shape = shape,
                fill = fill ?: material.fill,
                floating = floating,
            )
        },
    )
}

/** Refraction strength of floating glass (Android 13+). */
object GlassRefraction {
    val Height = 14.dp
    val Amount = 22.dp
}

private fun DrawScope.drawGlassSurface(
    tier: GlassTier,
    material: GlassMaterial,
    scrimAlpha: Float,
    tint: Color,
    pressProgress: Float,
    pressPosition: Offset,
    shape: RoundedCornerShape,
    fill: List<Color>,
    floating: Boolean,
) {
    val w = size.width
    val h = size.height
    if (tier == GlassTier.Solid) {
        drawRect(material.solid.copy(alpha = if (floating) SOLID_FLOATING_ALPHA else SOLID_ALPHA))
    } else if (scrimAlpha > 0f) {
        drawRect(material.scrim.copy(alpha = scrimAlpha))
    }
    // 135° sheen (mockup: linear-gradient(135deg, …, … 45%, …)).
    drawRect(
        if (fill.size >= 3) {
            Brush.linearGradient(0f to fill[0], 0.45f to fill[1], 1f to fill[2], start = Offset.Zero, end = Offset(w, h))
        } else {
            Brush.linearGradient(fill, start = Offset.Zero, end = Offset(w, h))
        },
    )
    if (tint.isSpecified) {
        drawRect(
            Brush.linearGradient(
                0f to tint.copy(alpha = GLASS_TINT_ALPHA),
                0.55f to tint.copy(alpha = 0f),
                start = Offset.Zero,
                end = Offset(w, h),
            ),
        )
    }
    // Radial highlight at the top left (mockup ::before: radial-gradient(120% 70% at 12% 0%, …, transparent 55%)).
    if (h > 0f && w > 0f) {
        val pivot = Offset(w * 0.12f, 0f)
        val radius = h * 0.7f
        scale(scaleX = (w * 1.2f) / radius, scaleY = 1f, pivot = pivot) {
            drawCircle(
                Brush.radialGradient(0f to material.sheen, 0.55f to material.sheen.copy(alpha = 0f), center = pivot, radius = radius),
                radius = radius,
                center = pivot,
            )
        }
    }
    // Pressed: brighter (mockup filter: brightness(1.12)) and a glow under the finger.
    if (pressProgress > 0f) {
        drawRect(Color.White.copy(alpha = Motion.GlassPressBrighten * pressProgress), blendMode = BlendMode.Plus)
        val at = if (pressPosition.isSpecified()) pressPosition else center
        val glow = size.minDimension * 1.2f
        drawCircle(
            Brush.radialGradient(0f to Color.White.copy(alpha = 0.16f * pressProgress), 1f to Color.Transparent, center = at, radius = glow),
            radius = glow,
            center = at,
            blendMode = BlendMode.Plus,
        )
    }
    drawGlassEdge(material, shape, pressProgress, pressPosition)
}

private fun Offset.isSpecified(): Boolean = this != Offset.Unspecified && x.isFinite() && y.isFinite()

/**
 * The 1 dp highlight edge: brightest along the top (mockup `inset 0 1px 0 rgba(255,255,255,.42)`),
 * softer on the left (`inset 1px 0 0 … .16`), faint at the bottom (`inset 0 -1px 0 … .07`). While
 * pressed the light moves toward the finger.
 */
private fun DrawScope.drawGlassEdge(material: GlassMaterial, shape: RoundedCornerShape, pressProgress: Float, pressPosition: Offset) {
    val stroke = 1.dp.toPx()
    val inset = stroke / 2
    val inner = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
    if (inner.width <= 0f || inner.height <= 0f) return
    val outline = shape.createOutline(inner, layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Generic -> addPath(outline.path)
        }
        translate(Offset(inset, inset))
    }
    drawPath(
        path,
        Brush.verticalGradient(
            0f to material.edgeTop,
            0.30f to material.edgeSide.copy(alpha = material.edgeSide.alpha * 0.6f),
            1f to material.edgeBottom,
        ),
        style = Stroke(stroke),
    )
    drawPath(
        path,
        Brush.horizontalGradient(0f to material.edgeSide, 0.3f to material.edgeSide.copy(alpha = 0f)),
        style = Stroke(stroke),
    )
    if (pressProgress > 0f && pressPosition.isSpecified()) {
        val reach = size.minDimension
        drawPath(
            path,
            Brush.radialGradient(
                0f to material.edgeTop.copy(alpha = material.edgeTop.alpha * pressProgress),
                1f to Color.Transparent,
                center = pressPosition,
                radius = reach,
            ),
            style = Stroke(stroke * 1.5f),
        )
    }
}
