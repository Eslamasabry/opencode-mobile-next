@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.FineTuneChoice
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.choice.KitStyleOptionCard
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Desktop renders of Glass's in-flow panes (glass-panes, 2026-11-08): style cards, the Corners control, a row group
 * with a chosen row, chips and a switch, over the painted aurora, for each Corners value, dark and light. Not a test:
 * it runs only when `GLASS_RENDER_OUT` names a folder. Desktop has no liquid lens; the panes' frost is the common blur.
 */
class GlassPaneRenders {
    @Test
    fun `glass pane renders`() {
        val out = System.getenv("GLASS_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        val styles = System.getenv("GLASS_RENDER_STYLES")?.split(',')?.map { Style.valueOf(it) } ?: listOf(Style.Glass)
        for (style in styles) for (dark in listOf(true, false)) for (corners in Corners.entries) {
            val name = "${style.name.lowercase()}-${corners.name.lowercase()}-${if (dark) "dark" else "light"}.png"
            runDesktopComposeUiTest(WIDTH, HEIGHT) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                        KitTheme(
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                                style = style,
                                fineTune = FineTuneChoice(corners = corners),
                                liveBackground = true,
                                liveStyle = "auroraImage",
                            ),
                            FakeKitPlatform(frost = true),
                            "en",
                        ) {
                            KitLiveBackground(Modifier.fillMaxSize()) { Sample(corners) }
                        }
                    }
                }
                waitForIdle()
                ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Sample(corners: Corners) {
        Column(Modifier.padding(top = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KitText("Appearance", Modifier.padding(horizontal = 16.dp), role = TypeRole.LargeTitle)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Style.Solid, Style.Glass, Style.Soft).forEach { s ->
                    KitStyleOptionCard(s, s.name, s == Style.Glass, {}, Modifier.weight(1f))
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                KitSegmented(Corners.entries.map { KitSegment(it, it.name) }, corners, "Corners", onChange = {})
            }
            KitRowGroup(leadingTiles = true) {
                KitRow("All projects", supporting = "24 conversations", leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
                KitRow(
                    "new",
                    supporting = "3 conversations · 1 running",
                    leading = KitRowLeading.Glyph(KitIcons.folderOpen, current = true),
                    selected = true,
                    trailing = KitRowTrailing.Check,
                    onClick = {},
                )
                KitRow("oc_app", supporting = "12 conversations", leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KitChip("new", kind = KitChipKind.Action, tone = KitChipTone.Active, onClick = {})
                KitChip("Running", kind = KitChipKind.Action, selected = true, onClick = {})
                KitChip("Needs you", kind = KitChipKind.Action, selected = false, onClick = {})
            }
            KitRowGroup {
                KitSwitchRow("Sounds", checked = true, supporting = "Play a sound when a reply ends", onCheckedChange = {})
                KitSwitchRow("Haptics", checked = false, onCheckedChange = {})
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2000
        const val DENSITY = 2.625f
    }
}
