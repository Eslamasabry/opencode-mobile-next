package io.github.eslamasabry.oc.engine.runtime.budget

import io.github.eslamasabry.oc.engine.runtime.heat.BatteryReading
import io.github.eslamasabry.oc.engine.runtime.heat.HeatTierPolicy
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalPolicy
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading

/**
 * Heavy work the budget admits, with about how many processes it adds as it starts: its own and its first tools
 * (plan 11 §7.2 "Heavy jobs").
 */
public enum class HeavyJob(public val processes: Int) {
    /** A new conversation with an agent on this phone (the agent, its card helper and its first tool). */
    AgentSession(4),

    /** The AI Team engine and its first worker (D6, later). */
    AiTeam(6),

    /** A setup job: an install or an update of a component. */
    SetupJob(3),

    /** A Linux service the app starts on request. */
    LinuxService(2),
}

/** Why heavy work waits. Declared mildest first: the guard reports the last one any waiter has. */
public enum class BudgetWait {
    /** Too many programs run already. */
    Room,

    /** The phone is hot. */
    Warm,

    /** The battery is low and the phone is not charging. */
    Battery,
}

/**
 * The app's own budget for heavy work on this phone (port of lib/builtin/process_budget.dart; plan 11 §7).
 *
 * Android 12+ kills an app's child processes above 32 (the phantom-process limit, shared by every app), oldest first.
 * When the person turns that limit off, nothing stops the app from growing until the phone overheats. So the app keeps
 * its own count either way: new heavy work starts only with [RESERVE] processes left free under [CAP] (or [LIFTED_CAP]
 * once Android's limit is off), and not while the phone is hot or the battery is low and unplugged. The rules, with no
 * platform in them.
 */
public object ProcessBudget {
    /** Android's limit, the app's ceiling while it is on. */
    public const val CAP: Int = 32

    /** The ceiling once the person turned Android's limit off: a little more, never unlimited. */
    public const val LIFTED_CAP: Int = 48

    /** Kept free under the ceiling for the tools of work already running: new work never takes the last few. */
    public const val RESERVE: Int = 4

    /** At or under this battery level, unplugged, new heavy work waits. */
    public const val LOW_BATTERY: Int = 20

    /** Running on this phone warns from this many processes (`PhoneProcessWatch.nearLimit`, plan 11 §7.2). */
    public const val WARN_FROM: Int = 28

    public fun ceiling(limitLifted: Boolean): Int = if (limitLifted) LIFTED_CAP else CAP

    public fun nearLimit(count: Int?): Boolean = count != null && count >= WARN_FROM

    /**
     * Whether [job] may start now: null when it may, else why it waits. [running] counts the processes Android counts
     * (the app's own excepted) plus what was admitted moments ago and does not show yet. Heat comes first (the
     * protective pause, then the [HeatTierPolicy] hold unless [fullSpeed]), then the battery, then room.
     */
    public fun decide(
        job: HeavyJob,
        running: Int,
        limitLifted: Boolean,
        thermal: ThermalReading = ThermalReading.Unknown,
        battery: BatteryReading = BatteryReading.Unknown,
        fullSpeed: Boolean = false,
    ): BudgetWait? {
        if (ThermalPolicy.hot(thermal)) return BudgetWait.Warm
        if (!fullSpeed && HeatTierPolicy.decide(thermal).holdsNewWork) return BudgetWait.Warm
        val level = battery.level
        if (level != null && level <= LOW_BATTERY && battery.charging == false) return BudgetWait.Battery
        val room = ceiling(limitLifted) - RESERVE - running
        return if (job.processes <= room) null else BudgetWait.Room
    }
}
