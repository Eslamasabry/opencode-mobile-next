package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitFloatingPanel
import kotlin.test.Test

/**
 * The default scene of test/goldens/kit/kit_floating_panel_golden_test.dart: a 600 dp tall box holding the panel over
 * its dimmed ground, its child the line "A small panel over the home screen". The ground's spoken name is the Flutter
 * l10n `widgetV2Cancel` ("Cancel"). Approximation: the Flutter child is a plain Material `Text` (bodyMedium); here it is
 * a body KitText.
 */
class KitFloatingPanelGateTest {
    private val scene: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(600.dp)) {
            KitFloatingPanel(onDismiss = {}, dismissLabel = "Cancel") { KitText("A small panel over the home screen") }
        }
    }

    @Test fun kit_floating_panel_default() { FlutterGoldenGate.check("kit_floating_panel_default", content = scene) }
    @Test fun kit_floating_panel_default_1280x800() { FlutterGoldenGate.check("kit_floating_panel_default_1280x800", content = scene) }
    @Test fun kit_floating_panel_default_text2() { FlutterGoldenGate.check("kit_floating_panel_default_text2", modes = listOf("dark"), content = scene) }
    @Test fun kit_floating_panel_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_floating_panel_default_text2_1280x800", modes = listOf("dark"), content = scene)
    }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_floating_panel", caps = mapOf("KitFloatingPanel" to "fill"), content = scene) }
}
