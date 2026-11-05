package io.github.eslamasabry.oc.ui.kitchat.rb.o06

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.*
import io.github.eslamasabry.oc.ui.design.type.*
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.theme.*
import kotlin.test.Test
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kitchat.composer.KitLevelMeter

class KitLevelMeterGateTest {
    @Composable private fun scene(level: Float = .9f, active: Boolean = true, ar: Boolean = false) {
        Column(Modifier.fillMaxWidth().background(LocalKitColors.current.surface2, RoundedCornerShape(FlutterKitTokens.PANEL_CORNER_RADIUS.dp)).padding(Space.S5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            KitLevelMeter({level}, active = active)
            Spacer(Modifier.height(Space.S2.dp))
            KitText(if (!active) "Paused · mic off" else if (ar) "يستمع · 0:07" else "Listening 0:07", role = TypeRole.Secondary)
        }
    }
    @Test fun kit_level_meter_listening_high() { FlutterGoldenGate.check("kit_level_meter_listening_high", modes = listOf("dark", "light")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_high_1280x800() { FlutterGoldenGate.check("kit_level_meter_listening_high_1280x800", modes = listOf("dark", "light")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_high_1600x1000() { FlutterGoldenGate.check("kit_level_meter_listening_high_1600x1000", modes = listOf("dark", "light")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_high_360x800() { FlutterGoldenGate.check("kit_level_meter_listening_high_360x800", modes = listOf("dark", "light")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_high_800x1280() { FlutterGoldenGate.check("kit_level_meter_listening_high_800x1280", modes = listOf("dark", "light")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_high_915x412() { FlutterGoldenGate.check("kit_level_meter_listening_high_915x412", modes = listOf("dark", "light")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_high_ar() { FlutterGoldenGate.check("kit_level_meter_listening_high_ar", modes = listOf("dark")) { scene(level = .9f, active = true, ar = true) } }
    @Test fun kit_level_meter_listening_high_ar_1280x800() { FlutterGoldenGate.check("kit_level_meter_listening_high_ar_1280x800", modes = listOf("dark")) { scene(level = .9f, active = true, ar = true) } }
    @Test fun kit_level_meter_listening_high_text2() { FlutterGoldenGate.check("kit_level_meter_listening_high_text2", modes = listOf("dark")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_high_text2_1280x800() { FlutterGoldenGate.check("kit_level_meter_listening_high_text2_1280x800", modes = listOf("dark")) { scene(level = .9f, active = true, ar = false) } }
    @Test fun kit_level_meter_listening_low() { FlutterGoldenGate.check("kit_level_meter_listening_low", modes = listOf("dark", "light")) { scene(level = .2f, active = true, ar = false) } }
    @Test fun kit_level_meter_paused() { FlutterGoldenGate.check("kit_level_meter_paused", modes = listOf("dark", "light")) { scene(level = .6f, active = false, ar = false) } }
    @Test fun kit_level_meter_quiet() { FlutterGoldenGate.check("kit_level_meter_quiet", modes = listOf("dark", "light")) { scene(level = 0f, active = true, ar = false) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_level_meter", caps = mapOf("KitLevelMeter" to "reading")) { scene() } }
}
