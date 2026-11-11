@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
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
 * Owner 2026-11-11: the dock is always there. With no ring at all it still draws: out, its strip with the "+"; tucked,
 * its nub. Its body is a sane box A (finite, inside the window's height, on the edge), and nothing crashes.
 */
class KitEdgeDockEmptyTest {
    private fun shown(tucked: Boolean): Pair<KitDockBody, Int> {
        val state = KitEdgeDockState()
        var body = KitDockBody()
        var plus = 0
        runDesktopComposeUiTest(412, 915) {
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = emptyList(), tucked = tucked),
                            object : KitDockIntents {},
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                            state = state,
                        )
                    }
                }
            }
            waitForIdle()
            body = runOnIdle { state.body() }
            plus = onAllNodes(hasContentDescription(TestDockWords.newConversation), useUnmergedTree = true).fetchSemanticsNodes().size
        }
        return body to plus
    }

    @Test
    fun `dock with no rings - out, its strip and the + are drawn`() {
        val (b, plus) = shown(tucked = false)
        assertTrue(plus >= 1, "the + is there")
        val values = listOf(b.stripLeft, b.stripTop, b.stripRight, b.stripBottom, b.stripRadius)
        assertTrue(values.all { it.isFinite() }, "a finite box A: $values")
        assertTrue(b.stripRight > b.stripLeft && b.stripBottom > b.stripTop, "box A has a size: $values")
        assertTrue(b.stripTop >= 24f && b.stripBottom <= 915f - 48f, "box A within the safe area: $values")
        assertTrue(b.stripLeft in 300f..412f, "box A on the right edge: $values")
        assertEquals(false, b.hasSecond)
    }

    @Test
    fun `dock with no rings - tucked, its nub is drawn`() {
        val (b, _) = shown(tucked = true)
        val values = listOf(b.stripLeft, b.stripTop, b.stripRight, b.stripBottom, b.stripRadius)
        assertTrue(values.all { it.isFinite() }, "a finite box A: $values")
        assertEquals(412f - DockDims.NUB_W, b.stripLeft, .5f, "the nub shows ${DockDims.NUB_W} dp of the strip")
        assertTrue(b.stripBottom > b.stripTop)
    }
}
