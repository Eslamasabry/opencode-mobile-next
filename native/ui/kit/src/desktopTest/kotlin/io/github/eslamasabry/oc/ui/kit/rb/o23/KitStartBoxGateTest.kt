package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import kotlin.test.Test

/** test/goldens/kit/kit_start_box_golden_test.dart: `_part` (16 dp padding, the question), empty and typed. */
class KitStartBoxGateTest {
    @Composable private fun part(text: String = "") {
        KitStartBox(rememberTextFieldState(text), "What would you like to build?", Modifier.padding(16.dp))
    }
    @Test fun kit_start_box_default() { gate23("kit_start_box_default") { part() } }
    @Test fun kit_start_box_default_1280x800() { gate23("kit_start_box_default_1280x800") { part() } }
    @Test fun kit_start_box_typed() { gate23("kit_start_box_typed") { part("A neon brick breaker game for the phone") } }
    @Test fun kit_start_box_default_text2() { gate23("kit_start_box_default_text2", "dark") { part() } }
    @Test fun kit_start_box_default_text2_1280x800() { gate23("kit_start_box_default_text2_1280x800", "dark") { part() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_start_box", caps = mapOf("KitStartBox" to "conversation")) { part() } }
}
