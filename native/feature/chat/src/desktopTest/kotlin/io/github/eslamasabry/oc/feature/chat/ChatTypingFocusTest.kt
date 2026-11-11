package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.withKeyDown
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.ui.ChatEdgeDock
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sweep 2026-11-11 P79/P80 (w4-focus): in a conversation, typing goes to the field that was just opened (Find, a
 * sheet's field) or to nothing, never into the message box under it. The page sits in a focus park as the shell's
 * (`ShellKeyPark`, N-55): one plain focus target that holds focus when nothing on the page does, and steers a stray
 * typed key into the page with `moveFocus(Enter)`.
 *
 * The keyboard is modelled as "whatever has focus takes the words": the words go to the focused text node. Desktop
 * tests have no IME, so which editor an Android keyboard is bound to is left to the device check.
 */
@OptIn(ExperimentalTestApi::class)
class ChatTypingFocusTest {
    private val park = FocusRequester()
    private lateinit var focusManager: FocusManager

    @Composable
    private fun Park(content: @Composable () -> Unit) {
        val manager = LocalFocusManager.current
        SideEffect { focusManager = manager }
        Box(Modifier.focusRequester(park).focusTarget()) { content() }
    }

    private class Screen(val scene: ChatScene, initial: ChatUi) {
        var ui by mutableStateOf(initial)
    }

    private fun ComposeUiTest.show(): Screen {
        val scene = ChatScene()
        val screen = Screen(scene, scene.live())
        setContent { E20Scene { Park { scene.Screen(screen.ui) } } }
        waitForIdle()
        runOnIdle { park.requestFocus() }
        waitForIdle()
        return screen
    }

    private fun ComposeUiTest.composer(): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("chat-composer-field")), useUnmergedTree = true)

    private fun ComposeUiTest.findField(): SemanticsNodeInteraction =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("transcript-find-input")), useUnmergedTree = true)

    /** The words land where focus is, as a keyboard's do. */
    private fun ComposeUiTest.type(words: String) {
        onNode(isFocused() and hasSetTextAction(), useUnmergedTree = true).performTextInput(words)
        waitForIdle()
    }

    private fun SemanticsNodeInteraction.words(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /** The shell's N-55 steer for a letter typed while nothing on the page has focus. */
    private fun ComposeUiTest.strayKey() {
        runOnIdle {
            park.requestFocus()
            focusManager.moveFocus(FocusDirection.Enter)
        }
        waitForIdle()
    }

    private fun ComposeUiTest.openFind(screen: Screen) {
        runOnIdle { screen.ui = screen.ui.copy(find = FindUi("", 0, 0, null, -1, false)) }
        waitForIdle()
    }

    private fun ComposeUiTest.closeFind(screen: Screen) {
        runOnIdle { screen.ui = screen.ui.copy(find = null) }
        waitForIdle()
    }

    @Test
    fun `P79 Find opened while typing takes the keyboard and the message box keeps its words`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = show()
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            openFind(screen)
            findField().assertIsFocused()
            composer().assertIsNotFocused()
            type("bg5")
            assertEquals("bg5", findField().words(), "the search field has what was typed")
            assertEquals("", composer().words(), "no stray words in the message box")
        }

    @Test
    fun `P79 Find opened while nothing has focus takes the keyboard`() = runDesktopComposeUiTest(412, 915) {
        val screen = show()
        openFind(screen)
        findField().assertIsFocused()
        type("bg5")
        assertEquals("bg5", findField().words())
        assertEquals("", composer().words())
    }

    @Test
    fun `P79 a tap into Find's field takes the keyboard while the page holds no focus`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = show()
            openFind(screen)
            // As after the keyboard went away or a send: focus back in the park, nothing on the page focused.
            runOnIdle { park.requestFocus() }
            waitForIdle()
            findField().assertIsNotFocused()
            findField().performClick()
            waitForIdle()
            findField().assertIsFocused()
            type("bg5")
            assertEquals("bg5", findField().words())
            assertEquals("", composer().words())
        }

    @Test
    fun `P79 a tap from the message box into Find's field moves the keyboard there`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = show()
            openFind(screen)
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            findField().performClick()
            waitForIdle()
            findField().assertIsFocused()
            type("bg5")
            assertEquals("bg5", findField().words())
            assertEquals("", composer().words())
        }

    @Test
    fun `P79 closing Find gives the message box its focus back only if it had it`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = show()
            composer().performClick()
            waitForIdle()
            openFind(screen)
            findField().assertIsFocused()
            closeFind(screen)
            composer().assertIsFocused()
            // Opened with nothing focused: closing it focuses nothing.
            runOnIdle { park.requestFocus() }
            waitForIdle()
            openFind(screen)
            closeFind(screen)
            composer().assertIsNotFocused()
        }

    @Test
    fun `N-55 a letter typed with nothing focused still lands in the message box`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            strayKey()
            composer().assertIsFocused()
        }

    @Test
    fun `P80 the model sheet opened while typing releases the message box and a stray key never reaches it`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            onNodeWithTag("chat-model-button").performClick()
            waitForIdle()
            assertTrue(onAllNodesWithTag("kit-sheet").fetchSemanticsNodes().isNotEmpty(), "the model sheet is up")
            composer().assertIsNotFocused()
            strayKey()
            composer().assertIsNotFocused()
            assertEquals("", composer().words())
        }

    @Test
    fun `a sheet opened while typing takes the message box's focus and gives it back when it closes`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            // Ctrl+K: the Commands sheet, opened from the keyboard while the box has focus.
            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
            waitForIdle()
            onNodeWithTag("chat-commands-sheet").fetchSemanticsNode()
            composer().assertIsNotFocused()
            onNode(hasContentDescription("Dismiss") and hasAnyAncestor(hasTestTag("kit-sheet"))).performClick()
            waitForIdle()
            composer().assertIsFocused()
        }

    // --- Gate (w4-focus): keyboard traversal, and the dock's rename field, with the page as N-55's landing spot. ---

    /** The focused control's bounds, or null while only a plain focus target (the park, the page) holds focus. */
    private fun ComposeUiTest.focusedBounds(): Rect? =
        onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().lastOrNull()?.boundsInRoot

    /** Tab ([FocusDirection.Next]) or Shift+Tab ([FocusDirection.Previous]) [steps] times; where focus went each time. */
    private fun ComposeUiTest.walk(direction: FocusDirection, steps: Int): List<Rect> = buildList {
        repeat(steps) {
            runOnIdle { focusManager.moveFocus(direction) }
            waitForIdle()
            focusedBounds()?.let { add(it) }
        }
    }

    private fun ComposeUiTest.paneTop(): Float = onNodeWithTag("chat-composer-pane").fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `Tab with nothing focused goes to the page's controls, not into the message box`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            // One plain stop on the page itself (as on the park), then the first control.
            val seen = walk(FocusDirection.Next, 2)
            composer().assertIsNotFocused()
            assertTrue(seen.isNotEmpty(), "two Tabs focused no control at all")
        }

    @Test
    fun `Tab from the message box reaches the controls above it, round the page`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            composer().performClick()
            waitForIdle()
            val top = paneTop()
            val seen = walk(FocusDirection.Next, 80)
            assertTrue(seen.any { it.bottom <= top }, "Tab never left the composer's pane: $seen")
        }

    @Test
    fun `Shift+Tab from the message box reaches the controls under it, round the page`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            composer().performClick()
            waitForIdle()
            val field = composer().fetchSemanticsNode().boundsInRoot
            val seen = walk(FocusDirection.Previous, 80)
            assertTrue(seen.any { it.top >= field.bottom }, "Shift+Tab never reached the composer's own row: $seen")
            assertTrue(seen.any { it.bottom <= paneTop() }, "Shift+Tab never reached the controls above: $seen")
        }

    @Test
    fun `N-55 a letter typed after a Tab still lands in the message box`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            walk(FocusDirection.Next, 1)
            strayKey()
            composer().assertIsFocused()
        }

    /**
     * The edge dock's rename field as `DockSwellContent.RenameField` takes focus: it clears focus (force), waits a
     * frame, then asks for focus. The dock is drawn inside the conversation, so on the base the N-55 redirect sent
     * that request to the message box.
     */
    private class RenameDock : ChatEdgeDock {
        var renaming by mutableStateOf(false)

        @Composable
        override fun Content(chrome: PaddingValues) {
            if (!renaming) return
            val field = rememberTextFieldState("")
            val focus = remember { FocusRequester() }
            val focusManager = LocalFocusManager.current
            LaunchedEffect(Unit) {
                focusManager.clearFocus(force = true)
                withFrameNanos {}
                focus.requestFocus()
            }
            BasicTextField(field, Modifier.size(200.dp, 48.dp).focusRequester(focus).testTag("dock-rename"))
        }
    }

    @Test
    fun `the dock's rename field takes the first typing, not the message box`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            val dock = RenameDock()
            val live = scene.live()
            setContent {
                E20Scene { Park { ChatScreen(live, scene.actions, scene.host, canGoBack = false, edgeDock = dock) } }
            }
            waitForIdle()
            runOnIdle { park.requestFocus() }
            waitForIdle()
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            runOnIdle { dock.renaming = true }
            waitForIdle()
            val rename = onNode(
                    hasSetTextAction() and (hasTestTag("dock-rename") or hasAnyAncestor(hasTestTag("dock-rename"))),
                    useUnmergedTree = true,
                )
            rename.assertIsFocused()
            type("Zed")
            assertEquals("Zed", rename.words(), "the rename field has what was typed")
            assertEquals("", composer().words(), "no stray words in the message box")
        }

    @Test
    fun `Find opened as a sheet closes keeps its focus when the sheet hands the message box back`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = show()
            composer().performClick()
            waitForIdle()
            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
            waitForIdle()
            onNodeWithTag("chat-commands-sheet").fetchSemanticsNode()
            openFind(screen)
            onNode(hasContentDescription("Dismiss") and hasAnyAncestor(hasTestTag("kit-sheet"))).performClick()
            waitForIdle()
            findField().assertIsFocused()
            composer().assertIsNotFocused()
        }
}
