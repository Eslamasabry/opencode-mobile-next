package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** P-103 (auditor sweep 2, req_state.png): one request in full, the others one line each until opened. */
class RequestStackRulesTest {
    private fun card(id: String, state: CardState = CardState.Waiting) =
        RequestCardUi.Question(RequestId(id), null, state, "Title", "Question $id?", oneAnswer = true, custom = false)

    private fun ids(vararg raw: String) = raw.map(::RequestId).toSet()

    @Test
    fun `P-103 three waiting requests show the first in full and the other two as one line each`() {
        val cards = listOf(card("a"), card("b"), card("c"))
        assertEquals(ids("b", "c"), RequestStackRules.folded(cards, emptySet()))
    }

    @Test
    fun `P-103 one waiting request is never folded`() {
        assertEquals(emptySet(), RequestStackRules.folded(listOf(card("a")), emptySet()))
        assertEquals(emptySet(), RequestStackRules.folded(emptyList(), emptySet()))
    }

    @Test
    fun `P-103 an opened request stays open and the first answered one hands the full place to the next`() {
        val held = card("a", CardState.Held(AnswerWords.Allowed))
        val cards = listOf(held, card("b"), card("c"), card("d"))
        // The answered card is one line already; "b" is now the first waiting one.
        assertEquals(ids("c", "d"), RequestStackRules.folded(cards, emptySet()))
        assertEquals(ids("d"), RequestStackRules.folded(cards, ids("c")))
    }

    @Test
    fun `P-103 the request a link opened the chat for is the one in full`() {
        val cards = listOf(card("a"), card("b"), card("c"))
        assertEquals(ids("a", "c"), RequestStackRules.folded(cards, emptySet(), focused = RequestId("b")))
        // A focus on a card that left changes nothing.
        assertEquals(ids("b", "c"), RequestStackRules.folded(cards, emptySet(), focused = RequestId("gone")))
    }

    @Test
    fun `P-103 an opened request that left is forgotten`() {
        val opened = ids("b", "gone")
        assertEquals(ids("b"), RequestStackRules.stillOpened(opened, listOf(card("a"), card("b"))))
        val same = ids("b")
        assertSame(same, RequestStackRules.stillOpened(same, listOf(card("a"), card("b"))))
    }
}
