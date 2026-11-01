package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigationevent.NavigationEvent
import io.github.eslamasabry.oc.ui.routes.RouteMotion

/**
 * Route transitions per [RouteMotion] (plan 07 §3.1, plan 08 §5.4/§5.9) and the Material predictive-back preview
 * (M8-9). Pure functions of (motion, level, pixels) so they are unit-tested without a window.
 */
public object ShellMotion {
    /** Metadata key the shell adds to every NavEntry so specs can read the route's motion class. */
    public const val MOTION_KEY: String = "oc.shell.routeMotion"

    public const val QUICK: Int = 150
    public const val STANDARD: Int = 250
    public const val PREDICTIVE_SPAN: Int = 300
    public const val PREDICTIVE_MIN_SCALE: Float = 0.9f
    public const val PREDICTIVE_FADE_AT: Float = 0.35f
    public const val PAGE_TRAVEL_DP: Int = 30
    public const val PREVIEW_CORNER_DP: Int = 28

    public val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    public val PredictiveBack: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

    private val none: ContentTransform
        get() = EnterTransition.None togetherWith ExitTransition.None

    /** +1 shifts right, -1 left, 0 none: away from the swiped edge, never by layout direction (RTL: either edge). */
    public fun shiftSign(edge: Int): Int =
        when (edge) {
            NavigationEvent.EDGE_LEFT -> 1
            NavigationEvent.EDGE_RIGHT -> -1
            else -> 0
        }

    /** `width / 20 − 8 dp`, never negative (Material shared-element preview, motion-native §3.1). */
    public fun edgeShiftPx(widthPx: Int, density: Float): Int = maxOf(0, widthPx / 20 - (8 * density).toInt())

    public fun <S> AnimatedContentTransitionScope<S>.forward(
        motion: RouteMotion?,
        level: ShellMotionLevel,
        travelPx: Int,
    ): ContentTransform {
        if (level == ShellMotionLevel.Off) return none
        return when (motion) {
            // The page grows out of the row through the kit's shared bounds; the list holds still underneath.
            RouteMotion.GrowFromRow -> EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
            RouteMotion.SlideUp ->
                slideInVertically(tween(STANDARD, easing = EaseOutCubic)) { it } togetherWith
                    ExitTransition.KeepUntilTransitionsFinished
            RouteMotion.Root, null -> fadeIn(tween(STANDARD)) togetherWith fadeOut(tween(STANDARD))
            RouteMotion.SharedAxisX ->
                (slideIntoContainer(SLIDE_START, tween(STANDARD, easing = EaseOutCubic)) { travelPx } +
                    fadeIn(tween(STANDARD, easing = EaseOutCubic))) togetherWith
                    (slideOutOfContainer(SLIDE_START, tween(STANDARD, easing = EaseOutCubic)) { travelPx } +
                        fadeOut(tween(QUICK, easing = EaseOutCubic)))
        }
    }

    /** Button-back (and 3-button tap) pop; [motion] is the popped route's. */
    public fun <S> AnimatedContentTransitionScope<S>.pop(
        motion: RouteMotion?,
        level: ShellMotionLevel,
        travelPx: Int,
    ): ContentTransform {
        if (level == ShellMotionLevel.Off) return none
        return when (motion) {
            // Shrinks back into its row (kit shared bounds, 300 ms). With no row to land in, the page's own
            // shared-bounds exit fades it out (the P2-5 fallback).
            RouteMotion.GrowFromRow -> EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
            RouteMotion.SlideUp ->
                EnterTransition.None togetherWith slideOutVertically(tween(STANDARD, easing = EaseOutCubic)) { it }
            RouteMotion.Root, null -> fadeIn(tween(STANDARD)) togetherWith fadeOut(tween(STANDARD))
            RouteMotion.SharedAxisX ->
                (slideIntoContainer(SLIDE_END, tween(STANDARD, easing = EaseOutCubic)) { travelPx } +
                    fadeIn(tween(STANDARD, easing = EaseOutCubic))) togetherWith
                    (slideOutOfContainer(SLIDE_END, tween(STANDARD, easing = EaseOutCubic)) { travelPx } +
                        fadeOut(tween(QUICK, easing = EaseOutCubic)))
        }
    }

    /**
     * The Material full-screen preview for every route class (plan 08 M8-9): the page scales to 0.9, shifts away
     * from the swiped edge and fades through from 35 % progress; the page underneath holds still. Off: no preview
     * (Back commits on release).
     */
    public fun predictivePop(edge: Int, level: ShellMotionLevel, edgeShiftPx: Int): ContentTransform {
        if (level == ShellMotionLevel.Off) return none
        val span = PREDICTIVE_SPAN
        val sign = shiftSign(edge)
        val fadeAt = (span * PREDICTIVE_FADE_AT).toInt()
        return EnterTransition.None togetherWith
            (scaleOut(tween(span, easing = PredictiveBack), targetScale = PREDICTIVE_MIN_SCALE) +
                slideOutHorizontally(tween(span, easing = PredictiveBack)) { sign * edgeShiftPx } +
                fadeOut(
                    keyframes {
                        durationMillis = span
                        1f at 0
                        1f at fadeAt
                        0f at span
                    },
                ))
    }

    private val SLIDE_START = AnimatedContentTransitionScope.SlideDirection.Start
    private val SLIDE_END = AnimatedContentTransitionScope.SlideDirection.End
}
