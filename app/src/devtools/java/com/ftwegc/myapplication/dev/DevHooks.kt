package com.ftwegc.myapplication.dev

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ftwegc.myapplication.VluerApp
import com.ftwegc.myapplication.data.prefs.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Properties

/**
 * Development hooks of the debug and perf builds (the release build has a no-op twin in
 * src/release). They make screenshots and measurements comparable between two builds; nothing
 * here is reachable from the app's own screens.
 *
 * Driven by intent extras on the launch intent (adb shell am start ... --es vluer.x value) and
 * kept in a small file of the app's private storage, so they also apply to the next cold start:
 *
 * | extra | value | effect |
 * |---|---|---|
 * | vluer.now | 2026-09-29T12:00 / off | "now" of the app's clock (dates, due days) |
 * | vluer.freeze | milliseconds / off | the background drift stands still at that time |
 * | vluer.settle | true / false | every animation jumps to its end state (like animations off) |
 * | vluer.legacyBg | true / false | draws the background the pre-Android-13 way (comparison) |
 * | vluer.theme | dark / light / system | the app's theme setting |
 * | vluer.tier | blurred / tinted / none | forces a lower glass tier (Android 12 / 11 looks) |
 * | vluer.reduce | true / false | "reduce transparency" |
 * | vluer.seed | true | imports the sample subscriptions (once) and points Komari at the local mock panel |
 */
object DevHooks {
    private const val TAG = "DevHooks"
    private const val FILE = "dev-hooks.properties"

    /** Local mock panel (tools/mock-komari), reached through `adb reverse tcp:8765 tcp:8765`. */
    private const val MOCK_PANEL = "http://127.0.0.1:8765"

    private val props = Properties()
    private var file: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appClock = DevClock(ZoneId.systemDefault())

    /** Everything jumps to its end state (screenshots). */
    @Volatile
    var settle: Boolean = false
        private set

    /** The background's drift time while frozen, or `null` (it flows). */
    @Volatile
    var auroraFreezeMs: Long? = null
        private set

    /** Draws the background the way Android 12 and lower do (to compare both paths on one device). */
    @Volatile
    var legacyBackground: Boolean = false
        private set

    /** The app container's clock: the system clock unless `vluer.now` pinned it. */
    fun clock(context: Context): Clock {
        load(context)
        return appClock
    }

    /** Applies the extras of [activity]'s launch intent. Call before `setContent`. */
    fun onActivityCreate(activity: Activity) {
        load(activity)
        val extras = activity.intent?.extras ?: return
        val keys = extras.keySet().filter { it.startsWith("vluer.") }
        if (keys.isEmpty()) return
        for (key in keys) {
            val value = extras.getString(key) ?: continue
            props.setProperty(key, value)
        }
        save()
        apply()
        val app = activity.application as VluerApp
        val settings = app.container.settingsStore
        runBlocking {
            extras.getString("vluer.theme")?.let { theme ->
                settings.setThemeMode(
                    when (theme) {
                        "light" -> ThemeMode.LIGHT
                        "system" -> ThemeMode.SYSTEM
                        else -> ThemeMode.DARK
                    },
                )
            }
            extras.getString("vluer.tier")?.let { tier -> settings.setGlassTierOverride(tier.takeIf { it != "none" }) }
            extras.getString("vluer.reduce")?.let { settings.setReduceTransparency(it == "true") }
        }
        if (extras.getString("vluer.seed") == "true") seed(app)
    }

    private fun seed(app: VluerApp) {
        scope.launch {
            runCatching {
                val container = app.container
                if (container.database.subscriptionDao().observeAll().first().isEmpty()) {
                    val text = app.assets.open("dev-seed.json").bufferedReader().use { it.readText() }
                    val plan = container.backupRepository.plan(text).getOrThrow()
                    container.backupRepository.import(plan)
                }
                container.settingsStore.setKomariUrl(MOCK_PANEL)
                container.trafficRepository.refreshAll()
            }.onFailure { Log.w(TAG, "Seeding failed", it) }
        }
    }

    private fun load(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        if (f.exists()) runCatching { f.inputStream().use { props.load(it) } }
        apply()
    }

    private fun save() {
        val f = file ?: return
        runCatching { f.outputStream().use { props.store(it, null) } }
    }

    private fun apply() {
        settle = props.getProperty("vluer.settle") == "true"
        legacyBackground = props.getProperty("vluer.legacyBg") == "true"
        auroraFreezeMs = props.getProperty("vluer.freeze")?.toLongOrNull()
        appClock.fixed = props.getProperty("vluer.now")?.let { now ->
            runCatching { LocalDateTime.parse(now).atZone(appClock.zone).toInstant() }.getOrNull()
        }
    }
}

/** The system clock, or a fixed instant while [fixed] is set. */
private class DevClock(private val zoneId: ZoneId) : Clock() {
    @Volatile
    var fixed: Instant? = null

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = DevClock(zone).also { it.fixed = fixed }

    override fun instant(): Instant = fixed ?: Instant.now()
}
