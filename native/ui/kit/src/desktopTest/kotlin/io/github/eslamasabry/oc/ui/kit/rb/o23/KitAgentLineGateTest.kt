package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentLine
import kotlin.test.Test

/**
 * test/goldens/kit/kit_agent_line_golden_test.dart `_part`: KitAgentLine('Claude Code', 'on this phone') in 16 padding;
 * 412x915 and 1280x800, light and dark; 2.0 text dark only.
 */
class KitAgentLineGateTest {
    private val part: @Composable () -> Unit = {
        KitAgentLine(
            agent = "Claude Code",
            place = "on this phone",
            onClick = {},
            semanticsLabel = "Claude Code on this phone. Change the agent or project.",
            modifier = Modifier.padding(16.dp),
        )
    }

    @Test fun kit_agent_line_default() { FlutterGoldenGate.check("kit_agent_line_default", content = part) }
    @Test fun kit_agent_line_default_1280x800() { FlutterGoldenGate.check("kit_agent_line_default_1280x800", content = part) }
    @Test fun kit_agent_line_default_text2() { FlutterGoldenGate.check("kit_agent_line_default_text2", modes = listOf("dark"), content = part) }
    @Test fun kit_agent_line_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_agent_line_default_text2_1280x800", modes = listOf("dark"), content = part)
    }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_agent_line", caps = mapOf("KitAgentLine" to "conversation"), content = part) }
}
