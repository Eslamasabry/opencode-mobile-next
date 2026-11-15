package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.engine.transcript.Ev.S
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue 147: a derive groups turns again only from the first changed one and reuses the settled turns before it. What
 * it publishes must be exactly what grouping every message from scratch gives, after every kind of change: a reply
 * streaming, a new step, a message that becomes a prompt only when its words arrive, a change to an old turn, a run
 * ending and a page of older history arriving.
 */
class IncrementalDeriveTest {
    private fun reasoning(msg: String, part: String, text: String) =
        GatewayEvent.PartUpserted(Part(part, msg, S, PartType.Reasoning, text = text))

    private fun step(msg: String, call: String, status: ToolStatus, search: String) =
        GatewayEvent.PartUpserted(Part(call, msg, S, PartType.Tool, toolName = "read", toolStatus = status, searchText = search))

    private val script: List<GatewayEvent> =
        Ev.prompt("u1", "first question") +
            listOf(
                GatewayEvent.RunStarted(S),
                Ev.assistant("a1"),
                reasoning("a1", "r0", "Looking at it.\nMore."),
                step("a1", "c1", ToolStatus.Running, "read one.kt"),
                Ev.text("a1", "t0"),
                Ev.delta("a1", "t0", "The first "),
                Ev.delta("a1", "t0", "answer."),
                step("a1", "c1", ToolStatus.Completed, "read one.kt"),
                Ev.assistant("a1", completed = 10),
                GatewayEvent.RunEnded(S, RunOutcome.Succeeded),
            ) +
            Ev.prompt("u2", "second question") +
            listOf(
                GatewayEvent.RunStarted(S),
                Ev.assistant("a2"),
                Ev.text("a2", "t0"),
                Ev.delta("a2", "t0", "Second "),
                // A user message with no words yet is not a prompt: it joins the turn before it ...
                Ev.user("u3"),
                Ev.delta("a2", "t0", "answer, still going."),
                // ... until its words arrive and it starts its own turn.
                Ev.text("u3", "u3-t", "third question", ended = true),
                Ev.file("u3", "f1", "image/png", "shot.png", "data:image/png;base64,AA"),
                Ev.assistant("a3"),
                Ev.text("a3", "t0"),
                Ev.delta("a3", "t0", "Third answer."),
                // An old turn changes while the newest streams.
                Ev.assistant("a1", completed = 10, error = "Rate limit exceeded"),
                Ev.delta("a3", "t0", " More."),
                step("a3", "c9", ToolStatus.Error, "grep nothing"),
                // Succeeded, not Interrupted: a stopped run marks the work of every turn built after it Stopped, and a
                // from-scratch derive builds every turn after it (a kept turn, before and after issue 147, keeps Done).
                GatewayEvent.RunEnded(S, RunOutcome.Succeeded),
            )

    private fun fresh(events: List<GatewayEvent>, history: List<MessageWithParts>?): TranscriptState {
        val model = TranscriptModel(S, TranscriptCounters())
        events.forEach { model.apply(it) }
        history?.let { model.loadHistory(it) }
        return model.derive(1)
    }

    private fun assertSame(expected: TranscriptState, actual: TranscriptState, at: String) {
        assertEquals(expected.rows.toList(), actual.rows.toList(), "rows after $at")
        assertEquals(expected.positions, actual.positions, "positions after $at")
        assertEquals(expected.searchText.toList(), actual.searchText.toList(), "search text after $at")
    }

    @Test
    fun `B-CHAT-096 PB-24 a derive that regroups only from the first changed turn publishes what a full one does`() {
        val model = TranscriptModel(S, TranscriptCounters())
        var revision = 0L
        script.forEachIndexed { i, event ->
            model.apply(event)
            assertSame(fresh(script.take(i + 1), null), model.derive(++revision), "event $i ($event)")
        }
        // A page of older history goes before what is loaded: everything is grouped again.
        val older =
            listOf(
                MessageWithParts(MessageInfo("u0", S, Role.User), listOf(Part("u0-t", "u0", S, PartType.Text, text = "older", ended = true))),
                MessageWithParts(
                    MessageInfo("a0", S, Role.Assistant, completedMs = 1),
                    listOf(Part("t0", "a0", S, PartType.Text, text = "Older answer.", ended = true)),
                ),
            )
        model.loadHistory(older)
        assertSame(fresh(script, older), model.derive(++revision), "older history")
        model.apply(Ev.delta("a3", "t0", " After the page."))
        val expected = TranscriptModel(S, TranscriptCounters()).run {
            script.forEach { apply(it) }
            loadHistory(older)
            apply(Ev.delta("a3", "t0", " After the page."))
            derive(1)
        }
        assertSame(expected, model.derive(++revision), "a delta after the page")
    }
}
