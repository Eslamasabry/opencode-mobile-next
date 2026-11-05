@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.agents.rb.o49

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.agents.presentation.*
import io.github.eslamasabry.oc.feature.agents.ui.SignInTerminalScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class SignInTerminalParityTest {
    private class Actions : SignInTerminalActions {
        var restarted = 0
        var left = 0

        override fun openSignInPage() {}

        override fun confirmSignInPage() {}

        override fun dismissSignInPage() {}

        override fun copyCode() {}

        override fun send(line: String) {}

        override fun setAccessibleMode(on: Boolean) {}

        override fun finish() {}

        override fun startAgain() {
            restarted++
        }

        override fun leave() {
            left++
        }
    }

    @Test
    fun `a completed but unsigned-in terminal retains readable output and a retry`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SignInTerminalScreen(
                        SignInTerminalUi(
                            agentName = "Codex",
                            step = SignInStep.NotYet,
                            transcript = "Sign-in has ended",
                            canOpenPage = true,
                            code = "DEMO",
                            inputEnabled = false,
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Sign-in has ended", substring = true).assertExists()
            onNodeWithText("Open sign-in page").assertDoesNotExist()
            onNodeWithText("Sign in with Codex").performClick()
            assertEquals(1, actions.restarted)
            assertEquals(0, actions.left)
        }

    @Test
    fun `only a confirmed signed-in state automatically leaves through cleanup`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SignInTerminalScreen(
                        SignInTerminalUi(agentName = "Codex", step = SignInStep.SignedIn),
                        actions,
                    )
                }
            }
            waitForIdle()
            assertEquals(1, actions.left)
        }

    @Test
    fun `an unavailable phone sign-in never invents a start or open-page action`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SignInTerminalScreen(
                        SignInTerminalUi(agentName = "Codex", step = SignInStep.Unavailable),
                        actions,
                    )
                }
            }
            onNodeWithText("Sign-in isn't ready on this phone yet").assertExists()
            onNodeWithText("Sign in with Codex").assertDoesNotExist()
            onNodeWithText("Open sign-in page").assertDoesNotExist()
            assertEquals(0, actions.restarted)
        }

    @Test
    fun `failed shell startup keeps the technical code behind Details with a retry`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SignInTerminalScreen(
                        SignInTerminalUi(
                            agentName = "Codex",
                            step = SignInStep.Failed,
                            failureCode = "sign-in-start",
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("The shell didn't start").assertExists()
            onNodeWithText("sign-in-start").assertDoesNotExist()
            onNodeWithText("Details").performScrollTo().performClick()
            onNodeWithText("sign-in-start", substring = true).assertExists()
            onNodeWithText("Sign in with Codex").performScrollTo().performClick()
            assertEquals(1, actions.restarted)
        }
}
