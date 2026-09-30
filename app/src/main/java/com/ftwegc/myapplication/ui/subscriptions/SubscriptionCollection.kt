package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Geometry of the tiles and rows (mockup `.grid` gap 11 px, `.tile` min-height 150 px). */
object CollectionDefaults {
    val Gap: Dp = 11.dp
    val TileHeight: Dp = 150.dp
    val TileHeightVps: Dp = 162.dp
    val RowHeight: Dp = 74.dp
    val RowHeightVps: Dp = 88.dp

    /** Tiles within this distance of the screen are still drawn (smooth edges while flinging). */
    val CullMargin: Dp = 120.dp
}

/** A tile carries a traffic bar (VPS with usage): it is a little taller. */
fun SubscriptionUi.hasBar(): Boolean = traffic != null

/**
 * Keeps the tiles on screen while the list changes: new tiles fade in, removed ones fade out in
 * place, the others move to their new places. The layout reads the animated values while
 * measuring, so nothing recomposes during the motion.
 */
@Stable
internal class CollectionAnimator(private val scope: CoroutineScope, private val reduced: Boolean) {

    @Stable
    inner class Entry(item: SubscriptionUi, var slot: Int) {
        val id: Long = item.id
        var item: SubscriptionUi by mutableStateOf(item)
        var leaving: Boolean by mutableStateOf(false)
        val appear = Animatable(if (reduced) 1f else 0f)
        val move = Animatable(1f)

        /** Where this entry was drawn last (layout coordinates), the start of a move. */
        var lastRect: Rect? = null
        var moveFrom: Rect? = null
    }

    val entries = mutableStateListOf<Entry>()
    private var first = true

    fun update(items: List<SubscriptionUi>) {
        val byId = entries.associateBy { it.id }
        val keep = items.map { it.id }.toSet()
        // Leaving entries stay where they were until their fade ends.
        entries.filter { it.id !in keep && !it.leaving }.forEach { e ->
            e.leaving = true
            scope.launch {
                if (!reduced) e.appear.animateTo(0f, tween(Motion.DurationItemExit, easing = Motion.EmphasizedAccelerate))
                entries.remove(e)
            }
        }
        items.forEachIndexed { index, item ->
            val existing = byId[item.id]?.takeIf { !it.leaving }
            if (existing == null) {
                val e = Entry(item, index)
                entries += e
                val delay = if (first) Motion.riseDelay(index.coerceAtMost(8)) else 0
                scope.launch {
                    if (!reduced) e.appear.animateTo(1f, tween(Motion.DurationRise, delay, Motion.RiseEasing)) else e.appear.snapTo(1f)
                }
            } else {
                existing.item = item
                if (existing.slot != index) {
                    existing.slot = index
                    existing.moveFrom = existing.lastRect
                    if (!reduced && existing.moveFrom != null) {
                        scope.launch {
                            existing.move.snapTo(0f)
                            existing.move.animateTo(1f, tween(Motion.DurationMorph, easing = Motion.MorphEasing))
                        }
                    }
                }
            }
        }
        first = false
    }
}

/**
 * The subscriptions below the pinned bar, as two columns of tiles or one column of rows. Switching
 * morphs every tile into its row (and back): position, size and the inside of the tile all follow
 * one [morph] value (0 = tiles, 1 = rows). Sorting, filtering, adding and removing move the tiles
 * to their new places.
 *
 * Tiles scrolled well out of the screen are not placed, so their glass is not drawn ([scroll] is
 * the page's scroll state, [viewportHeight] its visible height in pixels).
 */
@Composable
fun SubscriptionCollection(
    items: List<SubscriptionUi>,
    morph: State<Float>,
    scroll: ScrollState,
    viewportHeight: () -> Int,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: SubscriptionUi, leaving: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val animator = remember(reduced) { CollectionAnimator(scope, reduced) }
    LaunchedEffect(items, animator) { animator.update(items) }
    var top by remember { mutableFloatStateOf(0f) }

    Layout(
        modifier = modifier.onPlaced { top = it.positionInParent().y },
        content = {
            animator.entries.forEach { entry ->
                key(entry) {
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .layoutId(entry)
                            .graphicsLayer {
                                val a = entry.appear.value
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                                alpha = a.coerceIn(0f, 1f)
                                val s = if (entry.leaving) lerp(Motion.ItemExitScale, 1f, a) else lerp(Motion.RiseScale, 1f, a.coerceIn(0f, 1f))
                                scaleX = s
                                scaleY = s
                                translationY = if (entry.leaving) 0f else (1f - a) * Motion.RiseOffset.toPx()
                            },
                    ) {
                        itemContent(entry.item, entry.leaving)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = CollectionDefaults.Gap.roundToPx().toFloat()
        val tileW = (width - gap) / 2f
        val t = morph.value.coerceIn(0f, 1f)
        // Slots of the live (not leaving) entries.
        val live = animator.entries.filter { !it.leaving }.sortedBy { it.slot }
        val gridRects = HashMap<Long, Rect>()
        val listRects = HashMap<Long, Rect>()
        var y = 0f
        live.chunked(2).forEach { pair ->
            val h = pair.maxOf { (if (it.item.hasBar()) CollectionDefaults.TileHeightVps else CollectionDefaults.TileHeight).toPx() }
            pair.forEachIndexed { col, e ->
                val x = col * (tileW + gap)
                gridRects[e.id] = Rect(x, y, x + tileW, y + h)
            }
            y += h + gap
        }
        val gridHeight = (y - gap).coerceAtLeast(0f)
        y = 0f
        live.forEach { e ->
            val h = (if (e.item.hasBar()) CollectionDefaults.RowHeightVps else CollectionDefaults.RowHeight).toPx()
            listRects[e.id] = Rect(0f, y, width.toFloat(), y + h)
            y += h + gap
        }
        val listHeight = (y - gap).coerceAtLeast(0f)
        val totalHeight = lerp(gridHeight, listHeight, t).roundToInt().coerceAtLeast(0)

        val placements = measurables.mapNotNull { m ->
            val entry = m.layoutId as? CollectionAnimator.Entry ?: return@mapNotNull null
            val target = if (entry.leaving) {
                entry.lastRect ?: return@mapNotNull null
            } else {
                val g = gridRects[entry.id] ?: return@mapNotNull null
                val l = listRects[entry.id] ?: return@mapNotNull null
                lerpRect(g, l, t)
            }
            val from = entry.moveFrom
            val rect = if (!entry.leaving && from != null && entry.move.value < 1f) lerpRect(from, target, entry.move.value) else target
            if (!entry.leaving) entry.lastRect = rect
            val placeable = m.measure(
                Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)),
            )
            Triple(placeable, rect, entry)
        }
        layout(width, totalHeight) {
            val margin = CollectionDefaults.CullMargin.toPx()
            val visibleTop = scroll.value - top - margin
            val visibleBottom = scroll.value - top + viewportHeight() + margin
            placements.forEach { (placeable, rect, _) ->
                if (rect.bottom < visibleTop || rect.top > visibleBottom) return@forEach
                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
}

private fun lerpRect(a: Rect, b: Rect, t: Float): Rect = Rect(
    lerp(a.left, b.left, t),
    lerp(a.top, b.top, t),
    lerp(a.right, b.right, t),
    lerp(a.bottom, b.bottom, t),
)
