package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.ui.stepOutputShown
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlinx.collections.immutable.persistentListOf
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import io.github.eslamasabry.oc.feature.chat.presentation.RunTailRules
import io.github.eslamasabry.oc.feature.chat.presentation.stepClockStart
import io.github.eslamasabry.oc.feature.chat.requests.presentation.AnswerWords
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.ui.opensItself
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Issue 147 (live run): a command's output is readable under its step, running, finished and failed. */
class I147LiveRunStepOutputTest {
    private val input = JsonObject(mapOf("command" to JsonPrimitive("sleep 20 && uname -a")))

    private fun step(state: StepState, tool: ToolState): TurnStep =
        TurnStep("p1", toolFacts("bash", tool), state, durationMs = null, startedAtMs = 1_000L, output = toolOutputView(tool, "bash"))

    private fun lines(range: IntRange) = range.joinToString("\n") { "line $it" }

    @Test
    fun `B-CHAT-115 a finished command's output is read under its step`() {
        val done = step(StepState.Done, ToolState(ToolStatus.Completed, input = input, output = "Linux phone 6.1 aarch64\n"))
        assertEquals("Linux phone 6.1 aarch64\n", stepOutputShown(done, showAll = false))
    }

    @Test
    fun `B-CHAT-115 a running command shows the last lines it printed so far`() {
        val running =
            step(
                StepState.Running,
                ToolState(ToolStatus.Running, input = input, metadata = JsonObject(mapOf("output" to JsonPrimitive(lines(1..30))))),
            )
        val shown = stepOutputShown(running, showAll = false)!!.lines()
        assertEquals(12, shown.size)
        assertEquals("line 30", shown.last())
        assertEquals("line 19", shown.first())
    }

    @Test
    fun `B-CHAT-115 a running command that printed nothing yet shows no box text`() {
        assertNull(stepOutputShown(step(StepState.Running, ToolState(ToolStatus.Running, input = input)), showAll = false))
    }

    @Test
    fun `B-CHAT-115 a failed command opens on its last lines and Show all gives all of them`() {
        val failed = step(StepState.Failed, ToolState(ToolStatus.Error, input = input, output = lines(1..30)))
        assertEquals("line 30", stepOutputShown(failed, showAll = false)!!.lines().last())
        assertEquals(12, stepOutputShown(failed, showAll = false)!!.lines().size)
        assertEquals(30, stepOutputShown(failed, showAll = true)!!.lines().size)
    }

    private fun ask(id: String) =
        PermissionRequest(RequestId(id), Ref.sessionId, "bash", persistentListOf(), JsonObject(emptyMap()), persistentListOf(), null, true, null)

    @Test
    fun `B-CHAT-116 the turn's clock does not count the time spent waiting for the person's decision`() {
        val keeper = TurnFeedKeeper(Ref.sessionId.raw)
        keeper.running(1_000)
        keeper.event(GatewayEvent.PermissionAsked(ask("r1")), 5_000)
        // While it waits the start has not moved (the live line is not drawn then); the answer moves it by the wait.
        assertEquals(1_000L, keeper.feed.value.runStartedAtMs)
        keeper.event(GatewayEvent.PermissionSettled(Ref.sessionId, RequestId("r1")), 125_000)
        assertEquals(121_000L, keeper.feed.value.runStartedAtMs)
        // 20 s of work after the answer reads 20 s, not 2 min 20 s.
        assertEquals(20_000L, 141_000L - keeper.feed.value.runStartedAtMs!!)
    }

    @Test
    fun `B-CHAT-116 two requests waiting at once count their overlap once`() {
        val keeper = TurnFeedKeeper(Ref.sessionId.raw)
        keeper.running(0)
        keeper.event(GatewayEvent.PermissionAsked(ask("a")), 10_000)
        keeper.event(GatewayEvent.PermissionAsked(ask("b")), 20_000)
        keeper.event(GatewayEvent.PermissionSettled(Ref.sessionId, RequestId("a")), 30_000)
        keeper.event(GatewayEvent.PermissionSettled(Ref.sessionId, RequestId("b")), 40_000)
        assertEquals(30_000L, keeper.feed.value.runStartedAtMs)
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    /** A turn whose command runs; the server (the phone agents' helper) reported no start time for it. */
    private fun runningTurn(): List<MessageWithParts> {
        val sid = Ref.sessionId.raw
        val user =
            MessageWithParts(
                MessageInfo("m1", sid, Role.User, time = MsgTime(100, null)),
                listOf(Part("t0", "m1", sid, PartType.Text, "text", text = "run it", ended = true)).toImmutableList(),
            )
        val tool = Part("c1", "m2", sid, PartType.Tool, "tool", callId = "c1", toolName = "bash", tool = ToolState(ToolStatus.Running, input = input))
        val assistant = MessageWithParts(MessageInfo("m2", sid, Role.Assistant, time = MsgTime(200, null)), listOf(tool).toImmutableList())
        return listOf(user, assistant)
    }

    @Test
    fun `B-CHAT-116 a running step with no start time from the server gets its own clock from when it was first seen`() {
        val keeper = TurnFeedKeeper(Ref.sessionId.raw)
        keeper.running(100)
        keeper.history(runningTurn(), newest = true)
        val presenter = TurnFactsPresenter(FixedClock)
        fun step(now: Long, waiting: Boolean = false) =
            presenter.present(keeper.feed.value, now, waiting).work.values.single().steps.single()
        // A turn that waits for the person has no running clock yet.
        assertNull(step(5_000, waiting = true).startedAtMs)
        assertEquals(10_000L, step(10_000).startedAtMs)
        // 15 s later it is still the same start: the row reads 15 s.
        assertEquals(10_000L, step(25_000).startedAtMs)
    }

    @Test
    fun `B-CHAT-116 the box stops saying it waits once the decision is made, even inside the Undo hold`() {
        assertTrue(RunTailRules.asksYou(listOf(CardState.Waiting)))
        assertTrue(RunTailRules.asksYou(listOf(CardState.NotAccepted(AnswerWords.Allowed))))
        assertFalse(RunTailRules.asksYou(listOf(CardState.Held(AnswerWords.Allowed))))
        assertFalse(RunTailRules.asksYou(listOf(CardState.Sent(AnswerWords.Allowed))))
        assertFalse(RunTailRules.asksYou(emptyList()))
    }

    @Test
    fun `B-CHAT-116 a step's own clock starts after the decision, not at the permission card`() {
        // Marked running at 1 s, asked at 5 s, answered at 100 s: it runs from 100 s less the 4 s before the ask.
        assertEquals(96_000L, stepClockStart(1_000, listOf(5_000L to 100_000L)))
        // An earlier step's wait that ended before this one began costs it nothing.
        assertEquals(50_000L, stepClockStart(50_000, listOf(5_000L to 40_000L)))
        // A wait that began before the step did counts only from the step's start.
        assertEquals(60_000L, stepClockStart(50_000, listOf(10_000L to 60_000L)))
        val keeper = TurnFeedKeeper(Ref.sessionId.raw)
        keeper.running(0)
        keeper.event(GatewayEvent.PermissionAsked(ask("r1")), 5_000)
        keeper.event(GatewayEvent.PermissionSettled(Ref.sessionId, RequestId("r1")), 100_000)
        assertEquals(listOf(5_000L to 100_000L), keeper.feed.value.waits)
    }

    @Test
    fun `B-CHAT-115 a running command that has printed opens by itself, one that waits or has printed nothing does not`() {
        val running = step(StepState.Running, ToolState(ToolStatus.Running, input = input))
        assertTrue(opensItself(running, waitingForYou = false, hasOutput = true))
        assertFalse(opensItself(running, waitingForYou = true, hasOutput = true))
        assertFalse(opensItself(running, waitingForYou = false, hasOutput = false))
        assertFalse(opensItself(step(StepState.Done, ToolState(ToolStatus.Completed, input = input)), false, true))
    }
}
