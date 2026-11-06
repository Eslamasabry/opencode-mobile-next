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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
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
}
