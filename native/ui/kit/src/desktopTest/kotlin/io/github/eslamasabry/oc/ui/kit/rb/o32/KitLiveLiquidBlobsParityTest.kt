package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveLiquidBlobs
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart at 8302de537. */
class KitLiveLiquidBlobsParityTest {
    private val style = KitLiveLiquidBlobs()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "liquidBlobs")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(6, KitLiveLiquidBlobs.DIV)
        assertEquals(.03, KitLiveLiquidBlobs.EVERY)
        assertEquals(4, KitLiveLiquidBlobs.BLOBS)
    }

    @Test fun theGlowBlobOnlyWeighsWhileAReplyRuns() {
        // Idle and running differ only by the fourth blob; idle draws, running draws something else.
        val p = LiveRender.palette(true)
        val idle = LiveRender.pixels(style, p)
        val running = LiveRender.pixels(style, p, running = 1.0)
        kotlin.test.assertTrue(!idle.contentEquals(running))
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
