package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** The phone's installed and pinned OpenCode readings, and the events that wake the automatic update. */
class OpenCodeAutoUpdateWiringTest {
    private val pinned = "2.2.6"

    @Test
    fun `installed and pinned versions are present when the host is installed and the profile knows its version`() {
        val facts = OpenCodeVersions.of(RuntimeStatus(installed = true), profileVersion = "2.1.1", openCode2 = true)
        assertEquals("2.1.1", facts.installed)
        assertEquals(pinned, facts.pinned)
    }

    @Test
    fun `no installed version before Linux is installed or when the profile has none, and no pin for OpenCode 1`() {
        assertNull(OpenCodeVersions.of(RuntimeStatus(installed = false), "2.1.1", true).installed)
        assertNull(OpenCodeVersions.of(null, "2.1.1", true).installed)
        assertNull(OpenCodeVersions.of(RuntimeStatus(installed = true), " ", true).installed)
        assertNull(OpenCodeVersions.of(RuntimeStatus(installed = true), "1.4.0", false).pinned)
    }

    private class Host(var version: String?) : OpenCodeHostFacts {
        var refreshes = 0
        override suspend fun refresh() {
            refreshes++
        }

        override val installedVersion: String? get() = version
        override val pinnedVersion: String = "2.2.6"
        override val runtime: String = "opencode2"
        override val switchPending: Boolean = false
        override val installProblem: Boolean = false
    }

    private class Jobs : OpenCodeUpdateJobs {
        override var running = false
        override var failed = false
        var runs = 0
        var starts = true

        override suspend fun updateOpenCode(runtime: String): Boolean {
            runs++
            return starts
        }
    }

    @Test
    fun `updates once at start, then not again on later events, for an outdated version`() = runTest {
        val jobs = Jobs()
        val events = MutableSharedFlow<Unit>()
        val updater = OpenCodeAutoUpdater(Host("2.1.1"), jobs) { false }
        val job = launchOpenCodeAutoUpdate(updater, Host("2.1.1"), events)
        advanceUntilIdle()
        assertEquals(1, jobs.runs)
        events.emit(Unit)
        advanceUntilIdle()
        assertEquals(1, jobs.runs)
        job.cancel()
    }

    @Test
    fun `waits while busy and updates when the work ends`() = runTest {
        val jobs = Jobs()
        var busy = true
        val events = MutableSharedFlow<Unit>()
        val host = Host("2.1.1")
        val job = launchOpenCodeAutoUpdate(OpenCodeAutoUpdater(host, jobs) { busy }, host, events)
        advanceUntilIdle()
        assertEquals(0, jobs.runs)
        busy = false
        events.emit(Unit)
        advanceUntilIdle()
        assertEquals(1, jobs.runs)
        job.cancel()
    }

    @Test
    fun `an up to date server is never touched`() = runTest {
        val jobs = Jobs()
        val host = Host("2.2.6")
        val job = launchOpenCodeAutoUpdate(OpenCodeAutoUpdater(host, jobs) { false }, host, MutableSharedFlow<Unit>())
        advanceUntilIdle()
        assertEquals(0, jobs.runs)
        job.cancel()
    }

    @Test
    fun `an attempt refused before the job started does not use up the attempt for this run`() = runTest {
        val jobs = Jobs()
        jobs.starts = false
        val host = Host("2.1.1")
        val updater = OpenCodeAutoUpdater(host, jobs) { false }
        updater.changed()
        assertEquals(1, jobs.runs)
        jobs.starts = true
        updater.changed()
        assertEquals(2, jobs.runs)
        updater.changed()
        assertEquals(2, jobs.runs)
    }

    @Test
    fun `idle is known from a direct read when the lease reading has no entry yet, and unknown stays busy`() {
        assertEquals(true, PhoneWorkIdle.knownIdle(leaseBusy = null, directBusy = false, agentBusy = false))
        assertEquals(false, PhoneWorkIdle.knownIdle(leaseBusy = null, directBusy = null, agentBusy = false))
        assertEquals(false, PhoneWorkIdle.knownIdle(leaseBusy = null, directBusy = true, agentBusy = false))
        assertEquals(false, PhoneWorkIdle.knownIdle(leaseBusy = true, directBusy = false, agentBusy = false))
        assertEquals(true, PhoneWorkIdle.knownIdle(leaseBusy = false, directBusy = null, agentBusy = false))
        assertEquals(false, PhoneWorkIdle.knownIdle(leaseBusy = false, directBusy = false, agentBusy = true))
    }

    @Test
    fun `each decision says what held it back, once per change`() = runTest {
        val lines = mutableListOf<String>()
        val updater = OpenCodeAutoUpdater(Host("2.1.1"), Jobs(), lines::add) { true }
        updater.changed()
        updater.changed()
        assertEquals(1, lines.size)
        assertEquals(true, "busy=true" in lines.single() && "installed=2.1.1" in lines.single() && "pinned=2.2.6" in lines.single())
    }

    private class LateHost : OpenCodeHostFacts {
        var refreshes = 0
        override suspend fun refresh() {
            refreshes++
        }

        // The saved profile is not loaded yet at the first reads.
        override val installedVersion: String? get() = if (refreshes >= 3) "2.1.1" else null
        override val pinnedVersion: String = "2.2.6"
        override val runtime: String = "opencode2"
        override val switchPending: Boolean = false
        override val installProblem: Boolean = false
    }

    @Test
    fun `an installed version that was not known at start is looked for again, and the update follows`() = runTest {
        val jobs = Jobs()
        val host = LateHost()
        val updater = OpenCodeAutoUpdater(host, jobs) { false }
        val job = launchOpenCodeAutoUpdate(updater, host, openCodeAutoUpdateLooks(host, {}, 30.seconds))
        advanceUntilIdle()
        assertEquals(0, jobs.runs)
        advanceTimeBy(31_000)
        advanceUntilIdle()
        assertEquals(1, jobs.runs)
        job.cancel()
    }

    @Test
    fun `the first line says the updater started, with the versions it sees`() = runTest {
        val lines = mutableListOf<String>()
        val host = Host("2.1.1")
        val job = launchOpenCodeAutoUpdate(OpenCodeAutoUpdater(host, Jobs()) { true }, host, MutableSharedFlow<Unit>(), lines::add)
        runCurrent()
        assertEquals(true, lines.first().startsWith("started; installed=2.1.1 pinned=2.2.6"))
        job.cancel()
    }

    private class HangingHost : OpenCodeHostFacts {
        override suspend fun refresh() = kotlinx.coroutines.awaitCancellation()
        override val installedVersion: String = "2.1.1"
        override val pinnedVersion: String = "2.2.6"
        override val runtime: String = "opencode2"
        override val switchPending: Boolean = false
        override val installProblem: Boolean = false
    }

    @Test
    fun `a status read that never answers does not stop the update`() = runTest {
        val jobs = Jobs()
        val host = HangingHost()
        val job = launchOpenCodeAutoUpdate(OpenCodeAutoUpdater(host, jobs) { false }, host, MutableSharedFlow<Unit>())
        advanceTimeBy(60_000)
        advanceUntilIdle()
        assertEquals(1, jobs.runs)
        job.cancel()
    }
}
