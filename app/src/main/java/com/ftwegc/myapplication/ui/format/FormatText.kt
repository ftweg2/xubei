package com.ftwegc.myapplication.ui.format

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.ui.theme.VluerTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Maps formatter output and domain enums to string resources, icons and colors.
 */

@Composable
@ReadOnlyComposable
fun dueLabelText(label: DueLabel): String = when (label) {
    is DueLabel.InDays -> stringResource(R.string.due_in_days, label.days.toInt())
    DueLabel.Today -> stringResource(R.string.due_today)
    is DueLabel.Overdue -> stringResource(R.string.due_overdue_days, label.days.toInt())
    DueLabel.Permanent -> stringResource(R.string.due_permanent)
    DueLabel.NoDate -> stringResource(R.string.due_no_date)
}

@Composable
@ReadOnlyComposable
fun cycleText(cycle: BillingCycle): String = when (cycle.unit) {
    CycleUnit.ONCE -> stringResource(R.string.cycle_once)
    CycleUnit.DAY -> stringResource(R.string.cycle_every_n_days, cycle.count)
    CycleUnit.MONTH -> when (cycle.count) {
        1 -> stringResource(R.string.cycle_monthly)
        3 -> stringResource(R.string.cycle_quarterly)
        6 -> stringResource(R.string.cycle_semi_annual)
        12 -> stringResource(R.string.cycle_annual)
        24 -> stringResource(R.string.cycle_biennial)
        36 -> stringResource(R.string.cycle_triennial)
        else -> stringResource(R.string.cycle_every_n_months, cycle.count)
    }
}

@Composable
@ReadOnlyComposable
fun categoryLabel(category: Category): String = when (category) {
    Category.AI -> stringResource(R.string.category_ai)
    Category.VPS -> stringResource(R.string.category_vps)
    Category.OTHER -> stringResource(R.string.category_other)
}

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.AI -> Icons.Rounded.AutoAwesome
    Category.VPS -> Icons.Rounded.Dns
    Category.OTHER -> Icons.Rounded.Widgets
}

@Composable
@ReadOnlyComposable
fun categoryColor(category: Category): Color = when (category) {
    Category.AI -> VluerTheme.colors.categoryAi
    Category.VPS -> VluerTheme.colors.categoryVps
    Category.OTHER -> VluerTheme.colors.categoryOther
}

@Composable
@ReadOnlyComposable
fun limitTypeText(type: TrafficLimitType): String = when (type) {
    TrafficLimitType.SUM -> stringResource(R.string.limit_type_sum)
    TrafficLimitType.MAX -> stringResource(R.string.limit_type_max)
    TrafficLimitType.MIN -> stringResource(R.string.limit_type_min)
    TrafficLimitType.UP -> stringResource(R.string.limit_type_up)
    TrafficLimitType.DOWN -> stringResource(R.string.limit_type_down)
}

@Composable
@ReadOnlyComposable
fun countingModeText(mode: CountingMode): String = when (mode) {
    CountingMode.KOMARI_CUMULATIVE -> stringResource(R.string.counting_mode_komari)
    CountingMode.APP_ACCUMULATED -> stringResource(R.string.counting_mode_app)
}

@Composable
@ReadOnlyComposable
fun countingModeDescription(mode: CountingMode): String = when (mode) {
    CountingMode.KOMARI_CUMULATIVE -> stringResource(R.string.counting_mode_komari_desc)
    CountingMode.APP_ACCUMULATED -> stringResource(R.string.counting_mode_app_desc)
}

/** Date in "M月d日" within [today]'s year, else "yyyy年M月d日". */
@Composable
@ReadOnlyComposable
fun dateText(date: LocalDate, today: LocalDate): String = Formatters.formatDate(
    date = date,
    today = today,
    sameYearPattern = stringResource(R.string.date_pattern_same_year),
    fullPattern = stringResource(R.string.date_pattern_full),
)

/** Always "yyyy年M月d日". */
@Composable
@ReadOnlyComposable
fun fullDateText(date: LocalDate): String {
    val pattern = stringResource(R.string.date_pattern_full)
    return Formatters.formatDate(date, date, pattern, pattern)
}

/** "刚刚" / "5 分钟前" / "3 小时前" / "昨天 21:05" / "9月3日 21:05". */
@Composable
@ReadOnlyComposable
fun relativeTimeText(time: RelativeTime): String = when (time) {
    RelativeTime.JustNow -> stringResource(R.string.time_just_now)
    is RelativeTime.MinutesAgo -> stringResource(R.string.time_minutes_ago, time.minutes.toInt())
    is RelativeTime.HoursAgo -> stringResource(R.string.time_hours_ago, time.hours.toInt())
    is RelativeTime.Yesterday -> stringResource(
        R.string.time_yesterday,
        DateTimeFormatter.ofPattern(stringResource(R.string.time_pattern_clock), Locale.CHINA).format(time.time),
    )
    is RelativeTime.At -> DateTimeFormatter.ofPattern(
        stringResource(if (time.sameYear) R.string.time_pattern_same_year else R.string.time_pattern_full),
        Locale.CHINA,
    ).format(time.time)
}

/** "30 天前" / "当天" for one reminder day. */
@Composable
@ReadOnlyComposable
fun reminderDayLabel(day: Int): String =
    if (day == 0) stringResource(R.string.reminder_day_today) else stringResource(R.string.reminder_day_before, day)

/** "到期前 7、3、1 天和当天" and friends for a reminder day list. */
@Composable
@ReadOnlyComposable
fun reminderDaysText(days: List<Int>): String {
    val before = days.filter { it > 0 }.sortedDescending()
    val includesToday = 0 in days
    return when {
        before.isEmpty() && includesToday -> stringResource(R.string.settings_reminder_only_today)
        before.isEmpty() -> stringResource(R.string.settings_reminder_none)
        includesToday -> stringResource(R.string.settings_reminder_days_with_today, before.joinToString("、"))
        else -> stringResource(R.string.settings_reminder_days_value, before.joinToString("、"))
    }
}

/** "每 30 分钟" / "每小时" / "关闭" for a refresh interval in minutes. */
@Composable
@ReadOnlyComposable
fun refreshIntervalText(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.settings_refresh_off)
    minutes < 60 -> stringResource(R.string.settings_refresh_minutes, minutes)
    minutes == 60 -> stringResource(R.string.settings_refresh_hour)
    else -> stringResource(R.string.settings_refresh_hours, minutes / 60)
}
