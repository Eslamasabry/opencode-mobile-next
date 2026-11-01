package io.github.eslamasabry.oc.core.common.pace

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.transformLatest
import kotlin.random.Random
import kotlin.time.Duration

/**
 * The only [Pacer] implementation (plan 13 §11.2). Delays run on the collector's dispatcher, so tests drive it with
 * virtual time. [random] picks the jitter; production binds `Random.Default`, tests a seeded one.
 * (Plan signature had `clock` and `dispatchers`; neither is needed: `delay` is the clock — S0-CORE D-C6.)
 */
public class DefaultPacer(
    private val visibility: StateFlow<Visibility>,
    private val random: Random = Random.Default,
) : Pacer {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun ticks(spec: TimerSpec): Flow<Unit> {
        var previous: Visibility? = null
        return visibility.transformLatest { now ->
            val returned = previous != null && previous != Visibility.Foreground && now == Visibility.Foreground
            previous = now
            val period = spec.cadence.periodFor(now) ?: awaitCancellation()
            if (returned) emit(Unit)
            while (true) {
                delay(jittered(period, spec.cadence.jitter).coerceAtLeast(floorFor(now)))
                emit(Unit)
            }
        }
    }

    /** PB-42 holds for the interval that actually runs: jitter may never take a hidden-idle wait under 60 s. */
    private fun floorFor(visibility: Visibility): Duration =
        if (visibility == Visibility.HiddenIdle) Cadence.HIDDEN_IDLE_MIN else Duration.ZERO

    private fun jittered(period: Duration, jitter: Double): Duration =
        if (jitter == 0.0) period else period * (1.0 + jitter * (random.nextDouble() * 2.0 - 1.0))
}
