package io.github.eslamasabry.oc.feature.background

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundActions
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLiveFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import io.github.eslamasabry.oc.feature.background.presentation.SwitchFailure
import io.github.eslamasabry.oc.feature.background.presentation.backgroundUi
import io.github.eslamasabry.oc.feature.background.ui.BackgroundScreen
import kotlin.test.Test

/** Audit capture only: the Background page in many states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Taps : BackgroundActions {
        override fun onBack() {}
        override fun onSwitch(on: Boolean) {}
        override fun onRestart() {}
        override fun onResume() {}
        override fun onDismissPause() {}
        override fun onAllowBattery() {}
        override fun onOpenKeepRunning() {}
    }

    private fun shot(name: String, facts: BackgroundLiveFacts, failure: SwitchFailure? = null) =
        Audit.guard("background__background__${name}__build") {
            Audit.shoot("background", "background", name) { BackgroundScreen(backgroundUi(facts, failure), Taps) }
        }

    @Test
    fun background() {
        shot("unsupported", BackgroundLiveFacts(supported = false))
        shot("off", BackgroundLiveFacts(supported = true))
        shot("on-running", BackgroundLiveFacts(supported = true, enabled = true, active = true))
        shot("on-battery-unrestricted", BackgroundLiveFacts(supported = true, enabled = true, active = true, batteryUnrestricted = true))
        shot("on-battery-settings-opened", BackgroundLiveFacts(supported = true, enabled = true, active = true, batterySettingsOpened = true))
        shot("on-not-confirmed", BackgroundLiveFacts(supported = true, enabled = true, active = false))
        shot("busy", BackgroundLiveFacts(supported = true, enabled = true, busy = true))
        shot("stopped-by-android", BackgroundLiveFacts(supported = true, stoppedByAndroid = true))
        shot("failure-not-enabled", BackgroundLiveFacts(supported = true), SwitchFailure.NotEnabled)
        shot("failure-not-disabled", BackgroundLiveFacts(supported = true, enabled = true, active = true), SwitchFailure.NotDisabled)
        shot("failure-notifications-denied", BackgroundLiveFacts(supported = true), SwitchFailure.NotificationsDenied)
        shot("failure-unavailable", BackgroundLiveFacts(supported = true), SwitchFailure.Unavailable)
        for (reason in PauseReason.entries) {
            shot("pause-${reason.name.lowercase()}", BackgroundLiveFacts(supported = true, pause = PauseFacts(reason, clock = "1:10 PM")))
        }
        shot("pause-other-day", BackgroundLiveFacts(supported = true, pause = PauseFacts(PauseReason.UserStopped, clock = "9:10 AM", day = "Nov 2")))
        shot("pause-cannot-resume", BackgroundLiveFacts(supported = true, pause = PauseFacts(PauseReason.TimeLimit, clock = "1:10 PM", canResume = false)))
        shot("pause-resume-failed", BackgroundLiveFacts(supported = true, pause = PauseFacts(PauseReason.UserStopped, clock = "9:10 AM", resumeFailed = true)))
        shot("pause-resuming", BackgroundLiveFacts(supported = true, pause = PauseFacts(PauseReason.Interrupted, clock = "3:10 PM"), resuming = true))
    }
}
