@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11, run 4 (1b): with the keyboard up the dock's body is short and the second ring stood half hidden
 * under the "+". The strip shows only rings that fit whole above the "+" (the rest stay in the list), at every area the
 * keyboard passes through and in every frame of the column's scroll: a ring shown is a whole slot inside the viewport,
 * and none ever overlaps the "+".
 */
class KitEdgeDockWholeRingsTest {
    private val rings = (1..7).map { KitDockRing("r$it", "Ring number $it", null, KitDockRingState.Idle) }
    private val height = 915
    private val header = 102f

    @Test
    fun `dock rings - a ring is whole above the plus or not in the strip, as the keyboard comes and while the column scrolls`() {
        runDesktopComposeUiTest(412, height) {
            var bottom by mutableStateOf(120f)
            val state = KitEdgeDockState()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = rings, current = "r1", placement = KitDockPlacement(KitDockSide.End, 0.5f)),
                            object : KitDockIntents {},
                            TestDockWords,
                            insets = KitDockInsets(top = header.dp, bottom = bottom.dp),
                            state = state,
                        )
                    }
                }
            }
            val d = 1f
            fun check(what: String) {
                val plus = onAllNodes(hasContentDescription(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                val col = state.column
                val viewTop = runOnIdle { state.top.value } + DockDims.HEAD
                val viewBottom = viewTop + col.viewportHeight
                var shown = 0
                for (ring in rings) {
                    val r = onAllNodes(hasContentDescription(TestDockWords.ringLabel(ring)), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                    if (r.width <= 0f || r.height <= 0f) continue
                    shown++
                    assertTrue(r.height >= (col.pitch - 1f) * d, "$what: ring ${ring.name} $r is a whole slot (${col.pitch} dp), not part of one")
                    assertTrue(r.top >= viewTop - 1f && r.bottom <= viewBottom + 1f, "$what: ring ${ring.name} $r lies inside the viewport $viewTop..$viewBottom")
                    assertTrue(!r.overlaps(plus), "$what: ring ${ring.name} $r stays clear of the + $plus")
                }
                assertTrue(shown >= 1, "$what: at least one whole ring shows")
            }
            // The keyboard comes up and goes, a few dp at a time (the frames in between).
            var b = 120f
            while (b <= 560f) {
                bottom = b
                waitForIdle()
                check("bottom $b")
                b += 24f
            }
            // The room for one ring, with the column caught between two rings (a scroll still on its way).
            bottom = 580f
            waitForIdle()
            for (scroll in listOf(14f, 30f, 47f, 64f)) {
                runOnIdle { runBlocking { state.scroll.snapTo(scroll) } }
                waitForIdle()
                check("one ring of room, scroll $scroll")
            }
        }
    }
}
