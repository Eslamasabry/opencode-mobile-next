package io.github.eslamasabry.oc.ui.kit.content.rb.o23

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.content.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitWebPreview
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitWebPreviewPhase
import kotlin.test.Test

/**
 * test/goldens/kit/kit_web_preview_golden_test.dart: `_Page`, a 360 × 480 preview of http://127.0.0.1:5173/ whose
 * (fake, blank) page either loads or reports that nothing answered.
 */
class KitWebPreviewGateTest {
    @Composable private fun page(fail: Boolean) {
        Box(Modifier.size(360.dp, 480.dp)) {
            KitWebPreview(5173, if (fail) KitWebPreviewPhase.Failed else KitWebPreviewPhase.Ready, {}) { }
        }
    }
    private fun gate(name: String, vararg modes: String = arrayOf("light", "dark"), fail: Boolean) =
        FlutterGoldenGate.check(name = name, modes = modes.toList(), wrap = { FlutterCapture(it) }) { page(fail) }

    @Test fun kit_web_preview_error() { gate("kit_web_preview_error", fail = true) }
    @Test fun kit_web_preview_error_text2() { gate("kit_web_preview_error_text2", "dark", fail = true) }
    @Test fun kit_web_preview_error_text2_1280x800() { gate("kit_web_preview_error_text2_1280x800", "dark", fail = true) }
    @Test fun kit_web_preview_page() { gate("kit_web_preview_page", fail = false) }
    @Test fun kit_web_preview_page_1280x800() { gate("kit_web_preview_page_1280x800", fail = false) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_web_preview", caps = mapOf("KitWebPreview" to "reading")) { page(true) } }
}
