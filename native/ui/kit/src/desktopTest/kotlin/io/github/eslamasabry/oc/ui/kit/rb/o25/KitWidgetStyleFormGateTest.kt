package io.github.eslamasabry.oc.ui.kit.rb.o25

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitWidgetStyleForm
import kotlin.test.Test

/**
 * test/goldens/kit/kit_widget_style_form_golden_test.dart: a card, tinted, blue, six accents named by their ids and
 * coloured `Colors.primaries[id.length % 10]` (Material's primaries are the test's inputs, typed here as it has them).
 */
class KitWidgetStyleFormGateTest {
    private val primaries =
        listOf(
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5),
            Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50),
        )

    private val default: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            KitWidgetStyleForm(
                previewSize = "card",
                background = "tinted",
                accent = "blue",
                accents = listOf("wallpaper", "app", "green", "teal", "blue", "amber"),
                accentName = { it },
                accentColor = { primaries[it.length % 10] },
                onPreviewSize = {},
                onBackground = {},
                onAccent = {},
            )
        }
    }

    @Test fun kit_widget_style_form_default() { FlutterGoldenGate.check("kit_widget_style_form_default", content = default) }

    @Test fun kit_widget_style_form_default_1280x800() {
        FlutterGoldenGate.check("kit_widget_style_form_default_1280x800", content = default)
    }

    @Test fun kit_widget_style_form_default_text2() {
        FlutterGoldenGate.check("kit_widget_style_form_default_text2", modes = listOf("dark"), content = default)
    }

    @Test fun kit_widget_style_form_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_widget_style_form_default_text2_1280x800", modes = listOf("dark"), content = default)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_widget_style_form",
            caps = mapOf("KitWidgetStyleForm" to "reading"),
            content = default,
        )
    }
}
