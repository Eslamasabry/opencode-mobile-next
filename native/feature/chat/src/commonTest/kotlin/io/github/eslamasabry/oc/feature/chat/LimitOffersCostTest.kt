package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

/**
 * Issue 147: the limit offers of failed turns are worked out on every state the chat presents, so on every publish
 * while a reply streams. They must cost the live turn's rows and the failed turns, not a walk of every row (and not one
 * walk per failed footer).
 */
class LimitOffersCostTest {
    private object Now : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(1_762_600_000_000L)
    }

    /** A daily limit: its turn offers Switch model (B-169-011). */
    private val limit =
        "AI_APICallError: Rate limit exceeded: free-models-per-day. Add 10 credits to unlock 1000 free model " +
            "requests per day. (429)"

    private fun turn(i: Int): List<TranscriptRow> {
        val t = "u$i"
        return listOf(
            PromptRow("prompt:$t", t, "Question $i"),
            BlockRow("b:$t-a/text-0:0", t, RowKind.Prose, "text-0", 0, "Answer $i.", false, false, Origin.Loaded),
            FooterRow("footer:$t", t, failed = i % 5 == 0, reason = if (i % 5 == 0) limit else null),
        )
    }

    private fun live(n: Int): List<TranscriptRow> =
        listOf<TranscriptRow>(PromptRow("prompt:live", "live", "Write the long reply.")) +
            (0..n / 4).map { BlockRow("b:live-a/text-0:${it * 100}", "live", RowKind.Prose, "text-0", it * 100, "Words $it.", it == n / 4, false, Origin.Streamed) }

    private fun state(settled: PersistentList<TranscriptRow>, live: List<TranscriptRow>, revision: Long): TranscriptState {
        val all = settled.addAll(live)
        return TranscriptState(
            revision,
            NewestFirst(all, settled),
            all.mapIndexed { i, r -> r.key to i }.toMap().toPersistentMap(),
            RunFacts(busy = true),
        )
    }

    /** Rows looked at by the per-state walks, per publish, while 60 publishes stream after [turns] settled turns. */
    private fun perPublish(turns: Int): Pair<List<Long>, Int> {
        val world = FakeChatWorld().apply { capabilities.value = ServerCapabilities(promptModelChoice = true) }
        val settled = (0 until turns).flatMap(::turn).toPersistentList()
        world.transcript.value = state(settled, live(0), 1)
        val p = ChatPresenter(world, RecordingNavigator(), Immediate, clock = Now)
        p.current()
        var offers = 0
        val visits =
            (1..60).map { n ->
                world.transcript.value = state(settled, live(n), 1L + n)
                val before = p.rowVisits
                offers = p.current().limitSwitch.size
                p.rowVisits - before
            }
        return visits to offers
    }

    @Test
    fun `B-CHAT-096 PB-23 limit offers per publish do not grow with the settled turns loaded`() {
        val runs = listOf(20, 200, 1_000).associateWith(::perPublish)
        val summary =
            runs.entries.joinToString("; ") { (n, r) -> "$n turns: rows looked at max ${r.first.max()} mean ${r.first.sum() / r.first.size}" }
        println("OFFER_COST $summary")
        // Every failed turn still has its offer.
        assertEquals(4, runs.getValue(20).second)
        assertEquals(200, runs.getValue(1_000).second)
        assertTrue(runs.getValue(1_000).first.max() <= runs.getValue(20).first.max(), "rows looked at per publish grow with history: $summary")
    }
}
