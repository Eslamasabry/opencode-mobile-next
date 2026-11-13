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
import androidx.compose.ui.test.performScrollToIndex
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
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Home's foot bar (All conversations and the round +): the list's rows were cut off at the bar's top edge. Under Glass
 * with the lens, where the top bar floats as a pane over the list, the bottom block is a pane too: the list runs on
 * under it (rows pass behind its glass), and the frame's chrome room keeps the last row whole above it at rest. Every
 * other style keeps the list ending above the block.
 */
class KitScreenFootPaneTest {
    private class Seen(val list: DpRect, val foot: DpRect, val lastRow: DpRect)

    private fun page(style: Style, platform: KitPlatform): Seen {
        var seen: Seen? = null
        runDesktopComposeUiTest((W * D).toInt(), (H * D).toInt()) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(D)) {
                    KitTheme(appearance = KitAppearance(style = style, mode = AppearanceMode.Dark), platform = platform, localeTag = "en", stillFrame = true) {
                        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 16.dp)) {
                            Box(Modifier.size(W.dp, H.dp)) {
                                KitScreen(
                                    page = true,
                                    topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) { KitText("Conversations") } },
                                    floatingBar =
                                        KitFloatingBar(
                                            scrollsElsewhere = true,
                                            below = { Box(Modifier.fillMaxWidth().height(40.dp)) { KitText("All") } },
                                        ),
                                    bottom = { Box(Modifier.fillMaxWidth().height(56.dp).testTag("foot")) { KitText("All conversations") } },
                                    edges = KitScreenEdges.Bleed,
                                ) {
                                    LazyColumn(Modifier.fillMaxSize().testTag("list"), contentPadding = kitScreenChromePadding()) {
                                        items(60) { i -> Box(Modifier.fillMaxWidth().height(48.dp).background(Color.White).testTag("row-$i")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("list").performScrollToIndex(59)
            waitForIdle()
            seen =
                Seen(
                    list = onNodeWithTag("list").getBoundsInRoot(),
                    foot = onNodeWithTag("foot", useUnmergedTree = true).getBoundsInRoot(),
                    lastRow = onNodeWithTag("row-59", useUnmergedTree = true).getBoundsInRoot(),
                )
        }
        return seen!!
    }

    @Test
    fun `foot pane - under Glass the list runs on under the foot bar and its last row rests whole above it`() {
        val s = page(Style.Glass, FakeKitPlatform(frost = true, liquidGlass = true))
        assertTrue(s.list.bottom >= s.foot.bottom - 0.5.dp, "the list stops at ${s.list.bottom}, the foot bar ends at ${s.foot.bottom}")
        assertTrue(s.lastRow.bottom <= s.foot.top + 0.5.dp, "the last row ends at ${s.lastRow.bottom}, under the foot bar's top ${s.foot.top}")
    }

    @Test
    fun `foot pane - under Solid the list ends above the foot bar`() {
        val s = page(Style.Solid, FakeKitPlatform())
        assertTrue(s.list.bottom <= s.foot.top + 0.5.dp, "the list runs to ${s.list.bottom}, the foot bar starts at ${s.foot.top}")
        assertTrue(s.lastRow.bottom <= s.foot.top + 0.5.dp, "the last row ends at ${s.lastRow.bottom}, the foot bar starts at ${s.foot.top}")
    }

    private companion object {
        const val D = 2.625f
        const val W = 411f
        const val H = 760f
    }
}
