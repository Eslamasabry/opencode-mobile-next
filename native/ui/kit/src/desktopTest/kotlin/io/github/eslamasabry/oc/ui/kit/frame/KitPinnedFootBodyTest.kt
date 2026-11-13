@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * A page body with a block pinned under it ends in a hard cut across a line of text while more of the body lies
 * hidden below (device picture, Appearance with its "4 of 4 / Finish" bar, 2026-11-12). The part fades the body's last
 * 16 dp while it can scroll further, and leaves it whole at its end, with nothing to scroll, or with no foot.
 */
class KitPinnedFootBodyTest {
    @Test
    fun `a body that can scroll further fades above the foot`() {
        val px = scene(rowsDp = 600, withFoot = true, toEnd = false)
        assertTrue(px.just < px.mid - 0.5f, "the row just above the foot is faded (${px.just} vs ${px.mid})")
        assertTrue(px.just < 0.15f, "it is almost cleared at the foot (${px.just})")
    }

    @Test
    fun `a body scrolled to its end is left whole`() {
        val px = scene(rowsDp = 600, withFoot = true, toEnd = true)
        assertTrue(px.just > 0.99f, "nothing is cleared at the end (${px.just})")
    }

    @Test
    fun `a body with no foot is never faded`() {
        val px = scene(rowsDp = 600, withFoot = false, toEnd = false)
        assertTrue(px.just > 0.99f, "no foot, no fade (${px.just})")
    }

    @Test
    fun `a body shorter than the room is left whole`() {
        val px = scene(rowsDp = 60, withFoot = true, toEnd = false)
        assertEquals(1f, px.mid, 0.01f)
    }

    @Test
    fun `the body and the foot lie where the hand built column puts them`() {
        var part = Pair(0f, 0f)
        var ref = Pair(0f, 0f)
        for (usePart in listOf(false, true)) {
            runDesktopComposeUiTest(W, H) {
                setContent {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                        Box(Modifier.size(W.dp, H.dp)) {
                            val foot: @androidx.compose.runtime.Composable () -> Unit = {
                                Box(Modifier.fillMaxWidth().height(FOOT_DP.dp).testTag("foot"))
                            }
                            val body: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
                                Box(Modifier.fillMaxWidth().height(600.dp).testTag("rows"))
                            }
                            if (usePart)
                                KitPinnedFootBody(
                                    foot = foot,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 40.dp),
                                    content = body,
                                )
                            else
                                Column(Modifier.fillMaxSize()) {
                                    Column(
                                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                                            .padding(top = 8.dp, bottom = 40.dp),
                                        content = body,
                                    )
                                    foot()
                                }
                        }
                    }
                }
                waitForIdle()
                val foot = onNodeWithTag("foot").getBoundsInRoot()
                val rows = onNodeWithTag("rows").getBoundsInRoot()
                val got = Pair(foot.top.value, rows.top.value)
                if (usePart) part = got else ref = got
            }
        }
        assertEquals(ref, part)
    }

    @Test
    fun `a pinned head stays where it is at every scroll position and the body fades beneath it`() {
        var atRest = 0f
        var atEnd = 0f
        var fadedJustUnder = 1f
        var midUnder = 0f
        runDesktopComposeUiTest(W, H) {
            lateinit var scroll: ScrollState
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                        Box(Modifier.size(W.dp, H.dp).background(Color.Black).testTag("scene")) {
                            scroll = rememberScrollState()
                            KitPinnedFootBody(
                                scroll = scroll,
                                head = { Box(Modifier.fillMaxWidth().height(30.dp).background(Color.Black).testTag("head")) },
                            ) { Box(Modifier.fillMaxWidth().height(600.dp).background(Color.White)) }
                        }
                    }
                }
            }
            waitForIdle()
            atRest = onNodeWithTag("head").getBoundsInRoot().top.value
            runBlocking { scroll.scrollTo(scroll.maxValue / 2) }
            waitForIdle()
            val img = onNodeWithTag("scene").captureToImage().toPixelMap()
            fadedJustUnder = img[W / 2, 31].red
            midUnder = img[W / 2, 31 + 60].red
            runBlocking { scroll.scrollTo(scroll.maxValue) }
            waitForIdle()
            atEnd = onNodeWithTag("head").getBoundsInRoot().top.value
        }
        assertEquals(atRest, atEnd)
        assertTrue(fadedJustUnder < midUnder - 0.5f, "the body thins out just under the head ($fadedJustUnder vs $midUnder)")
    }

    private class Column3(val mid: Float, val just: Float)

    private fun scene(rowsDp: Int, withFoot: Boolean, toEnd: Boolean): Column3 {
        var out = Column3(0f, 0f)
        runDesktopComposeUiTest(W, H) {
            lateinit var scroll: ScrollState
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                        Box(Modifier.size(W.dp, H.dp).background(Color.Black).testTag("scene")) {
                            scroll = rememberScrollState()
                            KitPinnedFootBody(
                                scroll = scroll,
                                foot = if (withFoot) ({ Box(Modifier.fillMaxWidth().height(FOOT_DP.dp).background(Color.Black)) }) else null,
                            ) {
                                Box(Modifier.fillMaxWidth().height(rowsDp.dp).background(Color.White))
                            }
                        }
                    }
                }
            }
            waitForIdle()
            if (toEnd) {
                runBlocking { scroll.scrollTo(scroll.maxValue) }
                waitForIdle()
            }
            val img = onNodeWithTag("scene").captureToImage().toPixelMap()
            val bodyBottom = if (withFoot) H - FOOT_DP else H
            out = Column3(img[W / 2, minOf(bodyBottom / 2, rowsDp / 2)].red, img[W / 2, bodyBottom - 2].red)
        }
        return out
    }

    private companion object {
        const val W = 120
        const val H = 200
        const val FOOT_DP = 40
    }
}
