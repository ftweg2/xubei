package com.ftwegc.myapplication.ui.glass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** Geometry of the top bar of pages below the tabs (back, title, actions). */
object PageBarDefaults {
    /** The bar below the status bar. */
    val Height: Dp = 56.dp

    /** The fade continues this far below the bar. */
    val FadeReach: Dp = 30.dp

    /** Blur of what scrolls under the bar (as strong as the floating glass). */
    val Blur: Dp = GlassDefaults.FloatingBlur

    val Margin: Dp = 16.dp
}

/**
 * Paints the flowing background itself over the page content, opaque by [stops] (position 0..1
 * from the top of this box → opacity): content passing through dissolves into the background
 * instead of into a band of flat color. Without a recorded background (Android 11 and lower,
 * "reduce transparency") it paints the background's base color instead.
 */
@Composable
fun BackgroundFade(stops: List<Pair<Float, Float>>, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val bg = LocalGlassSource.current.backdrop
    val base = pageBarColor().copy(alpha = 1f)
    if (env.tier.blurs && bg != null) {
        val mask = remember(stops) { Brush.verticalGradient(*stops.map { (at, opacity) -> at to Color.Black.copy(alpha = opacity) }.toTypedArray()) }
        Box(
            modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // The aurora layer is translucent where it is dark (the window's base color
                    // shows through there), so the base goes under it or content would show.
                    drawRect(base)
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
                .drawBackdrop(
                    backdrop = bg,
                    shape = { RectangleShape },
                    effects = {},
                    highlight = null,
                    shadow = null,
                ),
        )
    } else {
        val brush = remember(stops, base) { Brush.verticalGradient(*stops.map { (at, opacity) -> at to base.copy(alpha = opacity) }.toTypedArray()) }
        Box(modifier.background(brush))
    }
}

/**
 * What scrolls under a page's top bar: blurred hard (the page content recorded in [contentLayer]
 * together with the background) and fading from opaque behind the bar to clear below it, so
 * text under the bar never collides with the title or the buttons. [visibility] 0..1 (usually
 * how far the page has scrolled). Without blur it is an opaque fade of the background color.
 */
@Composable
fun TopBarScrim(contentLayer: LayerBackdrop?, visibility: () -> Float, modifier: Modifier = Modifier, barHeight: Dp = PageBarDefaults.Height) {
    val env = currentGlass()
    val bg = LocalGlassSource.current.backdrop
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val total = statusTop + barHeight + PageBarDefaults.FadeReach
    val solid = with(density) { ((statusTop + barHeight * 0.9f).toPx() / total.toPx()).coerceIn(0f, 1f) }
    val base = pageBarColor().copy(alpha = 1f)
    val layered = modifier
        .fillMaxWidth()
        .height(total)
        .graphicsLayer {
            alpha = visibility().coerceIn(0f, 1f)
            compositingStrategy = CompositingStrategy.Offscreen
        }
    if (env.tier.blurs && bg != null && contentLayer != null) {
        val combined = rememberCombinedBackdrop(bg, contentLayer)
        val mask = remember(solid) { Brush.verticalGradient(0f to Color.Black, solid to Color.Black, 1f to Color.Transparent) }
        Box(
            layered
                .drawWithContent {
                    // The aurora layer is translucent where it is dark (the window's base color
                    // shows through there), so the base goes under it or content would show.
                    drawRect(base)
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
                .drawBackdrop(
                    backdrop = combined,
                    shape = { RectangleShape },
                    effects = { blur(PageBarDefaults.Blur.toPx()) },
                    highlight = null,
                    shadow = null,
                    // A veil of the background color: the blurred shapes stay soft behind the title.
                    onDrawSurface = { drawRect(base.copy(alpha = if (env.dark) 0.64f else 0.58f)) },
                ),
        )
    } else {
        Box(layered.background(Brush.verticalGradient(0f to base, solid to base.copy(alpha = 0.96f), 1f to base.copy(alpha = 0f))))
    }
}

/**
 * A page below the tabs (settings pages, Komari, about): the page content scrolls under a top
 * bar with the back button, the title (large at the top of the content, small in the bar once
 * scrolled) and [actions]; [bottomBar] (a save button) stays at the bottom above the keyboard,
 * the content fading out behind it.
 *
 * [content] gets the padding to apply and the modifier that records it for the bar's blur; it
 * lays itself out (a scrolling column or a lazy list). [collapsed] tells the bar how far the
 * content has scrolled away (0..1).
 */
@Composable
fun GlassPageScaffold(
    title: String,
    onBack: () -> Unit,
    collapsed: () -> Float,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.(contentPadding: PaddingValues, recordModifier: Modifier) -> Unit,
) {
    val env = currentGlass()
    val layer = if (env.tier.blurs) rememberLayerBackdrop() else null
    var bottomHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = if (bottomBar != null) with(density) { bottomHeight.toDp() } + 12.dp else navBottom + 28.dp
    Box(modifier.fillMaxSize()) {
        val padding = PaddingValues(
            start = PageBarDefaults.Margin,
            end = PageBarDefaults.Margin,
            top = statusTop + PageBarDefaults.Height,
            bottom = bottom,
        )
        content(padding, if (layer != null) Modifier.layerBackdrop(layer) else Modifier)
        TopBarScrim(layer, { (collapsed() * 3f).coerceIn(0f, 1f) })
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(PageBarDefaults.Height)
                .padding(horizontal = PageBarDefaults.Margin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassIconButton(LineIcons.Back, stringResource(R.string.action_back), onBack)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    title,
                    color = env.ink.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        val c = collapsed()
                        alpha = ((c - 0.5f) * 2f).coerceIn(0f, 1f)
                        translationY = (1f - c.coerceIn(0f, 1f)) * 8.dp.toPx()
                    },
                )
            }
            actions()
        }
        if (bottomBar != null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                BackgroundFade(
                    stops = listOf(0f to 0f, 0.3f to 0.75f, 0.5f to 0.95f, 1f to 1f),
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomHeight = it.height }
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = PageBarDefaults.Margin, end = PageBarDefaults.Margin, top = 40.dp, bottom = 14.dp),
                ) { bottomBar() }
            }
        }
    }
}

/** [GlassPageScaffold] with a scrolling column: the large [title] heads the content. */
@Composable
fun GlassPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scroll: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    spacing: Dp = 11.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val titleSpan = with(density) { PageTitleHeight.toPx() }
    GlassPageScaffold(
        title = title,
        onBack = onBack,
        collapsed = { (scroll.value / titleSpan).coerceIn(0f, 1f) },
        modifier = modifier,
        actions = actions,
        bottomBar = bottomBar,
    ) { padding, record ->
        Column(
            Modifier
                .fillMaxSize()
                .then(record)
                .verticalScroll(scroll)
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            PageTitle(title, subtitle = subtitle)
            content()
        }
    }
}

/** How far the large title scrolls before the small one shows in the bar. */
private val PageTitleHeight = 56.dp

/** Mockup `h1` (30 sp) with the dim line below (`.sub`), like the tab pages. */
@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val env = currentGlass()
    Column(modifier.padding(start = 3.dp, end = 3.dp, top = 2.dp, bottom = 4.dp)) {
        Text(
            title,
            color = env.ink.primary,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(subtitle, color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}
