package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        override suspend fun updateOpenCode(runtime: String) {
            runs++
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
}
