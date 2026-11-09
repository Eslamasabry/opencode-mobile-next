@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-11-09 (phone, native/next bc46d2aea): "a rough line just below Jump to latest", "the same on top, that
 * dark gradient below the notifications bar", and in Solid "an opaque slab under the composer". The chat's chrome laid
 * bands of the page's ground over the transcript (the header's ground, the liquid edge fade, the composer's veil) whose
 * ends cut straight across a live background. Guard: a chat frame over a busy picture, its transcript running under the
 * bar and the composer, the system bars' insets around it; down each side column (clear of the chrome's own parts)
 * the brightness never jumps from one pixel row to the next, from the status bar to the navigation bar.
 */
class ChromeEdgeBlendTest {
    /** The page's live picture, stand-in: dark vertical stripes in two colours (busy across, even down a column). */
    private val pictureA = Color(0xFF7A2E8C)
    private val pictureB = Color(0xFF1E6B4A)

    /** The transcript, stand-in: bright vertical stripes (white and yellow). */
    private val wordsA = Color(0xFFFFFFFF)
    private val wordsB = Color(0xFFFFE066)

    @Composable
    private fun Scene() {
        Box(
            Modifier.size(W.dp, H.dp).testTag("scene").drawBehind {
                val stripe = 12.dp.toPx()
                var x = 0f
                var i = 0
                while (x < size.width) {
                    drawRect(if (i++ % 2 == 0) pictureA else pictureB, Offset(x, 0f), Size(stripe, size.height))
                    x += stripe
                }
            }
        ) {
            // The page's ground made clear, so the picture behind stands in for the person's live background (on the
            // phone, the aurora); the chrome's own colours are untouched.
            val colors = LocalKitColors.current
            CompositionLocalProvider(
                LocalKitColors provides colors.copy(ground = colors.ground.copy(alpha = 0f)),
                LocalKitScreenInsets provides WindowInsets(top = STATUS_DP.dp, bottom = NAV_DP.dp),
            ) {
                val source = rememberKitFrostState()
                KitScreen(
                    page = true,
                    overlayChrome = true,
                    frost = source,
                    topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                    bottom = { Box(Modifier.fillMaxWidth().height(96.dp)) },
                    edges = KitScreenEdges.Bleed,
                ) {
                    Box(
                        Modifier.fillMaxSize().kitFrostSource(source).drawBehind {
                            val stripe = 20.dp.toPx()
                            var x = 0f
                            var i = 0
                            while (x < size.width) {
                                drawRect(if (i++ % 2 == 0) wordsA else wordsB, Offset(x, 0f), Size(stripe, size.height))
                                x += stripe
                            }
                        }
                    )
                }
            }
        }
    }

    private fun render(appearance: KitAppearance, platform: FakeKitPlatform): PixelMap {
        lateinit var image: PixelMap
        runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) { Scene() }
                }
            }
            waitForIdle()
            image = onNodeWithTag("scene").captureToImage().toPixelMap()
        }
        return image
    }

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    /** The biggest brightness change between two neighbouring rows down column [x], and the row where it happens. */
    private fun steepest(image: PixelMap, x: Int): Pair<Float, Int> {
        var worst = 0f
        var at = -1
        for (y in 0 until image.height - 1) {
            val d = abs(luma(image[x, y + 1]) - luma(image[x, y]))
            if (d > worst) {
                worst = d
                at = y
            }
        }
        return worst to at
    }

    private fun assertBlends(appearance: KitAppearance, platform: FakeKitPlatform, what: String) {
        val image = render(appearance, platform)
        // Two columns 2 dp in from each side: clear of the bar's capsule (6 dp in) and the composer's gutter (16 dp).
        for (x in listOf((2 * DENSITY).toInt(), image.width - 1 - (2 * DENSITY).toInt())) {
            val (step, y) = steepest(image, x)
            assertTrue(
                step <= STEP_LIMIT,
                "$what: a hard edge at y=${y / DENSITY} dp (column ${x / DENSITY} dp): brightness jumps $step of 255 in one row",
            )
        }
    }

    private fun appearance(style: Style, dark: Boolean) =
        KitAppearance(style = style, mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light)

    @Test
    fun `E20-S30 liquid Glass, the owner's case, blends at the status bar, under the bar and above the composer`() {
        for (dark in listOf(true, false)) {
            assertBlends(appearance(Style.Glass, dark), FakeKitPlatform(frost = true, liquidGlass = true), "liquid Glass dark=$dark")
        }
    }

    @Test
    fun `E20-S30 every other style blends at both ends, with and without a platform blur`() {
        for (style in Style.entries) for (dark in listOf(true, false)) for (blur in listOf(true, false)) {
            assertBlends(appearance(style, dark), FakeKitPlatform(frost = blur), "$style dark=$dark blur=$blur")
        }
    }

    private companion object {
        const val W = 240
        const val H = 480
        const val DENSITY = 2f
        const val STATUS_DP = 24
        const val NAV_DP = 16

        /**
         * The most brightness may change from one pixel row to the next (of 255, at 2x). The scene's transcript is
         * about 190 brighter than its picture: a smooth 12 dp edge changes at most ~12 a row, a cut 140 or more.
         */
        const val STEP_LIMIT = 24f
    }
}
