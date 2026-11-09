@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Desktop renders for the Appearance audit (owner 2026-11-10: "the themes ... are all washed off", "borders cutoff"):
 * one sample page (the preview card, a row group, a segmented choice, two chips and the three buttons) in every theme,
 * light and dark, and in every style. Not a test: it runs only when `APPEARANCE_AUDIT_OUT` names a folder, and writes
 * `theme-<pack>-<mode>.png` and `style-<style>-<mode>.png` there.
 */
class AppearanceAuditRenders {
    @Test
    fun `appearance audit renders`() {
        val out = System.getenv("APPEARANCE_AUDIT_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            for (pack in PackId.entries) render(File(out, "theme-${pack.name}-$mode.png"), KitAppearance(mode = mode(dark), pack = pack))
            for (style in Style.entries) render(File(out, "style-${style.name}-$mode.png"), KitAppearance(mode = mode(dark), style = style))
        }
    }

    private fun mode(dark: Boolean) = if (dark) AppearanceMode.Dark else AppearanceMode.Light

    private fun render(file: File, appearance: KitAppearance) {
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                    KitTheme(appearance, FakeKitPlatform(frost = true), "en", stillFrame = true) { Sample() }
                }
            }
            repeat(10) { mainClock.advanceTimeBy(100) }
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", file)
        }
    }

    @Composable
    private fun Sample() {
        val colors = LocalKitColors.current
        Column(
            Modifier.fillMaxSize().background(colors.ground).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KitText("Appearance", role = TypeRole.RowTitle)
            KitAppearancePreview("Tidy up the settings page", "Done. Three files changed and every check passes.", "Review changes", "sample")
            KitRowGroup {
                KitRow(title = "Server", supporting = "This phone · Connected", leading = KitRowLeading.Glyph(KitIcons.model), trailing = KitRowTrailing.Chevron, onClick = {})
                KitRow(title = "Conversations", leading = KitRowLeading.Glyph(KitIcons.check), trailing = KitRowTrailing.Chevron, onClick = {})
            }
            KitSegmented(listOf(KitSegment(0, "Round"), KitSegment(1, "Soft"), KitSegment(2, "Sharp")), 0, "Corners", onChange = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KitChip("Auto-approve on", kind = KitChipKind.Action, icon = KitIcons.shield, selected = true, onClick = {})
                KitChip("Running", kind = KitChipKind.Action, onClick = {})
            }
            KitText("Secondary words explain a choice in one line.", role = TypeRole.Secondary, tone = TextTone.Secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KitButton("Continue", {}, fullWidth = false)
                KitButton("Later", {}, role = KitButtonRole.Secondary, fullWidth = false)
                KitButton("Skip", {}, role = KitButtonRole.Tertiary, fullWidth = false)
            }
        }
    }

    private companion object {
        const val DENSITY = 2f
        const val WIDTH = 720
        const val HEIGHT = 1180
    }
}
