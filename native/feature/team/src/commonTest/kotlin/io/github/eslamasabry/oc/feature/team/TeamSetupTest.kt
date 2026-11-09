package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.team.PhoneSetupPorts
import io.github.eslamasabry.oc.engine.team.SetupHealth
import io.github.eslamasabry.oc.engine.team.SetupHost
import io.github.eslamasabry.oc.engine.team.SetupPhase
import io.github.eslamasabry.oc.engine.team.SetupProblem
import io.github.eslamasabry.oc.engine.team.SetupState
import io.github.eslamasabry.oc.engine.team.SetupStep
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamCommandResult
import io.github.eslamasabry.oc.engine.team.TeamConfig
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamHost
import io.github.eslamasabry.oc.engine.team.TeamRead
import io.github.eslamasabry.oc.feature.team.presentation.PhoneOffKind
import io.github.eslamasabry.oc.feature.team.presentation.SetupMark
import io.github.eslamasabry.oc.feature.team.presentation.SetupNote
import io.github.eslamasabry.oc.feature.team.presentation.SetupProof
import io.github.eslamasabry.oc.feature.team.presentation.SetupProofKind
import io.github.eslamasabry.oc.feature.team.presentation.TeamPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamSetupPresent
import io.github.eslamasabry.oc.feature.team.presentation.TeamSetupViewModel
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * "Turn on AI Team on this phone" as the page draws it (Dart `TeamPhoneSetupScreen`): four steps with their own marks,
 * words and times, the one question, a failure in plain kinds, and the phone-off page that joins a check going.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeamSetupTest {
    private val dispatcher = StandardTestDispatcher()
    private val pid = ProfileId("p1")

    @BeforeTest fun main() = Dispatchers.setMain(dispatcher)

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun TestScope.watch(flow: StateFlow<*>) = backgroundScope.launch { flow.collect {} }

    private val redactor = Redactor()
    private val redact: (String) -> Redacted = redactor::details

    private class Phone(var serverRunning: Boolean = false) : PhoneSetupPorts {
        var kept = false
        val calls = mutableListOf<String>()

        override suspend fun hasServer() = true

        override suspend fun replyRunning() = false

        override suspend fun inspect() = SetupHost(serverRunning, 0, false)

        override suspend fun stopServer(): Outcome<Unit> = Outcome.Ok(Unit).also { calls += "stopServer" }

        override suspend fun closeTerminals(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun startEngine(): Outcome<SetupHealth> = Outcome.Ok(SetupHealth(boundary = true, canExecute = true, boundaryTier = "proot"))

        override suspend fun stopEngine() = Unit

        override suspend fun probeEngine(): Outcome<SetupHealth> = Outcome.Ok(SetupHealth(boundary = true, canExecute = true, boundaryTier = "proot"))

        override suspend fun startServer(): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun restoreServer() = true

        override suspend fun saveTeam(): Outcome<Unit> = Outcome.Ok(Unit).also { kept = true }
    }

    private class Host(val phone: Phone) : TeamHost {
        override suspend fun config(profileId: String): TeamConfig? = null

        override suspend fun workspace(profileId: String, config: TeamConfig): Outcome<TeamRead> =
            Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.Unexpected())

        override suspend fun command(
            profileId: String,
            config: TeamConfig,
            command: TeamCommand,
            body: Map<String, Any?>,
        ): Outcome<TeamCommandResult> = Outcome.Ok(TeamCommandResult(accepted = true))

        override suspend fun canTurnOnPhoneTeam(profileId: String) = true

        override fun phoneSetup(profileId: String): PhoneSetupPorts = phone
    }

    private fun viewModel(phone: Phone, nav: FakeNav = FakeNav()) =
        TeamSetupViewModel(TeamRoute.TurnOn(pid), TeamEngine(Host(phone)), nav, FIXED_CLOCK, Redactor())

    @Test
    fun `B-AGENT-294 opening the page starts the run and shows the four steps done with their times`() =
        runTest(dispatcher) {
            val phone = Phone()
            val vm = viewModel(phone)
            watch(vm.state)
            runCurrent()
            val ui = vm.state.value
            assertEquals(SetupPhase.Done, ui.phase)
            assertEquals(SetupStep.entries, ui.steps.map { it.step })
            assertTrue(ui.steps.all { it.mark == SetupMark.Done })
            // Nothing was running, so the stop step says it was not needed rather than inventing a time.
            assertEquals(SetupNote.NotNeeded, ui.steps.first { it.step == SetupStep.Stop }.note)
            assertEquals("proot", ui.protection)
            assertTrue(phone.kept)
        }

    @Test
    fun `B-AGENT-294 the question waits for the person, and a yes goes on to the end`() =
        runTest(dispatcher) {
            val phone = Phone(serverRunning = true)
            val vm = viewModel(phone)
            watch(vm.state)
            runCurrent()
            val asking = vm.state.value
            assertEquals(SetupPhase.Confirming, asking.phase)
            assertEquals(SetupNote.StopWaiting, asking.steps.first { it.step == SetupStep.Stop }.note)
            assertFalse("stopServer" in phone.calls)
            vm.answer(true)
            runCurrent()
            assertEquals(SetupPhase.Done, vm.state.value.phase)
            assertTrue("stopServer" in phone.calls)
        }

    @Test
    fun `B-AGENT-043 a no ends calmly with nothing changed, and Start again asks again`() =
        runTest(dispatcher) {
            val phone = Phone(serverRunning = true)
            val vm = viewModel(phone)
            watch(vm.state)
            runCurrent()
            vm.answer(false)
            runCurrent()
            val ui = vm.state.value
            assertEquals(SetupProblem.Declined, ui.problem)
            assertTrue(ui.calm)
            assertNull(ui.details)
            assertEquals(SetupMark.Failed, ui.steps.first { it.step == SetupStep.Stop }.mark)
            vm.start()
            runCurrent()
            assertEquals(SetupPhase.Confirming, vm.state.value.phase)
        }

    @Test
    fun `B-AGENT-294 Done leaves the page for the team, and a finished run is forgotten`() =
        runTest(dispatcher) {
            val nav = FakeNav()
            val vm = viewModel(Phone(), nav)
            watch(vm.state)
            runCurrent()
            vm.openTeam()
            runCurrent()
            assertEquals(1, nav.backs)
            assertEquals(SetupPhase.Idle, vm.state.value.phase)
        }

    @Test
    fun `B-AGENT-294 a safe-check failure shows its code redacted and the step it stopped at`() {
        val failed =
            SetupState(
                phase = SetupPhase.Failed,
                step = SetupStep.Check,
                failedStep = SetupStep.Check,
                problem = SetupProblem.Unsafe,
                details = "boundary_proof_failed:native.signal-31",
                beganAtMs = mapOf(SetupStep.Reply to 0L, SetupStep.Check to 10L),
                tookMs = mapOf(SetupStep.Reply to 5L, SetupStep.Stop to 0L),
                skipped = setOf(SetupStep.Stop),
            )
        val ui = TeamSetupPresent.present(failed, 100L, redact)
        assertEquals(listOf(SetupMark.Done, SetupMark.Done, SetupMark.Failed, SetupMark.Waiting), ui.steps.map { it.mark })
        assertNotNull(ui.details)
        assertEquals(SetupProofKind.Signal, ui.proof?.kind)
        assertEquals(31, ui.proof?.signal)
    }

    @Test
    fun `B-AGENT-294 a step that works counts its time so far, the reply step says it is waiting`() {
        val going =
            SetupState(
                phase = SetupPhase.Running,
                step = SetupStep.Reply,
                beganAtMs = mapOf(SetupStep.Reply to 1_000L),
            )
        val reply = TeamSetupPresent.present(going, 9_500L, redact).steps.first()
        assertEquals(SetupMark.Working, reply.mark)
        assertEquals(SetupNote.ReplyWaiting, reply.note)
        assertEquals(8_500L, reply.soFarMs)
    }

    @Test
    fun `B-AGENT-294 the safety step names where a check stopped`() {
        assertEquals(SetupProofKind.CheckSignal, SetupProof.parse("boundary_proof_failed:native.io-uring.signal-11")?.kind)
        assertEquals("io-uring", SetupProof.parse("boundary_proof_failed:native.io-uring.signal-11")?.check)
        assertEquals(SetupProofKind.Ubuntu, SetupProof.parse("x; boundary_proof_failed:ubuntu.git-commit.exit-128")?.kind)
        assertEquals(SetupProofKind.Proot, SetupProof.parse("boundary_proof_failed:proot-tier.daemonProcDenied")?.kind)
        assertEquals(SetupProofKind.Fixture, SetupProof.parse("boundary_proof_failed:fixture-changed")?.kind)
        assertEquals(SetupProofKind.NoAnswer, SetupProof.parse("boundary_proof_failed:native.worker.no-answer")?.kind)
        assertNull(SetupProof.parse("timeout:startEngine"))
        assertNull(SetupProof.parse("boundary_proof_failed:../etc"))
    }

    @Test
    fun `B-AGENT-294 the phone-off page says waiting, working with its step, or off`() {
        assertEquals(PhoneOffKind.Off, TeamPresent.phoneOff(SetupState()).kind)
        assertEquals(PhoneOffKind.Waiting, TeamPresent.phoneOff(SetupState(phase = SetupPhase.Confirming)).kind)
        val working =
            TeamPresent.phoneOff(SetupState(phase = SetupPhase.Running, tookMs = mapOf(SetupStep.Reply to 1L, SetupStep.Stop to 0L)))
        assertEquals(PhoneOffKind.Working, working.kind)
        assertEquals(3, working.step)
        assertEquals(4, working.of)
    }
}
