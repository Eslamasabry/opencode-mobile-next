package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The live background's one shared clock (Dart `KitLiveClock`, `lib/ui/kit/live_background/kit_live_clock.dart`): a
 * slow tick for every page that shows a moving look, and a rest when nobody touches the app.
 *
 * Every look drifts slowly, so the look moves [FRAMES_PER_SECOND] times a second (never faster), every page on the
 * same tick, and nothing is drawn between ticks. After [idleAfterMs] with no touch it rests: no tick, no frame. The
 * next touch wakes it where it stopped ([seconds] never jumps).
 *
 * This holds the arithmetic only. The frames come from the backgrounds that use it: each one, while attached, waits on
 * `withFrameNanos` and calls [onFrame] with the frame time; the clock moves on only when a whole [INTERVAL_NANOS] has
 * passed since the last tick, so several pages on screen still give one tick per interval.
 */
public class KitLiveClock {
    /** Seconds the looks have moved; read only in the draw phase, so a tick redraws the look and nothing else. */
    public var seconds: Double by mutableDoubleStateOf(0.0)
        private set

    /** Ticks so far: a look slower than [FRAMES_PER_SECOND] moves on every few of them. */
    public var ticks: Int = 0
        private set

    /** False while the look rests (no input for [idleAfterMs]). */
    public var awake: Boolean by mutableStateOf(true)
        private set

    /** How long without a touch before the look rests (kit_live_clock.dart:42). */
    public var idleAfterMs: Long = IDLE_AFTER_MS

    /** Moving looks shown right now. */
    public var users: Int = 0
        private set

    private var quiet = 0L
    private var lastTimerTick = 0L
    private var wakeAt = -1L

    /** A moving look starts showing. */
    public fun attach() {
        if (users++ > 0) return
        wake()
    }

    /** A moving look stops showing: the last one stops the clock. */
    public fun detach() {
        check(users > 0) { "detach without attach" }
        if (--users > 0) return
        wakeAt = -1L
        awake = true
    }

    /** A touch, a scroll or a key: the look moves again if it rested (kit_live_clock.dart:98-101). */
    public fun input() {
        quiet = 0
        if (!awake && users > 0) wake()
    }

    private fun wake() {
        quiet = 0
        awake = true
        wakeAt = -1L
        lastTimerTick = 0
    }

    /**
     * A frame at [nowNanos] (from `withFrameNanos`). Returns the nanoseconds until the next tick is due, or -1 when
     * the clock rests or nobody is attached. Port of `_tick` (kit_live_clock.dart:116-128): a late frame counts the
     * ticks it missed, a long stall moves at most three ticks' worth, so nothing leaps.
     */
    public fun onFrame(nowNanos: Long): Long {
        if (users == 0 || !awake) return -1L
        if (wakeAt < 0) wakeAt = nowNanos
        val timerTick = (nowNanos - wakeAt) / INTERVAL_NANOS
        if (timerTick > lastTimerTick) {
            val missed = max(1L, timerTick - lastTimerTick)
            lastTimerTick = timerTick
            val step = min(missed, MAX_STEP)
            ticks += step.toInt()
            seconds += step * INTERVAL_MICROS / 1e6
            quiet += missed
            if (quiet * INTERVAL_MICROS >= idleAfterMs.milliseconds.inWholeMicroseconds) {
                awake = false
                return -1L
            }
        }
        return wakeAt + (lastTimerTick + 1) * INTERVAL_NANOS - nowNanos
    }

    /** Whether a look drawn [framesPerSecond] times a second moves on this tick (kit_live_background.dart:156-160). */
    public fun due(framesPerSecond: Int): Boolean {
        val fps = framesPerSecond.coerceIn(1, FRAMES_PER_SECOND)
        val every = (FRAMES_PER_SECOND.toDouble() / fps).roundToInt()
        return ticks % every == 0
    }

    public companion object {
        /** The one clock every page shares. */
        public val instance: KitLiveClock = KitLiveClock()

        /** Ticks a second while a look moves (kit_live_clock.dart:34). */
        public const val FRAMES_PER_SECOND: Int = 12 // F: lib/ui/kit/live_background/kit_live_clock.dart:34

        /** Dart `interval`: whole microseconds, as `Duration(microseconds: 1000000 ~/ 12)`. */
        public val INTERVAL_MICROS: Long = 1.seconds.inWholeMicroseconds / FRAMES_PER_SECOND
        internal val INTERVAL_NANOS: Long = INTERVAL_MICROS.microseconds.inWholeNanoseconds

        /** Dart `idleAfter` (kit_live_clock.dart:42). */
        public const val IDLE_AFTER_MS: Long = 30_000 // F: lib/ui/kit/live_background/kit_live_clock.dart:42

        private const val MAX_STEP = 3L // F: lib/ui/kit/live_background/kit_live_clock.dart:121
    }
}
