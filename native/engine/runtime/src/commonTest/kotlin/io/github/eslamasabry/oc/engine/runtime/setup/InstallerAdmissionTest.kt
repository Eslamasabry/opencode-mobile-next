package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The installer admission and ownership monitor, table-driven (port of test/installer_admission_native_test.dart with
 * test/native/installer_admission_harness.kt, and test/installer_monitor_native_test.dart with
 * test/native/installer_monitor_harness.kt). The same scenario names and the same assertions.
 */
class InstallerAdmissionTest {
    private class FixtureLaunch(var live: Boolean) : InstallerLaunch {
        override val alive: Boolean get() = live
    }

    private val unknownOwner = Failure.Runtime(RuntimeProblem.OwnershipUnknown)

    /** The admission harness's BuiltinLinux stand-in, as an [InstallerOwnershipPort]. */
    private class AdmissionFixture(private val scenario: String) : InstallerOwnershipPort {
        private val committedId = "b".repeat(64)
        val cached: FixtureLaunch? = when {
            "cached" in scenario -> FixtureLaunch(false)
            "living" in scenario -> FixtureLaunch(true)
            else -> null
        }
        var ticket: InstallerTicket? = when {
            scenario.startsWith("normal") -> null
            "prepared" in scenario -> InstallerTicket("prepared-" + committedId, operation = InstallerOperation.Install)
            "install" in scenario -> InstallerTicket(committedId, operation = InstallerOperation.Install)
            else -> InstallerTicket(committedId)
        }
        var pendingJournal = "install" in scenario && !scenario.startsWith("normal")
        var clock = 0L
        var proofs = 0
        var clears = 0
        var rollbacks = 0
        var admissions = 0
        var leasesReleased = 0
        var admittedTargets: Set<InstallerTarget>? = null
        var admittedOperation: InstallerOperation? = null
        lateinit var slot: InstallerSlot

        fun build(): InstallerSlot {
            slot = InstallerSlot(this, installed = { true }, elapsedMillis = { clock }, pause = { clock += it },
                componentUpdatesRecovered = "cold" !in scenario)
            cached?.let { slot.select(it, ticket!!.id) }
            return slot
        }

        override fun readTicket(): InstallerTicket? = ticket

        override fun clearTicket(): Boolean {
            if ("clear-failed" in scenario) return false
            ticket = null
            clears++
            return true
        }

        override fun saveTicket(ticket: InstallerTicket): Outcome<Unit> = fail("admission never saves a ticket")

        override fun ownedMembers(ticket: InstallerTicket, live: InstallerLaunch?, launchId: String?): Outcome<List<Int>> {
            if (live != null) {
                check(slot.process === live && slot.launchId == launchId && ticket.id == launchId)
            }
            proofs++
            return when {
                "orphan" in scenario -> listOf(22).ok()
                "unknown" in scenario || "uncertain" in scenario -> Failure.Runtime(RuntimeProblem.OwnershipUnknown).failed()
                else -> emptyList<Int>().ok()
            }
        }

        override fun drain(ticket: InstallerTicket, live: InstallerLaunch?, launchId: String?): Outcome<Unit> =
            fail("admission must never signal a process")

        override fun releaseLease(launch: InstallerLaunch) {
            check(!launch.alive)
            leasesReleased++
        }

        override fun recoverColdComponentUpdates(): Outcome<Unit> {
            check(slot.process == null)
            // Durable stale tickets must have been reclaimed first; cold cleanup cannot hide a missing reclaim.
            check(ticket == null)
            if (pendingJournal) {
                pendingJournal = false
                rollbacks++
            }
            return Unit.ok()
        }

        override fun launch(
            script: String,
            targets: Set<InstallerTarget>,
            operation: InstallerOperation,
            agentUser: Boolean,
        ): Outcome<LaunchedInstaller> {
            check(ticket == null && slot.process == null && slot.componentUpdatesRecovered)
            check(!pendingJournal) { "pending INSTALL journal must recover before another guest command" }
            admissions++
            admittedTargets = targets
            admittedOperation = operation
            return LaunchedInstaller(FixtureLaunch(true), "launched", InstallerTicket("launched")).ok()
        }
    }

    private val admissionScenarios = listOf(
        "check-cached-dead", "check-durable-dead", "install-cached-dead", "install-durable-dead",
        "check-living-owner", "install-living-owner", "check-orphan", "install-orphan", "check-unknown",
        "install-unknown", "check-uncertain-absence", "install-uncertain-absence", "install-prepared-dead",
        "install-clear-failed", "install-cached-clear-failed", "cold-durable-dead", "cold-install-durable-dead",
        "normal-check", "normal-install",
    )

    private suspend fun admission(scenario: String) {
        val linux = AdmissionFixture(scenario)
        val slot = linux.build()
        if (scenario.startsWith("cold")) {
            assertEquals(false, (slot.reclaimDeadInstaller() as Outcome.Ok).value, scenario)
            assertTrue(linux.ticket != null && linux.proofs == 0 && linux.clears == 0, scenario)
            assertEquals("install" in scenario, linux.pendingJournal, scenario)
            return
        }
        val admitted = if (scenario.startsWith("check") || scenario == "normal-check") {
            slot.admitCheck("fixed-check", 1_000, linux.clock, agentUser = true)
        } else {
            slot.startInstaller("fixed-install", setOf(InstallerTarget.Claude), InstallerOperation.Install, agentUser = true)
        }
        val refusal = listOf("living", "orphan", "unknown", "uncertain", "clear-failed").any { it in scenario }
        if (refusal) {
            assertTrue(admitted is Outcome.Failed, "unsafe installer owner was admitted: $scenario")
            assertTrue(linux.ticket != null && linux.clears == 0 && linux.admissions == 0, scenario)
            assertTrue(linux.leasesReleased == 0 && linux.rollbacks == 0, scenario)
            if ("cached" in scenario) assertSame(linux.cached, slot.process, scenario)
            if ("unknown" in scenario || "uncertain" in scenario) assertEquals(unknownOwner, (admitted as Outcome.Failed).failure, scenario)
            if ("living" in scenario && scenario.startsWith("check")) {
                assertEquals(Failure.Runtime(RuntimeProblem.SlotBusy), (admitted as Outcome.Failed).failure, scenario)
                assertTrue(linux.clock >= 1_000, "a check queues for its whole window before it is refused")
            }
        } else {
            val launch = (admitted as? Outcome.Ok)?.value ?: fail("$scenario was refused: $admitted")
            assertTrue(launch.alive, scenario)
            assertTrue(linux.ticket == null && linux.admissions == 1, scenario)
            if (!scenario.startsWith("normal")) {
                assertTrue(linux.clears == 1 && linux.proofs == 2, "dead ticket was not twice proved quiescent: $scenario")
                assertEquals(if ("cached" in scenario) 1 else 0, linux.leasesReleased, scenario)
                assertEquals(if (scenario.startsWith("install")) 1 else 0, linux.rollbacks, scenario)
            }
            val check = "check" in scenario
            assertEquals(if (check) InstallerOperation.Check else InstallerOperation.Install, linux.admittedOperation)
            assertEquals(
                if (check) InstallerTarget.entries.toSet() else setOf(InstallerTarget.Claude),
                linux.admittedTargets,
                scenario,
            )
            assertSame(launch, slot.process)
        }
    }

    @Test
    fun `B-LINUX-080 native installer admission scenarios decide admit or refuse like the harness`() = runTest {
        for (scenario in admissionScenarios) admission(scenario)
    }

    @Test
    fun `B-LINUX-080 a living installer owner refuses both a queued check and an install without proofs`() = runTest {
        admission("check-living-owner")
        admission("install-living-owner")
    }

    @Test
    fun `B-LINUX-080 a dead cached or durable owner is twice proved quiescent, cleared and its journal rolled back`() =
        runTest {
            for (scenario in listOf("check-cached-dead", "check-durable-dead", "install-cached-dead",
                "install-durable-dead", "install-prepared-dead")) admission(scenario)
        }

    @Test
    fun `B-LINUX-080 orphans, unknown writers, uncertain absence and a refused clear never admit`() = runTest {
        for (scenario in listOf("check-orphan", "install-orphan", "check-unknown", "install-unknown",
            "check-uncertain-absence", "install-uncertain-absence", "install-clear-failed",
            "install-cached-clear-failed")) admission(scenario)
    }

    @Test
    fun `B-LINUX-080 a cold process keeps its recovery authority and does not reclaim`() = runTest {
        admission("cold-durable-dead")
        admission("cold-install-durable-dead")
    }

    @Test
    fun `B-LINUX-080 the reclaim rechecks owner and ticket around each of its two proofs`() {
        val ticket = InstallerTicket("t")
        var reads = 0
        var proofs = 0
        // The ticket changes after the first proof: nothing is cleared.
        val changed = InstallerAdmission.reclaim(
            InstallerAdmission.Claim(ticket, ownerLive = false),
            ownerCurrent = { true },
            readTicket = { if (++reads <= 1) ticket else InstallerTicket("other") },
            quiescent = { proofs++; true.ok() },
            clearTicket = { fail("a changed ticket was cleared") },
        )
        assertEquals(false, (changed as Outcome.Ok).value)
        assertEquals(1, proofs)
        assertEquals(false, (InstallerAdmission.reclaim(InstallerAdmission.Claim(ticket, ownerLive = true), { true },
            { ticket }, { fail("a live owner was probed") }, { fail("a live owner was cleared") }) as Outcome.Ok).value)
        assertEquals(false, (InstallerAdmission.reclaim(InstallerAdmission.Claim(null, ownerLive = false), { true },
            { null }, { fail("no ticket was probed") }, { fail("no ticket was cleared") }) as Outcome.Ok).value)
    }

    // ---- the ownership monitor --------------------------------------------------------------------------------

    /** The monitor harness's BuiltinLinux stand-in. */
    private class MonitorFixture(private val scenario: String) : InstallerOwnershipPort {
        val observedFailure = Failure.Runtime(RuntimeProblem.OwnershipUnknown)
        val first = FixtureLaunch(true)
        val second = FixtureLaunch(true)
        var ticket: InstallerTicket? = null
        var drains = 0
        var clears = 0
        var releases = 0
        val slot = InstallerSlot(this, installed = { true }, elapsedMillis = { 0 }, pause = { delay(it) },
            componentUpdatesRecovered = true)

        fun select(launch: FixtureLaunch, nonce: String) {
            slot.select(launch, nonce)
            ticket = InstallerTicket(nonce)
        }

        override fun readTicket(): InstallerTicket? = ticket

        override fun clearTicket(): Boolean {
            ticket = null
            clears++
            return true
        }

        override fun saveTicket(ticket: InstallerTicket): Outcome<Unit> {
            this.ticket = ticket
            return Unit.ok()
        }

        override fun ownedMembers(ticket: InstallerTicket, live: InstallerLaunch?, launchId: String?): Outcome<List<Int>> {
            check(live === first && ticket.id == "first" && launchId == "first")
            if (scenario == "stale-monitor-new-launch") {
                // Deterministic replacement at the observation/failure boundary: the monitor must recheck the exact
                // launch before recording failure.
                first.live = false
                select(second, "second")
            }
            return observedFailure.failed()
        }

        override fun drain(ticket: InstallerTicket, live: InstallerLaunch?, launchId: String?): Outcome<Unit> {
            check(slot.process === live && slot.launchId == launchId && this.ticket == ticket)
            (live as FixtureLaunch).live = false
            drains++
            return Unit.ok()
        }

        override fun releaseLease(launch: InstallerLaunch) {
            check(launch === first || launch === second)
            releases++
        }

        override fun recoverColdComponentUpdates(): Outcome<Unit> = fail("not part of the monitor")

        override fun launch(
            script: String,
            targets: Set<InstallerTarget>,
            operation: InstallerOperation,
            agentUser: Boolean,
        ): Outcome<LaunchedInstaller> = fail("not part of the monitor")
    }

    private suspend fun monitor(scenario: String) {
        val linux = MonitorFixture(scenario)
        val slot = linux.slot
        linux.select(linux.first, "first")
        slot.monitor(linux.first, "first", linux.ticket!!)
        if (scenario == "stale-monitor-new-launch") {
            assertSame(linux.second, slot.process)
            assertEquals("second", slot.launchId)
            assertTrue(linux.second.alive)
            assertNull(slot.monitorFailure)
            assertTrue(linux.drains == 0 && linux.clears == 0, "a replaced launch was blamed")
            linux.second.live = false
            assertEquals(InstallerFinish.Done, slot.finish(linux.second))
            assertTrue(slot.process == null && linux.ticket == null && linux.clears == 1)
            return
        }
        assertFalse(linux.first.alive, "the monitor drained the launch it stopped")
        val finished = slot.finish(linux.first)
        val interrupted = finished as? InstallerFinish.Interrupted
            ?: fail("ownership monitor termination was reported as successful setup: $finished")
        assertSame(linux.observedFailure, interrupted.cause, "monitor failure cause was lost")
        assertTrue(slot.process == null && slot.launchId == null && linux.ticket == null, "failed launch blocked a retry")
        assertTrue(slot.monitorFailure == null && linux.clears == 1 && linux.drains == 2 && linux.releases == 1)
        assertFalse(slot.componentUpdatesRecovered, "retry skipped interrupted component recovery")
        if (scenario == "retry-same-runtime") {
            linux.select(linux.second, "second")
            linux.second.live = false
            assertEquals(InstallerFinish.Done, slot.finish(linux.second))
            assertTrue(slot.process == null && linux.ticket == null && linux.clears == 2 && linux.releases == 2)
        }
    }

    @Test
    fun `B-LINUX-080 native installer monitor - a failure that stays stops the install and finish reports it`() =
        runTest { monitor("monitor-failure-zero-exit") }

    @Test
    fun `B-LINUX-080 native installer monitor - a stopped install frees the slot for a retry on the same runtime`() =
        runTest { monitor("retry-same-runtime") }

    @Test
    fun `B-LINUX-080 native installer monitor - a stale monitor never blames a new launch`() =
        runTest { monitor("stale-monitor-new-launch") }

    @Test
    fun `B-LINUX-080 one failed ownership read is a race, not a stop`() {
        val strikes = InstallerStrikes()
        repeat(InstallerStrikes.MONITOR_ATTEMPTS - 1) { assertFalse(strikes.failed()) }
        strikes.passed()
        assertEquals(0, strikes.count)
        repeat(InstallerStrikes.MONITOR_ATTEMPTS - 1) { assertFalse(strikes.failed()) }
        assertTrue(strikes.failed())
    }
}
