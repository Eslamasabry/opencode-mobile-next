@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrostState
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.platform.KitBackdropSampler
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * glass-truth 2026-11-16, item 2: words on the plain bar and the plain message box. Their glass holds back bright
 * content with the same luminance knee as the joined body, menus and sheets; and their words' ink is judged by what lies
 * behind the words, not by the mean of the whole pane.
 */
class KitWordsOnPlainGlassTest {
    /** Black under the start quarter of the pane, white over the rest (a bright block at the bar's far end). */
    private class QuarterSampler : KitBackdropSampler {
        var samples = 0

        override suspend fun sample(width: Int, height: Int, density: Density, draw: DrawScope.() -> Unit): IntArray? {
            samples++
            return IntArray(width * height) { i -> if (i % width < width / 4) BLACK else WHITE }
        }

        override fun release() = Unit
    }

    private class Sampling(val sampler: QuarterSampler, val base: FakeKitPlatform = FakeKitPlatform(liquidGlass = true)) : KitPlatform by base {
        override fun backdropSampler(): KitBackdropSampler = sampler
    }

    @Test
    fun `GLASS-TRUTH-2 a bar's ink is judged under its words, not over the whole pane`() =
        runComposeUiTest {
            val sampler = QuarterSampler()
            var source: KitFrostState? = null
            setContent {
                KitTheme(KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark), Sampling(sampler), "en", stillFrame = true) {
                    val frost = rememberKitFrostState().also { source = it }
                    Box(Modifier.size(400.dp, 300.dp)) {
                        Box(Modifier.fillMaxSize().kitFrostSource(frost))
                        Box(Modifier.width(400.dp).height(64.dp).then(kitLiquidWordsHost(LiquidRole.Bar, frost))) {
                            // The title lies in the start quarter, over black; the end three quarters are white.
                            Row(Modifier.padding(start = 12.dp)) { Box(Modifier.width(70.dp)) { KitText("Fix it") } }
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(3_000)
            waitForIdle()
            assertTrue(sampler.samples >= 1, "the pane sampled what lies behind it")
            val flip = assertNotNullFlip(source)
            // The whole pane's mean is .75 (past TO_DARK): judged over the whole pane, the light title flipped to dark
            // ink over a black ground. Under the words it is black: the title keeps the dark theme's light ink.
            assertEquals(0f, flip, "the words' ink follows what is behind the words")
        }

    private fun assertNotNullFlip(source: KitFrostState?): Float =
        source?.liquidFlips?.get(LiquidRole.Bar)?.progress?.value ?: error("no flip for the bar")

    @Test
    fun `GLASS-TRUTH-2 the plain bar and message box hand the platform the luminance knee`() =
        runComposeUiTest {
            val platform = FakeKitPlatform(liquidGlass = true)
            setContent {
                KitTheme(KitAppearance(style = Style.Glass, mode = AppearanceMode.Dark), platform, "en", stillFrame = true) {
                    val frost = rememberKitFrostState()
                    Box(Modifier.size(400.dp, 300.dp)) {
                        Box(Modifier.fillMaxSize().kitFrostSource(frost))
                        Box(Modifier.fillMaxWidth().height(64.dp).then(kitLiquidFace(LiquidRole.Bar, frost, LiquidBarShape) ?: Modifier))
                        Box(Modifier.padding(top = 120.dp).fillMaxWidth().height(80.dp).then(kitLiquidFace(LiquidRole.Composer, frost, RoundedCornerShape(26.dp)) ?: Modifier))
                    }
                }
            }
            waitForIdle()
            val barRecipe = LiquidEdge.recipe(LiquidRole.Bar, true)
            val boxRecipe = LiquidEdge.recipe(LiquidRole.Composer, true)
            val bar = platform.liquidSpecs.lastOrNull { it.recipe == barRecipe }
            val box = platform.liquidSpecs.lastOrNull { it.recipe == boxRecipe }
            assertTrue(bar != null && box != null, "both panes were drawn liquid: ${platform.liquidSpecs.map { it.recipe }}")
            assertTrue(bar!!.recipe.hold.knee, "the bar holds back bright content behind its words")
            assertTrue(box!!.recipe.hold.knee, "the message box holds back bright content behind its words")
            assertEquals(0f, bar.recipe.hold.chroma, "colour passes on the bar")
        }

    private companion object {
        const val BLACK: Int = -0x1000000
        const val WHITE: Int = -0x1
    }
}
