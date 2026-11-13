package io.github.eslamasabry.oc.feature.chat.ui

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A declined step shows the note the person sent with Deny under "You declined this". */
class DeclinedNoteTest {
    @Test
    fun `B-CHAT-REV3-020 the note after the decline sentence is the person's, and no note is none`() {
        assertEquals("use the other folder", declinedNote("The user declined this tool call\nuse the other folder"))
        assertEquals("line one\nline two", declinedNote("The user declined this tool call\n  line one\nline two  "))
        assertNull(declinedNote("The user declined this tool call"))
        assertNull(declinedNote("The user declined this tool call\n   "))
        assertNull(declinedNote(null))
    }

    @Test
    fun `RC1-DENY-001 a declined step's row carries the note itself and does not open, other steps keep theirs`() {
        val error = "The user declined this tool call\nuse rc2.txt"
        assertEquals("use rc2.txt", declinedStepNote(StepState.Declined, error))
        assertFalse(stepOpens(StepState.Declined), "nothing more to open: the row says it all")
        assertNull(declinedStepNote(StepState.Declined, "The user declined this tool call"), "a Deny without a note")
        assertNull(declinedStepNote(StepState.Failed, error), "only a declined step reads it as the person's note")
        assertTrue(stepOpens(StepState.Done))
    }
}
