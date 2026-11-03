package io.github.eslamasabry.oc.engine.runtime.recovery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.FakeLinuxHost
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.AppOpenResult
import io.github.eslamasabry.oc.engine.runtime.server.AppOpenStart
import io.github.eslamasabry.oc.engine.runtime.server.DeliberateStop
import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.RuntimeFlags
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget
import io.github.eslamasabry.oc.engine.runtime.server.schedulerClock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** The owner's phone record (`reason=10 subreason=21 (FORCE STOP)` at 2026-09-26 00:06:33): a force stop. */
private val ownerAt = Instant.parse("2026-09-26T00:06:33Z")
private val forceStop = PreviousExit(PreviousExitKind.ForceStop, ownerAt)
private val phone = PhoneProfile(ProfileId("phone"), hasTeam = true)

private class MemoryFlags : RuntimeFlags {
    val values = mutableMapOf<String, Boolean>()

    override suspend fun readBoolean(key: String): Boolean? = values[key]

    override suspend fun writeBoolean(key: String, value: Boolean): Outcome<Unit> {
        values[key] = value
        return Unit.ok()
    }

    override suspend fun remove(key: String): Outcome<Unit> {
        values.remove(key)
        return Unit.ok()
    }
}

/** [FakeLinuxHost] with a hook inside the status read (Flutter `statusGate`). */
private class GatedPort(val host: FakeLinuxHost) : PhoneServerPort by host {
    var duringStatus: (() -> Unit)? = null

    override suspend fun refreshStatus(): Outcome<RuntimeStatus> {
        duringStatus?.also { duringStatus = null }?.invoke()
        return host.refreshStatus()
    }
}

/** Port of test/app_exit_recovery_test.dart group "recovery on start". */
class AppExitRecoveryTest {
    private val host = FakeLinuxHost().apply { running = false }
    private val port = GatedPort(host)
    private val flags = MemoryFlags()
    private val laptop = ProfileId("laptop")
    private val off = mutableSetOf<Pair<ProfileId, RuntimeAutomation>>()
    private val policy = AutomationGate { profile, behavior -> (profile to behavior) !in off }
    private val restartActs = mutableListOf<String>()
    private val reports = mutableListOf<ExitReport>()
    private val revived = mutableListOf<ProfileId>()
    private var reads = 0
    private lateinit var autoStart: StarterAutoStart

    private fun TestScope.recovery(
        facts: LaunchFacts,
        healing: HealingOwner? = null,
    ): AppExitRecovery {
        val clock = schedulerClock()
        val starter = PhoneServerStarter(port, clock)
        autoStart = StarterAutoStart(port, starter) { StartTarget(it, password = "secret") }
        return AppExitRecovery(
            launch = { reads++; facts },
            deliberateStop = DeliberateStop(flags),
            automation = policy,
            autoStart = autoStart,
            server = port,
            clock = clock,
            healing = healing,
            reviveTeam = { revived += it },
            onRestart = { restartActs += "${it.profile.raw}:${it.eventId}"; true },
            onNotableExit = { reports += it },
        )
    }

    private fun scene(active: ProfileId? = laptop) = RecoveryScene(listOf(phone), active)

    private suspend fun TestScope.run(facts: LaunchFacts, active: ProfileId? = laptop): AppExitRecovery =
        recovery(facts).also { it.runOnce(scene(active)) }

    private fun facts(exit: PreviousExit?, vararg services: String) = LaunchFacts(exit, services.toList())

    private fun record(kind: PreviousExitKind) = PreviousExit(kind, ownerAt)

    @Test
    fun `B-LINUX-010 delegates to the shared healing owner without duplicate starts or acts`() = runTest {
        val delegated = mutableListOf<ProfileId>()
        val recovery = recovery(facts(forceStop, "server"), healing = { delegated += it })
        assertEquals(RecoveryOutcome.Delegated, recovery.runOnce(scene(active = phone.id)))
        assertEquals(listOf(phone.id), delegated)
        assertEquals(0, host.launchCount)
        assertEquals(emptyList(), restartActs)
    }

    @Test
    fun `B-LINUX-010 after a force stop with the team running one notice, and the server and the team start again`() = runTest {
        val recovery = run(facts(forceStop, "server", "aiteam"))
        val notice = recovery.notice.value!!
        assertEquals(PreviousExitKind.ForceStop, notice.kind)
        assertEquals(ownerAt, notice.at)
        assertTrue(notice.teamStopped)
        assertTrue(notice.offersKeepAlive)
        assertEquals(phone.id, notice.phoneProfile)
        assertEquals(1, host.launchCount, "one start of the server")
        assertEquals(listOf<LaunchMode>(LaunchMode.Manual), host.launches)
        assertEquals(listOf(phone.id), revived, "then the team")
        assertEquals(PreviousExitKind.ForceStop, reports.single().exit.kind)
        assertEquals(listOf("server", "aiteam"), reports.single().whileRunning)
        assertTrue(recovery.phoneServerRunning.value, "above another server the notice says it runs again")
        recovery.dismiss()
        assertNull(recovery.notice.value)
    }

    @Test
    fun `B-LINUX-010 confirmed recovery files the exit episode only once`() = runTest {
        val recovery = run(facts(forceStop, "server"))
        val micros = ownerAt.epochSeconds * 1_000_000
        assertEquals(listOf("phone:app-exit:$micros"), restartActs)
        assertEquals(RecoveryOutcome.AlreadyRan, recovery.runOnce(scene()))
        assertEquals(1, restartActs.size)
        assertEquals(1, host.launchCount)
    }

    @Test
    fun `B-LINUX-010 runs once per process and reads the launch report once`() = runTest {
        val recovery = recovery(facts(forceStop, "server"))
        recovery.runOnce(scene())
        recovery.dismiss()
        recovery.runOnce(scene())
        assertEquals(1, reads)
        assertNull(recovery.notice.value)
    }

    @Test
    fun `B-LINUX-011 disabled restart policy keeps recovery idle and files no act`() = runTest {
        off += phone.id to RuntimeAutomation.RestartPhoneServer
        val recovery = run(facts(forceStop, "server"))
        assertFalse(recovery.notice.value!!.recoveryAllowed, "never worded as starting again")
        assertEquals(0, host.launchCount)
        assertEquals(emptyList(), host.calls)
        assertEquals(emptyList(), restartActs)
        assertEquals(emptyList(), revived)
    }

    @Test
    fun `B-LINUX-011 revoking policy during the status probe prevents the restart`() = runTest {
        port.duringStatus = { off += phone.id to RuntimeAutomation.RestartPhoneServer }
        val recovery = recovery(facts(forceStop, "server"))
        assertEquals(RecoveryOutcome.NotRestarted, recovery.runOnce(scene()))
        assertEquals(0, host.launchCount)
        assertEquals(emptyList(), restartActs)
    }

    @Test
    fun `B-LINUX-011 nothing was running - no notice and nothing started`() = runTest {
        val recovery = run(facts(forceStop))
        assertNull(recovery.notice.value)
        assertEquals(0, host.launchCount)
        assertEquals(emptyList(), restartActs)
    }

    @Test
    fun `B-LINUX-011 a server the person stopped is nothing to bring back`() = runTest {
        DeliberateStop(flags).mark(phone.id)
        val recovery = run(facts(forceStop, "server", "aiteam"))
        assertNull(recovery.notice.value)
        assertEquals(0, host.launchCount)
        assertEquals(emptyList(), reports, "an idle stop is no problem")
    }

    @Test
    fun `B-LINUX-011 an update or a normal exit says nothing but still brings the server back`() = runTest {
        for (exit in listOf(record(PreviousExitKind.Update), record(PreviousExitKind.Normal), null)) {
            host.running = false
            host.launches.clear()
            val recovery = run(facts(exit, "server"))
            assertNull(recovery.notice.value, "$exit")
            assertEquals(listOf<LaunchMode>(LaunchMode.Manual), host.launches, "$exit")
        }
        assertEquals(emptyList(), reports)
    }

    @Test
    fun `B-LINUX-012 a failed recovery files no restart act`() = runTest {
        host.dies = true
        val recovery = recovery(facts(forceStop, "server"))
        assertEquals(RecoveryOutcome.NotRestarted, recovery.runOnce(scene()))
        assertEquals(1, host.launchCount, "one start, not retried")
        assertEquals(emptyList(), restartActs)
    }

    @Test
    fun `B-LINUX-012 an already running server is not a confirmed restart`() = runTest {
        host.running = true
        val recovery = recovery(facts(forceStop, "server"))
        assertEquals(RecoveryOutcome.NotRestarted, recovery.runOnce(scene()))
        assertTrue(recovery.notice.value!!.recoveryAllowed)
        assertEquals(0, host.launchCount)
        assertEquals(emptyList(), restartActs)
    }

    @Test
    fun `B-LINUX-013 a stop of an idle app is not a problem, a crash or a stop that took the server still is`() = runTest {
        run(facts(forceStop))
        assertEquals(emptyList(), reports)

        run(facts(record(PreviousExitKind.Crash)))
        assertEquals(PreviousExitKind.Crash, reports.single().exit.kind)
        assertEquals(emptyList(), reports.single().whileRunning)

        reports.clear()
        run(facts(forceStop, "server"))
        assertEquals(listOf("server"), reports.single().whileRunning)

        assertFalse(ExitReportRule.reported(PreviousExitKind.ForceStop, wasRunning = false))
        assertTrue(ExitReportRule.reported(PreviousExitKind.LowMemory, wasRunning = false))
        assertFalse(ExitReportRule.reported(PreviousExitKind.Update, wasRunning = true))
        assertFalse(PreviousExitKind.Crash.offersKeepAlive)
    }

    @Test
    fun `B-LINUX-013 opened on the phone server the shell starts it and the recovery only tells`() = runTest {
        val recovery = recovery(facts(record(PreviousExitKind.LowMemory), "server"))
        assertEquals(RecoveryOutcome.ShellStarts, recovery.runOnce(scene(active = phone.id)))
        assertEquals(PreviousExitKind.LowMemory, recovery.notice.value!!.kind)
        assertFalse(recovery.notice.value!!.teamStopped)
        assertEquals(0, host.launchCount)
        assertTrue(autoStart.available, "the automatic start is left for the shell")

        // The shell's app-open start (the existing one path) then brings it back.
        host.generation = 4
        val clock = schedulerClock()
        val opened = AppOpenStart(host, PhoneServerStarter(host, clock), clock, mayAct = { true })
            .run(StartTarget(phone.id, password = "secret"))
        assertEquals(AppOpenResult(starts = 1), opened)
        assertEquals(listOf<LaunchMode>(LaunchMode.ForAppOpen(4)), host.launches)
    }
}
