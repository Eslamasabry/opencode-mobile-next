package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The reducer half of P3: events → newest-first rows (plan 09 §3.3, §3.6, §3.7, §3.8). Row ids are named where a
 * catalog row exists; the rest are slice-0 infrastructure checks (exempt from K12, as in P1).
 */
class TranscriptReducerTest {
    private val counters = TranscriptCounters()
    private val model = TranscriptModel(S, counters)
    private var revision = 0L

    private fun apply(vararg events: GatewayEvent) = events.forEach { model.apply(it) }

    private fun apply(events: List<GatewayEvent>) = events.forEach { model.apply(it) }

    private fun derive(): TranscriptState = model.derive(++revision)

    private fun TranscriptState.keys() = rows.map { it.key }

    @Test
    fun `B-CHAT-121 turns group by prompt, a notice never starts a turn, rows come newest first`() {
        apply(Ev.prompt("u1", "Hello"))
        apply(GatewayEvent.Notice(S, "n1", "n1-p", NoticeKind.InstructionsUpdated))
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a1"), Ev.text("a1", "t1"), Ev.delta("a1", "t1", "Hi there."))
        apply(Ev.tool("a1", "c1", ToolStatus.Completed), Ev.assistant("a1", completed = 5))
        apply(GatewayEvent.RunEnded(S, RunOutcome.Succeeded))
        apply(Ev.prompt("u2", "Again"))
        val s = derive()
        assertEquals(
            listOf("prompt:u2", "footer:u1", "work:u1", "b:a1/t1:0", "notice:n1-p", "prompt:u1"),
            s.keys(),
        )
        assertTrue(s.rows.all { it.turnId == "u1" || it.turnId == "u2" })
        assertEquals("u1", (s.rows[4] as NoticeRow).turnId) // the notice joined u1's turn
        assertEquals(WorkState.Done, (s.rows[2] as WorkLineRow).state)
        assertEquals(0, s.indexOf("prompt:u2"))
        assertEquals(5, s.indexOf("prompt:u1"))
        assertEquals(-1, s.indexOf("nope"))
    }

    @Test
    fun `B-CHAT-096 settled blocks keep their instances through ten tail updates`() {
        // perf_chat_test "settled markdown and code survive ten tail updates": 10 tail re-parses, 0 full parses.
        val prefix = "## Plan\n\nRead **this**.\n\n```dart\nfinal n = 1;\n```\n\n"
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t"), Ev.delta("a", "t", prefix + "Tail"))
        val first = derive()
        val heading = first.rows.first { it.kind == RowKind.Heading }
        val code = first.rows.first { it.kind == RowKind.Code }
        val parsesBefore = counters.markdownParses
        val tailBefore = counters.tailReparses
        var last = first
        repeat(10) {
            apply(Ev.delta("a", "t", "x"))
            last = derive()
            assertSame(heading, last.rows.first { r -> r.kind == RowKind.Heading })
            assertSame(code, last.rows.first { r -> r.kind == RowKind.Code })
        }
        assertEquals(10, counters.tailReparses - tailBefore)
        assertEquals(0, counters.markdownParses - parsesBefore)
        val tail = last.rows[0] as BlockRow
        assertEquals("Tail" + "x".repeat(10), tail.text)
        assertTrue(tail.live)
        assertEquals(Origin.Streamed, tail.origin)
        assertEquals(listOf(RowKind.Prose, RowKind.Code, RowKind.Prose, RowKind.Heading), last.rows.map { it.kind })
    }

    @Test
    fun `an open fence is one live code block and closes into a settled one`() {
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t"))
        apply(Ev.delta("a", "t", "Look:\n\n```kotlin\nval a = 1\n"))
        val open = derive().rows[0] as BlockRow
        assertEquals(RowKind.Code, open.kind)
        assertTrue(open.open)
        assertEquals("b:a/t:7", open.key)
        apply(Ev.delta("a", "t", "val b = 2\n```\nAfter"))
        val s = derive()
        val closed = s.rows[1] as BlockRow
        assertEquals("b:a/t:7", closed.key) // same key: the item keeps its place
        assertFalse(closed.open)
        assertFalse(closed.live)
        assertEquals("```kotlin\nval a = 1\nval b = 2\n```", closed.text)
        assertEquals("After", (s.rows[0] as BlockRow).text)
    }

    @Test
    fun `a whole-part upsert equal to the streamed text keeps the blocks, a rewrite re-parses once`() {
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t"), Ev.delta("a", "t", "One.\n\nTwo."))
        val before = derive()
        apply(Ev.text("a", "t", "One.\n\nTwo.", ended = true))
        val same = derive()
        assertSame((before.rows[1]), same.rows[1])
        assertEquals(0, counters.markdownParses)
        apply(Ev.text("a", "t", "Rewritten.", ended = true))
        val rewritten = derive()
        assertEquals(1, counters.markdownParses)
        assertEquals(listOf("Rewritten."), rewritten.rows.filterIsInstance<BlockRow>().map { it.text })
    }

    @Test
    fun `a delta before its part is kept and applied when the part arrives`() {
        apply(GatewayEvent.RunStarted(S), Ev.assistant("a"))
        assertFalse(model.apply(Ev.delta("a", "t", "early ")))
        apply(Ev.text("a", "t"), Ev.delta("a", "t", "words"))
        assertEquals("early words", (derive().rows[0] as BlockRow).text)
    }

    @Test
    fun `parts for an unknown message wait for it`() {
        assertFalse(model.apply(Ev.text("late", "t", "Hello")))
        apply(Ev.assistant("late"))
        assertEquals("Hello", (derive().rows[1] as BlockRow).text) // [0] is the finished turn's footer
    }

    @Test
    fun `events of another conversation change nothing`() {
        assertFalse(model.apply(Ev.delta("a", "t", "x", session = "other")))
        assertFalse(model.apply(GatewayEvent.RunStarted("other")))
        assertEquals(0, model.derive(1).rows.size)
    }

    @Test
    fun `deferred text is bounded and overflow asks for a refresh`() {
        repeat(300) { model.apply(Ev.delta("m$it", "p", "x")) }
        assertTrue(model.refreshRequests >= 44)
        model.apply(Ev.delta("big", "p", "y".repeat(1 shl 20)))
        assertTrue(model.refreshRequests > 44)
    }

    @Test
    fun `B-CHAT-005 a stale transcript and a reconnect ask for a re-read instead of a replay`() {
        model.apply(GatewayEvent.Connected)
        assertEquals(0, model.refreshRequests)
        assertFalse(model.apply(GatewayEvent.TranscriptStale(S)))
        assertEquals(1, model.refreshRequests)
        model.apply(GatewayEvent.RunStarted(S))
        model.apply(GatewayEvent.Connected) // drop while busy
        assertEquals(2, model.refreshRequests)
        model.apply(GatewayEvent.RunEnded(S, RunOutcome.Succeeded)) // the run that crossed a drop re-reads too
        assertEquals(3, model.refreshRequests)
    }

    @Test
    fun `the end of a run settles live blocks and adds one footer`() {
        apply(Ev.prompt("u", "Go") + listOf(GatewayEvent.RunStarted(S), Ev.assistant("a"), Ev.text("a", "t")))
        apply(Ev.delta("a", "t", "Working"), Ev.tool("a", "c", ToolStatus.Running))
        val running = derive()
        // The work line sits where the first tool call came, after the words: newest first it is index 0.
        assertEquals(WorkState.Running, (running.rows[0] as WorkLineRow).state)
        assertTrue((running.rows[1] as BlockRow).live)
        assertTrue(running.rows.none { it is FooterRow })
        apply(GatewayEvent.RunEnded(S, RunOutcome.Interrupted("user")))
        val ended = derive()
        assertTrue(ended.rows[0] is FooterRow)
        assertEquals(WorkState.Stopped, (ended.rows[1] as WorkLineRow).state)
        assertFalse((ended.rows[2] as BlockRow).live)
        assertEquals(1, ended.rows.count { it is FooterRow })
    }

    @Test
    fun `newest turn only - ten deltas into a 100-turn chat pass at most 20 messages`() {
        // chat_rebuild_budget_test: "Ten flushes of a two-message turn, not ten passes over 200 messages."
        model.loadHistory(Ev.history(200, fragmented = false))
        derive()
        val before = counters.displayPassMessages
        repeat(10) {
            apply(Ev.delta("tail", "p199", " more"))
            derive()
        }
        assertTrue(counters.displayPassMessages - before <= 10 * 2, "passed ${counters.displayPassMessages - before}")
    }

    @Test
    fun `the settled prefix is shared, never copied, between publishes`() {
        model.loadHistory(Ev.history(1000, fragmented = false))
        val a = derive()
        apply(Ev.delta("tail", "p999", "x"))
        val b = derive()
        assertEquals(a.rows.size, b.rows.size)
        // Every row but the changed tail block is the same instance.
        val changed = a.rows.indices.count { a.rows[it] !== b.rows[it] }
        assertEquals(1, changed)
        val srcA = (a.rows as NewestFirst).source
        val srcB = (b.rows as NewestFirst).source
        assertSame(srcA[0], srcB[0])
        assertTrue(b.rows[0] is FooterRow)
        assertTrue(b.rows[1] is BlockRow)
        assertTrue(b.rows.last() is PromptRow)
    }

    @Test
    fun `B-CHAT-096 a fragmented 5,000-part reply builds one row per part`() {
        model.loadHistory(Ev.history(5000, fragmented = true))
        val s = derive()
        assertEquals(5001, s.rows.size) // 5,000 blocks + the turn's one footer
        assertEquals("footer:head", s.rows[0].key)
        assertEquals("b:tail/p4999:0", s.rows[1].key)
        assertEquals("b:tail/p0:0", s.rows[5000].key)
        assertEquals(5001, s.rows.map { it.key }.toSet().size)
    }
}
