@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11 (M2): after the dock was dragged to the other edge its body moved to the left edge while the
 * rings and the "+" stayed on the right, detached. Every ring and the "+" lie inside box A on either edge, including
 * after the side changes while the dock is on screen.
 */
class KitEdgeDockSideTest {
    private val rings =
        listOf(
            KitDockRing("one", "create a file one.txt", null, KitDockRingState.NeedsYou),
            KitDockRing("two", "create a file two.txt", null, KitDockRingState.Working),
            KitDockRing("three", "Notes", null, KitDockRingState.Idle),
        )

    private fun inside(what: String, r: Rect, a: Rect) =
        assertTrue(r.left >= a.left - .5f && r.right <= a.right + .5f && r.top >= a.top - .5f && r.bottom <= a.bottom + .5f, "$what $r lies inside box A $a")

    @Test
    fun `dock side - on the leading edge and after a change of side the rings and the + lie inside box A`() =
        runDesktopComposeUiTest(412, 915) {
            var side by mutableStateOf(KitDockSide.End)
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
            for (now in listOf(KitDockSide.End, KitDockSide.Start, KitDockSide.End)) {
                side = now
                waitForIdle()
                val a = runOnIdle { state.body().let { Rect(it.stripLeft, it.stripTop, it.stripRight, it.stripBottom) } }
                if (now == KitDockSide.Start) assertTrue(a.right < 206f, "$now: box A on the left edge, $a")
                else assertTrue(a.left > 206f, "$now: box A on the right edge, $a")
                for (ring in rings) {
                    val r = onAllNodes(hasContentDescription(TestDockWords.ringLabel(ring)), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                    inside("$now: ring ${ring.name}", r, a)
                }
                val plus = onAllNodes(hasContentDescription(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                inside("$now: the +", plus, a)
            }
        }
}
