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
import io.github.eslamasabry.oc.core.common.text.PlainMarkdown
import io.github.eslamasabry.oc.feature.chat.presentation.StoppedSteps
import io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardRules
import io.github.eslamasabry.oc.feature.chat.ui.shownStepDurationMs
import io.github.eslamasabry.oc.engine.requests.ask.AskTarget
import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.AskView
import io.github.eslamasabry.oc.feature.chat.presentation.timelineEntries
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
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
    fun `W4 gate a turn that ends on the person's refusal is neither failed nor stopped, in both protocols' words`() {
        for (refusal in listOf("The user declined this tool call", "The user rejected permission to use this specific tool call.")) {
            val k = TurnFeedKeeper(sid)
            k.history(listOf(userMessage("count words"), reply(tool(ToolStatus.Error, "bash", refusal, mapOf("command" to "wc -w a.txt")))))
            val work = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 60_000).work["m1"])
            assertEquals(StepState.Declined, work.steps.single().state, refusal)
            // The 48 s it shows on the server was the wait for the answer: the row says no time.
            assertEquals(null, shownStepDurationMs(work.steps.single().state, work.steps.single().durationMs, 1_000, 60_000))
            assertEquals(WorkLineState.Done, work.state, refusal)
            assertTrue(StoppedSteps.endedOnRefusal(work), refusal)
        }
    }

    @Test
    fun `W4 gate a refusal the server ends as shutdown never reads as a restart, and offers no Continue`() {
        // Device run-6: OpenCode 2 ends a declined run as interrupted with reason "shutdown" (its default).
        val k = TurnFeedKeeper(sid)
        k.history(listOf(userMessage("write a file"), reply(tool(ToolStatus.Error, "write", "The user declined this tool call", mapOf("filePath" to "/p/d3.txt")))))
        val feed = k.feed.value.copy(lastOutcome = RunOutcome.Interrupted(InterruptReason.Shutdown))
        val facts = TurnFactsPresenter(FixedClock).present(feed, 60_000)
        val work = assertNotNull(facts.work["m1"])
        assertEquals(StepState.Declined, work.steps.single().state)
        assertEquals(WorkLineState.Done, work.state)
        assertTrue(StoppedSteps.endedOnRefusal(work))
        assertEquals(null, facts.reading.endNotes["m1"])
    }

    @Test
    fun `W4 gate a real server restart still says restart and offers Continue`() {
        val k = TurnFeedKeeper(sid)
        val words = Part("w1", "m2", sid, PartType.Text, "text", text = "I'll write these to a file.")
        k.history(listOf(userMessage("write a file"), reply(words, tool(ToolStatus.Error, "write", "Tool execution interrupted", mapOf("filePath" to "/p/d3.txt")))))
        val feed = k.feed.value.copy(lastOutcome = RunOutcome.Interrupted(InterruptReason.Shutdown))
        val facts = TurnFactsPresenter(FixedClock).present(feed, 60_000)
        assertEquals(TurnEndNote.StoppedServerRestart, facts.reading.endNotes["m1"])
        assertTrue(TurnEndNote.StoppedServerRestart.canContinue)
        assertEquals(false, StoppedSteps.endedOnRefusal(facts.work["m1"]))
    }

    @Test
    fun `W4 gate a Stop the person pressed after a refusal still reads Stopped`() {
        val k = TurnFeedKeeper(sid)
        k.history(listOf(userMessage("write a file"), reply(tool(ToolStatus.Error, "write", "The user declined this tool call", mapOf("filePath" to "/p/d3.txt")))))
        val feed = k.feed.value.copy(lastOutcome = RunOutcome.Interrupted(InterruptReason.User))
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(feed, 60_000).work["m1"])
        assertEquals(WorkLineState.Stopped, work.state)
        assertEquals(false, StoppedSteps.endedOnRefusal(work))
    }

    @Test
    fun `W4 gate a real failure or no work is not a refusal end`() {
        val k = TurnFeedKeeper(sid)
        k.history(listOf(userMessage("go"), reply(tool(ToolStatus.Error, "bash", "make: *** No rule", mapOf("command" to "make")))))
        val failed = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 60_000).work["m1"])
        assertEquals(false, StoppedSteps.endedOnRefusal(failed))
        assertEquals(false, StoppedSteps.endedOnRefusal(null))
    }

    @Test
    fun `W4 gate the permission Details box names the folder or the pattern, never the server's dot`() {
        val folder = AskView(AskVerb.ReadFolder, targets = persistentListOf(AskTarget("neon-breaker")))
        assertEquals("neon-breaker", RequestCardRules.detailsText(folder, "."))
        assertEquals(null, RequestCardRules.detailsText(AskView(AskVerb.ReadFolder), "."))
        assertEquals(false, RequestCardRules.detailsSaysAllMatching(AskView(AskVerb.ReadFolder)))
        val search = AskView(AskVerb.Search, targets = persistentListOf(AskTarget("TODO")))
        assertEquals("TODO", RequestCardRules.detailsText(search, "."))
        val run = AskView(AskVerb.Run, targets = persistentListOf(AskTarget("npm test")))
        assertEquals("npm test", RequestCardRules.detailsText(run, "npm test"))
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
