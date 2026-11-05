package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.teaching.KitTipBubble
import kotlin.test.Test

/** The default scene of test/goldens/kit/kit_tip_golden_test.dart: "Hold to stop after this step" over a stop button. */
class KitTipGateTest {
    // The gate captures one root, so the bubble is drawn inline above the anchor, as KitTip's popup places it.
    private val scene: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().padding(top = 320.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Box(Modifier.align(Alignment.CenterEnd)) {
                KitIconButton(KitIcons.stopCircle, "Stop", onClick = {})
                KitTipBubble(
                    "Hold to stop after this step",
                    Modifier.align(Alignment.TopEnd).layout { m, c ->
                        val p = m.measure(Constraints())
                        layout(p.width, 0) { p.place(0, -p.height - 8.dp.roundToPx()) }
                    },
                )
            }
        }
    }

    @Test fun kit_tip_default() { FlutterGoldenGate.check("kit_tip_default", content = scene) }
    @Test fun kit_tip_default_1280x800() { FlutterGoldenGate.check("kit_tip_default_1280x800", content = scene) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_tip", caps = mapOf("KitTip" to "popover"), content = scene) }
}
