package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.isDeclinedToolError
import io.github.eslamasabry.oc.feature.chat.presentation.PlainMarkdown
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.timelineEntries
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** W4 words: raw or internal text never shown as copy (tool refusals, timeline rows). */
class W4WordsTest {
    private val sid = Ref.sessionId.raw

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun tool(status: ToolStatus, name: String, output: String?, input: Map<String, String> = emptyMap()) =
        Part(
            "t-$name",
            "m2",
            sid,
            PartType.Tool,
            "tool",
            callId = "c-$name",
            toolName = name,
            tool = ToolState(status, input = JsonObject(input.mapValues { JsonPrimitive(it.value) }), output = output, startedAtMs = 1_000, completedAtMs = 49_000),
        )

    private fun userMessage(text: String) =
        MessageWithParts(
            MessageInfo("m1", sid, Role.User, time = MsgTime(500, null)),
            listOf(Part("p1", "m1", sid, PartType.Text, "text", text = text)).toImmutableList(),
        )

    private fun reply(vararg parts: Part, id: String = "m2") =
        MessageWithParts(
            MessageInfo(id, sid, Role.Assistant, providerId = "a", modelId = "m", time = MsgTime(1_000, 49_000)),
            parts.toList().toImmutableList(),
        )

    @Test
    fun `W4 the person's own refusal is declined, any other error stays a failure`() {
        assertTrue(isDeclinedToolError("The user declined this tool call"))
        assertTrue(isDeclinedToolError("The user rejected permission to use this specific tool call"))
        assertEquals(false, isDeclinedToolError("ENOENT: no such file"))
        assertEquals(false, isDeclinedToolError(null))
    }

    @Test
    fun `W4 a rejected command reads Declined, is not counted as run and the turn is not a failure`() {
        val k = TurnFeedKeeper(sid)
        k.history(
            listOf(
                userMessage("count words"),
                reply(tool(ToolStatus.Error, "bash", "The user declined this tool call", mapOf("command" to "wc -w a.txt"))),
            ),
        )
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 60_000).work["m1"])
        assertEquals(StepState.Declined, work.steps.single().state)
        assertEquals(0, work.counts.ran)
        assertTrue(work.state != WorkLineState.EndedFailed, "state was ${work.state}")
    }

    @Test
    fun `W4 a real failure stays Failed`() {
        val k = TurnFeedKeeper(sid)
        k.history(listOf(userMessage("go"), reply(tool(ToolStatus.Error, "bash", "make: *** No rule", mapOf("command" to "make")))))
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 60_000).work["m1"])
        assertEquals(StepState.Failed, work.steps.single().state)
    }

    @Test
    fun `W4 a timeline row of tools only carries the tools as facts, not their ids`() {
        val k = TurnFeedKeeper(sid)
        k.history(listOf(userMessage("list"), reply(tool(ToolStatus.Completed, "read", "x", mapOf("filePath" to "/p/bg5.txt")))))
        val state = stateOf(prompt("m1", "list"), footer("m1"))
        val entry = timelineEntries(k.feed.value, state, "", canFork = false).first { !it.prompt }
        assertEquals("", entry.preview)
        assertEquals(1, entry.tools.size)
        assertEquals("read", entry.tools.single().toolName)
    }

    @Test
    fun `W4 a change of model reads as the model's name in the timeline`() {
        val k = TurnFeedKeeper(sid)
        val switched =
            Part("s1", "m2", sid, PartType.Switch, "v2:switch", text = "claude-sonnet-4", noticeMetadata = persistentMapOf("kind" to "model"))
        k.history(listOf(userMessage("hi"), reply(switched)))
        val state = stateOf(prompt("m1", "hi"), footer("m1"))
        val entry = timelineEntries(k.feed.value, state, "", canFork = false).first { !it.prompt }
        assertEquals("Sonnet 4", entry.preview)
    }

    @Test
    fun `W4 a reply's list marks are dropped from its one-line preview`() {
        assertEquals("Folder: second/ Files: bg5.txt", PlainMarkdown.oneLine("Folder:\n- second/\nFiles:\n- bg5.txt"))
        assertEquals("a link and bold", PlainMarkdown.oneLine("# a [link](http://x) and **bold**"))
    }
}
