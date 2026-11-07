package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.state.KitWindowRow
import kotlin.test.Test

/**
 * test/goldens/kit/kit_window_row_golden_test.dart: `_windows` (5 hours, Week low with a note, Month used up, Last read
 * calm, an inline 5 hours in 16 side padding; 412x915 and 1280x800, light and dark; 2.0 text dark only) and `_arabic`
 * (two rows, dark only). The native row has no tone (Dart KitWindowTone low/out/calm fill and value tone), so every bar
 * is drawn in the normal accent; valueSemantics (optional in Dart) repeats the visible value.
 */
class KitWindowRowGateTest {
    private val windows: @Composable () -> Unit = {
        Column(Modifier.padding(vertical = 16.dp)) {
            KitWindowRow("5 hours", .72f, "72% left · 14:10", "72% left · 14:10")
            KitWindowRow("Week", .18f, "18% left · Thu", "18% left · Thu", note = "Resets at Wed 1:59 AM, in 14 h")
            KitWindowRow("Month", 0f, "Used up · Sep 1", "Used up · Sep 1")
            KitWindowRow("Last read", .4f, "40% left · 2 days ago", "40% left · 2 days ago")
            KitWindowRow(
                "5 hours", .12f, "12% · 1:25 PM", "12% · 1:25 PM",
                modifier = Modifier.padding(horizontal = 16.dp), inline = true,
            )
        }
    }

    private val arabic: @Composable () -> Unit = {
        Column(Modifier.padding(vertical = 16.dp)) {
            KitWindowRow("٥ ساعات", .72f, "متبقي 72% · 14:10", "متبقي 72% · 14:10")
            KitWindowRow("الأسبوع", .18f, "متبقي 18% · الخميس", "متبقي 18% · الخميس", note = "يُعاد الضبط الأربعاء 1:59 ص")
        }
    }

    @Test fun kit_window_row_default() { FlutterGoldenGate.check("kit_window_row_default", content = windows) }
    @Test fun kit_window_row_default_1280x800() { FlutterGoldenGate.check("kit_window_row_default_1280x800", content = windows) }
    @Test fun kit_window_row_default_text2() { FlutterGoldenGate.check("kit_window_row_default_text2", modes = listOf("dark"), content = windows) }
    @Test fun kit_window_row_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_window_row_default_text2_1280x800", modes = listOf("dark"), content = windows)
    }
    @Test fun kit_window_row_default_ar() { FlutterGoldenGate.check("kit_window_row_default_ar", modes = listOf("dark"), content = arabic) }
    @Test fun kit_window_row_default_ar_1280x800() {
        FlutterGoldenGate.check("kit_window_row_default_ar_1280x800", modes = listOf("dark"), content = arabic)
    }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_window_row", caps = mapOf("KitWindowRow" to "list"), content = windows) }
}
