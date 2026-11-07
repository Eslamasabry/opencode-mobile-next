package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEvent
import io.github.eslamasabry.oc.ui.design.generated.FlutterCurve
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitCurves
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import kotlin.math.roundToInt

/**
 * Route transitions per [RouteMotion] (plan 07 §3.1, plan 08 §5.4/§5.9) and the Material predictive-back preview
 * (M8-9). Pure functions of (motion, level, pixels) so they are unit-tested without a window.
 *
 * Pushed pages copy the Flutter app's Android transition (M-012): `FadeForwardsPageTransitionsBuilder` of the pinned
 * Flutter SDK (packages/flutter/lib/src/material/page_transitions_theme.dart): 450 ms; the arriving page slides a
 * quarter of the width on `easeInOutCubicEmphasized` and fades in over the first 75 % (linear); the page it covers
 * slides a quarter of the width the other way and fades out over the first 25 %.
 *
 * Levels (M-013): Full moves; Calm only fades (the same opacities, no travel); Off is instant.
 */
public object ShellMotion {
    /** Metadata key the shell adds to every NavEntry so specs can read the route's motion class. */
    public const val MOTION_KEY: String = "oc.shell.routeMotion"

    public const val QUICK: Int = MotionTokens.QUICK
    public const val STANDARD: Int = MotionTokens.STANDARD

    /** One span for the predictive preview, as Flutter's (MotionTokens.PREDICTIVE_SPAN, 300 ms). */
    public const val PREDICTIVE_SPAN: Int = MotionTokens.PREDICTIVE_SPAN
    public const val PREDICTIVE_MIN_SCALE: Float = MotionTokens.PREDICTIVE_MIN_SCALE
    public const val PREDICTIVE_FADE_AT: Float = MotionTokens.PREDICTIVE_FADE_AT
    public const val PREVIEW_CORNER_DP: Int = MotionTokens.PREVIEW_CORNER_DP

    /** `FadeForwardsPageTransitionsBuilder.kTransitionMilliseconds`. */
    public const val PAGE_MS: Int = 450

    /** Both pages travel a quarter of the width (`Offset(0.25, 0)` / `Offset(-0.25, 0)`). */
    public const val PAGE_TRAVEL_SHARE: Float = 0.25f

    /** `_fadeInTransition`: `Interval(0.0, 0.75)`, linear inside. */
    public const val PAGE_FADE_IN_SHARE: Float = 0.75f

    /** `_fadeOutTransition`: `Interval(0.0, 0.25)`, linear inside. */
    public const val PAGE_FADE_OUT_SHARE: Float = 0.25f

    public val PAGE_FADE_IN_MS: Int = (PAGE_MS * PAGE_FADE_IN_SHARE).roundToInt()
    public val PAGE_FADE_OUT_MS: Int = (PAGE_MS * PAGE_FADE_OUT_SHARE).roundToInt()

    /** Flutter `Curves.easeInOutCubicEmphasized` (`_transitionCurve` of fade-forwards), from its own points. */
    public val Emphasized: Easing = flutterEasing(FlutterKitCurves.emphasized)

    public val PredictiveBack: Easing = KitEasing.PredictiveBack

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

    /** A quarter of the container's width, as fade-forwards travels. */
    public fun pageTravelPx(fullWidthPx: Int): Int = (fullWidthPx * PAGE_TRAVEL_SHARE).roundToInt()

    public fun <S> AnimatedContentTransitionScope<S>.forward(
        motion: RouteMotion?,
        level: ShellMotionLevel,
    ): ContentTransform {
        if (level == ShellMotionLevel.Off) return none
        val travels = level == ShellMotionLevel.Full
        return when (motion) {
            // Full: the page grows out of the row through the kit's shared bounds; the list holds still underneath.
            // Calm: no growing, the page fades in over the list.
            RouteMotion.GrowFromRow ->
                if (travels) EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
                else fadeIn(tween(STANDARD, easing = KitEasing.Enter)) togetherWith
                    ExitTransition.KeepUntilTransitionsFinished
            RouteMotion.SlideUp ->
                (if (travels) slideInVertically(tween(STANDARD, easing = KitEasing.Enter)) { it }
                else fadeIn(tween(STANDARD, easing = KitEasing.Enter))) togetherWith
                    ExitTransition.KeepUntilTransitionsFinished
            RouteMotion.Root, null -> rootFade()
            RouteMotion.SharedAxisX -> fadeForwards(towardStart = true, travels = travels)
        }
    }

    /** Button-back (and 3-button tap) pop; [motion] is the popped route's. */
    public fun <S> AnimatedContentTransitionScope<S>.pop(
        motion: RouteMotion?,
        level: ShellMotionLevel,
    ): ContentTransform {
        if (level == ShellMotionLevel.Off) return none
        val travels = level == ShellMotionLevel.Full
        return when (motion) {
            // Full: shrinks back into its row (kit shared bounds, 200 ms). With no row to land in, the page's own
            // shared-bounds exit fades it out (the P2-5 fallback). Calm: the page fades out over the list.
            RouteMotion.GrowFromRow ->
                if (travels) EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
                else EnterTransition.None togetherWith fadeOut(tween(QUICK, easing = KitEasing.Enter))
            RouteMotion.SlideUp ->
                EnterTransition.None togetherWith
                    (if (travels) slideOutVertically(tween(STANDARD, easing = KitEasing.Enter)) { it }
                    else fadeOut(tween(QUICK, easing = KitEasing.Enter)))
            RouteMotion.Root, null -> rootFade()
            RouteMotion.SharedAxisX -> fadeForwards(towardStart = false, travels = travels)
        }
    }

    /** Root switches cross-fade at every level but Off: in on Enter over STANDARD, out over QUICK (Exit spec). */
    private fun rootFade(): ContentTransform =
        fadeIn(tween(STANDARD, easing = KitEasing.Enter)) togetherWith fadeOut(tween(QUICK, easing = KitEasing.Enter))

    /**
     * Flutter's fade-forwards. Push ([towardStart]): the new page comes in from the end side, the covered page leaves
     * toward the start side. Pop: the mirror. Start/End follow the layout direction, so Arabic mirrors.
     */
    private fun <S> AnimatedContentTransitionScope<S>.fadeForwards(
        towardStart: Boolean,
        travels: Boolean,
    ): ContentTransform {
        val direction = if (towardStart) SLIDE_START else SLIDE_END
        val fadeIn = fadeIn(tween(PAGE_FADE_IN_MS, easing = LinearEasing))
        val fadeOut = fadeOut(tween(PAGE_FADE_OUT_MS, easing = LinearEasing))
        if (!travels) return fadeIn togetherWith fadeOut
        val slide = tween<IntOffset>(PAGE_MS, easing = Emphasized)
        return (slideIntoContainer(direction, slide) { pageTravelPx(it) } + fadeIn) togetherWith
            (slideOutOfContainer(direction, slide) { pageTravelPx(it) } + fadeOut)
    }

    /**
     * The Material full-screen preview for every route class but a page that grew from a row at Full (plan 08 M8-9):
     * the page scales to 0.9, shifts away
     * from the swiped edge and fades through from 35 % progress; the page underneath holds still. Calm: the fade
     * only. Off: no preview (Back commits on release).
     */
    public fun predictivePop(
        edge: Int,
        level: ShellMotionLevel,
        edgeShiftPx: Int,
        motion: RouteMotion? = null,
        /** The page's exit while its shared bounds fly: NavDisplay passes its scope's `KeepUntilTransitionsFinished`. */
        keep: ExitTransition = ExitTransition.None,
    ): ContentTransform =
        when {
            // A page that grew from a row shrinks back into it under the finger: the shared bounds are the preview, the
            // page itself stays until the transform ends (motion lead 2026-11-07).
            level == ShellMotionLevel.Full && motion == RouteMotion.GrowFromRow ->
                EnterTransition.None togetherWith keep
            else -> predictivePreview(edge, level, edgeShiftPx)
        }

    private fun predictivePreview(edge: Int, level: ShellMotionLevel, edgeShiftPx: Int): ContentTransform =
        when (level) {
            ShellMotionLevel.Off -> none
            ShellMotionLevel.Calm -> EnterTransition.None togetherWith previewFade()
            ShellMotionLevel.Full -> {
                val span = PREDICTIVE_SPAN
                val sign = shiftSign(edge)
                EnterTransition.None togetherWith
                    (scaleOut(tween(span, easing = PredictiveBack), targetScale = PREDICTIVE_MIN_SCALE) +
                        slideOutHorizontally(tween(span, easing = PredictiveBack)) { sign * edgeShiftPx } +
                        previewFade())
            }
        }

    /** Opaque until 35 % of the gesture, then fades through to 0 at the end. */
    private fun previewFade(): ExitTransition =
        fadeOut(
            keyframes {
                durationMillis = PREDICTIVE_SPAN
                1f at 0
                1f at (PREDICTIVE_SPAN * PREDICTIVE_FADE_AT).roundToInt()
                0f at PREDICTIVE_SPAN
            },
        )

    private val SLIDE_START = AnimatedContentTransitionScope.SlideDirection.Start
    private val SLIDE_END = AnimatedContentTransitionScope.SlideDirection.End
}

/**
 * A Flutter curve as Compose easing, from the SDK's own definition (curves.dart): a cubic is the same Bézier; a
 * `ThreePointCubic` is two cubics joined at its midpoint, each scaled into its half
 * (`ThreePointCubic.transformInternal`).
 */
internal fun flutterEasing(curve: FlutterCurve): Easing =
    when (curve) {
        is FlutterCurve.Cubic -> CubicBezierEasing(curve.x1, curve.y1, curve.x2, curve.y2)
        FlutterCurve.Linear -> LinearEasing
        is FlutterCurve.Flipped -> {
            val inner = flutterEasing(curve.curve)
            Easing { t -> 1f - inner.transform(1f - t) }
        }
        is FlutterCurve.ThreePointCubic -> {
            val mx = curve.midX
            val my = curve.midY
            val first = CubicBezierEasing(curve.a1x / mx, curve.a1y / my, curve.b1x / mx, curve.b1y / my)
            val sx = 1f - mx
            val sy = 1f - my
            val second =
                CubicBezierEasing(
                    (curve.a2x - mx) / sx,
                    (curve.a2y - my) / sy,
                    (curve.b2x - mx) / sx,
                    (curve.b2y - my) / sy,
                )
            Easing { t ->
                when {
                    t <= 0f -> 0f
                    t >= 1f -> 1f
                    t < mx -> first.transform(t / mx) * my
                    else -> second.transform((t - mx) / sx) * sy + my
                }
            }
        }
    }
