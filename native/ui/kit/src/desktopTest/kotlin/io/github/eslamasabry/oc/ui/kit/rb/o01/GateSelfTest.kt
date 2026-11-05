package io.github.eslamasabry.oc.ui.kit.rb.o01

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Standard §8.2: the blob measure sees a missing 8 px square and does not see a 1 px shifted text line. */
class GateSelfTest {
    private fun frame(draw: (BufferedImage) -> Unit): BufferedImage {
        val img = BufferedImage(412, 915, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 915) for (x in 0 until 412) img.setRGB(x, y, 0xF4F4F4)
        draw(img)
        return img
    }

    private fun textLine(img: BufferedImage, dx: Int) {
        for (x in 40..300) for (y in 100..103) img.setRGB(x + dx, y, 0x202020)
    }

    @Test
    fun `a red 8 by 8 square is a blob of at least 36 px`() {
        val a = frame { textLine(it, 0) }
        val b = frame { textLine(it, 0); for (x in 200..207) for (y in 300..307) it.setRGB(x, y, 0xE01010) }
        val (m, _) = FlutterGoldenGate.measure(a, b)
        assertTrue(m.blob >= 36, "blob ${m.blob}")
        assertEquals("same-size", m.size)
    }

    @Test
    fun `a 1 px shift of a text line is a blob of at most 16 px`() {
        val a = frame { textLine(it, 0) }
        val b = frame { textLine(it, 1) }
        val (m, _) = FlutterGoldenGate.measure(a, b)
        assertTrue(m.blob <= 16, "blob ${m.blob}")
    }

    @Test
    fun `identical frames measure zero`() {
        val a = frame { textLine(it, 0) }
        val (m, _) = FlutterGoldenGate.measure(a, a)
        assertEquals(0, m.blob)
        assertEquals(0.0, m.boxPct)
    }

    @Test
    fun `the name grammar sets the axes`() {
        val a = FlutterGoldenGate.axesOf("kit_icon_sizes_ar_text2_1280x800")
        assertTrue(a.rtl); assertEquals(2f, a.scale); assertEquals(1280, a.w); assertEquals(800, a.h)
        val b = FlutterGoldenGate.axesOf("kit_text_type")
        assertTrue(!b.rtl); assertEquals(1f, b.scale); assertEquals(412, b.w); assertEquals(915, b.h)
    }
}
