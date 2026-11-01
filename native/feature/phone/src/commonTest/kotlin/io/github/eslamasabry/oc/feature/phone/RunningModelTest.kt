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
}
