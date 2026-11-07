package io.github.eslamasabry.oc.ui.kit.rb.o08

import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.state.KitChecklistRules
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.KitStep
import io.github.eslamasabry.oc.ui.kit.state.KitStepState
import io.github.eslamasabry.oc.ui.kit.team.KitPriority
import io.github.eslamasabry.oc.ui.kit.team.KitTaskCardRules
import io.github.eslamasabry.oc.ui.kit.team.KitTaskFlagKind
import io.github.eslamasabry.oc.ui.kit.team.KitTaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules of lib/ui/kit/kit_checklist.dart and lib/ui/kit/kit_task_card.dart at 8302de537. */
class KitChecklistTaskCardRulesTest {
    private fun s(state: KitStepState, person: Boolean = false, paused: Boolean = false) =
        KitStep("x", state, paused = paused, personAction = if (person) KitAction("Allow", {}) else null)

    @Test
    fun `B-KIT-CHECKLIST-001 the current step is the first working, failed or person step, else the first waiting, else the last`() {
        assertEquals(1, KitChecklistRules.current(listOf(s(KitStepState.Done), s(KitStepState.Working), s(KitStepState.Failed))))
        assertEquals(2, KitChecklistRules.current(listOf(s(KitStepState.Done), s(KitStepState.Waiting), s(KitStepState.Failed))))
        assertEquals(1, KitChecklistRules.current(listOf(s(KitStepState.Done), s(KitStepState.Waiting, person = true))))
        assertEquals(1, KitChecklistRules.current(listOf(s(KitStepState.Done), s(KitStepState.Waiting), s(KitStepState.Waiting))))
        assertEquals(1, KitChecklistRules.current(listOf(s(KitStepState.Done), s(KitStepState.Done))))
    }

    @Test
    fun `B-KIT-CHECKLIST-002 before start, working and done follow the steps, a paused step is not working`() {
        assertTrue(KitChecklistRules.beforeStart(listOf(s(KitStepState.Waiting), s(KitStepState.Waiting))))
        assertFalse(KitChecklistRules.beforeStart(listOf(s(KitStepState.Waiting, person = true))))
        assertFalse(KitChecklistRules.working(listOf(s(KitStepState.Working, paused = true))))
        assertTrue(KitChecklistRules.working(listOf(s(KitStepState.Working))))
        assertTrue(KitChecklistRules.allDone(listOf(s(KitStepState.Done))))
        assertFalse(s(KitStepState.Done, person = true).needsPerson)
    }

    @Test
    fun `B-KIT-CHECKLIST-003 a step refuses a retry off the failed row, a paused finished step and a value out of range`() {
        assertFailsWith<IllegalArgumentException> { KitStep("x", KitStepState.Done, retry = KitAction("Try again", {})) }
        assertFailsWith<IllegalArgumentException> { KitStep("x", KitStepState.Done, paused = true) }
        assertFailsWith<IllegalArgumentException> { KitStep("x", KitStepState.Working, value = 1.5f) }
    }

    @Test
    fun `B-KIT-CHECKLIST-004 the button moves under the words past half the row or past 1_6x text`() {
        assertFalse(KitChecklistRules.buttonBelow(100f, 300f, 1f))
        assertTrue(KitChecklistRules.buttonBelow(160f, 300f, 1f))
        assertTrue(KitChecklistRules.buttonBelow(100f, 300f, 2f))
    }

    @Test
    fun `B-KIT-TASKCARD-001 priority bars, resting titles, moving receipts and title lines`() {
        assertEquals(listOf(3, 3, 2, 1, 0), KitPriority.entries.map { KitTaskCardRules.litBars(it) })
        assertTrue(KitTaskCardRules.resting(KitTaskState.Done))
        assertTrue(KitTaskCardRules.resting(KitTaskState.Stopped))
        assertFalse(KitTaskCardRules.resting(KitTaskState.Working))
        assertTrue(KitTaskCardRules.moving(KitReceiptState.Sending))
        assertTrue(KitTaskCardRules.moving(KitReceiptState.Sent))
        assertFalse(KitTaskCardRules.moving(KitReceiptState.Confirmed))
        assertEquals(2, KitTaskCardRules.titleLines(1f))
        assertEquals(3, KitTaskCardRules.titleLines(1.3f))
        assertTrue(KitTaskCardRules.markDrawsFlagGlyph(KitTaskFlagKind.Failed, KitTaskState.Failed))
        assertFalse(KitTaskCardRules.markDrawsFlagGlyph(KitTaskFlagKind.Blocked, KitTaskState.Waiting))
    }
}
