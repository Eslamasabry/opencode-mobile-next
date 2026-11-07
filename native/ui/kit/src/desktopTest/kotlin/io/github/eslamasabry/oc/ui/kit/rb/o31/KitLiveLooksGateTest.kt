package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBlueprint
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBokeh
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBrushedMetal
import io.github.eslamasabry.oc.ui.kit.live.KitLiveCaustics
import io.github.eslamasabry.oc.ui.kit.live.KitLiveCircuit
import kotlin.test.Test

/** Every golden of order 31: test/goldens/live_styles/kit_live_<id lower case>_<mode>.png (phone, density 1). */
class KitLiveLooksGateTest {
    private val screen = FlutterGoldenGate.Frame.Screen

    @Test fun blueprint() {
        FlutterGoldenGate.check("kit_live_blueprint", frame = screen, modes = listOf("dark")) { LiveShot(KitLiveBlueprint) }
    }

    @Test fun bokeh() {
        FlutterGoldenGate.check("kit_live_bokeh", frame = screen, modes = listOf("dark")) { LiveShot(KitLiveBokeh) }
    }

    @Test fun brushedMetal() {
        FlutterGoldenGate.check("kit_live_brushedmetal", frame = screen, modes = listOf("dark")) { LiveShot(KitLiveBrushedMetal) }
    }

    @Test fun caustics() {
        FlutterGoldenGate.check("kit_live_caustics", frame = screen, modes = listOf("dark")) { LiveShot(KitLiveCaustics) }
    }

    @Test fun circuit() {
        FlutterGoldenGate.check("kit_live_circuit", frame = screen) { LiveShot(KitLiveCircuit) }
    }
}
