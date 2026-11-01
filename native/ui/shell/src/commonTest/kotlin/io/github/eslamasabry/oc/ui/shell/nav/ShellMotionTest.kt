package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigationevent.NavigationEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Plan 08 §5.4/M8-9 `ShellMotionTest`: the predictive preview's direction, size and the reduced-motion rule. */
class ShellMotionTest {
    @Test
    fun `B-KIT-054 the preview shifts away from the swiped edge, never by layout direction`() {
        assertEquals(1, ShellMotion.shiftSign(NavigationEvent.EDGE_LEFT))
        assertEquals(-1, ShellMotion.shiftSign(NavigationEvent.EDGE_RIGHT))
        assertEquals(0, ShellMotion.shiftSign(NavigationEvent.EDGE_NONE), "3-button long-press: no shift")
    }

    @Test
    fun `B-KIT-054 edge shift is width over 20 minus 8 dp`() {
        assertEquals(1080 / 20 - 21, ShellMotion.edgeShiftPx(1080, 2.625f))
        assertEquals(0, ShellMotion.edgeShiftPx(100, 3f), "never negative on narrow windows")
    }

    @Test
    fun `B-KIT-054 reduced motion has no preview, so Back commits on release`() {
        val off = ShellMotion.predictivePop(NavigationEvent.EDGE_LEFT, ShellMotionLevel.Off, 40)
        assertEquals(EnterTransition.None, off.targetContentEnter)
        assertEquals(ExitTransition.None, off.initialContentExit)
        val full = ShellMotion.predictivePop(NavigationEvent.EDGE_LEFT, ShellMotionLevel.Full, 40)
        assertEquals(EnterTransition.None, full.targetContentEnter, "the page underneath holds still")
        assertNotEquals(ExitTransition.None, full.initialContentExit)
    }
}
