package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F4, option C: where a message menu opens around the held face (the prompt's bubble), pure. A 360 dp phone at 3x:
 * status bar + step 132 px, navigation bar + step 84 px, gutter 48 px, three rows of 144 px the least room, a step of
 * 24 px between the face and the panel.
 */
class MessageMenuPlacementTest {
    private val window = MenuWindow(1080, 2400, margin = 48, top = 132, bottom = 84, minRoom = 432)
    private val gap = 24
    private val band = 288
    private val panel = IntSize(600, 900)

    private fun IntRect.meets(o: IntRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    private fun placed(face: IntRect, point: IntOffset? = null, rtl: Boolean = false, size: IntSize = panel): IntRect {
        val box = MenuPlacement.faceBox(face, point, band, gap, window)
        val held = IntSize(size.width, minOf(size.height, MenuPlacement.room(box.top, box.bottom, window)))
        val at = MenuPlacement.faceOffset(box, held, rtl, window)
        return IntRect(at, held)
    }

    @Test
    fun `B-KIT-MENU-006 the menu opens above the bubble when it fits, below near the top, a step clear either way`() {
        val low = IntRect(300, 1500, 1032, 1640)
        val above = placed(low)
        assertEquals(low.top - gap, above.bottom, "above, a step clear")
        assertEquals(low.right, above.right, "its end edge on the bubble's end edge")
        val high = IntRect(300, 300, 1032, 440)
        val below = placed(high)
        assertEquals(high.bottom + gap, below.top, "near the top: below, a step clear")
        assertEquals(high.left, placed(high, rtl = true).left, "right to left: on the bubble's left edge")
    }

    @Test
    fun `B-KIT-MENU-007 neither side has room for the whole menu, it takes the roomier side and still clears the bubble`() {
        val middle = IntRect(300, 1000, 1032, 1200)
        val at = placed(middle, size = IntSize(600, 1400))
        assertTrue(!at.meets(middle), "never over the bubble ($at, bubble $middle)")
        assertTrue(at.top >= window.top && at.bottom <= window.height - window.bottom, "inside the safe area ($at)")
    }

    @Test
    fun `B-KIT-MENU-008 a message taller than the screen opens beside the finger, not from the corner or over the finger`() {
        val tall = IntRect(48, 0, 1032, 2400)
        val finger = IntOffset(500, 1400)
        val at = placed(tall, finger)
        assertTrue(at.bottom <= finger.y - band / 2 || at.top >= finger.y + band / 2, "the finger's band stays clear ($at)")
        assertTrue(at.top >= window.top && at.bottom <= window.height - window.bottom, "inside the safe area ($at)")
    }

    @Test
    fun `B-KIT-MENU-010 the dim is the style's own, Glass a light tint and every other style its scrim`() {
        assertEquals(0.58f, MenuDimRules.alpha(glass = false, scrimAlpha = 0.58f), "Solid and the others: the kit's scrim")
        val glass = MenuDimRules.alpha(glass = true, scrimAlpha = 0.10f)
        assertTrue(glass > 0.10f && glass < 0.5f, "Glass: stronger than its sheet scrim, so the held face reads, and a tint the background shows through ($glass)")
    }

    @Test
    fun `B-KIT-MENU-009 a narrow bubble at the start keeps the menu inside the gutters`() {
        val short = IntRect(48, 1500, 200, 1600)
        val at = placed(short)
        assertTrue(at.left >= window.left, "inside the start gutter ($at)")
        assertTrue(at.right <= window.width - window.right, "inside the end gutter ($at)")
    }
}
