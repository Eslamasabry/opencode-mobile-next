package io.github.eslamasabry.oc.ui.kit.rb.o01

import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.style.KitRunningFeedLine
import kotlin.test.Test

/**
 * The Signal goldens (kit_signal_home, kit_signal_chat) are whole screens built from parts other orders own (rows,
 * request cards, step timelines, the feed); only the part this order owns is rendered here, in every style and scale.
 */
class KitSignalGateTest {
    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_signal") {
            KitRunningFeedLine(sinceMs = 180_000, nowMs = 180_000, command = "flutter test", runningLabel = "Running", minutesLabel = { "$it min" })
        }
    }
}
