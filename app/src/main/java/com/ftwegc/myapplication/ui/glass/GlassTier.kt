package com.ftwegc.myapplication.ui.glass

import android.os.Build

/**
 * How much of the liquid glass the device (or the user's settings) allows, from full to plain.
 *
 * | Tier | Where | Look |
 * |---|---|---|
 * | [Refractive] | Android 13+ | blur, refraction at the edges of floating glass, highlight |
 * | [Blurred] | Android 12 | blur and highlight, no refraction |
 * | [Tinted] | Android 11 and lower | a translucent tinted panel with a highlight edge, no blur |
 * | [Solid] | "降低透明效果" on | nearly opaque panel, no blur, highlight edge kept |
 *
 * The glass library does not degrade by itself (on Android 11 it simply draws the background
 * unblurred), so every glass reads the tier and draws accordingly.
 */
enum class GlassTier {
    Refractive,
    Blurred,
    Tinted,
    Solid;

    /** Samples and blurs what is behind it. */
    val blurs: Boolean get() = this == Refractive || this == Blurred

    /** Bends the backdrop at the edges (floating glass only). */
    val refracts: Boolean get() = this == Refractive

    /** Stable key for the debug setting. */
    val key: String get() = name.lowercase()

    companion object {
        /** The best tier the platform supports. */
        fun native(sdkInt: Int = Build.VERSION.SDK_INT): GlassTier = when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> Refractive
            sdkInt >= Build.VERSION_CODES.S -> Blurred
            else -> Tinted
        }

        /**
         * The tier to draw with: "reduce transparency" wins; a debug [override] can only force a
         * lower tier than the platform supports (to preview the fallbacks on a new device).
         */
        fun resolve(sdkInt: Int, override: GlassTier?, reduceTransparency: Boolean): GlassTier {
            if (reduceTransparency) return Solid
            val platform = native(sdkInt)
            return if (override != null && override.ordinal > platform.ordinal) override else platform
        }

        /** Parses a [key]; `null` for null / unknown text (no override). */
        fun fromKey(key: String?): GlassTier? = entries.firstOrNull { it.key == key?.trim()?.lowercase() }
    }
}
