package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentProgress
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress as JourneyComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState as JourneyComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupActivity
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent as JourneySetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress as JourneySetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState as JourneySetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupItemUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyActions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupTransport
import io.github.eslamasabry.oc.feature.phonesetup.ui.PhoneSetupScreen
import io.github.eslamasabry.oc.feature.phonesetup.ui.SetupJourneyScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** Wide-window behavior tests for PhoneSetupScreen and SetupJourneyScreen (B-SET-300..304). */
@OptIn(ExperimentalTestApi::class)
class PhoneSetupWideTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private object NoActions : PhoneSetupActions {
        override fun back() = Unit
        override fun setUp() = Unit
        override fun continueSetup() = Unit
        override fun askStop() = Unit
        override fun keepGoing() = Unit
        override fun stop() = Unit
        override fun openStorage() = Unit
        override fun done() = Unit
    }

    private object NoJourneyActions : SetupJourneyActions {
        override fun back() = Unit
        override fun retry() = Unit
        override fun toggle(id: String, checked: Boolean) = Unit
        override fun accept() = Unit
        override fun start() = Unit
        override fun customize() = Unit
        override fun continueSetup() = Unit
        override fun askStop() = Unit
        override fun cancelConfirm() = Unit
        override fun confirmStop() = Unit
        override fun askRemoveVoice() = Unit
        override fun confirmRemoveVoice() = Unit
        override fun openStorage() = Unit
        override fun done() = Unit
    }

    private val items =
        persistentListOf(
            SetupItemUi("linux", "Linux base"),
            SetupItemUi("essentials", "Git and SSH"),
            SetupItemUi("node", "Node.js"),
            SetupItemUi("opencode", "OpenCode"),
        )

    private val journeyRegistry =
        listOf(
            JourneySetupComponent(
                "linux", "Linux base", "Linux base", "true", "", required = true, estimatedSeconds = 20,
                downloadBytes = 30_000_000,
            ),
            JourneySetupComponent(
                "essentials", "Git, SSH and certificates", "Git and SSH", "true", "true", dependsOn = listOf("linux"),
                required = true, estimatedSeconds = 40, downloadBytes = 45_000_000,
            ),
            JourneySetupComponent(
                "node", "Node.js", "Node.js", "true", "true", dependsOn = listOf("essentials"), required = true,
                estimatedSeconds = 40, downloadBytes = 58_000_000,
            ),
            JourneySetupComponent(
                "opencode", "OpenCode", "OpenCode", "true", "true", dependsOn = listOf("node"), required = true,
                estimatedSeconds = 90, downloadBytes = 75_000_000,
            ),
        )

    private fun startUi() =
        PhoneSetupUi(
            checking = false,
            preflight = SetupPreflightResult.Ok,
            items = items,
            downloadBytes = 350_000_000,
        )

    private fun journeyRunningUi() =
        SetupJourneyUi(
            checking = false,
            options =
                SetupOptions(
                    journeyRegistry, emptySet(), SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10_000_000_000)
                ),
            progress =
                JourneySetupProgress(
                    state = JourneySetupState.Running,
                    components =
                        listOf(
                            JourneyComponentProgress(
                                "linux", JourneyComponentState.Done, bytesDone = 30_000_000, bytesTotal = 30_000_000,
                            ),
                            JourneyComponentProgress(
                                "essentials", JourneyComponentState.Done, bytesDone = 45_000_000,
                                bytesTotal = 45_000_000,
                            ),
                            JourneyComponentProgress(
                                "node", JourneyComponentState.Running, stage = "Downloading Node.js",
                                bytesDone = 24_000_000, bytesTotal = 58_000_000,
                            ),
                            JourneyComponentProgress("opencode", JourneyComponentState.Pending),
                        ),
                    overall = 0.48,
                    current = "node",
                    etaSeconds = 180,
                    jobId = "journey-test-1",
                    activity = SetupActivity.Downloading,
                    stepElapsedSeconds = 18,
                ),
            transport = SetupTransport.Wifi,
        )

    private fun showContentAt(
        widthDp: Int, heightDp: Int, content: @Composable () -> Unit, block: ComposeUiTest.() -> Unit
    ) {
        runDesktopComposeUiTest(widthDp, heightDp) {
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                        content()
                    }
                }
            }
            waitForIdle()
            block()
        }
    }

    private fun showAt(widthDp: Int, heightDp: Int, state: PhoneSetupUi, block: ComposeUiTest.() -> Unit) =
        showContentAt(widthDp, heightDp, { PhoneSetupScreen(state, NoActions) }, block)

    private fun ComposeUiTest.assertDoneMarkDrawn(rowName: String) {
        val doneRow =
            onNode(
                hasText(rowName, substring = true) and hasText("Done", substring = true),
                useUnmergedTree = false,
            )
        doneRow.assertExists()
        val rowBounds = doneRow.getBoundsInRoot()
        val map = onRoot().captureToImage().toPixelMap()
        val x0 = rowBounds.left.value.toInt().coerceIn(0, map.width - 1)
        val x1 = (x0 + 44).coerceIn(x0 + 1, map.width)
        val y0 = rowBounds.top.value.toInt().coerceIn(0, map.height - 1)
        val y1 = rowBounds.bottom.value.toInt().coerceIn(y0 + 1, map.height)
        val bg = map[x0, y0]
        var inkPixels = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val p = map[x, y]
                if (abs(p.red - bg.red) + abs(p.green - bg.green) + abs(p.blue - bg.blue) > 0.15f) {
                    inkPixels++
                }
            }
        }
        assertTrue(
            inkPixels >= 16,
            "Expected a visible done mark in the leading slot of the $rowName row ($rowBounds), found " +
                "$inkPixels ink pixels",
        )
    }

    @Test
    fun `B-SET-300 phone setup uses the width at 1280`() {
        showAt(1280, 800, startUi()) {
            val action = onNodeWithText("Set up OpenCode on this phone").getBoundsInRoot()
            val part = onNodeWithText("Linux base").getBoundsInRoot()
            val contentWidth = 1280f - 32f
            val delta = abs(part.left.value - action.left.value)
            assertTrue(
                delta > contentWidth / 2f,
                "Expected main action ($action) and part ($part) in separate side-by-side columns " +
                    "(|delta|=$delta > ${contentWidth / 2f})",
            )
        }
    }

    @Test
    fun `B-SET-301 phone setup unchanged under 840`() {
        for (width in listOf(600, 839)) {
            showAt(width, 800, startUi()) {
                val action = onNodeWithText("Set up OpenCode on this phone").getBoundsInRoot()
                val part = onNodeWithText("Linux base").getBoundsInRoot()
                val delta = abs(part.left.value - action.left.value)
                assertTrue(
                    part.bottom.value <= action.top.value + 1f && delta < 64f,
                    "Expected one vertical column at ${width}dp: part=$part, action=$action",
                )
            }
        }
    }

    @Test
    fun `B-SET-302 the done step shows a mark`() {
        val running =
            SetupProgress(
                state = SetupState.Running,
                components =
                    listOf(
                        ComponentProgress("linux", ComponentState.Done),
                        ComponentProgress("node", ComponentState.Running, stage = "Installing Node.js"),
                    ),
                overall = 0.42,
                current = "node",
                jobId = "setup-1",
                etaSeconds = 240,
            )
        showAt(1280, 800, PhoneSetupUi(checking = false, items = items, progress = running)) {
            onNodeWithText("1 of 4 done").assertExists()
            assertDoneMarkDrawn("Linux base")
        }
    }

    @Test
    fun `B-SET-303 title and action on the content's edge at 1280`() {
        showAt(1280, 800, startUi()) {
            val title = onNodeWithText("On this phone").getBoundsInRoot()
            val content = onNodeWithText("Run a coding agent right here").getBoundsInRoot()
            val action = onNodeWithText("Set up OpenCode on this phone").getBoundsInRoot()
            assertTrue(
                abs(title.left.value - content.left.value) <= 1f,
                "Expected title left (${title.left}) == content left (${content.left})",
            )
            assertTrue(
                abs(action.left.value - content.left.value) <= 1f,
                "Expected action left (${action.left}) == content left (${content.left})",
            )
        }
    }

    @Test
    fun `B-SET-304 journey progress uses two columns at 1280 and stays single column under 840`() {
        showContentAt(1280, 800, { SetupJourneyScreen(journeyRunningUi(), NoJourneyActions) }) {
            val stage = onNodeWithText("Downloading Node.js").getBoundsInRoot()
            val pause = onNodeWithText("Pause setup").getBoundsInRoot()
            val part = onNodeWithText("Linux base").getBoundsInRoot()
            assertTrue(
                part.left.value > 640f && stage.right.value < part.left.value && pause.right.value < part.left.value,
                "Expected ring stage ($stage) and Pause ($pause) on left column beside parts ($part) on right",
            )
            onNodeWithText("2 of 4 done").assertExists()
            assertDoneMarkDrawn("Linux base")
        }
        showContentAt(839, 800, { SetupJourneyScreen(journeyRunningUi(), NoJourneyActions) }) {
            onNodeWithText("Downloading Node.js").assertExists()
            onNodeWithText("Pause setup").assertExists()
            onAllNodesWithText("Linux base").assertCountEquals(0)
        }
    }
}
