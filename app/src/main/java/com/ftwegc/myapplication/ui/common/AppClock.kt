package com.ftwegc.myapplication.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.ftwegc.myapplication.VluerApp
import java.time.Clock
import java.time.Instant

/**
 * The app's clock (the container's): the same "now" the view models use, so relative times
 * ("3 分钟前") agree with the dates computed from it. The system clock outside the app (previews).
 */
@Composable
@ReadOnlyComposable
fun appClock(): Clock = (LocalContext.current.applicationContext as? VluerApp)?.container?.clock ?: Clock.systemDefaultZone()

/** "Now" on [appClock]. */
@Composable
@ReadOnlyComposable
fun appNow(): Instant = appClock().instant()
