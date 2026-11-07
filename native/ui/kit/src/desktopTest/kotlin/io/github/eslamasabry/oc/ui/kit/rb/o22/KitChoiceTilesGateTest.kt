package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMore
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceTiles
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import kotlin.test.Test

private val tileChoices = listOf(
    KitChoice("sonnet", "Default", supporting = "Claude Sonnet 5.5"),
    KitChoice("sol", "Recently used", supporting = "GPT-6 Sol"),
    KitChoice("pickle", "Cheapest", supporting = "Big Pickle · Free"),
)

/** test/goldens/kit/kit_choice_tiles_golden_test.dart `_tiles`: three model choices and "All models (9)". */
@Composable
private fun tiles(busy: String? = null, disabled: Boolean = false) {
    Box(Modifier.padding(16.dp)) {
        KitChoiceTiles(
            choices = if (disabled) tileChoices.map { it.copy(enabled = KitEnabled.Disabled("Saving the choice…")) } else tileChoices,
            selected = "sonnet",
            onSelected = {},
            busy = busy,
            more = KitChoiceMore("All models (9)") {},
            semanticsLabel = "Model",
        )
    }
}

class KitChoiceTilesGateTest {
    @Test fun kit_choice_tiles_default() { FlutterGoldenGate.check("kit_choice_tiles_default") { tiles() } }
    @Test fun kit_choice_tiles_default_1280x800() { FlutterGoldenGate.check("kit_choice_tiles_default_1280x800") { tiles() } }
    @Test fun kit_choice_tiles_default_text2() { FlutterGoldenGate.check("kit_choice_tiles_default_text2") { tiles() } }
    @Test fun kit_choice_tiles_default_text2_1280x800() { FlutterGoldenGate.check("kit_choice_tiles_default_text2_1280x800") { tiles() } }
    @Test fun kit_choice_tiles_working() { FlutterGoldenGate.check("kit_choice_tiles_working") { tiles(busy = "pickle") } }
    @Test fun kit_choice_tiles_disabled() { FlutterGoldenGate.check("kit_choice_tiles_disabled") { tiles(disabled = true) } }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_choice_tiles", caps = mapOf("KitChoiceTiles" to "reading")) { tiles() }
    }
}
