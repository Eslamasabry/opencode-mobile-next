package io.github.eslamasabry.oc.ui.kit.rb.o17

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitContextRegion
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import kotlin.test.Test

/** KitContextRegion draws its child and nothing else while idle (Dart `kit_context_region.dart`). */
class KitContextRegionGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_context_region", caps = mapOf("KitContextRegion" to "wrapper")) {
            KitContextRegion(menu = { listOf(KitMenuItem("Open", onSelect = {})) }, modifier = Modifier.padding(16.dp)) { KitText("Fix the login bug") }
        }
    }
}
