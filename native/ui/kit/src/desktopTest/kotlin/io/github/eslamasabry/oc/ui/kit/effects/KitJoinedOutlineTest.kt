package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Audit 2026-11-14 (c26): what is drawn along the message box (its running glow) follows the one joined body while the
 * jump pill is joined to it, in the box's own px, and the box's own outline while it stands alone.
 */
class KitJoinedOutlineTest {
    /** Runs [check] on the test's UI thread inside a composition test, as the draw phase calls these. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun onUi(check: () -> Unit) = androidx.compose.ui.test.v2.runComposeUiTest {
        setContent {}
        runOnIdle { check() }
    }

    private fun scene(dropGranted: Boolean): Pair<KitLiquidJoinBody, KitLiquidJoinDrop> {
        val join = KitLiquidJoin()
        val body = KitLiquidJoinBody(join, "bottom")
        val drop = KitLiquidJoinDrop(join, "bottom")
        join.bodies["bottom"] = body
        join.drops += drop
        // The box at (20, 500) in the window, 640 x 160; the pill 240 x 64 sunk 16 px into its top.
        body.rest = Rect(20f, 500f, 660f, 660f)
        body.radius = 52f
        body.granted = true
        drop.rest = Rect(220f, 452f, 460f, 516f)
        drop.radius = 32f
        drop.granted = dropGranted
        drop.reach = { 112f }
        return body to drop
    }

    @Test
    fun `B-GLASS-JOIN-OUTLINE-06 a joined body's band runs over the drop, in the body's own px`() = onUi {
        val (body, _) = scene(dropGranted = true)
        assertTrue(body.joined)
        val band = assertNotNull(body.joinedBand(4f, centred = false))
        val bounds = band.getBounds()
        // The pill's top lies 48 px above the box's top (the body's own y 0); the band starts there.
        assertEquals(-48f, bounds.top, 1.5f)
        assertEquals(0f, bounds.left, 1.5f)
        assertEquals(160f, bounds.bottom, 1.5f)
        // An origin moves it with the node that draws it.
        assertEquals(-48f + 24f, assertNotNull(body.joinedFill(Offset(24f, 24f))).getBounds().top, 1.5f)
    }

    @Test
    fun `B-GLASS-JOIN-OUTLINE-07 the same geometry is traced once, and a body alone has no joined outline`() = onUi {
        val (body, drop) = scene(dropGranted = true)
        assertSame(body.joinedBand(4f, centred = false), body.joinedBand(4f, centred = false))
        drop.granted = false
        assertTrue(!body.joined)
        assertNull(body.joinedBand(4f, centred = false))
        assertNull(body.joinedFill())
    }
}
