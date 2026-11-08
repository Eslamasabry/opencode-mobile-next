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
        var approved = 0
        var dismissed = 0

        override fun openSignInPage() {}

        override fun confirmSignInPage() {
            approved++
        }

        override fun dismissSignInPage() {
            dismissed++
        }

        override fun copyCode() {}

        val pasted = mutableListOf<String>()

        override fun paste(text: String) {
            pasted += text
        }

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
            // The title says "Sign in with Codex" too (Flutter agent_sign_in_terminal.dart); the retry is the button.
            onNode(hasText("Sign in with Codex") and hasClickAction()).performClick()
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
            onNode(hasText("Sign in with Codex") and hasClickAction()).assertDoesNotExist()
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
            onNode(hasText("Sign in with Codex") and hasClickAction()).performScrollTo().performClick()
            assertEquals(1, actions.restarted)
        }

    @Test
    fun `a failed start says why in words and keeps the step and failure under Details`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SignInTerminalScreen(
                        SignInTerminalUi(
                            agentName = "Antigravity",
                            pastesBrowserCode = true,
                            step = SignInStep.Failed,
                            failureCode = "sign-in-start",
                            startStep = io.github.eslamasabry.oc.feature.agents.signin.SignInStartStep.Server,
                            failureDetails = "sign-in-start · server\nRuntime (ServerStartUnavailable)",
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("OpenCode on this phone didn't start.", substring = true).assertExists()
            onNodeWithText("Runtime (ServerStartUnavailable)", substring = true).assertDoesNotExist()
            onNodeWithText("Details").performScrollTo().performClick()
            onNodeWithText("Runtime (ServerStartUnavailable)", substring = true).assertExists()
            onNode(hasText("Sign in with Antigravity") and hasClickAction()).performScrollTo().performClick()
            assertEquals(1, actions.restarted)
        }

    @Test
    fun `a running sign-in offers Paste, which hands the clipboard to the terminal`() =
        runComposeUiTest {
            val actions = Actions()
            val clipboard =
                object : androidx.compose.ui.platform.ClipboardManager {
                    override fun getText() = androidx.compose.ui.text.AnnotatedString("4/0AbCd-code")

                    override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) {}
                }
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    @Suppress("DEPRECATION")
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalClipboardManager provides clipboard,
                    ) {
                        SignInTerminalScreen(
                            SignInTerminalUi(
                                agentName = "Antigravity",
                                pastesBrowserCode = true,
                                step = SignInStep.Running,
                                inputEnabled = true,
                                canOpenPage = true,
                                transcript = "Open https://accounts.google.com/o/oauth2/auth?x=1",
                            ),
                            actions,
                        )
                    }
                }
            }
            onNode(hasText("Paste") and hasClickAction()).performClick()
            assertEquals(listOf("4/0AbCd-code"), actions.pasted)
        }

    @Test
    fun `standalone terminal hosts official-page approval and dispatches the admitted action`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    SignInTerminalScreen(
                        SignInTerminalUi(
                            agentName = "Codex",
                            step = SignInStep.Running,
                            transcript = "Waiting for sign-in",
                            accessibleMode = true,
                            linkPrompt = SignInPagePrompt("auth.example", false),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Open link?").assertExists()
            onNodeWithText("Open").performClick()
            assertEquals(1, actions.approved)
            assertEquals(0, actions.left)
            assertEquals(0, actions.restarted)
        }
}
