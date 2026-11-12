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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
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

    /** The sheet's Close and the dialog's Cancel as the strings say them (another branch renamed Dismiss to Close). */
    private val closeLabel: String by lazy { runBlocking { getString(Res.string.kitSheetClose) } }
    private val cancelLabel: String by lazy { runBlocking { getString(Res.string.kitConfirmCancel) } }

    /** Letters that reached the shell-like park (its N-55 steer); a field that holds focus uses them first. */
    private var steered = 0
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
            onNode(hasContentDescription(closeLabel) and hasAnyAncestor(hasTestTag("kit-sheet"))).performClick()
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

    /** The semantics ids Tab or Shift+Tab focuses, [steps] presses from the message box. */
    private fun ComposeUiTest.lap(direction: FocusDirection, steps: Int): List<Int> {
        composer().performClick()
        waitForIdle()
        return buildList {
            repeat(steps) {
                runOnIdle { focusManager.moveFocus(direction) }
                waitForIdle()
                onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().lastOrNull()?.let { add(it.id) }
            }
        }
    }

    @Test
    fun `Shift+Tab from the message box goes round the same controls as Tab`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            // Long enough for several laps either way; each direction must reach every control the other does (with
            // the page handing every arrival to the box, Tab skipped the controls before it, Shift+Tab those after it).
            val forward = lap(FocusDirection.Next, 120).toSet()
            val backward = lap(FocusDirection.Previous, 120).toSet()
            assertTrue(forward.size > 2, "Tab reached almost nothing: $forward")
            assertEquals(forward.sorted(), backward.sorted(), "Tab and Shift+Tab reach different controls")
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
     * Gate (w4-focus2): the shell's park now steers a letter only while the park itself holds focus, so after one Tab
     * (focus on the page's own plain stop, not the park) the letter key itself must reach the box. This harness's park
     * steers nothing: the key is pressed, not simulated.
     */
    @Test
    fun `N-55 a letter key pressed on the page's own stop after a Tab moves focus into the message box`() =
        runDesktopComposeUiTest(412, 915) {
            show()
            walk(FocusDirection.Next, 1)
            composer().assertIsNotFocused()
            onRoot().performKeyInput { pressKey(Key.H) }
            waitForIdle()
            composer().assertIsFocused()
            assertEquals("", composer().words(), "the key is used up, as the park's steer uses it")
        }

    /**
     * The edge dock's rename field as `DockSwellContent.RenameField` takes focus: it clears focus (force), waits a
     * frame, then asks for focus. The dock is drawn inside the conversation, so on the base the N-55 redirect sent
     * that request to the message box.
     */
    private class RenameDock : ChatEdgeDock {
        var renaming by mutableStateOf(false)

        @Composable
        override fun Content(chrome: PaddingValues, onBottomRoom: (Int) -> Unit) {
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
            onNode(hasContentDescription(closeLabel) and hasAnyAncestor(hasTestTag("kit-sheet"))).performClick()
            waitForIdle()
            findField().assertIsFocused()
            composer().assertIsNotFocused()
        }

    // --- w4-focus2: the kit's sheet host owns the page's focus around sheets and dialogs; the chat keeps Find's. ---

    private fun ComposeUiTest.dismissSheet(): SemanticsNodeInteraction =
        onNode(hasContentDescription(closeLabel) and hasAnyAncestor(hasTestTag("kit-sheet")))

    @Test
    fun `a sheet's row that closes the sheet and opens Find leaves the next typing in Find`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = show()
            composer().performClick()
            waitForIdle()
            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
            waitForIdle()
            onNodeWithTag("chat-commands-sheet").fetchSemanticsNode()
            // As the Commands sheet's "Find" row does it: the sheet is asked to close and Find opens in the same
            // frame, so Find's one request for focus comes while the sheet is still leaving.
            mainClock.autoAdvance = false
            dismissSheet().performSemanticsAction(SemanticsActions.OnClick)
            runOnUiThread { screen.ui = screen.ui.copy(find = FindUi("", 0, 0, null, -1, false)) }
            mainClock.autoAdvance = true
            waitForIdle()
            findField().assertIsFocused()
            composer().assertIsNotFocused()
            type("bg5")
            assertEquals("bg5", findField().words(), "the search field has what was typed")
            assertEquals("", composer().words(), "no stray words in the message box")
        }

    @Test
    fun `the dock's rename field takes the first typing right after a sheet closed`() =
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
            onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
            waitForIdle()
            onNodeWithTag("chat-commands-sheet").fetchSemanticsNode()
            // The sheet closes and the rename starts while it is still leaving (the kit hands the box its focus back
            // as it goes; the rename's "clear, wait a frame, ask" must still win).
            mainClock.autoAdvance = false
            dismissSheet().performSemanticsAction(SemanticsActions.OnClick)
            runOnUiThread { dock.renaming = true }
            mainClock.autoAdvance = true
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

    /** The chat with a header the test drives (the rename dialog, the ⋯ menu), as the header presenter would. */
    private class HeaderScreen(val scene: ChatScene) {
        var ui by mutableStateOf(scene.live())
        var header by mutableStateOf(ChatHeaderUi(title = "Fix flaky checkout test", projectName = "shopfront"))
    }

    private fun ComposeUiTest.showWithHeader(): HeaderScreen {
        val screen = HeaderScreen(ChatScene())
        setContent {
            E20Scene {
                Park {
                    ChatScreen(
                        screen.ui, screen.scene.actions, screen.scene.host,
                        header = ChatHeaderSlot(screen.header, screen.scene.headerActions),
                        canGoBack = false,
                    )
                }
            }
        }
        waitForIdle()
        runOnIdle { park.requestFocus() }
        waitForIdle()
        return screen
    }

    @Test
    fun `the rename dialog keeps its own field focused and Cancel gives the message box its focus back`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = showWithHeader()
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            runOnIdle { screen.header = screen.header.copy(renaming = "Fix flaky checkout test") }
            waitForIdle()
            composer().assertIsNotFocused()
            // The dialog's own field holds focus and takes the typing (its first words come from the dialog).
            type("Checkout")
            val dialogField = onNode(isFocused() and hasSetTextAction(), useUnmergedTree = true)
            assertTrue("Checkout" in dialogField.words(), "the dialog's field has what was typed: ${dialogField.words()}")
            assertEquals("", composer().words(), "no stray words in the message box")
            onNodeWithText(cancelLabel).performClick()
            // Cancel answers null; the header presenter then closes the dialog.
            runOnIdle { screen.header = screen.header.copy(renaming = null) }
            waitForIdle()
            composer().assertIsFocused()
            assertEquals("", composer().words())
        }

    @Test
    fun `the conversation's menu opening and closing moves no focus, from the message box or from Find`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = showWithHeader()
            composer().performClick()
            waitForIdle()
            runOnIdle { screen.header = screen.header.copy(menuOpen = true) }
            waitForIdle()
            onNodeWithTag("kit-menu").fetchSemanticsNode()
            composer().assertIsFocused()
            runOnIdle { screen.header = screen.header.copy(menuOpen = false) }
            waitForIdle()
            composer().assertIsFocused()
            runOnIdle { screen.ui = screen.ui.copy(find = FindUi("", 0, 0, null, -1, false)) }
            waitForIdle()
            findField().assertIsFocused()
            runOnIdle { screen.header = screen.header.copy(menuOpen = true) }
            waitForIdle()
            findField().assertIsFocused()
            runOnIdle { screen.header = screen.header.copy(menuOpen = false) }
            waitForIdle()
            findField().assertIsFocused()
            composer().assertIsNotFocused()
        }

    // --- Device B11 / ring rename: the shell's park as it is (re-parks a frame after focus leaves, steers letters). ---

    /**
     * The shell's `ShellKeyPark` (ui/shell nav/ShellKeys.kt) in the form this module can use: a frame after focus left
     * the page it parks again, but only if nothing took focus meanwhile; a letter that reaches it while it holds focus
     * itself (nothing on the page does) steers into the page. Keys are sent as key events, as a hardware keyboard and adb's key injection send them.
     */
    @Composable
    private fun ShellPark(content: @Composable () -> Unit) {
        val manager = LocalFocusManager.current
        SideEffect { focusManager = manager }
        var lost by remember { mutableStateOf(0) }
        val holds = remember { booleanArrayOf(false, false) }
        LaunchedEffect(lost) { if (!holds[0]) park.requestFocus() }
        Box(
            Modifier.focusRequester(park)
                .onFocusChanged {
                    holds[0] = it.hasFocus
                    holds[1] = it.isFocused
                    if (!it.hasFocus) lost++
                }
                .focusTarget()
                .onKeyEvent { e ->
                    val letter = e.utf16CodePoint > 0x1F && !e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed
                    // As the shell: only when nothing on the page holds focus (a focused field's letters are its own).
                    if (!letter || !holds[1]) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) {
                        steered++
                        park.requestFocus()
                        manager.moveFocus(FocusDirection.Enter)
                    }
                    true
                }
        ) { content() }
    }

    @Test
    fun `B11 Find opened while the message box has focus takes typed keys, the box stays as it was`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            val screen = Screen(scene, scene.live())
            setContent { E20Scene { ShellPark { scene.Screen(screen.ui) } } }
            waitForIdle()
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            openFind(screen)
            findField().assertIsFocused()
            onRoot().performKeyInput {
                pressKey(Key.B)
                pressKey(Key.G)
            }
            waitForIdle()
            // Desktop key presses do not type into a field; a key the park had used would have moved focus to the box.
            assertEquals(0, steered, "no key reached the park")
            findField().assertIsFocused()
            composer().assertIsNotFocused()
            assertEquals("", composer().words(), "the message box stays as it was")
        }

    @Test
    fun `ring rename the dock's rename field takes typed keys, the box stays as it was`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            val dock = RenameDock()
            val live = scene.live()
            setContent {
                E20Scene { ShellPark { ChatScreen(live, scene.actions, scene.host, canGoBack = false, edgeDock = dock) } }
            }
            waitForIdle()
            composer().performClick()
            waitForIdle()
            runOnIdle { dock.renaming = true }
            waitForIdle()
            val rename = onNode(
                    hasSetTextAction() and (hasTestTag("dock-rename") or hasAnyAncestor(hasTestTag("dock-rename"))),
                    useUnmergedTree = true,
                )
            rename.assertIsFocused()
            onRoot().performKeyInput {
                pressKey(Key.Z)
                pressKey(Key.E)
            }
            waitForIdle()
            assertEquals(0, steered, "no key reached the park")
            rename.assertIsFocused()
            composer().assertIsNotFocused()
            assertEquals("", composer().words(), "the message box stays as it was")
        }

    @Test
    fun `B11 device case the box focused earlier, the menu opened and closed, then Find takes typed keys`() =
        runDesktopComposeUiTest(412, 915) {
            val screen = HeaderScreen(ChatScene())
            setContent {
                E20Scene {
                    ShellPark {
                        ChatScreen(
                            screen.ui, screen.scene.actions, screen.scene.host,
                            header = ChatHeaderSlot(screen.header, screen.scene.headerActions),
                            canGoBack = false,
                        )
                    }
                }
            }
            waitForIdle()
            // The box had focus earlier (Back hides the keyboard, the box keeps its focus).
            composer().performClick()
            waitForIdle()
            composer().assertIsFocused()
            runOnIdle { screen.header = screen.header.copy(menuOpen = true) }
            waitForIdle()
            // The menu's Find: the menu closes and Find opens in the same frame.
            runOnIdle {
                screen.header = screen.header.copy(menuOpen = false)
                screen.ui = screen.ui.copy(find = FindUi("", 0, 0, null, -1, false))
            }
            waitForIdle()
            findField().assertIsFocused()
            onRoot().performKeyInput {
                pressKey(Key.B)
                pressKey(Key.G)
            }
            waitForIdle()
            // Desktop key presses do not type into a field; a key the park had used would have moved focus to the box.
            assertEquals(0, steered, "no key reached the park")
            findField().assertIsFocused()
            composer().assertIsNotFocused()
            assertEquals("", composer().words(), "the message box stays as it was")
        }
}

