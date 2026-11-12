package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-11-11, on the joined menu: "it's just large; size should be determined based on the content". A menu is as
 * wide as its widest row needs, within 180 to 320 dp, its rows filling that width; before, its rows stretched every menu
 * to 320 dp.
 */
@OptIn(ExperimentalTestApi::class)
class KitMenuWidthTest {
    private class Measured(val panel: Float, val labelRight: Float, val labelHeight: Float, val shortLabelHeight: Float, val panelRight: Float)

    private fun measure(labels: List<String>, long: String): Measured {
        var m: Measured? = null
        runDesktopComposeUiTest(600, 800) {
            val items = labels.map { KitMenuItem(it, onSelect = {}) }
            setContent { KitTheme(platform = FakeKitPlatform(), localeTag = "en") { Box { KitMenuPanel(items, Modifier, onChoose = {}) } } }
            waitForIdle()
            val panel = onNodeWithTag("kit-menu").fetchSemanticsNode().boundsInWindow
            val label = onNodeWithText(long).fetchSemanticsNode().boundsInWindow
            val short = onNodeWithText(labels.first()).fetchSemanticsNode().boundsInWindow
            m = Measured(panel.width / density.density, label.right, label.height, short.height, panel.right)
        }
        return m!!
    }

    @Test
    fun `a menu is as wide as its widest row, within 180 to 320 dp, and its rows are never cut`() {
        val short = measure(listOf("Find", "Copy", "Go to"), "Copy")
        val medium = measure(listOf("Find", "Rename conversation", "Fork conversation", "Copy transcript"), "Rename conversation")
        val long = measure(listOf("Find", "A very long menu label that will not fit on one line of any menu at all here"), "Find")
        assertEquals(180f, short.panel, 1f, "short labels: the least width")
        assertTrue(medium.panel > short.panel + 10f, "a longer label widens the menu (${medium.panel} vs ${short.panel} dp)")
        assertTrue(medium.panel < 320f - 10f, "and no further than it needs (${medium.panel} dp)")
        assertEquals(320f, long.panel, 1f, "never wider than 320 dp")
        assertTrue(medium.labelRight <= medium.panelRight, "the long row's words lie inside the panel")
        assertEquals(medium.shortLabelHeight, medium.labelHeight, 1f, "on one line: not cut or wrapped")
    }
}
