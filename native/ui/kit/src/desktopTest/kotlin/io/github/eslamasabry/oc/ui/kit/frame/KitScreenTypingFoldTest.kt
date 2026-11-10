@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A chat folds an optional status line (information, an update) away while the person types. That fold must not move
 * the message box to the frame's other layout: the move dropped the box's focus, so the keyboard closed the moment it
 * opened and the conversation could not be typed in (emulator 2026-11-10: the demo chat, whose "Simulated session"
 * line always shows).
 *
 * Guard: the box is focused, the keyboard comes up, the line folds; the box is still focused and takes the words.
 */
class KitScreenTypingFoldTest {
    @Test
    fun `the message box keeps its focus when the keyboard folds an optional status line`() = runComposeUiTest {
        var keyboard by mutableStateOf(false)
        val words = TextFieldState()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en") {
                CompositionLocalProvider(LocalKitKeyboardShown provides keyboard) {
                    Box(Modifier.size(412.dp, 720.dp)) {
                        KitScreen(
                            page = true,
                            overlayChrome = true,
                            hideStatusWhenTyping = true,
                            status = KitStatus(KitStatusKind.Info, "Simulated session.", id = "demo"),
                            bottom = {
                                KitField(
                                    state = words,
                                    label = "Message",
                                    kind = KitFieldKind.Multiline,
                                    labelShown = false,
                                    framed = false,
                                    composer = true,
                                    multilineLines = 1..5,
                                )
                            },
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        onNodeWithText("Simulated session.").assertExists()
        val field = onNode(hasSetTextAction())
        field.performTouchInput { click() }
        field.assertIsFocused()

        keyboard = true
        waitForIdle()

        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasText("Simulated session.")).fetchSemanticsNodes().size, "the line folds while typing")
        onNode(hasSetTextAction()).assertIsFocused()
        onNode(hasSetTextAction()).performTextInput("hello")
        runOnIdle { assertEquals("hello", words.text.toString()) }

        // The keyboard goes: the line is back and the words stay.
        keyboard = false
        waitForIdle()
        onNodeWithText("Simulated session.").assertExists()
        runOnIdle { assertEquals("hello", words.text.toString()) }
    }
}
