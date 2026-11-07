package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel

/**
 * The one clock every live background and look thumbnail on a page reads (E20-S28). It turns display frames into calm
 * background time:
 *
 * - it steps on every display frame (vsync through `withFrameNanos`), so a look follows the screen's own refresh rate
 *   (60, 90 or 120 Hz) with no cap of its own (owner, 11-07);
 * - it rests after [idleAfterNanos] (30 s) without a touch and wakes on the next one ([touch]);
 * - it holds while nothing uses it ([acquire] / [release]) or while [held] (app hidden, Saver, heat); the display
 *   stops sending frames while the screen is off or the app is in the background, and Battery Saver and heat force
 *   Calm motion, which holds one still frame (see [KitLiveBackground]);
 * - it never jumps: one step is at most a quarter second, so time carries on smoothly after a pause.
 *
 * [seconds] is snapshot state read only in draw blocks, so a step redraws the background layer and recomposes nothing.
 * The decision logic ([frame]) is plain code, driven by frame times, so tests drive it without a display.
 */
@Stable
public class LiveFrameClock(private val idleAfterNanos: Long = IDLE_AFTER_NANOS) {
    /** Background time in seconds. Read it in a draw block only. */
    public var seconds: Float by mutableFloatStateOf(0f)
        private set

    private var users = 0
    private var last = NONE
    private var lastActive = NONE
    private var touched = false
    private var idle = false

    /** Set while the app is hidden or the system asks for less work; the clock keeps its time. */
    public var held: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) restart() // back on screen counts as activity: the idle countdown starts again
                wake()
            }
        }

    internal val wakes = Channel<Unit>(Channel.CONFLATED)

    /** True while something on screen animates with this clock and it is not resting or held. */
    public val wantsFrames: Boolean
        get() = users > 0 && !held && !idle

    /** True after the idle timeout until the next touch. */
    public val resting: Boolean
        get() = idle

    public fun acquire() {
        users++
        if (users == 1) restart()
        wake()
    }

    public fun release() {
        if (users > 0) users--
    }

    /** A touch anywhere on the page: wakes a resting clock and restarts the idle countdown. */
    public fun touch() {
        restart()
        wake()
    }

    private fun restart() {
        touched = true
        if (idle) {
            idle = false
            last = NONE // the rest is not counted: time carries on from where it stopped
        }
    }

    /**
     * One display frame at [nanos] (monotonic). Returns true when background time stepped: on every frame while the
     * clock runs, however fast the display refreshes.
     */
    public fun frame(nanos: Long): Boolean {
        if (lastActive == NONE || touched) {
            lastActive = nanos
            touched = false
        }
        val used = users > 0 && !held
        if (used && nanos - lastActive >= idleAfterNanos) idle = true
        val running = used && !idle
        val steps = running && last != NONE && nanos > last
        when {
            !running -> last = NONE // the pause is not counted: time carries on where it stopped
            last == NONE -> last = nanos
            steps -> {
                seconds += (minOf(nanos - last, MAX_STEP_NANOS).toDouble() / NANOS_PER_SECOND).toFloat()
                last = nanos
            }
        }
        return steps
    }

    private fun wake() {
        wakes.trySend(Unit)
    }

    public companion object {
        /** Rest after this long without a touch (FG: lib/ui/kit/live_background/kit_live_clock.dart:44). */
        public const val IDLE_AFTER_NANOS: Long = 30_000_000_000L
        private const val MAX_STEP_NANOS: Long = 250_000_000L // FG: lib/ui/kit/live_background/kit_live_support.dart:61
        private const val NONE: Long = Long.MIN_VALUE
        private const val NANOS_PER_SECOND: Long = 1_000_000_000L
    }
}

/** The page's live clock; null outside a [KitLiveBackground]. */
public val LocalLiveClock: ProvidableCompositionLocal<LiveFrameClock?> = staticCompositionLocalOf { null }

/**
 * Remembers a clock and runs it: the only `withFrameNanos` loop of the live backgrounds, one step per vsync. While the
 * clock wants no frames the loop sleeps on its wake channel, so a resting or unused clock costs nothing.
 */
@Composable
internal fun rememberLiveFrameClock(): LiveFrameClock {
    val clock = remember { LiveFrameClock() }
    LaunchedEffect(clock) {
        while (true) {
            if (!clock.wantsFrames) {
                clock.wakes.receive()
                continue
            }
            withFrameNanos { clock.frame(it) }
        }
    }
    return clock
}
