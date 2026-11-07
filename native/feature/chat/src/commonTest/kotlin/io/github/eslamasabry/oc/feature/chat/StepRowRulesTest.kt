package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolKind
import io.github.eslamasabry.oc.feature.chat.ui.STEP_DURATION_MIN_MS
import io.github.eslamasabry.oc.feature.chat.ui.shownStepDurationMs
import io.github.eslamasabry.oc.feature.chat.ui.stepRowSubject
import io.github.eslamasabry.oc.feature.chat.ui.stepWaitsForYou
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** E20-S14 (owner's phone, 11-07): the step row's time and the waiting row's file name. */
class StepRowRulesTest {
    private val none = JsonObject(emptyMap())

    @Test
    fun `E20-S14 a finished step under a tenth of a second shows no time`() {
        assertNull(shownStepDurationMs(StepState.Done, 0, 1_000, 5_000), "0 ms read as 0.0s")
        assertNull(shownStepDurationMs(StepState.Done, 40, 1_000, 5_000))
        assertNull(shownStepDurationMs(StepState.Done, STEP_DURATION_MIN_MS - 1, null, 5_000))
        assertEquals(STEP_DURATION_MIN_MS, shownStepDurationMs(StepState.Done, STEP_DURATION_MIN_MS, null, 5_000))
        assertEquals(2_400L, shownStepDurationMs(StepState.Failed, 2_400, null, 5_000))
    }

    @Test
    fun `E20-S14 a running step shows its own clock only once it passes a tenth of a second`() {
        assertNull(shownStepDurationMs(StepState.Running, null, 1_000, 1_050))
        assertEquals(1_500L, shownStepDurationMs(StepState.Running, null, 1_000, 2_500))
        // A clock that runs behind the server's start never shows a negative or zero time.
        assertNull(shownStepDurationMs(StepState.Running, null, 3_000, 2_000))
        // A waiting step has no clock of its own.
        assertNull(shownStepDurationMs(StepState.Pending, null, 1_000, 9_000))
    }

    @Test
    fun `E20-S14 a waiting Write names its file as soon as the streamed input has the whole path`() {
        val half = """{"filePath":"/home/me/app/src/ma"""
        assertNull(stepRowSubject(ToolKind.Write, StepState.Pending, null, none, half), "a half-sent path is not shown")
        val closed = """{"filePath":"/home/me/app/src/main.kt","content":"fun main() {"""
        assertEquals("main.kt", stepRowSubject(ToolKind.Write, StepState.Pending, null, none, closed))
        val escaped = """{"file_path":"C:\\work\\notes \"v2\".md","""
        assertEquals("notes \"v2\".md", stepRowSubject(ToolKind.Write, StepState.Pending, null, none, escaped))
    }

    @Test
    fun `E20-S14 the structured input and the engine's own subject come first`() {
        val input = JsonObject(mapOf("filePath" to JsonPrimitive("lib/ui/chat_screen.dart")))
        assertEquals("chat_screen.dart", stepRowSubject(ToolKind.Edit, StepState.Pending, null, input, ""))
        assertEquals("given.kt", stepRowSubject(ToolKind.Write, StepState.Pending, "given.kt", input, ""))
        // Other kinds and settled steps are left to the engine.
        assertNull(stepRowSubject(ToolKind.Shell, StepState.Pending, null, input, ""))
        assertNull(stepRowSubject(ToolKind.Write, StepState.Done, null, input, ""))
    }

    @Test
    fun `P-105 a step still open while its turn waits for the person waits for them, not runs`() {
        assertTrue(stepWaitsForYou(StepState.Running, WorkLineState.WaitingForYou), "Write c.txt behind an approval")
        assertTrue(stepWaitsForYou(StepState.Pending, WorkLineState.WaitingForYou))
        assertFalse(stepWaitsForYou(StepState.Running, WorkLineState.Running))
        assertFalse(stepWaitsForYou(StepState.Done, WorkLineState.WaitingForYou), "a finished step keeps its own status")
    }

    @Test
    fun `P-102 a stopped or never-run step shows no elapsed time`() {
        assertNull(shownStepDurationMs(StepState.Stopped, 133_000, 2_000, 140_000), "read as 'Stopped 2m 13s'")
        assertNull(shownStepDurationMs(StepState.NotRun, 5_000, 2_000, 140_000))
    }
}
