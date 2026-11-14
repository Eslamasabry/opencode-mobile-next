@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.effects.KitLiquidSource
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidSource
import io.github.eslamasabry.oc.ui.kit.effects.rememberKitLiquidSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device 2026-11-14 (Glass, Aurora drift): a visible page that nobody touched never came to rest (about 60 frames a
 * second on the home and a Settings page long after the live background's clock had stopped). A page source (the
 * recording a liquid pane bends) counts its recordings in snapshot state that the panes read while drawing, and it
 * records whenever its node draws. A pane that reads the count and lies beside the source in the same layer (the edge
 * dock beside the conversations list, the bar beside the body) is redrawn by every new count, and redrawing that layer
 * draws the source again: a new count, every frame, for ever. A page at rest must record nothing, and a real change
 * must still reach the pane once.
 */
class KitSourceRestTest {
    @Test
    fun `GLASS-REST-01 a pane beside a frost source that reads its count leaves the page at rest`() {
        var state: KitFrostState? = null
        var content by mutableStateOf(Color.Red)
        var paneDraws = 0
        runDesktopComposeUiTest(200, 200) {
            mainClock.autoAdvance = false
            setContent {
                val s = rememberKitFrostState().also { state = it }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().kitFrostSource(s)) { Box(Modifier.size(40.dp).background(content)) }
                    // A pane over the recording: it reads the count, as every liquid pane's backdrop does.
                    Canvas(Modifier.size(20.dp)) {
                        s.version
                        paneDraws++
                        drawRect(Color.Blue)
                    }
                }
            }
            mainClock.advanceTimeBy(500)
            val v0 = state!!.version
            val d0 = paneDraws
            repeat(60) { mainClock.advanceTimeByFrame() }
            assertEquals(v0, state!!.version, "an untouched page records nothing for a second of frames")
            assertTrue(paneDraws - d0 <= 1, "the pane drew ${paneDraws - d0} times in a second at rest")
            // A real change behind the pane still reaches it.
            content = Color.Green
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertTrue(state!!.version > v0, "a change of the content records again")
            assertTrue(paneDraws > d0, "and the pane draws again")
            val v1 = state!!.version
            repeat(60) { mainClock.advanceTimeByFrame() }
            assertEquals(v1, state!!.version, "then the page rests again")
        }
    }

    @Test
    fun `GLASS-REST-02 a pane beside a liquid source that reads its count leaves the page at rest`() {
        var source: KitLiquidSource? = null
        runDesktopComposeUiTest(200, 200) {
            mainClock.autoAdvance = false
            setContent {
                val s = rememberKitLiquidSource().also { source = it }
                // The kit records only while a pane uses it.
                androidx.compose.runtime.DisposableEffect(s) {
                    s.users++
                    onDispose { s.users-- }
                }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().kitLiquidSource(s)) { Box(Modifier.size(40.dp).background(Color.Red)) }
                    Canvas(Modifier.size(20.dp)) {
                        s.version
                        drawRect(Color.Blue)
                    }
                }
            }
            mainClock.advanceTimeBy(500)
            val v0 = source!!.version
            repeat(60) { mainClock.advanceTimeByFrame() }
            assertEquals(v0, source!!.version, "an untouched page records nothing for a second of frames")
        }
    }
}
