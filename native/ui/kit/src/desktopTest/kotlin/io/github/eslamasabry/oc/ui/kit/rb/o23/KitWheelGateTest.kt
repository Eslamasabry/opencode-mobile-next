package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitWheel
import io.github.eslamasabry.oc.ui.kit.choice.KitWheelColumn
import kotlin.test.Test

/**
 * test/goldens/kit/kit_wheel_golden_test.dart `_wheel`: hour 1..12 at 9, minute 00/15/30/45 at 00, AM/PM at AM, in 16
 * padding; 412x915 and 1280x800, light and dark, each also at 2.0 text (the wheel caps itself at 1.5).
 */
class KitWheelGateTest {
    private val wheel: @Composable () -> Unit = {
        KitWheel(
            listOf(
                KitWheelColumn((1..12).toList(), 9, { "$it" }, {}, "Hour"),
                KitWheelColumn(listOf("00", "15", "30", "45"), "00", { it }, {}, "Minute"),
                KitWheelColumn(listOf("AM", "PM"), "AM", { it }, {}, "Morning or evening"),
            ),
            Modifier.padding(16.dp),
        )
    }

    @Test fun kit_wheel_default() { FlutterGoldenGate.check("kit_wheel_default", content = wheel) }
    @Test fun kit_wheel_default_1280x800() { FlutterGoldenGate.check("kit_wheel_default_1280x800", content = wheel) }
    @Test fun kit_wheel_default_text2() { FlutterGoldenGate.check("kit_wheel_default_text2", content = wheel) }
    @Test fun kit_wheel_default_text2_1280x800() { FlutterGoldenGate.check("kit_wheel_default_text2_1280x800", content = wheel) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_wheel", caps = mapOf("KitWheel" to "control"), content = wheel) }
}
