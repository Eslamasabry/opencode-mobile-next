package io.github.eslamasabry.oc.ui.kit.rb.o22

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.live.KitLiveLookPreview
import kotlin.test.Test

class KitLiveLookPreviewGateTest {
    @Test fun kit_live_look_preview_disabled() {
        FlutterGoldenGate.check("kit_live_look_preview_disabled") { KitLiveLookPreview(onTap = {}) }
    }

    @Test fun kit_live_look_preview_disabled_1280x800() {
        FlutterGoldenGate.check("kit_live_look_preview_disabled_1280x800") { KitLiveLookPreview(onTap = {}) }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_live_look_preview", caps = mapOf("KitLiveLookPreview" to "reading")) {
            KitLiveLookPreview(onTap = {})
        }
    }
}
