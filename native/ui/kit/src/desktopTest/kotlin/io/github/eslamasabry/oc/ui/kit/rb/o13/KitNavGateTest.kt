package io.github.eslamasabry.oc.ui.kit.rb.o13

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import kotlin.test.Test

/** The phone scene of test/goldens/kit/kit_nav_golden_test.dart: KitNav on a phone is the content alone, no bar. */
class KitNavGateTest {
    // A phone draws no navigation (kit_nav.dart KitNavLayout.none): twelve body rows, gutter inset, space3 between.
    private val phoneNoBar: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(FlutterKitTokens.GUTTER.dp)) {
            for (i in 1..12) {
                KitText("Row $i: the quick brown fox jumps over.", Modifier.padding(vertical = FlutterKitTokens.SPACE3.dp))
            }
        }
    }

    @Test fun kit_nav_phone_no_bar() {
        FlutterGoldenGate.check("kit_nav_phone_no_bar", frame = FlutterGoldenGate.Frame.Screen, content = phoneNoBar)
    }
}
