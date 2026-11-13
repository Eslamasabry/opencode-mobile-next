@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The body's scroll-edge fade is drawn on whole pixels (kitScrollEdges, wholePx). A status bar's height times the
 * edge's share, or a dp length at density 2.625, lands inside a pixel; drawn there, the clearing rectangles are
 * anti-aliased and the row they meet in keeps a share of the content: a hairline of half-cleared words between a
 * cleared row and a nearly cleared one. The same draw crashed the Android emulator's renderer on every chat
 * (2026-11-09; bisected 2026-11-10 to the commit that added the fade).
 *
 * Guard: white content on black, both edges set inside a pixel. Down a column the content only ever comes in from the
 * top edge and only ever goes out towards the bottom edge: no row is brighter than the one further into the page.
 */
class ScrollEdgeWholePixelTest {
    @Test
    fun `a fade whose edges fall inside a pixel leaves no half-cleared row`() {
        runDesktopComposeUiTest(W, H) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.size(W.dp, H.dp).background(Color.Black).testTag("scene")) {
                        Box(
                            Modifier.fillMaxSize()
                                .kitScrollEdges(
                                    clearAbove = { 10.5f },
                                    topReach = 0.dp,
                                    fadeFromBottom = { 30.4f },
                                    bottomReach = 20.dp,
                                    topReachPx = { 23.3f },
                                )
                                .background(Color.White)
                        )
                    }
                }
            }
            val px = onNodeWithTag("scene").captureToImage().toPixelMap()
            val column = List(px.height) { px[W / 2, it].red }
            val firstWhole = column.indexOfFirst { it > WHOLE }
            val lastWhole = column.indexOfLast { it > WHOLE }
            assertTrue(firstWhole in 1 until lastWhole, "the content is whole between its edges ($firstWhole..$lastWhole)")
            assertTrue(column.first() < CLEAR && column.last() < CLEAR, "the content is cleared at both ends")
            for (y in 1..firstWhole) {
                assertTrue(column[y] >= column[y - 1] - STEP, "top edge: row $y (${column[y]}) is dimmer than row ${y - 1} (${column[y - 1]})")
            }
            for (y in lastWhole + 1 until px.height) {
                assertTrue(column[y] <= column[y - 1] + STEP, "bottom edge: row $y (${column[y]}) is brighter than row ${y - 1} (${column[y - 1]})")
            }
        }
    }

    @Test
    fun `a bottom fade at a fractional height and strength leaves no brighter row towards the end`() {
        runDesktopComposeUiTest(W, H) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                    Box(Modifier.size(W.dp, H.dp).background(Color.Black).testTag("scene")) {
                        Box(
                            Modifier.fillMaxSize()
                                .kitScrollEdges(
                                    clearAbove = null,
                                    topReach = 0.dp,
                                    fadeFromBottom = { 42.4f },
                                    bottomReach = 0.dp,
                                    bottomReachPx = { 42.4f },
                                    bottomStrength = { .6f },
                                )
                                .background(Color.White)
                        )
                    }
                }
            }
            val px = onNodeWithTag("scene").captureToImage().toPixelMap()
            val column = List(px.height) { px[px.width / 2, it].red }
            assertTrue(column.first() > WHOLE, "the content is whole at the top")
            assertTrue(column.last() > .3f && column.last() < .5f, "a partial strength leaves the rest (${column.last()})")
            for (y in 1 until px.height) {
                assertTrue(column[y] <= column[y - 1] + STEP, "row $y (${column[y]}) is brighter than row ${y - 1} (${column[y - 1]})")
            }
        }
    }

    private companion object {
        const val W = 120
        const val H = 160
        const val WHOLE = .99f
        const val CLEAR = .02f
        const val STEP = 2f / 255f
    }
}
