package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * The live background's one shared clock (Dart `KitLiveClock`, `lib/ui/kit/live_background/kit_live_clock.dart`): a
 * slow tick for every page that shows a moving look, and a rest when nobody touches the app.
 *
 * Why a slow tick: every look drifts slowly (less than a pixel a second), so a frame on every vsync bought nothing a
 * person can see and cost the most of any part of the app. So the look moves [FRAMES_PER_SECOND] times a second (a look
 * may ask for fewer, [KitLiveStyle.framesPerSecond]), every page on the same tick, and nothing is drawn between ticks.
 * After [idleAfterMs] with no touch, scroll or key it rests: no tick, no frame. The next touch wakes it where it
 * stopped ([seconds] never jumps), so it never appears to skip.
 *
 * The clock runs only while at least one moving look is shown ([attach]); Live background off, reduced motion, Calm,
 * Off and Saver all leave it detached. It owns no timer of its own: each shown [KitLiveBackground] waits [INTERVAL_NANOS]
 * and offers the frame time to [advance], which moves the clock once per interval however many pages offer it.
 */
public class KitLiveClock {
    /** Seconds the looks have moved, advanced only on a tick; it holds still while the clock rests. */
    public var seconds: Double by mutableDoubleStateOf(0.0)
        private set

    /** Ticks so far ([seconds] in ticks): a look slower than [FRAMES_PER_SECOND] repaints on every few of them. */
    public var ticks: Int = 0
        private set

    /** False while the look rests (no input for [idleAfterMs]). */
    public var awake: Boolean by mutableStateOf(true)
        private set

    /** Moving looks shown right now. */
    public var users: Int = 0
        private set

    /** How long without a touch, scroll or key before the look rests. */
    public var idleAfterMs: Long = IDLE_AFTER_MS

    private var quiet = 0
    private var lastNanos = Long.MIN_VALUE

    /** A moving look starts showing: the first one wakes the clock. */
    public fun attach() {
        if (users++ > 0) return
        wake()
    }

    /** A moving look stops showing: the last one stops the clock. */
    public fun detach() {
        check(users > 0) { "detach without attach" }
        if (--users > 0) return
        lastNanos = Long.MIN_VALUE
        awake = true
    }

    /** A touch, a scroll or a key: the look moves again if it rested. */
    public fun input() {
        quiet = 0
        if (!awake && users > 0) wake()
    }

    private fun wake() {
        quiet = 0
        awake = true
        lastNanos = Long.MIN_VALUE
    }

    /**
     * A page offers the frame time [nowNanos] after waiting an interval. The first offer of an interval moves the clock
     * by the ticks that passed (a late frame reports the ticks it missed; a long stall moves at most three ticks'
     * worth, so nothing leaps); later offers in the same interval do nothing. Returns whether the clock moved.
     */
    public fun advance(nowNanos: Long): Boolean {
        if (users == 0 || !awake) return false
        val last = lastNanos
        val half = INTERVAL_NANOS shr 1
        if (last != Long.MIN_VALUE && nowNanos - last < half) return false
        val missed = if (last == Long.MIN_VALUE) 1 else max(1, ((nowNanos - last + half) / INTERVAL_NANOS).toInt())
        lastNanos = nowNanos
        tick(missed)
        return true
    }

    /** One timer tick that reports [missed] ticks (Dart `_tick`). */
    internal fun tick(missed: Int) {
        val step = min(missed, 3) // F: lib/ui/kit/live_background/kit_live_clock.dart:121
        ticks += step
        seconds += step * INTERVAL_MICROS / 1e6
        quiet += missed
        if (quiet * INTERVAL_MICROS >= idleAfterMs.milliseconds.inWholeMicroseconds) {
            awake = false
            lastNanos = Long.MIN_VALUE
        }
    }

    /** Whether a look drawn [fps] times a second moves on this tick (Dart `KitLiveBackground._tick`). */
    public fun due(fps: Int): Boolean {
        val every = (FRAMES_PER_SECOND.toDouble() / fps.coerceIn(1, FRAMES_PER_SECOND)).let { kotlin.math.round(it).toInt() }
        return ticks % every == 0
    }

    public companion object {
        /** Ticks a second while a look moves. */
        public const val FRAMES_PER_SECOND: Int = 12 // F: lib/ui/kit/live_background/kit_live_clock.dart:34

        /** The time between two ticks, in whole microseconds as Dart's `Duration` holds it. */
        public const val INTERVAL_MICROS: Long = 1_000_000L / FRAMES_PER_SECOND

        public val INTERVAL_NANOS: Long = INTERVAL_MICROS.microseconds.inWholeNanoseconds

        /** How long without a touch, scroll or key before the look rests. */
        public const val IDLE_AFTER_MS: Long = 30_000L // F: lib/ui/kit/live_background/kit_live_clock.dart:42

        /** The one clock every page shares. */
        public val instance: KitLiveClock = KitLiveClock()
    }
}
