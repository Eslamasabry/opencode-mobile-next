package io.github.eslamasabry.oc.feature.chatcomposer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ApprovalMode
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerFacts
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerPresenter
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldValue
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelFacts
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.SendHow
import io.github.eslamasabry.oc.feature.chatcomposer.ui.ComposerScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The composer screen over the real presenter and a fake port, on the desktop JVM. */
@OptIn(ExperimentalTestApi::class)
class ComposerScreenUiTest {
    private val clock = TestClock()
    private val port = FakeComposerPort()
    private val openers = RecordingOpeners()
    private lateinit var field: TextFieldState
    private lateinit var presenter: ComposerPresenter

    private fun ComposeUiTest.show(finePointer: Boolean = false, keepFocusOnSend: Boolean = false) {
        setContent {
            ComposerThemed {
                val scope = rememberCoroutineScope()
                val p = remember { ComposerPresenter(scope, port, openers, clock).also { presenter = it } }
                val state by p.state.collectAsState()
                field = rememberTextFieldState()
                ComposerScreen(state, p, field, finePointer = finePointer, keepFocusOnSend = keepFocusOnSend)
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.type(text: String) {
        onNode(hasSetTextAction()).performTextInput(text)
        waitForIdle()
    }

    @Test
    fun `P09-10_2 the mic gives way to Send as soon as there are words`() = runComposeUiTest {
        show()
        onNodeWithText("Talk instead of typing").performClick()
        assertEquals(listOf("voice"), openers.opened)
        type("fix the login test")
        onNodeWithText("Send").assertExists()
        onNodeWithText("Talk instead of typing").assertDoesNotExist()
        assertTrue("draft:fix the login test" in port.calls)
    }

    @Test
    fun `B-AGENT-069 while a turn runs the field offers Add to this turn and Stop sits at the row's end`() = runComposeUiTest {
        port.facts.value = ComposerFacts(busy = true, steering = true)
        show()
        type("also run the linter")
        onNodeWithText("Add to this turn").performClick()
        waitForIdle()
        assertEquals(listOf("also run the linter" to SendHow.Steer), port.sent)
        assertEquals("", field.text.toString(), "the field empties once the steer is handed over")
        clock.now += 1.seconds
        onNodeWithText("Stop").performClick()
        waitForIdle()
        assertTrue("stop" in port.calls)
    }

    @Test
    fun `P09-4_3 a server that restarts the turn says Stop and send, one not loaded yet cannot send`() = runComposeUiTest {
        port.facts.value = ComposerFacts(busy = true, midTurn = MidTurnPrompt.RestartsTurn)
        show()
        type("start over")
        onNodeWithText("Stop and send").assertExists()
        port.facts.value = ComposerFacts(busy = true)
        waitForIdle()
        onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    fun `P09-10_3 the chips show the model by name and the approval mode, and open their sheets`() = runComposeUiTest {
        port.facts.value =
            ComposerFacts(model = ModelFacts(modelId = "anthropic/claude-sonnet-5", catalogLoading = true), approval = ApprovalMode.AsksFirst)
        show()
        onNodeWithText("Sonnet 5").performClick()
        onNodeWithText("Ask first").performClick()
        assertEquals(listOf("model", "approvals"), openers.opened)
        onNodeWithText("anthropic/claude-sonnet-5", substring = true).assertDoesNotExist()
    }

    @Test
    fun `P09-10_1 with a fine pointer Enter sends and Shift+Enter is a newline`() = runComposeUiTest {
        show(finePointer = true, keepFocusOnSend = true)
        type("first line")
        onNode(hasSetTextAction()).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        waitForIdle()
        assertTrue(port.sent.isEmpty())
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1, port.sent.size)
        assertTrue(port.sent.single().first.startsWith("first line"))
        onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `P09-10_1 on a phone the field lets the keyboard go after a send`() = runComposeUiTest {
        show()
        onNode(hasSetTextAction()).performClick()
        type("ship it")
        onNode(hasSetTextAction()).assertIsFocused()
        onNodeWithText("Send").performClick()
        waitForIdle()
        onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun `B-CHAT-089 arrow up at the start browses sent prompts into the field`() = runComposeUiTest {
        port.historyAnswer = FieldValue("earlier prompt", 0)
        show()
        onNode(hasSetTextAction()).performClick()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals("earlier prompt", field.text.toString())
        assertEquals("history:older:", port.calls.first { it.startsWith("history") })
    }

    @Test
    fun `P09-10_2 a failed send puts the words back with Try again`() = runComposeUiTest {
        show()
        type("ship it")
        port.failNextSend = true
        onNodeWithText("Send").performClick()
        waitForIdle()
        assertEquals("ship it", field.text.toString())
        onNodeWithText("Your message wasn't sent", substring = true).assertExists()
        onNodeWithText("Try again").performClick()
        waitForIdle()
        assertEquals(2, port.sent.size)
    }

    @Test
    fun `P09-6_3 an agent choice is inserted at the selection once`() = runComposeUiTest {
        show()
        type("Use ")
        runOnIdle { presenter.insert(1, "option B") }
        waitForIdle()
        runOnIdle { presenter.insert(1, "option B") }
        waitForIdle()
        assertEquals("Use option B", field.text.toString())
    }
}
