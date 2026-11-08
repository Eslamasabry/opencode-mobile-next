package io.github.eslamasabry.oc.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveReason
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveResult
import io.github.eslamasabry.oc.feature.onboarding.presentation.ManagedMoveUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningGateActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.OpeningGateUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomePlace
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeSetupPhase
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.ui.ManagedMoveScreen
import io.github.eslamasabry.oc.feature.onboarding.ui.OpeningGateScreen
import io.github.eslamasabry.oc.feature.onboarding.ui.WelcomeScreen
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Audit capture only: welcome, opening gate and managed move in their states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object WelcomeA : WelcomeActions {
        override fun connect() {}

        override fun openSettings() {}
    }

    private object GateA : OpeningGateActions {
        override fun retry() {}

        override fun reportProblem() {}

        override fun askStartFresh() {}

        override fun confirmStartFresh(confirmationId: Long?) {}

        override fun cancelStartFresh() {}
    }

    private object MoveA : ManagedMoveActions {
        override fun retry() {}
    }

    private fun welcome(state: String, ui: WelcomeUi) {
        Audit.shoot("onboarding", "welcome", state) { WelcomeScreen(ui, WelcomeA) }
    }

    @Test
    fun welcomeStates() = Audit.guard("onboarding__welcome__build") {
        val phone = PhoneWelcomeUi(available = true, seconds = 240, bytes = 208_000_000)
        welcome("computer-only", WelcomeUi(PhoneWelcomeUi(), WelcomePlace.Computer))
        welcome("phone-chosen", WelcomeUi(phone, WelcomePlace.Phone))
        welcome("computer-chosen", WelcomeUi(phone, WelcomePlace.Computer))
        welcome("phone-no-estimate", WelcomeUi(PhoneWelcomeUi(available = true), WelcomePlace.Phone))
        welcome("phone-working", WelcomeUi(phone.copy(working = true), WelcomePlace.Phone))
        welcome("phone-running", WelcomeUi(phone.copy(phase = WelcomeSetupPhase.Running, percent = 42), WelcomePlace.Phone))
        welcome("phone-stopped", WelcomeUi(phone.copy(phase = WelcomeSetupPhase.Stopped, percent = 42), WelcomePlace.Phone))
        welcome("phone-done", WelcomeUi(phone.copy(phase = WelcomeSetupPhase.Done, percent = 100), WelcomePlace.Phone))
        welcome("phone-failed", WelcomeUi(phone.copy(failure = Failure.Unknown()), WelcomePlace.Phone))
    }

    @Test
    fun openingGateStates() = Audit.guard("onboarding__opening-gate__build") {
        fun gate(state: String, ui: OpeningGateUi) {
            Audit.shoot("onboarding", "opening-gate", state) { OpeningGateScreen(ui, GateA) }
        }
        gate("opening", OpeningGateUi.Waiting(updating = false, since = TimeSource.Monotonic.markNow()))
        gate("opening-slow", OpeningGateUi.Waiting(updating = false, since = TimeSource.Monotonic.markNow() - 20.seconds))
        gate("updating", OpeningGateUi.Waiting(updating = true, since = TimeSource.Monotonic.markNow()))
        gate("failed", OpeningGateUi.Failed())
        gate("failed-confirming", OpeningGateUi.Failed(confirming = true, confirmationId = 1L))
        gate("resetting", OpeningGateUi.Resetting)
        gate("reset-failed", OpeningGateUi.ResetFailed)
    }

    @Test
    fun managedMoveStates() = Audit.guard("onboarding__managed-move__build") {
        fun move(state: String, ui: ManagedMoveUi) {
            Audit.shoot("onboarding", "managed-move", state) { ManagedMoveScreen(ui, MoveA) }
        }
        move("working", ManagedMoveUi())
        move("retrying", ManagedMoveUi(retry = 2, retries = 3))
        move("failed-phone-not-ready", ManagedMoveUi(failure = ManagedMoveResult.Failed(ManagedMoveReason.PhoneNotReady)))
        move("failed-separate-history", ManagedMoveUi(failure = ManagedMoveResult.Failed(ManagedMoveReason.SeparateHistory)))
        move("failed-move-stopped", ManagedMoveUi(failure = ManagedMoveResult.Failed(ManagedMoveReason.MoveStopped)))
        move("failed-not-confirmed", ManagedMoveUi(failure = ManagedMoveResult.Failed(ManagedMoveReason.NotConfirmed)))
    }
}
