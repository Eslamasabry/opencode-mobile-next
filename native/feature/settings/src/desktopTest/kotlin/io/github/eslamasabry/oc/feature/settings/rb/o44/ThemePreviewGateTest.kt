package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.ui.ThemePreviewSheet
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import kotlin.test.Test

/**
 * Gate cases for the theme preview sheet (Flutter `test/revamp/shared_settings_1_golden_test.dart:169-240`): the
 * sheet opened over an empty page, with the scene's controller (Graphite in use, light or dark following Android):
 * another theme (Catppuccin) with Apply, on a phone and on a 1280×800 window; the theme in use; and Material You on a
 * device without its tones.
 */
class ThemePreviewGateTest {
    private val screen = FlutterGoldenGate.Frame.Screen

    private fun shot(name: String, pack: PackId, inUse: PackId = PackId.Opencode, window: Pair<Int, Int>? = null) {
        FlutterGoldenGate.check(name, frame = screen, window = window) {
            KitSheetHost(Modifier.fillMaxSize()) {
                // The scene's page: the "Open" text the test taps, centred (shared_settings_1_golden_test.dart:180-188).
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { KitText("Open") }
                ThemePreviewSheet(
                    pack = pack,
                    ui = AppearanceUi(mode = AppearanceMode.System, pack = inUse),
                    onDismiss = {},
                    onApply = {},
                )
            }
        }
    }

    @Test
    fun settings_theme_pack_preview_sheet_other() =
        shot("settings_theme_pack_preview_sheet_other", PackId.Catppuccin)

    @Test
    fun settings_theme_pack_preview_sheet_other_1280x800() =
        shot("settings_theme_pack_preview_sheet_other_1280x800", PackId.Catppuccin, window = Pair(1280, 800))

    @Test
    fun settings_theme_pack_preview_sheet_in_use() =
        shot("settings_theme_pack_preview_sheet_in_use", PackId.Catppuccin, inUse = PackId.Catppuccin)

    @Test
    fun settings_theme_pack_preview_sheet_unavailable() =
        shot("settings_theme_pack_preview_sheet_unavailable", PackId.Dynamic)
}
