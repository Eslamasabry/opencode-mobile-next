package io.github.eslamasabry.oc.ui.kit.rb.o13

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitArrival
import io.github.eslamasabry.oc.ui.kit.motion.KitArrivalScope
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import kotlin.test.Test

/**
 * test/goldens/kit/kit_arrival_golden_test.dart: Settings › Appearance › Effects opened by the search result
 * "vibration", the Vibration row arrived at and washed (the shot is taken while the wash holds).
 */
class KitArrivalGateTest {
    private val effects: @Composable () -> Unit = {
        @Composable
        fun row(id: String, icon: ImageVector, title: String, detail: String) {
            KitArrival(id) {
                KitSwitchRow(
                    title = title,
                    checked = true,
                    supporting = detail,
                    leading = KitRowLeading.Glyph(icon),
                    supportingMaxLines = 4,
                    onCheckedChange = {},
                )
            }
        }
        KitArrivalScope("effects-vibration") {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                    // ListView children take the full width (a tight constraint), so the capped group centres.
                    KitRowGroup(Modifier.fillMaxWidth(), label = "Effects") {
                        row("effects-glass", KitIcons.layers, "Glass", "Bars and sheets float over what is behind them")
                        row(
                            "effects-celebrations",
                            KitIcons.sparkle,
                            "Celebrations",
                            "A short moment when setup finishes or a task merges",
                        )
                        row("effects-vibration", KitIcons.touch, "Vibration", "A light tap when you send, and when a run finishes")
                    }
                }
            }
        }
    }

    private fun shot(name: String) = FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, content = effects)

    @Test fun kit_arrival_marked() { shot("kit_arrival_marked") }

    @Test fun kit_arrival_marked_text2() { shot("kit_arrival_marked_text2") }

    @Test fun kit_arrival_marked_1280x800() { shot("kit_arrival_marked_1280x800") }

    @Test fun kit_arrival_marked_text2_1280x800() { shot("kit_arrival_marked_text2_1280x800") }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_arrival",
            caps = mapOf("KitArrival" to "wrapper", "KitArrivalScope" to "scope"),
            frame = FlutterGoldenGate.Frame.Screen,
            content = effects,
        )
    }
}
