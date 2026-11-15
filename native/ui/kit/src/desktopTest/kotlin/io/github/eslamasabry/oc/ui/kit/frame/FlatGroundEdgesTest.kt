@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.gate.FrameMeter
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * c2-performance (2026-11-15, the first customer's "noticeable lag" on a mid-range phone): a page's scroll edges
 * (kitScrollEdges) cleared the body in an offscreen layer the size of the page, and a conversation nested a second one
 * for its list: on every frame the content moved the GPU filled one or two extra page-sized buffers and blended them back.
 * Over a flat ground (the default look: no live background) the fade is now that ground laid over the content, with no
 * layer. These tests count the offscreen layers of a framed page (FlatGroundEdgesPixelsTest holds the same pixels).
 */
class FlatGroundEdgesTest {
    /** A framed page, a conversation frame with its list's own end, and the live background as the control. */
    @Composable
    private fun Page(chat: Boolean) {
        CompositionLocalProvider(LocalKitScreenInsets provides WindowInsets(top = 24.dp, bottom = 16.dp)) {
            Box(Modifier.size(W.dp, H.dp)) {
                val list = rememberLazyListState(10)
                val source = if (chat) rememberKitFrostState() else null
                KitScreen(
                    page = true,
                    overlayChrome = chat,
                    frost = source,
                    topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                    bottom = if (chat) ({ Box(Modifier.fillMaxWidth().height(72.dp)) }) else null,
                ) {
                    KitReportScrolledUnder { list.canScrollBackward }
                    LazyColumn(
                        state = list,
                        contentPadding = kitScreenChromePadding(),
                        modifier =
                            Modifier.fillMaxSize()
                                .then(if (source != null) Modifier.kitFrostSource(source) else Modifier)
                                .then(if (chat) Modifier.kitScreenListEdges() else Modifier),
                    ) {
                        items(40, key = { it }) { i ->
                            BasicText("Line $i", Modifier.fillMaxWidth().height(40.dp), style = TextStyle(color = Color.White))
                        }
                    }
                }
            }
        }
    }

    private fun offscreen(appearance: KitAppearance, chat: Boolean): Pair<Int, Long> {
        var out = 0 to 0L
        runDesktopComposeUiTest(W, H) {
            mainClock.autoAdvance = false
            val meter = FrameMeter.attach(this)
            setContent { KitTheme(appearance, FakeKitPlatform(), "en") { Page(chat) } }
            meter.settle()
            out = meter.offscreenLayers()
            meter.close()
        }
        return out
    }

    @Test
    fun `PERF-EDGE-2 a framed page at the default look composites nothing offscreen for its edges`() {
        val (page, pagePx) = offscreen(KitAppearance(), chat = false)
        assertEquals(0, page, "a page on the flat ground: $page offscreen layers ($pagePx px)")
        val (chat, chatPx) = offscreen(KitAppearance(), chat = true)
        assertEquals(0, chat, "a conversation frame on the flat ground: $chat offscreen layers ($chatPx px)")
    }

    @Test
    fun `PERF-EDGE-3 over a live picture the edges still clear the content in their layer`() {
        val live = KitAppearance(liveBackground = true, liveStyle = "aurora")
        val (page, _) = offscreen(live, chat = false)
        assertTrue(page >= 1, "the page over the aurora keeps its clearing layer ($page)")
        val (chat, _) = offscreen(live, chat = true)
        assertTrue(chat >= 2, "the conversation over the aurora keeps its two clearing layers ($chat)")
    }

    private companion object {
        const val W = 240
        const val H = 400
    }
}
