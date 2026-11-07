package io.github.eslamasabry.oc.ui.kit.rb.o24

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTab
import io.github.eslamasabry.oc.ui.kit.nav.KitSessionTabs
import kotlin.test.Test

/**
 * The default scene of test/goldens/kit/kit_session_tabs_golden_test.dart (`_part`): "Open terminals" with
 * build-server chosen, Shell 2 resting and tests ended ("ended"), 16 dp above and below.
 */
class KitSessionTabsGateTest {
    private val scene: @Composable () -> Unit = {
        Box(Modifier.padding(vertical = 16.dp)) {
            KitSessionTabs(
                tabs = listOf(
                    KitSessionTab("build-server", selected = true, onClick = {}),
                    KitSessionTab("Shell 2", selected = false, onClick = {}),
                    KitSessionTab("tests", selected = false, onClick = {}, ended = true),
                ),
                label = "Open terminals",
                endedLabel = "ended",
            )
        }
    }

    @Test fun kit_session_tabs_default() { FlutterGoldenGate.check("kit_session_tabs_default", content = scene) }
    @Test fun kit_session_tabs_default_1280x800() { FlutterGoldenGate.check("kit_session_tabs_default_1280x800", content = scene) }
    @Test fun kit_session_tabs_default_text2() { FlutterGoldenGate.check("kit_session_tabs_default_text2", modes = listOf("dark"), content = scene) }
    @Test fun kit_session_tabs_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_session_tabs_default_text2_1280x800", modes = listOf("dark"), content = scene)
    }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_session_tabs", caps = mapOf("KitSessionTabs" to "fill"), content = scene) }
}
