package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun msg(id: String, role: Role, vararg parts: Part): MessageWithParts =
    MessageWithParts(MessageInfo(id, "s", role), parts.toList().toImmutableList())

private fun text(id: String, words: String): Part = Part(id, "m", "s", PartType.Text, "text", text = words)

private fun tool(status: ToolStatus, metadata: JsonObject? = null): Part =
    Part("t", "m", "s", PartType.Tool, "tool", toolName = "bash", tool = ToolState(status, metadata = metadata))

/** A compaction line as the OC2 history adapter writes it: its kind (running / completed) in noticeMetadata. */
private fun compaction(id: String, kind: String): Part =
    Part(id, id, "s", PartType.Compaction, "v2:compaction", ended = true, noticeMetadata = persistentMapOf("kind" to kind))

private fun hasRunningCompaction(messages: List<MessageWithParts>): Boolean =
    messages.any { m -> m.parts.any(::isCompactionLineLive) }

private fun status(messages: List<MessageWithParts>): ToolStatus =
    messages.flatMap { it.parts }.first { it.type == PartType.Tool }.tool!!.status

// Flutter test/transcript_settle_test.dart. Its Paseo-built transcripts are written out as the messages the Paseo
// mapper makes: a compaction is a user-role message holding one v2:compaction part.
class TranscriptSettleTest {
    private val real =
        listOf(
            msg("u1", Role.User, text("u1:0", "go")),
            msg("c1", Role.User, compaction("c1:0", "running")),
            msg("c2", Role.User, compaction("c2:0", "completed")),
            msg("a1", Role.Assistant, text("a1:0", "continuing")),
        )

    @Test
    fun `B-CHAT-116 the compaction line ends once the compaction notice and later work arrive`() {
        assertTrue(hasRunningCompaction(real))
        val settled = settleStaleLive(real, busy = true)
        assertFalse(hasRunningCompaction(settled))
        // One message per message: the transcript reads rows, parts and turn owners by the same index.
        assertEquals(real.size, settled.size)
        assertTrue(settled.any { m -> m.parts.any { it.noticeMetadata["kind"] == COMPACTION_PASSED } })
        assertTrue(settled.any { m -> m.parts.any { it.noticeMetadata["kind"] == "completed" } })
    }

    @Test
    fun `B-CHAT-116 the compaction line stays while it is the newest thing and the agent is busy`() {
        assertTrue(hasRunningCompaction(settleStaleLive(real.subList(0, 2), busy = true)))
    }

    @Test
    fun `B-CHAT-116 the compaction line ends when the turn has ended`() {
        assertFalse(hasRunningCompaction(settleStaleLive(real.subList(0, 2), busy = false)))
    }

    @Test
    fun `B-CHAT-116 the compaction line ends when more of the conversation follows`() {
        assertFalse(hasRunningCompaction(settleStaleLive(real.subList(0, 2), busy = true, moreFollows = true)))
    }

    @Test
    fun `B-CHAT-117 a tool under later words of its turn is finished`() {
        val messages =
            listOf(
                msg("u1", Role.User, text("u", "go")),
                msg("a0", Role.Assistant, tool(ToolStatus.Running)),
                msg("a1", Role.Assistant, text("a", "Read it.")),
            )
        assertEquals(ToolStatus.Running, status(messages))
        assertEquals(ToolStatus.Completed, status(settleStaleLive(messages, busy = true)))
    }

    @Test
    fun `B-CHAT-117 a tool of the newest step with no words after it keeps running`() {
        val messages = listOf(msg("u", Role.User, text("u", "go")), msg("a", Role.Assistant, tool(ToolStatus.Running)))
        assertSame(messages, settleStaleLive(messages, busy = true))
    }

    @Test
    fun `B-CHAT-117 a tool is finished when the turn has ended`() {
        val messages = listOf(msg("u", Role.User, text("u", "go")), msg("a", Role.Assistant, tool(ToolStatus.Running)))
        assertEquals(ToolStatus.Completed, settleStaleLive(messages, busy = false)[1].parts.single().tool!!.status)
    }

    @Test
    fun `B-CHAT-117 words of an earlier turn do not finish a later turn tool`() {
        val messages =
            listOf(
                msg("u1", Role.User, text("u1", "one")),
                msg("a1", Role.Assistant, text("a1", "done")),
                msg("u2", Role.User, text("u2", "two")),
                msg("a2", Role.Assistant, tool(ToolStatus.Running)),
            )
        assertSame(messages, settleStaleLive(messages, busy = true))
    }

    @Test
    fun `B-CHAT-117 a background process keeps running`() {
        val messages =
            listOf(
                msg("u", Role.User, text("u", "go")),
                msg(
                    "a",
                    Role.Assistant,
                    tool(ToolStatus.Running, JsonObject(mapOf("background" to JsonPrimitive(true)))),
                    text("a", "Started it."),
                ),
            )
        assertSame(messages, settleStaleLive(messages, busy = true))
    }

    @Test
    fun `B-CHAT-117 a call the server never ran is not marked finished`() {
        val notRun =
            Part(
                "t",
                "m",
                "s",
                PartType.Tool,
                "tool",
                toolName = "bash",
                tool = ToolState(ToolStatus.Running, executedFlag = false),
            )
        val messages = listOf(msg("u", Role.User, text("u", "go")), msg("a", Role.Assistant, notRun))
        assertSame(messages, settleStaleLive(messages, busy = false))
    }

    @Test
    fun `B-CHAT-117 a late running report does not revive a finished call`() {
        assertEquals(ToolStatus.Completed, settledToolStatus(ToolStatus.Completed, ToolStatus.Running))
        assertEquals(ToolStatus.Error, settledToolStatus(ToolStatus.Error, ToolStatus.Pending))
        assertEquals(ToolStatus.Completed, settledToolStatus(ToolStatus.Running, ToolStatus.Completed))
        assertEquals(ToolStatus.Running, settledToolStatus(ToolStatus.Pending, ToolStatus.Running))
    }
}
