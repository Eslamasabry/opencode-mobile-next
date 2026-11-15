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
}
