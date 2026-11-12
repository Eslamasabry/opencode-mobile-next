package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.header.presentation.SessionCost
import io.github.eslamasabry.oc.feature.chat.ui.ContextTotals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** C4: what the conversation cost, said by whose figure (Flutter session_context_screen.dart). */
class SessionCostTest {
    @Test
    fun `the server figure wins and is marked as the server's`() {
        assertEquals(SessionCost(1.25, true), SessionCost.of(1.25, 0.4))
    }

    @Test
    fun `without a server figure the loaded sum stands in and says so`() {
        assertEquals(SessionCost(0.4, false), SessionCost.of(null, 0.4))
    }

    @Test
    fun `less than half a cent says nothing`() {
        assertNull(SessionCost.of(0.001, 0.4))
        assertNull(SessionCost.of(null, 0.001))
        assertNull(SessionCost.of(null, null))
    }

    @Test
    fun `the context sheet shows the server cost first and the loaded one otherwise`() {
        assertEquals(2.0, ContextTotals(2.0, 10L, 0.5).cost)
        assertEquals(0.5, ContextTotals(null, null, 0.5).cost)
        assertNull(ContextTotals(null, null, 0.0).cost)
        assertTrue(ContextTotals(null, 10L, 0.0).reportedByServer)
    }
}
