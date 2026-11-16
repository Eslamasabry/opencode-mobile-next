package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.retryBannerFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * signin-followups: the usage-limit wording added to the retry/cause rules only classifies text; it never drops a row.
 * A conversation replayed (opened again, or re-read after a stale mark) whose last message is "You've hit your weekly
 * limit" still shows that reply.
 */
class LimitReplyReplayTest {
    private val limit = "You've hit your weekly limit · resets Nov 20, 1am (UTC)"

    private fun replay(vararg events: GatewayEvent): TranscriptState {
        val model = TranscriptModel(S, TranscriptCounters())
        events.forEach { model.apply(it) }
        return model.derive(1)
    }

    private fun history(asError: Boolean): Array<GatewayEvent> =
        (Ev.prompt("u1", "Hi") +
                listOf(GatewayEvent.RunStarted(S)) +
                (if (asError) listOf(Ev.assistant("a1", completed = 5, error = limit))
                else listOf(Ev.assistant("a1", completed = 5), Ev.text("a1", "t1", limit, ended = true))) +
                listOf(GatewayEvent.RunEnded(S, if (asError) RunOutcome.Failed("UsageLimit") else RunOutcome.Succeeded)))
            .toTypedArray()

    @Test
    fun `SIGNIN-T-20 a replayed reply that is the limit sentence stays a row`() {
        val shown = replay(*history(asError = false))
        assertTrue(shown.rows.filterIsInstance<BlockRow>().any { it.text == limit }, "the limit reply is still a row")
    }

    @Test
    fun `SIGNIN-T-21 a replayed failed turn with the limit as its reason keeps its footer and reason`() {
        val footer = replay(*history(asError = true)).rows.filterIsInstance<FooterRow>().single()
        assertTrue(footer.failed)
        assertEquals(limit, footer.reason)
    }

    @Test
    fun `SIGNIN-T-22 reading the same history twice (a reload) changes nothing`() {
        val first = replay(*history(asError = true)).rows.map { it.key }
        val second = replay(*history(asError = true), *history(asError = true)).rows.map { it.key }
        assertEquals(first, second)
    }

    @Test
    fun `SIGNIN-T-23 the only other reader of the new pattern is the live retry banner, which is not a row`() {
        // It no longer calls a plan limit "Rate limited"; it adds and removes no transcript row.
        val facts = retryBannerFacts(1, null, limit, 0L)
        assertEquals(false, facts.rateLimited)
        assertEquals(1, facts.attempt)
    }
}
