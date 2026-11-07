package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentChoice
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentChoiceState
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentGrid
import io.github.eslamasabry.oc.ui.kit.agent.agentChoiceLabel
import io.github.eslamasabry.oc.ui.kit.agent.agentChoiceMinHeight
import io.github.eslamasabry.oc.ui.kit.agent.agentGridOneColumn
import io.github.eslamasabry.oc.ui.kit.agent.agentGridRows
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** lib/ui/kit/kit_agent_choice.dart and kit_agent_grid.dart at 8302de537. */
@OptIn(ExperimentalTestApi::class)
class KitAgentChoiceTest {
    private fun matcher(label: String) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(label))

    @Test
    fun `B-KIT-270 pairs side by side, an odd last card alone, one column from large text`() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), agentGridRows(5, oneColumn = false))
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), agentGridRows(4, oneColumn = false))
        assertEquals(listOf(listOf(0), listOf(1)), agentGridRows(2, oneColumn = true))
        assertFalse(agentGridOneColumn(1.3f))
        assertTrue(agentGridOneColumn(1.31f))
    }

    @Test
    fun `B-KIT-271 the card is at least three and a half targets tall, two when wide or large`() {
        assertEquals(168.dp, agentChoiceMinHeight(wide = false, large = false))
        assertEquals(96.dp, agentChoiceMinHeight(wide = true, large = false))
        assertEquals(96.dp, agentChoiceMinHeight(wide = false, large = true))
        assertEquals("Codex. Fast edits and reviews", agentChoiceLabel("Codex", "Fast edits and reviews"))
        assertEquals("Mine", agentChoiceLabel("Mine", ""))
    }

    @Test
    fun `B-KIT-272 the chosen card reads as selected, a card that needs setup says Set up and still opens`() = runComposeUiTest {
        var opened = ""
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                CompositionLocalProvider(LocalKitLogoLoader provides MARKS) {
                    KitAgentGrid(
                        listOf(
                            { KitAgentChoice("Claude Code", "Careful with big changes", "claude", KitAgentChoiceState.Selected, { opened = "claude" }) },
                            { KitAgentChoice("Kilo", "Your choice of provider", "kilocode", KitAgentChoiceState.NeedsSetup, { opened = "kilo" }) },
                        )
                    )
                }
            }
        }
        onNode(matcher("Claude Code. Careful with big changes")).assertIsSelected()
        onNode(matcher("Kilo. Set up")).assertIsNotSelected().performClick()
        assertEquals("kilo", opened)
    }
}
