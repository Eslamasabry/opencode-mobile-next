@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The owner's report (2026-11-09, "that's not live liquid glass"): glass showed the previous background after the
 * background changed. A Glass pane's frost is made from the live picture as it is now: the shared blurred copy
 * (smoothness 2026-11-09) and the one picture pages share (smoothness round 2) are never kept across a change of the
 * picture, whether its look, its colours or its time moved.
 */
class KitGlassLiveCaptureTest {
    private fun centre(tag: String, test: androidx.compose.ui.test.ComposeUiTest): Color {
        val px = test.onNodeWithTag(tag).captureToImage().toPixelMap()
        return px[px.width / 2, px.height / 2]
    }

    /** The pane shows [expected] through its frost and tint: the same channel leads, clearly. */
    private fun assertShows(expected: Color, actual: Color, what: String) {
        fun lead(c: Color) = listOf(c.red, c.green, c.blue).withIndex().maxBy { it.value }
        val e = lead(expected)
        val a = lead(actual)
        val others = listOf(actual.red, actual.green, actual.blue).filterIndexed { i, _ -> i != a.index }
        assertTrue(a.index == e.index && a.value - others.max() > .25f, "$what: shows $actual, expected the hue of $expected")
    }

    @Test
    fun `B-KIT-018 a Glass pane's frost follows the picture behind it when the picture changes`() {
        for (shared in listOf(false, true)) {
            runComposeUiTest {
                var colour by mutableStateOf(Color(0xFFFF2200))
                setContent {
                    KitTheme(KitAppearance(style = Style.Glass), FakeKitPlatform(frost = true), "en", stillFrame = true) {
                        val pictures = remember { if (shared) KitGlassPictures() else null }
                        // Two pages of one size showing one picture (a push or a pop), or one page with its own.
                        Box(Modifier.size(320.dp, 480.dp)) {
                            repeat(if (shared) 2 else 1) { page ->
                                val backdrop = remember { KitGlassBackdrop().also { it.painted = true } }
                                Box(Modifier.fillMaxSize().kitGlassBackdropBoundary()) {
                                    // The paint reads its colour as the live background reads its clock: in the draw.
                                    Box(Modifier.fillMaxSize().kitGlassBackdropSource(backdrop, pictures) { drawRect(colour) })
                                    CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop) {
                                        Box(
                                            Modifier.padding(start = 16.dp + (page * 150).dp, top = 40.dp)
                                                .size(120.dp, 60.dp)
                                                .kitSurfaceGlass(SurfaceDecoration(frost = 12f))
                                                .testTag("pane$page")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                val pages = if (shared) 2 else 1
                for (p in 0 until pages) assertShows(Color(0xFFFF2200), centre("pane$p", this), "shared=$shared pane $p before")
                for (next in listOf(Color(0xFF0044FF), Color(0xFF22DD00), Color(0xFFFF2200))) {
                    colour = next
                    waitForIdle()
                    for (p in 0 until pages) assertShows(next, centre("pane$p", this), "shared=$shared pane $p after the picture changed")
                }
            }
        }
    }

    @Test
    fun `B-KIT-018 a page of another size never shows a shared picture of the wrong size`() = runComposeUiTest {
        lateinit var pictures: KitGlassPictures
        setContent {
            KitTheme(KitAppearance(style = Style.Glass), FakeKitPlatform(frost = true), "en", stillFrame = true) {
                pictures = remember { KitGlassPictures() }
                Box {
                    for ((i, w) in listOf(320, 200).withIndex()) {
                        val backdrop = remember { KitGlassBackdrop().also { it.painted = true } }
                        Box(Modifier.size(w.dp, 300.dp).kitGlassBackdropBoundary()) {
                            Box(Modifier.fillMaxSize().kitGlassBackdropSource(backdrop, pictures) { drawRect(Color(0xFF0044FF)) })
                            CompositionLocalProvider(LocalKitGlassBackdrop provides backdrop) {
                                Box(Modifier.padding(16.dp).size(100.dp, 60.dp).kitSurfaceGlass(SurfaceDecoration(frost = 12f)).testTag("pane$i"))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(2, pictures.count, "one picture per page size")
        assertShows(Color(0xFF0044FF), centre("pane0", this), "the large page")
        assertShows(Color(0xFF0044FF), centre("pane1", this), "the small page")
    }
}
