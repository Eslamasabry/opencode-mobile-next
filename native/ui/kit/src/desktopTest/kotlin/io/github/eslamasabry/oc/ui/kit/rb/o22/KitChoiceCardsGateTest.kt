package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCard
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCards
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/**
 * test/goldens/kit/kit_choice_cards_golden_test.dart `_cards`: "Where should your agent run?" answered by "On this phone"
 * (About 4 minutes · 208 MB download) and "On my computer" (Pair with a code, or type its address), under a 16 dp pad.
 *
 * The native KitChoiceCard has no busy or enabled input: the working scene (busy 'phone') renders as the default, and
 * the disabled scene puts Dart's joined line "Not on this device · About 4 minutes · 208 MB download" on the phone card
 * as its supporting line with the computer card chosen, without the muted tones or the dropped tap.
 */
class KitChoiceCardsGateTest {
    @Composable
    private fun Scene(disabled: Boolean = false) {
        Box(Modifier.padding(16.dp)) {
            KitChoiceCards {
                KitChoiceCard(
                    title = "On this phone",
                    selected = !disabled,
                    onSelect = {},
                    supporting = if (disabled) "Not on this device · About 4 minutes · 208 MB download" else "About 4 minutes · 208 MB download",
                    icon = KitIcons.phone,
                )
                KitChoiceCard(
                    title = "On my computer",
                    selected = disabled,
                    onSelect = {},
                    supporting = "Pair with a code, or type its address",
                    icon = KitIcons.computer,
                )
            }
        }
    }

    @Test fun kit_choice_cards_default() { FlutterGoldenGate.check("kit_choice_cards_default") { Scene() } }
    @Test fun kit_choice_cards_default_1280x800() { FlutterGoldenGate.check("kit_choice_cards_default_1280x800") { Scene() } }
    @Test fun kit_choice_cards_default_text2() { FlutterGoldenGate.check("kit_choice_cards_default_text2") { Scene() } }
    @Test fun kit_choice_cards_default_text2_1280x800() { FlutterGoldenGate.check("kit_choice_cards_default_text2_1280x800") { Scene() } }

    // Approximation: busy 'phone' (a spinner where the check goes, every card waiting) has no native input.
    @Test fun kit_choice_cards_working() { FlutterGoldenGate.check("kit_choice_cards_working") { Scene() } }
    @Test fun kit_choice_cards_disabled() { FlutterGoldenGate.check("kit_choice_cards_disabled") { Scene(disabled = true) } }

    @Test fun matrix() { FlutterGoldenGate.matrix("part-kit_choice_cards", caps = mapOf("KitChoiceCards" to "reading")) { Scene() } }
}
