package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The checks test/kit_live_styles_industrial_test.dart makes of every look: visible, under its ceiling (plus 2 for
 * rounding) at both sizes, idle and running, in both themes; the same picture for the same second; alive.
 */
internal fun checkLook(style: KitLiveStyle) {
    for (dark in listOf(true, false)) {
        for (size in listOf(PHONE, WIDE)) {
            for (run in listOf(0.0, 1.0)) {
                val f = liveFrame(dark, running = run)
                val worst = maxDelta(renderLook(style, size, f), f)
                assertTrue(worst > 0, "${style.id} is visible (dark=$dark $size run=$run)")
                assertTrue(worst <= capOf(style, f) + 2, "${style.id} dark=$dark $size run=$run: $worst over the ceiling ${capOf(style, f)}")
            }
        }
        val a = renderLook(style, PHONE, liveFrame(dark))
        val b = renderLook(style, PHONE, liveFrame(dark))
        assertTrue(a.contentEquals(b), "${style.id}: same t, same picture")
        val c = renderLook(style, PHONE, liveFrame(dark, t = GOLDEN_T + 3))
        assertFalse(a.contentEquals(c), "${style.id} is alive")
    }
}
