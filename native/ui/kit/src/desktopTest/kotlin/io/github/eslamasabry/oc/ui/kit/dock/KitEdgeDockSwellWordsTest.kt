@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11 (m7): in the swell the action words touched ("RenameRemove"). At 360 dp with five actions (a
 * request, in the held ring's card: Open, Allow, Deny, Rename, Remove) no two words meet: neighbours on one row keep at least the kit's gap.
 */
class KitEdgeDockSwellWordsTest {
    @Test
    fun `dock swell - at 360 dp five action words never meet`() =
        runDesktopComposeUiTest(360, 800) {
            val state = KitEdgeDockState()
            val ring = KitDockRing("one", "create a file two.txt with one line", null, KitDockRingState.NeedsYou, lastLine = "create a file two.txt with one line")
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = listOf(ring, ring.copy(key = "two", name = "create a file one.txt"))),
                            object : KitDockIntents {},
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                            state = state,
                        )
                    }
                }
            }
            waitForIdle()
            // The held ring's card (the peek) shows every action of a request: Open, Allow, Deny, Rename, Remove.
            runOnIdle { state.openSwell(SwellKind.Peek, 0) }
            waitForIdle()
            val words = listOf(TestDockWords.open, TestDockWords.allow, TestDockWords.deny, TestDockWords.rename, TestDockWords.remove)
            val boxes =
                words.map { w ->
                    val nodes = onAllNodes(hasText(w), useUnmergedTree = true).fetchSemanticsNodes()
                    assertTrue(nodes.isNotEmpty(), "the word $w shows")
                    w to nodes.first().boundsInRoot
                }
            var pairs = 0
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val (wa, a) = boxes[i]
                val (wb, b) = boxes[j]
                if (abs(a.center.y - b.center.y) > 4f) continue
                pairs++
                val gap = if (a.left < b.left) b.left - a.right else a.left - b.right
                assertTrue(gap >= 2 * ACTION_WORD_INSET - .5f, "$wa $a and $wb $b keep the kit's gap: $gap")
            }
            assertTrue(pairs >= 2, "the words stand side by side ($pairs pairs on one row)")
            println("swell words at 360 dp: " + boxes.joinToString { (w, r: Rect) -> "$w ${r.left}..${r.right}" })
        }
}
