package com.ftwegc.myapplication.dev

import android.app.Activity
import android.content.Context
import java.time.Clock

/**
 * The release build's twin of the development hooks (src/devtools): nothing is hooked, the app
 * runs on the system clock and never reads development extras.
 */
object DevHooks {
    const val settle: Boolean = false
    val auroraFreezeMs: Long? = null
    const val legacyBackground: Boolean = false

    fun clock(context: Context): Clock = Clock.systemDefaultZone()

    @Suppress("UNUSED_PARAMETER")
    fun onActivityCreate(activity: Activity) = Unit
}
