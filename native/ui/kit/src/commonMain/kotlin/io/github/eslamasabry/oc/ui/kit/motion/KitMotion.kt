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

/** The only place the kit creates tweens and springs (plan 08 §2.7). Off: the next frame is final (MOT-7). */
public class KitMotionSpecs internal constructor(private val s: KitMotionState) {
    private fun ms(v: Int) = (v * s.debugTimeScale).toInt()
    private fun k(stiffness: Float) = stiffness / (s.debugTimeScale * s.debugTimeScale)

    public fun <T> enter(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(MotionTokens.STANDARD), easing = KitEasing.Enter)

    public fun <T> quick(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else tween(ms(MotionTokens.QUICK), easing = KitEasing.Enter)

    /** A part leaving uses QUICK on `Enter` (Flutter's exit curve is a reverse-slot curve). */
    public fun <T> exit(): FiniteAnimationSpec<T> = quick()

    public fun <T> spatial(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else spring(MotionTokens.SPATIAL_ZETA, k(MotionTokens.SPATIAL_K))

    public fun <T> largeTravel(): FiniteAnimationSpec<T> =
        if (s.reduced) snap() else spring(1f, k(MotionTokens.LARGE_TRAVEL_K))

    public val state: KitMotionState get() = s
}

public object KitMotion {
    public val state: KitMotionState
        @Composable @ReadOnlyComposable get() = LocalKitMotion.current

    public val specs: KitMotionSpecs
        @Composable @ReadOnlyComposable get() = KitMotionSpecs(LocalKitMotion.current)
}
