@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.agents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.agents.presentation.SignInStep
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalActions
import io.github.eslamasabry.oc.feature.agents.presentation.SignInTerminalUi
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.terminal.LocalTerminalLinkHost
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalLinkHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner, 2026-11-18: after "Login successful." the page said "isn't signed in yet", kept an empty disabled "Send
 * command to terminal" row under a program that had ended, offered a second door to the sign-in ("Open link ·
 * claude.com", the printed address that ends with a code), showed the address as a wall of query text, and touched
 * the screen edge.
 */
class SignInTerminalConfirmTest {
    private class Actions : SignInTerminalActions {
        var opened = 0
        var checked = 0
        var restarted = 0
        val pasted = mutableListOf<String>()

        override fun openSignInPage() {
            opened++
        }

        override fun confirmSignInPage() = Unit

        override fun dismissSignInPage() = Unit

        override fun copyCode() = Unit

        override fun paste(text: String) {
            pasted += text
        }

        override fun send(line: String) = Unit

        override fun setAccessibleMode(on: Boolean) = Unit

        /** "Check sign-in again" on Could not check. */
        override fun finish() {
            checked++
        }

        override fun startAgain() {
            restarted++
        }

        override fun leave() = Unit
    }

    /** The app provides a link host on every screen (MainActivity); the page must not turn it into a second door. */
    private val appLinks = TerminalLinkHost { null }

    private fun ComposeUiTest.show(state: SignInTerminalUi, actions: Actions) {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                CompositionLocalProvider(LocalTerminalLinkHost provides appLinks) {
                    SignInTerminalScreen(state, actions)
                }
            }
        }
    }

    @Test
    fun `B-AGENT-447 once the program ended the page has no code field, no Send and no input-unavailable line`() =
        runComposeUiTest {
            show(
                SignInTerminalUi(
                    agentName = "Claude Code",
                    usesClaudeAccount = true,
                    step = SignInStep.NotYet,
                    transcript = "Paste code here if prompted > Login successful.",
                    accessibleMode = true,
                ),
                Actions(),
            )
            onNodeWithText("Login successful.", substring = true).assertExists()
            for (gone in
                listOf(
                    "Terminal input unavailable",
                    "Terminal command input",
                    "Send command to terminal",
                    "Sign-in code",
                    "Send code",
                ))
                onAllNodesWithText(gone, substring = true).assertCountEquals(0)
            onNode(hasText("Paste code") and hasClickAction()).assertDoesNotExist()
            // Its intro promises Paste code; the program has ended, so the intro is gone too.
            onAllNodesWithText("choose Paste code", substring = true).assertCountEquals(0)
        }

    @Test
    fun `B-AGENT-448 Could not check offers one Check sign-in again, no new sign-in, the reason under Details`() =
        runComposeUiTest {
            val actions = Actions()
            show(
                SignInTerminalUi(
                    agentName = "Claude Code",
                    usesClaudeAccount = true,
                    step = SignInStep.CouldNotCheck,
                    transcript = "Login successful.",
                    checkCode = "agentBusy",
                ),
                actions,
            )
            onNodeWithText("Couldn't check whether Claude Code is signed in").assertExists()
            onAllNodesWithText("isn't signed in yet", substring = true).assertCountEquals(0)
            onAllNodesWithText("agentBusy").assertCountEquals(0)
            onNode(hasText("Sign in with Claude Code") and hasClickAction()).assertDoesNotExist()
            onNode(hasText("Check sign-in again") and hasClickAction()).performScrollTo().performClick()
            assertEquals(1, actions.checked)
            assertEquals(0, actions.restarted)
            onNodeWithText("Details").performScrollTo().performClick()
            onNodeWithText("agentBusy", substring = true).assertExists()
        }

    @Test
    fun `B-AGENT-449 a running sign-in has one way to its page, the address only under Details, and a code field`() =
        runComposeUiTest {
            val actions = Actions()
            val address = "https://claude.com/cai/oauth/authorize?code=true&client_id=demo&state=demo"
            show(
                SignInTerminalUi(
                    agentName = "Claude Code",
                    usesClaudeAccount = true,
                    step = SignInStep.Running,
                    inputEnabled = true,
                    accessibleMode = true,
                    canOpenPage = true,
                    pageAddress = address,
                    transcript =
                        "If the browser didn't open, visit: https://claude.com/…\n" +
                            "Paste code here if prompted >",
                ),
                actions,
            )
            onAllNodesWithText("Open link", substring = true).assertCountEquals(0)
            onAllNodesWithText("client_id", substring = true).assertCountEquals(0)
            onNode(hasText("Open sign-in page") and hasClickAction()).performClick()
            assertEquals(1, actions.opened)
            onNodeWithText("Sign-in code").assertExists()
            onNode(hasText("Send code") and hasClickAction()).assertExists()
            onNode(hasText("Paste code") and hasClickAction()).assertExists()
            onNodeWithText("Details").performScrollTo().performClick()
            onNodeWithText(address, substring = true).assertExists()
        }

    /**
     * Not a check: review renders of the page at 360 dp, text 1.0 and 1.3, in each state after the program ended and
     * while it runs. Written only when `SIGNIN_TERMINAL_RENDER_DIR` names a folder.
     */
    @Test
    fun renders() {
        Locale.setDefault(Locale.ENGLISH)
        val out = System.getenv("SIGNIN_TERMINAL_RENDER_DIR")?.let(::File) ?: return
        out.mkdirs()
        val ended = "Opening browser to sign in…\nPaste code here if prompted > Login successful."
        val states =
            mapOf(
                "running" to
                    SignInTerminalUi(
                        step = SignInStep.Running,
                        inputEnabled = true,
                        accessibleMode = true,
                        canOpenPage = true,
                        pageAddress = "https://claude.com/cai/oauth/authorize?code=true&client_id=demo&state=demo",
                        transcript =
                            "Opening browser to sign in…\n" +
                                "If the browser didn't open, visit: https://claude.com/…\n" +
                                "Paste code here if prompted >",
                    ),
                "checking" to SignInTerminalUi(step = SignInStep.Checking, transcript = ended),
                "checking-busy" to SignInTerminalUi(step = SignInStep.Checking, transcript = ended, checkBusy = true),
                "signed-in" to SignInTerminalUi(step = SignInStep.SignedIn),
                "could-not-check" to
                    SignInTerminalUi(step = SignInStep.CouldNotCheck, transcript = ended, checkCode = "agentBusy"),
                "not-yet" to SignInTerminalUi(step = SignInStep.NotYet, transcript = ended),
            )
        for ((name, state) in states)
            for (scale in listOf(1.0f, 1.3f))
                for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark))
                    shoot(
                        File(out, "signin-$name-text${scale}-${mode.name.lowercase()}.png"),
                        state.copy(agentName = "Claude Code", usesClaudeAccount = true),
                        scale,
                        mode,
                    )
    }

    private fun shoot(file: File, state: SignInTerminalUi, fontScale: Float, mode: AppearanceMode) {
        val density = 3f
        runDesktopComposeUiTest((360 * density).toInt(), (780 * density).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    KitTheme(
                        appearance = KitAppearance(mode = mode, style = Style.Solid, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Page { SignInTerminalScreen(state, Actions()) }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            ImageIO.write(onAllNodes(isRoot()).onFirst().captureToImage().toAwtImage(), "png", file)
        }
    }

    @Composable
    private fun Page(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { content() }
    }
}
