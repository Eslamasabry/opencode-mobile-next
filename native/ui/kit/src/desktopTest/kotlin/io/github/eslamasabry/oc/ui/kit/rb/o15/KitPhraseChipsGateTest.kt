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
    private fun chips(phrases: List<String> = listOf("Continue", "Run the tests", "Commit this")): @Composable () -> Unit = {
        Box {
            KitPhraseChips(
                phrases = phrases,
                label = "Quick phrases",
                insertLabel = { "Send: $it" },
                menuLabel = { "Phrase options" },
                editLabel = "Edit",
                hideLabel = "Hide",
                onChoose = {},
                onEdit = {},
                onHide = {},
                bleed = false, // Flutter's part keeps the screen gutter (KitFilterChips)
            )
        }
    }

    @Test fun kit_phrase_chips_default() { FlutterGoldenGate.check("kit_phrase_chips_default", content = chips()) }
    @Test fun kit_phrase_chips_default_1280x800() { FlutterGoldenGate.check("kit_phrase_chips_default_1280x800", content = chips()) }
    // Flutter renders the 2.0 text shots in dark only.
    @Test fun kit_phrase_chips_default_text2() { FlutterGoldenGate.check("kit_phrase_chips_default_text2", modes = listOf("dark"), content = chips()) }
    @Test fun kit_phrase_chips_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_phrase_chips_default_text2_1280x800", modes = listOf("dark"), content = chips())
    }
    /** No phrases draws nothing. */
    @Test fun kit_phrase_chips_empty() { FlutterGoldenGate.check("kit_phrase_chips_empty", content = chips(emptyList())) }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_phrase_chips", caps = mapOf("KitPhraseChips" to "conversation"), content = chips()) }
}
