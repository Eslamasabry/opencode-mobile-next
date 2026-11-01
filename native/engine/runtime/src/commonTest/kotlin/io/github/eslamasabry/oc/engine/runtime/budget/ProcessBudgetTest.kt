package io.github.eslamasabry.oc.engine.runtime.budget

import io.github.eslamasabry.oc.engine.runtime.heat.BatteryReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Port of test/process_budget_test.dart groups "the rules" and "Android's own stops", and test/heat_hold_test.dart. */
class ProcessBudgetTest {
    private fun decide(
        running: Int,
        lifted: Boolean = false,
        job: HeavyJob = HeavyJob.AgentSession,
        thermal: ThermalReading = ThermalReading.Unknown,
        battery: BatteryReading = BatteryReading.Unknown,
        fullSpeed: Boolean = false,
    ): BudgetWait? = ProcessBudget.decide(job, running, lifted, thermal, battery, fullSpeed)

    @Test
    fun `B-LINUX-281 a new agent starts only with room under 32, less a reserve of 4`() {
        assertEquals(32, ProcessBudget.CAP)
        assertEquals(4, ProcessBudget.RESERVE)
        // 32 - 4 reserve - 24 running = 4: an agent session (4) fits.
        assertNull(decide(24))
        assertEquals(BudgetWait.Room, decide(25))
        assertNull(decide(14, job = HeavyJob.AiTeam))
        assertEquals(BudgetWait.Room, decide(23, job = HeavyJob.AiTeam))
        assertNull(decide(25, job = HeavyJob.SetupJob))
        assertEquals(BudgetWait.Room, decide(26, job = HeavyJob.SetupJob))
        assertNull(decide(26, job = HeavyJob.LinuxService))
    }

    @Test
    fun `B-LINUX-281 with Android's limit off the app still keeps its own ceiling of 48`() {
        assertNull(decide(25, lifted = true))
        assertNull(decide(40, lifted = true))
        // Never unlimited: 48 - 4 - 41 = 3 < 4.
        assertEquals(BudgetWait.Room, decide(41, lifted = true))
        assertEquals(BudgetWait.Room, decide(200, lifted = true))
    }

    @Test
    fun `B-LINUX-281 a hot phone holds new work even with room to spare`() {
        val severe = ThermalReading(ThermalStatus.Severe)
        val forecast = ThermalReading(ThermalStatus.Moderate, 0.97)
        assertEquals(BudgetWait.Warm, decide(3, thermal = severe))
        assertEquals(BudgetWait.Warm, decide(3, thermal = forecast))
        assertEquals(BudgetWait.Warm, decide(3, lifted = true, thermal = severe))
        assertNull(decide(3, thermal = ThermalReading(ThermalStatus.Light)))
    }

    @Test
    fun `B-LINUX-281 a low battery that is not charging holds new work, and unknown never blocks`() {
        assertEquals(BudgetWait.Battery, decide(3, battery = BatteryReading(15, charging = false)))
        assertEquals(BudgetWait.Battery, decide(3, battery = BatteryReading(20, charging = false)))
        assertNull(decide(3, battery = BatteryReading(15, charging = true)))
        assertNull(decide(3, battery = BatteryReading(21, charging = false)))
        assertNull(decide(3, battery = BatteryReading(5)))
    }

    @Test
    fun `B-LINUX-281 heat comes first, then the battery, then room`() {
        val low = BatteryReading(5, charging = false)
        assertEquals(BudgetWait.Warm, decide(40, thermal = ThermalReading(ThermalStatus.Severe), battery = low))
        assertEquals(BudgetWait.Battery, decide(40, battery = low))
    }

    @Test
    fun `B-ANDR-129 a forecast near severe or a moderate status holds new heavy work before the pause`() {
        assertEquals(BudgetWait.Warm, decide(4, thermal = ThermalReading(ThermalStatus.None, 0.92)))
        assertEquals(BudgetWait.Warm, decide(4, thermal = ThermalReading(ThermalStatus.Moderate)))
    }

    @Test
    fun `B-ANDR-129 a warm but not hot phone still starts work, only slowed`() {
        assertNull(decide(4, thermal = ThermalReading(ThermalStatus.None, 0.75)))
    }

    @Test
    fun `B-ANDR-129 Always full speed lifts the hold while a severe pause still holds`() {
        assertNull(decide(4, thermal = ThermalReading(ThermalStatus.None, 0.92), fullSpeed = true))
        assertEquals(BudgetWait.Warm, decide(4, thermal = ThermalReading(ThermalStatus.Severe), fullSpeed = true))
    }

    @Test
    fun `B-LINUX-145 Android's process warning starts at 28`() {
        assertEquals(false, ProcessBudget.nearLimit(27))
        assertEquals(true, ProcessBudget.nearLimit(28))
        assertEquals(false, ProcessBudget.nearLimit(null))
    }

    private val now = Instant.parse("2026-11-01T03:00:00Z")

    private fun exit(
        ago: Duration,
        requested: Boolean? = false,
        reason: ServiceExitReason = ServiceExitReason.MemoryOrPhantomKill,
    ) = ServiceExitRecord(lastExitAt = now - ago, lastStopRequested = requested, exitReason = reason)

    @Test
    fun `B-LINUX-283 the last Android kill is the latest unrequested SIGKILL of the last day`() {
        assertEquals(
            now - 3.minutes,
            lastAndroidKill(
                mapOf(
                    "agent-host.p" to exit(3.minutes),
                    "quota" to exit(1.minutes, requested = true),
                    "aiteam" to exit(30.seconds, reason = ServiceExitReason.Exited),
                    "helper" to exit(10.seconds, requested = null),
                ),
                now = now,
            ),
        )
        assertNull(lastAndroidKill(mapOf("agent-host.p" to exit(25.hours)), now = now))
        assertNull(lastAndroidKill(emptyMap(), now = now))
        assertNull(lastAndroidKill(mapOf("x" to ServiceExitRecord(null, false, ServiceExitReason.MemoryOrPhantomKill)), now))
    }

    @Test
    fun `B-LINUX-283 the exit reason words parse and unknown stays unknown`() {
        assertEquals(ServiceExitReason.MemoryOrPhantomKill, ServiceExitReason.parse("memory_or_phantom_kill"))
        assertEquals(ServiceExitReason.Exited, ServiceExitReason.parse("exited"))
        assertEquals(ServiceExitReason.Unknown, ServiceExitReason.parse("137"))
        assertEquals(ServiceExitReason.Unknown, ServiceExitReason.parse(null))
    }
}
