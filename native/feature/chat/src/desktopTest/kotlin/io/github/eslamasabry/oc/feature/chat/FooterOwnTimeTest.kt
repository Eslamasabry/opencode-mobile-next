package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.Tokens
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B-CHAT-FOOT-001: a finished turn's footer says when ITS reply ended and how long it took. A notice that lands in the
 * turn later (the server's "Instructions updated", a switch, a compaction) is not a reply and moves neither number.
 */
class FooterOwnTimeTest {
    private val sid = Ref.sessionId.raw
    private val hour = 3_600_000L

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun reply(id: String, at: Long, done: Long?) =
        MessageInfo(
            id, sid, Role.Assistant, providerId = "anthropic", modelId = "m",
            tokens = Tokens(input = 6_000, output = 100), time = MsgTime(at, done),
            finish = if (done != null) "stop" else null,
        )

    /** The server's own notices ride as assistant-role messages with no model, as the history adapter writes them. */
    private fun noticeMessage(id: String, at: Long, done: Long? = null) =
        MessageInfo(id, sid, Role.Assistant, time = MsgTime(at, done))

    private fun notice(id: String, mid: String, type: PartType, kind: String) =
        Part(id, mid, sid, type, "v2:notice", ended = true, noticeMetadata = persistentMapOf("kind" to kind))

    private fun text(id: String, mid: String, words: String) = Part(id, mid, sid, PartType.Text, "text", text = words)

    private fun step(id: String, mid: String, at: Long, end: Long) =
        Part(
            id, mid, sid, PartType.Tool, "tool", callId = "call-$id", toolName = "read",
            tool =
                ToolState(
                    ToolStatus.Completed,
                    input = JsonObject(mapOf("filePath" to JsonPrimitive("/p/a.txt"))),
                    output = "x",
                    startedAtMs = at,
                    completedAtMs = end,
                ),
        )

    private fun msg(info: MessageInfo, vararg parts: Part) = MessageWithParts(info, parts.toList().toImmutableList())

    private val prompt =
        msg(MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)), text("p1", "m1", "base three"))

    private fun facts(page: List<MessageWithParts>, live: Boolean = false): TurnFactsUi {
        val k = TurnFeedKeeper(sid)
        k.history(page, newest = true)
        if (live) k.running(1_000) else k.idle()
        return TurnFactsPresenter(FixedClock).present(k.feed.value, 80_000)
    }

    @Test
    fun `B-CHAT-FOOT-001 an Instructions updated notice after a finished turn does not move its time or length`() {
        val answered = reply("m2", 2_000, 74_000)
        val before = facts(listOf(prompt, msg(answered, text("x", "m2", "Done."))))
        val after =
            facts(
                listOf(
                    prompt,
                    msg(answered, text("x", "m2", "Done.")),
                    msg(noticeMessage("m3", 46 * hour), notice("n1", "m3", PartType.Notice, "instructions")),
                )
            )
        val was = assertNotNull(before.reading.metadata["m1"])
        val now = assertNotNull(after.reading.metadata["m1"])
        assertEquals(74_000L, was.endedAtMs)
        assertEquals(was.endedAtMs, now.endedAtMs, "the footer clock stays where the reply ended")
        assertEquals(73_000L, now.tookMs, "the length is the reply's, not the 46 hours until the notice")
        assertEquals(was.tokens, now.tokens)
    }

    @Test
    fun `B-CHAT-FOOT-002 a reply made only of steps, with no words, still ends the turn at its own end`() {
        val steps = reply("m2", 2_000, 50_000)
        val f = facts(listOf(prompt, msg(steps, step("t1", "m2", 2_000, 49_000))))
        val meta = assertNotNull(f.reading.metadata["m1"])
        assertEquals(50_000L, meta.endedAtMs)
        assertEquals(49_000L, meta.tookMs)
    }

    @Test
    fun `B-CHAT-FOOT-003 a compaction notice inside a live turn changes no clock and the finished footer ignores it`() {
        val live = reply("m2", 2_000, null)
        val compaction = noticeMessage("m3", 20_000, done = 200_000)
        val compacting = msg(compaction, notice("n2", "m3", PartType.Compaction, "completed"))
        val running = listOf(prompt, compacting, msg(live, step("t1", "m2", 21_000, 22_000)))
        val during = facts(running, live = true)
        assertEquals(1_000L, during.runStartedAtMs, "the live counter starts where the run started")
        assertNull(during.reading.metadata["m1"], "a turn that is still running has no footer yet")
        val answered = msg(reply("m2", 2_000, 74_000), text("x", "m2", "Done."))
        val finished = facts(listOf(prompt, compacting, answered))
        val meta = assertNotNull(finished.reading.metadata["m1"])
        assertEquals(74_000L, meta.endedAtMs, "the compaction finishing later is not the reply ending")
        assertEquals(73_000L, meta.tookMs)
    }
}
