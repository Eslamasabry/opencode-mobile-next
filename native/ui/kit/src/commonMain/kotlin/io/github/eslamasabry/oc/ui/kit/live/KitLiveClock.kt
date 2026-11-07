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
 * The live background's one shared clock (Dart `KitLiveClock`, `lib/ui/kit/live_background/kit_live_clock.dart`): a slow
 * tick for every page that shows a moving look, and a rest when nobody touches the app.
 *
 * The look moves [FRAMES_PER_SECOND] times a second (a look may ask for fewer), every page on the same tick, and nothing
 * is drawn between ticks. After [IDLE_AFTER_MS] with no touch it rests: no tick, no frame. The next touch wakes it where
 * it stopped ([seconds] never jumps). Pure logic: the host drives it with [advance] from its frame clock, so several
 * pages advancing it at once still move it once per tick.
 *
 * Interim copy of order 31's clock (orders 31-33 run in parallel).
 */
public class KitLiveClock internal constructor() {
    /** Seconds the looks have moved, advanced only on a tick. Snapshot state: a draw that reads it redraws on a tick. */
    public var seconds: Double by mutableDoubleStateOf(0.0)
        private set

    /** False while the look rests (no input for [IDLE_AFTER_MS]). */
    public var awake: Boolean by mutableStateOf(true)
        private set

    /** Ticks so far: a look slower than [FRAMES_PER_SECOND] repaints on every few of them. */
    public var ticks: Int = 0
        private set

    /** Moving looks shown right now. */
    public var users: Int = 0
        private set

    private var quiet = 0
    private var lastTimerTick = 0
    private var startNanos = 0L
    private var timing = false
    private var pendingStart = false

    /** A moving look starts showing. */
    public fun attach() {
        if (users++ > 0) return
        wake()
    }

    /** A moving look stops showing; the last one stops the clock. */
    public fun detach() {
        if (users == 0) return
        if (--users > 0) return
        timing = false
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
        if (timing) return
        timing = true
        lastTimerTick = 0
        pendingStart = true
    }

    /**
     * The frame clock reads [nowNanos]: every whole interval since the clock woke is a tick (Dart `Timer.periodic`),
     * and a late call reports the ticks it missed, at most [MAX_STEP] of them, so nothing leaps (kit_live_clock.dart:121-133).
     * Returns whether a tick happened.
     */
    public fun advance(nowNanos: Long): Boolean {
        if (users == 0 || !awake || !timing) return false
        if (pendingStart) {
            pendingStart = false
            startNanos = nowNanos
            return false
        }
        val due = ((nowNanos - startNanos) / INTERVAL_NANOS).toInt()
        if (due <= lastTimerTick) return false
        tick(due)
        return true
    }

    /** Dart `_tick` with the timer's tick count [timerTick]. */
    internal fun tick(timerTick: Int) {
        val missed = max(1, timerTick - lastTimerTick)
        lastTimerTick = timerTick
        val step = min(missed, MAX_STEP)
        ticks += step
        seconds += step * INTERVAL_US / 1e6
        quiet += missed
        if (quiet.toLong() * INTERVAL_US >= IDLE_AFTER_US) {
            timing = false
            awake = false
        }
    }

    /** Whether a look drawn [fps] times a second repaints on this tick (Dart `_tick` in kit_live_background.dart:156-160). */
    public fun repaintsAt(fps: Int): Boolean = ticks % everyFor(fps) == 0

    /** Back to a fresh clock (tests). */
    internal fun reset() {
        users = 0
        ticks = 0
        quiet = 0
        lastTimerTick = 0
        timing = false
        pendingStart = false
        seconds = 0.0
        awake = true
    }

    public companion object {
        /** Ticks a second while a look moves. */
        public const val FRAMES_PER_SECOND: Int = 12 // F: lib/ui/kit/live_background/kit_live_clock.dart:34

        /** The time between two ticks, in microseconds (Dart `interval`). */
        public const val INTERVAL_US: Long = 1_000_000L / FRAMES_PER_SECOND

        /** How long without a touch, scroll or key before the look rests. */
        public const val IDLE_AFTER_MS: Long = 30_000

        /** A long stall moves at most this many ticks' worth. */
        public const val MAX_STEP: Int = 3 // F: lib/ui/kit/live_background/kit_live_clock.dart:121

        private val INTERVAL_NANOS: Long = INTERVAL_US.microseconds.inWholeNanoseconds
        private val IDLE_AFTER_US: Long = IDLE_AFTER_MS.milliseconds.inWholeMicroseconds

        /** Ticks between two repaints of a look drawn [fps] times a second. */
        public fun everyFor(fps: Int): Int {
            val f = fps.coerceIn(1, FRAMES_PER_SECOND)
            return kotlin.math.round(FRAMES_PER_SECOND.toDouble() / f).toInt()
        }

        /** The one clock every page shares. */
        public val instance: KitLiveClock = KitLiveClock()
    }
}
