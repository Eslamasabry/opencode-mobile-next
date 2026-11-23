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
import io.github.eslamasabry.oc.engine.transcript.turnfacts.backgroundTaskName
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.ui.shownStepDurationMs
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * B-CHAT-311..313: Antigravity moves a slow command to the background and ends its turn; the helper says nothing more
 * until the next prompt. The engine stops every step of a turn that is not the newest busy one, so the command read
 * "Stopped before it finished" while it went on. It reads Running, sits on the background line, and ends only on its
 * real result.
 */
class BackgroundCommandTruthTest {
    private val sid = Ref.sessionId.raw

    private object Now : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(60_000)
    }

    private val command = "sleep 20 && uname -a"
    private val presenter = TurnFactsPresenter(Now)

    /** The agent's `bash` call as the Paseo mapper hands it over: flagged `background` while it still runs. */
    private fun bash(
        callId: String = "c1",
        status: ToolStatus = ToolStatus.Running,
        startedAtMs: Long = 2_000,
        output: String? = null,
    ): Part {
        val background = status == ToolStatus.Running
        return Part(
            id = "p-$callId",
            messageId = "m2",
            sessionId = sid,
            type = PartType.Tool,
            rawType = "tool",
            callId = callId,
            toolName = "bash",
            tool =
                ToolState(
                    status,
                    input = JsonObject(mapOf("command" to JsonPrimitive(command))),
                    output = output,
                    metadata = if (background) JsonObject(mapOf("background" to JsonPrimitive(true))) else null,
                    startedAtMs = startedAtMs,
                    completedAtMs = if (status == ToolStatus.Completed) startedAtMs + 20_000 else null,
                ),
        )
    }

    private val words =
        Part(
            "p-words",
            "m2",
            sid,
            PartType.Text,
            "text",
            text = "I started it in the background and will report the output once it finishes.",
        )

    private fun reply(vararg parts: Part) =
        MessageWithParts(
            MessageInfo(
                "m2",
                sid,
                Role.Assistant,
                providerId = "antigravity",
                modelId = "m",
                time = MsgTime(2_000, 12_000),
            ),
            parts.toList().toImmutableList(),
        )

    private val prompt =
        MessageWithParts(
            MessageInfo("m1", sid, Role.User, time = MsgTime(1_000, 1_000)),
            listOf(Part("p1", "m1", sid, PartType.Text, "text", text = "what kernel is this?")).toImmutableList(),
        )

    /** The person's prompt and the agent's reply, as the ledger holds them. */
    private fun keeper(vararg parts: Part): TurnFeedKeeper = TurnFeedKeeper(sid).also { k ->
        k.history(listOf(prompt, reply(*parts)))
        k.link(ChatLink.Live)
    }

    /**
     * The reply started and the agent ended its turn; the command goes on (nothing reaches the app until the next
     * prompt).
     */
    private fun turnEnded(k: TurnFeedKeeper, outcome: RunOutcome = RunOutcome.Succeeded) {
        k.event(GatewayEvent.RunStarted(SessionId(sid)), 2_000)
        k.event(GatewayEvent.RunEnded(SessionId(sid), outcome), 12_000)
    }

    private fun step(facts: TurnFactsUi) = facts.work.values.single().steps.first()

    @Test
    fun `B-CHAT-311 a background command its turn left running reads Running, never Stopped`() {
        val k = keeper(bash(), words)
        turnEnded(k)
        val facts = presenter.present(k.feed.value, 60_000)
        val step = step(facts)
        assertEquals(StepState.Running, step.state, "the command still runs")
        assertEquals(2_000L, step.startedAtMs, "the step keeps the time it started")
        assertNull(step.durationMs, "a running step has no time yet")
        assertEquals(
            58_000L,
            shownStepDurationMs(step.state, step.durationMs, step.startedAtMs, 60_000),
            "its own time counts",
        )
        assertTrue(facts.work.values.single().state != WorkLineState.Stopped, "the turn is not stopped")
        // The app is closed and opened while the command runs: from the history alone the step still runs.
        val restarted = keeper(bash(), words)
        assertEquals(StepState.Running, step(presenter.present(restarted.feed.value, 60_000)).state, "after a restart")
    }

    @Test
    fun `B-CHAT-312 a background command left running is on the background line until it ends`() {
        val k = keeper(bash(), words)
        turnEnded(k)
        val task = presenter.present(k.feed.value, 60_000).background.single()
        assertEquals("c1", task.callId)
        assertEquals(backgroundTaskName(command), task.name)
        assertEquals("m2", task.messageId)
        assertEquals(2_000L, task.sinceMs)
        assertEquals(command, task.command)
        assertTrue(presenter.ticking(k.feed.value), "the line's clock moves")
        // While the reply runs, the command is the reply's own step, not background work.
        val running = keeper(bash(), words)
        running.event(GatewayEvent.RunStarted(SessionId(sid)), 2_000)
        assertTrue(presenter.present(running.feed.value, 60_000).background.isEmpty())
        // Two such commands: two tasks, the older first.
        val two = keeper(bash("c2", startedAtMs = 9_000), bash("c3", startedAtMs = 4_000), words)
        turnEnded(two)
        assertEquals(listOf("c3", "c2"), presenter.present(two.feed.value, 60_000).background.map { it.callId })
        // The next prompt made the helper speak: the call's own result ends it, and the line clears.
        k.event(GatewayEvent.PartUpserted(bash(status = ToolStatus.Completed, output = "ok")), 60_000)
        val ended = presenter.present(k.feed.value, 61_000)
        assertEquals(StepState.Done, step(ended).state)
        assertTrue(ended.background.isEmpty(), "the status line clears")
        assertFalse(presenter.ticking(k.feed.value), "nothing runs: the clock stands still")
    }

    @Test
    fun `B-CHAT-313 its real result replaces the running state, and a stopped turn stays Stopped`() {
        val done = keeper(bash(status = ToolStatus.Completed, output = "ok"), words)
        turnEnded(done)
        val facts = presenter.present(done.feed.value, 60_000)
        assertEquals(StepState.Done, step(facts).state)
        assertTrue(facts.background.isEmpty())
        // The person stopped the turn: a turn they stopped stays Stopped, its command too.
        val stopped = keeper(bash(), words)
        turnEnded(stopped, RunOutcome.Interrupted(InterruptReason.User))
        val stoppedFacts = presenter.present(stopped.feed.value, 60_000)
        assertEquals(WorkLineState.Stopped, stoppedFacts.work.values.single().state)
        assertEquals(StepState.Stopped, step(stoppedFacts).state)
        assertTrue(stoppedFacts.background.isEmpty())
        assertFalse(presenter.ticking(stopped.feed.value))
    }
}
