package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeOut
import androidx.navigationevent.NavigationEvent
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitEasing
import kotlin.math.abs
import io.github.eslamasabry.oc.ui.routes.RouteMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Plan 08 §5.4/M8-9 `ShellMotionTest`: the predictive preview's direction, size and the reduced-motion rule. */
class ShellMotionTest {
    private fun near(expected: Float, actual: Float, what: String, tolerance: Float = 0.002f) =
        assertTrue(abs(expected - actual) <= tolerance, "$what: expected $expected, got $actual")

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

    @Test
    fun `MOT-PB a chat that grew from its row shrinks back through its own transform at Full, not the scale preview`() {
        val keep = fadeOut(snap())
        val grow = ShellMotion.predictivePop(NavigationEvent.EDGE_LEFT, ShellMotionLevel.Full, 40, RouteMotion.GrowFromRow, keep)
        assertEquals(EnterTransition.None, grow.targetContentEnter)
        assertSame(keep, grow.initialContentExit, "the page stays while its own transform carries it")
        val calm = ShellMotion.predictivePop(NavigationEvent.EDGE_LEFT, ShellMotionLevel.Calm, 40, RouteMotion.GrowFromRow, keep)
        assertNotEquals(keep, calm.initialContentExit, "Calm keeps the fade preview")
        val off = ShellMotion.predictivePop(NavigationEvent.EDGE_LEFT, ShellMotionLevel.Off, 40, RouteMotion.GrowFromRow)
        assertEquals(ExitTransition.None, off.initialContentExit)
    }

    @Test
    fun `M-001 Calm keeps a fade-only preview, without the scale and shift of Full`() {
        val calm = ShellMotion.predictivePop(NavigationEvent.EDGE_LEFT, ShellMotionLevel.Calm, 40)
        val full = ShellMotion.predictivePop(NavigationEvent.EDGE_LEFT, ShellMotionLevel.Full, 40)
        assertEquals(EnterTransition.None, calm.targetContentEnter)
        assertNotEquals(ExitTransition.None, calm.initialContentExit, "Calm still previews (a fade)")
        assertNotEquals(full.initialContentExit, calm.initialContentExit, "Calm drops the scale and shift")
    }

    @Test
    fun `M-001 one predictive-back span of 300 ms, the same value as the motion tokens`() {
        assertEquals(300, ShellMotion.PREDICTIVE_SPAN)
        assertEquals(MotionTokens.PREDICTIVE_SPAN, ShellMotion.PREDICTIVE_SPAN)
        assertSame(KitEasing.PredictiveBack, ShellMotion.PredictiveBack)
    }

    @Test
    fun `M-012 page timings are Flutter's fade-forwards`() {
        assertEquals(450, ShellMotion.PAGE_MS, "FadeForwardsPageTransitionsBuilder.kTransitionMilliseconds")
        assertEquals(338, ShellMotion.PAGE_FADE_IN_MS, "Interval(0, 0.75) of 450 ms")
        assertEquals(113, ShellMotion.PAGE_FADE_OUT_MS, "Interval(0, 0.25) of 450 ms")
        assertEquals(103, ShellMotion.pageTravelPx(412), "a quarter of the width")
        assertEquals(270, ShellMotion.pageTravelPx(1080))
    }

    @Test
    fun `M-012 the page curve is Flutter's easeInOutCubicEmphasized`() {
        // Reference values from curves.dart ThreePointCubic.transformInternal (bisection in Python, 1e-7).
        val e = ShellMotion.Emphasized
        assertEquals(0f, e.transform(0f))
        assertEquals(1f, e.transform(1f))
        near(0.0935f, e.transform(0.1f), "t=0.10")
        near(0.4f, e.transform(0.166666f), "midpoint")
        near(0.7728f, e.transform(0.25f), "t=0.25")
        near(0.9506f, e.transform(0.5f), "t=0.50")
        near(0.9914f, e.transform(0.75f), "t=0.75")
    }

    @Test
    fun `M-013 Calm, power saver and heat cross-fade pages and only Off is instant`() {
        val full = ShellMotionLevel.Full
        assertEquals(ShellMotionLevel.Full, shellLevel(full, MotionLevel.Full))
        assertEquals(ShellMotionLevel.Calm, shellLevel(full, MotionLevel.Calm), "Calm (and saver/heat → Calm)")
        assertEquals(ShellMotionLevel.Off, shellLevel(full, MotionLevel.Off), "Animations: Off")
        MotionLevel.entries.forEach {
            assertEquals(ShellMotionLevel.Off, shellLevel(ShellMotionLevel.Off, it), "system Remove animations")
        }
        assertEquals(ShellMotionLevel.Calm, shellLevel(ShellMotionLevel.Calm, MotionLevel.Full))
    }
}
