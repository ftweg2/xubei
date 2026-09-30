package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.categoryLabel
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.glass.glass
import com.ftwegc.myapplication.ui.glass.rememberGlassPress
import com.ftwegc.myapplication.ui.navigation.SharedKeys
import com.ftwegc.myapplication.ui.navigation.sharedBoundsOrSelf
import com.ftwegc.myapplication.ui.navigation.sharedElementOrSelf
import com.ftwegc.myapplication.ui.components.rememberHapticClick
import kotlin.math.roundToInt

/** Alpha of an archived tile. */
private const val ARCHIVED_ALPHA = 0.6f

/** Tile (mockup `.tile`: 26 px corners, 14 px padding) and row corner radii. */
private val TileCorner: Dp = 26.dp

/** Tiles and rows keep the same corners, so the morph never recomposes the glass. */
private val TileShape = RoundedCornerShape(TileCorner)
private val Pad: Dp = 14.dp

private enum class Part { Mark, Ring, Name, Meta, Bar, Price, Rings }

/**
 * One subscription as a glass tile (morph 0) or a compact glass row (morph 1), and everything in
 * between: the mark, the due ring, name, cycle line, the VPS traffic bar, the price and, for a
 * live VPS, its CPU / memory / disk rings each glide between their tile and row places.
 *
 * Light glass (no refraction), as the performance rules ask for list items. Tap opens the
 * subscription; a long press reports the tile's bounds for the menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubscriptionTile(
    item: SubscriptionUi,
    morph: State<Float>,
    resources: NodeResources?,
    onClick: () -> Unit,
    onLongPress: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    sharedOrigin: String = SharedKeys.ORIGIN_LIST,
) {
    val env = currentGlass()
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val click = rememberHapticClick(onClick)
    val shape = TileShape
    val meta = subscriptionMeta(item)
    val description = buildString {
        append(item.name).append("，").append(categoryLabel(item.category)).append("，").append(meta)
        if (item.needsBillingInfo) append("，").append(stringResource(R.string.billing_incomplete_tag))
        else if (item.isFree) append("，").append(stringResource(R.string.free_tag))
        else append("，").append(Formatters.formatAmount(item.priceMinor, item.currency))
        append("，").append(dueShortText(item))
        if (item.archived) append("，").append(stringResource(R.string.archived_tag))
    }
    val openLabel = stringResource(R.string.home_open_detail)
    val menuLabel = stringResource(R.string.list_menu_open)
    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .sharedBoundsOrSelf(SharedKeys.card(sharedOrigin, item.id), scaleContent = false)
            .graphicsLayer { alpha = if (item.archived) ARCHIVED_ALPHA else 1f }
            .glass(shape = shape, thickness = GlassThickness.Standard, press = press)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = openLabel,
                onLongClickLabel = menuLabel,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(bounds)
                },
                onClick = click,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        TileBody(item, morph, resources, meta, sharedOrigin)
    }
}

@Composable
private fun TileBody(item: SubscriptionUi, morph: State<Float>, resources: NodeResources?, meta: String, sharedOrigin: String) {
    val env = currentGlass()
    val showRing = !item.needsBillingInfo
    val traffic = item.traffic
    Layout(
        content = {
            BrandMark(
                item.mark,
                size = MarkSize.Medium,
                modifier = Modifier
                    .layoutId(Part.Mark)
                    .sharedElementOrSelf(SharedKeys.icon(sharedOrigin, item.id)),
            )
            if (showRing) MiniDueRing(item, Modifier.layoutId(Part.Ring))
            Text(
                item.name,
                color = env.ink.primary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .layoutId(Part.Name)
                    .sharedBoundsOrSelf(SharedKeys.name(sharedOrigin, item.id)),
            )
            Text(
                meta,
                color = if (item.needsBillingInfo) GlassAccents.amber(env.dark) else env.ink.secondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.layoutId(Part.Meta),
            )
            if (traffic != null) TrafficMiniBar(traffic, Modifier.layoutId(Part.Bar), memoryKey = "tile-bar-${item.id}")
            if (item.needsBillingInfo) {
                IncompleteCapsule(Modifier.layoutId(Part.Price))
            } else {
                PriceLabel(item, Modifier.layoutId(Part.Price))
            }
            if (resources != null) ResourceRings(resources, Modifier.layoutId(Part.Rings))
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val t = morph.value.coerceIn(0f, 1f)
        val pad = Pad.toPx()
        val byPart = measurables.associateBy { it.layoutId as Part }
        val mark = byPart.getValue(Part.Mark).measure(Constraints())
        val ring = byPart[Part.Ring]?.measure(Constraints())
        val price = byPart.getValue(Part.Price).measure(Constraints(maxWidth = (w - pad * 2).roundToInt().coerceAtLeast(0)))
        val rings = byPart[Part.Rings]?.measure(Constraints())

        // Row: [mark] [name / meta / bar] [price over rings] [ring]
        val ringW = ring?.width ?: 0
        val priceColW = maxOf(price.width, rings?.width ?: 0).toFloat()
        val rowRingX = w - pad - ringW
        val rowPriceRight = if (ring != null) rowRingX - 12.dp.toPx() else w - pad
        val rowTextX = pad + mark.width + 12.dp.toPx()
        val rowTextW = (rowPriceRight - priceColW - 12.dp.toPx() - rowTextX).coerceAtLeast(1f)
        val tileTextW = (w - pad * 2).coerceAtLeast(1f)
        val textW = lerp(tileTextW, rowTextW, t).roundToInt().coerceAtLeast(1)

        val name = byPart.getValue(Part.Name).measure(Constraints(maxWidth = textW))
        val meta = byPart.getValue(Part.Meta).measure(Constraints(maxWidth = textW))
        val bar = byPart[Part.Bar]?.measure(Constraints.fixed(textW, 4.dp.roundToPx()))

        val barGap = 8.dp.toPx()
        // Tile positions.
        val tMarkX = pad
        val tMarkY = pad
        val tRingX = w - pad - ringW
        val tRingY = pad + (mark.height - (ring?.height ?: 0)) / 2f
        val tNameX = pad
        val tNameY = pad + mark.height + 11.dp.toPx()
        val tMetaY = tNameY + name.height + 1.dp.toPx()
        val tBarY = tMetaY + meta.height + barGap
        val tPriceY = h - pad - price.height
        val tPriceX = pad
        val tRingsX = w - pad - (rings?.width ?: 0)
        val tRingsY = h - pad - (price.height + (rings?.height ?: 0)) / 2f
        // Row positions.
        val textBlock = name.height + 1.dp.toPx() + meta.height + (if (bar != null) barGap + bar.height else 0f)
        val rMarkX = pad
        val rMarkY = (h - mark.height) / 2f
        val rRingY = (h - (ring?.height ?: 0)) / 2f
        val rNameY = (h - textBlock) / 2f
        val rMetaY = rNameY + name.height + 1.dp.toPx()
        val rBarY = rMetaY + meta.height + barGap
        val priceBlock = price.height + (if (rings != null) 4.dp.toPx() + rings.height else 0f)
        val rPriceY = (h - priceBlock) / 2f
        val rPriceX = rowPriceRight - price.width
        val rRingsX = rowPriceRight - (rings?.width ?: 0)
        val rRingsY = rPriceY + price.height + 4.dp.toPx()

        layout(w.roundToInt(), h.roundToInt()) {
            fun Float.i() = roundToInt()
            mark.place(lerp(tMarkX, rMarkX, t).i(), lerp(tMarkY, rMarkY, t).i())
            ring?.place(lerp(tRingX, rowRingX, t).i(), lerp(tRingY, rRingY, t).i())
            name.place(lerp(tNameX, rowTextX, t).i(), lerp(tNameY, rNameY, t).i())
            meta.place(lerp(tNameX, rowTextX, t).i(), lerp(tMetaY, rMetaY, t).i())
            bar?.place(lerp(tNameX, rowTextX, t).i(), lerp(tBarY, rBarY, t).i())
            price.place(lerp(tPriceX, rPriceX, t).i(), lerp(tPriceY, rPriceY, t).i())
            rings?.place(lerp(tRingsX, rRingsX, t).i(), lerp(tRingsY, rRingsY, t).i())
        }
    }
}

/**
 * Mockup: the subscription due soonest, as a wide amber-washed glass bar with its price, the days
 * left and the passed share of the cycle (coral when overdue).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedDueBar(
    item: SubscriptionUi,
    onClick: () -> Unit,
    onLongPress: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    sharedOrigin: String = SharedKeys.ORIGIN_LIST,
) {
    val env = currentGlass()
    val chart = chartColors()
    val days = item.daysLeft()
    val overdue = days != null && days < 0
    val soon = days != null && days <= DUE_WARN_DAYS
    val tint = when {
        overdue -> chart.coral
        soon -> Color(0xFFFBBF24)
        else -> chart.teal
    }
    val textColor = when {
        overdue -> GlassAccents.coral(env.dark)
        soon -> GlassAccents.amber(env.dark)
        else -> GlassAccents.teal(env.dark)
    }
    val barColors = when {
        overdue -> listOf(Color(0xFFFDA4AF), chart.coral)
        soon -> listOf(Color(0xFFFDE68A), Color(0xFFFBBF24))
        else -> listOf(chart.mint, chart.teal)
    }
    val interaction = remember { MutableInteractionSource() }
    val press = rememberGlassPress(interaction)
    val haptics = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val click = rememberHapticClick(onClick)
    val meta = subscriptionMeta(item)
    val dueText = dueShortText(item)
    val description = stringResource(
        R.string.list_pinned_desc,
        item.name,
        dueText,
        if (item.isFree) stringResource(R.string.free_tag) else Formatters.formatAmount(item.priceMinor, item.currency),
    )
    Box(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .sharedBoundsOrSelf(SharedKeys.card(sharedOrigin, item.id), scaleContent = false)
            .glass(shape = RoundedCornerShape(TileCorner), thickness = GlassThickness.Standard, tint = tint, press = press)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(bounds)
                },
                onClick = click,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(item.mark, size = MarkSize.Medium, modifier = Modifier.sharedElementOrSelf(SharedKeys.icon(sharedOrigin, item.id)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        color = env.ink.primary,
                        fontSize = 15.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.sharedBoundsOrSelf(SharedKeys.name(sharedOrigin, item.id)),
                    )
                    Text(meta, color = env.ink.secondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    PriceLabel(item, fontSize = 21.sp)
                    Text(dueText, color = textColor, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 1)
                }
            }
            CycleBar(item.elapsed, barColors, Modifier.padding(top = 12.dp), memoryKey = "pinned-${item.id}")
        }
    }
}

/** Mockup `.tb` inside the pinned bar: the passed share of the cycle. */
@Composable
fun CycleBar(fraction: Float, colors: List<Color>, modifier: Modifier = Modifier, memoryKey: String? = null) {
    val chart = chartColors()
    val anim = com.ftwegc.myapplication.ui.charts.rememberAnimatedValues(
        targets = listOf(fraction.coerceIn(0f, 1f)),
        memoryKey = memoryKey,
        durationMillis = com.ftwegc.myapplication.ui.theme.Motion.DurationHBarGrow,
        easing = com.ftwegc.myapplication.ui.theme.Motion.HBarGrowEasing,
        delayOf = { com.ftwegc.myapplication.ui.theme.Motion.DelayHBarGrow },
    )
    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
        drawRoundRect(chart.track, cornerRadius = r)
        val w = size.width * anim[0].value.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(colors, endX = w.coerceAtLeast(1f)),
                size = androidx.compose.ui.geometry.Size(w, size.height),
                cornerRadius = r,
            )
        }
    }
}
