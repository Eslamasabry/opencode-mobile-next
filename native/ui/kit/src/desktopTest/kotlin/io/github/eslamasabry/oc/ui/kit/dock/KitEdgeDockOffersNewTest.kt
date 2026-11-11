@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
 * `KitDockModel.offersNew`: the home page has its own green "+", so it asks the dock not to offer a new conversation.
 * False: no "+" in the strip (and the strip has no foot), no "New conversation" row in the list; with no rings the dock
 * draws nothing at all and takes no touches. True (the default) is today's dock, on both edges.
 */
class KitEdgeDockOffersNewTest {
    private val three = listOf(
        KitDockRing("a", "Alpha", null, KitDockRingState.Idle),
        KitDockRing("b", "Beta", null, KitDockRingState.Working),
        KitDockRing("c", "Gamma", null, KitDockRingState.Idle),
    )

    private class Counting : KitDockIntents {
        var created = 0
        override fun onNewConversation() {
            created++
        }
    }

    private class Seen(val plus: Int, val row: Int, val dock: Int, val stripHeight: Float, val exclusion: Boolean, val created: Int)

    private fun run(rings: List<KitDockRing>, offersNew: Boolean, side: KitDockSide, openList: Boolean, tapEdge: Boolean = false): Seen {
        var seen: Seen? = null
        val state = KitEdgeDockState()
        val intents = Counting()
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = rings, offersNew = offersNew, placement = KitDockPlacement(side, 0.5f)),
                            intents,
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                            state = state,
                        )
                    }
                }
            }
            waitForIdle()
            val height = runOnIdle { state.body().let { it.stripBottom - it.stripTop } }
            if (openList) {
                runOnIdle { state.openPanel() }
                waitForIdle()
            }
            if (tapEdge) {
                val x = if (side == KitDockSide.End) 400f else 12f
                onRoot().performTouchInput { click(Offset(x, 915f / 2f)) }
                waitForIdle()
            }
            seen =
                Seen(
                    plus = onAllNodes(hasContentDescription(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().size,
                    row = onAllNodes(hasText(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().size,
                    dock = onAllNodes(hasTestTag("kit-edge-dock"), useUnmergedTree = true).fetchSemanticsNodes().size,
                    stripHeight = height,
                    exclusion = runOnIdle { state.gestureExclusion() } != null,
                    created = intents.created,
                )
        }
        return seen!!
    }

    @Test
    fun `dock offersNew - true keeps the + in the strip and the New conversation row in the list, on both edges`() {
        for (side in KitDockSide.entries) {
            val strip = run(three, offersNew = true, side = side, openList = false)
            assertTrue(strip.plus >= 1, "$side: the + is in the strip")
            val list = run(three, offersNew = true, side = side, openList = true)
            assertTrue(list.row >= 1, "$side: the list has its New conversation row")
        }
    }

    @Test
    fun `dock offersNew - false has no + in the strip and no row in the list, and a strip without a foot, on both edges`() {
        for (side in KitDockSide.entries) {
            val with = run(three, offersNew = true, side = side, openList = false)
            val without = run(three, offersNew = false, side = side, openList = false)
            assertEquals(0, without.plus, "$side: no + in the strip")
            assertTrue(without.stripHeight < with.stripHeight - DockDims.FOOT / 2f, "$side: the strip lost its foot (${with.stripHeight} to ${without.stripHeight})")
            assertTrue(without.dock == 1, "$side: the rings are still there")
            val list = run(three, offersNew = false, side = side, openList = true)
            assertEquals(0, list.row, "$side: no New conversation row in the list")
            assertEquals(0, list.plus, "$side: no + beside the list")
        }
    }

    @Test
    fun `dock offersNew - no rings, true still draws the strip with its +, false draws nothing and takes no touches, on both edges`() {
        for (side in KitDockSide.entries) {
            val shown = run(emptyList(), offersNew = true, side = side, openList = false)
            assertTrue(shown.dock == 1 && shown.plus >= 1, "$side: today's empty dock still draws its + (dock ${shown.dock}, + ${shown.plus})")
            val none = run(emptyList(), offersNew = false, side = side, openList = false, tapEdge = true)
            assertEquals(0, none.dock, "$side: nothing is drawn")
            assertEquals(0, none.plus, "$side: no +")
            assertEquals(false, none.exclusion, "$side: no back-gesture exclusion")
            assertEquals(0, none.created, "$side: a tap on the edge reaches nothing")
        }
    }
}
