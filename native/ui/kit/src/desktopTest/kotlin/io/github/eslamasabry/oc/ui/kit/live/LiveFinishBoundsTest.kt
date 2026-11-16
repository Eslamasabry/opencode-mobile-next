package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntOffset
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopLiveShader
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Black-frame, the cause (Galaxy S22 build 10056, 2026-11-16): Home → + and Close with Silk showed 1 to 6 black
 * frames; with Background Off, none. A "field" look (Silk, Mesh, …) is drawn at reduced resolution and finished at
 * full resolution by a runtime-shader render effect (`LiveShaderLib.FINISH`), which writes an opaque pixel everywhere
 * it is evaluated. Nothing bounded it to its page, so a page sliding up (or down) painted its finished background,
 * near black, over everything under it on the screen: the covered page was gone. A page's live background stays
 * inside the page.
 */
@OptIn(ExperimentalTestApi::class)
class LiveFinishBoundsTest {
    private val platform = FakeKitPlatform(frost = true, liveShaders = { source -> DesktopLiveShader.compile(source) })

    private fun appearance(style: Style) =
        KitAppearance(
            style = style,
            mode = AppearanceMode.Dark,
            motion = MotionLevel.Full,
            liveBackground = true,
            liveStyle = "silk",
        )

    @Test
    fun `B-KIT-LIVE-057 a page sliding over another never paints its live background outside itself`() {
        for (style in listOf(Style.Solid, Style.Glass)) {
            for (shared in listOf(false, true)) {
                runDesktopComposeUiTest(200, 400) {
                    setContent {
                        KitTheme(appearance(style), platform, "en") {
                            Box(Modifier.fillMaxSize()) {
                                // The covered page: plain red, so any background painted over it shows.
                                Box(Modifier.fillMaxSize().background(Color.Red))
                                // The arriving page, half way up its slide.
                                Box(Modifier.fillMaxSize().offset { IntOffset(0, 240) }) {
                                    KitLiveBackground(Modifier.fillMaxSize(), shared = shared) {}
                                }
                            }
                        }
                    }
                    waitForIdle()
                    val px = onRoot().captureToImage().toPixelMap()
                    for (y in listOf(10, 100, 200)) {
                        val c = px[100, y]
                        assertTrue(
                            c.red > .9f && c.green < .1f && c.blue < .1f,
                            "$style shared=$shared: the covered page at y=$y shows $c, not its own red",
                        )
                    }
                    // The arriving page does draw its background where it lies.
                    val inside = px[100, 320]
                    assertTrue(inside.red < .5f, "$style shared=$shared: its own background is gone ($inside)")
                }
            }
        }
    }
}
