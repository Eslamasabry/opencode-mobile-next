package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentRow
import io.github.eslamasabry.oc.ui.kit.choice.KitAccentSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitGlowOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitGlowSampleStyle
import io.github.eslamasabry.oc.ui.kit.choice.KitOptionCardSizes
import io.github.eslamasabry.oc.ui.kit.choice.KitPlainOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test

/** graphiteDark's main colour (theme_roles.dart `graphiteDark`): the "Match theme" tile's dot in the Flutter scene. */
private val graphiteDarkAccent: Color
    get() = Color(ThemeResolver.roles(PackId.Opencode, null, true, false, null).accent.v)

@Composable
private fun ThemeSwatch(pack: PackId, label: String, selected: Boolean = false) {
    Box(Modifier.size(130.dp, KitOptionCardSizes.THEME_HEIGHT.dp)) {
        KitThemeOptionCard(pack, label, selected, onSelect = {}, dark = true)
    }
}

/** test/goldens/kit/kit_option_card_golden_test.dart `_cards()`. */
private val cards: @Composable () -> Unit = {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            ThemeSwatch(PackId.Nord, "Nord", selected = true)
            Spacer(Modifier.width(12.dp))
            ThemeSwatch(PackId.Dracula, "Dracula")
        }
        Spacer(Modifier.height(12.dp))
        Row {
            KitGlowOptionCard(KitGlowSampleStyle.Classic, "Classic", selected = false, onSelect = {}, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            KitGlowOptionCard(KitGlowSampleStyle.SoftRing, "Soft ring", selected = true, onSelect = {}, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((style, label) in listOf(Style.Solid to "Solid", Style.Soft to "Soft", Style.Brutal to "Brutal")) {
                KitStyleOptionCard(style, label, selected = style == Style.Soft, onSelect = {}, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        KitAccentRow(
            semanticsLabel = "Accent",
            matchLabel = "Match theme",
            themeAccent = graphiteDarkAccent,
            swatches = listOf(
                KitAccentSwatch("teal", Color(0xFF3CCFCF), "Teal"),
                KitAccentSwatch("blue", Color(0xFF5AB0FF), "Blue"),
                KitAccentSwatch("pink", Color(0xFFF2A7E6), "Pink"),
            ),
            selected = "blue",
            onChanged = {},
        )
        Spacer(Modifier.height(12.dp))
        // The grid form: every swatch in sight, named, four to a row.
        Box(Modifier.padding(horizontal = 16.dp)) {
            KitAccentRow(
                semanticsLabel = "Accent",
                matchLabel = "Match",
                themeAccent = graphiteDarkAccent,
                swatches = listOf(
                    KitAccentSwatch("mint", Color(0xFF3DDC97), "Mint"),
                    KitAccentSwatch("teal", Color(0xFF3CCFCF), "Teal"),
                    KitAccentSwatch("blue", Color(0xFF5AB0FF), "Blue"),
                    KitAccentSwatch("violet", Color(0xFFB79CFF), "Violet"),
                    KitAccentSwatch("pink", Color(0xFFF2A7E6), "Pink"),
                    KitAccentSwatch("mono", Color(0xFFF2F2F2), "Mono"),
                    KitAccentSwatch("silver", Color(0xFFB8BDC6), "Silver"),
                ),
                selected = "teal",
                onChanged = {},
                grid = true,
            )
        }
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

/** `_disabled()`: a theme this phone cannot offer, its reason on the swatch, in a Scaffold (kitGalleryShot). */
private val disabled: @Composable () -> Unit = {
    Row(Modifier.padding(16.dp)) {
        Box(Modifier.size(150.dp, KitOptionCardSizes.THEME_HEIGHT.dp)) {
            KitThemeOptionCard(
                PackId.Nord,
                "Material You",
                selected = false,
                onSelect = {},
                enabled = false,
                disabledReason = "Not on this phone",
                dark = true,
            )
        }
    }
}

class KitOptionCardGateTest {
    @Test fun kit_option_card_default() { FlutterGoldenGate.check("kit_option_card_default", content = cards) }
    @Test fun kit_option_card_default_1280x800() { FlutterGoldenGate.check("kit_option_card_default_1280x800", content = cards) }
    @Test fun kit_option_card_default_text2() { FlutterGoldenGate.check("kit_option_card_default_text2", modes = listOf("dark"), content = cards) }
    @Test fun kit_option_card_default_text2_1280x800() {
        FlutterGoldenGate.check("kit_option_card_default_text2_1280x800", modes = listOf("dark"), content = cards)
    }
    @Test fun kit_option_card_disabled() {
        FlutterGoldenGate.check("kit_option_card_disabled", frame = FlutterGoldenGate.Frame.Screen, content = disabled)
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_option_card",
            caps = mapOf("KitAccentRow" to "reading", "KitOptionCard" to "reading"),
            content = cards,
        )
    }
}
