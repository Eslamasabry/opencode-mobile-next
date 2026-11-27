package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * B-486: before renders of Settings > Help at 360 dp (light/dark, 1.0/1.3) plus the red assertion
 * proving Settings > Help does not yet display the phone's Linux speed mode.
 */
@OptIn(ExperimentalTestApi::class)
class B486HelpLinuxSpeedBeforeRendersTest {
    private val out = File("build/renders/agents-fast-cold-start").also { it.mkdirs() }
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun `B-SET-486 captures Settings Help before renders at 360dp`() {
        Locale.setDefault(Locale.ENGLISH)
        val variants =
            listOf(
                Triple("light-10", false, 1.0f),
                Triple("dark-10", true, 1.0f),
                Triple("light-13", false, 1.3f),
                Triple("dark-13", true, 1.3f),
            )
        for ((name, dark, scale) in variants) {
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
                                ).also { it.onOpen(SettingsPage.Group(SettingsGroupId.Help)) }
                            }
                        val state by presenter.state.collectAsState()
                        SettingsScreen(state, presenter)
                    }
                }
                waitForIdle()
                onNodeWithText("About").performScrollTo().assertIsDisplayed()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "before-help-$name.png"),
                )
            }
        }
    }

    @Test
    fun `B-SET-486 Help page without built-in Linux speed port keeps clean baseline`() {
        Locale.setDefault(Locale.ENGLISH)
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
                            ).also { it.onOpen(SettingsPage.Group(SettingsGroupId.Help)) }
                        }
                    val state by presenter.state.collectAsState()
                    SettingsScreen(state, presenter)
                }
            }
            waitForIdle()
            onNodeWithText("About").performScrollTo().assertIsDisplayed()
            onNodeWithText("Phone Linux speed").assertDoesNotExist()
        }
    }
}
