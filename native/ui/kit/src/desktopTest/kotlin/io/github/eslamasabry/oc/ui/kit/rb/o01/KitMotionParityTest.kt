package io.github.eslamasabry.oc.ui.kit.rb.o01

import io.github.eslamasabry.oc.ui.design.generated.FlutterKitMotion as M
import io.github.eslamasabry.oc.ui.design.motion.CurveId
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/kit_motion.dart at 8302de537, typed from the Dart file. */
class KitMotionParityTest {
    @Test
    fun durations() {
        assertEquals(150, M.QUICK_MS); assertEquals(250, M.STANDARD_MS); assertEquals(300, M.ROW_RETURN_MS)
        assertEquals(900, M.ENTRANCE_MS); assertEquals(1400, M.CELEBRATION_MS); assertEquals(4000, M.BREATH_MS)
        assertEquals(8000, M.ESCALATE_AFTER_MS); assertEquals(100, M.PRESS_HOLD_MS); assertEquals(8000, M.UNDO_WINDOW_MS)
        assertEquals(2000, M.COPIED_HOLD_MS); assertEquals(2000, M.LOG_POLL_MS); assertEquals(300, M.TYPING_SETTLE_MS)
        assertEquals(450, M.EDGE_LIGHT_HUE_FADE_MS)
        assertEquals(2000, M.STEP_LAP_MS); assertEquals(4000, M.STEP_LAP_CALM_MS); assertEquals(1600, M.STEP_BREATHE_MS)
        assertEquals(300, M.STEP_FLASH_MS); assertEquals(200, M.STEP_FADE_MS); assertEquals(600, M.STEP_TICK_MS)
        assertEquals(260, M.STEP_SPRING_MS); assertEquals(2200, M.STEP_SWEEP_MS)
        assertEquals(600, M.CONTEXT_VALUE_MS); assertEquals(320, M.CONTEXT_COLOR_MS); assertEquals(700, M.CONTEXT_HALO_MS)
        assertEquals(900, M.CONTEXT_DRAIN_MS); assertEquals(1200, M.CONTEXT_SPIN_MS); assertEquals(2000, M.TERMINAL_COPIED_NOTE_MS)
    }

    @Test
    fun tokensReadTheGeneratedValues() {
        assertEquals(M.QUICK_MS, MotionTokens.QUICK); assertEquals(M.STANDARD_MS, MotionTokens.STANDARD)
        assertEquals(M.ROW_RETURN_MS, MotionTokens.ROW_RETURN); assertEquals(M.PRESS_HOLD_MS, MotionTokens.PRESS_HOLD)
        assertEquals(M.ESCALATE_AFTER_MS, MotionTokens.ESCALATE_AFTER); assertEquals(M.UNDO_WINDOW_MS, MotionTokens.UNDO_WINDOW)
        assertEquals(M.COPIED_HOLD_MS, MotionTokens.COPIED_HOLD); assertEquals(M.TYPING_SETTLE_MS, MotionTokens.TYPING_SETTLE)
        assertEquals(M.EDGE_LIGHT_HUE_FADE_MS, MotionTokens.GLOW_FADE); assertEquals(M.MARK_FROM, MotionTokens.MARK_FROM)
    }

    @Test
    fun springs() {
        assertEquals(Triple(1f, 560f, 32f), Triple(M.LENS_LEAD_MASS, M.LENS_LEAD_STIFFNESS, M.LENS_LEAD_DAMPING))
        assertEquals(Triple(1f, 210f, 23f), Triple(M.LENS_TRAIL_MASS, M.LENS_TRAIL_STIFFNESS, M.LENS_TRAIL_DAMPING))
        assertEquals(Triple(1f, 700f, 36f), Triple(M.LENS_DRAG_LEAD_MASS, M.LENS_DRAG_LEAD_STIFFNESS, M.LENS_DRAG_LEAD_DAMPING))
        assertEquals(Triple(1f, 260f, 26f), Triple(M.LENS_DRAG_TRAIL_MASS, M.LENS_DRAG_TRAIL_STIFFNESS, M.LENS_DRAG_TRAIL_DAMPING))
        assertEquals(Triple(1f, 260f, 20f), Triple(M.LENS_LIFT_MASS, M.LENS_LIFT_STIFFNESS, M.LENS_LIFT_DAMPING))
        assertEquals(Triple(1f, 700f, 53f), Triple(M.REPLY_GROW_MASS, M.REPLY_GROW_STIFFNESS, M.REPLY_GROW_DAMPING))
        assertEquals(M.REPLY_GROW_STIFFNESS, MotionTokens.REPLY_GROW_K)
    }

    @Test
    fun lapsPerSecond() {
        assertEquals(1f / 6, M.EDGE_LIGHT_MAX_LAPS_PER_SECOND); assertEquals(1f / 8, M.ACTIVITY_GLOW_LAPS_PER_SECOND)
        assertEquals(1f / 3.6f, M.CLASSIC_GLOW_LAPS_PER_SECOND, 1e-6f); assertEquals(1f / 1.8f, M.GLOW_MAX_LAPS_PER_SECOND, 1e-6f)
        assertEquals(1f / 2, M.STOP_RING_LAPS_PER_SECOND); assertEquals(0.9f, M.MARK_FROM)
    }

    @Test
    fun curves() {
        // easeOutCubic (enter), easeInCubic (exit), easeInOut (pulse): Flutter's own control points.
        assertEquals(listOf(.215f, .61f, .355f, 1f), CurveId.Enter.let { listOf(it.x1, it.y1, it.x2, it.y2) })
        assertEquals(listOf(.55f, .055f, .675f, .19f), CurveId.Exit.let { listOf(it.x1, it.y1, it.x2, it.y2) })
        assertEquals(listOf(.42f, 0f, .58f, 1f), CurveId.Pulse.let { listOf(it.x1, it.y1, it.x2, it.y2) })
        // easeInOutQuad (stepEase) and easeOutQuad (stepOut).
        assertEquals(listOf(.455f, .03f, .515f, .955f), CurveId.StepEase.let { listOf(it.x1, it.y1, it.x2, it.y2) })
        assertEquals(listOf(.25f, .46f, .45f, .94f), CurveId.StepOut.let { listOf(it.x1, it.y1, it.x2, it.y2) })
    }

    @Test
    fun levelsDecideWhatMoves() {
        // reduced = Off; travels = Full (and not reduced); loops = Full, not under a still frame.
        val full = KitMotionState(MotionLevel.Full)
        val calm = KitMotionState(MotionLevel.Calm)
        val off = KitMotionState(MotionLevel.Off)
        assertTrue(off.reduced); assertFalse(calm.reduced); assertFalse(full.reduced)
        assertTrue(full.travels); assertFalse(calm.travels); assertFalse(off.travels)
        assertTrue(full.ambientLoops); assertFalse(calm.ambientLoops); assertFalse(off.ambientLoops)
        assertFalse(KitMotionState(MotionLevel.Full, stillFrame = true).ambientLoops)
    }
}
