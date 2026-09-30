package com.ftwegc.myapplication.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.Motion
import java.time.LocalDate
import java.time.YearMonth

/** A day without payment in dark mode (a dark wash rather than a white one). */
private val EmptyDayDark = Color(0xFF070B14).copy(alpha = 0.30f)

private fun solidBrush(color: Color): Brush = androidx.compose.ui.graphics.SolidColor(color)

/** Cell corners of the calendars (mockup `.cal span`: 9 px). */
val CalendarCellShape = RoundedCornerShape(9.dp)

/**
 * A month grid (mockup `.cal`): the weekday header (Monday first) and square cells [spacing]
 * apart; [cell] draws each day. Rows fade in one after another while [reveal] goes 0 → 1. Shared
 * by the payment calendar and the glass date picker.
 */
@Composable
fun CalendarGrid(
    month: YearMonth,
    modifier: Modifier = Modifier,
    reveal: () -> Float = { 1f },
    spacing: Dp = 5.dp,
    cell: @Composable BoxScope.(day: Int) -> Unit,
) {
    val chart = chartColors()
    val layout = remember(month) { ChartMath.calendarLayout(month) }
    val weekdays = stringArrayResource(R.array.chart_weekdays)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            weekdays.forEach { day ->
                Box(Modifier.weight(1f).height(16.dp), contentAlignment = Alignment.Center) {
                    Text(day, color = chart.inkSecondary, fontSize = 10.5.sp, maxLines = 1)
                }
            }
        }
        for (row in 0 until layout.rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val t = ((reveal() * layout.rows) - row).coerceIn(0f, 1f)
                        alpha = t
                        translationY = (1f - t) * 6.dp.toPx()
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (col in 0 until layout.columns) {
                    val day = row * layout.columns + col - layout.leadingBlanks + 1
                    if (day < 1 || day > layout.days) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) { cell(day) }
                    }
                }
            }
        }
    }
}

/**
 * A month as a calendar heat map (mockup `.cal`): days with a payment tinted mint by amount
 * (three levels, the largest a mint-to-blue gradient with a glow), today outlined in white, the
 * [selectedDay] ringed. Tapping a day reports it ([onSelectDay]).
 *
 * @param amounts payment per day of month (minor units or any comparable number).
 * @param dayDescription what a day reads out (its payments), for accessibility.
 */
@Composable
fun CalendarHeatmap(
    month: YearMonth,
    amounts: Map<Int, Long>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    today: LocalDate? = null,
    selectedDay: Int? = null,
    onSelectDay: ((Int) -> Unit)? = null,
    memoryKey: String? = null,
    dayDescription: ((Int) -> String)? = null,
) {
    val chart = chartColors()
    val max = amounts.values.maxOrNull() ?: 0L
    val reveal by rememberReveal(month to amounts, memoryKey, Motion.DurationChartFade, Motion.DelayChartArc, Motion.ChartFadeEasing)
    val dark = chart.dark
    CalendarGrid(
        month = month,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        reveal = { reveal },
    ) { day ->
        val level = ChartMath.heatLevel(amounts[day] ?: 0L, max)
        val isToday = today != null && YearMonth.from(today) == month && today.dayOfMonth == day
        val isSelected = selectedDay == day
        HeatCell(day, level, isToday, isSelected, dark, chart, onSelectDay, dayDescription?.invoke(day))
    }
}

@Composable
private fun HeatCell(
    day: Int,
    level: Int,
    isToday: Boolean,
    isSelected: Boolean,
    dark: Boolean,
    chart: ChartColors,
    onSelectDay: ((Int) -> Unit)?,
    description: String?,
) {
    val textColor = when (level) {
        0 -> chart.inkSecondary
        1 -> chart.ink
        2 -> if (dark) Color(0xFF002A20) else chart.ink
        else -> Color(0xFF03202A)
    }
    val ring = if (dark) Color.White else chart.ink
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (level == 3) {
                    // box-shadow: 0 0 14px rgba(126,240,221,.55)
                    drawSoftGlow(chart.mint.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset.Zero, size, 9.dp.toPx(), 7.dp.toPx())
                }
            }
            .background(
                when (level) {
                    // Empty days darken a little in dark mode: the colored background behind the
                    // glass must not read as a payment.
                    0 -> solidBrush(if (dark) EmptyDayDark else chart.track.copy(alpha = chart.track.alpha * 0.5f))
                    1 -> Brush.linearGradient(listOf(chart.mint.copy(alpha = 0.34f), chart.mint.copy(alpha = 0.34f)))
                    2 -> Brush.linearGradient(listOf(chart.mint.copy(alpha = 0.55f), chart.mint.copy(alpha = 0.55f)))
                    else -> Brush.linearGradient(listOf(chart.mint, chart.blue))
                },
                CalendarCellShape,
            )
            .then(
                when {
                    isSelected -> Modifier.border(2.dp, ring, CalendarCellShape)
                    isToday -> Modifier.border(1.5.dp, ring.copy(alpha = 0.85f), CalendarCellShape)
                    else -> Modifier
                },
            )
            .then(
                if (onSelectDay != null) {
                    Modifier
                        .clickable(onClickLabel = description) { onSelectDay(day) }
                        .semantics {
                            role = Role.Button
                            selected = isSelected
                            if (description != null) contentDescription = description
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = if (level == 3 || isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@PreviewLightDark
@Composable
private fun CalendarHeatmapPreview() {
    GlassPreview {
        CalendarHeatmap(
            month = YearMonth.of(2026, 9),
            amounts = mapOf(1 to 72_000L, 7 to 14_400L, 12 to 12_900L, 15 to 36_000L, 21 to 2_100L, 24 to 39_000L, 28 to 2_100L),
            today = LocalDate.of(2026, 9, 28),
            selectedDay = 15,
            contentDescription = "缴费日历",
        )
    }
}
