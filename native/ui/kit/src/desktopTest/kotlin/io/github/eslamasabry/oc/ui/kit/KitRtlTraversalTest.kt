@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.nav.KitBreadcrumb
import io.github.eslamasabry.oc.ui.kit.nav.TabArrow
import io.github.eslamasabry.oc.ui.kit.nav.TabRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-A11Y-RTL (UX-24): tabs and the breadcrumb in Arabic. Right to left puts the first tab and the root crumb at the right
 * edge and walks the rest leftwards, in the order a screen reader reads them; exactly one tab is the selected one, and the
 * breadcrumb's current folder is the one selected crumb; the arrow keys move in reading order, so Left is the next tab.
 */
class KitRtlTraversalTest {
    private fun host(localeTag: String, content: @Composable () -> Unit): androidx.compose.ui.test.ComposeUiTest.() -> Unit = {
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), localeTag, stillFrame = true) {
                Box(Modifier.width(360.dp)) { content() }
            }
        }
    }

    private val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    private fun tabsAre(localeTag: String, rtl: Boolean) =
        runComposeUiTest {
            host(localeTag) { TabsScene(KitbWords.of(localeTag), index = 1) }()
            waitForIdle()
            val all = onAllNodes(tabs)
            val nodes = all.fetchSemanticsNodes()
            assertEquals(3, nodes.size, "three tabs")
            val lefts = nodes.indices.map { all[it].getBoundsInRoot().left.value }
            if (rtl) assertTrue(lefts[0] > lefts[1] && lefts[1] > lefts[2], "right to left: the first tab is at the right edge: $lefts")
            else assertTrue(lefts[0] < lefts[1] && lefts[1] < lefts[2], "left to right: $lefts")
            val chosen = nodes.indices.filter { nodes[it].config.getOrNull(SemanticsProperties.Selected) == true }
            assertEquals(listOf(1), chosen, "exactly the chosen tab is selected, and a screen reader reads that")
        }

    @Test fun `B-A11Y-RTL tabs are laid out and read right to left in Arabic, one selected`() = tabsAre("ar", rtl = true)

    @Test fun `B-A11Y-RTL tabs keep their order left to right in English, one selected`() = tabsAre("en", rtl = false)

    @Test
    fun `B-A11Y-RTL the arrow keys move in reading order, Left is the next tab in Arabic`() {
        assertEquals(2, TabRules.arrow(1, TabArrow.Left, 3, rtl = true))
        assertEquals(0, TabRules.arrow(1, TabArrow.Right, 3, rtl = true))
        assertEquals(2, TabRules.arrow(1, TabArrow.Right, 3, rtl = false))
        assertEquals(0, TabRules.arrow(1, TabArrow.Left, 3, rtl = false))
        assertEquals(2, TabRules.arrow(2, TabArrow.Left, 3, rtl = true), "they stop at the end")
    }

    @Test
    fun `B-A11Y-RTL the breadcrumb starts at the right edge in Arabic and its current folder is the one selected`() =
        runComposeUiTest {
            host("ar") { KitBreadcrumb(rootLabel = "Projects", segments = listOf("shopfront", "web"), onSelected = {}) }()
            waitForIdle()
            val root = onNode(hasText("Projects", substring = true)).getBoundsInRoot()
            val middle = onNode(hasText("shopfront", substring = true)).getBoundsInRoot()
            assertTrue(root.left > middle.left, "the root is at the right of the next crumb: $root vs $middle")
            val current = onAllNodes(selected)
            assertEquals(1, current.fetchSemanticsNodes().size, "one crumb is the current folder")
            val here = current[0].getBoundsInRoot()
            assertTrue(here.right <= middle.left + 1.dp, "the current folder is the last, at the left end: $here vs $middle")
        }
}
