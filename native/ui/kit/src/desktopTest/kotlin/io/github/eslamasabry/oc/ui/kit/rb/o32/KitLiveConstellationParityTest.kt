package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveConstellation
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_constellation.dart at 8302de537. */
class KitLiveConstellationParityTest {
    private val style = KitLiveConstellation()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "constellation")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(12, style.framesPerSecond)
        assertEquals(38, KitLiveConstellation.COUNT)
        assertEquals(5, KitLiveConstellation.SEED)
        assertEquals(.6f, KitLiveConstellation.LINE)
        assertEquals(1.1f, KitLiveConstellation.DOT)
    }

    @Test fun starsAreSeededInsideThePageWithSlowSpeeds() {
        val st = KitLiveConstellation.Stars(412.0, 915.0)
        for (i in 0 until KitLiveConstellation.COUNT) {
            assertTrue(st.x[i] in 0.0..412.0 && st.y[i] in 0.0..915.0)
            assertTrue(kotlin.math.abs(st.vx[i]) <= .06 && kotlin.math.abs(st.vy[i]) <= .06)
        }
        // Reseeding gives the same sky.
        val again = KitLiveConstellation.Stars(412.0, 915.0)
        assertTrue(st.x.contentEquals(again.x) && st.vy.contentEquals(again.vy))
    }

    @Test fun starsWrapAroundTheEdges() {
        val memory = KitLiveMemory()
        val p = LiveRender.palette(true)
        // Many small steps never leave the page.
        for (k in 0..400) LiveRender.pixels(style, p, t = k * .1, memory = memory)
        val st = memory.of("s") { KitLiveConstellation.Stars(0.0, 0.0) }
        for (i in 0 until KitLiveConstellation.COUNT) assertTrue(st.x[i] in 0.0..412.0 && st.y[i] in 0.0..915.0)
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
