package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.SessionId
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
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.feature.chat.presentation.StoppedSteps
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.WorkLineRules
import io.github.eslamasabry.oc.feature.chat.ui.shownStepDurationMs
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * P-102 (auditor sweep 2, stop_jump.png / e05_recovered_chat.png): after Stop the server ends each unfinished tool
 * with an error. That reads Stopped with no time, is not counted as work, and keeps the stop mark after a relaunch.
 */
class P102StoppedStepsTest {
    private val sid = Ref.sessionId.raw

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun tool(id: String, name: String, status: ToolStatus, input: Map<String, String>, output: String?) =
        Part(
            id,
            "m2",
            sid,
            PartType.Tool,
            "tool",
            callId = "call-$id",
            toolName = name,
            tool =
                ToolState(
                    status,
                    input = JsonObject(input.mapValues { JsonPrimitive(it.value) }),
                    output = output,
                    startedAtMs = 2_000,
                    completedAtMs = 135_000,
                ),
        )

    private fun stoppedTurn(): List<MessageWithParts> {
        val prompt =
            MessageWithParts(
                MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, null)),
                listOf(Part("p1", "m1", sid, PartType.Text, "text", text = "fix it")).toImmutableList(),
            )
        val reply =
            MessageWithParts(
                MessageInfo("m2", sid, Role.Assistant, providerId = "a", modelId = "m", time = MsgTime(2_000, 135_000)),
                listOf(
                        tool("t1", "edit", ToolStatus.Error, mapOf("filePath" to "/p/a.kt"), "Tool execution aborted"),
                        tool("t2", "write", ToolStatus.Error, mapOf("filePath" to "/p/b.kt"), "Tool execution aborted"),
                        tool("t3", "bash", ToolStatus.Error, mapOf("command" to "npm test"), "Tool execution interrupted"),
                    )
                    .toImmutableList(),
            )
        return listOf(prompt, reply)
    }

    @Test
    fun `P-102 stop errors are stops and real errors stay failures`() {
        assertTrue(StoppedSteps.isStopError("Tool execution aborted"))
        assertTrue(StoppedSteps.isStopError("Tool execution interrupted"))
        assertTrue(StoppedSteps.isStopError("Step interrupted"))
        assertTrue(StoppedSteps.isStopError("MessageAbortedError: The operation was aborted."))
        assertFalse(StoppedSteps.isStopError("ENOENT: no such file"))
        assertFalse(StoppedSteps.isStopError("Command failed: npm test was interrupted by a flaky check"))
        assertFalse(StoppedSteps.isStopError(null))
    }

    @Test
    fun `P-102 after Stop every ended step reads Stopped with no time and nothing counts as done`() {
        val k = TurnFeedKeeper(sid)
        k.history(stoppedTurn())
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 2_000)
        k.event(GatewayEvent.RunEnded(SessionId(sid), RunOutcome.Interrupted(InterruptReason.User)), 135_000)
        val facts = TurnFactsPresenter(FixedClock).present(k.feed.value, 140_000)
        val work = assertNotNull(facts.work["m1"])
        assertEquals(WorkLineState.Stopped, work.state)
        assertTrue(work.steps.all { it.state == StepState.Stopped }, work.steps.map { it.state }.toString())
        for (step in work.steps) {
            assertNull(step.durationMs, "a stopped step has no elapsed time")
            assertNull(shownStepDurationMs(step.state, 133_000, 2_000, 140_000))
        }
        val done = WorkLineRules.split(work).done
        assertEquals(0, done.edited, "nothing was edited")
        assertEquals(0, done.ran, "nothing ran")
        assertTrue(facts.results["m1"]?.commands.orEmpty().isEmpty(), "the stopped command is not 'ran 1 command'")
    }

    @Test
    fun `P-102 after a relaunch the stopped turn keeps the stop mark, not the warning`() {
        val k = TurnFeedKeeper(sid)
        // A fresh start: only the history is known, not the run's Interrupted outcome.
        k.history(stoppedTurn())
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 140_000).work["m1"])
        assertEquals(WorkLineState.Stopped, work.state)
        assertTrue(work.steps.all { it.state == StepState.Stopped })
    }

    @Test
    fun `P-104 the stop's tool errors never open the work line as did-not-finish while the run winds down`() {
        val k = TurnFeedKeeper(sid)
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 2_000)
        // The server has already ended the tools with the stop's error, but the run's end has not arrived yet.
        k.history(stoppedTurn())
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 134_000).work["m1"])
        // EndedFailed opens the line by itself; inside the list that unfold raced the stop and left rows blank.
        assertTrue(work.state != WorkLineState.EndedFailed, "state was ${work.state}")
    }

    @Test
    fun `P-102 a real failure before the stop stays Failed and keeps its time`() {
        val k = TurnFeedKeeper(sid)
        val turn = stoppedTurn()
        val failed = tool("t0", "bash", ToolStatus.Error, mapOf("command" to "make"), "make: *** No rule")
        val reply = turn[1].copy(parts = (listOf(failed) + turn[1].parts).toImmutableList())
        k.history(listOf(turn[0], reply))
        val work = assertNotNull(TurnFactsPresenter(FixedClock).present(k.feed.value, 140_000).work["m1"])
        assertEquals(StepState.Failed, work.steps.first().state)
        assertNotNull(work.steps.first().durationMs)
        assertEquals(1, WorkLineRules.split(work).done.ran, "the command that failed did run")
        assertEquals(WorkLineState.Stopped, work.state)
    }
}
