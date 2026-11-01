package io.github.eslamasabry.oc.feature.phone

import io.github.eslamasabry.oc.engine.runtime.budget.ServiceExitReason
import io.github.eslamasabry.oc.engine.runtime.host.RestorePhase
import io.github.eslamasabry.oc.engine.runtime.host.RestoreReason
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.engine.runtime.server.StartProblem
import io.github.eslamasabry.oc.feature.phone.presentation.AboutWait
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerTone
import io.github.eslamasabry.oc.feature.phone.presentation.GaveUpWhy
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerState
import io.github.eslamasabry.oc.feature.phone.presentation.RecoveryFacts
import io.github.eslamasabry.oc.feature.phone.presentation.StoppedBy
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneAnswers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Plan 11 §6.1's derivation and the page's one answer (port of this_phone_answer.dart `_answerNow`). */
class PhoneServerStateTest {
    private val installed = RuntimeStatus(installed = true, phase = RuntimePhase.Ready)
    private val running = installed.copy(serverRunning = true, serverPort = 4097)

    private fun derive(facts: PhoneServerFacts) = PhoneServerState.of(facts)

    @Test
    fun `B-LINUX-250 a server that stopped by itself is shown as stopped`() {
        val facts = PhoneServerFacts(status = installed, lastExit = ServiceExitReason.Exited)
        assertEquals(PhoneServerState.Stopped(StoppedBy.Itself), derive(facts))
        val answer = ThisPhoneAnswers.of(derive(facts), facts)
        assertEquals(AnswerHeadline.StoppedItself, answer.headline)
        assertEquals(AnswerTone.Off, answer.tone)
        assertEquals(PhoneAct.RestartNow, answer.primary)
        // An unknown exit is still "stopped by itself", never "running" from saved state.
        assertEquals(PhoneServerState.Stopped(StoppedBy.Itself), derive(PhoneServerFacts(status = installed)))
    }

    @Test
    fun `B-LINUX-250 an Android kill is named as Android closing it, never as a crash`() {
        val facts = PhoneServerFacts(status = installed, lastExit = ServiceExitReason.MemoryOrPhantomKill)
        assertEquals(PhoneServerState.Stopped(StoppedBy.Android), derive(facts))
        assertEquals(AnswerHeadline.AndroidClosed, ThisPhoneAnswers.of(derive(facts), facts).headline)
    }

    @Test
    fun `B-LINUX-240 running only with a live authenticated answer, a pid alone is never evidence`() {
        assertEquals(PhoneServerState.Checking, derive(PhoneServerFacts(status = running, probe = null)))
        assertEquals(PhoneServerState.NotAnswering, derive(PhoneServerFacts(status = running, probe = ProbeAnswer.NoAnswer)))
        // An authentication challenge is observed without claiming healthy.
        assertEquals(
            PhoneServerState.NotAnswering,
            derive(PhoneServerFacts(status = running, probe = ProbeAnswer.NeedsPassword)),
        )
        assertEquals(PhoneServerState.Running, derive(PhoneServerFacts(status = running, probe = ProbeAnswer.Answered)))
        // Nothing read yet: checking, not absent.
        assertEquals(PhoneServerState.Checking, derive(PhoneServerFacts(status = null)))
    }

    @Test
    fun `B-LINUX-240 a stopped set-up server reads stopped, not absent`() {
        assertEquals(PhoneServerState.NotInstalled, derive(PhoneServerFacts(status = RuntimeStatus(installed = false))))
        assertEquals(
            PhoneServerState.SettingUp,
            derive(PhoneServerFacts(status = RuntimeStatus(installed = false, phase = RuntimePhase.Installing))),
        )
        assertEquals(PhoneServerState.Stopped(StoppedBy.Itself), derive(PhoneServerFacts(status = installed)))
    }

    @Test
    fun `B-LINUX-038 a server the person stopped stays off and only Start brings it back`() {
        val facts = PhoneServerFacts(status = installed, stoppedByPerson = true, recovery = RecoveryFacts.Waiting(1, null))
        assertEquals(PhoneServerState.Stopped(StoppedBy.Person), derive(facts))
        val answer = ThisPhoneAnswers.of(derive(facts), facts)
        assertEquals(AnswerHeadline.Off, answer.headline)
        assertEquals(AnswerLine.StaysOff, answer.line)
        assertEquals(PhoneAct.Start, answer.primary)
        // A native restore that stopped for "Stopped" is the person's stop too.
        assertEquals(
            PhoneServerState.Stopped(StoppedBy.Person),
            derive(PhoneServerFacts(status = installed.copy(restoreReason = RestoreReason.Stopped))),
        )
    }

    @Test
    fun `B-LINUX-037 a start whose server exited is reported with Start again`() {
        val facts = PhoneServerFacts(status = installed, startProblem = StartProblem.Exited)
        val answer = ThisPhoneAnswers.of(derive(facts), facts)
        assertEquals(AnswerHeadline.StartFailed, answer.headline)
        assertEquals(AnswerTone.NeedsYou, answer.tone)
        assertEquals(AnswerLine.StartFailedLine, answer.line)
        assertEquals(PhoneAct.StartAgain, answer.primary)
        // A failed start wins over the deliberate-stop mark: the person asked for a start.
        val afterStop = facts.copy(stoppedByPerson = true)
        assertEquals(AnswerHeadline.StartFailed, ThisPhoneAnswers.of(derive(afterStop), afterStop).headline)
    }

    @Test
    fun `B-LINUX-036 three failed restarts give up and Restart now stays offered`() {
        val facts = PhoneServerFacts(status = installed, recovery = RecoveryFacts.Exhausted)
        assertEquals(PhoneServerState.GaveUp(GaveUpWhy.BudgetExhausted), derive(facts))
        val answer = ThisPhoneAnswers.of(derive(facts), facts)
        assertEquals(AnswerLine.GaveUp, answer.line)
        assertEquals(PhoneAct.RestartNow, answer.primary)
        assertEquals(
            PhoneServerState.GaveUp(GaveUpWhy.BudgetExhausted),
            derive(PhoneServerFacts(status = installed.copy(restoreReason = RestoreReason.BudgetExhausted))),
        )
    }

    @Test
    fun `B-LINUX-036 ownership unknown asks for the app-settings force stop, keeping the server`() {
        val facts = PhoneServerFacts(status = installed.copy(restoreReason = RestoreReason.OwnershipUnknown))
        assertEquals(PhoneServerState.GaveUp(GaveUpWhy.Ownership), derive(facts))
        val answer = ThisPhoneAnswers.of(derive(facts), facts)
        assertEquals(AnswerHeadline.Recovery, answer.headline)
        assertEquals(AnswerLine.RecoveryBody, answer.line)
        assertEquals(PhoneAct.OpenAppSettings, answer.primary)
        val start = PhoneServerFacts(status = installed, startProblem = StartProblem.ComponentRecovery)
        assertEquals(AnswerHeadline.Recovery, ThisPhoneAnswers.of(derive(start), start).headline)
    }

    @Test
    fun `plan 11 6-1 a scheduled restart counts down and the restore phase is shown`() {
        val at = Instant.fromEpochSeconds(1_000)
        val facts =
            PhoneServerFacts(
                status = installed,
                lastExit = ServiceExitReason.MemoryOrPhantomKill,
                recovery = RecoveryFacts.Waiting(attempt = 2, nextAt = at),
            )
        assertEquals(PhoneServerState.Restarting(attempt = 2, nextAt = at), derive(facts))
        val answer = ThisPhoneAnswers.of(derive(facts), facts, now = at - 42.seconds)
        assertEquals(AnswerHeadline.AndroidClosed, answer.headline)
        assertEquals(AnswerTone.Paused, answer.tone)
        assertEquals(AnswerLine.RestartsIn(AboutWait.Seconds(45)), answer.line)
        assertEquals(AnswerLine.RestartsSoon, ThisPhoneAnswers.of(derive(facts), facts, now = at).line)

        val restoring = PhoneServerFacts(status = installed.copy(restorePhase = RestorePhase.Restoring))
        assertEquals(PhoneServerState.Restoring, derive(restoring))
        assertEquals(AnswerHeadline.Restarting, ThisPhoneAnswers.of(derive(restoring), restoring).headline)
        assertEquals(AnswerTone.Working, ThisPhoneAnswers.of(derive(restoring), restoring).tone)
    }

    @Test
    fun `plan 11 6-6 an idle stop is not the person's and not a crash`() {
        val facts = PhoneServerFacts(status = installed.copy(serverIdlePolicySupported = true, serverIdleStopped = true))
        assertEquals(PhoneServerState.Stopped(StoppedBy.Idle), derive(facts))
        val answer = ThisPhoneAnswers.of(derive(facts), facts)
        assertEquals(AnswerHeadline.Off, answer.headline)
        assertEquals(null, answer.line)
        assertEquals(PhoneAct.Start, answer.primary)
    }

    @Test
    fun `B-LINUX-249 a failed install offers setup again, a not-installed phone offers setup`() {
        val failed = PhoneServerFacts(status = RuntimeStatus(installed = false, phase = RuntimePhase.Failed))
        val answer = ThisPhoneAnswers.of(derive(failed), failed)
        assertEquals(AnswerHeadline.InstallFailed, answer.headline)
        assertEquals(PhoneAct.SetUpAgain, answer.primary)
        val none = PhoneServerFacts(status = RuntimeStatus(installed = false))
        assertEquals(PhoneAct.SetUp, ThisPhoneAnswers.of(derive(none), none).primary)
        assertEquals(AnswerHeadline.NotSetUp, ThisPhoneAnswers.of(derive(none), none).headline)
    }

    @Test
    fun `plan 11 6-1 running shows the storage it takes and starting is working`() {
        val facts = PhoneServerFacts(status = running.copy(bytesUsed = 410L * 1024 * 1024), probe = ProbeAnswer.Answered)
        val answer = ThisPhoneAnswers.of(derive(facts), facts)
        assertEquals(AnswerHeadline.Running, answer.headline)
        assertEquals(AnswerTone.Ok, answer.tone)
        assertEquals(AnswerLine.Storage(410L * 1024 * 1024), answer.line)
        assertEquals(null, answer.primary)
        val starting = PhoneServerFacts(status = installed, starting = true)
        assertEquals(PhoneServerState.Starting, derive(starting))
        assertEquals(AnswerHeadline.Starting, ThisPhoneAnswers.of(derive(starting), starting).headline)
    }

    @Test
    fun `this_phone_answer thisPhoneWait rounds up so the promise is never early`() {
        assertEquals(AboutWait.Seconds(5), AboutWait.of(1.seconds))
        assertEquals(AboutWait.Seconds(5), AboutWait.of(5.seconds))
        assertEquals(AboutWait.Seconds(10), AboutWait.of(6.seconds))
        assertEquals(AboutWait.Seconds(55), AboutWait.of(51.seconds))
        assertEquals(AboutWait.Minutes(1), AboutWait.of(60.seconds))
        assertEquals(AboutWait.Minutes(2), AboutWait.of(61.seconds))
    }
}
