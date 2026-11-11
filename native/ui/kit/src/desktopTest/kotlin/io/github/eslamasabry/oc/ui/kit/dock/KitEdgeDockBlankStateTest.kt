@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A quiet ring may come with an EMPTY state word (the app stops saying "Idle"). A blank state line takes no line and no
 * gap: in the swell and in the list there is no empty text, the name and the next line close up, the swell's header is
 * one line shorter, and the spoken labels have no dangling comma. The sub-agent rows drop a blank state word too.
 */
class KitEdgeDockBlankStateTest {
    private object QuietWords : KitDockWords by TestDockWords {
        override fun idle(): String = ""
    }

    private val rings =
        listOf(
            KitDockRing("quiet", "Quiet one", null, KitDockRingState.Idle, lastLine = "the quiet last words"),
            KitDockRing(
                "busy", "Busy one", null, KitDockRingState.Working, lastLine = "the busy last words",
                children = listOf(KitDockChild("kid", "explore", KitDockRingState.Idle)),
            ),
        )

    private val blankText = SemanticsMatcher("text that is blank") { n -> n.config.getOrNull(SemanticsProperties.Text)?.let { t -> t.all { it.text.isBlank() } } == true }

    private fun ComposeUiTest.bounds(text: String): Rect = onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    @Test
    fun `dock blank state - a quiet ring with no state word has no empty line in the swell or the list and no dangling comma`() =
        runDesktopComposeUiTest(412, 915) {
            val state = KitEdgeDockState()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(KitDockModel(rings = rings), object : KitDockIntents {}, QuietWords, insets = KitDockInsets(top = 24.dp, bottom = 48.dp), state = state)
                    }
                }
            }
            waitForIdle()
            // Spoken: the quiet ring is its name alone.
            assertTrue(onAllNodes(hasContentDescription("Quiet one"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the quiet ring is labelled by its name alone")
            assertEquals("Quiet one", QuietWords.ringLabel(rings[0]))

            // The swell: the quiet ring's header is one line shorter than the busy one's, with no empty text.
            fun swellHeight(i: Int): Float {
                runOnIdle { state.openSwell(SwellKind.Scrub, i) }
                waitForIdle()
                assertTrue(onAllNodes(blankText, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "swell of ring $i: no blank text")
                val b = runOnIdle { state.body() }
                runOnIdle { state.closeSwell() }
                waitForIdle()
                return b.secondBottom - b.secondTop
            }
            val quiet = swellHeight(0)
            val busy = swellHeight(1)
            // The busy ring also shows its sub-agent row; the header alone differs by the state line (18 dp).
            val kids = SwellRows.children(1f) + DockDims.SW_GAP
            assertEquals(busy - kids - 18f, quiet, 0.5f, "the quiet swell is the busy one less its sub-agents and one state line")
            // The sub-agent's spoken label has no dangling comma.
            runOnIdle { state.openSwell(SwellKind.Scrub, 1) }
            waitForIdle()
            assertTrue(onAllNodes(hasContentDescription(TestDockWords.actionOn(TestDockWords.open, "explore")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the quiet sub-agent is named without a comma")
            runOnIdle { state.closeSwell() }
            waitForIdle()

            // The list: no empty text; the quiet ring's name and last line close up (its row a line shorter), the busy
            // one's hold its state line. The list's name is the leftmost (the strip's names stand at the edge).
            runOnIdle { state.openPanel() }
            waitForIdle()
            assertTrue(onAllNodes(blankText, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "list: no blank text")
            val quietGap = bounds("the quiet last words").top - onAllNodes(hasText("Quiet one"), useUnmergedTree = true).fetchSemanticsNodes().minBy { it.boundsInRoot.left }.boundsInRoot.bottom
            val busyGap = bounds("the busy last words").top - onAllNodes(hasText("Busy one"), useUnmergedTree = true).fetchSemanticsNodes().minBy { it.boundsInRoot.left }.boundsInRoot.bottom
            assertTrue(quietGap < 2f, "list: the quiet name and its last line close up ($quietGap)")
            assertTrue(busyGap > 10f, "list: the busy ring keeps its state line between ($busyGap)")
        }
}
