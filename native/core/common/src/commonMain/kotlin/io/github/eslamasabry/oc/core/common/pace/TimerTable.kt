package io.github.eslamasabry.oc.core.common.pace

/**
 * The PB-42 rules for one module's `ALL_TIMERS` (plan 13 §11.2 rule 3). Each module's commonTest asserts this list is
 * empty (`assertTimerTable` in :core:testing). The hidden-idle floor is also enforced by [Cadence]'s init.
 */
public fun timerTableProblems(all: List<TimerSpec>): List<String> {
    val problems = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (spec in all) {
        if (!seen.add(spec.name)) {
            problems += "${spec.name}: declared twice"
            continue
        }
        val idle = spec.cadence.hiddenIdle
        if (idle != null && idle < Cadence.HIDDEN_IDLE_MIN) problems += "${spec.name}: hiddenIdle $idle is under 60 s"
        val watching = spec.cadence.hiddenWatching
        if (watching != null && watching < Cadence.HIDDEN_WATCHING_MIN && spec.rows.isEmpty()) {
            problems += "${spec.name}: hiddenWatching $watching is under 5 s and names no catalog row"
        }
        if (spec.reason.isBlank()) problems += "${spec.name}: reason is empty"
        if (spec.name.isBlank()) problems += "(unnamed): name is empty"
    }
    return problems
}
