package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import kotlin.math.ceil
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared cases of test/kit_live_styles_industrial_test.dart for one look: visible and under its ceiling at both
 * sizes, idle and running, in dark and light; the same t gives the same picture and three seconds later it has moved.
 */
internal fun checkLook(style: KitLiveStyle, runs: List<Double> = listOf(0.0, 1.0)) {
    for (p in listOf(darkPalette, lightPalette)) {
        val cap = ceil(p.ceiling * style.ceilingFactor * 255).toInt()
        for (size in listOf(PHONE, WIDE)) {
            for (run in runs) {
                val worst = maxDelta(render(style, size, p, running = run), p)
                assertTrue(worst > 0, "${style.id} is visible")
                assertTrue(worst <= cap + 2, "${style.id} dark=${p.dark} $size run=$run over the ceiling ($worst > $cap)")
            }
        }
        val a = render(style, PHONE, p)
        val b = render(style, PHONE, p)
        assertContentEquals(a, b, "same t, same picture")
        val c = render(style, PHONE, p, t = GOLDEN_T + 3)
        assertFalse(a.contentEquals(c), "it is alive")
    }
}
