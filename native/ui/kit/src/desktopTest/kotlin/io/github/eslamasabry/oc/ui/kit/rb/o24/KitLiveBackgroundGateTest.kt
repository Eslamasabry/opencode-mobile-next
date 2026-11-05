package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_live_background_golden_test.dart. */
class KitLiveBackgroundGateTest {
    private val part: @Composable () -> Unit = {
        KitLiveBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitText("Chats", role = TypeRole.Headline) }
        }
    }

    @Test fun default_state() {
        FlutterGoldenGate.check("kit_live_background_default", content = part)
        FlutterGoldenGate.check("kit_live_background_default_1280x800", content = part)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_live_background", caps = mapOf("KitLiveBackground" to "reading"), content = part)
    }
}
