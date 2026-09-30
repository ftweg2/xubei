package com.ftwegc.myapplication.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Every motion parameter of the app. Other files reference these values instead of writing
 * durations, easings or spring constants inline.
 *
 * When the system animator duration scale is 0 ([LocalReducedMotion]), use [reduced] / the
 * `*Spec` helpers so animations jump straight to their end state.
 */
object Motion {

    // ---------------------------------------------------------------- Durations (ms)

    /** Small state changes: colors, icons, chips. */
    const val DurationShort: Int = 150

    /** Page transitions, content cross-fades, expand / collapse. */
    const val DurationMedium: Int = 300

    /** Shared element (list card to detail header) bounds morph. */
    const val DurationSharedElement: Int = 420

    /** Rolling count of amounts and traffic values. */
    const val DurationCount: Int = 600

    /** One list item's fade-and-rise entrance. */
    const val DurationItemEnter: Int = 360

    /** One sweep of the skeleton shimmer. */
    const val DurationShimmer: Int = 1_200

    /** Pause between shimmer sweeps. */
    const val DurationShimmerPause: Int = 250

    /** Success check: circle draw, then check stroke. */
    const val DurationSuccessCircle: Int = 260
    const val DurationSuccessCheck: Int = 220

    /** Selected tab icon: time to reach the bounce peak before springing back. */
    const val DurationIconBounceUp: Int = 110

    /** Empty-state illustration float cycle. */
    const val DurationIllustrationFloat: Int = 2_800

    // ---------------------------------------------------------------- Stagger

    /** Delay between consecutive list items on first entrance. */
    const val StaggerStepMillis: Long = 40

    /** Items after this index enter together with the last staggered one. */
    const val StaggerMaxItems: Int = 8

    /** Rise distance of an entering list item. */
    val StaggerOffset = 18.dp

    // ---------------------------------------------------------------- Distances and scales

    /** Forward page transition: new page starts this fraction of the width to the right. */
    const val PageSlideFraction: Float = 0.1f

    /** Bottom tab switch: entering tab starts at this scale. */
    const val TabEnterScale: Float = 0.97f

    /** Bottom tab switch: leaving tab ends at this scale. */
    const val TabExitScale: Float = 1.01f

    /** Pressed card scale. */
    const val PressedScale: Float = 0.98f

    /** Peak scale of the selected bottom-bar icon bounce. */
    const val NavIconBounceScale: Float = 1.18f

    /** Empty-state illustration float amplitude. */
    val IllustrationFloat = 6.dp

    // ---------------------------------------------------------------- Easing (Material 3)

    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // ---------------------------------------------------------------- Springs

    /** Progress ring / bar growth: settles with a slight overshoot. */
    const val ProgressDamping: Float = 0.62f
    const val ProgressStiffness: Float = 90f

    /** Press feedback: quick and a little bouncy on release. */
    const val PressDamping: Float = 0.5f
    const val PressStiffness: Float = Spring.StiffnessMedium

    /** Bottom-bar indicator slide. */
    const val IndicatorDamping: Float = 0.78f
    const val IndicatorStiffness: Float = 420f

    /** Selected bottom-bar icon bounce. */
    const val IconBounceDamping: Float = 0.35f
    const val IconBounceStiffness: Float = 700f

    /** Success check pop. */
    const val SuccessDamping: Float = 0.45f
    const val SuccessStiffness: Float = 500f

    /** List item placement changes (animateItem). */
    const val PlacementDamping: Float = Spring.DampingRatioNoBouncy
    const val PlacementStiffness: Float = Spring.StiffnessMediumLow

    // ---------------------------------------------------------------- Ready-made specs

    fun <T> emphasized(durationMillis: Int = DurationMedium, delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis, delayMillis, Emphasized)

    fun <T> emphasizedDecelerate(durationMillis: Int = DurationMedium, delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis, delayMillis, EmphasizedDecelerate)

    fun <T> emphasizedAccelerate(durationMillis: Int = DurationShort, delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis, delayMillis, EmphasizedAccelerate)

    fun <T> standard(durationMillis: Int = DurationShort, delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis, delayMillis, Standard)

    fun <T> progressSpring(): SpringSpec<T> = spring(ProgressDamping, ProgressStiffness)
    fun <T> pressSpring(): SpringSpec<T> = spring(PressDamping, PressStiffness)
    fun <T> indicatorSpring(): SpringSpec<T> = spring(IndicatorDamping, IndicatorStiffness)
    fun <T> iconBounceSpring(): SpringSpec<T> = spring(IconBounceDamping, IconBounceStiffness)
    fun <T> successSpring(): SpringSpec<T> = spring(SuccessDamping, SuccessStiffness)
    fun <T> placementSpring(): SpringSpec<T> = spring(PlacementDamping, PlacementStiffness)

    /** Count-up of numbers. */
    fun <T> count(): TweenSpec<T> = tween(DurationCount, easing = EmphasizedDecelerate)

    // ================================================================ Liquid glass (v2)
    // Values from docs/mockups/vluer-screens.html unless noted.

    // ---------------------------------------------------------------- Splash

    /** The splash fades into the overview while the mark grows a little. */
    const val DurationSplashExit: Int = 320
    const val SplashExitIconScale: Float = 1.12f

    /**
     * The splash of the 续杯 mark (ms from the first frame): the ring draws itself clockwise, the
     * liquid rises from the bottom of the cup, sloshes [SplashSloshCycles] times and rests, then
     * the splash fades into the overview.
     */
    const val SplashRingMillis: Int = 650
    const val SplashRiseDelayMillis: Int = 150
    const val SplashRiseMillis: Int = 700
    const val SplashSloshDelayMillis: Int = 600
    const val SplashSloshMillis: Int = 1_000
    const val SplashExitDelayMillis: Int = 1_480
    const val SplashSloshTilt: Float = 0.16f
    const val SplashSloshCycles: Float = 2.5f

    /** How far (SVG units) the waves drift sideways while the liquid sloshes. */
    const val SplashSloshDrift: Float = 10f

    // ---------------------------------------------------------------- The cup of the mark

    /** The liquid rising (refreshing) or falling back (refresh done) in the overview header. */
    const val DurationCupLevel: Int = 600

    /** A gentle slosh: two tilts that die out. */
    const val DurationCupSlosh: Int = 1_100
    const val CupSloshTilt: Float = 0.09f
    const val CupSloshCycles: Float = 2f
    const val CupSloshDrift: Float = 6f

    /** A successful connection test: the cup fills and the ring closes. */
    const val DurationCupFill: Int = 900
    const val DurationRingClose: Int = 520

    // ---------------------------------------------------------------- Background

    /** The background drift is redrawn this far apart (a few px/s of movement: smooth to the eye). */
    const val AuroraFrameIntervalMillis: Long = 50

    /** Longest step the drift clock takes after a pause, so it never jumps. */
    const val AuroraMaxStepMillis: Long = 100

    /** The drift stands still this long after the last scroll step. */
    const val AuroraScrollHoldMillis: Long = 400

    /** Cross-fade when the page or the mood changes the background colors. */
    const val DurationAuroraFade: Int = 700

    // ---------------------------------------------------------------- Glass press

    /** `.glass:active { transform: scale(.975); filter: brightness(1.12) }`. */
    const val GlassPressedScale: Float = 0.975f
    const val GlassPressBrighten: Float = 0.06f

    /** `transition: transform .25s cubic-bezier(.2,1.4,.4,1)`: overshoots, so release springs back. */
    const val DurationGlassPress: Int = 250
    val GlassPressEasing: Easing = CubicBezierEasing(0.2f, 1.4f, 0.4f, 1f)

    /** A tap shorter than this share of the press animation is first pressed fully. */
    const val GlassMinVisiblePress: Float = 0.6f
    const val DurationGlassTap: Int = 90

    /** "Sinks in": inner shadow of a pressed glass. */
    val GlassPressInnerShadow = 10.dp
    const val GlassPressInnerShadowAlpha: Float = 0.55f

    fun <T> glassPress(): TweenSpec<T> = tween(DurationGlassPress, easing = GlassPressEasing)

    // ---------------------------------------------------------------- Bento entrance

    /** `.rise`: opacity 0, translateY(16px) scale(.97) → none, .7s cubic-bezier(.2,1.2,.4,1). */
    const val DurationRise: Int = 700
    val RiseEasing: Easing = CubicBezierEasing(0.2f, 1.2f, 0.4f, 1f)
    val RiseOffset = 16.dp
    const val RiseScale: Float = 0.97f

    /** Delays of consecutive blocks (mockup: 0, .08, .14, .2, .26, .32, .38 s). */
    val RiseDelaysMillis: List<Int> = listOf(0, 80, 140, 200, 260, 320, 380)

    fun riseDelay(index: Int): Int =
        RiseDelaysMillis.getOrElse(index) { RiseDelaysMillis.last() + (index - RiseDelaysMillis.lastIndex) * RiseStepMillis }

    private const val RiseStepMillis = 60

    // ---------------------------------------------------------------- Charts

    /** Rings: `.arc` 1.4s .3s cubic-bezier(.3,.8,.2,1). */
    const val DurationChartArc: Int = 1_400
    const val DelayChartArc: Int = 300
    val ChartArcEasing: Easing = CubicBezierEasing(0.3f, 0.8f, 0.2f, 1f)

    /** Lines: `.draw` 1.6s .25s cubic-bezier(.3,.7,.2,1). */
    const val DurationChartDraw: Int = 1_600
    const val DelayChartDraw: Int = 250
    val ChartDrawEasing: Easing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f)

    /** Fills, forecast and end point: `.fade` .9s .9s ease. */
    const val DurationChartFade: Int = 900
    const val DelayChartFade: Int = 900
    val ChartFadeEasing: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** Bars: `grow` .7s cubic-bezier(.2,1.3,.4,1), delays .3 s + i × .05 s. */
    const val DurationBarGrow: Int = 700
    const val DelayBarGrow: Int = 300
    const val StaggerBarMillis: Int = 50
    val BarGrowEasing: Easing = CubicBezierEasing(0.2f, 1.3f, 0.4f, 1f)

    /** Horizontal bars: `gx` .9s .4s cubic-bezier(.2,1,.3,1), then +.1 s per row. */
    const val DurationHBarGrow: Int = 900
    const val DelayHBarGrow: Int = 400
    const val StaggerHBarMillis: Int = 100
    val HBarGrowEasing: Easing = CubicBezierEasing(0.2f, 1f, 0.3f, 1f)

    /** Value change after the entrance (new data): shorter, no delay. */
    const val DurationChartUpdate: Int = 600

    /** Online dot: `breathe` 2.4s ease-out, a 9 px ring fading out. */
    const val DurationBreathe: Int = 2_400
    val BreatheRing = 9.dp

    /** Liquid ring waves: 3.2 s and 5 s (reverse) per wave length. */
    const val DurationWaveFront: Int = 3_200
    const val DurationWaveBack: Int = 5_000

    /** Entry slosh of the liquid: starts this high (fraction of the ring) and settles to flat. */
    const val LiquidSloshAmplitude: Float = 0.06f

    /** Level rise of the liquid (`transition: height 1.4s`). */
    const val DurationLiquidRise: Int = 1_400

    // ---------------------------------------------------------------- Navigation

    /** The drop: `transition: transform .55s cubic-bezier(.3,1.5,.4,1), width .3s`. */
    const val DurationDropMove: Int = 550
    val DropMoveEasing: Easing = CubicBezierEasing(0.3f, 1.5f, 0.4f, 1f)

    /** The drop is `22px` wider for `220ms` while it travels. */
    val DropStretch = 22.dp
    const val DurationDropStretch: Int = 220
    const val DurationDropWidth: Int = 300

    /** Dock shrinking while scrolling down, and back. */
    const val DurationDockCollapse: Int = 320

    /** Scrolled distance before the dock collapses or expands again. */
    val DockCollapseThreshold = 24.dp

    /** Dock sliding away on pages below the tabs. */
    const val DurationDockHide: Int = 360

    // ---------------------------------------------------------------- Round 2: sheets, lists, detail

    /** A sheet grows out of the button that opened it, and closes a little faster. */
    const val DurationSheetOpen: Int = 520
    const val DurationSheetClose: Int = 280
    val SheetEasing: Easing = CubicBezierEasing(0.2f, 1f, 0.3f, 1f)

    /** A long-press menu opens from the pressed tile (mockup glass curve, a little overshoot). */
    const val DurationMenu: Int = 260
    val MenuEasing: Easing = CubicBezierEasing(0.2f, 1.25f, 0.4f, 1f)
    const val MenuStartScale: Float = 0.86f

    /** Tiles turning into rows and back (and moving to new places when sorted / filtered). */
    const val DurationMorph: Int = 460
    val MorphEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** A removed tile fades and shrinks a little. */
    const val DurationItemExit: Int = 220
    const val ItemExitScale: Float = 0.92f

    /** The search field opens out of the search button. */
    const val DurationSearch: Int = 340

    /** The detail header shrinks to this scale before it docks into the top bar. */
    const val HeaderDockScale: Float = 0.55f

    /** Knob of the glass switch travels this far (mockup-sized track 52 × 30). */
    val SwitchTravel = 20.dp

    // ---------------------------------------------------------------- Round 3: dialogs, rings, stats

    /** A glass dialog grows out of the middle with a small overshoot, and closes faster. */
    const val DurationDialogOpen: Int = 380
    const val DurationDialogClose: Int = 200
    val DialogEasing: Easing = CubicBezierEasing(0.2f, 1.25f, 0.4f, 1f)
    const val DialogStartScale: Float = 0.9f

    /** The ring spinner: one turn, and its gap breathing. */
    const val DurationSpinnerTurn: Int = 1_100
    const val DurationSpinnerBreathe: Int = 900

    /** The liquid in the traffic ring sloshes this long on entry (and on a tap), then rests. */
    const val DurationLiquidSlosh: Int = 3_000

    /** Month switch of the calendars (the picker and the payment calendar). */
    const val DurationMonthSwitch: Int = 320

    /** A toast slides this far up while it appears. */
    val ToastRise = 18.dp

    /** [spec], or an immediate snap when [reducedMotion] is true. */
    fun <T> reduced(reducedMotion: Boolean, spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
        if (reducedMotion) snap() else spec
}

/** [spec], or [snap] when the system has animations turned off. */
@Composable
@ReadOnlyComposable
fun <T> motionSpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    Motion.reduced(LocalReducedMotion.current, spec)
