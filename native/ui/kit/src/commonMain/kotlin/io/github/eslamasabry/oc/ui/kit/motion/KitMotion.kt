package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.eslamasabry.oc.ui.design.motion.CurveId
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion

/** What the kit may move right now (plan 08 §2.7). Built once by `KitTheme`; read only by kit parts. */
@Immutable
public data class KitMotionState(
    val level: MotionLevel,
    /** Tests, screenshots and the gallery: loops draw one fixed frame. */
    val stillFrame: Boolean = false,
    /** Gallery and debug builds only: slows every kit animation for review (1 = normal). */
    val debugTimeScale: Float = 1f,
) {
    val reduced: Boolean get() = level == MotionLevel.Off
    val travels: Boolean get() = level == MotionLevel.Full
    val ambientLoops: Boolean get() = level == MotionLevel.Full && !stillFrame
}

/**
 * Flutter's curves from their own control points (plan 06 §4.8). The androidx `EaseOutCubic`/`EaseInCubic` are
 * different curves and are not used.
 */
public object KitEasing {
    private fun of(c: CurveId): Easing = CubicBezierEasing(c.x1, c.y1, c.x2, c.y2)
    public val Enter: Easing = of(CurveId.Enter)
    public val Exit: Easing = of(CurveId.Exit)
    public val Pulse: Easing = of(CurveId.Pulse)
    public val PredictiveBack: Easing = of(CurveId.PredictiveBack)
    public val Steady: Easing = LinearEasing
}

/**
 * The only place the kit creates tweens and springs (plan 08 §2.7). Off: the next frame is final (MOT-7).
 *
 * The native motion scheme (motion lead 2026-11-07), shaped like M3's `MotionScheme`:
 * - **Spatial** (position, size, bounds, list placement): critically-near springs, ζ 0.9, no visible overshoot,
 *   [spatialFast] 1400 / [spatial] 700 / [spatialSlow] 300. Springs keep their velocity when a new target arrives
 *   mid-flight, so an interrupted part turns around smoothly instead of restarting a curve.
 * - **Effects** (alpha, colour): ζ 1 springs, [effectsFast] 3800 / [effects] 1600 / [effectsSlow] 800.
 * - **Release** ([release]): a part a finger let go of may overshoot slightly (ζ 0.82); nothing else does.
 * - The timed fades ([enter], [quick], [pace]) stay tweens on `Enter`: cross-fades read the same either way and their
 *   lengths are part of the kit's contracts.
 */
public class KitMotionSpecs internal constructor(private val s: KitMotionState) {
    private fun ms(v: Int) = (v * s.debugTimeScale).toInt()
    private fun k(stiffness: Float) = stiffness / (s.debugTimeScale * s.debugTimeScale)
    private fun <T> springOf(zeta: Float, stiffness: Float): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else spring(zeta, k(stiffness))

    public fun <T> enter(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(MotionTokens.STANDARD), easing = KitEasing.Enter)

    public fun <T> quick(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(MotionTokens.QUICK), easing = KitEasing.Enter)

    /** The Style surface transition (Flutter kitSurfaceBox, state 120 ms). */
    public fun <T> surface(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(120), easing = KitEasing.Enter)

    /** A part leaving uses QUICK on `Enter` (Flutter's exit curve is a reverse-slot curve). */
    public fun <T> exit(): FiniteAnimationSpec<T> = quick()

    /** Small spatial moves: chevron turn, tab indicator, slot widths, a count rolling. */
    public fun <T> spatialFast(): FiniteAnimationSpec<T> = springOf(MotionTokens.SPATIAL_ZETA, MotionTokens.SPATIAL_FAST_K)

    /** The default spatial move: a part unfolding, a row finding its new place, a bubble rising. */
    public fun <T> spatial(): FiniteAnimationSpec<T> = springOf(MotionTokens.SPATIAL_ZETA, MotionTokens.SPATIAL_K)

    /** Large spatial moves: a page-sized container growing, a sheet travelling its full height. */
    public fun <T> spatialSlow(): FiniteAnimationSpec<T> = springOf(MotionTokens.SPATIAL_ZETA, MotionTokens.SPATIAL_SLOW_K)

    public fun <T> largeTravel(): FiniteAnimationSpec<T> = springOf(1f, MotionTokens.LARGE_TRAVEL_K)

    /** List rows moving to a new place (insert above, removal, reorder). */
    public fun <T> placement(): FiniteAnimationSpec<T> = springOf(MotionTokens.SPATIAL_ZETA, MotionTokens.PLACEMENT_K)

    public fun <T> effectsFast(): FiniteAnimationSpec<T> = springOf(1f, MotionTokens.EFFECTS_FAST_K)

    public fun <T> effects(): FiniteAnimationSpec<T> = springOf(1f, MotionTokens.EFFECTS_K)

    public fun <T> effectsSlow(): FiniteAnimationSpec<T> = springOf(1f, MotionTokens.EFFECTS_SLOW_K)

    /** A part a finger let go of settles with the finger's velocity and may overshoot slightly. */
    public fun <T> release(): FiniteAnimationSpec<T> = springOf(MotionTokens.RELEASE_ZETA, MotionTokens.RELEASE_K)

    /**
     * Joined glass's springs (docs/designs/liquid-merge-one-body.md, "Drop, rebound, fill"; numbers in `LiquidJoin`): a
     * drop that passes its resting line once and rebounds ([zeta] 0.62), a calm fill (0.92), or a critically damped
     * spring ([zeta] 1) for closing and for the reach's swell, at [stiffness].
     */
    public fun <T> liquid(zeta: Float, stiffness: Float): FiniteAnimationSpec<T> = springOf(zeta, stiffness)

    /** The edge light's one halo (LIGHT_PULSE on `Pulse`): it grows and fades once when someone needs the person. */
    public fun <T> lightPulse(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(MotionTokens.LIGHT_PULSE), easing = KitEasing.Pulse)

    /** A finished mark's check drawing itself (CHECK_DRAW on `Enter`). */
    public fun <T> checkDraw(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(MotionTokens.CHECK_DRAW), easing = KitEasing.Enter)

    /**
     * A held answer's countdown: a linear drain over [millis] (`Steady`), the time left read without a number. Under
     * Off it does not drain at all (the caller keeps the bar still); its length still holds.
     */
    public fun <T> countdown(millis: Int): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(millis), easing = KitEasing.Steady)

    /** A part kept on screen, unchanged, for [millis] while its own motion runs (an exit that only waits). */
    public fun <T> hold(millis: Int): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(millis), easing = { if (it < 1f) 0f else 1f })

    /** [millis] at the debug time scale: the wait between two springs of one motion. */
    public fun delayMs(millis: Float): Long = (millis * s.debugTimeScale).toLong()

    public val state: KitMotionState get() = s
}

public object KitMotion {
    public val state: KitMotionState
        @Composable @ReadOnlyComposable get() = LocalKitMotion.current

    public val specs: KitMotionSpecs
        @Composable @ReadOnlyComposable get() = KitMotionSpecs(LocalKitMotion.current)
}
