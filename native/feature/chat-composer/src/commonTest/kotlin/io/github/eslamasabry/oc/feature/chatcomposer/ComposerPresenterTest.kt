package io.github.eslamasabry.oc.feature.chatcomposer

import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ApprovalMode
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerChip
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerFacts
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerNote
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerPresenter
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldEdit
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldValue
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelFacts
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ModelLabel
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ReadOnlyReason
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.SendHow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerPresenterTest {
    private val clock = TestClock()
    private val port = FakeComposerPort()
    private val openers = RecordingOpeners()

    private fun TestScope.presenter(): ComposerPresenter =
        ComposerPresenter(backgroundScope, port, openers, clock).also { testScheduler.runCurrent() }

    private fun test(body: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) { body() }

    @Test
    fun `B-STATE-100 typing hands committed words to the draft autosave and switches mic to Send`() = test {
        val p = presenter()
        assertEquals(FieldButton.Mic, p.state.value.button)
        p.onTextChanged("fix the login test")
        assertEquals(FieldButton.Send, p.state.value.button)
        assertEquals(listOf("draft:fix the login test"), port.calls)
        p.onTextChanged("fix the login test") // same words: nothing new to save
        assertEquals(1, port.calls.size)
    }

    @Test
    fun `B-AGENT-069 Send while a turn runs hands delivery a steer and clears the field`() = test {
        port.facts.value = ComposerFacts(busy = true, steering = true)
        val p = presenter()
        p.onTextChanged("also run the linter")
        assertEquals(FieldButton.AddToTurn, p.state.value.button)
        p.onSend()
        assertEquals(listOf("also run the linter" to SendHow.Steer), port.sent)
        assertEquals(FieldEdit.Change.Clear, p.state.value.edit?.change)
        assertEquals(1, p.state.value.sends)
        assertTrue("flush" in port.calls)
    }

    @Test
    fun `P09-4_3 an agent not loaded yet sends nothing and keeps the words`() = test {
        port.facts.value = ComposerFacts(busy = true)
        val p = presenter()
        p.onTextChanged("hello")
        p.onSend()
        assertTrue(port.sent.isEmpty())
        assertNull(p.state.value.edit)
        assertEquals(FieldButton.CannotSendYet, p.state.value.button)
    }

    @Test
    fun `P09-10_2 a failed send brings the words back in neutral words with Try again`() = test {
        val p = presenter()
        p.onTextChanged("ship it")
        port.failNextSend = true
        p.onSend()
        val edit = p.state.value.edit
        assertEquals(ComposerNote.SendFailed, p.state.value.note)
        assertEquals(FieldEdit.Change.Replace("ship it", 7), edit?.change)
        p.onEditApplied(edit!!.id)
        p.onTextChanged("ship it")
        p.onRetry()
        assertEquals(listOf("ship it" to SendHow.Start, "ship it" to SendHow.Start), port.sent)
        assertNull(p.state.value.note)
    }

    @Test
    fun `P09-4_4 Stop ignores taps for 800 ms after a send`() = test {
        port.facts.value = ComposerFacts(busy = true, steering = true)
        val p = presenter()
        p.onTextChanged("one more thing")
        p.onSend()
        clock.now += 799.milliseconds
        p.onStop()
        assertFalse("stop" in port.calls, "a double tap on Send never stops the turn")
        clock.now += 1.milliseconds
        p.onStop()
        assertTrue("stop" in port.calls)
    }

    @Test
    fun `P09-10_3 the chip row is model, approval, then Stop at the trailing end while a reply runs`() = test {
        port.facts.value =
            ComposerFacts(model = ModelFacts(modelId = "anthropic/claude-sonnet-5"), approval = ApprovalMode.AutoApprove)
        val p = presenter()
        val idle = p.state.value.chips
        assertEquals(2, idle.size)
        assertEquals(ModelLabel.Named("Sonnet 5"), (idle[0] as ComposerChip.Model).label)
        assertEquals(ComposerChip.Approval(ApprovalMode.AutoApprove), idle[1])
        port.facts.value = port.facts.value.copy(busy = true)
        assertEquals(ComposerChip.Stop(stuck = false), p.state.value.chips.last())
        port.facts.value = port.facts.value.copy(stuck = true)
        assertEquals(ComposerChip.Stop(stuck = true), p.state.value.chips.last(), "a stuck step turns the same Stop amber")
    }

    @Test
    fun `P09-4_4 no Stop and no glow while a request waits, and none in a read-only sub-agent chat`() = test {
        port.facts.value = ComposerFacts(busy = true, requestWaiting = true)
        val p = presenter()
        assertFalse(p.state.value.chips.any { it is ComposerChip.Stop })
        assertFalse(p.state.value.glow)
        port.facts.value = ComposerFacts(busy = true, readOnly = ReadOnlyReason.SubAgent)
        assertTrue(p.state.value.chips.isEmpty())
        assertEquals(FieldButton.None, p.state.value.button)
        assertEquals(ComposerNote.ReadOnly(ReadOnlyReason.SubAgent), p.state.value.note)
        assertFalse(p.state.value.fieldEnabled)
    }

    @Test
    fun `B-CHAT-006 the glow shows while a reply runs and a stored off stays off`() = test {
        val p = presenter()
        assertFalse(p.state.value.glow)
        port.facts.value = ComposerFacts(busy = true)
        assertTrue(p.state.value.glow, "absent setting reads as on")
        port.facts.value = ComposerFacts(busy = true, glowSetting = false)
        assertFalse(p.state.value.glow)
    }

    @Test
    fun `B-CHAT-089 arrow keys browse sent prompts through the history, else stay the field's`() = test {
        val p = presenter()
        port.historyAnswer = null
        assertFalse(p.onHistoryKey(older = true, FieldValue("two\nlines", 4), suggestionsOpen = false))
        assertNull(p.state.value.edit)
        port.historyAnswer = FieldValue("earlier prompt", 0)
        assertTrue(p.onHistoryKey(older = true, FieldValue("", 0), suggestionsOpen = false))
        assertEquals(FieldEdit.Change.Replace("earlier prompt", 0), p.state.value.edit?.change)
        assertEquals("history:older:", port.calls.last())
    }

    @Test
    fun `P09-6_3 an insert request is applied once per id, even when asked again`() = test {
        val p = presenter()
        p.insert(7, "Option B")
        val first = p.state.value.edit
        assertIs<FieldEdit.Change.Insert>(first?.change)
        p.onEditApplied(first!!.id)
        assertNull(p.state.value.edit)
        p.insert(7, "Option B")
        assertNull(p.state.value.edit, "a request already inserted is never inserted twice")
        p.insert(8, "Option C")
        assertEquals(FieldEdit.Change.Insert("Option C"), p.state.value.edit?.change)
    }

    @Test
    fun `P09-10_3 the model and approval chips open their sheets and the mic starts voice`() = test {
        val p = presenter()
        p.onModelChip()
        p.onApprovalChip()
        p.onMic()
        assertEquals(listOf("model", "approvals", "voice"), openers.opened)
    }
}
