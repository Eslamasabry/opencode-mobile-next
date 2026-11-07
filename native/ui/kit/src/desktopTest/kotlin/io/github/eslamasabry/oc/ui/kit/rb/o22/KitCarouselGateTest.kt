package io.github.eslamasabry.oc.ui.kit.rb.o22

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitOptionCardSizes
import io.github.eslamasabry.oc.ui.kit.choice.KitThemeOptionCard
import io.github.eslamasabry.oc.ui.kit.frame.KitCarousel
import kotlin.test.Test

/** `themePackLabels` (theme_packs.dart, theme_packs_generated.dart) in `ThemePackId.values` order. */
private val packLabels = listOf(
    "Graphite", "Catppuccin", "Gruvbox", "Solarized", "Dracula", "Nord", "Tokyo Night", "One Dark", "Monokai", "Rosé Pine",
    "Everforest", "Kanagawa", "Ayu", "Night Owl", "GitHub", "Palenight", "Synthwave", "Cobalt", "Midnight", "Paper",
    "Forest", "Ocean", "Sunset", "Sakura", "Lavender", "Mint", "Coffee", "Slate", "Amber", "High contrast", "Material You",
)

/** test/goldens/kit/kit_carousel_golden_test.dart `_part(count)`: theme swatches, opened on the second. */
private fun part(count: Int): @Composable () -> Unit = {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
        KitCarousel("Themes", count, KitOptionCardSizes.THEME_HEIGHT, initialIndex = 1) { i ->
            val pack = PackId.entries[i % PackId.entries.size]
            KitThemeOptionCard(pack, packLabels[pack.ordinal], selected = i == 1, onSelect = {}, dark = true)
        }
    }
}

class KitCarouselGateTest {
    @Test fun kit_carousel_five() { FlutterGoldenGate.check("kit_carousel_five", content = part(5)) }
    @Test fun kit_carousel_five_1280x800() { FlutterGoldenGate.check("kit_carousel_five_1280x800", content = part(5)) }
    @Test fun kit_carousel_many() { FlutterGoldenGate.check("kit_carousel_many", content = part(30)) }
    @Test fun kit_carousel_many_1280x800() { FlutterGoldenGate.check("kit_carousel_many_1280x800", content = part(30)) }
    @Test fun kit_carousel_five_text2() { FlutterGoldenGate.check("kit_carousel_five_text2", modes = listOf("dark"), content = part(5)) }
    @Test fun kit_carousel_five_text2_1280x800() {
        FlutterGoldenGate.check("kit_carousel_five_text2_1280x800", modes = listOf("dark"), content = part(5))
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix("part-kit_carousel", caps = mapOf("KitCarousel" to "reading"), content = part(5))
    }
}
