package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveOscilloscope
import kotlin.test.Test

/** The `oscilloscope golden, dark, phone` case of test/kit_live_styles_industrial_test.dart: the look alone on its ground. */
class KitLiveOscilloscopeGateTest {
    @Test fun kit_live_oscilloscope() {
        FlutterGoldenGate.check(name = "kit_live_oscilloscope", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark")) {
            LiveGoldenScene(KitLiveOscilloscope)
        }
    }
}
