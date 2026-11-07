package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveCircuit
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_circuit.dart at 8302de537. */
class KitLiveCircuitParityTest {
    @Test fun constants() {
        assertEquals("circuit", KitLiveCircuit.id)
        assertEquals(2.0, KitLiveCircuit.ceilingFactor)
        assertEquals(.66, KitLiveCircuit.CALIBRATED)
        assertEquals(14, KitLiveCircuit.STEPS)
        assertEquals(16, KitLiveCircuit.TRACES)
        assertEquals(5, KitLiveCircuit.SEGMENTS)
        assertEquals(listOf(1 to 0, 0 to 1, 1 to 1, -1 to 1, 0 to -1), KitLiveCircuit.DIRS.toList())
    }

    /** The traces Dart 3.9 builds for a 412 × 915 phone from the seed of 'circuit'. */
    @Test fun theTracesAreDartsTraces() {
        val geo = withGraphics { KitLiveCircuit.build(PHONE) }
        assertEquals(16, geo.traces.size)
        assertEquals(32, geo.ends.size)
        val first =
            listOf(
                147.14285714285714 to 353.1428571428571, 206.0 to 411.99999999999994, 117.71428571428572 to 500.2857142857142,
                176.57142857142858 to 559.1428571428571, 176.57142857142858 to 500.2857142857143, 176.57142857142858 to 588.5714285714286,
            )
        val last =
            listOf(
                323.7142857142857 to 500.2857142857143, 323.7142857142857 to 470.85714285714283, 323.7142857142857 to 441.4285714285714,
                412.0 to 529.7142857142857, 353.14285714285717 to 588.5714285714286, 353.14285714285717 to 676.8571428571429,
            )
        for ((trace, want) in listOf(geo.traces[0] to first, geo.traces[15] to last)) {
            assertEquals(want.size, trace.pts.size)
            for ((p, w) in trace.pts.zip(want)) {
                assertEquals(w.first, p.x.toDouble(), 1e-3)
                assertEquals(w.second, p.y.toDouble(), 1e-3)
            }
        }
        assertEquals(0.7868407502254252, geo.traces[0].offset, 1e-15)
        assertEquals(0.2709481704101634, geo.traces[15].offset, 1e-15)
    }

    @Test fun aPulseWalksTheTraceEndToEnd() {
        val tr = withGraphics { KitLiveCircuit.build(PHONE) }.traces[0]
        assertEquals(tr.pts.first(), tr.pointAt(0.0))
        assertEquals(tr.pts.last().x, tr.pointAt(1.0).x, 1e-3f)
        assertEquals(tr.pts.last().y, tr.pointAt(1.0).y, 1e-3f)
    }

    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(KitLiveCircuit)
}
