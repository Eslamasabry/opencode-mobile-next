package io.github.eslamasabry.oc.ui.kit.rb.o10

import androidx.compose.foundation.layout.Column
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.row.KitRisk
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLineFold
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import io.github.eslamasabry.oc.ui.kit.status.KitStatusSlot
import kotlin.test.Test

/**
 * part-kit_status_slot has no Flutter golden: its matrix renders a scope with an update condition and a slot whose
 * risky switch, turned on below it, contributes the line that wins.
 */
class KitStatusSlotGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_status_slot",
            caps = mapOf("KitStatusContribution" to "scope", "KitStatusLineSlot" to "fill", "KitStatusScope" to "scope"),
            frame = FlutterGoldenGate.Frame.Screen,
        ) {
            KitStatusScope(listOf(KitStatus(KitStatusKind.Update, "Update ready"))) {
                KitStatusSlot(status = null) { shown ->
                    Column {
                        KitStatusLineFold(shown)
                        KitSwitchRow("Approve everything", checked = true, riskSpec = KitRisk("Every conversation on this server", "Auto-approve is on")) {}
                    }
                }
            }
        }
    }
}
