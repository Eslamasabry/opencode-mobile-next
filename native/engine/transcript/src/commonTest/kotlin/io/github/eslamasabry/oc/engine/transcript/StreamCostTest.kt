package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.internal.StreamingChunk
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * STREAM-FIX: the work one publish costs must not grow with the length of the reply that is streaming (plan 09 §3.7,
 * §3.8; plan 13 §5.2 "one block re-measures per tail update"). The emulator showed the 6,000-word reply's frames
 * growing with its length; these tests pin the engine half with deterministic counters (the timings are in
 * desktopTest `StreamCostBenchmark`).
 */
class StreamCostTest {
    private val counters = TranscriptCounters()
    private val model = TranscriptModel(S, counters)
    private var revision = 0L

    private fun derive(): TranscriptState = model.derive(++revision)

    private fun blocks(s: TranscriptState) = s.rows.filterIsInstance<BlockRow>().reversed()

    /** Every block's text is the reply's own text at its offset, and together they cover every visible character. */
    private fun assertCovers(reply: String, blocks: List<BlockRow>) {
        var at = 0
        for (b in blocks) {
            assertTrue(b.start >= at, "blocks in order: ${b.key} starts at ${b.start}, before $at")
            assertTrue(reply.substring(at, b.start).isBlank(), "only blank text between blocks before ${b.key}")
            assertEquals(reply.substring(b.start, b.start + b.text.length), b.text, b.key)
            at = b.start + b.text.length
        }
        assertTrue(reply.substring(at).isBlank())
    }

    @Test
    fun `B-CHAT-096 PB-11 per-publish engine work stays flat while a 6,000-word reply grows`() {
        val reply = Reply6000.reply()
        Reply6000.opening().forEach { model.apply(it) }
        derive()
        val deltas = Reply6000.deltas(reply)
        val tailChars = ArrayList<Long>()
        val rowWrites = ArrayList<Long>()
        var shown = ""
        var last = TranscriptState.Empty
        // About one publish per 50 ms at the fixture's 40–90 deltas per second.
        deltas.chunked(3).forEach { group ->
            group.forEach { model.apply(Reply6000.delta(it)) }
            shown += group.joinToString("")
            val t0 = counters.tailChars
            val w0 = counters.rowWrites
            last = derive()
            tailChars += counters.tailChars - t0
            rowWrites += counters.rowWrites - w0
        }
        assertEquals(reply, shown)
        assertCovers(reply, blocks(last))
        // The 400-line fence used to be one tail block of up to 10,800 characters, copied on every publish; the
        // newest turn's 140 rows were re-added to the published list on every publish.
        val maxTail = tailChars.max()
        assertTrue(maxTail <= MAX_TAIL_CHARS, "tail characters per publish: max $maxTail")
        val maxWrites = rowWrites.max()
        assertTrue(maxWrites <= MAX_ROW_WRITES, "rows written per publish: max $maxWrites")
        // Flat: the last tenth of the reply costs no more per publish than the first tenth (exact counts, no clock).
        val tenth = tailChars.size / 10
        assertTrue(rowWrites.takeLast(tenth).average() <= rowWrites.take(tenth).average() + 0.5)
        // The reply ends settled, the fence as settled chunks of at most 16 lines.
        model.apply(GatewayEvent.RunEnded(S, RunOutcome.Succeeded))
        val end = blocks(derive())
        assertTrue(end.none { it.live })
        assertCovers(reply, end)
        val fence = end.filter { it.kind == RowKind.Code && it.text.startsWith("```kotlin") || it.continued }
        assertEquals(26, fence.size, fence.joinToString { "${it.key}:${it.text.lines().size}" })
        assertTrue(fence.all { it.text.lines().size <= StreamingChunk.LINES })
    }

    @Test
    fun `an open 400-line fence streams as chunks of 16 lines that join into one fence`() {
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t"), Ev.delta("a", "t", "Look:\n\n```kotlin\n"))
        val code = StringBuilder()
        var s = derive()
        for (i in 0 until 400) {
            val line = "val line$i = compute($i)\n"
            code.append(line)
            apply(Ev.delta("a", "t", line))
            s = derive()
            val tail = s.rows[0] as BlockRow
            assertTrue(tail.live && tail.open && tail.kind == RowKind.Code)
            assertTrue(tail.text.lines().size <= StreamingChunk.LINES, "live chunk lines ${tail.text.lines().size}")
        }
        val open = blocks(s).filter { it.kind == RowKind.Code }
        assertTrue(open.first().text.startsWith("```kotlin\n"))
        assertFalse(open.first().continued)
        assertTrue(open.drop(1).all { it.continued })
        assertTrue(open.dropLast(1).all { it.continues && !it.live && !it.open }) // settled chunks never change again
        assertFalse(open.last().continues)
        // Settled chunks keep their instances while the tail grows.
        val settled = open.dropLast(1)
        apply(Ev.delta("a", "t", "val more = 1\n"))
        val next = blocks(derive()).filter { it.kind == RowKind.Code }
        settled.forEachIndexed { i, b -> assertSame(b, next[i]) }
        // The fence closes into settled chunks with the same keys: items keep their places.
        apply(Ev.delta("a", "t", "```\n\nAfter."))
        val closed = blocks(derive())
        val chunks = closed.filter { it.kind == RowKind.Code }
        assertEquals(open.map { it.key }, chunks.map { it.key })
        assertTrue(chunks.none { it.open || it.live })
        assertFalse(chunks.last().continues)
        assertEquals("```kotlin\n" + code + "val more = 1\n```", chunks.joinToString("\n") { it.text })
        assertEquals("After.", closed.last().text)
    }

    @Test
    fun `a paragraph of many lines splits at line ends into continued prose chunks`() {
        val lines = (1..40).map { "Line $it of a long paragraph." }
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t"))
        lines.forEach { apply(Ev.delta("a", "t", it + "\n")) }
        apply(Ev.delta("a", "t", "\nNext."))
        val b = blocks(derive())
        val para = b.dropLast(1)
        assertEquals(listOf(RowKind.Prose), para.map { it.kind }.distinct())
        assertEquals(3, para.size)
        assertEquals(lines.joinToString("\n"), para.joinToString("\n") { it.text })
        assertEquals(listOf(false, true, true), para.map { it.continued })
        assertEquals(listOf(true, true, false), para.map { it.continues })
        assertEquals("Next.", b.last().text)
    }

    @Test
    fun `a fence chunk that ends in blank code lines keeps them`() {
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t"))
        val body = (1..15).joinToString("") { "x$it\n" } + "\n" + "y\n" // line 16 of the chunk is blank code
        apply(Ev.delta("a", "t", "```\n$body```\n"))
        val code = blocks(derive())
        assertEquals("```\n" + body + "```", code.joinToString("\n") { it.text })
    }

    @Test
    fun `a whole-part rewrite of a chunked reply re-parses once and settles`() {
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t"))
        apply(Ev.delta("a", "t", "```\n" + (1..40).joinToString("") { "v$it\n" }))
        derive()
        val rewritten = "```\n" + (1..20).joinToString("") { "w$it\n" } + "```"
        apply(Ev.text("a", "t", rewritten, ended = true))
        val b = blocks(derive())
        assertEquals(1, counters.markdownParses)
        assertEquals(rewritten, b.joinToString("\n") { it.text })
        assertTrue(b.none { it.live })
    }

    private fun apply(vararg events: GatewayEvent) = events.forEach { model.apply(it) }

    private companion object {
        /** A 16-line chunk of the fixture's code is under 500 characters; its longest paragraph about 400. */
        const val MAX_TAIL_CHARS = 1_024L

        /** The changed tail row out and in, plus the blocks that settled in this publish (the P3 model rewrote all ~140). */
        const val MAX_ROW_WRITES = 6L
    }
}
