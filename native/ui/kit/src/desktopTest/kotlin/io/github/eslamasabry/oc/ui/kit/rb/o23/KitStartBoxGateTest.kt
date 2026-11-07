package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import kotlin.test.Test

/**
 * test/goldens/kit/kit_start_box_golden_test.dart: KitStartBox(question: 'What would you like to build?') in 16 padding,
 * unfocused; `default` empty (412x915 and 1280x800, light and dark; 2.0 text dark only) and `typed` with
 * 'A neon brick breaker game for the phone' (412x915, light and dark).
 */
class KitStartBoxGateTest {
    private fun part(text: String = ""): @Composable () -> Unit = {
        KitStartBox(rememberTextFieldState(text), "What would you like to build?", Modifier.padding(16.dp))
    }

    @Test fun kit_start_box_default() { FlutterGoldenGate.check("kit_start_box_default", content = part()) }
    @Test fun kit_start_box_default_1280x800() { FlutterGoldenGate.check("kit_start_box_default_1280x800", content = part()) }
    @Test fun kit_start_box_default_text2() { FlutterGoldenGate.check("kit_start_box_default_text2", modes = listOf("dark"), content = part()) }
    @Test fun kit_start_box_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_start_box_default_text2_1280x800", modes = listOf("dark"), content = part())
    }
    @Test fun kit_start_box_typed() { FlutterGoldenGate.check("kit_start_box_typed", content = part("A neon brick breaker game for the phone")) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_start_box", caps = mapOf("KitStartBox" to "conversation"), content = part()) }
}
