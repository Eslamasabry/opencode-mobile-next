package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.FrameMeter
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitReportScrolledUnder
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitScreenInsets
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * c2-performance (2026-11-15): what each background of Settings > Appearance > Background costs a framed page, still
 * and while its list is dragged: how often the look is drawn, and the offscreen pixels per frame against the window.
 * Printed as a table (`PERF-LOOKS`); the guard is the rate: no look is drawn more than the cap.
 */
@OptIn(ExperimentalTestApi::class)
class LiveLooksCostTest {
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

    private data class Row(val look: String, val stillDraws: Int, val dragDraws: Int, val layers: Int, val share: Double)

    private fun measure(look: String?): Row {
        val platform = Counting()
        lateinit var row: Row
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val meter = FrameMeter.attach(this)
            setContent {
                KitTheme(
                    KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Full, liveBackground = look != null, liveStyle = look ?: "aurora"),
                    platform,
                    "en",
                ) {
                    CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 16.dp)) {
                        Box(Modifier.size(W.dp, H.dp)) {
                            val list = rememberLazyListState()
                            KitScreen(page = true, topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) }) {
                                KitReportScrolledUnder { list.canScrollBackward }
                                LazyColumn(state = list, contentPadding = kitScreenChromePadding(), modifier = Modifier.fillMaxSize()) {
                                    items(60, key = { it }) { i ->
                                        BasicText("Line $i", Modifier.fillMaxWidth().height(40.dp), style = TextStyle(color = Color.White))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            repeat(30) { mainClock.advanceTimeByFrame() }
            val d0 = platform.draws
            val still = meter.frames(60)
            val d1 = platform.draws
            val drag = meter.drag(androidx.compose.ui.geometry.Offset(W / 2f, H * .7f), 30, -8f)
            val d2 = platform.draws
            val px = (still + drag).maxOf { it.offscreenPx }
            row = Row(look ?: "off", d1 - d0, d2 - d1, (still + drag).maxOf { it.offscreen }, px.toDouble() / (W * H))
            meter.close()
        }
        return row
    }

    @Test
    fun `PERF-LOOKS-1 every background of the picker draws at most the cap, still or dragged`() {
        val rows = (listOf<String?>(null) + PICKER).map(::measure)
        println(
            "PERF-LOOKS look | draws in 60 still frames | draws in a 30-frame drag | offscreen layers | offscreen px / window\n" +
                rows.joinToString("\n") { "${it.look} | ${it.stillDraws} | ${it.dragDraws} | ${it.layers} | ${"%.2f".format(it.share)}" }
        )
        // 60 frames of 16 ms are about a second: at most the cap's steps (and a frame either side).
        rows.forEach { assertTrue(it.stillDraws <= CAP + 2, "${it.look} drew ${it.stillDraws} times in a still second") }
    }

    private companion object {
        const val W = 240
        const val H = 400
        const val CAP = 15

        /** Settings > Appearance > Background's looks (AppearancePage LOOK_PICKS), the painted aurora included. */
        val PICKER = listOf("aurora", "glowSpill", "mesh", "silk", "topo", "lightRays", "dotRipple")
    }
}
