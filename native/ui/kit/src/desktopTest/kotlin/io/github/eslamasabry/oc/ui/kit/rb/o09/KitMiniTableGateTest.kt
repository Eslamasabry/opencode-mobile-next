package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.state.KitMiniTable
import io.github.eslamasabry.oc.ui.kit.state.KitMiniTableRow
import kotlin.test.Test

/** test/goldens/kit/kit_mini_table_golden_test.dart `_states['default']`: four files, 16 dp at the sides. */
class KitMiniTableGateTest {
    private val table: @androidx.compose.runtime.Composable () -> Unit = {
        Box(Modifier.padding(horizontal = 16.dp)) {
            KitMiniTable(
                columns = listOf("File", "Added", "Removed", "Status"),
                rows = listOf(
                    KitMiniTableRow(listOf("lib/main.dart", "12", "3", "Changed")),
                    KitMiniTableRow(listOf("lib/ui/kit/kit.dart", "5", "0", "Changed")),
                    KitMiniTableRow(listOf("test/kit/kit_chart_test.dart", "140", "0", "New")),
                    KitMiniTableRow(listOf("docs/old-plan.md", "0", "86", "Removed")),
                ),
            )
        }
    }

    @Test fun kit_mini_table_default() { FlutterGoldenGate.check("kit_mini_table_default", wrap = { FlutterCapture(it) }, content = table) }
    @Test fun kit_mini_table_default_1280x800() { FlutterGoldenGate.check("kit_mini_table_default_1280x800", wrap = { FlutterCapture(it) }, content = table) }
    @Test fun kit_mini_table_default_text2() {
        FlutterGoldenGate.check("kit_mini_table_default_text2", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = table)
    }
    @Test fun kit_mini_table_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_mini_table_default_text2_1280x800", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = table)
    }
    @Test fun kit_mini_table_default_ar() {
        FlutterGoldenGate.check("kit_mini_table_default_ar", modes = listOf("dark"), wrap = { FlutterCapture(it) }, content = table)
    }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_mini_table", caps = mapOf("KitMiniTable" to "list"), wrap = { FlutterCapture(it) }, content = table) }
}
