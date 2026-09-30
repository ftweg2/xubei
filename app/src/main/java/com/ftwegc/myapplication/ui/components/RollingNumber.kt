package com.ftwegc.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.VluerTheme
import kotlin.math.roundToLong

/**
 * Values last shown per memory key, for the whole process. A number that comes back on screen
 * (returning to a tab, re-entering a page) starts from what it showed before, so it only counts
 * when the value really changed instead of replaying the count-up every time.
 */
object CountMemory {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun get(key: String): Long? = values[key]

    fun put(key: String, value: Long) {
        values[key] = value
    }

    /** For tests and previews. */
    fun clear() = values.clear()
}

/**
 * A Long that counts from its previous value to [target] over [Motion.DurationCount].
 *
 * The interpolation runs on a 0..1 progress in double precision, so large byte counts and
 * amounts stay exact at the end. A new target mid-animation continues from the value on screen.
 *
 * @param countFromZero start the very first animation at 0 (entrance count-up).
 * @param memoryKey when set, the first value comes from [CountMemory] (the last value shown under
 *   this key), so the count-up plays once and later only on a change.
 */
@Composable
fun animateCountAsState(target: Long, countFromZero: Boolean = false, memoryKey: String? = null): State<Long> {
    val reduced = LocalReducedMotion.current
    val shown = remember {
        val remembered = memoryKey?.let(CountMemory::get)
        mutableLongStateOf(
            when {
                reduced -> target
                remembered != null -> remembered
                countFromZero -> 0L
                else -> target
            },
        )
    }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target, reduced) {
        memoryKey?.let { CountMemory.put(it, target) }
        val from = shown.longValue
        if (reduced || from == target) {
            shown.longValue = target
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, Motion.count()) {
            shown.longValue = from + ((target - from).toDouble() * value).roundToLong()
        }
        shown.longValue = target
    }
    return shown
}

/** Text that counts to [value] and renders every intermediate value through [format]. */
@Composable
fun RollingNumber(
    value: Long,
    format: (Long) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = VluerTheme.numbers.medium,
    color: Color = LocalContentColor.current,
    countFromZero: Boolean = false,
    memoryKey: String? = null,
) {
    val shown by animateCountAsState(value, countFromZero, memoryKey)
    Text(text = format(shown), modifier = modifier, style = style, color = color, maxLines = 1)
}

@PreviewLightDark
@Composable
private fun RollingNumberPreview() {
    com.ftwegc.myapplication.ui.preview.GlassPreview {
        Column {
            RollingNumber(value = 853L shl 30, format = Formatters::formatBytes, style = VluerTheme.numbers.large)
            RollingNumber(value = 12_345, format = { Formatters.formatAmount(it, "CNY") })
        }
    }
}
