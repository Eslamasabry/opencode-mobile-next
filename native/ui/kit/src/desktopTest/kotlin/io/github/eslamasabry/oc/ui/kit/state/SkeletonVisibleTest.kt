@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-11-14 (Glass, Dark, Contour lines; a saved conversation took ten seconds to open): "I didn't have any
 * indication that it was loading". The placeholder lines were `surface3`, dark grey on a near-black ground. Now, in
 * every style and both modes, over the Contour lines and the painted Aurora, a placeholder line's pixels stand off the
 * ground beside it by the kit's non-text floor (3:1).
 */
class SkeletonVisibleTest {
    private val styles = listOf(Style.Solid, Style.Glass, Style.Soft, Style.Brutal, Style.Paper)
    private val looks = listOf("topo", AuroraImage.ID)

    @Test
    fun `LOAD-1 the placeholder lines read over the ground and the live background in every style and mode`() {
        val weak = mutableListOf<String>()
        for (style in styles) for (dark in listOf(false, true)) for (look in looks) {
            val appearance =
                KitAppearance(
                    style = style,
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    liveBackground = true,
                    liveStyle = look,
                )
            val contrast = lineContrast(appearance)
            if (contrast < FLOOR) weak += "$style ${if (dark) "dark" else "light"} $look: ${"%.2f".format(contrast)}"
        }
        assertTrue(weak.isEmpty(), "placeholder lines under 3:1 against the ground beside them: $weak")
    }

    @Test
    fun `LOAD-2 the flat ground too, with no live background`() {
        for (style in styles) for (dark in listOf(false, true)) {
            val appearance = KitAppearance(style = style, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light)
            val contrast = lineContrast(appearance)
            assertTrue(contrast >= FLOOR, "$style dark=$dark: ${"%.2f".format(contrast)}")
        }
    }

    /**
     * One placeholder turn laid out from the top (an unbounded column). Its first reply line spans 94 % of the column,
     * its third 62 %: the line's middle is read against the ground to the right of the third line, on that line's row.
     */
    private fun lineContrast(appearance: KitAppearance): Double {
        var gutter = 0
        lateinit var image: PixelMap
        runDesktopComposeUiTest(W, H) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(appearance, DesktopKitPlatform(), "en", stillFrame = true) {
                        gutter = LocalKitTokens.current.gutter
                        KitLiveBackground(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                KitSkeletonTranscript(Modifier.fillMaxWidth(), turns = 1)
                            }
                        }
                    }
                }
            }
            waitForIdle()
            image = onRoot().captureToImage().toPixelMap()
        }
        val inner = W - 2 * gutter
        val firstRow = gutter + Space.S6 + Space.S3 + Space.S4
        fun rowMiddle(i: Int) = firstRow + i * 2 * Space.S2 + Space.S2 / 2
        val ink = pixel(image, gutter + (inner * 0.47f).roundToInt(), rowMiddle(0))
        val groundX = gutter + (inner * 0.82f).roundToInt()
        val ground = mean(image, groundX - 6, groundX + 6, rowMiddle(2) - 2, rowMiddle(2) + 2)
        return wcagContrast(ink, ground)
    }

    private fun pixel(image: PixelMap, x: Int, y: Int): Argb = Argb(image[x, y].toArgb() or OPAQUE)

    private fun mean(image: PixelMap, x0: Int, x1: Int, y0: Int, y1: Int): Argb {
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (y in y0..y1) for (x in x0..x1) {
            val c = pixel(image, x, y)
            r += c.red
            g += c.green
            b += c.blue
            n++
        }
        return Argb(OPAQUE or ((r / n) shl 16) or ((g / n) shl 8) or (b / n))
    }

    private companion object {
        const val W = 360
        const val H = 400
        const val FLOOR = 3.0
        const val OPAQUE = -0x1000000 // 0xFF000000
    }
}
