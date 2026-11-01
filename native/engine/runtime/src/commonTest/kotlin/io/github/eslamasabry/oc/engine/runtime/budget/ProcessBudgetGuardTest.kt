@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.engine.runtime.budget

import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.runtime.FakeLinuxHost
import io.github.eslamasabry.oc.engine.runtime.heat.BatteryReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Port of test/process_budget_test.dart group "the guard", over the fake Linux host. */
class ProcessBudgetGuardTest {
    private val host = FakeLinuxHost().apply { processCount = 10; runningAgents = 2 }
    private val clock = TestClock()

    private fun TestScope.guard(scope: CoroutineScope = backgroundScope, pollEvery: kotlin.time.Duration = 1.hours) =
        ProcessBudgetGuard(readings = host, fullSpeed = { false }, clock = clock, scope = scope, pollEvery = pollEvery)

    @Test
    fun `B-LINUX-282 held work waits in a queue and starts once room frees`() = runTest {
        val g = guard()
        host.processCount = 30
        val admitted = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        assertFalse(admitted.isCompleted)
        assertEquals(BudgetWait.Room, g.state.value.wait)
        assertEquals(HeavyJob.AgentSession, g.state.value.waiting.single().job)
        assertEquals(30, g.state.value.lastCount)
        assertEquals(2, g.state.value.runningAgents)
        g.check()
        assertFalse(admitted.isCompleted, "still no room")
        host.processCount = 12
        g.check()
        assertEquals(BudgetAdmission.Admitted, admitted.await())
        assertNull(g.state.value.wait)
    }

    @Test
    fun `B-LINUX-282 heat queues work below the cap and it starts once cool`() = runTest {
        val g = guard()
        host.thermal = ThermalReading(ThermalStatus.Severe)
        val admitted = async { g.admit(HeavyJob.AiTeam) }
        runCurrent()
        assertEquals(BudgetWait.Warm, g.state.value.wait)
        host.thermal = ThermalReading(ThermalStatus.Light)
        g.check()
        assertEquals(BudgetAdmission.Admitted, admitted.await())
    }

    @Test
    fun `B-LINUX-282 low battery queues work and charging lets it start`() = runTest {
        val g = guard()
        host.battery = BatteryReading(9, charging = false)
        val admitted = async { g.admit(HeavyJob.SetupJob) }
        runCurrent()
        assertEquals(BudgetWait.Battery, g.state.value.wait)
        host.battery = BatteryReading(9, charging = true)
        g.check()
        assertEquals(BudgetAdmission.Admitted, admitted.await())
        assertNull(g.state.value.wait)
    }

    @Test
    fun `B-LINUX-281 the cap still applies with Android's limit turned off`() = runTest {
        val g = guard()
        host.limitLifted = true
        host.processCount = 30
        assertEquals(BudgetAdmission.Admitted, g.admit(HeavyJob.AgentSession)) // 30 + 4 fits under 48 - 4
        assertEquals(48, g.state.value.lastCeiling)
        host.processCount = 42
        val admitted = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        assertEquals(BudgetWait.Room, g.state.value.wait)
        host.processCount = 20
        g.check()
        assertEquals(BudgetAdmission.Admitted, admitted.await())
    }

    @Test
    fun `B-LINUX-282 work admitted moments ago counts before it shows, and only for a while`() = runTest {
        val g = guard()
        host.processCount = 17
        g.admit(HeavyJob.AgentSession) // 17 + 4 = 21
        g.admit(HeavyJob.AgentSession) // 21 + 4 = 25
        val third = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        assertEquals(BudgetWait.Room, g.state.value.wait, "25 + 4 > 28")
        clock.advance(46.seconds) // the reservations expire; the reading is the truth again
        g.check()
        assertEquals(BudgetAdmission.Admitted, third.await())
    }

    @Test
    fun `B-LINUX-282 a cancelled wait ends cleanly as cancelled`() = runTest {
        val g = guard()
        host.processCount = 31
        val admitted = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        assertEquals(1, g.state.value.waiting.size)
        admitted.cancel()
        runCurrent()
        assertTrue(admitted.isCancelled)
        assertTrue(g.state.value.waiting.isEmpty())
    }

    @Test
    fun `B-LINUX-282 closing the guard ends every wait as cancelled`() = runTest {
        val g = guard()
        host.processCount = 31
        val admitted = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        g.close()
        assertEquals(BudgetAdmission.Cancelled, admitted.await())
        assertTrue(g.state.value.waiting.isEmpty())
    }

    @Test
    fun `B-LINUX-281 a phone it cannot read never blocks work`() = runTest {
        val g = guard()
        host.processCount = null
        assertEquals(BudgetAdmission.Admitted, g.admit(HeavyJob.AgentSession))
        assertTrue(g.state.value.waiting.isEmpty())
        val off = ProcessBudgetGuard(host, { false }, clock, backgroundScope, enabled = false)
        host.processCount = 99
        val readsBefore = host.reads
        assertEquals(BudgetAdmission.Admitted, off.admit(HeavyJob.AgentSession))
        assertEquals(readsBefore, host.reads, "a guard that is off reads nothing")
    }

    @Test
    fun `B-LINUX-282 waiting for room twice within an hour reads as often`() = runTest {
        val g = guard()
        host.processCount = 31
        val first = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        assertFalse(g.queuesOften())
        val second = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        assertTrue(g.queuesOften())
        clock.advance(61.minutes)
        assertFalse(g.queuesOften())
        host.processCount = 0
        g.check()
        assertEquals(BudgetAdmission.Admitted, first.await())
        assertEquals(BudgetAdmission.Admitted, second.await())
    }

    @Test
    fun `B-LINUX-282 oldest first, a younger waiting job never jumps a waiting older one`() = runTest {
        val g = guard()
        host.processCount = 31
        val older = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        val younger = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        assertEquals(2, g.state.value.waiting.size)
        host.processCount = 24 // room for exactly one agent session
        g.check()
        assertEquals(BudgetAdmission.Admitted, older.await())
        assertFalse(younger.isCompleted)
        assertEquals(1, g.state.value.waiting.size)
        g.close()
        assertEquals(BudgetAdmission.Cancelled, younger.await())
    }

    @Test
    fun `B-LINUX-282 the guard looks again by itself while work waits`() = runTest {
        val g = guard(pollEvery = 5.seconds)
        host.processCount = 31
        val admitted = async { g.admit(HeavyJob.AgentSession) }
        runCurrent()
        host.processCount = 3
        testScheduler.advanceTimeBy(6.seconds)
        runCurrent()
        assertEquals(BudgetAdmission.Admitted, admitted.await())
    }
}
