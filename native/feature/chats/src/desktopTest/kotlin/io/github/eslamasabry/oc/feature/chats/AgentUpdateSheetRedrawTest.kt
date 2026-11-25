package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

@OptIn(ExperimentalTestApi::class)
class AgentUpdateSheetRedrawTest {
    private fun runSheet(
        offer: AgentUpdateOffer,
        fontScale: Float = 1f,
        block: suspend KitUiTest.() -> Unit,
    ) {
        val previous = Locale.getDefault()
        val scope = CoroutineScope(Job())
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            val model = AgentUpdateSheetModel(RedrawOffered(offer), scope)
            val actions = actionsFor(model)
            kitUiTest(900, 2000) {
                setContent {
                    val updates by model.ui.collectAsState(initial = remember { model.current() })
                    CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                        ChatEnvironment {
                            KitTheme(
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                                platform = DesktopKitPlatform(),
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatsScreen(ChatsUi(phase = ChatsPhase.Empty, agentUpdates = updates), actions)
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                block()
            }
        } finally {
            scope.cancel()
            Locale.setDefault(previous)
        }
    }

    private fun actionsFor(model: AgentUpdateSheetModel): ChatsActions =
        object : RecordingChatsActions() {
            override val agentUpdates: AgentUpdateActions =
                object : AgentUpdateActions {
                    override fun chooseAgentUpdate(id: AgentId, choice: AgentUpdateChoice) {
                        model.choose(id, choice)
                    }
                }
        }

    @Test
    fun `B-UPD-006 the total and the mobile-data line come before the first row`() =
        runSheet(redrawFiveOffer()) {
            val downloadTop = onNodeWithText("Download", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            val aboutTop =
                onNodeWithText("About ", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            val meteredTop =
                onNodeWithText("You're on mobile data", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            val helperTop =
                onNodeWithText("Agent helper", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            assertTrue(
                downloadTop < aboutTop && aboutTop < meteredTop && meteredTop < helperTop,
                "expected Download ($downloadTop) < About ($aboutTop) < metered ($meteredTop) < helper ($helperTop)",
            )
        }

    @Test
    fun `B-UPD-007 the helper row comes first with its own Needed line and no group labels`() =
        runSheet(redrawFiveOffer()) {
            onNodeWithText("Optional").assertDoesNotExist()
            onNodeWithText("The agents you update need these to run.").assertDoesNotExist()
            val needed = onAllNodesWithText("Needed for these updates", substring = true, useUnmergedTree = true)
            needed.assertCountEquals(1)
            val helperTop =
                onNodeWithText("Agent helper", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            val neededTop = needed[0].fetchSemanticsNode().boundsInRoot.top
            val claudeTop =
                onNodeWithText("Claude Code", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            assertTrue(
                neededTop > helperTop && neededTop < claudeTop,
                "expected helper ($helperTop) < needed line ($neededTop) < Claude Code ($claudeTop)",
            )
        }

    @Test
    fun `B-UPD-008 one switch per agent and turning Codex off updates one fewer`() =
        runSheet(redrawFiveOffer()) {
            onAllNodes(isToggleable()).assertCountEquals(5)
            onNodeWithText("Keep").assertDoesNotExist()
            onNodeWithText("Codex", substring = true).performClick()
            waitForIdle()
            onNodeWithText("Update 4 agents").assertExists()
        }

    @Test
    fun `B-UPD-009 an agent that cannot be kept shows a locked on switch and says why`() =
        runSheet(redrawFiveOffer()) {
            val gemini = onNode(isToggleable() and hasText("Gemini CLI", substring = true))
            gemini.assertIsOn().assertIsNotEnabled()
            onNodeWithText(HELPER_TOO_NEW, substring = true, useUnmergedTree = true).assertExists()
            gemini.performScrollTo().performClick()
            waitForIdle()
            onNodeWithText("Update 5 agents").assertExists()
        }

    @Test
    fun `B-UPD-010 the sheet has no Linux line`() =
        runSheet(redrawFiveOffer()) {
            onNodeWithText("Git and Python come from Ubuntu.").assertDoesNotExist()
        }

    @Test
    fun `B-UPD-011 nothing is cut at 360 dp at text 1_3`() =
        runSheet(redrawFiveOffer(), fontScale = 1.3f) {
            val textNodes =
                onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                    useUnmergedTree = true,
                )
            val count = textNodes.fetchSemanticsNodes().size
            assertTrue(count > 0, "expected text nodes on the update sheet")
            for (i in 0 until count) {
                val layouts = mutableListOf<TextLayoutResult>()
                textNodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val label =
                    textNodes[i]
                        .fetchSemanticsNode()
                        .config
                        .getOrNull(SemanticsProperties.Text)
                        ?.joinToString(" ") { it.text }
                        .orEmpty()
                layouts.forEach { layout ->
                    assertFalse(layout.hasVisualOverflow, "visual overflow on: $label")
                }
            }
        }

    private companion object {
        const val HELPER_TOO_NEW = "Its version can't work with the new agent helper, so it can't be kept."
    }
}
