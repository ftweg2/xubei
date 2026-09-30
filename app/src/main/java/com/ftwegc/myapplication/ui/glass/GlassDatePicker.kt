package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.charts.CalendarCellShape
import com.ftwegc.myapplication.ui.charts.CalendarGrid
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.charts.drawSoftGlow
import com.ftwegc.myapplication.ui.format.fullDateText
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/**
 * The date picker in glass: a month calendar (the statistics page's calendar grid) in a glass
 * dialog. The chosen day is a glowing mint-to-blue cell, today is outlined; the arrows (or a
 * sideways swipe) change the month, "今天" jumps back. Chinese whatever the system language.
 */
@Composable
fun GlassDatePickerDialog(
    visible: Boolean,
    initial: LocalDate,
    title: String,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(com.ftwegc.myapplication.ui.common.appClock()),
) {
    // Reset to the given date every time the dialog opens.
    var picked by rememberSaveable(visible, initial) { mutableStateOf(initial) }
    var month by rememberSaveable(visible, initial) { mutableStateOf(YearMonth.from(initial)) }
    var forward by rememberSaveable { mutableStateOf(true) }
    val env = currentGlass()
    GlassDialogFrame(visible = visible, onDismissRequest = onDismiss, paneTitle = title) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = env.ink.secondary, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
            GlassChip(
                stringResource(R.string.date_picker_today),
                onClick = {
                    forward = YearMonth.from(today).isAfter(month)
                    month = YearMonth.from(today)
                    picked = today
                },
            )
        }
        Text(
            fullDateText(picked),
            color = env.ink.primary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        MonthSwitcher(
            month = month,
            onPrevious = {
                forward = false
                month = month.minusMonths(1)
            },
            onNext = {
                forward = true
                month = month.plusMonths(1)
            },
        )
        Spacer(Modifier.height(10.dp))
        val density = LocalDensity.current
        val swipe = with(density) { 56.dp.toPx() }
        val reduced = LocalReducedMotion.current
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(Motion.DurationMonthSwitch, easing = Motion.Emphasized)) { it / 3 * dir } + fadeIn(tween(Motion.DurationMonthSwitch))) togetherWith
                        (slideOutHorizontally(tween(Motion.DurationMonthSwitch, easing = Motion.Emphasized)) { -it / 3 * dir } + fadeOut(tween(Motion.DurationShort)))
                }
            },
            label = "pickerMonth",
            modifier = Modifier.pointerInput(Unit) {
                var travel = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragEnd = {
                        if (abs(travel) > swipe) {
                            forward = travel < 0
                            month = if (travel < 0) month.plusMonths(1) else month.minusMonths(1)
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    travel += amount
                }
            },
        ) { shown ->
            CalendarGrid(shown, spacing = 6.dp) { day ->
                val date = shown.atDay(day)
                PickerCell(day, selected = date == picked, isToday = date == today, onClick = { picked = date })
            }
        }
        Spacer(Modifier.height(20.dp))
        GlassDialogButtons(
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = {
                onPick(picked)
                onDismiss()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = onDismiss,
            confirmEnabled = visible,
        )
    }
}

/** "‹  2026 年 9 月  ›": glass arrows around the month. */
@Composable
fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(LineIcons.Back, stringResource(R.string.month_previous), onPrevious, size = 36.dp)
        Text(
            stringResource(R.string.month_label, month.year, month.monthValue),
            color = env.ink.primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        GlassIconButton(LineIcons.Forward, stringResource(R.string.month_next), onNext, size = 36.dp)
    }
}

@Composable
private fun PickerCell(day: Int, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val chart = chartColors()
    val env = currentGlass()
    val ring = if (chart.dark) Color.White else chart.ink
    val label = stringResource(R.string.date_picker_day_desc, day)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (selected) drawSoftGlow(chart.mint.copy(alpha = 0.55f), Offset.Zero, size, 9.dp.toPx(), 7.dp.toPx())
            }
            .background(
                if (selected) Brush.linearGradient(listOf(chart.mint, chart.blue)) else Brush.linearGradient(listOf(chart.track.copy(alpha = chart.track.alpha * 0.5f), chart.track.copy(alpha = chart.track.alpha * 0.5f))),
                CalendarCellShape,
            )
            .then(if (isToday && !selected) Modifier.border(1.5.dp, ring.copy(alpha = 0.85f), CalendarCellShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            color = if (selected) Color(0xFF03202A) else env.ink.primary,
            fontSize = 13.sp,
            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}
