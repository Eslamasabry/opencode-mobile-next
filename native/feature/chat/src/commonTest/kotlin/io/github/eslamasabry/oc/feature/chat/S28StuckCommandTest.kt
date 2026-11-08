package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnStep
import io.github.eslamasabry.oc.engine.transcript.ledger.TurnWork
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkCounts
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolFacts
import io.github.eslamasabry.oc.engine.transcript.turnfacts.toolOutputView
import io.github.eslamasabry.oc.feature.chat.composer.presentation.SendFieldRules
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.StuckCommandRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * S28 story 14 (Flutter `stuck_command.dart`): a foreground command past 15 minutes makes the live line say the
 * reply may be stuck, and on a server that does not steer Send becomes "Stop and send".
 */
class S28StuckCommandTest {
    private val min = 60_000L

    private fun work(tool: String, input: Map<String, JsonElement>, startedAt: Long?, state: StepState = StepState.Running): Map<String, TurnWork> {
        val tool0 = ToolState(ToolStatus.Running, input = JsonObject(input))
        val step = TurnStep("p1", toolFacts(tool, tool0), state, null, startedAt, toolOutputView(tool0, tool))
        return mapOf("t" to TurnWork("t", listOf(step), WorkCounts(ran = 1, steps = 1), WorkLineState.Running, step))
    }

    @Test
    fun `B-CHAT-S28-50 a command past fifteen minutes is stuck, with how long and its short name`() {
        val cmd = mapOf<String, JsonElement>("command" to JsonPrimitive("cd /work/app && npm run dev\nsecond"))
        assertNull(StuckCommandRules.of(work("bash", cmd, startedAt = 0), nowMs = 14 * min))
        assertEquals(
            LiveNoticeUi.StuckCommand("npm run dev", 149 * min),
            StuckCommandRules.of(work("bash", cmd, startedAt = 0), nowMs = 149 * min),
        )
    }

    @Test
    fun `B-CHAT-S28-51 background commands, other tools and steps without a start time are never stuck`() {
        val late = 60 * min
        val bg = mapOf<String, JsonElement>("command" to JsonPrimitive("serve"), "run_in_background" to JsonPrimitive(true))
        assertNull(StuckCommandRules.of(work("bash", bg, 0), late))
        assertNull(StuckCommandRules.of(work("read", mapOf("filePath" to JsonPrimitive("a")), 0), late))
        assertNull(StuckCommandRules.of(work("bash", emptyMap(), null), late))
        assertEquals(LiveNoticeUi.StuckCommand(null, late), StuckCommandRules.of(work("bash", emptyMap(), 0), late))
    }

    @Test
    fun `B-CHAT-S28-52 a long name is cut at forty characters`() {
        assertEquals("a".repeat(39) + "…", StuckCommandRules.commandName("a".repeat(60)))
        assertNull(StuckCommandRules.commandName("  "))
    }

    @Test
    fun `B-CHAT-S28-53 Stop and send only for a hung reply on a server that does not steer`() {
        assertTrue(SendFieldRules.stopsBeforeSend(busy = true, steers = false, stuck = true))
        assertFalse(SendFieldRules.stopsBeforeSend(busy = true, steers = true, stuck = true))
        assertFalse(SendFieldRules.stopsBeforeSend(busy = true, steers = false, stuck = false))
        assertFalse(SendFieldRules.stopsBeforeSend(busy = false, steers = false, stuck = true))
    }
}
