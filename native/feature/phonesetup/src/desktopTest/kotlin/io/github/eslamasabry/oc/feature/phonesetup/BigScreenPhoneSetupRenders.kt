package io.github.eslamasabry.oc.feature.phonesetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentProgress
import io.github.eslamasabry.oc.engine.linuxsetup.ComponentState
import io.github.eslamasabry.oc.engine.linuxsetup.SetupPreflightResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProblem
import io.github.eslamasabry.oc.engine.linuxsetup.SetupProgress
import io.github.eslamasabry.oc.engine.linuxsetup.SetupState
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress as JourneyComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState as JourneyComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupActivity
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent as JourneySetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
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
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Picture only, asserts nothing: phone setup start, waiting, failed, done and journey ring states across sizes. */
@OptIn(ExperimentalTestApi::class)
class BigScreenPhoneSetupRenders {
    private object NoActions : PhoneSetupActions {
        override fun back() {}
        override fun setUp() {}
        override fun continueSetup() {}
        override fun askStop() {}
        override fun keepGoing() {}
        override fun stop() {}
        override fun openStorage() {}
        override fun done() {}
    }

    private object NoJourneyActions : SetupJourneyActions {
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
    }

    private class Shot(val key: String, val w: Int, val h: Int, val scale: Float, val suffix: String)

    private val shots =
        listOf(
            Shot("phone", 360, 780, 1f, ""),
            Shot("phone", 360, 780, 1.3f, "-t13"),
            Shot("phone412", 412, 915, 1f, ""),
            Shot("split", 600, 800, 1f, ""),
            Shot("tabp", 800, 1280, 1f, ""),
            Shot("exp840", 840, 800, 1f, ""),
            Shot("tabl", 1280, 800, 1f, ""),
            Shot("tabl", 1280, 800, 1.3f, "-t13"),
        )

    private val out = File("build/bigscreen").also { it.mkdirs() }

    private fun shoot(name: String, after: ComposeUiTest.() -> Unit = {}, content: @Composable () -> Unit) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (s in shots) {
            runCatching {
                runDesktopComposeUiTest(s.w * 2, s.h * 2) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(2f, s.scale)) {
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
                    }
                    waitForIdle()
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    after()
                    waitForIdle()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-${s.key}${s.suffix}.png"))
                }
            }.onFailure { println("RENDERFAIL $name ${s.key}${s.suffix} ${it::class.simpleName}: ${it.message?.take(200)}") }
        }
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
            JourneySetupComponent("linux", "Linux base", "Linux base", "true", "", required = true, estimatedSeconds = 20, downloadBytes = 30_000_000),
            JourneySetupComponent("essentials", "Git, SSH and certificates", "Git and SSH", "true", "true", dependsOn = listOf("linux"), required = true, estimatedSeconds = 40, downloadBytes = 45_000_000),
            JourneySetupComponent("node", "Node.js", "Node.js", "true", "true", dependsOn = listOf("essentials"), required = true, estimatedSeconds = 40, downloadBytes = 58_000_000),
            JourneySetupComponent("opencode", "OpenCode", "OpenCode", "true", "true", dependsOn = listOf("node"), required = true, estimatedSeconds = 90, downloadBytes = 75_000_000),
        )

    private val journeyOptions =
        SetupOptions(
            journeyRegistry,
            emptySet(),
            SetupDeviceInfo(listOf("arm64-v8a"), 8192, 10_000_000_000),
        )

    @Test
    fun render() {
        shoot("phonesetup-start") {
            PhoneSetupScreen(
                PhoneSetupUi(
                    checking = false,
                    preflight = SetupPreflightResult.Ok,
                    items = items,
                    downloadBytes = 350_000_000,
                ),
                NoActions,
            )
        }
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
        shoot("phonesetup-waiting") {
            PhoneSetupScreen(PhoneSetupUi(checking = false, items = items, progress = running), NoActions)
        }
        val failed =
            SetupProgress(
                state = SetupState.Failed,
                components =
                    listOf(
                        ComponentProgress("linux", ComponentState.Done),
                        ComponentProgress("opencode", ComponentState.Failed),
                    ),
                overall = 0.6,
                error = SetupProblem.Offline("OpenCode"),
                logTail = "curl: (6) Could not resolve host",
                jobId = "setup-1",
            )
        shoot("phonesetup-failed") {
            PhoneSetupScreen(PhoneSetupUi(checking = false, items = items, progress = failed), NoActions)
        }
        val done =
            SetupProgress(
                state = SetupState.Done,
                components =
                    listOf(
                        ComponentProgress("linux", ComponentState.Done),
                        ComponentProgress("essentials", ComponentState.Done),
                        ComponentProgress("node", ComponentState.Done),
                        ComponentProgress("opencode", ComponentState.Done),
                    ),
                overall = 1.0,
                jobId = "setup-1",
            )
        shoot("phonesetup-done") {
            PhoneSetupScreen(PhoneSetupUi(checking = false, items = items, progress = done), NoActions)
        }
        val journeyRunning =
            JourneySetupProgress(
                state = JourneySetupState.Running,
                components =
                    listOf(
                        JourneyComponentProgress("linux", JourneyComponentState.Done, bytesDone = 30_000_000, bytesTotal = 30_000_000),
                        JourneyComponentProgress("essentials", JourneyComponentState.Done, bytesDone = 45_000_000, bytesTotal = 45_000_000),
                        JourneyComponentProgress("node", JourneyComponentState.Running, stage = "Downloading Node.js", bytesDone = 24_000_000, bytesTotal = 58_000_000),
                        JourneyComponentProgress("opencode", JourneyComponentState.Pending),
                    ),
                overall = 0.48,
                current = "node",
                etaSeconds = 180,
                jobId = "journey-render-1",
                activity = SetupActivity.Downloading,
                stepElapsedSeconds = 18,
            )
        shoot("phonesetup-journey-waiting") {
            SetupJourneyScreen(
                SetupJourneyUi(
                    checking = false,
                    options = journeyOptions,
                    progress = journeyRunning,
                    transport = SetupTransport.Wifi,
                ),
                NoJourneyActions,
            )
        }
        val journeyFailed =
            JourneySetupProgress(
                state = JourneySetupState.Failed,
                components =
                    listOf(
                        JourneyComponentProgress("linux", JourneyComponentState.Done),
                        JourneyComponentProgress("essentials", JourneyComponentState.Done),
                        JourneyComponentProgress("node", JourneyComponentState.Failed, error = SetupError.Offline("Node.js")),
                        JourneyComponentProgress("opencode", JourneyComponentState.Pending),
                    ),
                overall = 0.48,
                current = "node",
                error = SetupError.Offline("Node.js"),
                jobId = "journey-render-2",
            )
        shoot("phonesetup-journey-failed") {
            SetupJourneyScreen(
                SetupJourneyUi(
                    checking = false,
                    options = journeyOptions,
                    progress = journeyFailed,
                ),
                NoJourneyActions,
            )
        }
    }
}
