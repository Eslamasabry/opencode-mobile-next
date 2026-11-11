@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11, run 4 (3a): renaming a long name showed two lines of it, and the rest of the dock became a tall
 * empty body with the "+" floating in the middle. Now the field shows the whole name (up to four lines, then it scrolls
 * inside itself), the swell stays inside the area above the keyboard, and the strip keeps rings other than the renamed
 * one, whole, with the "+" at its foot.
 */
class KitEdgeDockRenameLongTest {
    private val longName = "Quarterly budget review for the phone app and the release notes for the next beta"
    private val rings =
        listOf(
            KitDockRing("one", "name two animals", null, KitDockRingState.Working),
            KitDockRing("two", "count from one to eighty", null, KitDockRingState.Idle),
            KitDockRing("three", longName, null, KitDockRingState.Idle),
            KitDockRing("four", "create a file hello.txt", null, KitDockRingState.Idle),
            KitDockRing("five", "Alpha", null, KitDockRingState.Idle),
        )
    private val height = 915
    private val top = 24f

    private fun inside(what: String, r: Rect, a: Rect) =
        assertTrue(r.left >= a.left - .5f && r.right <= a.right + .5f && r.top >= a.top - .5f && r.bottom <= a.bottom + .5f, "$what $r lies inside $a")

    /** [bottom]: the dock area's bottom inset (the keyboard and the bar). */
    private fun rename(side: KitDockSide, bottom: Float, check: (KitEdgeDockState, Rect, Rect, Rect, androidx.compose.ui.test.ComposeUiTest) -> Unit) {
        runDesktopComposeUiTest(412, height) {
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
            runOnIdle { state.startRename(2) }
            waitForIdle()
            waitForIdle()
            val body = runOnIdle { state.body() }
            val a = Rect(body.stripLeft, body.stripTop, body.stripRight, body.stripBottom)
            val b = Rect(body.secondLeft, body.secondTop, body.secondRight, body.secondBottom)
            val field = onAllNodes(hasContentDescription(TestDockWords.nickname), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
            check(state, a, b, field, this)
        }
    }

    @Test
    fun `dock rename - a name of four lines shows whole in the field, on either edge`() {
        for (side in KitDockSide.entries) {
            rename(side, bottom = 48f) { _, _, b, field, ui ->
                inside("$side: the field", field, b)
                val node = ui.onAllNodes(hasContentDescription(TestDockWords.nickname), useUnmergedTree = true).fetchSemanticsNodes().first()
                val results = ArrayList<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                val text = assertNotNull(results.firstOrNull(), "$side: the field's text layout")
                assertTrue(text.lineCount in 3..4, "$side: the long name wraps to 3 or 4 lines, not ${text.lineCount}")
                assertTrue(
                    text.size.height <= field.height + .5f && !text.hasVisualOverflow,
                    "$side: the whole name shows: ${text.lineCount} lines (${text.size.height} px) in a field ${field.height} px tall",
                )
            }
        }
    }

    @Test
    fun `dock rename - with room for one ring the swell stays above the keyboard and the strip keeps another ring and the plus`() {
        // The keyboard and the bar leave a span of 291 dp: room for one ring in the column.
        val bottom = 600f
        for (side in KitDockSide.entries) {
            rename(side, bottom = bottom) { state, a, b, field, ui ->
                val areaBottom = height - bottom
                assertTrue(b.top >= top - .5f && b.bottom <= areaBottom + .5f, "$side: the swell $b stays inside the area $top..$areaBottom")
                inside("$side: the field", field, b)
                var others = 0
                for ((i, ring) in rings.withIndex()) {
                    val r = ui.onAllNodes(hasContentDescription(TestDockWords.ringLabel(ring)), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                    if (r.width <= 0f || r.height <= 0f || i == 2) continue
                    inside("$side: ring ${ring.name}", r, a)
                    // A ring stands whole in the strip or not at all (not a slice of one beside the swell).
                    assertTrue(r.height >= state.column.pitch - 1f, "$side: ring ${ring.name} $r is a whole slot (${state.column.pitch} dp)")
                    others++
                    assertTrue(!r.overlaps(field), "$side: ring ${ring.name} stays clear of the field")
                }
                assertTrue(others >= 1, "$side: the strip keeps a ring besides the renamed one (not an empty body)")
                val plus = ui.onAllNodes(hasContentDescription(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                inside("$side: the +", plus, a)
            }
        }
    }
}
