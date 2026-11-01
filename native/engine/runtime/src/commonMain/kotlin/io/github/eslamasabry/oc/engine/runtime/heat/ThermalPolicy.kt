package io.github.eslamasabry.oc.engine.runtime.heat

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What the guard does next. */
public enum class ThermalAction { None, Pause, Stop, Resume }

/** What the guard holds right now. */
public enum class ThermalHold { None, Paused, Stopped }

/**
 * The protective pause (port of lib/builtin/thermal_guard.dart `ThermalPolicy`; plan 11 §8.2):
 *
 * - SEVERE, or a 30-second forecast at [PAUSE_HEADROOM] or above: pause.
 * - CRITICAL, EMERGENCY or SHUTDOWN: stop (paused first, so work is saved).
 * - Resume only what the guard itself held, and only once the phone stayed below that for [coolFor]; a hot reading in
 *   between starts the wait again, so it never flaps.
 * - Turned off, the guard starts nothing new but still gives back what it holds once the phone is cool.
 *
 * The OpenCode server is never stopped for heat (plan 11 §8.2 "Never"); this policy is for the AI Team (D6, later)
 * and, through [veryHot] and [cool], for the Linux services heat hold. Not thread-safe: one owner calls it in order.
 */
public class ThermalPolicy(public val coolFor: Duration = 2.minutes) {
    public var hold: ThermalHold = ThermalHold.None
        private set

    /** Since when the phone has been cool while the guard holds; null when not waiting. */
    public var coolSince: Instant? = null
        private set

    /** When the wait for a cool phone ends; null when not waiting. */
    public val resumeAt: Instant?
        get() = if (hold == ThermalHold.None) null else coolSince?.plus(coolFor)

    public fun decide(reading: ThermalReading, now: Instant, enabled: Boolean): ThermalAction {
        when (hold) {
            ThermalHold.None -> {
                coolSince = null
                if (!enabled) return ThermalAction.None
                if (veryHot(reading)) return ThermalAction.Stop
                if (hot(reading)) return ThermalAction.Pause
                return ThermalAction.None
            }
            ThermalHold.Paused, ThermalHold.Stopped -> {
                if (enabled && hold == ThermalHold.Paused && veryHot(reading)) {
                    coolSince = null
                    return ThermalAction.Stop
                }
                if (!cool(reading)) {
                    coolSince = null
                    return ThermalAction.None
                }
                val since = coolSince ?: now.also { coolSince = it }
                return if (now - since >= coolFor) ThermalAction.Resume else ThermalAction.None
            }
        }
    }

    /** The guard did [action]; a failed or empty action is not reported here. */
    public fun applied(action: ThermalAction) {
        hold =
            when (action) {
                ThermalAction.Pause -> ThermalHold.Paused
                ThermalAction.Stop -> ThermalHold.Stopped
                ThermalAction.Resume -> ThermalHold.None
                ThermalAction.None -> return
            }
        coolSince = null
    }

    public companion object {
        public val PauseAt: ThermalStatus = ThermalStatus.Severe
        public val StopAt: ThermalStatus = ThermalStatus.Critical
        public const val PAUSE_HEADROOM: Double = 0.95

        public fun veryHot(reading: ThermalReading): Boolean = reading.status.atLeast(StopAt)

        public fun hot(reading: ThermalReading): Boolean =
            reading.status.atLeast(PauseAt) || (reading.headroom ?: 0.0) >= PAUSE_HEADROOM

        public fun cool(reading: ThermalReading): Boolean = !hot(reading)
    }
}
