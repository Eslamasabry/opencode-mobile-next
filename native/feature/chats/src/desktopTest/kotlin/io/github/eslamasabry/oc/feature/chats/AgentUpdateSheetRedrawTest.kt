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
import androidx.compose.ui.test.assertIsOff
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
import kotlin.test.assertEquals
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
        offered: RedrawOffered = RedrawOffered(offer),
        block: suspend KitUiTest.() -> Unit,
    ) {
        val previous = Locale.getDefault()
        val scope = CoroutineScope(Job())
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            val model = AgentUpdateSheetModel(offered, scope)
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

                    override fun chooseAgentUpdateWifi(waitForWifi: Boolean) {
                        model.chooseWifiOnly(waitForWifi)
                    }

                    override fun proceedAgentUpdates() {
                        model.applyChoices()
                    }
                }
        }

    @Test
    fun `B-UPD-006 the total and Wi-Fi only row come before the first agent row`() =
        runSheet(redrawFiveOffer()) {
            val downloadTop = onNodeWithText("Download", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            val aboutTop =
                onNodeWithText("About ", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            val wifiTop =
                onNodeWithText("Wi-Fi only", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            val helperTop =
                onNodeWithText("Agent helper", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            assertTrue(
                downloadTop < aboutTop && aboutTop < wifiTop && wifiTop < helperTop,
                "expected Download ($downloadTop) < About ($aboutTop) < Wi-Fi only ($wifiTop) < helper ($helperTop)",
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
            onAllNodes(isToggleable()).assertCountEquals(6)
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

    @Test
    fun `B-UPD-012 turning Codex off at text 1_3 keeps its row height and moves nothing`() =
        runSheet(redrawFiveOffer(), fontScale = 1.3f) {
            val codex = onNode(isToggleable() and hasText("Codex", substring = true))
            val before = codex.fetchSemanticsNode().size.height
            codex.performScrollTo().performClick()
            waitForIdle()
            assertEquals(before, codex.fetchSemanticsNode().size.height, "Codex row height changed when turned off")
            onNodeWithText("Update 4 agents").assertExists()
            // Wait past the 60-frame ABA window before toggling back on so the second tap is a separate user action.
            mainClock.advanceTimeBy(1_500)
            codex.performClick()
            waitForIdle()
            assertEquals(before, codex.fetchSemanticsNode().size.height, "Codex row height changed when turned on")
        }

    @Test
    fun `B-UPD-013 on mobile data Wi-Fi only defaults on and decides waitForWifi`() {
        val offered = RedrawOffered(redrawFiveOffer())
        runSheet(redrawFiveOffer(), offered = offered) {
            val wifi = onNode(isToggleable() and hasText("Wi-Fi only", substring = true))
            wifi.assertIsOn()
            onNodeWithText("Will update on Wi-Fi", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText("Update 5 agents").performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastWaitForWifi != null }
            assertEquals(true, offered.lastWaitForWifi)
        }
    }

    @Test
    fun `B-UPD-014 turning Wi-Fi only off keeps row height and decides mobile download`() {
        val offered = RedrawOffered(redrawFiveOffer())
        runSheet(redrawFiveOffer(), offered = offered) {
            val wifi = onNode(isToggleable() and hasText("Wi-Fi only", substring = true))
            val before = wifi.fetchSemanticsNode().size.height
            wifi.performClick()
            waitForIdle()
            wifi.assertIsOff()
            assertEquals(before, wifi.fetchSemanticsNode().size.height, "Wi-Fi only row height changed")
            onNodeWithText(METERED_LINE, substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText("Update 5 agents").performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastWaitForWifi != null }
            assertEquals(false, offered.lastWaitForWifi)
        }
    }

    @Test
    fun `B-UPD-015 on Wi-Fi the Wi-Fi only row is absent and decides without waiting`() {
        val offered = RedrawOffered(redrawOneOffer())
        runSheet(redrawOneOffer(), offered = offered) {
            onNodeWithText("Wi-Fi only").assertDoesNotExist()
            onNodeWithText("Will update on Wi-Fi").assertDoesNotExist()
            onAllNodes(isToggleable()).assertCountEquals(1)
            onNodeWithText("Update 1 agent").performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastWaitForWifi != null }
            assertEquals(false, offered.lastWaitForWifi)
        }
    }

    @Test
    fun `B-UPD-016 turning Wi-Fi only off at text 1_3 keeps its row height and moves nothing`() =
        runSheet(redrawFiveOffer(), fontScale = 1.3f) {
            val wifi = onNode(isToggleable() and hasText("Wi-Fi only", substring = true))
            val before = wifi.fetchSemanticsNode().size.height
            wifi.performClick()
            waitForIdle()
            assertEquals(before, wifi.fetchSemanticsNode().size.height, "Wi-Fi only height changed when turned off")
            mainClock.advanceTimeBy(1_500)
            wifi.performClick()
            waitForIdle()
            assertEquals(before, wifi.fetchSemanticsNode().size.height, "Wi-Fi only height changed when turned on")
        }

    @Test
    fun `B-UPD-017 on mobile data with every agent off the Wi-Fi only row is gone`() {
        runSheet(redrawFiveOffer()) {
            listOf("Claude Code", "Codex", "Antigravity", "Qwen Code").forEach { switchOff(it) }
            onNodeWithText("Update 1 agent").assertExists()
            onNode(isToggleable() and hasText("Wi-Fi only", substring = true)).assertExists()
        }
        runSheet(noLockedFiveOffer()) {
            listOf("Claude Code", "Codex", "Antigravity", "Gemini CLI", "Qwen Code").forEach { switchOff(it) }
            onNodeWithText("Keep these versions").assertExists()
            onNodeWithText("Wi-Fi only").assertDoesNotExist()
            onNodeWithText("Will update on Wi-Fi", substring = true, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    private fun KitUiTest.switchOff(name: String) {
        onNode(isToggleable() and hasText(name, substring = true)).performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
    }

    private fun noLockedFiveOffer(): AgentUpdateOffer =
        redrawFiveOffer().let { offer ->
            offer.copy(
                items = offer.items.map { if (it.id == AgentId("gemini")) it.copy(keepAllowed = true) else it },
            )
        }

    private companion object {
        const val HELPER_TOO_NEW = "Its version can't work with the new agent helper, so it can't be kept."
        const val METERED_LINE = "You're on mobile data. The updates download over it."
    }
}
