@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidBackdrop
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidPaneSpec
import io.github.eslamasabry.oc.ui.kit.effects.drawLiquidTint
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Desktop renders of the Glass look picker (owner 2026-11-09, mockup `02-picker-B-grid.png`): the sheet over a
 * Settings › Appearance page, light and dark, with each offered look chosen, and the app's own Frosted sheet over a
 * busy page. Not a test: it runs only when `GLASS_LOOK_RENDER_OUT` names a folder.
 *
 * Desktop has no lens. Its liquid panes here are the library's minus the bend (the backdrop blurred by the recipe's
 * blur, the face and the rim over it), so Glossy and Smoked read as on the phone without the curve at the rim; the
 * Frosted look draws the kit's own frost, exactly as on the phone.
 */
class GlassLookPickerRenders {
    /** Desktop with a blur, whose liquid panes draw like Android's without the lens. */
    private class LensRenderPlatform(private val base: FakeKitPlatform = FakeKitPlatform(frost = true, liquidGlass = true)) :
        KitPlatform by base {
        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float): Modifier {
            val at = arrayOfNulls<LayoutCoordinates>(1)
            return Modifier.onGloballyPositioned { at[0] = it }
                .clip(shape)
                .drawWithCache {
                    val layer = obtainGraphicsLayer()
                    val outline = shape.createOutline(size, layoutDirection, this)
                    onDrawBehind {
                        val c = at[0] ?: return@onDrawBehind
                        val t = transform().coerceIn(0f, 1f)
                        val r = (spec.recipe.blurDp + (spec.frostBlurDp - spec.recipe.blurDp) * t).dp.toPx()
                        layer.renderEffect = if (r > 0f) BlurEffect(r, r) else null
                        layer.record { backdrop.draw(this, c) }
                        drawLayer(layer)
                        drawLiquidTint(spec, shape)
                        drawOutline(outline, spec.rim, style = Stroke(spec.rimDp.dp.toPx() * 2f))
                    }
                }
        }
    }

    @Test
    fun `glass look picker renders`() {
        val out = System.getenv("GLASS_LOOK_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            for (look in GlassLooks.offered) render(File(out, "picker-${look.storageName}-$mode.png"), look, dark) { Picker(look) }
            render(File(out, "app-frosted-sheet-$mode.png"), GlassLookId.Frosted, dark) { FrostedSheet() }
        }
    }

    private fun render(file: File, look: GlassLookId, dark: Boolean, sheet: @Composable () -> Unit) {
        runDesktopComposeUiTest(WIDTH, HEIGHT) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                    KitTheme(
                        KitAppearance(
                            mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                            style = Style.Glass,
                            glassLook = look,
                            liveBackground = true,
                            liveStyle = "auroraImage",
                        ),
                        LensRenderPlatform(),
                        "en",
                        stillFrame = true,
                    ) {
                        KitSheetHost {
                            KitLiveBackground(Modifier.fillMaxSize()) { Page() }
                            sheet()
                        }
                    }
                }
            }
            repeat(40) { mainClock.advanceTimeBy(100) }
            ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", file)
        }
    }

    @Composable
    private fun Picker(look: GlassLookId) {
        val words =
            mapOf(
                GlassLookId.Glossy to ("Glossy" to "Bright shine, bends at the edges"),
                GlassLookId.Clear to ("Clear" to "See-through, light shine"),
                GlassLookId.Frosted to ("Frosted" to "Soft blur, easiest to read"),
                GlassLookId.Smoked to ("Smoked" to "Dark tinted glass on bars and buttons"),
            )
        KitSheet(visible = true, onDismissRequest = {}, title = "Glass look", closeLabel = "Close") {
            KitGlassLookPicker(
                options = GlassLooks.offered.map { KitGlassLookOption(it, words.getValue(it).first, words.getValue(it).second) },
                selected = look,
                onSelect = {},
                disclosure = "The previews use bright colours so the differences show. The page behind shows your real background.",
            )
        }
    }

    @Composable
    private fun FrostedSheet() {
        KitSheet(visible = true, onDismissRequest = {}, title = "Rename conversation", closeLabel = "Close") {
            KitRowGroup {
                KitRow("Frosted glass", supporting = "The page behind this sheet is blurred, not hidden", onClick = {})
                KitRow("Second row", supporting = "Words on frost stay readable", onClick = {})
            }
        }
    }

    /** A stand-in for Settings › Appearance: bright theme cards and rows, so the glass has something to show. */
    @Composable
    private fun Page() {
        Column(Modifier.padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KitText("Appearance", Modifier.padding(horizontal = 16.dp), role = TypeRole.LargeTitle)
            listOf(listOf(PackId.Opencode, PackId.Nord, PackId.Catppuccin), listOf(PackId.Dracula, PackId.TokyoNight, PackId.Gruvbox)).forEach { packs ->
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    packs.forEach { p -> KitThemeOptionCard(p, p.name, p == PackId.Opencode, {}, Modifier.weight(1f)) }
                }
            }
            KitRowGroup(leadingTiles = true) {
                KitRow("Style", supporting = "Glass", leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
                KitRow("Glass look", supporting = "Frosted", leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
                KitRow("Corners and spacing", supporting = "Round, Roomy", leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
                KitRow("Accent colour", supporting = "Match", leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
                KitRow("Background", supporting = "Painted aurora", leading = KitRowLeading.Glyph(KitIcons.folderOpen), onClick = {})
            }
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
        const val DENSITY = 2.625f
    }
}
