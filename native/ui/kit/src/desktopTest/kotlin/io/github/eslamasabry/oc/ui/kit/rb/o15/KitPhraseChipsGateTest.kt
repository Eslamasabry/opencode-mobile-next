package io.github.eslamasabry.oc.ui.kit.rb.o15

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import kotlin.test.Test

/** The default scenes of test/goldens/kit/kit_phrase_chips_golden_test.dart: three phrases. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitPhraseChipsGateTest {
    private val chips: @Composable () -> Unit = {
        Box {
            KitPhraseChips(
                phrases = listOf("Continue", "Run the tests", "Commit this"),
                label = "Quick phrases",
                insertLabel = { "Send: $it" },
                menuLabel = { "Phrase options" },
                editLabel = "Edit",
                hideLabel = "Hide",
                onChoose = {},
                onEdit = {},
                onHide = {},
            )
        }
    }

    @Test fun kit_phrase_chips_default() { FlutterGoldenGate.check("kit_phrase_chips_default", content = chips) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_phrase_chips", caps = mapOf("KitPhraseChips" to "conversation"), content = chips) }
}
