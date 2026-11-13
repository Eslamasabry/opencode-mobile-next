@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A page's tab row under its bar (coordinator 2026-11-13, the Appearance page's tabs): where the bar does not float
 * (Solid and the other styles, Glass without the lens) the floating bar's second row used to scroll away with the page
 * as the scrolling header's first part. That is right for the home's chips row and wrong for a page's tabs.
 * [KitFloatingBar.belowPinned] keeps it pinned under the bar; the default keeps the home's behaviour.
 */
class KitFloatingBarPinnedBelowTest {
    /** The second row's bounds at rest and after three drags up the body (null: not laid out any more). */
    private fun rowBefore(style: Style, platform: KitPlatform, pinned: Boolean): Pair<DpRect?, DpRect?> {
        var rest: DpRect? = null
        var scrolled: DpRect? = null
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = KitAppearance(style = style, mode = AppearanceMode.Dark), platform = platform, localeTag = "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 16.dp)) {
                            Box(Modifier.size(W.dp, H.dp)) {
                                KitScreen(
                                    page = true,
                                    topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) { KitText("Appearance") } },
                                    floatingBar =
                                        KitFloatingBar(
                                            below = { Box(Modifier.fillMaxWidth().height(40.dp).testTag("tabs")) { KitText("Style") } },
                                            belowPinned = pinned,
                                        ),
                                    edges = KitScreenEdges.Bleed,
                                ) {
                                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                                        items(60) { i -> Box(Modifier.fillMaxWidth().height(48.dp).background(Color.White).testTag("row-$i")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            rest = runCatching { onNodeWithTag("tabs", useUnmergedTree = true).getBoundsInRoot() }.getOrNull()
            // A finger's drag, so the scroll reaches the page's scrolling header as it does on a phone.
            repeat(3) {
                onNodeWithTag("list").performTouchInput { swipeUp(startY = bottom * .8f, endY = top + height * .2f, durationMillis = 400) }
                waitForIdle()
            }
            scrolled = runCatching { onNodeWithTag("tabs", useUnmergedTree = true).getBoundsInRoot() }.getOrNull()
        }
        return rest to scrolled
    }

    private fun stays(r: Pair<DpRect?, DpRect?>): Boolean {
        val (a, b) = r
        return a != null && b != null && abs(a.top.value - b.top.value) < .5f && abs(a.bottom.value - b.bottom.value) < .5f
    }

    @Test
    fun `pinned second row - under Solid a page's tab row stays under the bar while the body scrolls`() {
        val r = rowBefore(Style.Solid, FakeKitPlatform(), pinned = true)
        assertTrue(stays(r), "the tab row moved or left: at rest ${r.first}, scrolled ${r.second}")
    }

    @Test
    fun `pinned second row - under Soft, Brutal and Paper a page's tab row stays under the bar while the body scrolls`() {
        for (style in listOf(Style.Soft, Style.Brutal, Style.Paper)) {
            val r = rowBefore(style, FakeKitPlatform(), pinned = true)
            assertTrue(stays(r), "$style: the tab row moved or left: at rest ${r.first}, scrolled ${r.second}")
        }
    }

    @Test
    fun `pinned second row - under Glass without the lens a page's tab row stays under the bar while the body scrolls`() {
        val r = rowBefore(Style.Glass, FakeKitPlatform(frost = true, liquidGlass = false), pinned = true)
        assertTrue(stays(r), "the tab row moved or left: at rest ${r.first}, scrolled ${r.second}")
    }

    @Test
    fun `pinned second row - by default under Solid the row scrolls away with the page, as the home's chips do`() {
        val r = rowBefore(Style.Solid, FakeKitPlatform(), pinned = false)
        assertTrue(r.first != null, "the row is laid out at rest")
        assertTrue(!stays(r), "the home's row stayed: at rest ${r.first}, scrolled ${r.second}")
    }

    @Test
    fun `pinned second row - under Glass with the lens the row is in the bar's pane either way`() {
        for (pinned in listOf(true, false)) {
            val r = rowBefore(Style.Glass, FakeKitPlatform(frost = true, liquidGlass = true), pinned = pinned)
            assertTrue(stays(r), "pinned=$pinned: the row moved in Glass: at rest ${r.first}, scrolled ${r.second}")
        }
    }

    private companion object {
        const val D = 2.625f
        const val W = 411f
        const val H = 760f
    }
}
