package com.ftwegc.myapplication.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.background.AuroraClock
import com.ftwegc.myapplication.ui.components.rememberHapticClick
import com.ftwegc.myapplication.ui.glass.BackgroundFade
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GrowOrigin
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.glass
import com.ftwegc.myapplication.ui.glass.rememberGlassPress
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion

/**
 * Space tab pages leave at the bottom so their last item clears the floating dock (dock, its gap
 * and the system navigation bar). Zero outside the tab shell.
 */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/** Dock geometry (mockup `.dock`: 14 px from the sides, 20 px from the bottom, 9 px gap). */
object DockDefaults {
    val SideMargin: Dp = 14.dp
    val CollapsedSideMargin: Dp = 44.dp
    val Gap: Dp = 9.dp

    /** Gap above the gesture / button navigation bar (the mockup's 20 px on a phone without one). */
    val BottomGap: Dp = 12.dp
    val BottomGapWithoutNavBar: Dp = 20.dp

    /** Extra space between the last list item and the dock (the item ends clear of the fade too). */
    val ContentGap: Dp = 28.dp

    /** How far above the dock page content starts fading out. */
    val FadeReach: Dp = 36.dp
}

/** Distance from the screen bottom to the dock's bottom edge. */
@Composable
fun dockBottomOffset(): Dp {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return if (nav > 0.dp) nav + DockDefaults.BottomGap else DockDefaults.BottomGapWithoutNavBar
}

/** What tab pages pad at the bottom for the dock. */
@Composable
fun bottomBarTotalHeight(): Dp = dockBottomOffset() + LiquidTabsDefaults.Height + DockDefaults.ContentGap

/**
 * Collapses the dock while the page scrolls down and expands it when it scrolls up; also holds
 * the background drift still while anything scrolls (the glass then only re-samples what moved).
 */
@Stable
class DockScrollState(private val thresholdPx: Float, private val clock: AuroraClock?) {
    var collapsed: Boolean by mutableStateOf(false)
        private set

    private var travel = 0f

    fun expand() {
        collapsed = false
        travel = 0f
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y
            if (dy != 0f) {
                clock?.hold(Motion.AuroraScrollHoldMillis)
                // Content moving up (dy < 0) means the user scrolls down the page.
                travel = if (dy.sign() == travel.sign() || travel == 0f) travel + dy else dy
                if (travel < -thresholdPx && !collapsed) collapsed = true
                if (travel > thresholdPx && collapsed) collapsed = false
            }
            return Offset.Zero
        }
    }

    private fun Float.sign(): Float = if (this > 0f) 1f else if (this < 0f) -1f else 0f
}

/**
 * The floating dock: the glass tab bar and, next to it, the glass add button. Shrinks to an
 * icons-only bar while [collapsed].
 */
@Composable
fun VluerDock(
    selected: TopLevelTab,
    onSelect: (TopLevelTab) -> Unit,
    onAdd: () -> Unit,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    addOrigin: GrowOrigin? = null,
) {
    val reduced = LocalReducedMotion.current
    val collapse by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = if (reduced) tween(0) else tween(Motion.DurationDockCollapse, easing = Motion.Emphasized),
        label = "dockCollapse",
    )
    val tabs = TopLevelTab.entries.map { LiquidTab(stringResource(it.label), it.icon) }
    val side = lerp(DockDefaults.SideMargin, DockDefaults.CollapsedSideMargin, collapse)
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = side, end = side, bottom = dockBottomOffset()),
        horizontalArrangement = Arrangement.spacedBy(DockDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidTabs(
            tabs = tabs,
            selectedIndex = selected.ordinal,
            onSelect = { onSelect(TopLevelTab.entries[it]) },
            collapse = collapse,
            modifier = Modifier.weight(1f),
        )
        AddButton(onClick = onAdd, size = lerp(LiquidTabsDefaults.Height, LiquidTabsDefaults.CollapsedHeight, collapse), modifier = addOrigin?.modifier ?: Modifier)
    }
}

/** Mockup `.add`: a 62 px glass circle washed teal to blue, with a white plus. */
@Composable
private fun AddButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val click = rememberHapticClick(onClick)
    Box(
        modifier
            .size(size)
            .glass(
                shape = GlassDefaults.CapsuleShape,
                thickness = GlassThickness.Thick,
                floating = true,
                press = press,
                // Light theme: deeper colors, or the white plus fades into the pale glass.
                fill = if (currentGlass().dark) {
                    listOf(Color(0xFF2DD4BF).copy(alpha = 0.78f), Color(0xFF60A5FA).copy(alpha = 0.42f))
                } else {
                    listOf(Color(0xFF0D9488).copy(alpha = 0.88f), Color(0xFF2563EB).copy(alpha = 0.70f))
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.dock_add_desc),
                onClick = click,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(NavIcons.Add, contentDescription = stringResource(R.string.dock_add_desc), tint = Color.White, modifier = Modifier.size(25.dp))
    }
}

/**
 * The flowing background painted back over the page from just above the dock down to the screen
 * edge (see [BackgroundFade]). It is part of the page layer, so text scrolling toward the dock
 * dissolves into the background before it passes under the glass: from the dock's top edge down
 * the content is practically gone, below the dock (the gesture area) completely.
 */
@Composable
fun DockFade(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (LocalReducedMotion.current) tween(0) else tween(Motion.DurationDockHide),
        label = "dockFade",
    )
    if (alpha <= 0f) return
    val below = dockBottomOffset()
    val dock = LiquidTabsDefaults.Height
    val reach = DockDefaults.FadeReach
    val total = below + dock + reach
    val dockTop = reach / total
    val dockMiddle = (reach + dock / 2) / total
    val dockBottom = (reach + dock) / total
    BackgroundFade(
        stops = listOf(0f to 0f, dockTop * 0.5f to 0.38f, dockTop to 0.84f, dockMiddle to 0.96f, dockBottom to 1f, 1f to 1f),
        modifier = modifier
            .fillMaxWidth()
            .height(total)
            .graphicsLayer { this.alpha = alpha },
    )
}