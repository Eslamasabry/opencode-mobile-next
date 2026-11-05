package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.frame.kitScrollFade
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_scroll_fade_golden_test.dart. */
class KitScrollFadeGateTest {
    private val default: @Composable () -> Unit = {
        val state = rememberScrollState()
        Row(Modifier.width(240.dp).kitScrollFade(state).horizontalScroll(state)) {
            KitChip("Ask first")
            Spacer(Modifier.width(8.dp))
            KitChip("Auto Accept")
            Spacer(Modifier.width(8.dp))
            KitChip("Background")
        }
    }

    @Test fun scenes() {
        FlutterGoldenGate.check("kit_scroll_fade_default", content = default)
    }

    @Test fun sizes() {
        FlutterGoldenGate.check("kit_scroll_fade_default_1280x800", content = default)
    }

    @Test fun scaled() {
        for (s in listOf("", "_1280x800")) {
            FlutterGoldenGate.check("kit_scroll_fade_default_text2$s", modes = listOf("dark"), textScale = 2f, content = default)
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_scroll_fade", caps = mapOf("KitScrollFade" to "wrapper")) { default() }
    }
}
