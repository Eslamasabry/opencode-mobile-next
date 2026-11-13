package io.github.eslamasabry.oc.feature.chat.ui

import kotlin.test.Test
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
}
