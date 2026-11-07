package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveSilk
import kotlin.test.Test

/** The `silk golden, dark, phone` case of test/kit_live_styles_industrial_test.dart: the look alone on its ground. */
class KitLiveSilkGateTest {
    @Test fun kit_live_silk() {
        FlutterGoldenGate.check(name = "kit_live_silk", frame = FlutterGoldenGate.Frame.Screen, modes = listOf("dark")) {
            LiveGoldenScene(KitLiveSilk)
        }
    }
}
