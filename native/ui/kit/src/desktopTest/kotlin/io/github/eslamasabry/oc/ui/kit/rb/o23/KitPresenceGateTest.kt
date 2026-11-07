package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import kotlin.test.Test

/** test/goldens/kit/kit_presence_golden_test.dart: `shown`, a presence in a row holding a long line of text. */
class KitPresenceGateTest {
    @Composable private fun shown() {
        Row(Modifier.fillMaxWidth()) { KitPresence(visible = true, modifier = Modifier.weight(1f, fill = false)) { KitText("Find the latest finance hub project in the folder") } }
    }
    @Test fun kit_presence_shown() { gate23("kit_presence_shown") { shown() } }
    @Test fun kit_presence_shown_1280x800() { gate23("kit_presence_shown_1280x800") { shown() } }
    @Test fun kit_presence_shown_text2() { gate23("kit_presence_shown_text2") { shown() } }
    @Test fun kit_presence_shown_text2_1280x800() { gate23("kit_presence_shown_text2_1280x800") { shown() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_presence", caps = mapOf("KitPresence" to "wrapper")) { shown() } }
}
