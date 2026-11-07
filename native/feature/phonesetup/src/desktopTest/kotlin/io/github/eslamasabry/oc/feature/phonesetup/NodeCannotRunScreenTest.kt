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
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.feature.phonesetup.ui.nodeCannotRun
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

/**
 * The give-up screen when Node.js cannot run inside built-in Linux (#117/#119): one plain message, "Connect a
 * computer" and "Report this failure", never Continue. Pictures for a person to look at are written to
 * build/node-compat/ (light, dark and Arabic).
 */
class NodeCannotRunScreenTest {
    private class Actions : SetupJourneyActions {
        val calls = mutableListOf<String>()

        override fun back() {}

        override fun retry() {}

        override fun toggle(id: String, checked: Boolean) {}

        override fun accept() {}

        override fun start() {}

        override fun customize() {}

        override fun continueSetup() {
            calls += "continue"
        }

        override fun askStop() {}

        override fun cancelConfirm() {}

        override fun confirmStop() {}

        override fun askRemoveVoice() {}

        override fun confirmRemoveVoice() {}

        override fun openStorage() {}

        override fun done() {}

        override fun connectComputer() {
            calls += "connectComputer"
        }

        override fun reportFailure() {
            calls += "report"
        }
    }

    private val registry =
        listOf(
            SetupComponent("linux", "Linux base", "Linux base", "true", "", required = true),
            SetupComponent("node", "Node.js", "Node.js", "true", "true", dependsOn = listOf("linux"), required = true),
            SetupComponent("opencode", "OpenCode", "OpenCode", "true", "true", dependsOn = listOf("node"), required = true),
        )

    private val failed =
        SetupProgress(
            SetupState.Failed,
            listOf(
                ComponentProgress("linux", ComponentState.Done),
                ComponentProgress("node", ComponentState.Failed, error = SetupError.NodeCannotRun),
                ComponentProgress("opencode", ComponentState.Pending),
            ),
            0.4,
            current = "node",
            error = SetupError.NodeCannotRun,
            logTail = "[oc] probe both-version 139 # node --version",
            jobId = "setup-1",
        )

    private val state =
        SetupJourneyUi(
            checking = false,
            progress = failed,
            options = SetupOptions(registry, emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10_000_000_000)),
        )

    private val message =
        "This phone's system can't run Node.js inside built-in Linux. " +
            "You can still use OpenCode Mobile by connecting to your computer."

    @Test
    fun `B-LINUX-203 only the Node give-up counts as cannot run`() {
        assertTrue(nodeCannotRun(failed))
        assertFalse(nodeCannotRun(failed.copy(error = SetupError.CannotStart, components = emptyList())))
        assertFalse(nodeCannotRun(failed.copy(state = SetupState.Running)))
    }

    @Test
    fun `B-LINUX-203 the give-up screen offers a computer and a report, never Continue`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(
                    platform = DesktopKitPlatform(),
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    SetupJourneyScreen(state, actions)
                }
            }
            onAllNodesWithText(message).assertCountEquals(1)
            onAllNodesWithText("Continue setup").assertCountEquals(0)
            onAllNodesWithText("[oc] probe", substring = true).assertCountEquals(0)
            onNodeWithText("Connect a computer").performClick()
            onNodeWithText("Report this failure").performClick()
            assertEquals(listOf("connectComputer", "report"), actions.calls)
        }

    @Test
    fun `B-LINUX-203 pictures of the give-up screen in light, dark and Arabic`() {
        val out = File("build/node-compat").apply { mkdirs() }
        for ((name, locale, dark) in listOf(Triple("en_light", "en", false), Triple("en_dark", "en", true), Triple("ar_light", "ar", false))) {
            runComposeUiTest {
                setContent {
                    KitTheme(
                        platform = DesktopKitPlatform(),
                        appearance =
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                            ),
                        localeTag = locale,
                        stillFrame = true,
                    ) {
                        Box(Modifier.size(412.dp, 915.dp)) { SetupJourneyScreen(state, Actions()) }
                    }
                }
                waitForIdle()
                val picture = onAllNodes(isRoot())[0].captureToImage().toAwtImage()
                ImageIO.write(picture, "png", File(out, "node_cannot_run_$name.png"))
            }
        }
    }
}
