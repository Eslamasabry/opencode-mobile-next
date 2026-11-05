package io.github.eslamasabry.oc.ui.kit.rb.o05

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import io.github.eslamasabry.oc.ui.design.color.Accents
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatch
import io.github.eslamasabry.oc.ui.kit.choice.KitSwatchGrid
import io.github.eslamasabry.oc.ui.kit.theme.KitThemePreview
import kotlin.test.Test

/** Every scene of test/goldens/kit/kit_swatch_golden_test.dart. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class KitSwatchGateTest {
    private fun roles(id: PackId, light: Boolean): Roles = ThemeResolver.roles(id, null, !light, false, null)

    private val ids =
        listOf(PackId.Opencode, PackId.Dynamic, PackId.Catppuccin, PackId.Gruvbox, PackId.Solarized, PackId.Dracula, PackId.Nord, PackId.TokyoNight)

    private fun themeSwatch(id: PackId, light: Boolean): KitSwatch =
        if (id == PackId.Dynamic) {
            KitSwatch("theme-pack-${id.storageName}", "Material You", null, false, null, "Needs Android 12 or later", roles = null, theme = true)
        } else {
            KitSwatch("theme-pack-${id.storageName}", Packs.of(id).label, null, id == PackId.Opencode, {}, roles = roles(id, light), theme = true)
        }

    private fun grid(light: Boolean): @Composable () -> Unit = {
        KitSwatchGrid("Theme", ids.map { themeSwatch(it, light) }, inUseLabel = "In use")
    }

    private fun disabled(light: Boolean): @Composable () -> Unit = {
        KitSwatchGrid("Theme", listOf(themeSwatch(PackId.Opencode, light), themeSwatch(PackId.Dynamic, light)), inUseLabel = "In use")
    }

    private fun accent(light: Boolean): @Composable () -> Unit = {
        KitSwatchGrid(
            "Accent colour",
            Accents.graphite.filter { it.id != "green" }.map {
                KitSwatch("accent-${it.id}", it.id, Color(it.of(!light).v), it.id == "blue", {})
            },
            inUseLabel = "In use",
        )
    }

    private fun preview(id: PackId, name: String, light: Boolean): @Composable () -> Unit = {
        KitThemePreview(roles(id, light), "Preview of $name")
    }

    private val tab: ComposeUiTest.() -> Unit = { onRoot().performKeyInput { pressKey(Key.Tab) } }

    @Test fun declared_states() {
        for (light in listOf(false, true)) {
            val mode = listOf(if (light) "light" else "dark")
            FlutterGoldenGate.check("kit_swatch_grid", modes = mode, content = grid(light))
            FlutterGoldenGate.check("kit_swatch_accent", modes = mode, content = accent(light))
            FlutterGoldenGate.check("kit_swatch_preview_graphite", modes = mode, content = preview(PackId.Opencode, "Graphite", light))
            FlutterGoldenGate.check("kit_swatch_preview_catppuccin", modes = mode, content = preview(PackId.Catppuccin, "Catppuccin", light))
            FlutterGoldenGate.check("kit_swatch_disabled", modes = mode, content = disabled(light))
            FlutterGoldenGate.check("kit_swatch_grid_focused", modes = mode, act = tab, content = grid(light))
        }
    }

    @Test fun grid_sizes() {
        for (light in listOf(false, true)) {
            val mode = listOf(if (light) "light" else "dark")
            for (s in listOf("_360x800", "_915x412", "_800x1280", "_1280x800", "_1600x1000")) {
                FlutterGoldenGate.check("kit_swatch_grid$s", modes = mode, content = grid(light))
            }
        }
    }

    @Test fun scaled_and_arabic() {
        val dark = listOf("dark")
        for (s in listOf("", "_1280x800")) {
            FlutterGoldenGate.check("kit_swatch_grid_text2$s", modes = dark, content = grid(false))
            FlutterGoldenGate.check("kit_swatch_grid_ar$s", modes = dark, content = grid(false))
            FlutterGoldenGate.check("kit_swatch_preview_graphite_text2$s", modes = dark, content = preview(PackId.Opencode, "Graphite", false))
            FlutterGoldenGate.check("kit_swatch_preview_graphite_ar$s", modes = dark, content = preview(PackId.Opencode, "Graphite", false))
        }
    }

    @Test fun matrix() {
        FlutterGoldenGate.matrix(
            "part-kit_swatch",
            caps = mapOf("KitSwatch" to "glyph", "KitSwatchGrid" to "reading", "KitThemePreview" to "reading"),
            content = {
                // One painted width is held against every class cap, the 96 dp glyph cap included: one accent circle.
                KitSwatchGrid("Accent colour", listOf(KitSwatch("accent-blue", "blue", Color(0xFF1F6FEB), true, {})), inUseLabel = "In use")
            },
        )
    }
}
