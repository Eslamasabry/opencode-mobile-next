package io.github.eslamasabry.oc.feature.desktopupdates

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopCheckPhase
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopUpdatesActions
import io.github.eslamasabry.oc.feature.desktopupdates.presentation.DesktopUpdatesUi
import io.github.eslamasabry.oc.feature.desktopupdates.ui.DesktopUpdatesScreen
import kotlin.test.Test

/** Audit capture only: the desktop Updates page in each phase. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private object Taps : DesktopUpdatesActions {
        override fun onCheck() {}
        override fun onOpenRelease() {}
        override fun onConfirmOpen() {}
        override fun onCancelOpen() {}
        override fun onClose() {}
    }

    private fun shot(name: String, ui: DesktopUpdatesUi) =
        Audit.guard("desktopupdates__desktop-updates__${name}__build") {
            Audit.shoot("desktopupdates", "desktop-updates", name) { DesktopUpdatesScreen(ui, Taps) }
        }

    @Test
    fun desktopUpdates() {
        val page = "https://github.com/example/app/releases/tag/2.1.2"
        shot("never-checked", DesktopUpdatesUi("2.1.1"))
        shot("checking", DesktopUpdatesUi("2.1.1", phase = DesktopCheckPhase.Checking))
        shot("current", DesktopUpdatesUi("2.1.1", phase = DesktopCheckPhase.Current))
        shot("available", DesktopUpdatesUi("2.1.1", phase = DesktopCheckPhase.Available, releaseTag = "2.1.2", releasePage = page))
        shot("available-ask", DesktopUpdatesUi("2.1.1", phase = DesktopCheckPhase.Available, releaseTag = "2.1.2", releasePage = page, askingToOpen = true))
        shot("available-open-failed", DesktopUpdatesUi("2.1.1", phase = DesktopCheckPhase.Available, releaseTag = "2.1.2", releasePage = page, openFailed = true))
        shot("failed", DesktopUpdatesUi("2.1.1", phase = DesktopCheckPhase.Failed))
        shot("long-version", DesktopUpdatesUi("2.1.1-beta.12345678901234567890+build.987654321", phase = DesktopCheckPhase.Available, releaseTag = "2.1.2-rc.1234567890123456789", releasePage = page))
    }
}
