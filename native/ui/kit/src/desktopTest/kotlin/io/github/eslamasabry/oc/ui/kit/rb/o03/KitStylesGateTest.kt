package io.github.eslamasabry.oc.ui.kit.rb.o03

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.style.KitSignalNeedsYou
import io.github.eslamasabry.oc.ui.kit.style.KitSignalSlip
import kotlin.test.Test

/** Order 03's visual parts have no Flutter goldens: the matrix renders every Style × mode × scale × direction. */
class KitStylesGateTest {
    @Test fun pageTransitions() {
        FlutterGoldenGate.matrix("part-kit_page_transitions") {
            KitGrowPage(growKey = null) { KitText("Page", role = TypeRole.RowTitle) }
        }
    }

    @Test fun refresh() {
        FlutterGoldenGate.matrix("part-kit_refresh", caps = mapOf("KitRefresh" to "wrapper")) {
            KitRefresh(refreshing = false, onRefresh = {}) { KitText("Conversations", role = TypeRole.RowTitle) }
        }
    }

    @Test fun reveal() {
        FlutterGoldenGate.matrix("part-kit_reveal", caps = mapOf("KitEntrance" to "wrapper", "KitReveal" to "wrapper")) {
            Column(Modifier.fillMaxWidth()) {
                KitEntrance { KitText("Entrance", role = TypeRole.RowTitle) }
                KitReveal(visible = true) { KitText("Reveal", role = TypeRole.RowTitle) }
            }
        }
    }

    @Test fun signalSlip() {
        FlutterGoldenGate.matrix("part-kit_signal_slip") {
            KitSignalSlip {
                Column(Modifier.fillMaxWidth()) {
                    KitSignalNeedsYou("Needs you")
                    KitText("Allow the command?", role = TypeRole.RowTitle)
                }
            }
        }
    }
}
