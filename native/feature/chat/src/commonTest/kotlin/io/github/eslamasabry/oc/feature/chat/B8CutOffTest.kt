package io.github.eslamasabry.oc.feature.chat

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

    private fun facts(page: List<MessageWithParts>, outcome: RunOutcome? = null, busy: Boolean = false) =
        TurnFeedKeeper(sid).run {
            if (busy) running(516_086)
            history(page)
            TurnFactsPresenter(FixedClock).present(feed.value.copy(lastOutcome = outcome), 600_000)
        }

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
        assertNull(facts.reading.endNotes["m1"])
        // And as the chat saw it live (OpenCode 2 names the refusal's end "shutdown"): still no restart line.
        val live = facts(listOf(prompt("write a file"), unfinished(declined)), outcome = RunOutcome.Interrupted(InterruptReason.Shutdown))
        assertNull(live.reading.endNotes["m1"])
        // What 2.2.6 really keeps after a Deny: the step ended, error `aborted` "Step interrupted", the session has no
        // newer idle (the same session shape as a lost run): the chat reads the step, so no cut-off line.
        val ended =
            MessageWithParts(
                MessageInfo(
                    "m2", sid, Role.Assistant, providerId = "opencode", modelId = "big-pickle",
                    time = MsgTime(1_000, 17_000), errorText = "Step interrupted",
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
}
