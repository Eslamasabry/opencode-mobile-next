@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * Device QA 2026-11-11 (M3): renaming a ring with the keyboard up broke the dock: the name being edited was cut ("o.txt
 * with one line", the rest scrolled out of a one-line field), the swell cut across the rings, and a "+" stood amid the
 * ring column. With the keyboard's inset on, on either edge: the whole name shows in the field, the field lies inside
 * box B, the ridden-out ring stands in B, every other ring shown and the "+" lie in box A and clear of the field, and the
 * body stays inside the area.
 */
class KitEdgeDockRenameTest {
    private val rings =
        listOf(
            KitDockRing("one", "name two animals", null, KitDockRingState.Working),
            KitDockRing("two", "Alpha", null, KitDockRingState.Idle),
            KitDockRing("three", "create a file one.txt", null, KitDockRingState.Idle),
            KitDockRing("four", "create a file hello.txt with one line", null, KitDockRingState.Idle),
            KitDockRing("five", "name four fruits", null, KitDockRingState.Idle),
            KitDockRing("six", "tell a short joke", null, KitDockRingState.NeedsYou),
            KitDockRing("seven", "name three colours", null, KitDockRingState.Idle),
        )
    private val renamed = 3
    private val height = 915
    private val top = 24f
    private val keyboard = 378f

    private fun inside(what: String, r: Rect, a: Rect) =
        assertTrue(r.left >= a.left - .5f && r.right <= a.right + .5f && r.top >= a.top - .5f && r.bottom <= a.bottom + .5f, "$what $r lies inside $a")

    @Test
    fun `dock rename - with the keyboard up on either edge the whole name shows in a field inside the body and nothing is stranded`() {
        for (side in listOf(KitDockSide.End, KitDockSide.Start)) {
            runDesktopComposeUiTest(412, height) {
                var bottom by mutableStateOf(48f)
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
                runOnIdle { state.startRename(renamed) }
                waitForIdle()
                // The keyboard comes up under the field.
                bottom = 48f + keyboard
                waitForIdle()
                val at = "$side edge"
                val body = runOnIdle { state.body() }
                assertTrue(body.hasSecond, "$at: box B is out while renaming")
                val a = Rect(body.stripLeft, body.stripTop, body.stripRight, body.stripBottom)
                val b = Rect(body.secondLeft, body.secondTop, body.secondRight, body.secondBottom)
                val areaBottom = height - bottom
                assertTrue(a.top >= top - .5f && a.bottom <= areaBottom + .5f, "$at: box A $a lies inside the area $top..$areaBottom")
                assertTrue(b.top >= top - .5f && b.bottom <= areaBottom + .5f && b.left >= -.5f && b.right <= 412.5f + 40f, "$at: box B $b lies inside the area")

                val node = onAllNodes(hasContentDescription(TestDockWords.nickname), useUnmergedTree = true).fetchSemanticsNodes().first()
                val field = node.boundsInRoot
                inside("$at: the rename field", field, b)
                assertTrue(field.left >= -.5f && field.right <= 412.5f, "$at: the rename field $field lies on the screen")
                val results = ArrayList<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                val text = assertNotNull(results.firstOrNull(), "$at: the field's text layout")
                assertTrue(
                    text.size.width <= field.width + .5f && text.size.height <= field.height + .5f && !text.hasVisualOverflow,
                    "$at: the whole name shows in the field ${field.width} x ${field.height}: its text is ${text.size} on ${text.lineCount} lines",
                )

                for ((i, ring) in rings.withIndex()) {
                    val r = onAllNodes(hasContentDescription(TestDockWords.ringLabel(ring)), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                    if (r.width <= 0f || r.height <= 0f) continue
                    if (i == renamed) {
                        inside("$at: the ridden-out ring", r, b)
                    } else {
                        inside("$at: ring ${ring.name}", r, a)
                        assertTrue(!r.overlaps(field), "$at: ring ${ring.name} $r stays clear of the field $field")
                    }
                }
                val plus = onAllNodes(hasContentDescription(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
                inside("$at: the +", plus, a)
                assertTrue(!plus.overlaps(field), "$at: the + $plus stays clear of the field $field")
            }
        }
    }
}
