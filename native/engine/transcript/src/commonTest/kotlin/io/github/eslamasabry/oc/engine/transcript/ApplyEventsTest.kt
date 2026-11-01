package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.common.ids.InboxItemId
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InboxChange
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.NoticeKind
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.Ev.SID
import io.github.eslamasabry.oc.engine.transcript.state.AssistantErrorRow
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.MarkerKind
import io.github.eslamasabry.oc.engine.transcript.state.MarkerRow
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TurnPosition
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind as RowNotice

/**
 * Events → newest-first rows (plan 09 §3.3, §3.6, §3.7, §3.8; map CHAT-GENUI-02, 05, 06). Ported from the slice-0 P3
 * reducer tests onto the core vocabulary, plus the S2-TX rows.
 */
class ApplyEventsTest {
    private val h = Harness()

    @Test
    fun `B-CHAT-121 turns group by prompt, a notice never starts a turn, rows come newest first`() {
        h.apply(Ev.prompt("u1", "Hello"))
        h.apply(Ev.notice("n1"))
        h.apply(Ev.runStarted, Ev.assistant("a1"), Ev.text("a1", "t1"), Ev.delta("a1", "t1", "Hi there."))
        h.apply(Ev.tool("a1", "c1", ToolStatus.Completed), Ev.assistant("a1", completed = 5))
        h.apply(Ev.runEnded())
        h.apply(Ev.prompt("u2", "Again"))
        val s = h.derive()
        assertEquals(listOf("prompt:u2", "footer:u1", "work:u1", "b:a1/t1:0", "notice:n1/notice", "prompt:u1"), h.keys(s))
        assertEquals("u1", (s.rows[4] as NoticeRow).turnId) // the notice joined u1's turn (it came before any step)
        assertEquals(RowNotice.InstructionsUpdated, (s.rows[4] as NoticeRow).notice)
        assertEquals(WorkState.Done, (s.rows[2] as WorkLineRow).state)
        assertEquals(0, s.indexOf("prompt:u2"))
        assertEquals(5, s.indexOf("prompt:u1"))
        assertEquals(-1, s.indexOf("nope"))
        assertEquals(TurnPosition.Only, s.rows[0].position)
        assertEquals(TurnPosition.First, s.rows[5].position)
        assertEquals(TurnPosition.Last, s.rows[1].position)
        assertEquals("a1", s.rows[3].messageId)
    }

    @Test
    fun `B-CHAT-096 settled blocks keep their instances through ten tail updates`() {
        // perf_chat_test "settled markdown and code survive ten tail updates" (TailUpdateTest, engine half).
        val prefix = "## Plan\n\nRead **this**.\n\n```dart\nfinal n = 1;\n```\n\n"
        h.apply(Ev.runStarted, Ev.assistant("a"), Ev.text("a", "t"), Ev.delta("a", "t", prefix + "Tail"))
        val first = h.derive()
        val heading = first.rows.first { it.kind == RowKind.Heading }
        val code = first.rows.first { it.kind == RowKind.Code }
        val parsesBefore = h.counters.markdownParses
        val tailBefore = h.counters.tailReparses
        var last = first
        repeat(10) {
            h.apply(Ev.delta("a", "t", "x"))
            last = h.derive()
            assertSame(heading, last.rows.first { r -> r.kind == RowKind.Heading })
            assertSame(code, last.rows.first { r -> r.kind == RowKind.Code })
        }
        assertEquals(10, h.counters.tailReparses - tailBefore)
        assertEquals(0, h.counters.markdownParses - parsesBefore)
        val tail = last.rows[0] as BlockRow
        assertEquals("Tail" + "x".repeat(10), tail.text)
        assertTrue(tail.live)
        assertEquals(Origin.Streamed, tail.origin)
        assertEquals(listOf(RowKind.Prose, RowKind.Code, RowKind.Prose, RowKind.Heading), last.rows.map { it.kind })
    }

    @Test
    fun `CHAT-GENUI-02 an open fence is one live code block and closes into a settled one`() {
        h.apply(Ev.runStarted, Ev.assistant("a"), Ev.text("a", "t"))
        h.apply(Ev.delta("a", "t", "Look:\n\n```kotlin\nval a = 1\n"))
        val open = h.derive().rows[0] as BlockRow
        assertEquals(RowKind.Code, open.kind)
        assertTrue(open.open)
        assertEquals("b:a/t:7", open.key)
        h.apply(Ev.delta("a", "t", "val b = 2\n```\nAfter"))
        val s = h.derive()
        val closed = s.rows[1] as BlockRow
        assertEquals("b:a/t:7", closed.key) // same key: the item keeps its place
        assertFalse(closed.open)
        assertFalse(closed.live)
        assertEquals("```kotlin\nval a = 1\nval b = 2\n```", closed.text)
        assertEquals("After", (s.rows[0] as BlockRow).text)
    }

    @Test
    fun `CHAT-GENUI-02 a whole-part upsert equal to the streamed text keeps the blocks, a rewrite re-parses once`() {
        h.apply(Ev.runStarted, Ev.assistant("a"), Ev.text("a", "t"), Ev.delta("a", "t", "One.\n\nTwo."))
        val before = h.derive()
        h.apply(Ev.text("a", "t", "One.\n\nTwo.", ended = true))
        val same = h.derive()
        assertSame(before.rows[1], same.rows[1])
        assertEquals(0, h.counters.markdownParses)
        h.apply(Ev.text("a", "t", "Rewritten.", ended = true))
        val rewritten = h.derive()
        assertEquals(1, h.counters.markdownParses)
        assertEquals(listOf("Rewritten."), rewritten.rows.filterIsInstance<BlockRow>().map { it.text })
    }

    @Test
    fun `B-CHAT-121 a delta before its part is kept and applied when the part arrives`() {
        h.apply(Ev.runStarted, Ev.assistant("a"))
        assertFalse(h.apply(Ev.delta("a", "t", "early ")))
        h.apply(Ev.text("a", "t"), Ev.delta("a", "t", "words"))
        assertEquals("early words", (h.derive().rows[0] as BlockRow).text)
    }

    @Test
    fun `B-CHAT-121 parts for an unknown message wait for it`() {
        assertFalse(h.apply(Ev.text("late", "t", "Hello")))
        h.apply(Ev.assistant("late"))
        assertEquals("Hello", (h.derive().rows[1] as BlockRow).text) // [0] is the finished turn's footer
    }

    @Test
    fun `OtherConversationQuietTest events of another conversation change nothing`() {
        assertFalse(h.apply(Ev.delta("a", "t", "x", session = "other")))
        assertFalse(h.apply(GatewayEvent.RunStarted(io.github.eslamasabry.oc.core.common.ids.SessionId("other"))))
        assertFalse(h.apply(GatewayEvent.MessageUpserted(Ev.info("o", Role.Assistant, session = "other"))))
        assertEquals(0, h.derive().rows.size)
    }

    @Test
    fun `DeferredBoundsTest deferred text is bounded and overflow asks for a refresh`() {
        repeat(300) { h.apply(Ev.delta("m$it", "p", "x")) }
        assertTrue(h.model.deferredKeys() <= 256)
        assertEquals(44, h.refreshes.count { it == RefreshReason.DeferredOverflow })
        h.apply(Ev.delta("big", "p", "y".repeat(1 shl 20)))
        assertTrue(h.refreshes.count { it == RefreshReason.DeferredOverflow } > 44)
    }

    @Test
    fun `B-CHAT-005 a stale transcript and a reconnect ask for a re-read instead of a replay`() {
        h.load(emptyList()) // history read once: a Connected is now a reconnect
        assertFalse(h.apply(GatewayEvent.TranscriptStale(SID)))
        assertEquals(listOf(RefreshReason.Stale), h.refreshes)
        h.apply(Ev.runStarted)
        h.apply(GatewayEvent.Connected) // drop while busy
        assertEquals(RefreshReason.Reconnected, h.refreshes.last())
        assertTrue(h.model.run.crossedDrop)
        h.apply(Ev.runEnded()) // the run that crossed a drop re-reads too
        assertEquals(RefreshReason.RunEndedAfterDrop, h.refreshes.last())
        assertFalse(h.model.run.crossedDrop)
    }

    @Test
    fun `B-PROTO-040 a finished step is not a finished run, the execution end is`() {
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted, Ev.assistant("a1"), Ev.text("a1", "t", "Step one.")))
        h.apply(Ev.assistant("a1", completed = 10)) // session.step.ended
        val between = h.derive()
        assertTrue(between.run.busy)
        assertTrue(between.rows.none { it is FooterRow }, "no footer while the run is still running")
        h.apply(Ev.assistant("a2"), Ev.text("a2", "t", "Step two."), Ev.assistant("a2", completed = 20))
        assertTrue(h.derive().run.busy)
        h.apply(Ev.runEnded())
        val ended = h.derive()
        assertFalse(ended.run.busy)
        assertEquals(1, ended.rows.count { it is FooterRow })
        // Adjacent text of both steps is one reply stretch owned by the first step (Dart `_timelineDisplayParts`), so
        // the step that ends the turn draws nothing of its own and the footer goes to the last step that draws.
        assertEquals("a1", (ended.rows[0] as FooterRow).messageId)
    }

    @Test
    fun `B-PROTO-015 adapted run endings, failures and tool input deltas land as the engine expects`() {
        // Interrupted reasons stop the work line; a failure marks the footer; streamed tool input never draws.
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted, Ev.assistant("a")))
        h.apply(Ev.tool("a", "c", ToolStatus.Pending, name = "write"))
        val before = h.derive()
        assertFalse(h.apply(Ev.delta("a", "c", "{\"path\":", field = DeltaField.ToolInputRaw)))
        assertEquals("{\"path\":", h.model.messages["a"]!!.parts["c"]!!.snapshot().tool!!.inputRaw)
        assertEquals(h.keys(before), h.keys(h.derive()))
        h.apply(Ev.runEnded(RunOutcome.Interrupted(InterruptReason.User)))
        val stopped = h.derive()
        assertEquals(WorkState.Stopped, stopped.rows.filterIsInstance<WorkLineRow>().single().state)
        assertTrue((stopped.rows[0] as FooterRow).stopped)
        h.apply(Ev.prompt("u2", "Again") + listOf(Ev.runStarted, Ev.assistant("b"), Ev.text("b", "t", "Trying.")))
        h.apply(Ev.runEnded(RunOutcome.Failed(null, null, null))) // a blank failure still gets a failed footer
        val failed = h.derive()
        assertTrue((failed.rows[0] as FooterRow).failed)
        assertTrue(failed.run.lastOutcome is RunOutcome.Failed)
    }

    @Test
    fun `CHAT-GENUI-06 the end of a run settles live blocks and adds one footer`() {
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted, Ev.assistant("a"), Ev.text("a", "t")))
        h.apply(Ev.delta("a", "t", "Working"), Ev.tool("a", "c", ToolStatus.Running))
        val running = h.derive()
        // The running turn keeps its work where it happened, after the words: newest first it is index 0.
        assertEquals(WorkState.Running, (running.rows[0] as WorkLineRow).state)
        assertTrue((running.rows[1] as BlockRow).live)
        assertTrue(running.rows.none { it is FooterRow })
        h.apply(Ev.runEnded(RunOutcome.Interrupted(InterruptReason.User)))
        val ended = h.derive()
        assertTrue(ended.rows[0] is FooterRow)
        // A turn that ended on a tool call still said something last, and that stays the answer: nothing folds.
        assertEquals(WorkState.Stopped, (ended.rows[1] as WorkLineRow).state)
        assertEquals(0, (ended.rows[1] as WorkLineRow).folded)
        assertEquals(1, ended.rows.count { it is FooterRow })
        assertTrue(ended.rows.filterIsInstance<BlockRow>().none { it.live })
    }

    @Test
    fun `B-CHAT-121 thoughts show while the turn runs and fold into the work line when it ends`() {
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted, Ev.assistant("a")))
        h.apply(Ev.reasoning("a", "r", "**Planning**\nthe steps"), Ev.reasoning("a", "r2", "No reasoning available."))
        h.apply(Ev.tool("a", "c", ToolStatus.Running))
        val live = h.derive()
        assertEquals(listOf("work:u", "thought:a/r", "prompt:u"), h.keys(live))
        assertEquals("**Planning**", (live.rows[1] as ThoughtRow).firstLine)
        h.apply(Ev.tool("a", "c", ToolStatus.Completed), Ev.text("a", "t", "Done."), Ev.runEnded())
        val done = h.derive()
        assertEquals(listOf("footer:u", "b:a/t:0", "work:u", "prompt:u"), h.keys(done))
        assertEquals(2, (done.rows[2] as WorkLineRow).thoughts)
    }

    @Test
    fun `B-CHAT-121 a recovered failure stays quiet, a failure the turn ended on reads as failed`() {
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted, Ev.assistant("a")))
        h.apply(Ev.tool("a", "c1", ToolStatus.Error, output = "boom"), Ev.tool("a", "c2", ToolStatus.Completed))
        h.apply(Ev.text("a", "t", "Done."), Ev.runEnded())
        assertEquals(WorkState.Done, h.derive().rows.filterIsInstance<WorkLineRow>().single().state)
        h.apply(Ev.prompt("u2", "Again") + listOf(Ev.runStarted, Ev.assistant("b")))
        h.apply(Ev.tool("b", "c3", ToolStatus.Error, output = "boom"), Ev.runEnded())
        val s = h.derive()
        assertEquals(WorkState.EndedFailed, s.rows.filterIsInstance<WorkLineRow>().first { it.turnId == "u2" }.state)
        // Trap T1: a declined call is the person's choice, never Failed.
        h.apply(Ev.prompt("u3", "Third") + listOf(Ev.runStarted, Ev.assistant("c")))
        h.apply(Ev.tool("c", "c4", ToolStatus.Error, output = "The user declined this"), Ev.runEnded())
        val declined = h.derive().rows.filterIsInstance<WorkLineRow>().first { it.turnId == "u3" }
        assertEquals(WorkState.Done, declined.state)
        assertEquals(0, declined.failed)
    }

    @Test
    fun `B-CHAT-121 an assistant error is its own row, quiet when more steps followed`() {
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted))
        h.apply(Ev.assistant("a1", error = "ProviderAuthError"), Ev.assistant("a2"), Ev.text("a2", "t", "Recovered."))
        h.apply(Ev.runEnded())
        val s = h.derive()
        val err = s.rows.filterIsInstance<AssistantErrorRow>().single()
        assertEquals("err:a1", err.key)
        assertTrue(err.recovered)
        assertEquals("a2", (s.rows[0] as FooterRow).messageId)
    }

    @Test
    fun `B-CHAT-121 markers and compaction rows take their own form, a passed compaction draws nothing`() {
        h.apply(Ev.prompt("u", "Go") + listOf(Ev.runStarted))
        h.apply(GatewayEvent.MessageUpserted(Ev.info("sw", Role.User)))
        h.apply(GatewayEvent.PartUpserted(Part("x", "sw", S, PartType.Switch, rawType = "v2:switch")))
        h.apply(GatewayEvent.MessageUpserted(Ev.info("k", Role.User)))
        h.apply(GatewayEvent.PartUpserted(Part("c", "k", S, PartType.Compaction, rawType = "v2:compaction", toolName = "running")))
        val running = h.derive()
        assertEquals(MarkerKind.Compacting, (running.rows[0] as MarkerRow).marker)
        assertEquals(MarkerKind.Switch, (running.rows[1] as MarkerRow).marker)
        // Anything after it settles the compaction line (B-CHAT-116): the message stays, it draws nothing.
        h.apply(Ev.assistant("a"), Ev.text("a", "t", "Continuing."))
        val after = h.derive()
        assertTrue(after.rows.none { it is MarkerRow && it.marker == MarkerKind.Compacting })
        assertEquals(4, after.messageCount)
        // An unknown v2 type degrades to a generic notice (trap T9).
        h.apply(GatewayEvent.MessageUpserted(Ev.info("z", Role.User)))
        h.apply(GatewayEvent.PartUpserted(Part("q", "z", S, PartType.Unknown, rawType = "v2:mystery")))
        assertEquals(RowNotice.Other, h.derive().rows.filterIsInstance<NoticeRow>().single().notice)
    }

    @Test
    fun `B-CHAT-097 notices keep only their kind and never create text rows`() {
        h.apply(Ev.prompt("u", "Go"))
        h.apply(Ev.notice("n", NoticeKind("note.changed")))
        h.apply(Ev.notice("n2", NoticeKind.ShellChanged))
        val s = h.derive()
        val notices = s.rows.filterIsInstance<NoticeRow>()
        assertEquals(listOf(RowNotice.Other, RowNotice.ShellChanged), notices.map { it.notice }.reversed())
        assertTrue(s.rows.none { it is PromptRow && it.messageId != "u" }, "a notice is never a prompt")
        val part = h.model.messages["n"]!!.parts.values.single().snapshot()
        assertEquals("", part.text) // no instruction or note text reaches the model
        assertEquals(RefreshReason.ShellChanged, h.refreshes.last())
    }

    @Test
    fun `B-CHAT-121 a prompt waiting in the server inbox is not a prompt row and joins the newest turn`() {
        h.apply(Ev.prompt("u", "First") + listOf(Ev.runStarted, Ev.assistant("a"), Ev.text("a", "t", "Working")))
        h.apply(GatewayEvent.InboxChanged(SID, InboxItemId("q"), InboxChange.Enqueued))
        h.apply(Ev.prompt("q", "Queued"))
        val s = h.derive()
        assertEquals(listOf("u"), s.rows.map { it.turnId }.distinct())
        assertTrue(s.rows.none { it.messageId == "q" }, "the waiting prompt is not drawn")
        h.apply(GatewayEvent.InboxChanged(SID, InboxItemId("q"), InboxChange.Delivered))
        assertEquals(RefreshReason.InboxDelivered, h.refreshes.last())
        assertEquals(listOf("q", "u"), h.derive().rows.map { it.turnId }.distinct())
    }

    @Test
    fun `B-CHAT-096 a fragmented 5,000-part reply builds one row per part`() {
        h.load(Ev.history(5000, fragmented = true))
        val s = h.derive()
        assertEquals(5001, s.rows.size) // 5,000 blocks + the turn's one footer
        assertEquals("footer:head", s.rows[0].key)
        assertEquals("b:tail/p4999:0", s.rows[1].key)
        assertEquals("b:tail/p0:0", s.rows[5000].key)
        assertEquals(5001, s.rows.map { it.key }.toSet().size)
        assertTrue(s.rows.filterIsInstance<BlockRow>().all { it.origin == Origin.Loaded })
    }

    @Test
    fun `MessageDeletedTest a removed message is gone and a page read before the removal cannot bring it back`() {
        h.load(Ev.history(4, fragmented = false))
        val start = h.model.eventVersion
        assertTrue(h.model.removeMessage("m2"))
        h.load(Ev.history(4, fragmented = false), versionAtStart = start)
        assertFalse(h.derive().rows.any { it.messageId == "m2" })
        assertEquals(listOf(MessageId("m0").raw, "m1", "tail"), h.model.messages.keys.toList())
    }
}
