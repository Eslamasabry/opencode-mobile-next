package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitInkLayer
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.test.Test

class KitInkLayerGateTest {
    @Composable private fun scene() {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            KitInkLayer(RoundedCornerShape(0.dp)) { KitRow("Open project", onClick = {}) }
            KitInkLayer(RoundedCornerShape(0.dp)) { KitRow("Recent sessions", onClick = {}) }
            Spacer(Modifier.height(16.dp))
            KitInkLayer(RoundedCornerShape(22.dp)) { KitButton("Connect", onClick = {}, fullWidth = true) }
            Spacer(Modifier.height(8.dp))
            KitInkLayer(RoundedCornerShape(22.dp)) {
                KitButton("Not now", onClick = {}, role = KitButtonRole.Secondary, fullWidth = true)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                KitChip("Filter", kind = KitChipKind.Action, onClick = {})
                Spacer(Modifier.width(8.dp))
                KitIconButton(KitIcons.retry, "Reload", {})
            }
        }
    }

    @Test fun kit_ink_layer_rest() { FlutterGoldenGate.check("kit_ink_layer_rest") { scene() } }
    @Test fun kit_ink_layer_rest_1280x800() { FlutterGoldenGate.check("kit_ink_layer_rest_1280x800") { scene() } }
    @Test fun kit_ink_layer_rest_text2() { FlutterGoldenGate.check("kit_ink_layer_rest_text2") { scene() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_ink_layer", caps = mapOf("KitInkLayer" to "wrapper")) { scene() } }
}
