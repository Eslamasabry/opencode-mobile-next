package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.feature.chats.ui.fieldMayTakeState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewConversationTypedAheadTest {
    @Test
    fun `a stored draft never replaces letters typed before the field reported them`() {
        // The person typed "hel" in a burst; the presenter last heard "h" and a late draft arrives.
        assertFalse(fieldMayTakeState(fieldText = "hel", reportedText = "h"))
    }

    @Test
    fun `a starter or a cleared prompt still reaches a field the presenter has heard from`() {
        assertTrue(fieldMayTakeState(fieldText = "hello", reportedText = "hello"))
        assertTrue(fieldMayTakeState(fieldText = "", reportedText = ""))
    }
}
