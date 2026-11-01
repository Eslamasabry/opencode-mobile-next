package io.github.eslamasabry.oc.core.testing

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.pace.DefaultPacer
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.common.pace.timerTableProblems
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestDispatcher
import kotlin.random.Random
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Every dispatcher is the test dispatcher, serial() included. */
public class TestDispatchers(private val dispatcher: TestDispatcher) : AppDispatchers {
    override val main: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher

    override fun serial(name: String): CoroutineDispatcher = dispatcher
}

/**
 * A [Pacer] on virtual time (plan 13 §11.2): the real DefaultPacer over a visibility the test sets, with a seeded
 * jitter. [started] lists the specs collected so far, so a test can prove a job is (not) armed.
 */
public class TestPacer(initial: Visibility = Visibility.Foreground, seed: Int = 0) : Pacer {
    public val visibility: MutableStateFlow<Visibility> = MutableStateFlow(initial)
    public val started: MutableList<String> = mutableListOf()
    private val real = DefaultPacer(visibility, Random(seed))

    override fun ticks(spec: TimerSpec): Flow<Unit> = real.ticks(spec).onStart { started += spec.name }
}

/** A clock the test moves by hand. */
public class TestClock(public var now: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000)) : Clock {
    override fun now(): Instant = now

    public fun advance(by: Duration) {
        now += by
    }
}

/** The per-module `PB-42 timer table` assertion (plan 13 §11.2 rule 3). */
public fun assertTimerTable(all: List<TimerSpec>) {
    val problems = timerTableProblems(all)
    assertTrue(problems.isEmpty(), "PB-42 timer table: ${problems.joinToString("; ")}")
}
