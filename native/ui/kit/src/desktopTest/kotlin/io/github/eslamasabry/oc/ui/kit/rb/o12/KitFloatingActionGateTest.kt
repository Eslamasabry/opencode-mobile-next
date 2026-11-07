package io.github.eslamasabry.oc.ui.kit.rb.o12

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.action.KitFloatingAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/**
 * test/goldens/kit/kit_floating_action_golden_test.dart: `_part()` and `_disabled()`, a 360 x 320 box with the
 * floating "New conversation" over an empty list, at 412x915 and 1280x800, light and dark, and at 2.0 text (dark).
 */
class KitFloatingActionGateTest {
    @Composable
    private fun Part(enabled: Boolean) {
        Box(Modifier.size(width = 360.dp, height = 320.dp)) {
            KitFloatingAction(
                label = "New conversation",
                onClick = if (enabled) ({}) else null,
                icon = KitIcons.add,
            ) { Box(Modifier.fillMaxSize()) }
        }
    }

    @Test fun kit_floating_action_default() { FlutterGoldenGate.check("kit_floating_action_default") { Part(true) } }
    @Test fun kit_floating_action_default_1280x800() { FlutterGoldenGate.check("kit_floating_action_default_1280x800") { Part(true) } }
    @Test fun kit_floating_action_default_text2() {
        FlutterGoldenGate.check("kit_floating_action_default_text2", modes = listOf("dark")) { Part(true) }
    }
    @Test fun kit_floating_action_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_floating_action_default_text2_1280x800", modes = listOf("dark")) { Part(true) }
    }
    @Test fun kit_floating_action_disabled() { FlutterGoldenGate.check("kit_floating_action_disabled") { Part(false) } }
    @Test fun kit_floating_action_disabled_1280x800() { FlutterGoldenGate.check("kit_floating_action_disabled_1280x800") { Part(false) } }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_floating_action", caps = mapOf("KitFloatingAction" to "glyph")) { Part(true) }
    }
}
