@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.background.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.background.presentation.*
import io.github.eslamasabry.oc.feature.background.ui.BackgroundScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.*

class BackgroundParityTest {
    private class Actions : BackgroundActions {
        var resumed = 0
        var settings = 0

        override fun onBack() {}

        override fun onSwitch(on: Boolean) {}

        override fun onRestart() {}

        override fun onResume() {
            resumed++
        }

        override fun onDismissPause() {}

        override fun onAllowBattery() {}

        override fun onOpenKeepRunning() {
            settings++
        }
    }

    @Test
    fun `unsupported background service exposes no switch or invented running state`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    BackgroundScreen(
                        backgroundUi(BackgroundLiveFacts(supported = false)),
                        Actions(),
                    )
                }
            }
            onNodeWithText("Stay connected in the background").assertDoesNotExist()
            onNodeWithText("Running now").assertDoesNotExist()
        }

    @Test
    fun `failed resume shows a single compact failure and opens actual recovery`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    BackgroundScreen(
                        backgroundUi(
                            BackgroundLiveFacts(
                                supported = true,
                                pause =
                                    PauseFacts(
                                        PauseReason.UserStopped,
                                        "09:10",
                                        resumeFailed = true,
                                    ),
                            )
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Couldn't resume. Open Keep running settings.").assertExists()
            onNodeWithText("Background connection stopped when the app closed at 09:10.")
                .assertDoesNotExist()
            onNodeWithText("Open Keep running settings").performClick()
            assertEquals(1, actions.settings)
            assertEquals(0, actions.resumed)
        }
}
