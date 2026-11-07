package io.github.eslamasabry.oc.ui.kit.rb.o10

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import kotlin.test.Test

/** part-kit_row_group_seam has no Flutter golden: its matrix renders two groups, the second set right under the first. */
class KitRowGroupSeamGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_row_group_seam", frame = FlutterGoldenGate.Frame.Screen) {
            Column(Modifier.padding(16.dp)) {
                KitRowGroup { KitRow("Formatter", onClick = {}); KitRow("Linter", onClick = {}) }
                KitRowGroup(afterGroup = true) { KitRow("Git", onClick = {}) }
            }
        }
    }
}
