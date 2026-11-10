package io.github.eslamasabry.oc.engine.runtime.server

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Flutter's `inAppAutoUpdateSettle`: the work counts as busy until idle has held for 30 s without a break. */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenCodeIdleSettleTest {
    private class Host : OpenCodeHostFacts {
        override suspend fun refresh() = Unit
        override val installedVersion: String = "2.1.1"
        override val pinnedVersion: String = "2.2.6"
        override val runtime: String = "opencode2"
        override val switchPending: Boolean = false
        override val installProblem: Boolean = false
    }

    private class Jobs : OpenCodeUpdateJobs {
        override var running = false
        override var failed = false
        var runs = 0

        override suspend fun updateOpenCode(runtime: String) {
            runs++
        }
    }

    private class Rig(scope: TestScope) {
        var knownIdle = false
        val jobs = Jobs()
        val wake = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val settle = OpenCodeIdleSettle(scope, { scope.currentTime }, 30.seconds) { wake.tryEmit(Unit) }
        val job = scope.launchOpenCodeAutoUpdate(
            OpenCodeAutoUpdater(Host(), jobs) { settle.busy(knownIdle) },
            Host(),
            wake,
        )

        /** What the binding does on a change event. */
        fun poke() {
            wake.tryEmit(Unit)
        }
    }

    @Test
    fun `not idle long enough means no update`() = runTest {
        val rig = Rig(this)
        rig.knownIdle = true
        rig.poke()
        runCurrent()
        advanceTimeBy(29_000)
        rig.poke()
        runCurrent()
        assertEquals(0, rig.jobs.runs)
        rig.job.cancel()
        rig.settle.cancel()
    }

    @Test
    fun `idle for 30 s updates exactly once, by itself when the time elapses`() = runTest {
        val rig = Rig(this)
        rig.knownIdle = true
        rig.poke()
        runCurrent()
        assertEquals(0, rig.jobs.runs)
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(1, rig.jobs.runs)
        rig.poke()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, rig.jobs.runs)
        rig.job.cancel()
        rig.settle.cancel()
    }

    @Test
    fun `a busy blip at 20 s restarts the wait`() = runTest {
        val rig = Rig(this)
        rig.knownIdle = true
        rig.poke()
        runCurrent()
        advanceTimeBy(20_000)
        rig.knownIdle = false
        rig.poke()
        runCurrent()
        rig.knownIdle = true
        rig.poke()
        runCurrent()
        advanceTimeBy(29_000) // 49 s since the start, 29 s since the blip
        runCurrent()
        assertEquals(0, rig.jobs.runs)
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals(1, rig.jobs.runs)
        rig.job.cancel()
        rig.settle.cancel()
    }
}
