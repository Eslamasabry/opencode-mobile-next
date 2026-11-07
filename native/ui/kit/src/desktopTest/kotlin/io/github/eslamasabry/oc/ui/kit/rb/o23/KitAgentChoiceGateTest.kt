package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentChoice
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentChoiceState
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentGrid
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import java.io.File
import kotlin.test.Test

/** The six personas of AgentPersona.all (lib/domain/agent_persona.dart): name, strength (app_en.arb) and mark slug. */
internal val PERSONAS =
    listOf(
        Triple("Claude Code", "Careful with big changes", "claude"),
        Triple("Codex", "Fast edits and reviews", "codex"),
        Triple("Gemini", "Reads a lot at once", "gemini"),
        Triple("Kilo", "Your choice of provider", "kilocode"),
        Triple("Cline", "Step by step, asks first", "cline"),
        Triple("OpenCode", "Any model, free ones too", "opencode"),
    )

/** The app's marks (`androidApp/src/main/assets/marks/<slug>.svg`, the same files as Flutter's `assets/marks/`). */
internal val MARKS = KitLogoLoader { id, _ ->
    File(System.getProperty("user.dir"), "../../androidApp/src/main/assets/marks/$id.svg").takeIf { it.isFile }?.readText()
}

/**
 * The first [count] personas as cards in a grid in 16 padding: the first chosen, the next two available, the rest need
 * setup (kit_agent_choice_golden_test.dart `_part`, all six; kit_agent_grid_golden_test.dart, the first five).
 */
@Composable
internal fun AgentCards(count: Int) {
    CompositionLocalProvider(LocalKitLogoLoader provides MARKS) {
        KitAgentGrid(
            PERSONAS.take(count).mapIndexed { i, (name, strength, slug) ->
                @Composable {
                    KitAgentChoice(
                        name,
                        strength,
                        slug,
                        when {
                            i == 0 -> KitAgentChoiceState.Selected
                            i < 3 -> KitAgentChoiceState.Available
                            else -> KitAgentChoiceState.NeedsSetup
                        },
                        onClick = {},
                    )
                }
            },
            Modifier.padding(16.dp),
        )
    }
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
