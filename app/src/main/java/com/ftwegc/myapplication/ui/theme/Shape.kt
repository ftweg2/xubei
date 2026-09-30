package com.ftwegc.myapplication.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Large, soft corners. Cards use [androidx.compose.material3.Shapes.large] (24 dp). */
val VluerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing tokens from the design brief. */
object Spacing {
    /** Horizontal screen margin. */
    val screen = 20.dp

    /** Inner padding of cards. */
    val card = 20.dp

    /** Gap between cards. */
    val cardGap = 12.dp

    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Minimum touch target. */
    val touchTarget = 48.dp
}
