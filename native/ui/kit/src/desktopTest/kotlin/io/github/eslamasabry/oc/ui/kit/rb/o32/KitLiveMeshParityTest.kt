package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveMesh
import io.github.eslamasabry.oc.ui.kit.live.mixRgb
import io.github.eslamasabry.oc.ui.kit.live.packArgb
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_mesh.dart at 8302de537. */
class KitLiveMeshParityTest {
    private val style = KitLiveMesh()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "mesh")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(8, KitLiveMesh.DIV)
        assertEquals(.03, KitLiveMesh.EVERY)
        assertEquals(4, KitLiveMesh.POINTS)
    }

    @Test fun colourHelpersPackAsFlutter() {
        val mixed = mixRgb(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(10.0, 20.0, 30.0), .5)
        assertContentEquals(doubleArrayOf(5.0, 10.0, 15.0), mixed)
        assertEquals(0x80FF0001.toInt(), packArgb(300.0, -4.0, 1.4, .5))
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
