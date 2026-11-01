package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import kotlin.math.PI
import kotlin.math.cos

/**
 * The reply glow is **one choice** (B-KIT-002, B-CHAT-006; plan 08 §3.1, §5.3): Off, Classic or Soft ring. Its colours
 * and speed are the app's own. `:feature:chat` maps the migrated `oc.effectsActivityGlow` + `oc.effectsGlowStyle` into
 * it with [of]; the retired `oc.effectsGlowColours` and `oc.effectsGlowSpeed` are never read.
 */
public enum class KitGlowChoice {
    Off,
    Classic,
    Soft,
    ;

    public companion object {
        /** On by default and Classic by default: a missing stored value reads on/classic, a stored off stays off. */
        public fun of(on: Boolean = true, soft: Boolean = false): KitGlowChoice =
            when {
                !on -> Off
                soft -> Soft
                else -> Classic
            }
    }
}

/** The one moving part of the composer (MOT-15, plan 08 §5.3). */
internal enum class MovingPart { Glow, StopRing, None }

/**
 * Only one thing moves around the composer at a time. Calm and Off move nothing (Calm keeps a still, lit glow). The
 * running-turn Stop chip never moves; only the voice-mode "Stop reading" circle has a ring, and it turns only when the
 * glow is switched off.
 */
internal fun movingPart(level: MotionLevel, glowOn: Boolean): MovingPart =
    when {
        level != MotionLevel.Full -> MovingPart.None
        glowOn -> MovingPart.Glow
        else -> MovingPart.StopRing
    }

/** What the glow draws (plan 08 §5.3 "Levels"). */
internal enum class GlowDraw {
    /** No glow: the choice is Off, or motion is Off (person or system). */
    None,

    /** Full: the highlight travels and the halo breathes. */
    Travel,

    /** Calm: a still, lit ring (Classic at full strength with the halo held at its midpoint; Soft at half strength). */
    Still,

    /** Saver (which forces motion Off): one still 1 dp hairline in the accent, no halo. */
    Hairline,
}

/** The resolved look of the glow for one (choice, level, Saver, still-frame) combination. Pure; see [glowPlan]. */
internal data class GlowPlan(
    val draw: GlowDraw,
    val soft: Boolean,
    /** Strength while a reply runs (the fade's target). */
    val strength: Float,
    /** One lap, ms (only read when [draw] is [GlowDraw.Travel]). */
    val lapMillis: Int,
    /** Classic draws a halo under the pill (Soft has its wash instead; the hairline has none). */
    val halo: Boolean,
    /** The loop clock runs (Full, not a still frame). */
    val loops: Boolean,
    /** Where a still frame puts the highlight (tests, screenshots, gallery). */
    val framePhase: Float,
)

internal object GlowGeometry {
    /** Classic ring stroke, halo blur and spread, dp (plan 08 §2.5). */
    const val CLASSIC_STROKE_DP: Float = 1.6f
    const val HALO_BLUR_DP: Float = 18f
    const val HALO_SPREAD_DP: Float = 1f

    /** Soft ring line, wash width and wash blur, dp; line and wash peaks (Flutter `_ActivityGlow`). */
    const val SOFT_LINE_DP: Float = 2.2f
    const val SOFT_WASH_DP: Float = 12f
    const val SOFT_WASH_BLUR_DP: Float = 7f
    const val SOFT_LINE_PEAK: Float = 0.75f
    const val SOFT_WASH_PEAK: Float = 0.22f
    const val SOFT_CALM_STRENGTH: Float = 0.5f

    /** Saver hairline, dp. */
    const val HAIRLINE_DP: Float = 1f

    /** Halo alpha: floor ↔ ceiling twice per lap; a still glow holds the midpoint so it reads as lit. */
    const val HALO_FLOOR: Float = 0.15f
    const val HALO_CEILING: Float = 0.35f
    const val HALO_STILL: Float = (HALO_FLOOR + HALO_CEILING) / 2f

    /** A still frame: Classic centres the highlight on the top edge; Soft starts its sweep here (Flutter parity). */
    const val CLASSIC_FRAME_PHASE: Float = 0.57f
    const val SOFT_FRAME_PHASE: Float = 0.08f
}

internal fun glowPlan(choice: KitGlowChoice, level: MotionLevel, saver: Boolean, stillFrame: Boolean): GlowPlan {
    val soft = choice == KitGlowChoice.Soft
    val lap = if (soft) MotionTokens.GLOW_SOFT_LAP else MotionTokens.GLOW_CLASSIC_LAP
    val frame = if (soft) GlowGeometry.SOFT_FRAME_PHASE else GlowGeometry.CLASSIC_FRAME_PHASE
    fun plan(draw: GlowDraw, strength: Float = 1f, halo: Boolean = !soft, loops: Boolean = false) =
        GlowPlan(draw, soft, strength, lap, halo, loops, frame)
    return when {
        choice == KitGlowChoice.Off -> plan(GlowDraw.None, strength = 0f, halo = false)
        saver -> plan(GlowDraw.Hairline, halo = false)
        level == MotionLevel.Off -> plan(GlowDraw.None, strength = 0f, halo = false)
        level == MotionLevel.Calm -> plan(GlowDraw.Still, strength = if (soft) GlowGeometry.SOFT_CALM_STRENGTH else 1f)
        else -> plan(GlowDraw.Travel, loops = !stillFrame)
    }
}

/** The share of a lap reached after [elapsedNanos] at [speed] laps-per-lap-time (1 normal, 0.5 half speed). */
internal fun lapPhase(elapsedNanos: Long, lapMillis: Int, speed: Float = 1f): Float {
    if (lapMillis <= 0 || speed <= 0f) return 0f
    val laps = elapsedNanos.toDouble() * speed / (lapMillis.toDouble() * 1_000_000.0)
    return (laps - kotlin.math.floor(laps)).toFloat()
}

/** Classic halo alpha for a lap [phase]: 0.25 − 0.10 × cos(4π·phase), two breaths per lap; null = held at 0.25. */
internal fun haloAlpha(phase: Float?): Float =
    if (phase == null) {
        GlowGeometry.HALO_STILL
    } else {
        GlowGeometry.HALO_STILL -
            (GlowGeometry.HALO_CEILING - GlowGeometry.HALO_FLOOR) / 2f * cos(4f * PI.toFloat() * phase)
    }

/** A fade's eased progress (Flutter `easeOutCubic` = `KitEasing.Enter`) after [elapsedNanos] of [durationMillis]. */
internal fun fadeProgress(elapsedNanos: Long, durationMillis: Float): Float {
    if (durationMillis <= 0f) return 1f
    val linear = (elapsedNanos / 1_000_000f / durationMillis).coerceIn(0f, 1f)
    return KitEasing.Enter.transform(linear)
}

/**
 * The loop clock of one moving part: a lap phase in `[0, 1)` read only in the draw phase (plan 08 R3). It runs only
 * while [running]; otherwise it holds [still]. Durations follow the debug time scale and the system animator scale
 * (plan 08 §3.2: our own frame loops multiply their rate by `1 / scale`). No frames are requested while it does not
 * run, and frames stop with the window (Choreographer) — nothing ticks in the background (§3.5).
 */
@Composable
internal fun rememberLoopPhase(running: Boolean, lapMillis: Int, speed: Float, still: Float): FloatState {
    val phase = remember { mutableFloatStateOf(still) }
    val motion = KitMotion.state
    val signals by LocalKitPlatform.current.signals.collectAsState()
    val scale = (motion.debugTimeScale * signals.animatorScale).takeIf { it > 0f } ?: 1f
    LaunchedEffect(running, lapMillis, speed, scale, still) {
        if (!running) {
            phase.floatValue = still
            return@LaunchedEffect
        }
        val from = phase.floatValue
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0) start = now
                phase.floatValue = (from + lapPhase(now - start, lapMillis, speed / scale)) % 1f
            }
        }
    }
    return phase
}

/**
 * The glow's strength, 0..1: eased toward [target] over `GLOW_FADE` (450 ms, exception X6) on `Enter`; Off snaps.
 * The first composition shows the final state (R8): a reply already running when the chat opens is lit at once.
 * Frames are requested only while the fade runs.
 */
@Composable
internal fun rememberGlowStrength(target: Float, motion: KitMotionState): FloatState {
    val strength = remember { mutableFloatStateOf(target) }
    val signals by LocalKitPlatform.current.signals.collectAsState()
    val scale = motion.debugTimeScale * signals.animatorScale
    LaunchedEffect(target, motion.reduced, scale) {
        val from = strength.floatValue
        if (motion.reduced || scale <= 0f || from == target) {
            strength.floatValue = target
            return@LaunchedEffect
        }
        val duration = MotionTokens.GLOW_FADE * scale
        var start = -1L
        var done = false
        while (!done) {
            withFrameNanos { now ->
                if (start < 0) start = now
                val p = fadeProgress(now - start, duration)
                strength.floatValue = from + (target - from) * p
                done = p >= 1f
            }
        }
    }
    return strength
}
