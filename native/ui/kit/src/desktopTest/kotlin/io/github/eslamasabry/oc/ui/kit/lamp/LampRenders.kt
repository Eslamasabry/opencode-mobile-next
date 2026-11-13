@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.lamp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures of the edge light for the coordinator to judge, at the phone's 2.625 px/dp. Not a test: it runs only when
 * `LAMP_RENDER_OUT` names a folder, and writes there:
 * - `lamp-rest-{style}-{dark|light}.png`: the quiet tab, a counted face (1 failed, a run at 40 %, a run without a task
 *   list) and a face with someone needing you (amber number), on the right edge over a few lines of reply text;
 *   `lamp-rest-solid-dark-rtl.png` the same in Arabic (the left edge);
 * - `lamp-halo-{style}-{1..4}.png`: a request arriving, 0 / 60 / 125 / 200 ms into the halo (dark);
 * - `lamp-grow-{style}-{1..4}.png`: the pane growing out of the light, at 2 frames / 60 / 120 ms and settled (dark).
 */
class LampRenders {
    private val styles = listOf(Style.Solid, Style.Glass, Style.Soft, Style.Brutal, Style.Paper)

    private fun ComposeUiTest.theme(style: Style, dark: Boolean, locale: String = "en", content: @Composable () -> Unit) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(DENSITY)) {
                KitTheme(
                    KitAppearance(mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light, style = style, motion = MotionLevel.Full),
                    FakeKitPlatform(),
                    locale,
                ) {
                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground.copy(alpha = 1f))) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            repeat(LINES) { KitText(PAGE_WORDS, role = TypeRole.Body, maxLines = 1) }
                        }
                        content()
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.shoot(out: File, name: String) {
        ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", File(out, name))
    }

    @Test
    fun `lamp renders`() {
        val out = System.getenv("LAMP_RENDER_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (style in styles) for (dark in listOf(true, false)) rest(out, style, dark, rtl = false)
        rest(out, Style.Solid, dark = true, rtl = true)
        for (style in styles) halo(out, style)
        for (style in styles) grow(out, style)
    }

    private fun rest(out: File, style: Style, dark: Boolean, rtl: Boolean) =
        runDesktopComposeUiTest(W, H) {
            theme(style, dark, if (rtl) "ar" else "en") {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.End) {
                    Spacer(Modifier.height(48.dp))
                    KitLamp(KitLampFace(), "Other conversations", {})
                    Spacer(Modifier.height(24.dp))
                    KitLamp(COUNTED, "Other conversations", {})
                    Spacer(Modifier.height(24.dp))
                    KitLamp(NEEDS, "Other conversations", {})
                    Spacer(Modifier.height(24.dp))
                    KitLamp(NEEDS.copy(open = true), "Close conversations", {})
                }
            }
            waitForIdle()
            shoot(out, "lamp-rest-${style.storageName}-${if (dark) "dark" else "light"}${if (rtl) "-rtl" else ""}.png")
        }

    private fun halo(out: File, style: Style) =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            var face by mutableStateOf(COUNTED)
            theme(style, dark = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) { KitLamp(face, "Other conversations", {}) }
            }
            mainClock.advanceTimeBy(500)
            face = NEEDS
            // The arrival's frame, then the halo's start, 60 ms, 125 ms and 200 ms in.
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            shoot(out, "lamp-halo-${style.storageName}-1.png")
            mainClock.advanceTimeBy(60)
            shoot(out, "lamp-halo-${style.storageName}-2.png")
            mainClock.advanceTimeBy(65)
            shoot(out, "lamp-halo-${style.storageName}-3.png")
            mainClock.advanceTimeBy(75)
            shoot(out, "lamp-halo-${style.storageName}-4.png")
        }

    private fun grow(out: File, style: Style) =
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            var open by mutableStateOf(false)
            theme(style, dark = true) {
                Box(Modifier.fillMaxSize()) {
                    // The pane: 8 dp from the far edge, 16 dp from the light's edge; the light below its end.
                    Box(Modifier.fillMaxSize().padding(start = 8.dp, end = 16.dp, top = 40.dp, bottom = 24.dp)) {
                        KitGrowFromAnchor(open, { anchorPx() }, { open = false }, Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize().padding(16.dp)) {
                                KitText("Conversations", role = TypeRole.Title)
                                repeat(4) { KitText("create a file hello.txt with one line", role = TypeRole.Secondary, maxLines = 1) }
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize().padding(bottom = LIGHT_BOTTOM_DP.dp), contentAlignment = Alignment.BottomEnd) {
                        KitLamp(COUNTED.copy(open = open), "Other conversations", { open = !open })
                    }
                }
            }
            mainClock.advanceTimeBy(300)
            open = true
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            shoot(out, "lamp-grow-${style.storageName}-1.png")
            mainClock.advanceTimeBy(60)
            shoot(out, "lamp-grow-${style.storageName}-2.png")
            mainClock.advanceTimeBy(60)
            shoot(out, "lamp-grow-${style.storageName}-3.png")
            mainClock.advanceTimeBy(800)
            shoot(out, "lamp-grow-${style.storageName}-4.png")
        }

    /** The strip at the pane's end edge beside the light, the light's height (window px). */
    private fun anchorPx(): Rect {
        val d = DENSITY
        val right = (W_DP - 16f) * d
        val bottom = (H_DP - LIGHT_BOTTOM_DP - KitLampDims.TOUCH_PAD) * d
        val top = bottom - KitLampDims.OPEN_H * d
        return Rect(right - KitLampDims.FACE_W * d, top, right, bottom)
    }

    private companion object {
        const val DENSITY = 2.625f
        const val W_DP = 200f
        const val H_DP = 420f
        val W = (W_DP * DENSITY).toInt()
        val H = (H_DP * DENSITY).toInt()
        const val LIGHT_BOTTOM_DP = 60
        const val LINES = 14
        const val PAGE_WORDS = "Oxpeckers and rhinos. The birds eat ticks and shriek when danger comes."
        val COUNTED =
            KitLampFace(
                1,
                listOf(
                    KitLampMark("f", KitLampMarkKind.Failed),
                    KitLampMark("w", KitLampMarkKind.Working, 0.4f),
                    KitLampMark("u", KitLampMarkKind.Working, null),
                ),
            )
        val NEEDS = KitLampFace(2, COUNTED.marks + KitLampMark("n", KitLampMarkKind.NeedsYou))
    }
}
