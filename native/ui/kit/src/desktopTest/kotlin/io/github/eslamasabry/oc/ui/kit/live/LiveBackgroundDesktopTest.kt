@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E20-S28 on desktop Skia (the same shader language as Android's AGSL): every look compiles, the background moves
 * without recomposing the page, and a still frame does not move.
 */
class LiveBackgroundDesktopTest {
    /** Counts the frames each look's shader is drawn. */
    private class CountingPlatform(private val base: KitPlatform = FakeKitPlatform()) : KitPlatform by base {
        var draws = 0
        val times = mutableListOf<Float>()

        override val liveShaders: LiveShaderCompiler =
            LiveShaderCompiler { source -> DesktopLiveShader.compile(source)?.let(::counted) }

        private fun counted(real: LiveShaderProgram): LiveShaderProgram =
            object : LiveShaderProgram() {
                override val brush: Brush
                    get() {
                        draws++
                        times += time
                        return real.brush
                    }

                override fun float(name: String, v: Float) = real.float(name, v)

                override fun float2(name: String, x: Float, y: Float) = real.float2(name, x, y)

                override fun float3(name: String, x: Float, y: Float, z: Float) = real.float3(name, x, y, z)

                override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) =
                    real.float4(name, x, y, z, w)
            }
    }

    @Test
    fun `B-KIT-LIVE-020 every shader look compiles with the shared prelude`() {
        for (id in LiveLooks.offered) {
            val look = LiveLooks.shader(id) ?: continue
            assertNotNull(DesktopLiveShader.compile(LiveShaderLib.PRELUDE + look.shader), "$id does not compile")
        }
        assertNotNull(DesktopLiveShader.compile(LiveShaderLib.PROBE))
    }

    private fun appearance(motion: MotionLevel, look: String = "mesh") =
        KitAppearance(mode = AppearanceMode.Dark, motion = motion, liveBackground = true, liveStyle = look)

    @Test
    fun `B-KIT-LIVE-021 background frames redraw the background layer only and never recompose the page`() =
        runDesktopComposeUiTest(200, 400) {
            val platform = CountingPlatform()
            var pageCompositions = 0
            var pageDraws = 0
            setContent {
                KitTheme(appearance(MotionLevel.Full), platform, "en") {
                    KitLiveBackground(Modifier.fillMaxSize()) {
                        SideEffect { pageCompositions++ }
                        Box(Modifier.fillMaxSize().drawBehind { pageDraws++ })
                    }
                }
            }
            mainClock.autoAdvance = false
            val start = platform.draws
            val pageDrawsBefore = pageDraws
            repeat(120) { mainClock.advanceTimeByFrame() } // two seconds at 60 Hz
            val frames = platform.draws - start
            assertTrue(frames in 40..62, "drew $frames background frames in two seconds (30 fps cap)")
            assertTrue(platform.times.last() > platform.times.first(), "time moved")
            assertEquals(1, pageCompositions, "the page above the background never recomposed")
            assertEquals(pageDrawsBefore, pageDraws, "the page above the background was never redrawn")
        }

    @Test
    fun `B-KIT-LIVE-022 with motion Off the look holds one still frame`() =
        runDesktopComposeUiTest(200, 400) {
            val platform = CountingPlatform()
            setContent {
                KitTheme(appearance(MotionLevel.Off), platform, "en") {
                    KitLiveBackground(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                }
            }
            mainClock.autoAdvance = false
            repeat(5) { mainClock.advanceTimeByFrame() }
            val drawn = platform.draws
            assertTrue(drawn >= 1, "the still frame is drawn")
            repeat(120) { mainClock.advanceTimeByFrame() }
            assertEquals(drawn, platform.draws, "no further frames")
            assertTrue(platform.times.all { it == STILL_SECONDS })
        }

    @Test
    fun `B-KIT-LIVE-023 Background off draws no shader at all`() =
        runDesktopComposeUiTest(200, 400) {
            val platform = CountingPlatform()
            setContent {
                KitTheme(appearance(MotionLevel.Full).copy(liveBackground = false), platform, "en") {
                    KitLiveBackground(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                }
            }
            mainClock.autoAdvance = false
            repeat(30) { mainClock.advanceTimeByFrame() }
            assertEquals(0, platform.draws)
        }
}
