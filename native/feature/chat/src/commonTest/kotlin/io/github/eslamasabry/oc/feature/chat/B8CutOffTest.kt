package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B8 (device basics-2): a run cut short when the server stopped (the phone's own server dies with a force-stopped
 * app) reopened to the prompt and nothing else. The shapes are what OpenCode 2.2.6 really gives back after its
 * process was killed 5 s and 25 s into a run and started again (the session's status read idle, nothing resumed):
 * the prompt, then the step's assistant message with its model, a creation time, no completion, no error, and one
 * empty reasoning part (the streamed words were never durable).
 */
class B8CutOffTest {
    private val sid = Ref.sessionId.raw

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun prompt(text: String = "write 400 numbered lines about rivers, one per line") =
        MessageWithParts(
            MessageInfo("m1", sid, Role.User, time = MsgTime(516_091, null)),
            listOf(Part("text-0", "m1", sid, PartType.Text, "text", text = text, ended = true)).toImmutableList(),
        )

    /** The step OpenCode 2 started and never ended: `{"type":"assistant","time":{"created":…},"content":[{"type":"reasoning","text":""}]}`. */
    private fun unfinished(vararg parts: Part, id: String = "m2", created: Long = 516_098) =
        MessageWithParts(
            MessageInfo(id, sid, Role.Assistant, agent = "build", providerId = "opencode", modelId = "big-pickle", time = MsgTime(created, null)),
            parts.toList().toImmutableList(),
        )

    private fun finished(vararg parts: Part, id: String) =
        MessageWithParts(
            MessageInfo(id, sid, Role.Assistant, agent = "build", providerId = "opencode", modelId = "big-pickle", time = MsgTime(516_100, 520_000), finish = "tool-calls"),
            parts.toList().toImmutableList(),
        )

    private fun emptyThought(message: String = "m2") = Part("reasoning-0", message, sid, PartType.Reasoning, "reasoning", text = "")

    private fun words(text: String, message: String) = Part("text-1", message, sid, PartType.Text, "text", text = text)

    private fun tool(status: ToolStatus, output: String?, message: String) =
        Part(
            "t-write",
            message,
            sid,
            PartType.Tool,
            "tool",
            callId = "c-write",
            toolName = "write",
            tool = ToolState(status, input = JsonObject(mapOf("filePath" to JsonPrimitive("/p/rivers.txt"))), output = output, startedAtMs = 517_000, completedAtMs = 518_000),
        )

    /** As the chat opens it: the status read, the newest page, then the server asked again (said = it answered idle). */
    private fun facts(
        page: List<MessageWithParts>,
        outcome: RunOutcome? = null,
        busy: Boolean = false,
        said: Boolean = true,
        waiting: Boolean = false,
    ) =
        TurnFeedKeeper(sid).run {
            if (busy) running(516_086)
            history(page, newest = true)
            idle(said)
            TurnFactsPresenter(FixedClock).present(feed.value.copy(lastOutcome = outcome), 600_000, waiting)
        }

    private fun note(keeper: TurnFeedKeeper) = TurnFactsPresenter(FixedClock).present(keeper.feed.value, 600_000).reading.endNotes["m1"]

    @Test
    fun `B8 a run the server lost before any words reads cut off under the prompt, with Send again`() {
        val facts = facts(listOf(prompt(), unfinished(emptyThought())))
        val note = assertNotNull(facts.reading.endNotes["m1"])
        assertEquals(TurnEndNote.CutOffBeforeReply, note)
        assertTrue(note.canSendAgain)
        assertFalse(note.canContinue)
        // Its own line in place of the generic "Stopped" footer.
        assertTrue(note.isServerStop)
    }

    @Test
    fun `B8 words that came before the cut stay, and the cut-off line with Continue follows them`() {
        val said = words("1. The Nile flows north.\n2. The Amazon carries the most water.", "m2")
        val facts = facts(listOf(prompt(), unfinished(emptyThought(), said)))
        assertEquals(TurnEndNote.CutOff, facts.reading.endNotes["m1"])
        assertTrue(TurnEndNote.CutOff.canContinue)
        assertFalse(TurnEndNote.CutOff.canSendAgain)
        assertFalse("m2/text-1" in facts.reading.hiddenPartKeys, "the partial reply must stay on screen")
    }

    @Test
    fun `B8 a step that ended with words, then a step the server lost, still reads cut off with Continue`() {
        val first = finished(words("I'll write the list to a file.", "m2"), tool(ToolStatus.Completed, "Wrote file", "m2"), id = "m2")
        val facts = facts(listOf(prompt(), first, unfinished(emptyThought("m3"), id = "m3", created = 520_100)))
        assertEquals(TurnEndNote.CutOff, facts.reading.endNotes["m1"])
    }

    @Test
    fun `B8 a run still going is never called cut off`() {
        val facts = facts(listOf(prompt(), unfinished(emptyThought())), busy = true)
        assertNull(facts.reading.endNotes["m1"])
    }

    @Test
    fun `B8 a run this chat saw end is read by how it ended, never as cut off, even before its last message lands`() {
        // The server's idle can arrive a moment before the step's own end: no flash of "connection dropped".
        assertNull(facts(listOf(prompt(), unfinished(emptyThought())), outcome = RunOutcome.Succeeded).reading.endNotes["m1"])
        assertNull(facts(listOf(prompt(), unfinished(emptyThought())), outcome = RunOutcome.Idle).reading.endNotes["m1"])
    }

    @Test
    fun `B8 a Stop the person pressed still reads Stopped, not cut off`() {
        val facts = facts(listOf(prompt(), unfinished(emptyThought())), outcome = RunOutcome.Interrupted(InterruptReason.User))
        assertEquals(TurnEndNote.Stopped, facts.reading.endNotes["m1"])
    }

    @Test
    fun `B8 a restart the chat saw live keeps its own words and Continue`() {
        val said = words("1. The Nile flows north.", "m2")
        val facts = facts(listOf(prompt(), unfinished(said)), outcome = RunOutcome.Interrupted(InterruptReason.Shutdown))
        assertEquals(TurnEndNote.StoppedServerRestart, facts.reading.endNotes["m1"])
    }

    @Test
    fun `B8 W4 rule holds - a declined step, reopened with no outcome and no end on the step, is not cut off`() {
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        val facts = facts(listOf(prompt("write a file"), unfinished(declined)))
        // W4 surface: the turn says the person declined (not "connection dropped").
        assertEquals(TurnEndNote.Declined, facts.reading.endNotes["m1"])
        // And as the chat saw it live (OpenCode 2 names the refusal's end "shutdown"): still no restart line.
        val live = facts(listOf(prompt("write a file"), unfinished(declined)), outcome = RunOutcome.Interrupted(InterruptReason.Shutdown))
        assertEquals(TurnEndNote.Declined, live.reading.endNotes["m1"])
        // What 2.2.6 really keeps after a Deny: the step ended, error `aborted` "Step interrupted", the session has no
        // newer idle (the same session shape as a lost run): the chat reads the step, so no cut-off line.
        val ended =
            MessageWithParts(
                MessageInfo(
                    "m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle",
                    time = MsgTime(516_100, 532_000), errorText = "Step interrupted",
                    errorKind = io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind.Aborted, finish = "error",
                ),
                listOf(declined).toImmutableList(),
            )
        for (outcome in listOf(null, RunOutcome.Interrupted(InterruptReason.Shutdown))) {
            val note = facts(listOf(prompt("write a file"), ended), outcome = outcome).reading.endNotes["m1"]
            assertTrue(note != TurnEndNote.CutOff && note != TurnEndNote.CutOffBeforeReply, "after a Deny: $note")
            assertTrue(note != TurnEndNote.StoppedServerRestart, "after a Deny: $note")
        }
    }

    @Test
    fun `W4 a declined turn says so on its surface, with words before the step or none, reopened or live`() {
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        val intro = Part("text-0", "m2", sid, PartType.Text, "text", text = "I'll create the file now.", ended = true)
        assertEquals(TurnEndNote.Declined, facts(listOf(prompt("write a file"), unfinished(intro, declined))).reading.endNotes["m1"])
        assertFalse(TurnEndNote.Declined.canContinue)
        assertFalse(TurnEndNote.Declined.isServerStop)
    }

    @Test
    fun `W4 a refused step the agent went on past and finished has no declined line`() {
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        val first = finished(declined, id = "m2")
        val after = MessageWithParts(
            MessageInfo("m3", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(520_100, 521_000), finish = "stop"),
            listOf(words("Understood, I left the file out.", "m3")).toImmutableList(),
        )
        assertNull(facts(listOf(prompt("write a file"), first, after)).reading.endNotes["m1"])
    }

    @Test
    fun `W4 Stop after a refusal still reads Stopped and a normal finish has no line`() {
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        assertEquals(
            TurnEndNote.Stopped,
            facts(listOf(prompt("write a file"), unfinished(declined)), outcome = RunOutcome.Interrupted(InterruptReason.User)).reading.endNotes["m1"],
        )
        val done = MessageWithParts(
            MessageInfo("m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(516_098, 520_000), finish = "stop"),
            listOf(words("one\ntwo\nthree", "m2")).toImmutableList(),
        )
        assertNull(facts(listOf(prompt(), done)).reading.endNotes["m1"])
    }

    @Test
    fun `W4 gate - the real 2_2_6 Deny shape closes with one line, the work line folded and no Stopped of its own`() {
        // What OpenCode 2.2.6 keeps after a Deny: the step's message ended as aborted ("Step interrupted").
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        val ended =
            MessageWithParts(
                MessageInfo(
                    "m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle",
                    time = MsgTime(516_100, 532_000), errorText = "Step interrupted",
                    errorKind = io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind.Aborted, finish = "error",
                ),
                listOf(declined).toImmutableList(),
            )
        // Reopened (no outcome known), and live as OpenCode 2 reports it ("shutdown").
        for (outcome in listOf(null, RunOutcome.Interrupted(InterruptReason.Shutdown))) {
            val facts = facts(listOf(prompt("write a file"), ended), outcome = outcome)
            assertEquals(TurnEndNote.Declined, facts.reading.endNotes["m1"], "outcome $outcome")
            val work = assertNotNull(facts.work["m1"])
            // The step row's own "You declined this" stays folded away by default: a Done work line does not open.
            assertEquals(io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState.Done, work.state, "outcome $outcome")
            // The footer drops its generic "Stopped" for a turn that ended on the refusal: the declined line is the one.
            assertTrue(io.github.eslamasabry.oc.feature.chat.presentation.StoppedSteps.endedOnRefusal(work), "outcome $outcome")
        }
        // No way on that would run the refused step again.
        assertFalse(TurnEndNote.Declined.canSendAgain)
        assertFalse(TurnEndNote.Declined.canContinue)
    }

    @Test
    fun `W4 gate - while the run still goes after a Deny there is no closing line yet`() {
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        assertNull(facts(listOf(prompt("write a file"), unfinished(declined)), busy = true).reading.endNotes["m1"])
    }

    @Test
    fun `W4 gate - a reply that went on in words past a refusal and was then lost reads cut off, not declined`() {
        val declined = tool(ToolStatus.Error, "The user rejected permission to use this specific tool call. with feedback: use b.txt", "m2")
        val after = Part("text-2", "m2", sid, PartType.Text, "text", text = "Understood, writing b.txt instead.")
        val facts = facts(listOf(prompt("write a file"), unfinished(declined, after)))
        assertEquals(TurnEndNote.CutOff, facts.reading.endNotes["m1"])
    }

    @Test
    fun `W4 gate - a refusal the agent went past to another step, then a Stop, reads Stopped`() {
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        val next =
            Part(
                "t-read", "m3", sid, PartType.Tool, "tool", callId = "c-read", toolName = "read",
                tool = ToolState(ToolStatus.Error, input = JsonObject(mapOf("filePath" to JsonPrimitive("/p/a.txt"))), output = "Tool execution interrupted", startedAtMs = 519_000, completedAtMs = 519_500),
            )
        val page = listOf(prompt("write a file"), finished(declined, id = "m2"), unfinished(next, id = "m3", created = 518_900))
        assertEquals(TurnEndNote.Stopped, facts(page, outcome = RunOutcome.Interrupted(InterruptReason.User)).reading.endNotes["m1"])
        // Its last step was not the refusal: reopened, the refusal earlier in the turn is not the turn's end.
        assertTrue(facts(page).reading.endNotes["m1"] != TurnEndNote.Declined)
    }

    @Test
    fun `B8 a server notice written as a message is not a reply and is never cut off`() {
        // OpenCode 2 `model-switched` / `synthetic` rows have a creation time and no completion, and no model.
        val notice =
            MessageWithParts(
                MessageInfo("m3", sid, Role.Assistant, time = MsgTime(530_000, null)),
                listOf(
                    Part("notice-m3", "m3", sid, PartType.Switch, "v2:switch", text = "gpt-5.6-sol", ended = true, noticeMetadata = persistentMapOf("kind" to "model")),
                ).toImmutableList(),
            )
        val done =
            MessageWithParts(
                MessageInfo("m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(516_098, 520_000), finish = "stop"),
                listOf(words("one\ntwo\nthree", "m2")).toImmutableList(),
            )
        assertNull(facts(listOf(prompt(), done, notice)).reading.endNotes["m1"])
    }

    @Test
    fun `B8 a prompt the server has no reply for at all keeps No reply came back with Send again`() {
        // Killed before the step began: OpenCode 2 keeps the prompt and nothing after it (Flutter `_unansweredTurn`).
        assertEquals(TurnEndNote.Silent, facts(listOf(prompt())).reading.endNotes["m1"])
    }

    @Test
    fun `B8 only the newest turn reads cut off`() {
        val older = unfinished(emptyThought("m2"))
        val newer = MessageWithParts(
            MessageInfo("m3", sid, Role.User, time = MsgTime(600_000, null)),
            listOf(Part("text-0", "m3", sid, PartType.Text, "text", text = "say hi", ended = true)).toImmutableList(),
        )
        val answer =
            MessageWithParts(
                MessageInfo("m4", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(600_010, 601_000), finish = "stop"),
                listOf(words("Hi!", "m4")).toImmutableList(),
            )
        val facts = facts(listOf(prompt(), older, newer, answer))
        assertNull(facts.reading.endNotes["m1"])
        assertNull(facts.reading.endNotes["m3"])
    }

    // ---- Gate: "not running" must be what the server said, never "not known yet".

    @Test
    fun `B8 gate - before the server answered, or when its status read failed, an unfinished reply is not cut off`() {
        val page = listOf(prompt(), unfinished(emptyThought()))
        assertNull(facts(page, said = false).reading.endNotes["m1"])
        val keeper = TurnFeedKeeper(sid)
        keeper.history(page, newest = true)
        // The page is in, the status read has not answered yet: no line.
        assertNull(note(keeper))
        keeper.idle()
        assertEquals(TurnEndNote.CutOffBeforeReply, note(keeper))
        // Asked again, the read failed or said busy: unknown again, the line goes.
        keeper.idle(said = false)
        assertNull(note(keeper))
    }

    @Test
    fun `B8 gate - a run that starts after the said idle takes the line away`() {
        val page = listOf(prompt(), unfinished(emptyThought()))
        for (event in listOf<GatewayEvent>(
            GatewayEvent.RunStarted(SessionId(sid)),
            GatewayEvent.RunRetrying(SessionId(sid), 1, null, null, null),
            // Streamed words arrive for the reply.
            GatewayEvent.PartDelta(SessionId(sid), MessageId("m2"), PartId("text-9"), DeltaField.Text, "The Nile"),
            // A new reply in the turn (the step after a tool), before the run's own start arrives.
            GatewayEvent.MessageUpserted(MessageInfo("m3", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(530_000, null))),
            // The same reply, changed and still open (the server is writing it).
            GatewayEvent.MessageUpserted(page[1].info.copy(cost = 0.01)),
            // A step of it starts running.
            GatewayEvent.PartUpserted(tool(ToolStatus.Running, null, "m2")),
        )) {
            val keeper = TurnFeedKeeper(sid)
            keeper.history(page, newest = true)
            keeper.idle()
            assertEquals(TurnEndNote.CutOffBeforeReply, note(keeper))
            keeper.event(event, 601_000)
            assertNull(note(keeper), "after $event")
        }
    }

    @Test
    fun `B8 gate - a durable event replayed after a reconnect, or the same page read again, keeps the line without a flicker`() {
        val page = listOf(prompt(), unfinished(emptyThought()))
        val keeper = TurnFeedKeeper(sid)
        keeper.history(page, newest = true)
        keeper.idle()
        keeper.event(GatewayEvent.MessageUpserted(page[1].info), 601_000)
        assertEquals(TurnEndNote.CutOffBeforeReply, note(keeper))
        keeper.history(page, newest = true)
        assertEquals(TurnEndNote.CutOffBeforeReply, note(keeper))
        // A newer reply in the newest page: what was said no longer holds until the server is asked again.
        keeper.history(page + unfinished(emptyThought("m3"), id = "m3", created = 530_000), newest = true)
        assertNull(note(keeper))
    }

    @Test
    fun `B8 gate - a reply waiting on the person is never cut off`() {
        assertNull(facts(listOf(prompt(), unfinished(emptyThought())), waiting = true).reading.endNotes["m1"])
    }

    @Test
    fun `B8 gate - a turn whose last message is a finished tool step is not cut off`() {
        val step = finished(tool(ToolStatus.Completed, "Wrote file", "m2"), id = "m2")
        val note = facts(listOf(prompt(), step)).reading.endNotes["m1"]
        assertTrue(note != TurnEndNote.CutOff && note != TurnEndNote.CutOffBeforeReply, "$note")
    }

    @Test
    fun `B8 gate - Codex keeps no completion for a turn the person interrupted, its finish reason ends the reply`() {
        // CodexMappers.codexMessages: an `interrupted` turn has `completedAt: null` and maps to finish "cancelled".
        val interrupted =
            MessageWithParts(
                MessageInfo("agent-1", sid, Role.Assistant, providerId = "codex", time = MsgTime(520_000, null), finish = "cancelled"),
                listOf(words("Half a list", "agent-1")).toImmutableList(),
            )
        assertNull(facts(listOf(prompt(), interrupted)).reading.endNotes["m1"])
        val silent = interrupted.copy(parts = listOf(emptyThought("agent-1")).toImmutableList())
        val note = facts(listOf(prompt(), silent)).reading.endNotes["m1"]
        assertTrue(note != TurnEndNote.CutOff && note != TurnEndNote.CutOffBeforeReply, "$note")
    }

    @Test
    fun `B8 gate - a Paseo reply read while its agent was idle is ended, and while busy only the chat's busy run holds it`() {
        // PaseoMappers.paseoTimeline: every item gets its timestamp as completion unless the agent is busy and it is
        // the newest item; Paseo items name a provider and no model. (Replies are dated after the prompt, as the
        // ledger orders by time.)
        val ended =
            MessageWithParts(
                MessageInfo("p2", sid, Role.Assistant, providerId = "claude", time = MsgTime(520_000, 520_000)),
                listOf(words("Done.", "p2")).toImmutableList(),
            )
        assertNull(facts(listOf(prompt(), ended)).reading.endNotes["m1"])
        val streaming = ended.copy(info = ended.info.copy(time = MsgTime(520_000, null)))
        assertNull(facts(listOf(prompt(), streaming), busy = true).reading.endNotes["m1"])
    }

    // ---- Older turns: a refusal is that turn's result and stays; the other closing lines stay newest-only.

    private fun nextPrompt(id: String, at: Long, text: String) =
        MessageWithParts(
            MessageInfo(id, sid, Role.User, time = MsgTime(at, null)),
            listOf(Part("text-$id", id, sid, PartType.Text, "text", text = text, ended = true)).toImmutableList(),
        )

    private fun answer(id: String, at: Long, text: String) =
        MessageWithParts(
            MessageInfo(id, sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle", time = MsgTime(at + 10, at + 1_000), finish = "stop"),
            listOf(Part("text-$id", id, sid, PartType.Text, "text", text = text)).toImmutableList(),
        )

    private fun deniedTurn(): List<MessageWithParts> =
        listOf(prompt("write a file"), unfinished(tool(ToolStatus.Error, "The user declined this tool call", "m2")))

    @Test
    fun `W4 older - a declined turn keeps its line after a new prompt and after two more turns`() {
        val one = deniedTurn() + listOf(nextPrompt("m3", 600_000, "say hi"), answer("m4", 600_000, "Hi!"))
        val f1 = facts(one)
        assertEquals(TurnEndNote.Declined, f1.reading.endNotes["m1"])
        assertNull(f1.reading.endNotes["m3"])
        val two = one + listOf(nextPrompt("m5", 700_000, "and again"), answer("m6", 700_000, "Again!"))
        val f2 = facts(two)
        assertEquals(TurnEndNote.Declined, f2.reading.endNotes["m1"])
        assertEquals(setOf("m1"), f2.reading.endNotes.keys)
        // The newest turn declined too: both turns say so, once each.
        val both = deniedTurn() + listOf(nextPrompt("m3", 600_000, "again"), unfinished(tool(ToolStatus.Error, "The user declined this tool call", "m4").copy(id = "t2", messageId = "m4"), id = "m4", created = 600_010))
        val f3 = facts(both)
        assertEquals(TurnEndNote.Declined, f3.reading.endNotes["m1"])
        assertEquals(TurnEndNote.Declined, f3.reading.endNotes["m3"])
        // While the new turn runs, the older line stays and the live turn has none yet.
        val live = facts(one.dropLast(1), busy = true)
        assertEquals(TurnEndNote.Declined, live.reading.endNotes["m1"])
        assertNull(live.reading.endNotes["m3"])
    }

    @Test
    fun `W4 older - a turn that declined one step and then finished with words has no line`() {
        val declined = tool(ToolStatus.Error, "The user declined this tool call", "m2")
        val first = finished(declined, id = "m2")
        val after = answer("m3a", 520_000, "Understood, I left the file out.")
        val page = listOf(prompt("write a file"), first, after, nextPrompt("m3", 600_000, "ok"), answer("m4", 600_000, "Done"))
        assertNull(facts(page).reading.endNotes["m1"])
    }

    @Test
    fun `W4 older - a Stop or a lost run on an older turn gets no line, as before`() {
        val stopped = tool(ToolStatus.Error, "Tool execution interrupted", "m2")
        val page = listOf(prompt("write a file"), unfinished(stopped), nextPrompt("m3", 600_000, "ok"), answer("m4", 600_000, "Done"))
        val reading = facts(page, outcome = RunOutcome.Interrupted(InterruptReason.User)).reading
        assertNull(reading.endNotes["m1"])
        assertNull(facts(page).reading.endNotes["m1"])
        val lost = listOf(prompt(), unfinished(emptyThought("m2")), nextPrompt("m3", 600_000, "ok"), answer("m4", 600_000, "Done"))
        assertNull(facts(lost).reading.endNotes["m1"])
    }

    @Test
    fun `W4 older - a command turn or a turn without a reply gets no declined line`() {
        val cmd = listOf(prompt("/compact"), unfinished(tool(ToolStatus.Error, "The user declined this tool call", "m2")), nextPrompt("m3", 600_000, "ok"), answer("m4", 600_000, "Done"))
        assertNull(facts(cmd).reading.endNotes["m1"])
    }
}
