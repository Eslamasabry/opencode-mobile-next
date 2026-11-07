package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitPanel
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import kotlin.test.Test

/** part-kit_panel has no golden (lib/ui/kit/kit_panel.dart): its native-only axes (schools, scales, directions, cap). */
class KitPanelGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_panel", caps = mapOf("KitPanel" to "reading"), wrap = { FlutterCapture(it) }) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                KitPanel(title = "Last run", icon = KitIcons.info) { KitText("Finished in 3 minutes with 12 changes.", role = TypeRole.Body) }
            }
        }
    }
}
