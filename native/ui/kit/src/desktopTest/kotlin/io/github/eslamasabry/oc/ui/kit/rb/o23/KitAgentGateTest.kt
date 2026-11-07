package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentChoice
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentChoiceState
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentGrid
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentLine
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentMark
import kotlin.test.Test

/** AgentPersona.all at the Flutter ref with its English name and strength (lib/ui/agents/agent_persona_text.dart). */
private val personas = listOf(
    Triple("Claude Code", "Careful with big changes", KitAgentMark.Claude),
    Triple("Codex", "Fast edits and reviews", KitAgentMark.Codex),
    Triple("Gemini", "Reads a lot at once", KitAgentMark.Gemini),
    Triple("Kilo", "Your choice of provider", KitAgentMark.Kilo),
    Triple("Cline", "Step by step, asks first", KitAgentMark.Cline),
    Triple("OpenCode", "Any model, free ones too", KitAgentMark.OpenCode),
)

/** kit_agent_choice / kit_agent_grid golden tests: the first card chosen, the next two available, the rest need setup. */
@Composable
private fun grid(count: Int) {
    Box(Modifier.padding(16.dp)) {
        KitAgentGrid(
            personas.take(count).mapIndexed { i, (name, line, mark) ->
                @Composable {
                    val state = when {
                        i == 0 -> KitAgentChoiceState.Selected
                        i < 3 -> KitAgentChoiceState.Available
                        else -> KitAgentChoiceState.NeedsSetup
                    }
                    KitAgentChoice(name, line, state, {}, mark = mark)
                }
            },
        )
    }
}

class KitAgentChoiceGateTest {
    @Test fun kit_agent_choice_default() { gate23("kit_agent_choice_default") { grid(6) } }
    @Test fun kit_agent_choice_default_1280x800() { gate23("kit_agent_choice_default_1280x800") { grid(6) } }
    @Test fun kit_agent_choice_default_text2() { gate23("kit_agent_choice_default_text2", "dark") { grid(6) } }
    @Test fun kit_agent_choice_default_text2_1280x800() { gate23("kit_agent_choice_default_text2_1280x800", "dark") { grid(6) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_agent_choice", caps = mapOf("KitAgentChoice" to "reading")) { grid(6) } }
}

class KitAgentGridGateTest {
    @Test fun kit_agent_grid_default() { gate23("kit_agent_grid_default") { grid(5) } }
    @Test fun kit_agent_grid_default_1280x800() { gate23("kit_agent_grid_default_1280x800") { grid(5) } }
    @Test fun kit_agent_grid_default_text2() { gate23("kit_agent_grid_default_text2", "dark") { grid(5) } }
    @Test fun kit_agent_grid_default_text2_1280x800() { gate23("kit_agent_grid_default_text2_1280x800", "dark") { grid(5) } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_agent_grid", caps = mapOf("KitAgentGrid" to "reading")) { grid(5) } }
}

class KitAgentLineGateTest {
    @Composable private fun line() {
        KitAgentLine("Claude Code", "on this phone", {}, "Claude Code on this phone. Change the agent or project.", Modifier.padding(16.dp))
    }
    @Test fun kit_agent_line_default() { gate23("kit_agent_line_default") { line() } }
    @Test fun kit_agent_line_default_1280x800() { gate23("kit_agent_line_default_1280x800") { line() } }
    @Test fun kit_agent_line_default_text2() { gate23("kit_agent_line_default_text2", "dark") { line() } }
    @Test fun kit_agent_line_default_text2_1280x800() { gate23("kit_agent_line_default_text2_1280x800", "dark") { line() } }
    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_agent_line", caps = mapOf("KitAgentLine" to "conversation")) { line() } }
}
