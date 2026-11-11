@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryOpen
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.ui.JourneyBody
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.feature.phonesetup.ui.journeyBody
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** S9: Add tools from This phone opens over This phone's name, never over the first-run "Set up OpenCode" page. */
class AddToolsScreenTest {
    private class Actions : SetupJourneyActions {
        override fun back() {}
        override fun retry() {}
        override fun toggle(id: String, checked: Boolean) {}
        override fun accept() {}
        override fun start() {}
        override fun customize() {}
        override fun continueSetup() {}
        override fun askStop() {}
        override fun cancelConfirm() {}
        override fun confirmStop() {}
        override fun askRemoveVoice() {}
        override fun confirmRemoveVoice() {}
        override fun openStorage() {}
        override fun done() {}
        override fun openBatterySettings(open: BatteryOpen) {}
    }

    private val options =
        SetupOptions(emptyList(), emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10_000_000_000))

    private fun show(state: SetupJourneyUi, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) { SetupJourneyScreen(state, Actions()) }
            }
            block()
        }

    @Test
    fun `S9 Add tools on an installed phone draws no first-run page behind its sheet`() =
        show(SetupJourneyUi(customizing = true, adding = true, checking = false, options = options)) {
            onNodeWithText("Run a coding agent right here").assertDoesNotExist()
            onNodeWithText("Set up OpenCode on this phone").assertDoesNotExist()
        }

    @Test
    fun `S9 the page under the sheet is nothing for Add tools, the setup page only for first-run Customize`() {
        val done = SetupProgress(SetupState.Done, emptyList(), 1.0, jobId = "setup-1")
        assertEquals(
            JourneyBody.Nothing,
            journeyBody(SetupJourneyUi(customizing = true, adding = true, checking = false, progress = done)),
        )
        assertEquals(
            JourneyBody.Hero,
            journeyBody(SetupJourneyUi(customizing = true, adding = false, checking = false, progress = done)),
        )
        // A running add job still shows its progress.
        val running = done.copy(state = SetupState.Running)
        assertEquals(
            JourneyBody.Job,
            journeyBody(SetupJourneyUi(customizing = true, adding = true, checking = false, progress = running)),
        )
    }
}
