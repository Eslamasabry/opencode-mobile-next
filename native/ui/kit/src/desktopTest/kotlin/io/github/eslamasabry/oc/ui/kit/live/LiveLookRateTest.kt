package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * c2-performance (2026-11-15, a Galaxy S22 home sitting still drew 36 frames a second for a minute under Glow spill): a
 * page's live look followed every display frame for the half minute after each touch. It steps at most
 * [LIVE_LOOK_FPS] times a second now, on a page of its own and on a framed (shared) page: the shader is drawn once per
 * step and nothing between steps.
 */
@OptIn(ExperimentalTestApi::class)
class LiveLookRateTest {
    /** The desktop's shaders, counting how often a look is drawn (each draw reads the program's brush once). */
    private class Counting(private val base: KitPlatform = DesktopKitPlatform()) : KitPlatform by base {
        var draws = 0
        override val liveShaders: LiveShaderCompiler =
            LiveShaderCompiler { source ->
                val inner = base.liveShaders?.compile(source) ?: return@LiveShaderCompiler null
                object : LiveShaderProgram() {
                    override val brush: Brush
                        get() {
                            draws++
                            return inner.brush
                        }

                    override fun float(name: String, v: Float) = inner.float(name, v)

                    override fun float2(name: String, x: Float, y: Float) = inner.float2(name, x, y)

                    override fun float3(name: String, x: Float, y: Float, z: Float) = inner.float3(name, x, y, z)

                    override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) = inner.float4(name, x, y, z, w)
                }
            }
    }

    private fun drawsPerTwoSeconds(shared: Boolean): Int {
        val platform = Counting()
        var counted = 0
        runDesktopComposeUiTest(120, 200) {
            mainClock.autoAdvance = false
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Full, liveBackground = true, liveStyle = "glowSpill"),
                    platform,
                    "en",
                ) {
                    KitLiveBackground(Modifier.fillMaxSize(), shared = shared) {}
                }
            }
            mainClock.advanceTimeBy(1_000)
            val d0 = platform.draws
            mainClock.advanceTimeBy(2_000)
            counted = platform.draws - d0
        }
        return counted
    }

    private companion object {
        /** The page's cap (KitLiveBackground's LIVE_LOOK_FPS), written out so the test reads the same before the cap. */
        const val CAP = 15
    }

    @Test
    fun `PERF-LIVE-1 a live look on its own page is drawn at most LIVE_LOOK_FPS times a second`() {
        val n = drawsPerTwoSeconds(shared = false)
        assertTrue(n in 1..(2 * CAP + 2), "the look was drawn $n times in 2 s (cap $CAP/s, and it must move)")
    }

    @Test
    fun `PERF-LIVE-2 a live look behind a framed page is drawn at most LIVE_LOOK_FPS times a second`() {
        val n = drawsPerTwoSeconds(shared = true)
        assertTrue(n in 1..(2 * CAP + 2), "the look was drawn $n times in 2 s (cap $CAP/s, and it must move)")
    }
}
