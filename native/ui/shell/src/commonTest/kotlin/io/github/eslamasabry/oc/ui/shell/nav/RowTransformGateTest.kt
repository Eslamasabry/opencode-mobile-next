package io.github.eslamasabry.oc.ui.shell.nav

import io.github.eslamasabry.oc.ui.shell.nav.GateEvent.ButtonBack
import io.github.eslamasabry.oc.ui.shell.nav.GateEvent.GestureCancelled
import io.github.eslamasabry.oc.ui.shell.nav.GateEvent.GestureCommitted
import io.github.eslamasabry.oc.ui.shell.nav.GateEvent.GestureStarted
import io.github.eslamasabry.oc.ui.shell.nav.GateEvent.TapOpen
import io.github.eslamasabry.oc.ui.shell.nav.GateEvent.TransitionSettled
import io.github.eslamasabry.oc.ui.shell.nav.GateState.Closing
import io.github.eslamasabry.oc.ui.shell.nav.GateState.Gesture
import io.github.eslamasabry.oc.ui.shell.nav.GateState.GestureSettling
import io.github.eslamasabry.oc.ui.shell.nav.GateState.Idle
import io.github.eslamasabry.oc.ui.shell.nav.GateState.Opening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Plan 08 §5.4 `RowTransformGateTest`: every transition of the gate table (B-KIT-054). */
class RowTransformGateTest {
    private fun run(vararg events: GateEvent): GateState = events.fold(Idle) { s, e -> reduce(s, e) }

    @Test
    fun `B-KIT-054 tap opens and settles back to idle`() {
        assertEquals(Opening, run(TapOpen))
        assertEquals(Idle, run(TapOpen, TransitionSettled))
    }

    @Test
    fun `B-KIT-054 button back closes from idle and from opening`() {
        assertEquals(Closing, run(ButtonBack))
        assertEquals(Closing, run(TapOpen, ButtonBack))
        assertEquals(Idle, run(ButtonBack, TransitionSettled))
    }

    @Test
    fun `B-KIT-054 a gesture disables shared bounds until its transition settles, then the next tap transforms`() {
        var s = run(TapOpen, TransitionSettled, GestureStarted)
        assertEquals(Gesture, s)
        assertFalse(sharedBoundsEnabled(s, ShellMotionLevel.Full))
        s = reduce(s, GestureCommitted)
        assertEquals(GestureSettling, s)
        assertFalse(sharedBoundsEnabled(s, ShellMotionLevel.Full), "no shared-bounds flash while the pop finishes")
        s = reduce(s, TransitionSettled)
        assertEquals(Idle, s)
        assertTrue(sharedBoundsEnabled(s, ShellMotionLevel.Full))
        assertEquals(Opening, reduce(s, TapOpen), "the bug in the 07 sketch: the next tap must get its transform")
    }

    @Test
    fun `B-KIT-054 cancel settles the same way as commit`() {
        assertEquals(Idle, run(GestureStarted, GestureCancelled, TransitionSettled))
        // The dispatcher reports Idle after onBack: a late cancel after a commit changes nothing.
        assertEquals(GestureSettling, run(GestureStarted, GestureCommitted, GestureCancelled))
        assertEquals(GestureSettling, run(GestureStarted, GestureCancelled, GestureCommitted))
    }

    @Test
    fun `B-KIT-054 a gesture during the opening transform turns the transform off at once`() {
        val s = run(TapOpen, GestureStarted)
        assertEquals(Gesture, s)
        assertFalse(sharedBoundsEnabled(s, ShellMotionLevel.Full))
    }

    @Test
    fun `B-KIT-054 settling never ends a gesture that is still under the finger`() {
        assertEquals(Gesture, run(GestureStarted, TransitionSettled))
    }

    @Test
    fun `B-KIT-054 taps and button backs are ignored mid-gesture`() {
        assertEquals(Gesture, run(GestureStarted, TapOpen))
        assertEquals(Gesture, run(GestureStarted, ButtonBack))
        assertEquals(GestureSettling, run(GestureStarted, GestureCommitted, TapOpen))
    }

    @Test
    fun `B-KIT-054 reduced motion never enables shared bounds`() {
        GateState.entries.forEach { assertFalse(sharedBoundsEnabled(it, ShellMotionLevel.Off), "$it") }
        assertEquals(ShellMotionLevel.Off, motionLevelOf(0f))
        assertEquals(ShellMotionLevel.Full, motionLevelOf(0.5f))
        assertEquals(ShellMotionLevel.Full, motionLevelOf(1f))
    }

    @Test
    fun `B-KIT-054 the holder tracks direction for the bounds spec`() {
        val gate = RowTransformGate()
        gate.on(TapOpen)
        assertTrue(gate.opening)
        gate.on(TransitionSettled)
        gate.on(ButtonBack)
        assertFalse(gate.opening)
        assertEquals(listOf(TapOpen, TransitionSettled, ButtonBack), gate.trace)
    }
}
