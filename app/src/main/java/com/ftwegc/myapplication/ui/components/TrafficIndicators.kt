package com.ftwegc.myapplication.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Below this used fraction the indicator uses the primary color. */
const val TrafficWarnFrom: Float = 0.70f

/** At this fraction the color has fully become amber. */
const val TrafficWarnFull: Float = 0.90f

/** At this fraction the color has fully become coral (a short blend avoids a hard jump). */
const val TrafficDangerFull: Float = 0.92f

/**
 * Indicator color for a used [fraction]: primary below 70 %, blending to amber at 90 %, coral
 * above 90 %. Pure, so the color follows the animated value frame by frame.
 */
fun trafficColor(fraction: Float, primary: Color, warning: Color, danger: Color): Color = when {
    fraction < TrafficWarnFrom -> primary
    fraction < TrafficWarnFull -> lerp(primary, warning, (fraction - TrafficWarnFrom) / (TrafficWarnFull - TrafficWarnFrom))
    fraction < TrafficDangerFull -> lerp(warning, danger, (fraction - TrafficWarnFull) / (TrafficDangerFull - TrafficWarnFull))
    else -> danger
}
