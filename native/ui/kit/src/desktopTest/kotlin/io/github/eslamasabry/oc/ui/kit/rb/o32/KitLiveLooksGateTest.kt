package io.github.eslamasabry.oc.ui.kit.rb.o32

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveHoneycomb
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMatrixRain
import io.github.eslamasabry.oc.ui.kit.live.KitLiveStyle
import io.github.eslamasabry.oc.ui.kit.live.rememberKitLiveLayer
import kotlin.test.Test

/**
 * The order-32 goldens of test/kit_live_styles_industrial_test.dart: each look over its ground on a 412×915 phone at
 * DPR 1, clock 7.3 s, idle, strength 1 (`_Painter`), drawn through the same layer the page uses.
 */
class KitLiveLooksGateTest {
    private fun scene(style: KitLiveStyle): @androidx.compose.runtime.Composable () -> Unit = {
        Spacer(Modifier.fillMaxSize().then(rememberKitLiveLayer(style, 1.0) { LiveRender.T }))
    }

    @Test fun kit_live_honeycomb() {
        FlutterGoldenGate.check(
            "kit_live_honeycomb",
            frame = FlutterGoldenGate.Frame.Screen,
            content = scene(KitLiveHoneycomb()),
        )
    }

    @Test fun kit_live_matrixrain() {
        // The Dart test draws the ASCII set so the picture does not depend on a system CJK face.
        FlutterGoldenGate.check(
            "kit_live_matrixrain",
            frame = FlutterGoldenGate.Frame.Screen,
            modes = listOf("dark"),
            content = scene(KitLiveMatrixRain(KitLiveMatrixRain.ASCII_GLYPHS)),
        )
    }
}
