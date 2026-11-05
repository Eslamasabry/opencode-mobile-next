package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import kotlin.test.Test

/** The default scene of test/goldens/kit/kit_tip_golden_test.dart: "Hold to stop after this step" over a stop button. */
class KitTipGateTest {
    private val scene: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(883.dp)) {
            KitTip(
                message = "Hold to stop after this step",
                visible = true,
                onDismissed = {},
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 320.dp, end = 8.dp),
            ) {
                KitIconButton(KitIcons.stopCircle, "Stop", onClick = {})
            }
        }
    }

    @Test fun kit_tip_default() { FlutterGoldenGate.check("kit_tip_default", content = scene) }
    @Test fun kit_tip_default_1280x800() { FlutterGoldenGate.check("kit_tip_default_1280x800", content = scene) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_tip", caps = mapOf("KitTip" to "popover"), content = scene) }
}
