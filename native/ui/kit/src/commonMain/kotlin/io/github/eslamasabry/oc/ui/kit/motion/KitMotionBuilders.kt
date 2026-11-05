package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitRoles
import kotlin.math.roundToInt

/**
 * The two paces a kit part may move at (Dart `KitPace`, MOT-1): QUICK for a control answering a touch and every exit,
 * STANDARD for a part appearing or changing.
 */
public enum class KitPace { Quick, Standard }

/*
 * S2-KITB's builders (plan 08 §2.7) that `KitMotion.kt` does not carry yet. They are extensions over the same
 * `KitMotionSpecs` state so every one snaps under Off and scales with `debugTimeScale`. `KitMotion.kt` is a K0 file
 * this unit may not edit; the kit owner folds these into `KitMotionSpecs` at the contract freeze (report §4 D1), which
 * also makes this the fifth file rule R1 exempts until then.
 */

private fun KitMotionSpecs.ms(v: Int): Int = (v * state.debugTimeScale).roundToInt()

private fun KitMotionSpecs.k(stiffness: Float): Float = stiffness / (state.debugTimeScale * state.debugTimeScale)

private fun <T> KitMotionSpecs.t(duration: Int, delay: Int = 0): FiniteAnimationSpec<T> =
    if (state.reduced) snap() else tween(ms(duration), delayMillis = ms(delay), easing = KitEasing.Enter)

/** [KitPace.Quick] = QUICK, [KitPace.Standard] = STANDARD, both on `Enter`. */
public fun <T> KitMotionSpecs.pace(p: KitPace): FiniteAnimationSpec<T> =
    t(if (p == KitPace.Quick) MotionTokens.QUICK else MotionTokens.STANDARD)

/** `SpatialFast` (ζ 0.9, 1400): chevron turn, tab indicator, slot widths, small drags released. */
public fun <T> KitMotionSpecs.spatialFast(): FiniteAnimationSpec<T> =
    if (state.reduced) snap() else spring(MotionTokens.SPATIAL_ZETA, k(MotionTokens.SPATIAL_FAST_K))

private val split: Int get() = (MotionTokens.STANDARD * FADE_THROUGH_SPLIT).roundToInt()

/** Fade-through, leaving: out over the first third of STANDARD (83 ms). */
public fun <T> KitMotionSpecs.fadeThroughOut(): FiniteAnimationSpec<T> = t(split)

/** Fade-through, arriving: in over the rest of STANDARD, after the leaving part is gone. */
public fun <T> KitMotionSpecs.fadeThroughIn(): FiniteAnimationSpec<T> = t(MotionTokens.STANDARD - split, delay = split)

/** The Settings gear's quarter turn (§5.15), ICON_TURN_MS. */
public fun <T> KitMotionSpecs.iconTurn(): FiniteAnimationSpec<T> = t(ICON_TURN_MS)

/** A tip's own fade, TIP_FADE. */
public fun <T> KitMotionSpecs.tipFade(): FiniteAnimationSpec<T> = t(MotionTokens.TIP_FADE)

/** A movement only Full may make; null = do not move (Calm fades instead, Off jumps). */
public fun <T> KitMotionSpecs.travelOrNull(): FiniteAnimationSpec<T>? = if (state.travels) spatial() else null

/** One linear lap of a loop. Callers create it only when [KitMotionState] allows that loop class (rule R4). */
public fun KitMotionSpecs.lap(lapMillis: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(ms(lapMillis), easing = KitEasing.Steady), RepeatMode.Restart)

/** A breath, one way per [halfMillis], repeated with reverse (refresh portal, waiting outline). */
public fun KitMotionSpecs.breath(halfMillis: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(ms(halfMillis), easing = KitEasing.Pulse), RepeatMode.Reverse)

/** Plan 08 §2.5 `FADE_THROUGH_SPLIT`; MotionTokens (a K0 file) does not carry it yet. */
internal const val FADE_THROUGH_SPLIT: Float = 1f / 3f

/** Plan 08 §2.1 `ICON_TURN_MS`; not yet in MotionTokens (report §6, catalog requests). */
internal const val ICON_TURN_MS: Int = 200

/**
 * A box whose paint changes between surface steps: the fill level and the hairline edge animate on [pace]; a change of
 * size snaps (Dart `KitAnimatedBox`, kit_motion_parts.dart:322). [level] null is no fill. The fill and the edge never
 * take layout space, so turning [outlined] on or off changes paint only (MOT-5).
 *
 * Motion: colour tween [pace] on `Enter`; Calm = same; Off = instant (class Fade)
 */
@Composable
public fun KitAnimatedBox(
    modifier: Modifier = Modifier,
    level: SurfaceLevel? = null,
    shape: KitShapeId = KitShapeId.Panel,
    outlined: Boolean = false,
    pace: KitPace = KitPace.Quick,
    content: @Composable () -> Unit,
) {
    val roles = LocalKitRoles.current
    val fillColor =
        when (level) {
            null -> Color.Transparent
            SurfaceLevel.Ground -> Color(roles.ground.v)
            SurfaceLevel.Surface1 -> Color(roles.surface1.v)
            SurfaceLevel.Surface2 -> Color(roles.surface2.v)
            SurfaceLevel.Surface3 -> Color(roles.surface3.v)
        }
    val hairline = Color(roles.hairline.v)
    val specs = KitMotion.specs
    val fill by animateColorAsState(fillColor, specs.pace(pace), label = "kit.box.fill")
    val edge by animateColorAsState(if (outlined) hairline else hairline.copy(alpha = 0f), specs.pace(pace), label = "kit.box.edge")
    val shown = shape.shape()
    val width: Dp = Dp(1f / LocalDensity.current.density) // F: lib/ui/kit/kit_tokens.dart:317
    androidx.compose.foundation.layout.Box(modifier.background(fill, shown).border(width, edge, shown)) { content() }
}
