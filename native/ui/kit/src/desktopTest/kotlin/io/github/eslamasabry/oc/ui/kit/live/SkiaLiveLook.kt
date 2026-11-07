@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface

/**
 * A look compiled the way the host compiles it ([LiveShaderLib.PRELUDE] + the look's text, Skia's runtime effect: the
 * same language Android runs as AGSL), with every uniform set through the host's own [setFrame]. Test-only: the look
 * tests read its pixels.
 */
internal class SkiaLiveLook(val look: LiveLook) {
    private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(LiveShaderLib.PRELUDE + look.shader))

    /** Names of every uniform set during the last [pixels] call, shared and the look's own. */
    val set = mutableSetOf<String>()

    private val sink =
        object : LiveShaderProgram() {
            override val brush: Brush
                get() = error("not drawn through Compose")

            override fun float(name: String, v: Float) {
                set += name
                builder.uniform(name, v)
            }

            override fun float2(name: String, x: Float, y: Float) {
                set += name
                builder.uniform(name, x, y)
            }

            override fun float3(name: String, x: Float, y: Float, z: Float) {
                set += name
                builder.uniform(name, x, y, z)
            }

            override fun float4(name: String, x: Float, y: Float, z: Float, w: Float) {
                set += name
                builder.uniform(name, x, y, z, w)
            }
        }

    /** One moment of the clock at an intensity and pixels per dp. */
    data class Moment(val time: Float, val strength: Float = 1f, val density: Float = 1f)

    /** One frame, [w]×[h] pixels, as packed RGB (alpha dropped: every look is opaque). */
    fun pixels(w: Int, h: Int, palette: LivePalette, at: Moment): IntArray {
        set.clear()
        sink.setSize(w.toFloat(), h.toFloat(), at.density)
        sink.setFrame(look, at.time, at.strength, palette)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), Paint().apply { shader = builder.makeShader() })
        val bmp = Bitmap()
        bmp.allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
        surface.readPixels(bmp, 0, 0)
        val bytes = requireNotNull(bmp.readPixels())
        return IntArray(w * h) { i ->
            val o = i * 4
            (bytes[o].u() shl 16) or (bytes[o + 1].u() shl 8) or bytes[o + 2].u()
        }
    }

    private fun Byte.u(): Int = toInt() and 0xFF

    companion object {
        private val cache = mutableMapOf<Boolean, KitColors>()

        /** The real kit colours of the default theme, dark or light. */
        fun kitColors(dark: Boolean): KitColors =
            cache.getOrPut(dark) {
                var colors: KitColors? = null
                runDesktopComposeUiTest(10, 10) {
                    setContent {
                        KitTheme(
                            KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light),
                            FakeKitPlatform(),
                            "en",
                            stillFrame = true,
                        ) { colors = LocalKitColors.current }
                    }
                    waitForIdle()
                }
                requireNotNull(colors)
            }

        /** The look colours the host would use for the default theme. */
        fun palette(dark: Boolean): LivePalette = livePaletteOf(kitColors(dark))
    }
}
