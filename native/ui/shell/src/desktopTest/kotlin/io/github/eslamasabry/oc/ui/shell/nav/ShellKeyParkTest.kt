package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * N-55 (E20-S04) end to end on the desktop JVM: keys typed while nothing has focus reach the shell's park and never
 * press the first button (Servers on Home, Back in a chat); in a chat they land focus in the message field.
 */
@OptIn(ExperimentalTestApi::class)
class ShellKeyParkTest {
    /** A page: the first control (what a stray Space used to press) and, on a chat, the message field. */
    @Composable
    private fun Page(onChat: Boolean, pressed: MutableList<String>, field: TextFieldState) {
        val composer = remember { FocusRequester() }
        ShellKeyPark(onChat = { onChat }) {
            Column(
                Modifier.focusProperties {
                        onEnter = {
                            if (requestedFocusDirection == FocusDirection.Enter && onChat && composer.requestFocus())
                                cancelFocusChange()
                        }
                    }
                    .focusGroup()
            ) {
                Box(Modifier.size(48.dp).testTag("first").clickable { pressed += "first" })
                if (onChat)
                    BasicTextField(field, Modifier.size(200.dp, 48.dp).testTag("composer").focusRequester(composer))
            }
        }
    }

    @Test
    fun `B-NAV-005 typing then Space or Backspace on Home presses nothing and navigates nowhere`() = runComposeUiTest {
        val pressed = mutableListOf<String>()
        setContent { Page(onChat = false, pressed = pressed, field = rememberTextFieldState()) }
        waitForIdle()
        onRoot().performKeyInput {
            pressKey(Key.H)
            pressKey(Key.I)
            pressKey(Key.Spacebar)
            pressKey(Key.Enter)
            pressKey(Key.Backspace)
        }
        waitForIdle()
        assertEquals(emptyList(), pressed)
    }

    @Test
    fun `B-NAV-005 a letter typed in a chat with nothing focused lands focus in the message field`() =
        runComposeUiTest {
            val pressed = mutableListOf<String>()
            val field = TextFieldState()
            setContent { Page(onChat = true, pressed = pressed, field = field) }
            waitForIdle()
            onRoot().performKeyInput { pressKey(Key.H) }
            waitForIdle()
            onNodeWithTag("composer").assertIsFocused()
            onRoot().performKeyInput { pressKey(Key.Spacebar) }
            waitForIdle()
            assertEquals(emptyList(), pressed, "Space went to the field, never to the first button")
        }

    /** A chat page whose message field gives way to another field (Find; the dock's rename) that asks for focus. */
    private class Swap {
        var other by mutableStateOf(false)
        val composer = TextFieldState()
        val field = TextFieldState()
        /** The keys that reached the other field on their way in (desktop key presses do not type into a field). */
        val reached = mutableListOf<Key>()
    }

    @Composable
    private fun SwapPage(swap: Swap, clearFirst: Boolean) {
        ShellKeyPark(onChat = { true }) {
            Column {
                // Find replaces the box's place in the layout (the box's focus leaves the tree); the rename keeps it.
                if (!swap.other || clearFirst) BasicTextField(swap.composer, Modifier.size(200.dp, 48.dp).testTag("composer"))
                if (swap.other) {
                    val focus = remember { FocusRequester() }
                    val manager = LocalFocusManager.current
                    LaunchedEffect(Unit) {
                        if (clearFirst) {
                            manager.clearFocus(force = true)
                            withFrameNanos {}
                        }
                        focus.requestFocus()
                    }
                    BasicTextField(
                        swap.field,
                        Modifier.size(200.dp, 48.dp).testTag("other").focusRequester(focus).onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) swap.reached += it.key
                            false
                        },
                    )
                }
            }
        }
    }

    private fun swapCase(clearFirst: Boolean) = runComposeUiTest {
        val swap = Swap()
        setContent { SwapPage(swap, clearFirst) }
        waitForIdle()
        onNodeWithTag("composer").performClick()
        waitForIdle()
        onNodeWithTag("composer").assertIsFocused()
        runOnIdle { swap.other = true }
        waitForIdle()
        onNodeWithTag("other").assertIsFocused()
        // Key events, as a hardware keyboard (and adb's key injection) sends them, not committed text.
        onRoot().performKeyInput {
            pressKey(Key.B)
            pressKey(Key.G)
        }
        waitForIdle()
        // A key the park had used would have moved focus into the message field (N-55's steer).
        assertEquals(listOf(Key.B, Key.G), swap.reached, "the keys went to the field that asked for focus")
        onNodeWithTag("other").assertIsFocused()
        if (clearFirst) onNodeWithTag("composer").assertIsNotFocused()
        assertEquals("", swap.composer.text.toString(), "the message field stays as it was")
    }

    @Test
    fun `a field that takes focus as the message field leaves keeps it, and typed keys go to it`() = swapCase(clearFirst = false)

    @Test
    fun `a field that clears focus, waits a frame and asks keeps it, and typed keys go to it`() = swapCase(clearFirst = true)
}

