package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Smoothness round 2 (docs/native/qa/smoothness-2026-11-09): one live background for the whole app. While a page is
 * pushed or popped, two framed pages are on screen; each used to run its own clock, draw its own shader and record and
 * blur its own picture. They now show the theme root's one picture: one clock, one shader frame per display frame.
 */
@OptIn(ExperimentalTestApi::class)
class KitLiveShareTest {
    /** Counts the frames the look's shader is recorded. */
    private class CountingPlatform(private val base: KitPlatform = FakeKitPlatform()) : KitPlatform by base {
        var draws = 0
        override val liveShaders: LiveShaderCompiler =
            LiveShaderCompiler { source -> DesktopLiveShader.compile(source)?.let(::counted) }

        private fun counted(real: LiveShaderProgram): LiveShaderProgram =
            object : LiveShaderProgram() {
                override val brush: Brush
                    get() {
                        draws++
                        return real.brush
                    }

                override fun float(name: String, v: Float) = real.float(name, v)

                override fun float2(name: String, x: Float, y: Float) = real.float2(name, x, y)

                override fun float3(name: String, x: Float, y: Float, z: Float) = real.float3(name, x, y, z)

                override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) = real.float4(name, x, y, z, w)
            }
    }

    private fun appearance(style: Style) =
        KitAppearance(style = style, mode = AppearanceMode.Dark, motion = MotionLevel.Full, liveBackground = true, liveStyle = "mesh")

    /** Two framed pages at once, as during a push or a pop; [seen] gets each page's clock and the root's share. */
    @Composable
    private fun TwoPages(seen: (LiveFrameClock?, KitLiveShare?) -> Unit) {
        Box(Modifier.fillMaxSize()) {
            repeat(2) { i ->
                KitScreen(page = true) {
                    seen(LocalLiveClock.current, LocalKitLiveShare.current)
                    KitText("Page $i")
                }
            }
        }
    }

    @Test
    fun `B-KIT-LIVE-021 two pages on screen at once draw one live background between them`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            runDesktopComposeUiTest(200, 400) {
                val platform = CountingPlatform()
                val clocks = mutableSetOf<LiveFrameClock?>()
                var share: KitLiveShare? = null
                mainClock.autoAdvance = false
                setContent {
                    KitTheme(appearance(style), platform, "en") {
                        TwoPages { clock, s ->
                            clocks += clock
                            share = s
                        }
                    }
                }
                repeat(3) { mainClock.advanceTimeByFrame() }
                val start = platform.draws
                repeat(60) { mainClock.advanceTimeByFrame() }
                val frames = platform.draws - start
                assertTrue(frames in 50..63, "$style: the look was drawn $frames times in 60 frames (one background, not two)")
                assertEquals(1, clocks.size, "$style: both pages run on one clock")
                assertSame(share!!.clock, clocks.single(), "$style: the theme root's clock")
                assertEquals(1, share!!.pictures.count, "$style: one recorded picture for two pages of one size")
            }
        }
    }
}
