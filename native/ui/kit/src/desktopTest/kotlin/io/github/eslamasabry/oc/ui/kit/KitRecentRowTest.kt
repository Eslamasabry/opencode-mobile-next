package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitFeedAgent
import io.github.eslamasabry.oc.ui.kit.row.KitPanelLinkRow
import io.github.eslamasabry.oc.ui.kit.row.KitRecentRow
import io.github.eslamasabry.oc.ui.kit.row.KitRecentRowRules
import io.github.eslamasabry.oc.ui.kit.row.KitRowPanel
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A1's roomy conversation row and its panel (owner 2026-11-08: no icons in boxes, plain coloured status). */
@OptIn(ExperimentalTestApi::class)
class KitRecentRowTest {
    @Test
    fun `A1-KIT-01 a project keeps one palette colour wherever it shows, and a blank one takes none`() {
        val slot = KitRecentRowRules.paletteSlot("oc_app", 4)
        assertEquals(slot, KitRecentRowRules.paletteSlot(" OC_APP ", 4), "the name alone decides, case and spaces aside")
        assertTrue(slot in 0..3)
        assertEquals(-1, KitRecentRowRules.paletteSlot("", 4))
        assertEquals(-1, KitRecentRowRules.paletteSlot("api", 0))
        val spread = listOf("api", "web", "site", "oc_app", "notes", "docs", "infra", "mobile").map { KitRecentRowRules.paletteSlot(it, 4) }
        assertTrue(spread.toSet().size > 1, "projects spread over the palette: $spread")
    }

    @Test
    fun `A1-KIT-02 the preview leads the second line, and the quiet words only stand in for it`() {
        assertEquals("Reading the kit", KitRecentRowRules.line(" Reading the kit ", "api · Laptop"))
        assertEquals("api · Laptop", KitRecentRowRules.line("  ", "api · Laptop"))
        assertEquals(null, KitRecentRowRules.line(null, null))
    }

    @Test
    fun `A1-KIT-03 the row is one target heard as one sentence, its acts below stay their own targets`() = runComposeUiTest {
        var opened = 0
        var allowed = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitRowPanel(label = "Right now") {
                    KitRecentRow(
                        title = "Run the tests",
                        agent = KitFeedAgent("", "OpenCode"),
                        time = "5m ago",
                        onClick = { opened++ },
                        preview = "Asks to run: npm test",
                        project = "api",
                        state = KitRowState("Needs you", KitRowStateTone.NeedsYou),
                        speakAgent = false,
                        below = { KitButton("Allow once", { allowed++ }) },
                    )
                }
            }
        }
        onNodeWithContentDescription("Run the tests, Needs you, api, Asks to run: npm test, 5m ago").performClick()
        onNodeWithText("Allow once").performClick()
        assertEquals(1, opened)
        assertEquals(1, allowed)
        // No tile and no badge: the mark is bare and the project is a dot.
        onAllNodesWithTag("kit-project-tile", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithTag("kit-recent-project-dot", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `A1-KIT-04 the link row says its label and count and opens on a tap`() = runComposeUiTest {
        var opened = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                KitRowPanel(leadingMarks = false) { KitPanelLinkRow("All conversations", "11", { opened++ }) }
            }
        }
        onNodeWithContentDescription("All conversations, 11").performClick()
        assertEquals(1, opened)
    }
}
