package io.github.eslamasabry.oc.ui.kit.rb.o09

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.rb.o06.FlutterCapture
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.kitSliverRowGroup
import kotlin.test.Test

/** test/goldens/kit/kit_sliver_row_group_golden_test.dart `_list`: one head row, then four recent conversations. */
class KitSliverRowGroupGateTest {
    private val recent = listOf(
        "Fix the login redirect loop" to "Needs you · asked 2 min ago",
        "Add dark mode to settings" to "Working · 6 steps so far",
        "Explain the release script" to "Done · 1 h ago",
        "Rename the sync service" to "Done · yesterday",
    )

    private val list: @Composable () -> Unit = {
        // shrinkWrap in the gallery's scroll: a bounded lazy list that wraps its rows.
        LazyColumn(Modifier.heightIn(max = 4000.dp), userScrollEnabled = false) {
            kitSliverRowGroup(
                label = "Recent conversations",
                itemCount = recent.size,
                children = listOf({
                    KitRow(
                        "New conversation", supporting = "In oc_app on the workstation",
                        leading = KitRowLeading.Glyph(KitIcons.chat), trailing = KitRowTrailing.Chevron, onClick = {},
                    )
                }),
            ) { i ->
                KitRow(
                    recent[i].first, supporting = recent[i].second,
                    leading = KitRowLeading.Glyph(KitIcons.agent), trailing = KitRowTrailing.Chevron, onClick = {},
                )
            }
        }
    }

    @Test fun kit_sliver_row_group_default() { FlutterGoldenGate.check("kit_sliver_row_group_default", wrap = { FlutterCapture(it) }, content = list) }
    @Test fun kit_sliver_row_group_default_1280x800() { FlutterGoldenGate.check("kit_sliver_row_group_default_1280x800", wrap = { FlutterCapture(it) }, content = list) }
    @Test fun kit_sliver_row_group_default_text2() { FlutterGoldenGate.check("kit_sliver_row_group_default_text2", wrap = { FlutterCapture(it) }, content = list) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_sliver_row_group", caps = mapOf("KitSliverRowGroup" to "list"), wrap = { FlutterCapture(it) }, content = list) }
}
