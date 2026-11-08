package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Audit C09 (2026-11-09): Running on this phone in Arabic showed "MB 215.0" and "%3.0". [KitUnits] keeps a number and
 * its unit as one left-to-right run inside a right-to-left line.
 */
@OptIn(ExperimentalTestApi::class)
class KitUnitsRtlTest {
    private val lri = KitBidi.LRI
    private val pdi = KitBidi.PDI

    @Test
    fun `C09 a quantity and a percent are one isolated run`() {
        assertEquals("${lri}215.0 MB$pdi", KitUnits.quantity("215.0", "MB"))
        assertEquals("${lri}3.0%$pdi", KitUnits.percent("3.0"))
    }

    @Test
    fun `C09 isolate finds every unit run in a built line and leaves the words alone`() {
        assertEquals(
            "مشغول · ${lri}91.8%$pdi · ${lri}215.0 MB$pdi · قيد التشغيل منذ 12 د",
            KitUnits.isolate("مشغول · 91.8% · 215.0 MB · قيد التشغيل منذ 12 د"),
        )
        assertEquals("${lri}1.5 GB$pdi", KitUnits.isolate("1.5 GB"))
        // A unit that is part of a longer word, and a bare number, stay as they are.
        assertEquals("12 MBit", KitUnits.isolate("12 MBit"))
        assertEquals("12 د", KitUnits.isolate("12 د"))
    }

    @Test
    fun `C09 isolating twice changes nothing`() {
        val once = KitUnits.isolate("Busy · 45% · 43 MB")
        assertEquals(once, KitUnits.isolate(once))
    }

    @Test
    fun `C09 in a right-to-left line the unit stays after its number, read left to right`() =
        runDesktopComposeUiTest(800, 200) {
            var isolated: TextLayoutResult? = null
            var plain: TextLayoutResult? = null
            val line = "يعمل · 215.0 MB"
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val measurer = rememberTextMeasurer()
                    val style = TextStyle(textDirection = TextDirection.Rtl)
                    isolated = measurer.measure(kitUnits(line), style)
                    plain = measurer.measure(line, style)
                }
            }
            waitForIdle()
            fun order(layout: TextLayoutResult, text: String): Pair<Float, Float> {
                val digit = text.indexOf('2')
                val unit = text.indexOf('M')
                return layout.getBoundingBox(digit).left to layout.getBoundingBox(unit).left
            }
            val (digit, unit) = order(isolated!!, KitUnits.isolate(line))
            assertTrue(unit > digit, "isolated: MB must sit to the right of 215.0 (digit $digit, unit $unit)")
            // The guard sees the bug it prevents: without the isolate the unit jumps to the left of the number.
            val (bareDigit, bareUnit) = order(plain!!, line)
            assertTrue(bareUnit < bareDigit, "plain: bidi puts MB first (digit $bareDigit, unit $bareUnit)")
        }
}
