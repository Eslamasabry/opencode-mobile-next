@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLivePicture
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitLivePicture
import io.github.eslamasabry.oc.ui.kit.foundation.kitGlassBackdropSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cut audit 2 (2026-11-14; c-rowmenu-top, c-rowmenu-bot, p2-home-glass-*-rowmenu): the home row's long-press menu showed
 * the chips behind it as green blobs, the rows' icons and project dots as dark and blue blocks, and a "Try again" button
 * as a ghost in its middle. A menu is modal while it is open, so it now shows the page as a sheet does: its picture (the
 * live background) whole, the page's content only in its rim along its open edges. And a menu opened inside a sheet (the
 * Servers row's ⋯, a flat white pane) is a raised piece of the sheet's glass. Measured on the real menu face
 * ([kitLiquidMenuFace]: the claim, the spec, the backdrop) through the desktop stand-in of the plain liquid pane.
 */
class KitModalMenuPageTest {
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float) =
            kitLiquidPlainStandIn(spec, backdrop, shape, this)
    }

    /** What the page holds, besides its picture. */
    private data class Page(
        val controls: Boolean = false,
        val edgeBlock: Boolean = false,
        val deepBlock: Boolean = false,
        val live: Boolean = true,
    )

    /** The background: a green field on the left, a violet one on the right, eased across the middle. */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.picture(dark: Boolean) {
        val a = if (dark) Color(0xFF1E5A3C) else Color(0xFFB9F0D6)
        val b = if (dark) Color(0xFF3B2E5C) else Color(0xFFD9D0F7)
        drawRect(Brush.horizontalGradient(0f to a, .35f to a, .65f to b, 1f to b))
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.content(page: Page, dark: Boolean) {
        val d = density
        fun rect(color: Color, l: Float, t: Float, r: Float, b: Float, round: Float = 0f) =
            drawRoundRect(color, Offset(l * d, t * d), Size((r - l) * d, (b - t) * d), CornerRadius(round * d))
        if (page.controls) {
            // A chip and a row's black icon under the menu's middle (the home's chips and rows under its row menu).
            rect(if (dark) Color(0xFF3DDC8A) else Color(0xFF087F43), 130f, 210f, 230f, 250f, 20f)
            rect(Color.Black, 150f, 270f, 190f, 310f)
        }
        // A bright block across the menu's left edge, and one lying wholly in its middle, past its rim.
        if (page.edgeBlock) rect(if (dark) Color.White else Color.Black, MENU_L - 30f, 250f, MENU_L + 20f, 300f)
        if (page.deepBlock) rect(if (dark) Color.White else Color.Black, MENU_L + 50f, 320f, MENU_L + 80f, 350f)
    }

    @Composable
    private fun Scene(page: Page, dark: Boolean, overModal: Boolean) {
        val source = rememberKitLiquidSource()
        val backdrop = rememberKitGlassBackdrop()
        val live = remember { KitLivePicture() }
        SideEffect {
            backdrop.painted = page.live
            live.backdrop = backdrop
        }
        CompositionLocalProvider(LocalKitLiquidPage provides source, LocalKitLivePicture provides live) {
            Box(Modifier.size(W.dp, H.dp).background(io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.ground).testTag("scene")) {
                Box(Modifier.fillMaxSize().kitLiquidSource(source)) {
                    Spacer(Modifier.fillMaxSize().kitGlassBackdropSource(backdrop) { picture(dark) })
                    Spacer(Modifier.fillMaxSize().drawBehind { content(page, dark) })
                }
                CompositionLocalProvider(LocalKitLiquidOverModal provides overModal) {
                    // A menu in the middle of the window: every edge borders the visible page.
                    val face = kitLiquidMenuFace(RoundedCornerShape(16.dp), { 0f })
                    Box(Modifier.offset(MENU_L.dp, MENU_T.dp).size(MENU_W.dp, MENU_H.dp).then(face ?: Modifier.background(Color.Magenta)).testTag("menu"))
                }
            }
        }
    }

    private fun shot(page: Page, dark: Boolean = true, overModal: Boolean = false): PixelMap {
        lateinit var out: PixelMap
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        appearance = KitAppearance(style = Style.Glass, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
                        platform = LensPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) { Scene(page, dark, overModal) }
                }
            }
            waitForIdle()
            out = onNodeWithTag("scene").captureToImage().toPixelMap()
        }
        return out
    }

    /** The largest per-channel difference between [a] and [b] over a region (dp), of 255. */
    private fun peakDiff(a: PixelMap, b: PixelMap, l: Float, t: Float, r: Float, bottom: Float): Float {
        var m = 0f
        for (y in (t * D).roundToInt() until (bottom * D).roundToInt()) for (x in (l * D).roundToInt() until (r * D).roundToInt()) {
            val p = a[x, y]
            val q = b[x, y]
            m = max(m, max(abs(p.red - q.red), max(abs(p.green - q.green), abs(p.blue - q.blue))) * 255f)
        }
        return m
    }

    private fun mean(img: PixelMap, l: Float, t: Float, r: Float, b: Float): FloatArray {
        val s = FloatArray(3)
        var n = 0
        for (y in (t * D).roundToInt() until (b * D).roundToInt()) for (x in (l * D).roundToInt() until (r * D).roundToInt()) {
            val c = img[x, y]
            s[0] += c.red
            s[1] += c.green
            s[2] += c.blue
            n++
        }
        return FloatArray(3) { s[it] / max(n, 1) * 255f }
    }

    private fun apart(a: FloatArray, b: FloatArray): Float = sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]))

    /** The menu's middle, past its 26 dp rim on every side. */
    private val middle = floatArrayOf(MENU_L + 40f, MENU_T + 40f, MENU_L + MENU_W - 40f, MENU_T + MENU_H - 40f)

    @Test
    fun `B-GLASS-MENU-010 the chips and icons under a menu's middle do not read through it, while the background's colour passes`() {
        for (dark in listOf(true, false)) {
            val bare = shot(Page(), dark)
            val withControls = shot(Page(controls = true), dark)
            val under = peakDiff(bare, withControls, middle[0], middle[1], middle[2], middle[3])
            // Before (the page is the menu's only picture, as every menu showed it until now): they read through.
            val before = peakDiff(shot(Page(live = false), dark), shot(Page(controls = true, live = false), dark), middle[0], middle[1], middle[2], middle[3])
            println("MENU dark=$dark: the chip and icon under the menu's middle change it by $under (before: $before)")
            assertTrue(before >= 12f, "the scene puts visible controls under the menu (dark=$dark): $before")
            assertTrue(under <= 2f, "a control reads through the menu's middle (dark=$dark): a change of $under")
            // Alive, not fog: the green and the violet fields of the picture still differ under the menu.
            val left = mean(bare, MENU_L + 30f, MENU_T + 40f, MENU_L + 70f, MENU_T + MENU_H - 40f)
            val right = mean(bare, MENU_L + MENU_W - 70f, MENU_T + 40f, MENU_L + MENU_W - 30f, MENU_T + MENU_H - 40f)
            println("MENU dark=$dark: the picture's two fields under the menu are ${apart(left, right)} apart")
            assertTrue(apart(left, right) >= 15f, "the background's colour passes through the menu (dark=$dark)")
        }
    }

    @Test
    fun `B-GLASS-MENU-011 a menu keeps the page in its rim along an open edge, and none of it past the rim`() {
        val bare = shot(Page())
        val edge = shot(Page(edgeBlock = true))
        val deep = shot(Page(deepBlock = true))
        // The block across the left edge shows in the rim's outer part (2 to 12 dp in; the stand-in does not bend).
        val inRim = peakDiff(bare, edge, MENU_L + 2f, 260f, MENU_L + 12f, 290f)
        // Past the rim: the page is gone at 26 dp, and the detail hold's mean (28 dp) no longer carries the rim's page into
        // the middle as a cloud of its colour (it takes the middle's mean from the middle), so from 50 dp in nothing of it
        // is left; before this, the hold's mean spread it up to 54 dp in.
        val pastRim = peakDiff(bare, edge, MENU_L + 50f, 260f, MENU_L + 90f, 290f)
        // A block lying wholly in the middle (50 to 80 dp in) shows nowhere.
        val middleBlock = peakDiff(bare, deep, MENU_L + 40f, 310f, MENU_L + 90f, 360f)
        println("MENU band: in the rim $inRim, past it $pastRim, a block in the middle $middleBlock")
        assertTrue(inRim >= 20f, "the page across the menu's edge shows in its rim: $inRim")
        assertTrue(pastRim <= 3f, "the page shows past the menu's rim: $pastRim")
        assertTrue(middleBlock <= 2f, "a block in the menu's middle reads through: $middleBlock")
    }

    @Test
    fun `B-GLASS-MENU-012 a menu inside a sheet is a raised piece of its glass - the background's colour, none of the page`() {
        for (dark in listOf(true, false)) {
            val bare = shot(Page(), dark, overModal = true)
            val withControls = shot(Page(controls = true, edgeBlock = true), dark, overModal = true)
            // Nothing of the page under it: not its middle, not along its edges (the sheet's rows would lie there).
            val under = peakDiff(bare, withControls, MENU_L + 2f, MENU_T + 2f, MENU_L + MENU_W - 2f, MENU_T + MENU_H - 2f)
            // Glass, not the flat surface it was (the Servers row's ⋯ in the Servers sheet): the picture's fields differ.
            val left = mean(bare, MENU_L + 30f, MENU_T + 40f, MENU_L + 70f, MENU_T + MENU_H - 40f)
            val right = mean(bare, MENU_L + MENU_W - 70f, MENU_T + 40f, MENU_L + MENU_W - 30f, MENU_T + MENU_H - 40f)
            println("MENU over a sheet dark=$dark: the page changes it by $under, its fields ${apart(left, right)} apart")
            assertTrue(under <= 2f, "the page reads through a menu over a sheet (dark=$dark): $under")
            assertTrue(apart(left, right) >= 15f, "a menu over a sheet is glass over the background, not a flat pane (dark=$dark)")
            // The same glass a step nearer: a little more of the sheet's tint than the bare menu over the page.
            val plain = shot(Page(), dark)
            val raised = apart(mean(bare, middle[0], middle[1], middle[2], middle[3]), mean(plain, middle[0], middle[1], middle[2], middle[3]))
            println("MENU over a sheet dark=$dark: its face differs from a menu over the page by $raised")
            assertTrue(raised >= 2f, "a menu over a sheet wears the sheet's tint under its own (dark=$dark): $raised")
        }
    }

    private companion object {
        const val W = 360f
        const val H = 480f
        const val D = 2f
        const val MENU_L = 60f
        const val MENU_T = 140f
        const val MENU_W = 240f
        const val MENU_H = 240f
    }
}
