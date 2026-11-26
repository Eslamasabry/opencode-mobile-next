package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.design.effects.GlassTuning
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure optical lighting math for Apple Liquid Glass (B-610): maps device gravity tilt and scroll motion into a live
 * rim-light direction around the canonical 45° diagonal, with deadband filtering so stationary devices trigger zero
 * redraws (~0% visible idle CPU).
 */
public object GlassLightMath {
    /** Minimum angular change (degrees) before emitting a new highlight angle (eliminates sensor jitter at rest). */
    public const val DEADBAND_DEG: Float = 0.45f

    /** Maximum angular sweep from device tilt around 45° (degrees). */
    public const val MAX_TILT_SWEEP_DEG: Float = 38f

    /** Maximum angular sweep from scroll motion (degrees). */
    public const val MAX_SCROLL_SWEEP_DEG: Float = 22f

    /** Exponential smoothing factor for gravity tilt updates. */
    public const val TILT_EMA: Float = 0.20f

    private const val MIN_NORM: Float = 1e-3f
    private const val MIN_PLANAR: Float = 0.04f
    private const val ROLL_DENOM_MIN: Float = 0.35f
    private const val HALF_TURN_DEG: Float = 180f
    private const val PITCH_REST_NY: Float = 0.55f
    private const val PITCH_GAIN_DEG: Float = 14f
    private const val ROLL_GAIN: Float = 0.65f
    internal const val MIN_ANGLE_DEG: Float = 5f
    internal const val MAX_ANGLE_DEG: Float = 175f
    private const val MIN_SCROLL_PX: Float = 0.5f
    private const val SCROLL_DP_GAIN: Float = 0.22f
    internal const val SCROLL_DECAY_CARRY: Float = 0.72f
    internal const val SCROLL_MIN_STEP_DEG: Float = 0.25f
    internal const val SCROLL_REST_EPS_DEG: Float = 0.1f

    /**
     * Maps Android gravity vector `(gx, gy, gz)` (m/s²) to a specular light direction in degrees around
     * [GlassTuning.LIGHT_DEG] (45°). Flat on a table (`gx ≈ 0, gy ≈ 0`) rests calmly at 45°; tilting left/right or
     * pitching smoothly sweeps the highlight along curved glass rims.
     */
    public fun tiltAngleDeg(gx: Float, gy: Float, gz: Float): Float {
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        val planar = if (norm >= MIN_NORM) {
            val nx = (gx / norm).coerceIn(-1f, 1f)
            val ny = (gy / norm).coerceIn(-1f, 1f)
            sqrt(nx * nx + ny * ny).coerceIn(0f, 1f)
        } else 0f
        return if (planar < MIN_PLANAR) {
            GlassTuning.LIGHT_DEG
        } else {
            val nx = (gx / norm).coerceIn(-1f, 1f)
            val ny = (gy / norm).coerceIn(-1f, 1f)
            val roll = atan2(nx, maxOf(abs(ny), ROLL_DENOM_MIN)) * (HALF_TURN_DEG / PI.toFloat())
            val pitchShift = (ny - PITCH_REST_NY) * PITCH_GAIN_DEG
            val delta = (roll * ROLL_GAIN + pitchShift).coerceIn(-MAX_TILT_SWEEP_DEG, MAX_TILT_SWEEP_DEG)
            (GlassTuning.LIGHT_DEG + delta * planar).coerceIn(MIN_ANGLE_DEG, MAX_ANGLE_DEG)
        }
    }

    /** Returns [targetDeg] only when it moved at least [DEADBAND_DEG] from [currentDeg]; else returns [currentDeg]. */
    public fun applyDeadband(currentDeg: Float, targetDeg: Float, deadbandDeg: Float = DEADBAND_DEG): Float =
        if (abs(targetDeg - currentDeg) >= deadbandDeg) targetDeg else currentDeg

    /** Maps a vertical scroll delta in px to a bounded specular nudge in degrees. */
    public fun scrollImpulseDeg(deltaYPx: Float, density: Float): Float =
        if (density <= 0f || abs(deltaYPx) < MIN_SCROLL_PX) {
            0f
        } else {
            ((deltaYPx / density) * SCROLL_DP_GAIN).coerceIn(-MAX_SCROLL_SWEEP_DEG, MAX_SCROLL_SWEEP_DEG)
        }

    /** Unit direction vector `(cos, sin)` for [deg] degrees. */
    public fun unitDir(deg: Float): FloatArray {
        val rad = deg * (PI.toFloat() / HALF_TURN_DEG)
        return floatArrayOf(cos(rad), sin(rad))
    }
}

/**
 * Live specular light state for all Liquid Glass panes in one window (B-610).
 * Read strictly inside draw phases so tilting or scrolling updates GPU shader uniforms with ZERO recomposition.
 */
@Stable
public class KitGlassLight {
    internal var tiltDeg: Float by mutableFloatStateOf(GlassTuning.LIGHT_DEG)
    internal var scrollOffsetDeg: Float by mutableFloatStateOf(0f)
    internal var scrollPulse: Int by mutableIntStateOf(0)

    /** Applies a gravity-derived angle (degrees) through deadband filtering (no state write when steady). */
    public fun onTiltDeg(targetDeg: Float) {
        val next = GlassLightMath.applyDeadband(tiltDeg, targetDeg)
        if (next != tiltDeg) tiltDeg = next
    }

    /** Applies a scroll delta impulse (px) to sweep edge highlights while content scrolls beneath glass. */
    public fun onScrollDelta(deltaYPx: Float, density: Float) {
        val impulse = GlassLightMath.scrollImpulseDeg(deltaYPx, density)
        if (impulse == 0f) return
        val maxSweep = GlassLightMath.MAX_SCROLL_SWEEP_DEG
        val next = (scrollOffsetDeg * GlassLightMath.SCROLL_DECAY_CARRY + impulse).coerceIn(-maxSweep, maxSweep)
        if (abs(next - scrollOffsetDeg) >= GlassLightMath.SCROLL_MIN_STEP_DEG) {
            scrollOffsetDeg = next
            scrollPulse++
        }
    }

    /** Resets live light to canonical 45° (used when Reduced Motion turns on or glass turns off). */
    public fun reset() {
        if (tiltDeg != GlassTuning.LIGHT_DEG) tiltDeg = GlassTuning.LIGHT_DEG
        if (scrollOffsetDeg != 0f) scrollOffsetDeg = 0f
    }

    /** Effective light direction in degrees (base [fallbackDeg] + live tilt delta + scroll sweep). */
    public fun effectiveDeg(fallbackDeg: Float = GlassTuning.LIGHT_DEG): Float {
        val tiltShift = tiltDeg - GlassTuning.LIGHT_DEG
        return (fallbackDeg + tiltShift + scrollOffsetDeg).coerceIn(
            GlassLightMath.MIN_ANGLE_DEG,
            GlassLightMath.MAX_ANGLE_DEG,
        )
    }

    /** Effective unit light vector `(x, y)` for shader uniforms, read in the draw phase. */
    public fun lightDir(fallbackDeg: Float = GlassTuning.LIGHT_DEG): FloatArray =
        GlassLightMath.unitDir(effectiveDeg(fallbackDeg))
}

/** Window-scoped live specular light for Liquid Glass (provided once by `KitTheme`). */
public val LocalKitGlassLight: ProvidableCompositionLocal<KitGlassLight> = staticCompositionLocalOf { KitGlassLight() }

/** Convenience entry point for `Modifier.then(kitGlassScrollLightModifier(...))`. */
@Composable
internal fun kitGlassScrollLightModifier(active: Boolean): Modifier = Modifier.kitGlassScrollLight(active)

/**
 * Observes scroll deltas from a screen's body without consuming them and gently sweeps [LocalKitGlassLight] while
 * scrolling, then springs smoothly back to rest with zero idle wakeups once settled (B-610).
 */
@Composable
internal fun Modifier.kitGlassScrollLight(active: Boolean): Modifier {
    if (!active) return this
    val light = LocalKitGlassLight.current
    val density = LocalDensity.current.density
    val decay = remember(light) { Animatable(0f) }
    val decaySpec = KitMotion.specs.effectsSlow<Float>()
    val pulse = light.scrollPulse
    LaunchedEffect(light, pulse) {
        if (pulse == 0 || abs(light.scrollOffsetDeg) < GlassLightMath.SCROLL_REST_EPS_DEG) return@LaunchedEffect
        decay.snapTo(light.scrollOffsetDeg)
        decay.animateTo(
            targetValue = 0f,
            animationSpec = decaySpec,
        ) {
            light.scrollOffsetDeg = value
        }
        light.scrollOffsetDeg = 0f
    }
    val connection =
        remember(light, density) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (consumed.y != 0f) light.onScrollDelta(consumed.y, density)
                    return Offset.Zero
                }
            }
        }
    return this.nestedScroll(connection)
}
