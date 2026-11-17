package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.keyDown
import androidx.compose.ui.test.keyUp
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.input.ImeAction
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.kitEnterSends
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KitComposerFieldInputTest {
    @Test
    fun `B-CHAT-006 N19 composer tap opens keyboard and words retain focus through spaces`() =
        runComposeUiTest {
            val text = TextFieldState()
            var shows = 0
            val submitted = mutableListOf<String>()
            val keyboard =
                object : SoftwareKeyboardController {
                    override fun show() {
                        shows++
                    }

                    override fun hide() {}
                }
            setContent {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = FakeKitPlatform(),
                    localeTag = "en",
                ) {
                    CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                        KitField(
                            state = text,
                            label = "Message",
                            kind = KitFieldKind.Multiline,
                            labelShown = false,
                            framed = false,
                            composer = true,
                            multilineLines = 1..5,
                            imeAction = ImeAction.Send,
                            onSubmit = { submitted += text.text.toString() },
                        )
                    }
                }
            }
            val field = onNode(hasSetTextAction())
            field.performTouchInput { click() }
            field.assertIsFocused()
            runOnIdle { assertTrue(shows > 0) }
            field.performTextInput("first")
            // A hardware Space must not move focus away. The desktop test harness sends the key without a typed
            // character, so the space itself arrives through the text-input path, as the IME delivers it.
            field.performKeyInput { pressKey(Key.Spacebar) }
            field.assertIsFocused()
            field.performTextInput(" second")
            field.assertIsFocused()
            runOnIdle { assertEquals("first second", text.text.toString()) }
            val beforeRetap = shows
            field.performTouchInput { click() }
            runOnIdle {
                assertTrue(shows > beforeRetap, "Retapping a focused composer reopens the keyboard")
            }
            // Issue 148: the on-screen keyboard's action key is a new line, never a send.
            field.performImeAction()
            runOnIdle { assertEquals(emptyList(), submitted) }
        }

    private fun androidx.compose.ui.test.ComposeUiTest.messageBox(
        text: TextFieldState,
        submitted: MutableList<String>,
    ) {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Off),
                platform = FakeKitPlatform(),
                localeTag = "en",
            ) {
                KitField(
                    state = text,
                    label = "Message",
                    kind = KitFieldKind.Multiline,
                    labelShown = false,
                    framed = false,
                    composer = true,
                    multilineLines = 1..5,
                    imeAction = ImeAction.Send,
                    onSubmit = { submitted += text.text.toString() },
                )
            }
        }
    }

    @Test
    fun `issue 148 a message box never offers a send key on the keyboard`() = runComposeUiTest {
        val submitted = mutableListOf<String>()
        messageBox(TextFieldState(), submitted)
        val field = onNode(hasSetTextAction())
        field.performTouchInput { click() }
        field.performTextInput("one")
        field.performImeAction()
        field.performImeAction()
        runOnIdle { assertEquals(emptyList(), submitted) }
    }

    @Test
    fun `issue 148 a real keyboard Enter sends and Shift Enter makes a new line`() = runComposeUiTest {
        val submitted = mutableListOf<String>()
        val text = TextFieldState()
        messageBox(text, submitted)
        val field = onNode(hasSetTextAction())
        field.performTouchInput { click() }
        field.performTextInput("one")
        field.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(listOf("one"), submitted) }
        field.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Enter)
            keyUp(Key.ShiftLeft)
        }
        runOnIdle {
            assertEquals(listOf("one"), submitted)
            assertEquals("one\n", text.text.toString())
        }
        field.performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Enter)
            keyUp(Key.CtrlLeft)
        }
        runOnIdle { assertEquals(listOf("one", "one\n"), submitted) }
    }

    @Test
    fun `issue 148 the Enter rule is decided by the key source, not the pointer`() {
        assertTrue(kitEnterSends(fromSoftKeyboard = false, shift = false, ctrlOrMeta = false))
        assertTrue(!kitEnterSends(fromSoftKeyboard = false, shift = true, ctrlOrMeta = false))
        assertTrue(kitEnterSends(fromSoftKeyboard = false, shift = true, ctrlOrMeta = true))
        assertTrue(!kitEnterSends(fromSoftKeyboard = true, shift = false, ctrlOrMeta = false))
        assertTrue(!kitEnterSends(fromSoftKeyboard = true, shift = true, ctrlOrMeta = false))
        assertTrue(kitEnterSends(fromSoftKeyboard = true, shift = false, ctrlOrMeta = true))
    }
}
