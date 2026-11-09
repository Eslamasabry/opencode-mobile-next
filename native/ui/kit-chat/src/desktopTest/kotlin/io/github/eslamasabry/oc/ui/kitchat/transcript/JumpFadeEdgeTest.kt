@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Owner 2026-11-09 (phone, native/next bc46d2aea, Glass with liquid glass, dark): "There is a rough line showing
 * here... just below the Jump to latest". Scrolled away from the newest message, the transcript's soft fade above the
 * composer ended at its strongest where the composer's room begins; under liquid glass nothing lay below it, so the
 * transcript came back at full brightness in one pixel row. Guard: a chat scrolled away, its words a bright even
 * block; down the side column the brightness never jumps from one row to the next.
 */
class JumpFadeEdgeTest {
    private val rows = (0 until 80).map { "Row $it" }

    /** Desktop with the lens, so Glass is liquid as on Android 13+. */
    private class LensPlatform : KitPlatform by DesktopKitPlatform() {
        override val liquidGlass: Boolean = true
    }

    private fun render(appearance: KitAppearance, platform: KitPlatform): PixelMap {
        lateinit var image: PixelMap
        runDesktopComposeUiTest((W * DENSITY).toInt(), (H * DENSITY).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                    KitTheme(appearance = appearance, platform = platform, localeTag = "en", stillFrame = true) {
                        Box(Modifier.size(W.dp, H.dp).testTag("scene")) {
                            val source = rememberKitFrostState()
                            val state = rememberTranscriptListState()
                            KitScreen(
                                page = true,
                                overlayChrome = true,
                                frost = source,
                                bottom = { Box(Modifier.fillMaxWidth().height(96.dp)) },
                                edges = KitScreenEdges.Bleed,
                            ) {
                                Box(Modifier.fillMaxSize().kitFrostSource(source)) {
                                    KitTranscriptList(state, rows.first(), "Jump to latest", KitMotionLevel.Off) {
                                        // An even bright block, so only an edge drawn over it changes a column.
                                        items(rows, key = { it }) { Box(Modifier.fillMaxWidth().height(40.dp).background(Color.White)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("transcript-list").performScrollToIndex(40)
            waitForIdle()
            onNodeWithTag("jump-to-latest").assertExists()
            image = onNodeWithTag("scene").captureToImage().toPixelMap()
        }
        return image
    }

    private fun luma(c: Color): Float = (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue) * 255f

    private fun assertBlends(appearance: KitAppearance, platform: KitPlatform, what: String) {
        val image = render(appearance, platform)
        val x = (2 * DENSITY).toInt()
        var worst = 0f
        var at = -1
        for (y in 0 until image.height - 1) {
            val d = abs(luma(image[x, y + 1]) - luma(image[x, y]))
            if (d > worst) {
                worst = d
                at = y
            }
        }
        assertTrue(worst <= STEP_LIMIT, "$what: a hard edge at y=${at / DENSITY} dp: brightness jumps $worst of 255 in one row")
    }

    @Test
    fun `E20-S30 scrolled away under liquid glass the fade above the composer has no edge`() {
        for (dark in listOf(true, false)) {
            val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
            assertBlends(KitAppearance(style = Style.Glass, mode = mode), LensPlatform(), "liquid Glass dark=$dark")
        }
    }

    @Test
    fun `E20-S30 scrolled away in every style the fade above the composer has no edge`() {
        for (style in Style.entries) for (dark in listOf(true, false)) {
            val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
            assertBlends(KitAppearance(style = style, mode = mode), DesktopKitPlatform(), "$style dark=$dark")
        }
    }

    private companion object {
        const val W = 240
        const val H = 480
        const val DENSITY = 2f

        /** Of 255, per pixel row at 2x: a smooth 16 dp edge over this block changes at most ~12, a cut 140 or more. */
        const val STEP_LIMIT = 24f
    }
}
