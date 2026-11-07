package io.github.eslamasabry.oc.ui.kitchat.rb.o15

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kitchat.tool.kitWorkIcon
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import kotlin.test.Test

/**
 * The default (done, folded) look of test/goldens/kit/kit_work_line_golden_test.dart at 2.0 text: a reply's prose,
 * the work line under it, and the reply's next line, 16 dp from the window's sides, in the 412x915 window.
 */
@OptIn(ExperimentalTestApi::class)
class KitWorkLineGateTest {
    @Test fun default_text2() {
        FlutterGoldenGate.check(
            "kit_work_line_default_text2",
            frame = FlutterGoldenGate.Frame.Screen,
            textScale = 2f,
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) { // F: kit_work_line_golden_test.dart:35
                KitText("I will look at how the app starts, then fix the failing test.")
                Spacer(Modifier.height(8.dp))
                KitWorkLine("Read 3 files · edited 1 file · ran 2 commands", null, KitWorkState.Done, expanded = false, onToggle = {}, icon = kitWorkIcon(read = 3, edited = 1, ran = 2)) {}
                Spacer(Modifier.height(8.dp))
                KitText("The test passes now; the change is one line.")
            }
        }
    }
}
