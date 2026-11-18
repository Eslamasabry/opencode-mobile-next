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
    private val pinned = "2.3.2"

    private fun decide(
        installed: String? = "2.0.29",
        busy: Boolean = false,
        engineRunning: Boolean = false,
        switchPending: Boolean = false,
        installProblem: Boolean = false,
        attempted: Boolean = false,
        metered: Boolean = false,
    ) =
        OpenCodeAutoUpdateRule.shouldUpdate(
            installed,
            pinned,
            busy,
            engineRunning,
            switchPending,
            installProblem,
            attempted,
            metered = metered,
        )

    @Test
    fun `B-LINUX-078 up to date does nothing, with or without a leading v, and an unknown version does nothing`() {
        assertFalse(decide(installed = "2.3.2"))
        assertFalse(decide(installed = "v2.3.2"))
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

    @Test
    fun `B-LINUX-078 a phone behind the pin waits for an unmetered network, as the agents' update does`() {
        assertFalse(decide(metered = true), "metered or unknown network: wait")
        assertTrue(decide(metered = false), "unmetered: run")
        assertFalse(decide(installed = pinned, metered = false))
    }

    private class Host(
        var version: String?,
        override val pinnedVersion: String,
        override var metered: Boolean = false,
    ) : OpenCodeHostFacts {
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

        override suspend fun updateOpenCode(runtime: String): Boolean {
            runs += runtime
            return true
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

    @Test
    fun `B-LINUX-079 on a metered network the automatic update waits and downloads nothing`() = runTest {
        val jobs = Jobs()
        val updater = OpenCodeAutoUpdater(Host("2.1.1", pinned, metered = true), jobs) { false }
        repeat(3) { updater.changed() }
        assertEquals(emptyList(), jobs.runs)
    }

    @Test
    fun `B-LINUX-079 a network that cannot be read waits like a metered one`() = runTest {
        // The binding reads an unreadable network as metered (the agents' update's own reading); the updater holds.
        val logged = mutableListOf<String>()
        val jobs = Jobs()
        OpenCodeAutoUpdater(Host("2.1.1", pinned, metered = true), jobs, log = { logged += it }) { false }.changed()
        assertEquals(emptyList(), jobs.runs)
        assertTrue(logged.single().contains("metered=true"), logged.toString())
        assertTrue(logged.single().endsWith("hold"), logged.toString())
    }

    @Test
    fun `B-LINUX-079 on an unmetered network the automatic update runs`() = runTest {
        val jobs = Jobs()
        OpenCodeAutoUpdater(Host("2.1.1", pinned, metered = false), jobs) { false }.changed()
        assertEquals(listOf("openCode2"), jobs.runs)
    }

    @Test
    fun `B-LINUX-079 the phone becoming unmetered while the update waits runs it once`() = runTest {
        val host = Host("2.1.1", pinned, metered = true)
        val jobs = Jobs()
        val updater = OpenCodeAutoUpdater(host, jobs) { false }
        updater.changed()
        updater.changed()
        assertEquals(emptyList(), jobs.runs, "the wait does not spend the run's one try")
        host.metered = false
        updater.changed()
        updater.changed()
        host.metered = true
        updater.changed()
        host.metered = false
        updater.changed()
        assertEquals(listOf("openCode2"), jobs.runs)
    }

    @Test
    fun `B-LINUX-079 the Update row runs on a metered network while the automatic update waits`() = runTest {
        // The row (ThisPhoneModel Update -> PhoneSources.updateOpenCode) runs the same job with no network input;
        // only the automatic look reads the network.
        val jobs = Jobs()
        val updater = OpenCodeAutoUpdater(Host("2.1.1", pinned, metered = true), jobs) { false }
        updater.changed()
        assertEquals(emptyList(), jobs.runs)
        assertTrue(jobs.updateOpenCode("openCode2"))
        assertEquals(listOf("openCode2"), jobs.runs)
    }

    // R7: the phone server restarts itself after an OpenCode update.

    private val host = FakeLinuxHost().apply { stage("2.3.2") }
    private val phone = StartTarget(ProfileId("builtin"), password = "secret")

    private fun TestScope.restart() =
        OpenCodeUpdateRestart(FakeJournal(host), host, PhoneServerStarter(host, schedulerClock()))

    @Test
    fun `B-LINUX-245 a new OpenCode restarts even a connected server, once, with the same generation`() = runTest {
        host.running = true
        host.generation = 4
        val result = restart().afterInstall(phone) { true }
        assertEquals(UpdateRestartResult.Updated("2.3.2", restarted = true), result)
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
        assertEquals(UpdateRestartResult.Updated("2.3.2", restarted = false), result)
        assertEquals(0, host.launchCount)
        assertFalse(host.running)
        assertEquals("new", host.binary, "it picks the new version up at its next start")
    }

    @Test
    fun `B-LINUX-245 a server that was not running is not started by an update`() = runTest {
        assertEquals(UpdateRestartResult.Updated("2.3.2", restarted = false), restart().afterInstall(phone) { true })
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
        assertEquals(UpdateRestartResult.Updated("2.3.2", restarted = false), result)
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

    @Test
    fun `DOWNLOAD-CONSENT metered and behind, the updater says it waits and downloads nothing`() = runTest {
        val jobs = Jobs()
        val updater = OpenCodeAutoUpdater(Host("2.1.1", pinned, metered = true), jobs) { false }
        assertFalse(updater.waitingForUnmetered.value)
        updater.changed()
        assertTrue(updater.waitingForUnmetered.value)
        assertEquals(emptyList(), jobs.runs)
    }

    @Test
    fun `DOWNLOAD-CONSENT Download now runs this one update on the metered network, once`() = runTest {
        val host = Host("2.1.1", pinned, metered = true)
        val jobs = Jobs()
        val updater = OpenCodeAutoUpdater(host, jobs) { false }
        updater.changed()
        updater.allowMeteredOnce()
        updater.changed()
        assertEquals(listOf("openCode2"), jobs.runs)
        assertFalse(updater.waitingForUnmetered.value)
        // A later pin (another update) on the same metered network waits again: the consent was spent.
        host.version = "2.1.2"
        val next = Host("2.1.2", "2.3.0", metered = true)
        val again = OpenCodeAutoUpdater(next, jobs) { false }
        again.changed()
        assertEquals(1, jobs.runs.size)
        assertTrue(again.waitingForUnmetered.value)
    }

    @Test
    fun `DOWNLOAD-CONSENT Wait for Wi-Fi holds, and becoming unmetered runs it and clears the wait`() = runTest {
        val host = Host("2.1.1", pinned, metered = true)
        val jobs = Jobs()
        val updater = OpenCodeAutoUpdater(host, jobs) { false }
        repeat(3) { updater.changed() }
        assertEquals(emptyList(), jobs.runs)
        host.metered = false
        updater.changed()
        assertEquals(1, jobs.runs.size)
        assertFalse(updater.waitingForUnmetered.value)
    }

    @Test
    fun `DOWNLOAD-CONSENT a consent given while the update is refused is kept until it starts`() = runTest {
        val host = Host("2.1.1", pinned, metered = true)
        val jobs = object : OpenCodeUpdateJobs {
            override val running = false
            override val failed = false
            var starts = false
            var runs = 0
            override suspend fun updateOpenCode(runtime: String): Boolean { runs++; return starts }
        }
        val updater = OpenCodeAutoUpdater(host, jobs) { false }
        updater.allowMeteredOnce()
        updater.changed()
        jobs.starts = true
        updater.changed()
        assertEquals(2, jobs.runs)
    }
}
