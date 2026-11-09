package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ToolWaitStallTest {
    private val min = 60_000L

    private fun work(tool: String, startedAt: Long?, state: StepState = StepState.Running): Map<String, TurnWork> {
        val tool0 = ToolState(ToolStatus.Running, input = JsonObject(emptyMap()))
        val step = TurnStep("p1", toolFacts(tool, tool0), state, null, startedAt, toolOutputView(tool0, tool))
        return mapOf("t" to TurnWork("t", listOf(step), WorkCounts(steps = 1), WorkLineState.Running, step))
    }

    @Test
    fun `P0-STALL-001 a tool call waiting past three minutes is named`() {
        assertNull(ToolWaitStallRules.of(work("webfetch", 0), 2 * min))
        val stall = assertNotNull(ToolWaitStallRules.of(work("webfetch", 0), 4 * min))
        assertEquals("webfetch", stall.toolName)
        assertEquals(4 * min, stall.waitedMs)
    }

    @Test
    fun `P0-STALL-002 a pending call counts, a finished one and one with no start time do not`() {
        assertNotNull(ToolWaitStallRules.of(work("read", 0, StepState.Pending), 5 * min))
        assertNull(ToolWaitStallRules.of(work("read", 0, StepState.Done), 5 * min))
        assertNull(ToolWaitStallRules.of(work("read", null), 5 * min))
    }

    @Test
    fun `P0-STALL-003 a shell command, a subagent task and a question are never this stall`() {
        for (tool in listOf("bash", "task", "question", "todowrite")) {
            assertNull(ToolWaitStallRules.of(work(tool, 0), 60 * min), tool)
        }
    }
}
