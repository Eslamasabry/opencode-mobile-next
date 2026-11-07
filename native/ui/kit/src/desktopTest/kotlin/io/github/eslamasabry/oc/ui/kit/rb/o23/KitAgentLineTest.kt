package io.github.eslamasabry.oc.ui.kit.rb.o23

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.agent.KitAgentLine
import io.github.eslamasabry.oc.ui.kit.agent.agentLineText
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.team.KitSpecBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** lib/ui/kit/kit_agent_line.dart and lib/ui/kit/team/kit_spec_block.dart at 8302de537. */
@OptIn(ExperimentalTestApi::class)
class KitAgentLineTest {
    private val spoken = "Claude Code on this phone. Change the agent or project."

    @Test
    fun `B-KIT-250 the agent line joins agent and place with a middle dot`() {
        assertEquals("Claude Code · on this phone", agentLineText("Claude Code", "on this phone"))
    }

    @Test
    fun `B-KIT-251 the whole line is one 48 dp target that speaks its label and opens`() = runComposeUiTest {
        var opened = 0
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                KitAgentLine("Claude Code", "on this phone", onClick = { opened++ }, semanticsLabel = spoken)
            }
        }
        val line = onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(spoken)))
        assertTrue(line.getBoundsInRoot().let { it.bottom - it.top } >= 48.dp)
        line.performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `B-KIT-252 a read-only spec block shows label words and helper, an editable one reports changes`() = runComposeUiTest {
        val changes = mutableListOf<String>()
        lateinit var editable: TextFieldState
        setContent {
            KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), stillFrame = true) {
                editable = rememberTextFieldState("")
                KitSpecBlock("Goal", rememberTextFieldState("Keep drafts across restarts."), readOnly = true, helper = "One line")
                KitSpecBlock("Scope", editable, onChanged = { changes += it })
            }
        }
        onNodeWithText("Keep drafts across restarts.").assertExists()
        onNodeWithText("One line").assertExists()
        runOnIdle { editable.setTextAndPlaceCursorAtEnd("Phones only") }
        waitForIdle()
        assertEquals(listOf("Phones only"), changes)
    }
}
