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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import kotlin.test.Test

// Flutter's Icons.refresh (Material filled glyph), which the golden shows.
private val materialRefresh: ImageVector by lazy {
    ImageVector.Builder("refresh", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes("M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"),
        fill = SolidColor(Color.Black),
    ).build()
}

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
                KitIconButton(materialRefresh, "Reload", {})
            }
        }
    }

    @Test fun kit_ink_layer_rest() { FlutterGoldenGate.check("kit_ink_layer_rest") { scene() } }
    @Test fun kit_ink_layer_rest_1280x800() { FlutterGoldenGate.check("kit_ink_layer_rest_1280x800") { scene() } }
    @Test fun kit_ink_layer_rest_text2() { FlutterGoldenGate.check("kit_ink_layer_rest_text2") { scene() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_ink_layer", caps = mapOf("KitInkLayer" to "wrapper")) { scene() } }
}
