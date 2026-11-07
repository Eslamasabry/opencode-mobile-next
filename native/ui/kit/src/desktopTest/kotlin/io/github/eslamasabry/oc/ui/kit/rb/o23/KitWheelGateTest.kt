package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.input.KitWheel
import io.github.eslamasabry.oc.ui.kit.input.KitWheelColumn
import kotlin.test.Test

/** test/goldens/kit/kit_wheel_golden_test.dart: `_wheel`, hour · minute · AM/PM at 9:00 AM. */
class KitWheelGateTest {
    @Composable private fun wheel() {
        KitWheel(
            listOf(
                KitWheelColumn((1..12).toList(), 9, { "$it" }, {}, "Hour"),
                KitWheelColumn(listOf("00", "15", "30", "45"), "00", { it }, {}, "Minute"),
                KitWheelColumn(listOf("AM", "PM"), "AM", { it }, {}, "Morning or evening"),
            ),
            Modifier.padding(16.dp),
        )
    }
    @Test fun kit_wheel_default() { gate23("kit_wheel_default") { wheel() } }
    @Test fun kit_wheel_default_1280x800() { gate23("kit_wheel_default_1280x800") { wheel() } }
    @Test fun kit_wheel_default_text2() { gate23("kit_wheel_default_text2") { wheel() } }
    @Test fun kit_wheel_default_text2_1280x800() { gate23("kit_wheel_default_text2_1280x800") { wheel() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_wheel", caps = mapOf("KitWheel" to "control")) { wheel() } }
}
