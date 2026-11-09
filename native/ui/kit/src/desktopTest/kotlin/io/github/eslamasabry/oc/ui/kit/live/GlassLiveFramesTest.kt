@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * glass-live (owner 2026-11-09: "When the background changes behind the glass, the render is fixed with the previous
 * background render"): a Glass pane over the live background shows every change of what lies behind it on the next
 * frame: a step of the look's time, and a switch to another look.
 */
class GlassLiveFramesTest {
    /** Desktop with the platform's blur (Skia) and the looks' shaders, as Android 13+ has them. */
    private class GlassPlatform(base: KitPlatform = FakeKitPlatform()) : KitPlatform by base {
        override val liveShaders: LiveShaderCompiler = LiveShaderCompiler { DesktopLiveShader.compile(it) }

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect {
            val r = FrostPolicy.blurRadiusPx(spec.sigmaDp, density) * .57735f + .5f
            return BlurEffect(r, r, TileMode.Clamp)
        }
    }

    /** Mean per-channel difference between two captures of the pane, 0..255. */
    private fun difference(a: androidx.compose.ui.graphics.PixelMap, b: androidx.compose.ui.graphics.PixelMap): Double {
        var sum = 0.0
        var n = 0
        for (y in 0 until minOf(a.height, b.height) step 2) for (x in 0 until minOf(a.width, b.width) step 2) {
            val p = a[x, y]
            val q = b[x, y]
            sum += (abs(p.red - q.red) + abs(p.green - q.green) + abs(p.blue - q.blue)) * 255.0 / 3.0
            n++
        }
        return sum / n
    }

    @Test
    fun `B-KIT-LIVE-053 a glass pane shows the background's next frame and a new look at once, never a stale picture`() {
        var look by mutableStateOf("aurora")
        runDesktopComposeUiTest(320, 420) {
            mainClock.autoAdvance = false
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, style = Style.Glass, liveBackground = true, liveStyle = look, liveStrength = 1.6f),
                    GlassPlatform(),
                    "en",
                ) {
                    KitLiveBackground(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.padding(24.dp)
                                .size(240.dp, 300.dp)
                                .then(kitSurfaceLook(KitSurfaceInput(SurfaceRole.Content, KitShapeId.Card, SurfaceLevel.Surface1)).modifier)
                                .testTag("pane")
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(600)
            fun pane() = onNodeWithTag("pane").captureToImage().toPixelMap()
            val first = pane()
            // The look's time steps: the pane shows the new frame of it.
            mainClock.advanceTimeBy(1_500)
            val stepped = pane()
            val moved = difference(first, stepped)
            // A stale pane changes by exactly 0; the haze itself drifts slowly (a third of a step a pixel in 1.5 s at Bold).
            assertTrue(moved > .15, "the pane shows the background's next frame (mean change $moved)")
            // Another look: the pane shows it on the next frame, not the old one.
            look = "matrixRain"
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val switched = pane()
            val changed = difference(stepped, switched)
            assertTrue(changed > .4, "the pane shows the new look at once (mean change $changed)")
        }
    }
}
