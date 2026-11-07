package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.kit.live.KitLiveFlowThreads
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_flow_threads.dart at 8302de537. */
class KitLiveFlowThreadsParityTest {
    private val style = KitLiveFlowThreads()

    @Test fun idAndConstants() {
        LiveChecks.registered(style, "flowThreads")
        assertEquals(1.0, style.ceilingFactor)
        assertEquals(220, KitLiveFlowThreads.COUNT)
        assertEquals(12, KitLiveFlowThreads.TRAIL)
        assertEquals(7, KitLiveFlowThreads.SEED)
        assertEquals(1.1f, KitLiveFlowThreads.STROKE.width)
    }

    @Test fun aTrailKeepsItsLastTwelvePointsInOrder() {
        val q = KitLiveFlowThreads.Thread(0.0, 0.0)
        for (k in 0 until 20) q.add(k.toDouble(), -k.toDouble())
        assertEquals(12, q.count)
        for (k in 0 until 12) {
            assertEquals((k + 8).toFloat(), q.xAt(k))
            assertEquals(-(k + 8).toFloat(), q.yAt(k))
        }
        q.clear()
        assertEquals(0, q.count)
    }

    @Test fun threadsAreSeededInsideThePage() {
        val st = KitLiveFlowThreads.Threads(412.0, 915.0)
        assertEquals(220, st.p.size)
        st.p.forEach { kotlin.test.assertTrue(it.x in 0.0..412.0 && it.y in 0.0..915.0) }
    }

    @Test fun deterministicVisibleAndAlive() = LiveChecks.deterministicAndAlive(style)
}
