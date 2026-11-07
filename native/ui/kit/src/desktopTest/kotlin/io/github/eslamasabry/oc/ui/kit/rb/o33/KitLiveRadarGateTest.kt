package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveRadar
import kotlin.test.Test

/** The `radar golden, dark, phone` case of test/kit_live_styles_industrial_test.dart: the look alone on its ground. */
class KitLiveRadarGateTest {
    @Test fun kit_live_radar() {
        FlutterGoldenGate.check(name = "kit_live_radar", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark")) {
            LiveGoldenScene(KitLiveRadar)
        }
    }
}
