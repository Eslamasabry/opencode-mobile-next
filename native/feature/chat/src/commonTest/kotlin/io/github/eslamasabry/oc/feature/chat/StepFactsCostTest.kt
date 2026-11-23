package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.MsgTime
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedKeeper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** B-CHAT-096: a long chat works out step facts only for what changed, not every turn each second. */
class StepFactsCostTest {
    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private fun bashPart(i: Int, j: Int, output: String): Part {
        val sid = Ref.sessionId.raw
        return Part(
            "t$i-$j",
            "a$i",
            sid,
            PartType.Tool,
            "tool",
            callId = "t$i-$j",
            toolName = "bash",
            tool = ToolState(
                ToolStatus.Completed,
                input = JsonObject(mapOf("command" to JsonPrimitive("echo $j"))),
                output = output,
                startedAtMs = 1_000L + i * 10 + 2,
                completedAtMs = 1_000L + i * 10 + 3,
            ),
        )
    }

    private fun history(): List<MessageWithParts> {
        val sid = Ref.sessionId.raw
        val messages = ArrayList<MessageWithParts>()
        for (i in 0 until 200) {
            val t = 1_000L + i * 10
            messages += MessageWithParts(
                MessageInfo("u$i", sid, Role.User, time = MsgTime(t, null)),
                listOf(Part("q$i", "u$i", sid, PartType.Text, "text", text = "go $i", ended = true)).toImmutableList(),
            )
            val parts = ArrayList<Part>()
            for (j in 0 until 5) parts += bashPart(i, j, "x".repeat(10_000))
            parts += Part("d$i", "a$i", sid, PartType.Text, "text", text = "done", ended = true)
            messages += MessageWithParts(
                MessageInfo("a$i", sid, Role.Assistant, time = MsgTime(t + 1, null)),
                parts.toImmutableList(),
            )
        }
        messages += MessageWithParts(
            MessageInfo("u200", sid, Role.User, time = MsgTime(3_000, null)),
            listOf(Part("q200", "u200", sid, PartType.Text, "text", text = "run it", ended = true)).toImmutableList(),
        )
        messages += MessageWithParts(
            MessageInfo("a200", sid, Role.Assistant, time = MsgTime(3_001, null)),
            listOf(
                Part(
                    "run",
                    "a200",
                    sid,
                    PartType.Tool,
                    "tool",
                    callId = "run",
                    toolName = "bash",
                    tool = ToolState(ToolStatus.Running, startedAtMs = 5_000),
                ),
            ).toImmutableList(),
        )
        return messages
    }

    private fun runPart(k: Int): Part {
        val sid = Ref.sessionId.raw
        val lines = (1..k).joinToString("\n") { "line $it" }
        return Part(
            "run",
            "a200",
            sid,
            PartType.Tool,
            "tool",
            callId = "run",
            toolName = "bash",
            tool = ToolState(
                ToolStatus.Running,
                startedAtMs = 5_000,
                metadata = JsonObject(mapOf("output" to JsonPrimitive(lines))),
            ),
        )
    }

    @Test
    fun `B-CHAT-096 a progress event and a tick reuse every settled turn's step facts`() {
        val keeper = TurnFeedKeeper(Ref.sessionId.raw)
        keeper.running(100)
        keeper.history(history(), newest = true)
        val presenter = TurnFactsPresenter(FixedClock)
        val first = presenter.present(keeper.feed.value, 10_000)
        fun reusesSettledTurns(work: Map<String, TurnWork>, at: String) {
            for (i in 0 until 200) assertSame(first.work["u$i"], work["u$i"], "turn u$i at $at")
        }
        for (k in 1..50) {
            keeper.event(GatewayEvent.PartUpserted(runPart(k)), 10_000L + k)
            val next = presenter.present(keeper.feed.value, 10_000L + k)
            reusesSettledTurns(next.work, "event $k")
            assertTrue(
                next.work["u200"]!!.steps.single().output.liveTail.endsWith("line $k"),
                "live tail after event $k",
            )
        }
        var now = 10_050L
        repeat(50) { tick ->
            now += 1_000
            reusesSettledTurns(presenter.present(keeper.feed.value, now).work, "tick $tick")
        }
    }

    @Test
    fun `B-CHAT-096 a settled turn whose step changed is worked out again`() {
        val keeper = TurnFeedKeeper(Ref.sessionId.raw)
        keeper.running(100)
        keeper.history(history(), newest = true)
        val presenter = TurnFactsPresenter(FixedClock)
        val before = presenter.present(keeper.feed.value, 10_000)
        assertEquals("x".repeat(10_000), before.work["u5"]!!.steps[0].output.text)
        keeper.event(GatewayEvent.PartUpserted(bashPart(5, 0, "changed")), 11_000)
        val after = presenter.present(keeper.feed.value, 11_000)
        assertEquals("changed", after.work["u5"]!!.steps[0].output.text)
        assertEquals("x".repeat(10_000), after.work["u6"]!!.steps[0].output.text)
    }

    @Test
    fun `B-CHAT-112 two calls that differ only in output are not the same view`() {
        val a = toolOutputView(ToolState(ToolStatus.Completed, output = "a"), null)
        val b = toolOutputView(ToolState(ToolStatus.Completed, output = "b"), null)
        assertFalse(a == b)
    }
}
