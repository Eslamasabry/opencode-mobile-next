@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
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
 * Device QA 2026-11-11 (M1): in a chat, after the keyboard closed (or with a needs-you ring), the dock's rings stood near
 * the top of the screen, over the header, while its body started lower with an empty tail under the "+". The column
 * scrolled to show the conversation on screen while the keyboard left room for one ring; when the area grew back the
 * scroll stayed, so every ring stood a whole column above the body, and rings scrolled out of view were drawn anyway.
 *
 * Here the area changes under the dock (a chat's header and message box, the keyboard opening and closing) at rest, at
 * the top and in the middle, pulled out, and with a needs-you ring's swell open. Every ring shown and the "+" lie inside
 * box A of [KitEdgeDockState.body], and the body lies inside the area the host gives it.
 */
class KitEdgeDockAreaTest {
    private val rings =
        listOf(
            KitDockRing("one", "name two animals", null, KitDockRingState.Working),
            KitDockRing("two", "Alpha", null, KitDockRingState.Idle),
            KitDockRing("three", "create a file one.txt", null, KitDockRingState.Idle),
            KitDockRing("four", "name three colours", null, KitDockRingState.Idle),
            KitDockRing("five", "name four fruits", null, KitDockRingState.Idle),
            KitDockRing("six", "tell a short joke", null, KitDockRingState.NeedsYou),
            KitDockRing("seven", "create a file hello.txt with one line", null, KitDockRingState.Idle),
        )

    /** The chat: its header, and its message box with the keyboard closed or open (dp). */
    private val header = 102f
    private val box = 120f
    private val keyboard = 520f
    private val height = 915

    private fun inside(what: String, r: Rect, a: Rect) =
        assertTrue(r.left >= a.left - .5f && r.right <= a.right + .5f && r.top >= a.top - .5f && r.bottom <= a.bottom + .5f, "$what $r lies inside box A $a")

    private fun ComposeUiTest.check(what: String, state: KitEdgeDockState, bottom: Float) {
        val body = runOnIdle { state.body() }
        val a = Rect(body.stripLeft, body.stripTop, body.stripRight, body.stripBottom)
        val areaBottom = height - bottom
        assertTrue(a.top >= header - .5f && a.bottom <= areaBottom + .5f, "$what: box A $a lies inside the area $header..$areaBottom")
        if (body.hasSecond) {
            assertTrue(
                body.secondTop >= header - .5f && body.secondBottom <= areaBottom + .5f && body.secondLeft >= -.5f,
                "$what: box B ${body.secondLeft},${body.secondTop}..${body.secondRight},${body.secondBottom} lies inside the area $header..$areaBottom",
            )
        }
        var shown = 0
        for (ring in rings) {
            val r = onAllNodes(hasContentDescription(TestDockWords.ringLabel(ring)), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
            // A ring scrolled out of the column's view is cut away entirely (empty bounds); one that shows lies in A.
            if (r.width <= 0f || r.height <= 0f) continue
            shown++
            inside("$what: ring ${ring.name}", r, a)
        }
        assertTrue(shown >= 1, "$what: at least one ring shows")
        val current = onAllNodes(hasContentDescription(TestDockWords.ringLabel(rings.last())), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        assertTrue(current.width > 0f && current.height > 0f, "$what: the conversation on screen shows its ring, $current")
        val plus = onAllNodes(hasContentDescription(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        inside("$what: the +", plus, a)
    }

    @Test
    fun `dock area - the keyboard opening and closing at rest, pulled out and with a needs-you swell keeps every ring and the + in box A and the body in the area`() =
        runDesktopComposeUiTest(412, height) {
            var bottom by mutableStateOf(box)
            var bias by mutableStateOf(0.5f)
            val state = KitEdgeDockState()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = rings, current = "seven", placement = KitDockPlacement(KitDockSide.End, bias)),
                            object : KitDockIntents {},
                            TestDockWords,
                            insets = KitDockInsets(top = header.dp, bottom = bottom.dp),
                            state = state,
                        )
                    }
                }
            }
            val steps = listOf(box, box + keyboard - 100f, box, keyboard, box)
            for (b in listOf(0.5f, 0f, 1f)) {
                bias = b
                for ((n, now) in steps.withIndex()) {
                    bottom = now
                    waitForIdle()
                    check("bias $b, at rest, step $n (bottom $now)", state, now)
                }
            }
            bias = 0.5f
            runOnIdle { state.openPanel() }
            waitForIdle()
            for ((n, now) in steps.withIndex()) {
                bottom = now
                waitForIdle()
                check("pulled out, step $n (bottom $now)", state, now)
            }
            runOnIdle { state.closePanel() }
            waitForIdle()
            // The needs-you ring's swell, as its alert opens it.
            runOnIdle { state.openSwell(SwellKind.Scrub, 5) }
            waitForIdle()
            for ((n, now) in steps.withIndex()) {
                bottom = now
                waitForIdle()
                check("needs-you swell, step $n (bottom $now)", state, now)
            }
        }
}
