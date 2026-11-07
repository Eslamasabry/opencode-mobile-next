package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlin.test.Test

/**
 * test/goldens/kit/kit_agent_grid_golden_test.dart: the first five persona cards (the odd fifth spans the row, sideways),
 * 412x915 and 1280x800, light and dark; 2.0 text dark only. Approximations as in [AgentCards].
 */
class KitAgentGridGateTest {
    private val part: @Composable () -> Unit = { AgentCards(5) }

    @Test fun kit_agent_grid_default() { FlutterGoldenGate.check("kit_agent_grid_default", content = part) }
    @Test fun kit_agent_grid_default_1280x800() { FlutterGoldenGate.check("kit_agent_grid_default_1280x800", content = part) }
    @Test fun kit_agent_grid_default_text2() { FlutterGoldenGate.check("kit_agent_grid_default_text2", modes = listOf("dark"), content = part) }
    @Test fun kit_agent_grid_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_agent_grid_default_text2_1280x800", modes = listOf("dark"), content = part)
    }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_agent_grid", caps = mapOf("KitAgentGrid" to "reading"), content = part) }
}
