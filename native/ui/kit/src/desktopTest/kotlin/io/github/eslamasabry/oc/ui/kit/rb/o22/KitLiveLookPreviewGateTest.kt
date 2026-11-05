package io.github.eslamasabry.oc.ui.kit.rb.o22

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveLookPreview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.Test

class KitLiveLookPreviewGateTest {
    @Test fun kit_live_look_preview_disabled() {
        FlutterGoldenGate.check("kit_live_look_preview_disabled") { KitLiveLookPreview(onTap = {}) }
    }

    @Test fun kit_live_look_preview_disabled_1280x800() {
        FlutterGoldenGate.check("kit_live_look_preview_disabled_1280x800") {
            // The Flutter wide golden sits 16 dp lower than the phone frame's 16 dp top inset.
            Box(Modifier.padding(top = 16.dp)) { KitLiveLookPreview(onTap = {}) }
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_live_look_preview", caps = mapOf("KitLiveLookPreview" to "reading")) {
            KitLiveLookPreview(onTap = {})
        }
    }
}
