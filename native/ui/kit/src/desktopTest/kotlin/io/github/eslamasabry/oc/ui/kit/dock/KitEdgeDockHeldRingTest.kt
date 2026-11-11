@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11 (m1): holding a ring until its card opened and then sliding along the strip picked the ring up
 * (reorder), closed the card, and drew the lifted ring under the finger between two slots, over its neighbours' rings
 * and names. Driven through the dock's own gesture path (as its pointer loop drives it): the held ring's card stays
 * out while the ring is carried, and the carried ring stands in its drop place, a whole pitch from every other ring.
 */
class KitEdgeDockHeldRingTest {
    private val rings =
        listOf(
            KitDockRing("one", "create a file one.txt", null, KitDockRingState.Idle),
            KitDockRing("two", "create a file two.txt", null, KitDockRingState.Idle),
            KitDockRing("three", "create a file hello.txt", null, KitDockRingState.Idle),
            KitDockRing("four", "name three colours", null, KitDockRingState.Idle),
            KitDockRing("five", "name four fruits", null, KitDockRingState.Idle),
            KitDockRing("six", "tell a short joke", null, KitDockRingState.Idle),
        )

    /** The finger moves to [y] (edge frame) at [time], as the dock's pointer loop reports a move. */
    private fun ComposeUiTest.moveTo(state: KitEdgeDockState, x: Float, y: Float, time: Long) {
        runOnIdle {
            state.computeFrame()
            state.onFeedback(state.gesture.move(x, y, time, state.hits))
            state.onMove(x, y, x)
        }
        waitForIdle()
    }

    @Test
    fun `dock held ring - sliding a held ring keeps its card out and the carried ring off its neighbours`() {
        for (side in listOf(KitDockSide.End, KitDockSide.Start)) {
            runDesktopComposeUiTest(412, 915) {
                val state = KitEdgeDockState()
                setContent {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        Box(Modifier.fillMaxSize()) {
                            KitEdgeDock(
                                KitDockModel(rings = rings, placement = KitDockPlacement(side, 0.5f)),
                                object : KitDockIntents {},
                                TestDockWords,
                                insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                                state = state,
                            )
                        }
                    }
                }
                waitForIdle()
                // Hold ring one still until its card opens.
                val x = runOnIdle { state.widthDp - DockDims.REST_W / 2f }
                runOnIdle {
                    val y0 = state.slotY(0)
                    state.computeFrame()
                    state.gesture.down(DockZone.Ring, x, y0, 0L, state.hits)
                    state.onDown(DockZone.Ring)
                    state.onFeedback(state.gesture.elapsed(DockRules().holdMs))
                }
                waitForIdle()
                assertTrue(runOnIdle { state.body().hasSecond }, "$side: the held ring's card is out")
                // Slide along the strip, the finger ending between two slots.
                val pitch = runOnIdle { state.column.pitch }
                for ((n, dy) in listOf(0.4f, 1.2f, 2.3f).withIndex()) {
                    val y = runOnIdle { state.slotY(0) } + dy * pitch
                    moveTo(state, x, y, 600L + 50L * n)
                }
                val at = "$side, carried"
                assertEquals(0, runOnIdle { state.carrying }, "$at: ring one is carried")
                val body = runOnIdle { state.body() }
                assertTrue(body.hasSecond && !body.secondIsList && body.growth > 0.9f, "$at: its card stays out (growth ${body.growth})")
                assertEquals(0, runOnIdle { state.swellRing }, "$at: the card is ring one's")
                // Every ring a whole pitch from every other: the carried one covers no neighbour's ring or name.
                val centers =
                    rings.map { ring ->
                        val r = onAllNodes(hasContentDescription(TestDockWords.ringLabel(ring)), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                        ring.name to r.center.y
                    }
                for (i in centers.indices) for (j in i + 1 until centers.size) {
                    val gap = abs(centers[i].second - centers[j].second)
                    assertTrue(gap >= pitch - .5f, "$at: ${centers[i].first} and ${centers[j].first} stand $gap apart (pitch $pitch)")
                }
                // Lifting drops it and closes the card.
                runOnIdle {
                    val y = state.slotY(0) + 2.3f * pitch
                    state.onOutcome(state.gesture.up(x, y, 0f, state.hits))
                }
                waitForIdle()
                assertTrue(!runOnIdle { state.body().hasSecond }, "$side: the card closes when the ring is dropped")
            }
        }
    }
}
