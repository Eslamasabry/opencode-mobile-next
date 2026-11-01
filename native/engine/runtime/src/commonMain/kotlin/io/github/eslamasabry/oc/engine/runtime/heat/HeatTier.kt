package io.github.eslamasabry.oc.engine.runtime.heat

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Easing the phone's agents before it is hot (port of lib/builtin/heat_tier.dart; plan 11 §8.2).
 *
 * [ThermalPolicy] pauses the AI Team at SEVERE. Below it, two gentler steps: [Slow] runs the agents' programs at a
 * lower priority; [Hold] does the same and makes new heavy work wait (the process budget asks [holdsNewWork]).
 */
public enum class HeatTier {
    /** Full speed. */
    Normal,

    /** Agent programs at lower priority. */
    Slow,

    /** Lower priority, and new heavy work waits. */
    Hold,
    ;

    public val slowsAgents: Boolean get() = this != Normal
    public val holdsNewWork: Boolean get() = this == Hold
}

/** The rules, with no platform in them. Numbers unchanged from the Flutter freeze tip (plan 11 §8.2). */
public object HeatTierPolicy {
    /** The forecast at or above which agents are slowed / new work waits. 1.0 is where Android throttles hard. */
    public const val SLOW_HEADROOM: Double = 0.7
    public const val HOLD_HEADROOM: Double = 0.9

    /** Unplugged and at or under this level: slow / hold. */
    public const val SLOW_BATTERY: Int = 20
    public const val HOLD_BATTERY: Int = 10

    /** How long readings must stay clear of a step before it is left; a hot reading in between restarts the wait. */
    public val calmFor: Duration = 2.minutes

    /**
     * The raw tier for these readings. [batteryLevel] and [charging] are null when unknown (then the battery does not
     * count; unknown headroom counts as 0). [hidden]: the app is out of sight. [fullSpeed]: "Always full speed".
     */
    public fun decide(
        thermal: ThermalReading,
        batteryLevel: Int? = null,
        charging: Boolean? = null,
        hidden: Boolean = false,
        fullSpeed: Boolean = false,
        powerSave: Boolean = false,
    ): HeatTier {
        if (fullSpeed) return HeatTier.Normal
        val headroom = thermal.headroom ?: 0.0
        val unplugged = charging == false
        if (thermal.status.atLeast(ThermalStatus.Moderate) ||
            headroom >= HOLD_HEADROOM ||
            (unplugged && batteryLevel != null && batteryLevel <= HOLD_BATTERY)
        ) {
            return HeatTier.Hold
        }
        if (thermal.status.atLeast(ThermalStatus.Light) ||
            headroom >= SLOW_HEADROOM ||
            (unplugged && batteryLevel != null && batteryLevel <= SLOW_BATTERY) ||
            powerSave ||
            hidden
        ) {
            return HeatTier.Slow
        }
        return HeatTier.Normal
    }
}
