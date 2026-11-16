package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.presentation.LongTable
import io.github.eslamasabry.oc.feature.chat.ui.ScrollPlaceRules
import io.github.eslamasabry.oc.feature.chat.ui.TranscriptRowFacts
import io.github.eslamasabry.oc.feature.chat.ui.distinctKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList

/**
 * Issue 147: what the transcript list takes from all of its rows (unique keys, long tables, the turns to copy, the
 * newest completed footer, the item keys and the kept scroll place) runs on every publish while a reply streams. It
 * must cost the live turn's rows, whatever number of settled turns lies behind it, and give what the full walk gives.
 */
class TranscriptRowFactsCostTest {
    private fun block(turn: String, part: String, start: Int, text: String, continued: Boolean = false, continues: Boolean = false, live: Boolean = false) =
        BlockRow("b:$turn-a/$part:$start", turn, RowKind.Prose, part, start, text, live, false, Origin.Loaded, continued, continues)

    /** One settled turn, oldest row first: prompt, thought, work line, words (a two-chunk table every tenth turn), footer. */
    private fun settledTurn(i: Int): List<TranscriptRow> {
        val t = "u$i"
        val words =
            if (i % 10 == 3)
                listOf(
                    block(t, "text-0", 0, "| a | b |\n|---|---|\n| 1 | 2 |", continues = true),
                    block(t, "text-0", 30, "| 3 | 4 |", continued = true),
                )
            else listOf(block(t, "text-0", 0, "Answer $i."))
        return listOf(PromptRow("prompt:$t", t, "Question $i"), ThoughtRow("thought:$t-a/r", t, "Thinking $i")) +
            WorkLineRow("work:$t", t, 2, 0, WorkState.Done) +
            words +
            FooterRow("footer:$t", t, failed = false)
    }

    /** The live turn after [n] publishes: its prompt, the settled blocks so far and a growing tail block. */
    private fun liveTurn(n: Int): List<TranscriptRow> =
        listOf<TranscriptRow>(PromptRow("prompt:live", "live", "Write the long reply.")) +
            (0 until n / 4).map { block("live", "text-0", it * 100, "Paragraph $it.") } +
            block("live", "text-0", (n / 4) * 100, "Tail ".repeat(n % 4 + 1), live = true)

    private fun rowsOf(settled: PersistentList<TranscriptRow>, live: List<TranscriptRow>): ImmutableList<TranscriptRow> =
        NewestFirst(settled.addAll(live), settled)

    /** A list that counts the elements read from it (the scroll place's capture reads through it). */
    private class Counted(private val inner: List<String>) : AbstractList<String>(), RandomAccess {
        var reads = 0L
        override val size: Int
            get() = inner.size

        override fun get(index: Int): String {
            reads++
            return inner[index]
        }
    }

    /**
     * Rows looked at per publish while 120 publishes of one reply stream after [turns] settled turns. The conversation
     * is open already (publish 0, not counted): what its settled turns hold was found once, as the list first drew them.
     */
    private fun perPublish(turns: Int): List<Long> {
        val settled = (0 until turns).flatMap(::settledTurn).toPersistentList()
        val metadata = mapOf("u${turns - 1}" to Unit)
        val facts = TranscriptRowFacts()
        facts.of(rowsOf(settled, liveTurn(0))).also { facts.newestCompleted(it.unique, metadata) }
        return (1..120).map { n ->
            val before = facts.visits
            val rows = rowsOf(settled, liveTurn(n))
            val list = facts.of(rows)
            facts.newestCompleted(list.unique, metadata)
            val keys = Counted(facts.itemKeys(listOf("live-line"), list.unique, listOf("earlier")))
            ScrollPlaceRules.capture(keys, 0, 0)
            facts.visits - before + keys.reads
        }
    }

    @Test
    fun `B-CHAT-096 PB-21 the list's per-publish passes do not grow with the settled turns loaded`() {
        val runs = listOf(20, 200, 1_000).associateWith(::perPublish)
        val summary = runs.entries.joinToString("; ") { (n, v) -> "$n turns: rows looked at max ${v.max()} mean ${v.sum() / v.size}" }
        println("LIST_COST $summary")
        assertTrue(runs.getValue(1_000).max() <= runs.getValue(20).max(), "rows looked at per publish grow with history: $summary")
    }

    @Test
    fun `B-CHAT-096 PB-22 the kept settled part gives what a walk over every row gives`() {
        val settled = (0 until 40).flatMap(::settledTurn).toPersistentList()
        val facts = TranscriptRowFacts()
        val metadata = mapOf("u12" to Unit, "u30" to Unit)
        for (n in listOf(1, 2, 7, 8, 30)) {
            val live = liveTurn(n) + FooterRow("footer:live", "live", failed = false)
            val rows = rowsOf(settled, live)
            val got = facts.of(rows)
            val plain: ImmutableList<TranscriptRow> = settled.addAll(live).asReversed().toPersistentList()
            val unique = plain.distinctKeys()
            assertEquals(unique, got.unique)
            assertEquals(LongTable.of(unique), got.tables)
            val copyable = unique.filterIsInstance<BlockRow>().filter { it.text.isNotBlank() }.map { it.turnId }.toSet()
            for (turn in unique.map { it.turnId }.toSet() + "nobody") assertEquals(turn in copyable, got.copyable(turn), turn)
            assertEquals(
                unique.filterIsInstance<FooterRow>().firstOrNull { metadata[it.turnId] != null }?.key,
                facts.newestCompleted(got.unique, metadata),
            )
            assertEquals(
                listOf("live-line") + unique.map { it.key } + "earlier",
                facts.itemKeys(listOf("live-line"), got.unique, listOf("earlier")).toList(),
            )
        }
        // A live row that repeats a settled key: the newest wins, as before.
        val clash = rowsOf(settled, listOf(PromptRow("prompt:u3", "live", "again")))
        val expected = settled.addAll(listOf(PromptRow("prompt:u3", "live", "again"))).asReversed().toPersistentList().distinctKeys()
        assertEquals(expected, facts.of(clash).unique)
    }
}
