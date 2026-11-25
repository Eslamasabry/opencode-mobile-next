@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.JoinBox
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidJoin
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpButton
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Apple Liquid Glass Milestone 3 (Glass Fluids) verification:
 * - B-616: upward-growing composer menu smooth-minimum morph (`up = true`)
 * - B-616: modal menu drop priority over resting pill drop on `"bottom"` body
 * - B-616: floating round `KitJumpButton` wears real Liquid Glass (`LiquidRole.Pill`)
 * - B-616: `LiquidJoinShader` meniscus specular highlight compiles and renders in Skia SkSL
 */
class AppleLiquidGlassM3Test {

    @Test
    fun `B-616 upward menuRest and dropBox grow seamlessly upward out of bottom composer body`() {
        val composer = JoinBox.of(12f, 700f, 348f, 764f, 26f)
        val placed = JoinBox.of(20f, 500f, 220f, 680f, 20f)
        val (colL, colR) = LiquidJoin.menuColumn(composer, 24f, 72f, 56f)
        val overlap = 4f
        val (left, top) = LiquidJoin.menuRest(composer, placed, colL, colR, overlap, up = true)
        assertEquals(composer.left, left, 0.5f, "left-end trigger aligns flush with composer start")
        assertEquals(composer.top - 180f + overlap, top, 0.5f, "upward menu overlaps composer top edge by overlap")

        val rest = JoinBox.of(left, top, left + 200f, top + 180f, 20f)
        val mid = LiquidJoin.dropBox(rest, colL, colR, length = 0.5f, fill = 0.2f, up = true)
        assertEquals(rest.bottom, mid.bottom, 0.5f, "upward drop stays anchored at composer edge while rising")
        assertTrue(mid.top > rest.top && mid.top < rest.bottom, "upward drop rises from bottom edge")
    }

    @Test
    fun `B-616 modal menu drop takes priority over resting pill drop on same body and restores on dismiss`() {
        val join = KitLiquidJoin()
        val body = KitLiquidJoinBody(join, "bottom").also { it.granted = true }
        join.bodies["bottom"] = body
        val pill = KitLiquidJoinDrop(join, "bottom").also { it.granted = true; it.reachesCentre = false }
        val menu = KitLiquidJoinDrop(join, "bottom").also { it.granted = true; it.reachesCentre = true }
        join.drops += pill
        assertSame(pill, join.partnerOf(body), "pill joins bottom when alone")
        join.drops += menu
        assertSame(menu, join.partnerOf(body), "opening composer menu takes priority over resting pill")
        join.drops -= menu
        assertSame(pill, join.partnerOf(body), "dismissing menu cleanly restores pill join")
    }

    @Test
    fun `B-616 floating round KitJumpButton wears real Liquid Glass when backdrop is provided`() =
        runComposeUiTest {
            val platform = FakeKitPlatform(liquidGlass = true)
            setContent {
                KitTheme(KitAppearance(style = Style.Glass), platform, "en", stillFrame = true) {
                    val frost = rememberKitFrostState()
                    val join = KitLiquidJoin()
                    CompositionLocalProvider(LocalKitLiquidJoin provides join) {
                        Box(Modifier.size(360.dp, 640.dp)) {
                            Box(Modifier.fillMaxSize().kitFrostSource(frost))
                            Box(
                                Modifier.align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .then(kitLiquidFace(LiquidRole.Composer, frost, RoundedCornerShape(24.dp)) ?: Modifier)
                            )
                            KitJumpButton(
                                label = "Jump to latest",
                                onClick = {},
                                visible = true,
                                modifier = Modifier.align(Alignment.Center),
                                backdrop = frost,
                            )
                        }
                    }
                }
            }
            waitForIdle()
            val pillSpec = platform.liquidSpecs.firstOrNull { it.recipe.blurDp == 0.5f && it.recipe.refractionDp == 14f }
            assertNotNull(pillSpec, "round KitJumpButton must request LiquidRole.Pill liquid glass")
        }

    @Test
    fun `B-616 LiquidJoinShader with meniscus specular highlight compiles and renders in Skia SkSL`() {
        val effect = assertNotNull(DesktopLiveShader.compile(LiquidJoinShader.source(effect = true)), "effect shader compiles")
        val brush = assertNotNull(DesktopLiveShader.compile(LiquidJoinShader.source(effect = false)), "brush shader compiles")
        val body = Rect(20f, 200f, 340f, 264f)
        val drop = Rect(150f, 152f, 202f, 204f)
        val geom = JoinGeometry.of(body, 26f, drop, 22f, body, dropIsMine = false, reachesCentre = false)
        val u =
            JoinUniforms(
                geometry = geom,
                reach = 56f,
                band = 24f,
                rimMine = 28f,
                rimTheirs = 16f,
                refractionMine = 26f,
                refractionTheirs = 14f,
                saturation = 1.6f,
                contrast = 1f,
                brightness = -0.03f,
                knee = KNEE_OFF,
                tintMine = Color(0x28141820),
                tintTheirs = Color(0x24141820),
                rimPx = 1.25f,
            )
        u.set(effect, mode = 0f, res = 1f)
        u.set(brush, mode = 2f, res = 1f)
        assertNotNull((brush as DesktopLiveShader).skiaShader())
    }
}
