@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11 (m2): the pulled-out dock's names and the list's names ran to the screen's edge ("tell a sho..."
 * touching it, the list's names cut at it). At 360 and 412 dp, on both edges: the strip's names keep in from the screen
 * edge; the list, the peek and the scrub swell stay inside the screen and the host's area, keep the page gutter on the
 * side toward the screen's middle, and their names end before the gutter on the dock's own edge.
 */
class KitEdgeDockEdgesTest {
    private val rings =
        listOf(
            KitDockRing("one", "create a file hello.txt with one line and then say done", null, KitDockRingState.NeedsYou, lastLine = "I'll finish making the space file bold, then give you the lines"),
            KitDockRing("two", "tell a short joke about the sea and the stars", null, KitDockRingState.Working),
            KitDockRing("three", "name three colours", null, KitDockRingState.Idle),
        )
    private val top = 102f
    private val bottom = 120f
    private val height = 915

    /** The text nodes showing a ring's name (the strip's names, the list's rows, the swell's header). */
    private fun ComposeUiTest.names(): List<Pair<String, Rect>> =
        rings.flatMap { ring ->
            onAllNodes(hasText(ring.name, substring = true), useUnmergedTree = true).fetchSemanticsNodes().map { ring.name to it.boundsInRoot }
        }.filter { (_, r) -> r.width > 0f && r.height > 0f }

    private fun ComposeUiTest.body(state: KitEdgeDockState): KitDockBody = runOnIdle { state.body() }

    @Test
    fun `dock edges - at 360 and 412 dp on both edges the strip, the list, the peek and the swell keep their words on the screen and the gutter`() {
        for (width in listOf(360, 412)) {
            for (side in listOf(KitDockSide.End, KitDockSide.Start)) {
                runDesktopComposeUiTest(width, height) {
                    val state = KitEdgeDockState()
                    setContent {
                        KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                            Box(Modifier.fillMaxSize()) {
                                KitEdgeDock(
                                    KitDockModel(rings = rings, placement = KitDockPlacement(side, 0.5f)),
                                    object : KitDockIntents {},
                                    TestDockWords,
                                    insets = KitDockInsets(top = top.dp, bottom = bottom.dp),
                                    state = state,
                                )
                            }
                        }
                    }
                    waitForIdle()
                    val w = width.toFloat()
                    val onRight = side == KitDockSide.End
                    val at = "$width dp, $side edge"
                    // The pulled-out strip at rest: every name keeps in from the screen's edge.
                    for ((name, r) in names()) {
                        assertTrue(r.left >= 0f && r.right <= w, "$at, strip: the name $name $r lies on the screen")
                        assertTrue(if (onRight) r.right <= w - 4f else r.left >= 4f, "$at, strip: the name $name $r keeps clear of the dock's screen edge")
                    }

                    // The list, the peek and the scrub swell.
                    for (what in listOf("list", "peek", "swell")) {
                        when (what) {
                            "list" -> runOnIdle { state.openPanel() }
                            "peek" -> runOnIdle { state.openSwell(SwellKind.Peek, 0) }
                            else -> runOnIdle { state.openSwell(SwellKind.Scrub, 1) }
                        }
                        waitForIdle()
                        val b = body(state)
                        assertTrue(b.hasSecond, "$at, $what: box B is out")
                        val box = Rect(b.secondLeft, b.secondTop, b.secondRight, b.secondBottom)
                        // Inside the host's area, and the page gutter on the side toward the middle.
                        assertTrue(box.top >= top - .5f && box.bottom <= height - bottom + .5f, "$at, $what: B $box lies inside the area")
                        // B's far end: its left edge on the right, its right edge on the left (the other end meets the strip).
                        val far = if (onRight) box.left else w - box.right
                        assertTrue(far >= DockDims.GUTTER - .5f, "$at, $what: B $box keeps the gutter from the far edge: $far")
                        // Every name shown out of the strip lies on the screen, the gutter kept on both sides.
                        for ((name, r) in names()) {
                            val inStrip = if (onRight) r.left >= w - DockDims.REST_W - 1f else r.right <= DockDims.REST_W + 1f
                            if (inStrip) continue
                            assertTrue(
                                r.left >= DockDims.GUTTER - .5f && r.right <= w - DockDims.GUTTER + .5f,
                                "$at, $what: the name $name $r keeps the page gutter from both screen edges",
                            )
                        }
                        when (what) {
                            "list" -> runOnIdle { state.closePanel() }
                            else -> runOnIdle { state.closeSwell() }
                        }
                        waitForIdle()
                    }
                }
            }
        }
    }
}
