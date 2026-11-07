package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitAgentGrid
import io.github.eslamasabry.oc.ui.kit.choice.KitAgentGridItem
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/** The six personas of AgentPersona.all, in the picker's order (lib/ui/agents/agent_persona_text.dart `name`). */
internal val PERSONA_NAMES = listOf("Claude Code", "Codex", "Gemini", "Kilo", "Cline", "OpenCode")

/**
 * The first [count] personas as cards in a grid, the first one chosen: the `_part` of kit_agent_choice_golden_test.dart
 * (all six) and kit_agent_grid_golden_test.dart (the first five).
 *
 * Approximations, as the native card has no such inputs: the brand marks are drawn with the neutral terminal glyph
 * (KitAgentGridItem takes an ImageVector, the marks are SVG assets); the strength line ("Careful with big changes", ...)
 * and the needs-setup look (cards 4..6 dimmed with "Set up") have no native counterpart, so those cards show as available.
 */
@Composable
internal fun AgentCards(count: Int) {
    KitAgentGrid(
        PERSONA_NAMES.take(count).mapIndexed { i, name -> KitAgentGridItem(name, KitIcons.terminal, onClick = {}, selected = i == 0) },
        Modifier.padding(16.dp),
    )
}

/** test/goldens/kit/kit_agent_choice_golden_test.dart: the six persona cards, 412x915 and 1280x800, light and dark; 2.0 text dark only. */
class KitAgentChoiceGateTest {
    private val part: @Composable () -> Unit = { AgentCards(6) }

    @Test fun kit_agent_choice_default() { FlutterGoldenGate.check("kit_agent_choice_default", content = part) }
    @Test fun kit_agent_choice_default_1280x800() { FlutterGoldenGate.check("kit_agent_choice_default_1280x800", content = part) }
    @Test fun kit_agent_choice_default_text2() { FlutterGoldenGate.check("kit_agent_choice_default_text2", modes = listOf("dark"), content = part) }
    @Test fun kit_agent_choice_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_agent_choice_default_text2_1280x800", modes = listOf("dark"), content = part)
    }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_agent_choice", caps = mapOf("KitAgentChoice" to "reading"), content = part) }
}
