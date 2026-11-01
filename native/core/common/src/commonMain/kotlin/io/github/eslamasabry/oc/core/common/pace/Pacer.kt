package io.github.eslamasabry.oc.core.common.pace

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One shared value for every repeating job (plan 13 §11.1). Bound at graph level from `LinkDemand`
 * (:engine:connection); engines below connection receive the same StateFlow through DI.
 */
public enum class Visibility { Foreground, HiddenWatching, HiddenIdle }

/** How often a repeating job runs in each visibility. null = it does not run (no timer is armed). */
public data class Cadence(
    val foreground: Duration?,
    val hiddenWatching: Duration?,
    val hiddenIdle: Duration?,
    /** ± fraction of the period, 0.0–0.5. */
    val jitter: Double = 0.0,
) {
    init {
        require(hiddenIdle == null || hiddenIdle >= HIDDEN_IDLE_MIN) { "PB-42: hidden-idle timers ≥ 60 s" }
        require(jitter in 0.0..0.5) { "jitter is a fraction 0.0–0.5" }
        listOfNotNull(foreground, hiddenWatching, hiddenIdle).forEach { require(it.isPositive()) { "periods are positive" } }
    }

    public fun periodFor(visibility: Visibility): Duration? =
        when (visibility) {
            Visibility.Foreground -> foreground
            Visibility.HiddenWatching -> hiddenWatching
            Visibility.HiddenIdle -> hiddenIdle
        }

    public companion object {
        public val HIDDEN_IDLE_MIN: Duration = 60.seconds
        public val HIDDEN_WATCHING_MIN: Duration = 5.seconds
    }
}

/** A declared repeating job. Every instance lives in a file named `*Timers.kt` (Konsist K-P2). */
public data class TimerSpec(
    /** Trace-safe, e.g. "runtime.recoveryCheck". */
    val name: String,
    val cadence: Cadence,
    /** One line: what breaks if it stops. */
    val reason: String,
    /** Catalog rows / PB ids it serves. A row is what allows hidden-watching under 5 s. */
    val rows: List<String> = emptyList(),
)

/** The only way to repeat work (plan 13 §11.2). No `while` + `delay` loops outside this package (K-P1). */
public interface Pacer {
    /**
     * Ticks on [spec]'s cadence for the current visibility. Suspends with no timer armed while the cadence is null.
     * The first tick comes one period after collection starts; after a change to Foreground it comes at once
     * (a returning person sees fresh state).
     */
    public fun ticks(spec: TimerSpec): Flow<Unit>
}
