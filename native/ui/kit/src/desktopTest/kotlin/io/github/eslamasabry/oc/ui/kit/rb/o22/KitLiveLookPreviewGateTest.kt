package io.github.eslamasabry.oc.ui.kit.rb.o22

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveLookPreview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_live_look_preview_golden_test.dart: `_part(enabled)` is the tile (aurora, normal
 * intensity) inside a 16 dp padding.
 *
 * The native port draws only the disabled state (no `style`, `intensity` or `enabled` input), so the `on` shots render
 * that state too; under flutter test the on tile holds the flat ground and two mock chat lines instead of "Off".
 */
class KitLiveLookPreviewGateTest {
    /** `_part(true)`: the tile in its 16 dp padding, as kitGalleryPart lays it out. */
    private val on: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().padding(16.dp)) { KitLiveLookPreview(onTap = {}) }
    }

    @Test fun kit_live_look_preview_disabled() {
        FlutterGoldenGate.check("kit_live_look_preview_disabled") { KitLiveLookPreview(onTap = {}) }
    }

    @Test fun kit_live_look_preview_disabled_1280x800() {
        FlutterGoldenGate.check("kit_live_look_preview_disabled_1280x800") {
            // The Flutter wide golden sits 16 dp lower than the phone frame's 16 dp top inset.
            Box(Modifier.padding(top = 16.dp)) { KitLiveLookPreview(onTap = {}) }
        }
    }

    @Test fun kit_live_look_preview_on() { FlutterGoldenGate.check("kit_live_look_preview_on", content = on) }

    @Test fun kit_live_look_preview_on_1280x800() { FlutterGoldenGate.check("kit_live_look_preview_on_1280x800", content = on) }

    @Test fun kit_live_look_preview_on_text2() {
        FlutterGoldenGate.check("kit_live_look_preview_on_text2", modes = listOf("dark"), content = on)
    }

    @Test fun kit_live_look_preview_on_text2_1280x800() {
        FlutterGoldenGate.check("kit_live_look_preview_on_text2_1280x800", modes = listOf("dark"), content = on)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_live_look_preview", caps = mapOf("KitLiveLookPreview" to "reading")) {
            KitLiveLookPreview(onTap = {})
        }
    }
}
