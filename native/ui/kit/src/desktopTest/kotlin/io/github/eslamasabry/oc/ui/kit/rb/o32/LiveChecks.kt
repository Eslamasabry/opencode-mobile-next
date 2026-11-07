package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyles
import kotlin.math.ceil
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules every look keeps (Dart `test/kit_live_styles_industrial_test.dart` and kit_live_style.dart's contract). */
internal object LiveChecks {
    /** Registered once, under its Dart id, byte for byte. */
    fun registered(style: KitLiveStyle, id: String) {
        assertEquals(id, style.id)
        assertEquals(1, kitLiveStyles.count { it.id == id }, "$id is in the picker list once")
        assertTrue(id.encodeToByteArray().contentEquals(style.id.encodeToByteArray()))
    }

    /** Same clock, same picture; three seconds on, another picture; something shows over the ground. */
    fun deterministicAndAlive(style: KitLiveStyle) {
        for (dark in listOf(true, false)) {
            val p = LiveRender.palette(dark)
            val a = LiveRender.pixels(style, p)
            val b = LiveRender.pixels(style, p)
            assertContentEquals(a, b, "${style.id}: same t, same picture")
            val c = LiveRender.pixels(style, p, t = LiveRender.T + 3)
            assertFalse(a.contentEquals(c), "${style.id}: it is alive")
            assertTrue(LiveRender.maxDelta(a, p) > 0, "${style.id}: it is visible")
        }
    }

    /** At strength 1 the look stays within the alpha ceiling times its factor, idle and running (sparse looks). */
    fun underCeiling(style: KitLiveStyle) {
        for (dark in listOf(true, false)) {
            val p = LiveRender.palette(dark)
            val cap = ceil(p.ceiling * style.ceilingFactor * 255).toInt()
            for (run in listOf(0.0, 1.0)) {
                val worst = LiveRender.maxDelta(LiveRender.pixels(style, p, running = run), p)
                assertTrue(worst <= cap + 2, "${style.id} dark=$dark run=$run: $worst over the ceiling $cap")
            }
        }
    }
}
