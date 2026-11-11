@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.feature.phonesetup.presentation.BatteryOpen
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

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
    fun `first-run Customize still shows its setup page behind the choices`() =
        show(SetupJourneyUi(customizing = true, adding = false, checking = false, options = options)) {
            onNodeWithText("Run a coding agent right here").assertExists()
        }
}
