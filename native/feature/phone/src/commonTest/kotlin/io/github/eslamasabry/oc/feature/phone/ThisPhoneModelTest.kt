@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.AboutWait
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneOp
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.RecoveryFacts
import io.github.eslamasabry.oc.feature.phone.presentation.ServerRow
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneConfirm
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneModel
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneNotice
import io.github.eslamasabry.oc.feature.phone.presentation.UpdateRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** This phone's state holder (plan 07 F01; port of this_phone_screen.dart's server, version and row behaviour). */
class ThisPhoneModelTest {
    private val installed = RuntimeStatus(installed = true, phase = RuntimePhase.Ready)
    private val runningFacts =
        PhoneServerFacts(
            status = installed.copy(serverRunning = true),
            probe = ProbeAnswer.Answered,
            installedVersion = "1.4.0",
            pinnedVersion = "1.4.0",
        )

    private class Rig(scope: TestScope, facts: PhoneServerFacts) {
        val server = FakeServer(facts)
        val settings = FakeSettings()
        val nav = RecordingNav()
        val model = ThisPhoneModel(server, settings, nav, scope.backgroundScope, scope.testClock())
    }

    private fun TestScope.rig(facts: PhoneServerFacts): Rig = Rig(this, facts).also { runCurrent() }

    @Test
    fun `plan 07 F01 a running server offers Stop and Restart, each naming the server on this phone`() =
        runTest {
            val rig = rig(runningFacts)
            val ui = rig.model.state.value
            assertEquals(AnswerHeadline.Running, ui.answer.headline)
            assertEquals(listOf(ServerRow.Stop, ServerRow.Restart), ui.serverRows)
            // A stopped server has no Stop row: Start is the answer's one act.
            rig.server.facts.value = PhoneServerFacts(status = installed, stoppedByPerson = true)
            runCurrent()
            assertEquals(emptyList(), rig.model.state.value.serverRows)
            assertEquals(PhoneAct.Start, rig.model.state.value.answer.primary)
        }

    @Test
    fun `B-LINUX-038 Start runs once however often it is tapped, and the page says it is starting`() =
        runTest {
            val rig = rig(PhoneServerFacts(status = installed, stoppedByPerson = true))
            val gate = CompletableDeferred<Unit>()
            rig.server.gate = gate
            rig.model.act(PhoneAct.Start)
            rig.model.act(PhoneAct.Start)
            rig.model.stopServer()
            runCurrent()
            assertEquals(listOf("start"), rig.server.calls)
            assertEquals(PhoneOp.Start, rig.model.state.value.working)
            assertEquals(AnswerHeadline.Starting, rig.model.state.value.answer.headline)
            gate.complete(Unit)
            runCurrent()
            assertNull(rig.model.state.value.working)
        }

    @Test
    fun `plan 11 6-7 Stop says stopping, and a failed stop is said in place`() =
        runTest {
            val rig = rig(runningFacts)
            val gate = CompletableDeferred<Unit>()
            rig.server.gate = gate
            rig.model.stopServer()
            runCurrent()
            assertEquals(AnswerHeadline.Stopping, rig.model.state.value.answer.headline)
            rig.server.answer = refused
            gate.complete(Unit)
            runCurrent()
            assertEquals(ThisPhoneNotice.Failed(PhoneOp.Stop), rig.model.state.value.notice)
            rig.model.dismiss()
            runCurrent()
            assertNull(rig.model.state.value.notice)
        }

    @Test
    fun `plan 07 F01 Restart asks first, and says a reply being written stops`() =
        runTest {
            val rig = rig(runningFacts.copy(replyRunning = true))
            rig.model.askRestart()
            runCurrent()
            assertEquals(ThisPhoneConfirm.Restart(replyRunning = true), rig.model.state.value.confirm)
            rig.model.dismiss()
            runCurrent()
            assertEquals(emptyList(), rig.server.calls)
            rig.model.askRestart()
            runCurrent()
            rig.model.confirm()
            runCurrent()
            assertEquals(listOf("restart"), rig.server.calls)
            assertNull(rig.model.state.value.confirm)
        }

    @Test
    fun `B-LINUX-078 the update row offers the pinned version, ignoring a leading v and an unknown version`() =
        runTest {
            val rig = rig(runningFacts.copy(installedVersion = "v1.3.9", pinnedVersion = "1.4.0"))
            assertEquals(UpdateRow.Available("1.4.0"), rig.model.state.value.version?.update)
            assertEquals("1.3.9", rig.model.state.value.version?.installed)
            rig.server.facts.value = runningFacts.copy(installedVersion = "v1.4.0", pinnedVersion = "1.4.0")
            runCurrent()
            assertEquals(UpdateRow.UpToDate, rig.model.state.value.version?.update)
            rig.server.facts.value = runningFacts.copy(installedVersion = null, pinnedVersion = "1.4.0")
            runCurrent()
            assertNull(rig.model.state.value.version?.update)
            rig.server.facts.value = PhoneServerFacts(status = RuntimeStatus(installed = false))
            runCurrent()
            assertNull(rig.model.state.value.version)
        }

    @Test
    fun `B-LINUX-079 a failed automatic update is left to the manual row, which asks first`() =
        runTest {
            val rig = rig(runningFacts.copy(installedVersion = "1.3.9", updateFailed = true))
            assertEquals(UpdateRow.Failed("1.4.0"), rig.model.state.value.version?.update)
            rig.model.askUpdate()
            runCurrent()
            assertEquals(ThisPhoneConfirm.Update("1.4.0"), rig.model.state.value.confirm)
            rig.model.confirm()
            runCurrent()
            assertEquals(listOf("update"), rig.server.calls)
            rig.server.facts.value = runningFacts.copy(installedVersion = "1.3.9", updateRunning = true)
            runCurrent()
            assertEquals(UpdateRow.Updating, rig.model.state.value.version?.update)
        }

    @Test
    fun `plan 07 F01 an update is refused while a reply is being written`() =
        runTest {
            val rig = rig(runningFacts.copy(installedVersion = "1.3.9", replyRunning = true))
            rig.model.askUpdate()
            runCurrent()
            assertNull(rig.model.state.value.confirm)
            assertEquals(ThisPhoneNotice.UpdateBusy, rig.model.state.value.notice)
            runCurrent()
            assertEquals(emptyList(), rig.server.calls)
        }

    @Test
    fun `B-LINUX-145 the Running on this phone row warns from 28 programs`() =
        runTest {
            val rig = rig(runningFacts.copy(processCount = 27))
            assertEquals(false, rig.model.state.value.running.nearLimit)
            rig.server.facts.value = runningFacts.copy(processCount = 28)
            runCurrent()
            assertTrue(rig.model.state.value.running.nearLimit)
            assertEquals(32, rig.model.state.value.running.cap)
            rig.server.facts.value = runningFacts.copy(processCount = 28, limitLifted = true)
            runCurrent()
            assertEquals(48, rig.model.state.value.running.cap)
        }

    @Test
    fun `plan 07 F01 rows open their pages and the answer's acts go where they name`() =
        runTest {
            val rig = rig(PhoneServerFacts(status = RuntimeStatus(installed = false)))
            rig.model.act(PhoneAct.SetUp)
            rig.model.openRunning()
            rig.model.openKeepRunning()
            rig.model.back()
            assertEquals(listOf("open SetupStart", "open Running", "open KeepRunning", "back"), rig.nav.calls)
            rig.settings.opens = false
            rig.model.act(PhoneAct.OpenAppSettings)
            runCurrent()
            assertEquals(listOf(KeepAliveSetting.AppDetails), rig.settings.opened)
            assertEquals(ThisPhoneNotice.SettingsMissing, rig.model.state.value.notice)
        }

    @Test
    fun `plan 11 6-1 the restart countdown moves each second while the page is shown`() =
        runTest {
            val at = Instant.fromEpochMilliseconds(testScheduler.currentTime) + 42.seconds
            val rig = rig(PhoneServerFacts(status = installed, recovery = RecoveryFacts.Waiting(1, at)))
            rig.model.shown(true)
            runCurrent()
            assertEquals(listOf("refresh"), rig.server.calls)
            assertEquals(AnswerLine.RestartsIn(AboutWait.Seconds(45)), rig.model.state.value.answer.line)
            advanceTimeBy(20.seconds)
            runCurrent()
            assertEquals(AnswerLine.RestartsIn(AboutWait.Seconds(25)), rig.model.state.value.answer.line)
            rig.model.shown(false)
            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(AnswerLine.RestartsIn(AboutWait.Seconds(25)), rig.model.state.value.answer.line)
        }
}
