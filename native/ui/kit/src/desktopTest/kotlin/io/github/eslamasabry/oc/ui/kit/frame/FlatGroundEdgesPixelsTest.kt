@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * c2-performance (2026-11-15): over a flat ground a page's scroll edges lay the ground over the content instead of
 * clearing it in an offscreen layer (KitScrollEdges.kt). A still page must look exactly as it did: the two ways draw the
 * same pixels, words, translucent bars and content running past the box's edge included.
 */
class FlatGroundEdgesPixelsTest {
    private val ground = Color(0xFF15171C)

    /** Rows of words and translucent bars, some running past the box's edges, as a page body draws them. */
    @Composable
    private fun Body() {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.offset(x = (-12).dp, y = (-10).dp).size(80.dp, 24.dp).background(Color(0xCC3388FF)))
            repeat(14) { i ->
                BasicText("Row $i of the conversation list", style = TextStyle(color = Color(0xFFE8EAED), fontSize = 14.sp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0x80FF8800 + i * 0x0400)))
            }
        }
    }

    private fun shot(flat: Boolean): IntArray {
        lateinit var pixels: IntArray
        runDesktopComposeUiTest(W, H) {
            setContent {
                Box(Modifier.size(W.dp, H.dp).background(ground)) {
                    Box(
                        Modifier.padding(16.dp)
                            .fillMaxSize()
                            .kitScrollEdges(
                                clearAbove = { 30f },
                                topReach = 24.dp,
                                fadeFromBottom = { 70f },
                                bottomReach = 16.dp,
                                keepBottomPx = { 12f },
                                ground = if (flat) ground else Color.Unspecified,
                            )
                    ) { Body() }
                }
            }
            waitForIdle()
            pixels = onRoot().captureToImage().toPixelMap().buffer.copyOf()
        }
        return pixels
    }

    @Test
    fun `PERF-EDGE-1 over a flat ground the fade draws the pixels the offscreen clearing drew`() {
        val layered = shot(flat = false)
        val flat = shot(flat = true)
        assertEquals(layered.size, flat.size)
        var worst = 0
        var differ = 0
        for (i in layered.indices) {
            val d = channelGap(layered[i], flat[i])
            if (d > 0) differ++
            worst = maxOf(worst, d)
        }
        assertTrue(worst <= 1, "the flat fade differs by up to $worst levels in a channel ($differ pixels); rounding allows 1")
    }

    private fun channelGap(a: Int, b: Int): Int {
        var worst = 0
        for (shift in intArrayOf(0, 8, 16, 24)) worst = maxOf(worst, abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)))
        return worst
    }

    private companion object {
        const val W = 240
        const val H = 400
    }
}
