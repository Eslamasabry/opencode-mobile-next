package io.github.eslamasabry.oc.ui.kit.content.rb.o23

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitWebPreview
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitWebPreviewPhase
import kotlin.test.Test

/**
 * test/goldens/kit/kit_web_preview_golden_test.dart `_Page`: a 360x480 box holding KitWebPreview for
 * http://127.0.0.1:5173/ with the test's fake web view (a blank page). `error`: the page reported that nothing answered
 * (412x915, light and dark; 2.0 text dark only at 412x915 and 1280x800); `page`: the blank page (412x915 and 1280x800,
 * light and dark).
 */
class KitWebPreviewGateTest {
    private fun scene(phase: KitWebPreviewPhase): @Composable () -> Unit = {
        Box(Modifier.size(360.dp, 480.dp)) { KitWebPreview(phase, port = 5173, onRetry = {}) }
    }

    @Test fun kit_web_preview_error() { FlutterGoldenGate.check("kit_web_preview_error", content = scene(KitWebPreviewPhase.Failed)) }
    @Test fun kit_web_preview_error_text2() {
        FlutterGoldenGate.check("kit_web_preview_error_text2", modes = listOf("dark"), content = scene(KitWebPreviewPhase.Failed))
    }
    @Test fun kit_web_preview_error_text2_1280x800() {
        FlutterGoldenGate.check("kit_web_preview_error_text2_1280x800", modes = listOf("dark"), content = scene(KitWebPreviewPhase.Failed))
    }
    @Test fun kit_web_preview_page() { FlutterGoldenGate.check("kit_web_preview_page", content = scene(KitWebPreviewPhase.Ready)) }
    @Test fun kit_web_preview_page_1280x800() {
        FlutterGoldenGate.check("kit_web_preview_page_1280x800", content = scene(KitWebPreviewPhase.Ready))
    }
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_web_preview", caps = mapOf("KitWebPreview" to "reading"), content = scene(KitWebPreviewPhase.Failed))
    }
}
