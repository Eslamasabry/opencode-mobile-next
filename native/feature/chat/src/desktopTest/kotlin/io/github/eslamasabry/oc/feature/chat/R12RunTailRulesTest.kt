package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.feature.chat.presentation.RunTail
import io.github.eslamasabry.oc.feature.chat.presentation.RunTailRules
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review #12: each row of the run-state table (RunTailRules.tail) and when a request card waits on the person. */
class R12RunTailRulesTest {
    @Test
    fun `R12 the state table - each run state and what the tail and the box show`() {
        // Idle: no live line, the box is not running.
        assertEquals(RunTail.Idle, RunTailRules.tail(busy = false, stopping = false, waiting = true))
        assertFalse(RunTailRules.liveLineShown(RunTail.Idle))
        assertFalse(RunTailRules.boxRunning(RunTail.Idle))
        // Working: the live line says what the turn does; the box runs.
        assertEquals(RunTail.Working, RunTailRules.tail(busy = true, stopping = false, waiting = false))
        assertTrue(RunTailRules.liveLineShown(RunTail.Working))
        assertTrue(RunTailRules.boxRunning(RunTail.Working))
        // Waiting for the person: the card alone; no live line, the box shows no working state.
        assertEquals(RunTail.WaitingForYou, RunTailRules.tail(busy = true, stopping = false, waiting = true))
        assertFalse(RunTailRules.liveLineShown(RunTail.WaitingForYou))
        assertFalse(RunTailRules.boxRunning(RunTail.WaitingForYou))
        // Stopping wins over a card still waiting: "Stopping" once.
        assertEquals(RunTail.Stopping, RunTailRules.tail(busy = true, stopping = true, waiting = true))
        assertTrue(RunTailRules.liveLineShown(RunTail.Stopping))
    }

    @Test
    fun `R12 a decision being sent, sent or retired by a stop waits on no one`() {
        assertTrue(RunTailRules.waitsForYou(CardState.Waiting))
        assertTrue(RunTailRules.waitsForYou(CardState.Held(AnswerWords.Allowed)))
        assertTrue(RunTailRules.waitsForYou(CardState.NotAccepted(AnswerWords.Rejected)))
        assertFalse(RunTailRules.waitsForYou(CardState.Sending(AnswerWords.RejectedWith("first l"))))
        assertFalse(RunTailRules.waitsForYou(CardState.Sent(AnswerWords.Allowed)))
        assertFalse(RunTailRules.waitsForYou(CardState.NoLongerNeeded))
        assertFalse(RunTailRules.waitsForYou(null as CardState?))
        assertTrue(RunTailRules.waitsForYou(listOf(CardState.Sending(AnswerWords.Allowed), CardState.Waiting)))
        assertFalse(RunTailRules.waitsForYou(listOf(CardState.Sending(AnswerWords.Allowed))))
    }
}
