package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCardItem
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceCards
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/** test/goldens/kit/kit_choice_cards_golden_test.dart `_cards`: where the agent runs, phone or computer. */
@Composable
private fun cards(busy: String? = null, disabled: Boolean = false) {
    Box(Modifier.padding(16.dp)) {
        KitChoiceCards(
            choices = listOf(
                KitChoiceCardItem(
                    "phone",
                    "On this phone",
                    supporting = "About 4 minutes · 208 MB download",
                    icon = KitIcons.phone,
                    enabled = if (disabled) KitEnabled.Disabled("Not on this device") else KitEnabled.Enabled,
                ),
                KitChoiceCardItem(
                    "computer",
                    "On my computer",
                    supporting = "Pair with a code, or type its address",
                    icon = KitIcons.computer,
                ),
            ),
            selected = if (disabled) "computer" else "phone",
            onSelected = {},
            busy = busy,
            semanticsLabel = "Where should your agent run?",
        )
    }
}

class KitChoiceCardsGateTest {
    @Test fun kit_choice_cards_default() { FlutterGoldenGate.check("kit_choice_cards_default") { cards() } }
    @Test fun kit_choice_cards_default_1280x800() { FlutterGoldenGate.check("kit_choice_cards_default_1280x800") { cards() } }
    @Test fun kit_choice_cards_default_text2() { FlutterGoldenGate.check("kit_choice_cards_default_text2") { cards() } }
    @Test fun kit_choice_cards_default_text2_1280x800() { FlutterGoldenGate.check("kit_choice_cards_default_text2_1280x800") { cards() } }
    @Test fun kit_choice_cards_working() { FlutterGoldenGate.check("kit_choice_cards_working") { cards(busy = "phone") } }
    @Test fun kit_choice_cards_disabled() { FlutterGoldenGate.check("kit_choice_cards_disabled") { cards(disabled = true) } }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_choice_cards", caps = mapOf("KitChoiceCards" to "reading")) { cards() }
    }
}
