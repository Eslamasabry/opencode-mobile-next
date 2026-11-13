@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitCardKind
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitCards
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitState
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitStatus
import io.github.eslamasabry.oc.feature.phone.presentation.DeveloperOptionsOpened
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneProcess
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessReading
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRole
import io.github.eslamasabry.oc.feature.phone.presentation.RunningModel
import io.github.eslamasabry.oc.engine.runtime.procs.RunningGroupKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Running on this phone (plan 07 F08) and the guided child-process-limit card (plan 11 §7.2; child_limit_card.dart). */
class RunningModelTest {
    private val processes =
        listOf(
            PhoneProcess(pid = 10, parentPid = 1, name = "OpenCode", kind = ProcessKind.OpenCode, role = ProcessRole.ServerRoot),
            PhoneProcess(pid = 11, parentPid = 10, name = "Claude Code", kind = ProcessKind.ClaudeCode, cpuPercent = 41.6),
            PhoneProcess(pid = 2, parentPid = null, name = "OpenCode Mobile", role = ProcessRole.App),
        )
    private val reading = ProcessReading(processes, count = 12, limitLifted = false)
    private val android14 =
        ChildLimitStatus(sdkInt = 34, state = ChildLimitState.Enforced, developerOptionsEnabled = true, canOpenSettings = true)

    private class Rig(scope: TestScope, reading: Outcome<ProcessReading>, limit: FakeChildLimit) {
        val source = FakeProcesses(reading)
        val limit = limit
        val nav = RecordingNav()
        val model = RunningModel(source, limit, nav, scope.backgroundScope, scope.testClock(), foregroundPacer())
    }

    private fun TestScope.rig(limit: FakeChildLimit = FakeChildLimit()): Rig =
        Rig(this, reading.ok(), limit).also {
            it.model.shown(true)
            runCurrent()
        }

    @Test
    fun `B-LINUX-143 protected rows are never offered a stop`() =
        runTest {
            val rows = rig().model.state.value.rows
            assertEquals(listOf(10, 11, 2), rows.map { it.pid })
            assertEquals(listOf(false, true, false), rows.map { it.stoppable })
            assertEquals(42, rows[1].cpuPercent)
        }

    @Test
    fun `B-LINUX-145 Stop all left behind asks once naming each, then stops only those it named`() =
        runTest {
            val stray =
                listOf(
                    PhoneProcess(pid = 30, parentPid = 1, name = "node", leftBehind = true),
                    PhoneProcess(pid = 31, parentPid = 1, name = "esbuild", leftBehind = true),
                    // Left behind but protected: never a target.
                    PhoneProcess(pid = 32, parentPid = 1, name = "OpenCode", leftBehind = true, role = ProcessRole.ServerRoot),
                )
            val rig = Rig(this, reading.copy(processes = processes + stray).ok(), FakeChildLimit())
            rig.model.shown(true)
            runCurrent()
            assertEquals(listOf(30, 31), rig.model.state.value.leftBehind.map { it.pid })
            rig.model.askStopLeftBehind()
            assertEquals(listOf("node", "esbuild"), rig.model.state.value.confirmStopLeftBehind?.map { it.name })
            assertTrue(rig.source.stopped.isEmpty())
            // A new one appears after the question was asked: it is not stopped by this answer.
            rig.source.reading =
                reading.copy(processes = processes + stray + PhoneProcess(pid = 33, parentPid = 1, name = "vite", leftBehind = true)).ok()
            rig.model.confirmStopLeftBehind()
            runCurrent()
            assertEquals(listOf(30, 31), rig.source.stopped)
            assertEquals(2, rig.model.state.value.stoppedCount)
            assertNull(rig.model.state.value.stopping)
            assertFalse(rig.model.state.value.stopFailed)
        }

    @Test
    fun `B-LINUX-145 Stop all left behind is not offered with nothing left behind, and a failed stop says so`() =
        runTest {
            val rig = rig()
            assertTrue(rig.model.state.value.leftBehind.isEmpty())
            rig.model.askStopLeftBehind()
            assertNull(rig.model.state.value.confirmStopLeftBehind)

            val stray = PhoneProcess(pid = 30, parentPid = 1, name = "node", leftBehind = true)
            rig.source.reading = reading.copy(processes = processes + stray).ok()
            rig.model.retry()
            runCurrent()
            rig.source.stopAnswer = refused
            rig.model.askStopLeftBehind()
            rig.model.confirmStopLeftBehind()
            runCurrent()
            assertTrue(rig.model.state.value.stopFailed)
            assertEquals(0, rig.model.state.value.stoppedCount)
            rig.model.dismiss()
            assertNull(rig.model.state.value.stoppedCount)
        }

    @Test
    fun `B-LINUX-145 Android's process warning starts at 28`() =
        runTest {
            val rig = rig()
            assertFalse(rig.model.state.value.nearLimit)
            rig.source.reading = reading.copy(count = 28).ok()
            rig.model.retry()
            runCurrent()
            assertTrue(rig.model.state.value.nearLimit)
            assertEquals(28, rig.model.state.value.count)
            assertEquals(32, rig.model.state.value.cap)
        }

    @Test
    fun `plan 07 F08 the list refreshes every 10 s only while shown, keeping the last reading on a failed refresh`() =
        runTest {
            val rig = rig()
            assertEquals(1, rig.source.reads)
            advanceTimeBy(10.seconds)
            runCurrent()
            assertEquals(2, rig.source.reads)
            rig.source.reading = Outcome.Failed(refusedFailure())
            advanceTimeBy(10.seconds)
            runCurrent()
            assertTrue(rig.model.state.value.refreshFailed)
            assertEquals(3, rig.model.state.value.rows.size)
            rig.model.shown(false)
            advanceTimeBy(60.seconds)
            runCurrent()
            assertEquals(3, rig.source.reads)
        }

    @Test
    fun `plan 07 F08 a first reading that fails says so with Try again`() =
        runTest {
            val rig = Rig(this, Outcome.Failed(refusedFailure()), FakeChildLimit())
            rig.model.shown(true)
            runCurrent()
            assertTrue(rig.model.state.value.loadFailed)
            rig.source.reading = reading.ok()
            rig.model.retry()
            runCurrent()
            assertFalse(rig.model.state.value.loadFailed)
            assertEquals(3, rig.model.state.value.rows.size)
        }

    @Test
    fun `B-LINUX-147 stop asks first, sends the pid once and reads the list again`() =
        runTest {
            val rig = rig()
            rig.model.askStop(10) // protected: never asked
            runCurrent()
            assertNull(rig.model.state.value.confirmStop)
            rig.model.askStop(11)
            runCurrent()
            assertEquals(11, rig.model.state.value.confirmStop?.pid)
            rig.model.confirmStop()
            rig.model.confirmStop()
            runCurrent()
            assertEquals(listOf(11), rig.source.stopped)
            assertEquals(2, rig.source.reads)
            rig.source.stopAnswer = refused
            rig.model.askStop(11)
            runCurrent()
            rig.model.confirmStop()
            runCurrent()
            assertTrue(rig.model.state.value.stopFailed)
        }

    @Test
    fun `plan 11 7-2 the card shows only on Android 14+, while the limit applies, after a kill or frequent queueing`() {
        val kill = Instant.fromEpochSeconds(10_000)
        fun kind(status: ChildLimitStatus, killAt: Instant? = kill, queued: Boolean = false, hiddenAt: Instant? = null) =
            ChildLimitCards.kind(status, recentKill = killAt != null, queuedOften = queued, hiddenAt = hiddenAt, lastKillAt = killAt)
        assertNull(kind(android14.copy(sdkInt = 33)))
        assertNull(kind(android14.copy(sdkInt = null)))
        assertNull(kind(android14.copy(state = ChildLimitState.Lifted)))
        assertNull(kind(android14, killAt = null, queued = false))
        assertEquals(ChildLimitCardKind.OpenSettings, kind(android14, killAt = null, queued = true))
        assertEquals(ChildLimitCardKind.OpenSettings, kind(android14.copy(state = ChildLimitState.Unknown)))
        assertEquals(ChildLimitCardKind.EnableDeveloperOptions, kind(android14.copy(developerOptionsEnabled = false)))
        assertEquals(ChildLimitCardKind.EnableDeveloperOptions, kind(android14.copy(developerOptionsEnabled = null)))
        assertEquals(ChildLimitCardKind.InstructionsOnly, kind(android14.copy(canOpenSettings = false)))
        // Hidden until a newer kill.
        assertNull(kind(android14, hiddenAt = kill + 1.hours))
        assertNull(kind(android14, killAt = null, queued = true, hiddenAt = kill))
        assertEquals(ChildLimitCardKind.OpenSettings, kind(android14, hiddenAt = kill - 1.hours))
    }

    @Test
    fun `plan 11 7-2 the card opens Developer options, falls back to the steps, and Hide remembers the moment`() =
        runTest {
            val limit = FakeChildLimit(status = android14, kill = Instant.fromEpochMilliseconds(0))
            val rig = rig(limit)
            assertEquals(ChildLimitCardKind.OpenSettings, rig.model.state.value.childLimit)
            limit.opens = DeveloperOptionsOpened.None
            rig.model.openChildLimitSettings()
            runCurrent()
            assertEquals(1, limit.openCalls)
            assertEquals(ChildLimitCardKind.InstructionsOnly, rig.model.state.value.childLimit)
            advanceTimeBy(5.seconds)
            rig.model.hideChildLimit()
            runCurrent()
            assertNull(rig.model.state.value.childLimit)
            assertEquals(Instant.fromEpochMilliseconds(5_000), limit.hidden)
            // The setting is read, never written: coming back with it turned off folds the card.
            rig.model.shown(false)
            limit.hidden = null
            limit.status = android14.copy(state = ChildLimitState.Lifted)
            rig.model.shown(true)
            runCurrent()
            assertNull(rig.model.state.value.childLimit)
        }

    @Test
    fun `plan 07 F08 a protected row leads back to This phone`() =
        runTest {
            val rig = rig()
            rig.model.back()
            assertEquals(listOf("back"), rig.nav.calls)
        }

    private fun refusedFailure() = (refused as Outcome.Failed).failure

    @Test
    fun `B-LINUX-083 rows are grouped by what started them, one group per Claude Code conversation, in Flutter's order`() =
        runTest {
            val all =
                listOf(
                    PhoneProcess(pid = 40, parentPid = 1, name = "Terminal", kind = ProcessKind.Terminal, group = RunningGroupKind.Terminal),
                    PhoneProcess(pid = 21, parentPid = 1, name = "Claude Code", group = RunningGroupKind.ClaudeCode, session = "s-b", conversationTitle = "Fix login", cpuPercent = 90.0, heavy = true, memoryBytes = 300L shl 20),
                    PhoneProcess(pid = 20, parentPid = 1, name = "Claude Code", group = RunningGroupKind.ClaudeCode, session = "s-a", memoryBytes = 100L shl 20),
                    PhoneProcess(pid = 22, parentPid = 20, name = "node", group = RunningGroupKind.ClaudeCode, session = "s-a", depth = 1, memoryBytes = 50L shl 20),
                    PhoneProcess(pid = 10, parentPid = 1, name = "OpenCode", group = RunningGroupKind.OpenCode, role = ProcessRole.ServerRoot),
                    PhoneProcess(pid = 30, parentPid = 1, name = "node", leftBehind = true, group = RunningGroupKind.LeftBehind),
                )
            val rig = Rig(this, reading.copy(processes = all).ok(), FakeChildLimit())
            rig.model.shown(true)
            runCurrent()
            val groups = rig.model.state.value.groups
            assertEquals(listOf("leftBehind", "openCode", "claudeCode:s-a", "claudeCode:s-b", "terminal"), groups.map { it.id })
            assertEquals(listOf(20, 22), groups[2].rows.map { it.pid })
            assertEquals(1, groups[2].rows[1].depth)
            assertEquals(150L shl 20, groups[2].memoryBytes)
            assertEquals("Fix login", groups[3].title)
            assertNull(groups[2].title)
            // Heat shows on the heading: the heavy conversation's group is hot and very busy.
            assertTrue(groups[3].hot)
            assertEquals(io.github.eslamasabry.oc.engine.runtime.procs.CpuLevel.VeryBusy, groups[3].cpuWords?.level)
            assertFalse(groups[2].hot)
        }

    @Test
    fun `B-LINUX-083 a reading without the runtime's group still groups by kind, never calling a busy ownerless row left behind`() =
        runTest {
            val stray = PhoneProcess(pid = 50, parentPid = 1, name = "python", leftBehind = true, orphanReason = io.github.eslamasabry.oc.feature.phone.presentation.ProcessOrphanReason.CpuWithoutOwner)
            val gone = PhoneProcess(pid = 51, parentPid = 1, name = "esbuild", leftBehind = true, orphanReason = io.github.eslamasabry.oc.feature.phone.presentation.ProcessOrphanReason.ParentGone)
            val rig = Rig(this, reading.copy(processes = processes + stray + gone).ok(), FakeChildLimit())
            rig.model.shown(true)
            runCurrent()
            val byPid = rig.model.state.value.groups.flatMap { g -> g.rows.map { it.pid to g.kind } }.toMap()
            assertEquals(RunningGroupKind.OpenCode, byPid[10])
            assertEquals(RunningGroupKind.ClaudeCode, byPid[11])
            assertEquals(RunningGroupKind.App, byPid[2])
            assertEquals(RunningGroupKind.Other, byPid[50])
            assertEquals(RunningGroupKind.LeftBehind, byPid[51])
            // Both are still offered to "Stop all left behind".
            assertEquals(listOf(50, 51), rig.model.state.value.leftBehind.map { it.pid })
        }

    @Test
    fun `S7 the summary line says the reading's own count and memory, not the grouped rows'`() =
        runTest {
            val rig = Rig(this, reading.copy(runningCount = 8, runningMemoryBytes = 1046L shl 20).ok(), FakeChildLimit())
            rig.model.shown(true)
            runCurrent()
            val state = rig.model.state.value
            assertEquals(3, state.rows.size)
            assertEquals(8, state.summaryCount)
            assertEquals(1046L shl 20, state.memoryBytes)
        }
}
