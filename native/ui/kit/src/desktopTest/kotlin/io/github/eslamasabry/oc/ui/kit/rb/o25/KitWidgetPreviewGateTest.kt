package io.github.eslamasabry.oc.ui.kit.rb.o25

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreview
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreviewBackground
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreviewSize
import kotlin.test.Test

/** test/goldens/kit/kit_widget_preview_golden_test.dart: the three sizes, one per ground, each in 16 dp padding. */
class KitWidgetPreviewGateTest {
    private val default: @Composable () -> Unit = {
        Column {
            for ((size, bg, accent) in
                listOf(
                    Triple(KitWidgetPreviewSize.Compact, KitWidgetPreviewBackground.Solid, Color(0xFF48AC6F)),
                    Triple(KitWidgetPreviewSize.Card, KitWidgetPreviewBackground.Tinted, Color(0xFF4187CD)),
                    Triple(KitWidgetPreviewSize.List, KitWidgetPreviewBackground.Transparent, Color(0xFFBA8420)),
                )) {
                KitWidgetPreview(size, bg, accent, Modifier.padding(16.dp))
            }
        }
    }

    @Test fun kit_widget_preview_default() { FlutterGoldenGate.check("kit_widget_preview_default", content = default) }

    @Test fun kit_widget_preview_default_1280x800() {
        FlutterGoldenGate.check("kit_widget_preview_default_1280x800", content = default)
    }

    @Test fun kit_widget_preview_default_text2() {
        FlutterGoldenGate.check("kit_widget_preview_default_text2", modes = listOf("dark"), content = default)
    }

    @Test fun kit_widget_preview_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_widget_preview_default_text2_1280x800", modes = listOf("dark"), content = default)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_widget_preview", caps = mapOf("KitWidgetPreview" to "reading"), content = default)
    }
}
