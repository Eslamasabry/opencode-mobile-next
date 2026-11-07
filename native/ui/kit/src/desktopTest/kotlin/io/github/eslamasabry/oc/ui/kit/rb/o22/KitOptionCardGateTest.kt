package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitPlainOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatchGrid
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/**
 * The scenes of test/goldens/kit/kit_option_card_golden_test.dart: `_cards()` (two theme swatches, two glow cards,
 * three surface cards, the Accent row and grid, one plain card) and `_disabled()` (a theme swatch with no action).
 *
 * Approximations, the native kit having no counterpart:
 * - `KitOptionCard.glow` (Classic, Soft ring): no native part, the row is left out.
 * - `KitAccentRow` / `KitAccentRow.grid`: no native part; drawn with the accent [KitSwatchGrid], its null-colour swatch
 *   standing in for "Match theme".
 * - `KitThemeOptionCard` has no disabled input or reason, so `_disabled()` draws an enabled, unselected swatch.
 */
class KitOptionCardGateTest {
    private val cards: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                KitThemeOptionCard(PackId.Nord, "Nord", selected = true, onSelect = {}, modifier = Modifier.size(130.dp, THEME_HEIGHT.dp))
                Spacer(Modifier.width(12.dp))
                KitThemeOptionCard(PackId.Dracula, "Dracula", selected = false, onSelect = {}, modifier = Modifier.size(130.dp, THEME_HEIGHT.dp))
            }
            Spacer(Modifier.height(12.dp))
            // KitOptionCard.glow Classic / Soft ring: no native part.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((style, label) in listOf(Style.Solid to "Solid", Style.Soft to "Soft", Style.Brutal to "Brutal")) {
                    KitStyleOptionCard(style, label, selected = style == Style.Soft, onSelect = {}, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            KitSwatchGrid("Accent", accents(listOf(TEAL, BLUE, PINK), "blue", "Match theme"), "In use")
            Spacer(Modifier.height(12.dp))
            KitSwatchGrid(
                "Accent",
                accents(listOf(MINT, TEAL, BLUE, VIOLET, PINK, MONO, SILVER), "teal", "Match"),
                "In use",
                Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            KitPlainOptionCard(
                label = "Calm",
                caption = "Drawings appear, nothing keeps moving and nothing celebrates",
                icon = KitIcons.waveform,
                selected = true,
                onSelect = {},
            )
        }
    }

    /** `_disabled()`: the Scaffold body of a kitGalleryShot, so the frame's 16 dp top stands for the padding's. */
    private val disabled: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            KitThemeOptionCard(PackId.Nord, "Material You", selected = false, onSelect = {}, modifier = Modifier.size(150.dp, THEME_HEIGHT.dp))
        }
    }

    @Test fun kit_option_card_default() { FlutterGoldenGate.check("kit_option_card_default", content = cards) }

    @Test fun kit_option_card_default_1280x800() { FlutterGoldenGate.check("kit_option_card_default_1280x800", content = cards) }

    @Test fun kit_option_card_default_text2() {
        FlutterGoldenGate.check("kit_option_card_default_text2", modes = listOf("dark"), content = cards)
    }

    @Test fun kit_option_card_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_option_card_default_text2_1280x800", modes = listOf("dark"), content = cards)
    }

    @Test fun kit_option_card_disabled() { FlutterGoldenGate.check("kit_option_card_disabled", content = disabled) }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_option_card",
            caps = mapOf("KitAccentRow" to "reading", "KitOptionCard" to "reading"),
            content = cards,
        )
    }

    private companion object {
        const val THEME_HEIGHT = 168 // F: lib/ui/kit/kit_option_card.dart KitOptionCard.themeHeight
        val MINT = Triple("mint", Color(0xFF3DDC97), "Mint")
        val TEAL = Triple("teal", Color(0xFF3CCFCF), "Teal")
        val BLUE = Triple("blue", Color(0xFF5AB0FF), "Blue")
        val VIOLET = Triple("violet", Color(0xFFB79CFF), "Violet")
        val PINK = Triple("pink", Color(0xFFF2A7E6), "Pink")
        val MONO = Triple("mono", Color(0xFFF2F2F2), "Mono")
        val SILVER = Triple("silver", Color(0xFFB8BDC6), "Silver")

        /** "Match theme" (a null colour) first, then the swatches; [chosen] is the selected id. */
        fun accents(swatches: List<Triple<String, Color, String>>, chosen: String, match: String): List<KitSwatch> =
            listOf(KitSwatch("", match, null, selected = false, onPressed = {})) +
                swatches.map { (id, color, label) -> KitSwatch(id, label, color, selected = id == chosen, onPressed = {}) }
    }
}
