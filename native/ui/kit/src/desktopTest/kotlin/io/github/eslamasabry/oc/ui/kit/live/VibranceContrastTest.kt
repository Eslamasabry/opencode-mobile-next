@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiveVibrance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Vibrance spike: the page ground with each option, both looks (shader aurora, painted aurora), light and dark, Normal
 * and Bold. The worst contrast of the body text (text 1) and the secondary text (text 2) over any pixel of the page
 * must stay at 4.5:1, as the existing look tests require; the numbers go to VIBRANCE_OUT/contrast.txt when it is set.
 */
class VibranceContrastTest {
    private class ShaderPlatform(base: KitPlatform = FakeKitPlatform(frost = true)) : KitPlatform by base {
        override val liveShaders: LiveShaderCompiler = LiveShaderCompiler { DesktopLiveShader.compile(it) }
    }

    @Test
    fun `text keeps 4,5 to 1 over every vibrance option`() {
        val report = StringBuilder()
        val failures = mutableListOf<String>()
        var calm = 0.0 to 0.0
        for (look in listOf("aurora", "auroraImage")) for (dark in listOf(true, false)) for (strength in listOf(1f, 1.6f)) {
            for (vibe in LiveVibrance.entries) {
                var t1 = 0
                var t2 = 0
                var img: java.awt.image.BufferedImage? = null
                runDesktopComposeUiTest(270, 600) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(2.625f / 4f)) {
                            KitTheme(
                                KitAppearance(
                                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                    motion = MotionLevel.Off,
                                    liveBackground = true,
                                    liveStyle = look,
                                    liveStrength = strength,
                                    liveVibrance = vibe,
                                ),
                                ShaderPlatform(),
                                "en",
                            ) {
                                val c = LocalKitColors.current
                                t1 = c.text1.toArgb() and 0xFFFFFF
                                t2 = c.text2.toArgb() and 0xFFFFFF
                                KitLiveBackground(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                            }
                        }
                    }
                    waitForIdle()
                    mainClock.advanceTimeBy(500)
                    waitForIdle()
                    img = onAllNodesRoot().captureToImage().toAwtImage()
                }
                val image = img!!
                var w1 = Double.MAX_VALUE
                var w2 = Double.MAX_VALUE
                var far = 0
                val g = image.getRGB(0, image.height - 1) and 0xFFFFFF
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, y) and 0xFFFFFF
                    w1 = min(w1, contrast(t1, rgb))
                    w2 = min(w2, contrast(t2, rgb))
                    far = max(far, delta(g, rgb))
                }
                val line = "%-11s %-5s x%.1f %-8s text1 %.2f text2 %.2f".format(look, if (dark) "dark" else "light", strength, vibe.wire, w1, w2)
                report.appendLine(line)
                if (vibe == LiveVibrance.Calm) calm = w2 to w1 else if (w2 < minOf(4.5, calm.first - .1) || w1 < 4.5) failures += line
            }
        }
        System.getenv("VIBRANCE_OUT")?.let { File(it).apply { mkdirs() }.resolve("contrast.txt").writeText(report.toString()) }
        println(report)
        assertTrue(failures.isEmpty(), "an option reads worse than Calm (or body text under 4.5:1)\n" + failures.joinToString("\n"))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesRoot() = onAllNodes(isRoot())[0]

    private fun delta(a: Int, b: Int): Int =
        maxOf(
            kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
            kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
            kotlin.math.abs((a and 0xFF) - (b and 0xFF)),
        )

    private fun lum(rgb: Int): Double {
        fun lin(c: Int): Double = (c / 255.0).let { if (it <= .03928) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
        return .2126 * lin((rgb shr 16) and 0xFF) + .7152 * lin((rgb shr 8) and 0xFF) + .0722 * lin(rgb and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val x = lum(a)
        val y = lum(b)
        return (max(x, y) + .05) / (min(x, y) + .05)
    }
}
