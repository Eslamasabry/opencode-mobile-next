@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, io.github.eslamasabry.oc.core.common.RedactorApi::class)

package io.github.eslamasabry.oc.feature.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.setup.presentation.SetupActions
import io.github.eslamasabry.oc.feature.setup.presentation.SetupFailureLine
import io.github.eslamasabry.oc.feature.setup.presentation.SetupHeadline
import io.github.eslamasabry.oc.feature.setup.presentation.SetupPhase
import io.github.eslamasabry.oc.feature.setup.presentation.SetupProgressUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupStartUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupStepUi
import io.github.eslamasabry.oc.feature.setup.presentation.SetupUi
import io.github.eslamasabry.oc.feature.setup.ui.SetupScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real setup page with fake state, found by visible text (BUILD-RUNTIME-B). */
class SetupScreenTest {
    private class Recorder : SetupActions {
        val calls = mutableListOf<String>()

        override fun start() {
            calls += "start"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun resume() {
            calls += "resume"
        }

        override fun openAppSettings() {
            calls += "settings"
        }

        override fun back() {
            calls += "back"
        }
    }

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    private val steps =
        persistentListOf(
            SetupStepUi("linux", "Linux base", ComponentState.Done, version = "24.04"),
            SetupStepUi("essentials", "Git and SSH", ComponentState.Done),
            SetupStepUi("python", "Python", ComponentState.Done),
            SetupStepUi("node", "Node.js", ComponentState.Running, stage = "Downloading Node.js 24.21.0", bytes = "18" to "30 MB"),
            SetupStepUi("opencode", "OpenCode", ComponentState.Pending),
            SetupStepUi("start", "Start OpenCode", ComponentState.Pending),
        )

    private val downloading =
        SetupUi(
            phase =
                SetupPhase.Progress(
                    SetupProgressUi(
                        state = SetupState.Running,
                        headline = SetupHeadline.Downloading,
                        subject = "Node.js",
                        step = 4,
                        of = 6,
                        measured = 0.6f,
                        bytes = "18" to "30 MB",
                        minutesLeft = 3,
                        steps = steps,
                    ),
                ),
        )

    @Test
    fun `E2E-17 the start page promises the download and Set up starts it`() = runComposeUiTest {
        val actions = Recorder()
        val state = SetupUi(phase = SetupPhase.Start(SetupStartUi(208_000_000L, persistentListOf("Git and SSH", "Python", "Node.js"))))
        setContent { Themed { SetupScreen(state, actions) } }
        onNodeWithText("Run a coding agent right here").assertIsDisplayed()
        onNodeWithText("No computer and no other apps. ~208 MB to download the first time.").assertIsDisplayed()
        onNodeWithText("Includes Git and SSH, Python and Node.js.").assertIsDisplayed()
        onNodeWithText("Set up OpenCode on this phone").performClick()
        assertEquals(listOf("start"), actions.calls)
    }

    @Test
    fun `B-LINUX-201 a running job says what it downloads, how much has arrived, and offers Pause`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { SetupScreen(downloading, actions) } }
        onNodeWithText("Downloading Node.js").assertIsDisplayed()
        onNode(hasContentDescription("Step 4 of 6 · Node.js · 18 of 30 MB · about 3 min left", substring = true)).assertExists()
        onNodeWithText("Downloading Node.js 24.21.0 · 18 of 30 MB").assertExists()
        onNodeWithText("24.04").assertExists()
        onNodeWithText("Pause setup").performClick()
        assertEquals(listOf("pause"), actions.calls)
    }

    @Test
    fun `B-LINUX-203 a failed job says why in one plain line, keeps the log under Details, and continues`() = runComposeUiTest {
        val actions = Recorder()
        val failed =
            SetupUi(
                phase =
                    SetupPhase.Progress(
                        SetupProgressUi(
                            state = SetupState.Failed,
                            headline = SetupHeadline.Failed,
                            step = 2,
                            of = 6,
                            failure = SetupFailureLine.Error(SetupError.Offline("Git and SSH")),
                            steps = steps,
                            details = Redacted("E: Failed to fetch http://ports.ubuntu.com/pool/main/g/git"),
                        ),
                    ),
            )
        setContent { Themed { SetupScreen(failed, actions) } }
        onNodeWithText("Setup didn't finish").assertIsDisplayed()
        onNodeWithText("Could not download Git and SSH: no internet connection").assertIsDisplayed()
        onNodeWithText("E: Failed to fetch", substring = true).assertDoesNotExist()
        onNodeWithText("Details").performClick()
        onNodeWithText("E: Failed to fetch", substring = true).assertExists()
        onNodeWithText("Continue setup").performClick()
        assertEquals(listOf("resume"), actions.calls)
    }

    @Test
    fun `B-LINUX-163 a finished job says it is ready and Done goes back`() = runComposeUiTest {
        val actions = Recorder()
        val done =
            SetupUi(
                phase =
                    SetupPhase.Progress(
                        SetupProgressUi(state = SetupState.Done, headline = SetupHeadline.Done, of = 6, steps = steps),
                    ),
            )
        setContent { Themed { SetupScreen(done, actions) } }
        onNodeWithText("All set").assertIsDisplayed()
        onNodeWithText("OpenCode is ready on this phone").assertIsDisplayed()
        onNodeWithText("Done").performClick()
        assertEquals(listOf("back"), actions.calls)
    }
}
