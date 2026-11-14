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
 * Cut audit 1 (c16, c22, c23, the h2 pictures): under the Glass Details sheet the green "Allow once" button showed as a
 * big green ghost right under the field's label, the permission card's code box as a dark plate behind the Details row,
 * the card's colours as patches; under the model sheet the send button as a green smear by a row's star. A modal sheet
 * now shows the page's own picture (the live background) in its middle, the page only along the edge it shares with the
 * visible page (a bottom sheet's top, where its rim bends it), and none of the page along the window's edges. Measured on
 * the real sheet face ([kitLiquidSheetFace]: the claim, the spec, the backdrop) through the desktop stand-in of the plain
 * liquid pane ([kitLiquidPlainStandIn]: colour controls, blur, the hold, the tint).
 */
class KitModalSheetPageTest {
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true

        override fun liquidPane(spec: KitLiquidPaneSpec, backdrop: KitLiquidBackdrop, shape: CornerBasedShape, transform: () -> Float) =
            kitLiquidPlainStandIn(spec, backdrop, shape, this)
    }

    /** What the page holds, besides its picture. */
    private data class Page(
        val controls: Boolean = false,
        val topBlock: Boolean = false,
        val sideBlock: Boolean = false,
        /** A bright block across the sheet's top-right corner (a reply's bubble ending near the window's edge). */
        val cornerBlock: Boolean = false,
        /** The page's picture is recorded (a live background); false: the page is its only picture. */
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
            // The permission card's code box and its green "Allow once" button, under the sheet's middle.
            rect(if (dark) Color(0xFF0E0E10) else Color(0xFFE4E4E6), 20f, 335f, 340f, 375f, 12f)
            rect(if (dark) Color(0xFF3DDC8A) else Color(0xFF087F43), 190f, 390f, 340f, 440f, 16f)
        }
        // A bright block across the sheet's top edge (the transcript above it), and a green one across its left edge.
        if (page.topBlock) rect(if (dark) Color.White else Color.Black, 40f, 220f, 120f, 290f)
        if (page.sideBlock) rect(Color(0f, 1f, 0f), -20f, 360f, 30f, 420f)
        if (page.cornerBlock) rect(if (dark) Color.White else Color.Black, 300f, 220f, 380f, 300f)
    }

    @Composable
    private fun Scene(page: Page, dark: Boolean) {
        val source = rememberKitLiquidSource()
        val backdrop = rememberKitGlassBackdrop()
        val live = remember { KitLivePicture() }
        SideEffect {
            backdrop.painted = page.live
            live.backdrop = backdrop
        }
        CompositionLocalProvider(LocalKitLiquidPage provides source, LocalKitLivePicture provides live) {
            Box(Modifier.size(W.dp, H.dp).background(io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current.ground).testTag("scene")) {
                // The page, as a conversation's: its live background inside it, its content over that.
                Box(Modifier.fillMaxSize().kitLiquidSource(source)) {
                    Spacer(Modifier.fillMaxSize().kitGlassBackdropSource(backdrop) { picture(dark) })
                    Spacer(Modifier.fillMaxSize().drawBehind { content(page, dark) })
                }
                // A bottom sheet: the window's sides and foot, its top over the page.
                val face = kitLiquidSheetFace(RoundedCornerShape(28.dp), { 0f })
                Box(Modifier.offset(0.dp, SHEET_TOP.dp).size(W.dp, (H - SHEET_TOP).dp).then(face ?: Modifier.background(Color.Magenta)).testTag("sheet"))
            }
        }
    }

    private fun shot(page: Page, dark: Boolean = true): PixelMap {
        lateinit var out: PixelMap
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(
                        appearance = KitAppearance(style = Style.Glass, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
                        platform = LensPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) { Scene(page, dark) }
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

    @Test
    fun `B-GLASS-MODAL-003 a control under a sheet's middle does not read through, while the page's picture passes`() {
        for (dark in listOf(true, false)) {
            val bare = shot(Page(), dark)
            val withControls = shot(Page(controls = true), dark)
            // The code box and the button lie under the sheet's middle (from 85 dp below its top, past the page's band).
            val under = peakDiff(bare, withControls, 30f, 330f, 330f, 435f)
            // Before (the page is the sheet's only picture, as every sheet showed it until now): the button reads through.
            val beforeBare = shot(Page(live = false), dark)
            val beforeControls = shot(Page(controls = true, live = false), dark)
            val before = peakDiff(beforeBare, beforeControls, 30f, 330f, 330f, 435f)
            println("MODAL dark=$dark: the controls under the sheet's middle change it by $under (before: $before)")
            assertTrue(before >= 12f, "the scene puts a visible control under the sheet (dark=$dark): $before")
            assertTrue(under <= 2f, "a control reads through the sheet's middle (dark=$dark): a change of $under")
            // Alive, not fog: the green and the violet fields of the picture still differ under the sheet.
            val left = mean(bare, 30f, 330f, 90f, 440f)
            val right = mean(bare, 270f, 330f, 330f, 440f)
            val apart = sqrt((left[0] - right[0]) * (left[0] - right[0]) + (left[1] - right[1]) * (left[1] - right[1]) + (left[2] - right[2]) * (left[2] - right[2]))
            println("MODAL dark=$dark: the picture's two fields under the sheet are $apart apart (left ${left.toList()}, right ${right.toList()})")
            assertTrue(apart >= 15f, "the background's colour passes through the sheet (dark=$dark): $apart")
        }
    }

    @Test
    fun `B-GLASS-MODAL-004 the sheet's top edge still shows the page, its window edges none of it`() {
        val bare = shot(Page())
        val top = shot(Page(topBlock = true))
        val side = shot(Page(sideBlock = true))
        // The block across the top edge shows in the sheet's top band (the rim's outer part, no hold there).
        val t = peakDiff(bare, top, 50f, SHEET_TOP + 2f, 110f, SHEET_TOP + 12f)
        // The green block across the window's left edge shows nowhere in the sheet's left rim.
        val s = peakDiff(bare, side, 1f, 360f, 16f, 420f)
        println("MODAL edges: top edge change $t, left (window) edge change $s")
        assertTrue(t >= 20f, "the page across the sheet's top edge shows in its rim: $t")
        assertTrue(s <= 2f, "the page along the window's edge shows in the sheet's rim: $s")
        // And the page beside the sheet (above its top) is drawn as it was: the scene is real.
        assertTrue(peakDiff(bare, top, 50f, 225f, 110f, 245f) >= 100f, "the bright block lies on the page above the sheet")
    }

    @Test
    fun `B-GLASS-MODAL-005 a top corner on the window's edge shows none of the page, so the lens's turn there has no hard wedge`() {
        // Wave-one check (c-5-details-topright): a hard white wedge in the Details sheet's top-right corner. The top band
        // ran into the corner, where the side's rim (on the window's edge) read the page and the top's read the
        // background. Within the side's band nothing of the page is left, the top's band included.
        val bare = shot(Page())
        val corner = shot(Page(cornerBlock = true))
        val inCorner = peakDiff(bare, corner, W - 28f, SHEET_TOP + 2f, W - 2f, SHEET_TOP + 30f)
        // Away from the side the top band still shows the page (the same block's left part, 300-312 dp, lies 48-60 dp in).
        val apart = peakDiff(bare, corner, 300f, SHEET_TOP + 2f, 310f, SHEET_TOP + 20f)
        println("MODAL corner: in the top-right corner $inCorner, 50-60 dp from the side $apart")
        assertTrue(inCorner <= 2f, "the page shows in the sheet's top corner on the window's edge: $inCorner")
        assertTrue(apart >= 10f, "the top band away from the side still shows the page: $apart")
    }

    private companion object {
        const val W = 360f
        const val H = 480f
        const val D = 2f
        const val SHEET_TOP = 250f
    }
}
