package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.FakeJournal
import io.github.eslamasabry.oc.engine.runtime.FakeLinuxHost
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Port of test/in_app_auto_update_test.dart, plus the restart-after-update rule R7 (plan 11 §6.4). */
class OpenCodeUpdateTest {
    private val pinned = "2.2.5"

    private fun decide(
        installed: String? = "2.0.29",
        busy: Boolean = false,
        engineRunning: Boolean = false,
        switchPending: Boolean = false,
        installProblem: Boolean = false,
        attempted: Boolean = false,
    ) = OpenCodeAutoUpdateRule.shouldUpdate(installed, pinned, busy, engineRunning, switchPending, installProblem, attempted)

    @Test
    fun `B-LINUX-078 up to date does nothing, with or without a leading v, and an unknown version does nothing`() {
        assertFalse(decide(installed = "2.2.5"))
        assertFalse(decide(installed = "v2.2.5"))
        assertFalse(decide(installed = null))
        assertFalse(decide(installed = " "))
    }

    @Test
    fun `B-LINUX-078 behind and idle updates, never under work, a job, a half switch, a failed install, or twice`() {
        assertTrue(decide())
        assertFalse(decide(busy = true))
        assertFalse(decide(engineRunning = true))
        assertFalse(decide(switchPending = true))
        assertFalse(decide(installProblem = true))
        assertFalse(decide(attempted = true))
    }

    private class Host(var version: String?, override val pinnedVersion: String) : OpenCodeHostFacts {
        var refreshes = 0
        override suspend fun refresh() {
            refreshes++
        }

        override val installedVersion: String? get() = version
        override val runtime: String = "openCode2"
        override val switchPending: Boolean = false
        override val installProblem: Boolean = false
    }

    private class Jobs : OpenCodeUpdateJobs {
        override var running = false
        override var failed = false
        val runs = mutableListOf<String>()

        override suspend fun updateOpenCode(runtime: String) {
            runs += runtime
        }
    }

    @Test
    fun `B-LINUX-079 asks the engine for the current runtime when behind and idle`() = runTest {
        val jobs = Jobs()
        OpenCodeAutoUpdater(Host("2.0.29", pinned), jobs) { false }.changed()
        assertEquals(listOf("openCode2"), jobs.runs)
    }

    @Test
    fun `B-LINUX-079 waits while work is busy, then runs once when work ends`() = runTest {
        var busy = true
        val jobs = Jobs()
        val updater = OpenCodeAutoUpdater(Host("2.0.29", pinned), jobs) { busy }
        updater.changed()
        assertEquals(emptyList(), jobs.runs)
        busy = false
        updater.changed()
        assertEquals(1, jobs.runs.size)
        updater.changed()
        updater.changed()
        assertEquals(1, jobs.runs.size)
    }

    @Test
    fun `B-LINUX-079 does nothing when already pinned and does not retry after a failure`() = runTest {
        val jobs = Jobs()
        OpenCodeAutoUpdater(Host("v$pinned", pinned), jobs) { false }.changed()
        assertEquals(emptyList(), jobs.runs)
        val updater = OpenCodeAutoUpdater(Host("2.0.29", pinned), jobs) { false }
        updater.changed()
        jobs.failed = true
        updater.changed()
        jobs.failed = false
        updater.changed()
        assertEquals(1, jobs.runs.size)
    }

    // R7: the phone server restarts itself after an OpenCode update.

    private val host = FakeLinuxHost().apply { stage("2.2.5") }
    private val phone = StartTarget(ProfileId("builtin"), password = "secret")

    private fun TestScope.restart() =
        OpenCodeUpdateRestart(FakeJournal(host), host, PhoneServerStarter(host, schedulerClock()))

    @Test
    fun `B-LINUX-245 a new OpenCode restarts even a connected server, once, with the same generation`() = runTest {
        host.running = true
        host.generation = 4
        val result = restart().afterInstall(phone) { true }
        assertEquals(UpdateRestartResult.Updated("2.2.5", restarted = true), result)
        assertEquals(listOf<LaunchMode>(LaunchMode.AfterUpdate(4)), host.launches)
        assertEquals("new", host.binary)
        assertTrue(host.running)
        assertEquals(emptyList(), host.confirmed, "an update restart spends no recovery budget")
    }

    @Test
    fun `B-LINUX-079 the update restart waits while the phone is working`() = runTest {
        host.running = true
        assertEquals(UpdateRestartResult.NotIdle, restart().afterInstall(phone) { false })
        assertEquals(emptyList(), host.journal)
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-038 an update never restarts a server the person stopped`() = runTest {
        host.running = true
        host.personStops()
        val result = restart().afterInstall(phone) { true }
        assertEquals(UpdateRestartResult.Updated("2.2.5", restarted = false), result)
        assertEquals(0, host.launchCount)
        assertFalse(host.running)
        assertEquals("new", host.binary, "it picks the new version up at its next start")
    }

    @Test
    fun `B-LINUX-245 a server that was not running is not started by an update`() = runTest {
        assertEquals(UpdateRestartResult.Updated("2.2.5", restarted = false), restart().afterInstall(phone) { true })
        assertEquals(0, host.launchCount)
    }

    @Test
    fun `B-LINUX-245 a new version that does not come up is rolled back and the old one started`() = runTest {
        host.running = true
        host.newBinaryBroken = true
        val result = restart().afterInstall(phone) { true }
        assertIs<UpdateRestartResult.RolledBack>(result)
        assertEquals(StartProblem.Exited, result.failure.problem)
        assertTrue(result.oldRestarted)
        assertEquals(listOf("activate", "rollBack"), host.journal)
        assertEquals("old", host.binary)
        assertTrue(host.running)
        assertEquals(2, host.launchCount)
    }

    @Test
    fun `B-LINUX-245 a Stop during the update restart wins and keeps the new version`() = runTest {
        host.running = true
        host.duringPasswordWrite = { host.personStops() }
        val result = restart().afterInstall(phone) { true }
        assertEquals(UpdateRestartResult.Updated("2.2.5", restarted = false), result)
        assertEquals(listOf("activate"), host.journal)
        assertFalse(host.running)
    }

    @Test
    fun `B-LINUX-245 a staged binary that cannot be committed leaves the old one running`() = runTest {
        host.running = true
        host.activationRefused = true
        assertEquals(UpdateRestartResult.NotActivated, restart().afterInstall(phone) { true })
        assertEquals(0, host.launchCount)
        assertEquals("old", host.binary)
    }
}
