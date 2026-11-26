package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.feature.settings.presentation.LinuxSpeedFacts
import io.github.eslamasabry.oc.feature.settings.presentation.LinuxSpeedMode
import io.github.eslamasabry.oc.feature.settings.presentation.LinuxSpeedSetting
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * B-486: proves Settings > Help displays phone Linux speed mode in plain words, hides the row
 * when built-in Linux is not installed, offers one tap to try fast mode again when in compatible
 * mode, and captures after renders at 360 dp (light/dark, 1.0/1.3).
 */
@OptIn(ExperimentalTestApi::class)
class B486HelpLinuxSpeedUiTest {
    private val out = File("build/b486-renders").also { it.mkdirs() }
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private class FakeLinuxSpeed(
        initial: LinuxSpeedFacts,
        private val onRetry: () -> Outcome<LinuxSpeedMode> = { Outcome.Ok(LinuxSpeedMode.Fast) },
    ) : LinuxSpeedSetting {
        val flow = MutableStateFlow(initial)
        override val facts: StateFlow<LinuxSpeedFacts> = flow
        var refreshCalls = 0
        var retryCalls = 0

        override fun refresh() {
            refreshCalls++
        }

        override suspend fun tryFastMode(): Outcome<LinuxSpeedMode> {
            retryCalls++
            val result = onRetry()
            if (result is Outcome.Ok) {
                flow.value = flow.value.copy(installed = true, mode = result.value)
            }
            return result
        }
    }

    @Test
    fun `B-SET-486 Help shows Fast mode in plain words without retry row and captures renders`() {
        Locale.setDefault(Locale.ENGLISH)
        val variants =
            listOf(
                Triple("light-10", false, 1.0f),
                Triple("dark-10", true, 1.0f),
                Triple("light-13", false, 1.3f),
                Triple("dark-13", true, 1.3f),
            )
        for ((name, dark, scale) in variants) {
            val port = FakeLinuxSpeed(LinuxSpeedFacts(installed = true, mode = LinuxSpeedMode.Fast))
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    SettingsThemed(dark = dark, locale = "en", fontScale = scale, width = 360f) {
                        val scope = rememberCoroutineScope()
                        val presenter =
                            remember {
                                SettingsPresenter(
                                    scope,
                                    MutableStateFlow(phoneFacts()),
                                    FakeChoices(),
                                    RecordingOpeners(),
                                    linuxSpeed = port,
                                ).also { it.onOpen(SettingsPage.Group(SettingsGroupId.Help)) }
                            }
                        val state by presenter.state.collectAsState()
                        SettingsScreen(state, presenter)
                    }
                }
                waitForIdle()
                onNodeWithText("Phone Linux speed").performScrollTo().assertIsDisplayed()
                onNodeWithText("Fast").assertIsDisplayed()
                onNodeWithText("Running in fast mode.").assertIsDisplayed()
                onNodeWithTag("settings-help-try-fast-mode").assertDoesNotExist()
                assertEquals(1, port.refreshCalls)
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "after-help-fast-$name.png"),
                )
            }
        }
    }

    @Test
    fun `B-SET-486 Help shows Compatible mode and one tap switches back to Fast`() {
        Locale.setDefault(Locale.ENGLISH)
        val variants =
            listOf(
                Triple("light-10", false, 1.0f),
                Triple("dark-10", true, 1.0f),
                Triple("light-13", false, 1.3f),
                Triple("dark-13", true, 1.3f),
            )
        for ((name, dark, scale) in variants) {
            val port =
                FakeLinuxSpeed(LinuxSpeedFacts(installed = true, mode = LinuxSpeedMode.Compatible)) {
                    Outcome.Ok(LinuxSpeedMode.Fast)
                }
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    SettingsThemed(dark = dark, locale = "en", fontScale = scale, width = 360f) {
                        val scope = rememberCoroutineScope()
                        val presenter =
                            remember {
                                SettingsPresenter(
                                    scope,
                                    MutableStateFlow(phoneFacts()),
                                    FakeChoices(),
                                    RecordingOpeners(),
                                    linuxSpeed = port,
                                ).also { it.onOpen(SettingsPage.Group(SettingsGroupId.Help)) }
                            }
                        val state by presenter.state.collectAsState()
                        SettingsScreen(state, presenter)
                    }
                }
                waitForIdle()
                onNodeWithText("Phone Linux speed").performScrollTo().assertIsDisplayed()
                onNodeWithText("Compatible (slower)").assertIsDisplayed()
                onNodeWithText("Try fast mode again").performScrollTo().assertIsDisplayed()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "after-help-compat-$name.png"),
                )
                onNodeWithText("Try fast mode again").performClick()
                waitForIdle()
                assertEquals(1, port.retryCalls)
                onNodeWithText("Fast").assertIsDisplayed()
                onNodeWithText("Fast mode is on again.").assertIsDisplayed()
                onNodeWithTag("settings-help-try-fast-mode").assertDoesNotExist()
            }
        }
    }

    @Test
    fun `B-SET-486 Help stays in Compatible mode with plain words when fast probe still fails`() {
        Locale.setDefault(Locale.ENGLISH)
        val port =
            FakeLinuxSpeed(LinuxSpeedFacts(installed = true, mode = LinuxSpeedMode.Compatible)) {
                Outcome.Ok(LinuxSpeedMode.Compatible)
            }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    val scope = rememberCoroutineScope()
                    val presenter =
                        remember {
                            SettingsPresenter(
                                scope,
                                MutableStateFlow(phoneFacts()),
                                FakeChoices(),
                                RecordingOpeners(),
                                linuxSpeed = port,
                            ).also { it.onOpen(SettingsPage.Group(SettingsGroupId.Help)) }
                        }
                    val state by presenter.state.collectAsState()
                    SettingsScreen(state, presenter)
                }
            }
            waitForIdle()
            onNodeWithText("Try fast mode again").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Compatible (slower)").assertIsDisplayed()
            onNodeWithText("This phone still needs compatible mode.").assertIsDisplayed()
            onNodeWithText("Try fast mode again").assertIsDisplayed()
        }
    }

    @Test
    fun `B-SET-486 Help reports retry failure plainly and hides row when Linux not installed`() {
        Locale.setDefault(Locale.ENGLISH)
        val failing =
            FakeLinuxSpeed(LinuxSpeedFacts(installed = true, mode = LinuxSpeedMode.Compatible)) {
                Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
            }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    val scope = rememberCoroutineScope()
                    val presenter =
                        remember {
                            SettingsPresenter(
                                scope,
                                MutableStateFlow(phoneFacts()),
                                FakeChoices(),
                                RecordingOpeners(),
                                linuxSpeed = failing,
                            ).also { it.onOpen(SettingsPage.Group(SettingsGroupId.Help)) }
                        }
                    val state by presenter.state.collectAsState()
                    SettingsScreen(state, presenter)
                }
            }
            waitForIdle()
            onNodeWithText("Try fast mode again").performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Could not test fast mode right now. Compatible mode is kept.").assertIsDisplayed()
        }

        val notInstalled = FakeLinuxSpeed(LinuxSpeedFacts(installed = false))
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    val scope = rememberCoroutineScope()
                    val presenter =
                        remember {
                            SettingsPresenter(
                                scope,
                                MutableStateFlow(phoneFacts()),
                                FakeChoices(),
                                RecordingOpeners(),
                                linuxSpeed = notInstalled,
                            ).also { it.onOpen(SettingsPage.Group(SettingsGroupId.Help)) }
                        }
                    val state by presenter.state.collectAsState()
                    SettingsScreen(state, presenter)
                }
            }
            waitForIdle()
            onNodeWithTag("settings-help-linux-speed").assertDoesNotExist()
        }
    }
}
