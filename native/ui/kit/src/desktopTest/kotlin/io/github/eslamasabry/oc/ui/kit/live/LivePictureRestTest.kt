@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitGlassBackdrop
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Device 2026-11-14 (Glass, Aurora drift): the rule for a visible page over a live background. It draws while the
 * background's clock runs, and once the clock rests (30 s after the last touch) the picture panes read stops changing and
 * nothing over it draws again; a touch starts it again.
 */
class LivePictureRestTest {
    /** Desktop with the platform's blur (Skia) and the looks' shaders, as Android 13+ has them. */
    private class GlassPlatform(base: KitPlatform = FakeKitPlatform()) : KitPlatform by base {
        override val liveShaders: LiveShaderCompiler = LiveShaderCompiler { DesktopLiveShader.compile(it) }

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect {
            val r = FrostPolicy.blurRadiusPx(spec.sigmaDp, density) * .57735f + .5f
            return BlurEffect(r, r, TileMode.Clamp)
        }
    }

    @Test
    fun `GLASS-REST-03 the live picture stops changing and nothing over it draws once the clock rests, a touch starts it again`() {
        var picture: KitGlassBackdrop? = null
        var clock: LiveFrameClock? = null
        var draws = 0
        // 33 s of frames: past the default minute of a test on a slow runner, so it gets five.
        runDesktopComposeUiTest(120, 120, EmptyCoroutineContext, EmptyCoroutineContext, 5.minutes) {
            mainClock.autoAdvance = false
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, style = Style.Glass, liveBackground = true, liveStyle = "aurora", liveStrength = 1.6f),
                    GlassPlatform(),
                    "en",
                ) {
                    KitLiveBackground(Modifier.fillMaxSize()) {
                        picture = LocalKitGlassBackdrop.current
                        clock = LocalLiveClock.current
                        // A pane's reading of the picture, as every Glass pane over it makes.
                        Canvas(Modifier.size(8.dp)) {
                            picture?.frame()
                            draws++
                            drawRect(Color.Transparent)
                        }
                    }
                }
            }
            mainClock.advanceTimeBy(2_000)
            val d0 = draws
            mainClock.advanceTimeBy(1_000)
            assertTrue(draws - d0 >= 3, "while the clock runs the picture steps and the pane draws (${draws - d0} in 1 s)")
            // Past the rest: 30 s after the last touch (the start counts as one).
            mainClock.advanceTimeBy(30_000)
            assertTrue(clock!!.resting, "the clock rests 30 s after the last touch")
            val still = picture!!.frame()
            val d1 = draws
            mainClock.advanceTimeBy(3_000)
            assertEquals(still, picture!!.frame(), "a resting picture keeps its last frame")
            assertTrue(draws - d1 <= 1, "nothing over a resting picture draws (${draws - d1} draws in 3 s)")
            clock!!.touch()
            mainClock.advanceTimeBy(1_500)
            assertNotEquals(still, picture!!.frame(), "a touch starts the picture again")
            assertTrue(draws > d1 + 1, "and the pane over it draws again")
        }
    }
}
