@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A still-frame host (tests, screenshots) showed the plain ground where the painted aurora was chosen, so every
 * "over Aurora" picture of a glass pane tested nothing (PaneRimFloorTest: "the Aurora is not drawn"). The aurora is
 * drawn there too, at its still moment: the page has the aurora's light in it, and two pictures are the same pixels.
 */
class AuroraStillFrameTest {
    private fun shot(dark: Boolean): PixelMap {
        lateinit var out: PixelMap
        runDesktopComposeUiTest(360, 640) {
            setContent {
                KitTheme(
                    KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, style = Style.Glass, liveBackground = true, liveStyle = AuroraImage.ID),
                    FakeKitPlatform(frost = true),
                    "en",
                    stillFrame = true,
                ) { KitLiveBackground(Modifier.fillMaxSize()) {} }
            }
            waitForIdle()
            out = onAllNodes(isRoot())[0].captureToImage().toPixelMap()
        }
        return out
    }

    /**
     * How far apart any two points of the page are in colour (0-255, Euclidean), on a coarse grid: the flat ground is 0;
     * the dark plate at its still moment moves the ground's hue (teal to grey) more than its light, about 10-12.
     */
    private fun spread(p: PixelMap): Float {
        val px = (1..9).flatMap { r -> (1..5).map { c -> p[p.width * c / 6, p.height * r / 10] } }
        return px.maxOf { a -> px.maxOf { b -> kotlin.math.sqrt(((a.red - b.red) * (a.red - b.red) + (a.green - b.green) * (a.green - b.green) + (a.blue - b.blue) * (a.blue - b.blue)).toDouble()).toFloat() } } * 255f
    }

    private fun same(a: PixelMap, b: PixelMap): Boolean = (0 until a.height step 7).all { y -> (0 until a.width step 7).all { x -> a[x, y] == b[x, y] } }

    @Test
    fun `a still-frame host paints the chosen aurora, the same frame every time`() {
        val first = shot(dark = true)
        assertTrue(spread(first) >= 6f, "the aurora is drawn under a still frame: the page's colour spreads ${spread(first)} of 255")
        assertEquals(true, same(first, shot(dark = true)), "the still aurora is one fixed frame")
    }
}
