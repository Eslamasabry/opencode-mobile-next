package io.github.eslamasabry.oc.ui.kit.rb.o21

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.input.KitOneTimeCode
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import kotlin.test.Test

/** test/goldens/kit/kit_one_time_code_golden_test.dart: `_code`, "VPFI-KC2ZK" under "Your code", in a space4 padding. */
class KitOneTimeCodeGateTest {
    @Composable
    private fun code() {
        KitOneTimeCode(code = "VPFI-KC2ZK", copyLabel = "Copy code", label = "Your code", modifier = Modifier.padding(Space.S4.dp))
    }

    private fun shot(name: String) = FlutterGoldenGate.check(wrap = { FlutterCapture(it) }, name = name) { code() }

    @Test fun kit_one_time_code_default() { shot("kit_one_time_code_default") }
    @Test fun kit_one_time_code_default_360x800() { shot("kit_one_time_code_default_360x800") }
    @Test fun kit_one_time_code_default_800x1280() { shot("kit_one_time_code_default_800x1280") }
    @Test fun kit_one_time_code_default_1280x800() { shot("kit_one_time_code_default_1280x800") }
    @Test fun kit_one_time_code_default_1600x1000() { shot("kit_one_time_code_default_1600x1000") }
    @Test fun kit_one_time_code_default_text2() { shot("kit_one_time_code_default_text2") }
    @Test fun kit_one_time_code_default_text2_1280x800() { shot("kit_one_time_code_default_text2_1280x800") }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_one_time_code", caps = mapOf("KitOneTimeCode" to "control")) { code() }
    }
}
