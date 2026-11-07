package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import kotlin.test.Test

/**
 * test/goldens/kit/kit_presence_golden_test.dart `shown`: a Row holding a Flexible KitPresence(show: true) over
 * KitText('Find the latest finance hub project in the folder'); 412x915 and 1280x800, light and dark, also at 2.0 text.
 */
class KitPresenceGateTest {
    private val shown: @Composable () -> Unit = {
        Row {
            KitPresence(visible = true, modifier = Modifier.weight(1f, fill = false)) { KitText("Find the latest finance hub project in the folder") }
        }
    }

    @Test fun kit_presence_shown() { FlutterGoldenGate.check("kit_presence_shown", content = shown) }
    @Test fun kit_presence_shown_1280x800() { FlutterGoldenGate.check("kit_presence_shown_1280x800", content = shown) }
    @Test fun kit_presence_shown_text2() { FlutterGoldenGate.check("kit_presence_shown_text2", content = shown) }
    @Test fun kit_presence_shown_text2_1280x800() { FlutterGoldenGate.check("kit_presence_shown_text2_1280x800", content = shown) }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_presence", caps = mapOf("KitPresence" to "wrapper"), content = shown) }
}
