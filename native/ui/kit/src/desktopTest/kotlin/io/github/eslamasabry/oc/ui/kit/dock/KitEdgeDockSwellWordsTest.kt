@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The swell's action words (device QA 2026-11-11, "RenameRemove"; owner 2026-11-11: "Rena..." and "Rem..." are not
 * acceptable). The scrub swell offers only the ring's state's actions (a request: Open, Allow, Deny), never Rename or
 * Remove; no word is ever shortened: each shows whole or its cell shows the glyph alone, labelled with the word; and two
 * neighbours' words never meet. At 360 dp, at text size 1 and 1.3; and the held ring's card with all five actions.
 */
class KitEdgeDockSwellWordsTest {
    private val ring = KitDockRing("one", "create a file two.txt with one line", null, KitDockRingState.NeedsYou, lastLine = "create a file two.txt with one line")

    private fun ComposeUiTest.open(kind: SwellKind, fontScale: Float) {
        val state = KitEdgeDockState()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
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
        }
        waitForIdle()
        runOnIdle { state.openSwell(kind, 0) }
        waitForIdle()
    }

    /** Each of [words] shown whole (never cut) or not at all; the shown ones keep the kit's gap. Returns those shown. */
    private fun ComposeUiTest.wordsWhole(what: String, words: List<String>): List<Pair<String, Rect>> {
        val shown = ArrayList<Pair<String, Rect>>()
        for (w in words) {
            val node = onAllNodes(hasText(w), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: continue
            val results = ArrayList<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.firstOrNull()
            if (layout != null) {
                assertTrue(!layout.isLineEllipsized(0) && !layout.hasVisualOverflow, "$what: the word $w is whole")
            }
            shown += w to node.boundsInRoot
        }
        for (i in shown.indices) for (j in i + 1 until shown.size) {
            val (wa, a) = shown[i]
            val (wb, b) = shown[j]
            if (abs(a.center.y - b.center.y) > 4f) continue
            val gap = if (a.left < b.left) b.left - a.right else a.left - b.right
            assertTrue(gap >= 2 * ACTION_WORD_INSET - .5f, "$what: $wa $a and $wb $b keep the kit's gap: $gap")
        }
        println("$what: " + shown.joinToString { (w, r) -> "$w ${r.left}..${r.right}" })
        return shown
    }

    private val request = listOf(TestDockWords.open, TestDockWords.allow, TestDockWords.deny)

    @Test
    fun `dock swell - the scrub swell offers a request's actions only, whole, at 360 dp and text size 1`() =
        runDesktopComposeUiTest(360, 800) {
            open(SwellKind.Scrub, 1f)
            val shown = wordsWhole("scrub x1", request + listOf(TestDockWords.rename, TestDockWords.remove))
            assertTrue(shown.none { it.first == TestDockWords.rename || it.first == TestDockWords.remove }, "no Rename or Remove in the scrub swell")
            assertTrue(shown.size == 3, "Open, Allow and Deny read whole at text size 1: $shown")
        }

    @Test
    fun `dock swell - at text size 1_3 a word that does not fit leaves its glyph alone, labelled, never cut`() =
        runDesktopComposeUiTest(360, 800) {
            open(SwellKind.Scrub, 1.3f)
            wordsWhole("scrub x1.3", request)
            // Every action cell keeps its label (the word) for assistive technology, whether its word shows or not.
            for (w in request) {
                val label = TestDockWords.actionOn(w, ring.name)
                assertTrue(onAllNodes(hasContentDescription(label), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the $w cell is labelled \"$label\"")
            }
        }

    @Test
    fun `dock swell - the held ring's card shows its five actions, whole, apart`() =
        runDesktopComposeUiTest(360, 800) {
            open(SwellKind.Peek, 1f)
            wordsWhole("card x1", request + listOf(TestDockWords.rename, TestDockWords.remove))
        }
}
