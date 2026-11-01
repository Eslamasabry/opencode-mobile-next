package io.github.eslamasabry.oc.ui.kit.content.terminal

import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.content.ContentFakePlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Terminal accessible mode (plan 06 §7.7, 17 §2.9 `TerminalAccessibleModeTest`; DESKTOP-WEB-CROSS-30, the Dart
 * `terminal_accessibility_test` behaviours that belong to the kit): no grid node in the semantics tree, the transcript
 * stripped and capped, the field sends value + CR, and the mode is on by itself with a screen reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], qualifiers = "w412dp-h915dp-420dpi")
class TerminalAccessibleModeTest {
    @get:Rule val compose = createComposeRule()

    private val words =
        KitTerminalWords(
            terminal = "Terminal",
            accessibleMode = "Accessible mode",
            transcript = "Terminal transcript",
            noOutput = "No terminal output yet.",
            sendToTerminal = "Send to terminal",
            send = "Send",
            inputUnavailable = "Input is unavailable while disconnected.",
        )

    /** A stand-in for the Termux TerminalView: a plain Android view whose text a screen reader could read. */
    private val grid = TextView(ApplicationProvider.getApplicationContext()).apply { text = "GRID-CELL-TEXT" }

    private val platform = ContentFakePlatform()
    private val sent = mutableListOf<String>()
    private var choice by mutableStateOf<Boolean?>(null)
    private var transcript by mutableStateOf("")
    private var inputEnabled by mutableStateOf(true)

    private fun show(withGrid: Boolean = true) {
        compose.setContent {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                platform = platform,
                localeTag = "en",
                stillFrame = true,
            ) {
                CompositionLocalProvider(LocalKitTerminalSlot provides AndroidKitTerminalSlot) {
                    Box(Modifier.fillMaxSize()) {
                        KitTerminalView(
                            platformView = if (withGrid) grid else null,
                            title = "claude",
                            transcript = transcript,
                            accessibleMode = choice,
                            onAccessibleModeChange = { choice = it },
                            onSend = { sent += it },
                            words = words,
                            inputEnabled = inputEnabled,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 the live grid is one Terminal node and no grid cell reaches the semantics tree`() {
        show()
        compose.onNodeWithTag(KIT_TERMINAL_LIVE_TAG, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Terminal")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "claude"))
        compose.onAllNodesWithText("GRID-CELL-TEXT", substring = true, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, grid.importantForAccessibility)
        assertTrue("the Termux view is hosted", grid.parent != null)
        compose.onNodeWithTag(KitTerminalTags.ACCESSIBLE_MODE, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 accessible mode is off without a screen reader and on by itself with one`() {
        show()
        compose.onNodeWithTag(KitTerminalTags.TRANSCRIPT).assertDoesNotExist()
        platform.signals.value = KitSystemSignals(accessibleNavigation = true)
        compose.waitForIdle()
        compose.onNodeWithTag(KitTerminalTags.TRANSCRIPT).assertExists()
        compose.onNodeWithTag(KIT_TERMINAL_LIVE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 the person can turn accessible mode off under a screen reader and on without one`() {
        platform.signals.value = KitSystemSignals(accessibleNavigation = true)
        show()
        compose.onNodeWithText("Accessible mode", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(KitTerminalTags.ACCESSIBLE_MODE).performClick()
        compose.waitForIdle()
        assertEquals(false, choice)
        compose.onNodeWithTag(KitTerminalTags.TRANSCRIPT).assertDoesNotExist()
        compose.onNodeWithTag(KIT_TERMINAL_LIVE_TAG, useUnmergedTree = true).assertExists()

        platform.signals.value = KitSystemSignals(accessibleNavigation = false)
        compose.onNodeWithTag(KitTerminalTags.ACCESSIBLE_MODE).performClick()
        compose.waitForIdle()
        assertEquals(true, choice)
        compose.onNodeWithTag(KitTerminalTags.TRANSCRIPT).assertExists()
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 the transcript is shown stripped and capped, one node per line`() {
        choice = true
        transcript = "\u001b]0;private title\u0007Hello \u001b[31mworld\u001b[0m\r\nDone\n"
        show()
        compose.onNodeWithText("Hello world").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.onAllNodesWithText("private", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("\u001b", substring = true).assertCountEquals(0)

        transcript = "x".repeat(TerminalTranscript.MAX_CHARS + 500)
        compose.waitForIdle()
        val shown =
            compose.onNodeWithText("x", substring = true).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertEquals(TerminalTranscript.MAX_CHARS, shown.length)
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 an empty transcript says there is no output yet`() {
        choice = true
        show()
        compose.onNodeWithText("No terminal output yet.").assertExists()
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 Send writes the value and a carriage return, then clears the field`() {
        choice = true
        show()
        val input = compose.onNode(hasSetTextAction() and hasAncestorTag(KitTerminalTags.INPUT))
        input.performTextInput("pwd")
        compose.onNodeWithTag(KitTerminalTags.SEND).performClick()
        compose.waitForIdle()
        assertEquals(listOf("pwd\r"), sent)
        input.assert(SemanticsMatcher("field cleared") { node ->
            node.config.getOrNull(SemanticsProperties.EditableText)?.text.isNullOrEmpty()
        })
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 the keyboard's send action sends like the button`() {
        choice = true
        show()
        val input = compose.onNode(hasSetTextAction() and hasAncestorTag(KitTerminalTags.INPUT))
        input.performTextInput("claude --version")
        input.performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("claude --version\r"), sent)
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 an ended terminal disables input and says why`() {
        choice = true
        inputEnabled = false
        show()
        compose.onNodeWithTag(KitTerminalTags.SEND).performClick()
        compose.waitForIdle()
        assertTrue(sent.isEmpty())
        val reason = "Input is unavailable while disconnected."
        val says =
            SemanticsMatcher("states why input is off") { node ->
                node.config.getOrNull(SemanticsProperties.StateDescription) == reason ||
                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == reason }
            }
        assertTrue("the reason is stated", compose.onAllNodes(says, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 without a live view only the accessible view shows`() {
        show(withGrid = false)
        compose.onNodeWithTag(KitTerminalTags.TRANSCRIPT).assertExists()
        compose.onNodeWithTag(KIT_TERMINAL_LIVE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `DESKTOP-WEB-CROSS-30 a screen reader hears only the newest three lines, as a polite live region`() {
        choice = true
        transcript = "one\ntwo\nthree\nfour\n"
        show()
        compose.mainClock.advanceTimeBy(ANNOUNCE_EVERY_MS + 100)
        compose.waitForIdle()
        compose.onNodeWithTag(KitTerminalTags.ANNOUNCER, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, androidx.compose.ui.semantics.LiveRegionMode.Polite))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("two\nthree\nfour")))
        assertFalse(KitTerminalRules.announcement(listOf("a", "b", "c", "d")).contains("a"))
    }

    private fun hasAncestorTag(tag: String) = androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag))

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(n: Int) =
        assertEquals(n, fetchSemanticsNodes().size)
}
