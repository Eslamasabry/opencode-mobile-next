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

    private val on: @androidx.compose.runtime.Composable () -> Unit = {
        // _part: 16 dp around the tile, aurora at normal intensity, Live background on.
        Box(Modifier.padding(16.dp)) { KitLiveLookPreview(onTap = {}, enabled = true, lookName = "Aurora drift", lookId = "aurora") }
    }

    @Test fun kit_live_look_preview_on() { FlutterGoldenGate.check("kit_live_look_preview_on", content = on) }
    @Test fun kit_live_look_preview_on_1280x800() { FlutterGoldenGate.check("kit_live_look_preview_on_1280x800", content = on) }
    @Test fun kit_live_look_preview_on_text2() { FlutterGoldenGate.check("kit_live_look_preview_on_text2", modes = listOf("dark"), content = on) }
    @Test fun kit_live_look_preview_on_text2_1280x800() {
        FlutterGoldenGate.check("kit_live_look_preview_on_text2_1280x800", modes = listOf("dark"), content = on)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_live_look_preview", caps = mapOf("KitLiveLookPreview" to "reading")) {
            KitLiveLookPreview(onTap = {})
        }
    }
}
