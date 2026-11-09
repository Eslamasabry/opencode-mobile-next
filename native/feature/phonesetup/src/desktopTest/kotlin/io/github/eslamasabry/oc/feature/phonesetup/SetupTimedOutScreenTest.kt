@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupActivity
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupTimedOutScreenTest {
    private class Actions : SetupJourneyActions {
        val calls = mutableListOf<String>()
        override fun back() {}
        override fun retry() {}
        override fun toggle(id: String, checked: Boolean) {}
        override fun accept() {}
        override fun start() {}
        override fun customize() {}
        override fun continueSetup() { calls += "tryAgain" }
        override fun askStop() {}
        override fun cancelConfirm() {}
        override fun confirmStop() {}
        override fun askRemoveVoice() {}
        override fun confirmRemoveVoice() {}
        override fun openStorage() {}
        override fun done() {}
        override fun connectComputer() { calls += "computer" }
        override fun reportFailure() {}
    }

    private val registry =
        listOf(
            SetupComponent("linux", "Linux base", "Linux base", "true", "", required = true),
            SetupComponent("essentials", "Git and SSH", "Git and SSH", "true", "true", dependsOn = listOf("linux"), required = true),
        )

    private fun ui(progress: SetupProgress) =
        SetupJourneyUi(
            checking = false,
            progress = progress,
            options = SetupOptions(registry, emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10_000_000_000)),
        )

    private fun run(state: SetupJourneyUi, actions: Actions, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runComposeUiTest {
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) { SetupJourneyScreen(state, actions) }
            }
            block()
        }

    @Test
    fun `P0-SETUP-005 a timed-out step offers Try again and Use a computer instead`() {
        val timedOut =
            SetupProgress(
                SetupState.Failed,
                listOf(
                    ComponentProgress("linux", ComponentState.Done),
                    ComponentProgress("essentials", ComponentState.Failed),
                ),
                0.3,
                current = "essentials",
                error = SetupError.StepTimedOut("Git and SSH"),
                jobId = "setup-1",
            )
        val actions = Actions()
        run(ui(timedOut), actions) {
            onNodeWithText("Git and SSH is taking far longer than it should. Try again, or use a computer instead.").assertExists()
            onNodeWithText("Try again").performClick()
            onNodeWithText("Use a computer instead").performClick()
        }
        assertEquals(listOf("tryAgain", "computer"), actions.calls)
    }

    @Test
    fun `P0-SETUP-006 a running step shows its step clock and what it is doing`() {
        val running =
            SetupProgress(
                SetupState.Running,
                listOf(
                    ComponentProgress("linux", ComponentState.Done),
                    ComponentProgress("essentials", ComponentState.Running),
                ),
                0.3,
                current = "essentials",
                jobId = "setup-1",
                stepElapsedSeconds = 125,
                activity = SetupActivity.Unpacking,
                quietSeconds = 45,
            )
        run(ui(running), Actions()) {
            onNodeWithText("Unpacking files · \u20662:05\u2069").assertExists()
            onNodeWithText("Still working. Long steps can take several minutes.").assertExists()
        }
    }
}
