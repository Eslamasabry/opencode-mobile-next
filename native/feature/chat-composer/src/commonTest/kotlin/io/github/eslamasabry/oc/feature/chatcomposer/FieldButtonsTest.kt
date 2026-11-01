package io.github.eslamasabry.oc.feature.chatcomposer

import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerFacts
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButtons
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ReadOnlyReason
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.SendHow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Plan 09 §4.3 (steer, never queue) and §10.2 (field states), as the field button shows them. */
class FieldButtonsTest {
    private val running = ComposerFacts(busy = true)

    private fun button(text: String, facts: ComposerFacts) = FieldButtons.button(text.isNotBlank(), facts)

    @Test
    fun `P09-10_2 idle empty shows the mic and idle with text shows Send`() {
        assertEquals(FieldButton.Mic, button("", ComposerFacts()))
        assertEquals(FieldButton.Send, button("fix the test", ComposerFacts()))
        assertEquals(SendHow.Start, FieldButtons.how(ComposerFacts()))
    }

    @Test
    fun `P09-10_2 blank words are nothing to send, and no mic leaves no button`() {
        assertEquals(FieldButton.Mic, button("  \n ", ComposerFacts()))
        assertEquals(FieldButton.None, button("", ComposerFacts(micAvailable = false)))
    }

    @Test
    fun `B-AGENT-069 a send while a turn runs steers where steering exists, never a queue`() {
        val facts = running.copy(steering = true, midTurn = MidTurnPrompt.RestartsTurn)
        // Steering wins over whatever the server would do with a plain mid-turn prompt.
        assertEquals(FieldButton.AddToTurn, button("also check the docs", facts))
        assertEquals(SendHow.Steer, FieldButtons.how(facts))
    }

    @Test
    fun `P09-4_3 a server that joins the turn by itself adds to it with an ordinary send`() {
        val facts = running.copy(midTurn = MidTurnPrompt.JoinsTurn)
        assertEquals(FieldButton.AddToTurn, button("and the readme", facts))
        assertEquals(SendHow.JoinTurn, FieldButtons.how(facts))
    }

    @Test
    fun `P09-4_3 a server that restarts the turn shows Stop and send`() {
        val facts = running.copy(midTurn = MidTurnPrompt.RestartsTurn)
        assertEquals(FieldButton.StopAndSend, button("start over", facts))
        assertEquals(SendHow.StopThenSend, FieldButtons.how(facts))
    }

    @Test
    fun `P09-4_3 an agent not loaded yet promises nothing and sends nothing`() {
        assertEquals(FieldButton.CannotSendYet, button("hello", running))
        assertNull(FieldButtons.how(running))
    }

    @Test
    fun `P09-4_3 offline sends when back online, also while a turn runs`() {
        val facts = running.copy(offline = true, steering = true)
        assertEquals(FieldButton.SendWhenOnline, button("later", facts))
        assertEquals(SendHow.WhenOnline, FieldButtons.how(facts))
    }

    @Test
    fun `P09-10_2 read-only hides the button and a send being got ready shows Sending`() {
        assertEquals(FieldButton.None, button("x", ComposerFacts(readOnly = ReadOnlyReason.SubAgent)))
        assertEquals(FieldButton.None, button("", ComposerFacts(readOnly = ReadOnlyReason.SubAgent)))
        assertNull(FieldButtons.how(ComposerFacts(readOnly = ReadOnlyReason.SubAgent)))
        assertEquals(FieldButton.Sending, button("x", ComposerFacts(sending = true)))
    }

    @Test
    fun `P09-4_3 there is no queue choice anywhere in the send modes`() {
        // NoQueueChoiceTest: the only after-reply word is the legacy one, and no SendHow names a queue.
        assertEquals(listOf("Start", "Steer", "JoinTurn", "StopThenSend", "WhenOnline"), SendHow.entries.map { it.name })
    }
}
