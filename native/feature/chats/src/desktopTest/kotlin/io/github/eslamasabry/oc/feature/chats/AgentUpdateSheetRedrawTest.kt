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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
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
        motion: MotionLevel = MotionLevel.Off,
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
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = motion),
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

                    override fun proceedAgentUpdates(updateAll: Boolean) {
                        model.applyChoices(updateAll)
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
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Keep these versions").assertExists()
            onNodeWithText("Wi-Fi only").assertDoesNotExist()
            onNodeWithText("Will update on Wi-Fi", substring = true, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `B-UPD-018 turning the last agent off folds the total, Wi-Fi only and the helper away without a jump`() =
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.dropLast(1).forEach { switchOff(it) }
            val fold = walkSwitch("Qwen Code")
            assertTrue(fold.heights.last() < fold.heights.first(), "the sheet kept its height: ${fold.heights}")
            assertTrue(
                fold.heights.distinct().size > 2,
                "the total, Wi-Fi only and the helper left in one frame: ${fold.heights}",
            )
            fold.heights.zipWithNext().forEach { (was, now) ->
                assertTrue(now <= was + ONE_DP_PX, "the sheet's height rose again while it shrank: ${fold.heights}")
            }
            fold.noRowForOneFrame("the fold")
            fold.buttonStaysPut()
            onNodeWithText("Keep these versions").assertExists()
            onNodeWithText("Download").assertDoesNotExist()
            onNodeWithText("Wi-Fi only").assertDoesNotExist()
            onNodeWithText("Agent helper", substring = true).assertDoesNotExist()
        }

    @Test
    fun `B-UPD-019 turning an agent back on unfolds the total, Wi-Fi only and the helper the same way`() =
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            val unfold = walkSwitch("Qwen Code")
            assertTrue(unfold.heights.last() > unfold.heights.first(), "the sheet did not grow back: ${unfold.heights}")
            assertTrue(
                unfold.heights.distinct().size > 2,
                "the total, Wi-Fi only and the helper came back in one frame: ${unfold.heights}",
            )
            unfold.heights.zipWithNext().forEach { (was, now) ->
                assertTrue(now + ONE_DP_PX >= was, "the sheet's height fell again while it grew: ${unfold.heights}")
            }
            unfold.noRowForOneFrame("the unfold")
            unfold.buttonStaysPut()
            onNodeWithText("Update 1 agent").assertExists()
            onNodeWithText("Wi-Fi only").assertExists()
            onNodeWithText("Agent helper", substring = true).assertExists()
        }

    @Test
    fun `B-UPD-020 the total keeps its amount while it folds away`() =
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.dropLast(1).forEach { switchOff(it) }
            val fold = walkSwitch("Qwen Code")
            val kept = fold.before.filter { it.contains("About ") }
            assertEquals(1, kept.size, "expected one total before the tap: ${fold.before}")
            fold.frames.forEachIndexed { frame, words ->
                words.filter { it.contains("About ") }.forEach { amount ->
                    assertEquals(kept.single(), amount, "the total's amount changed while it folded (frame $frame)")
                }
            }
        }

    @Test
    fun `B-UPD-021 a needed part that leaves while another stays folds away`() =
        runSheet(alwaysNeededOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.dropLast(1).forEach { switchOff(it) }
            val fold = walkSwitch("Qwen Code")
            val helperFrames = fold.frames.count { words -> words.any { it.contains("Agent helper") } }
            assertTrue(helperFrames >= 2, "the agent helper left in $helperFrames frame(s) after the tap")
            onNodeWithText("Node.js", substring = true).assertExists()
        }

    @Test
    fun `B-UPD-022 turning an agent switch off still shows both versions and size with no Stays`() =
        runSheet(redrawFiveOffer()) {
            val codex = onNode(isToggleable() and hasText("Codex", substring = true))
            codex.performScrollTo().performClick()
            waitForIdle()
            codex.assertIsOff()
            onNodeWithText("Stays", substring = true, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("0.171.0", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText("0.174.0", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText(PhoneSizes.text(48_000_000L), substring = true, useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-UPD-023 with every agent off Update all with size is primary above Keep and Not now`() =
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Stays", substring = true, useUnmergedTree = true).assertDoesNotExist()
            val updateAll = onNodeWithText("Update all · ${PhoneSizes.text(660_000_000L)}")
            val keep = onNodeWithText("Keep these versions")
            val notNow = onNodeWithText("Not now")
            updateAll.assertExists()
            keep.assertExists()
            notNow.assertExists()
            val updateAllBounds = updateAll.fetchSemanticsNode().boundsInRoot
            val keepBounds = keep.fetchSemanticsNode().boundsInRoot
            val notNowBounds = notNow.fetchSemanticsNode().boundsInRoot
            assertTrue(
                updateAllBounds.bottom <= keepBounds.top && keepBounds.bottom <= notNowBounds.top,
                "expected Update all ($updateAllBounds) above Keep ($keepBounds) above Not now ($notNowBounds)",
            )
        }

    @Test
    fun `B-UPD-024 tapping Update all when every agent is off turns all on and starts`() {
        val offered = RedrawOffered(noLockedFiveOffer())
        runSheet(noLockedFiveOffer(), offered = offered, motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Update all · ${PhoneSizes.text(660_000_000L)}").performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastChoices != null }
            assertEquals(5, offered.lastChoices?.size)
            assertTrue(offered.lastChoices?.values?.all { it == AgentUpdateChoice.Update } == true)
            assertEquals(true, offered.lastWaitForWifi)
        }
    }

    @Test
    fun `B-UPD-025 tapping Keep these versions when every agent is off keeps all`() {
        val offered = RedrawOffered(noLockedFiveOffer())
        runSheet(noLockedFiveOffer(), offered = offered, motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Keep these versions").performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastChoices != null }
            assertEquals(5, offered.lastChoices?.size)
            assertTrue(offered.lastChoices?.values?.all { it == AgentUpdateChoice.Keep } == true)
        }
    }

    /** [noLockedFiveOffer] with a second needed part that is always needed, right after the agent helper. */
    private fun alwaysNeededOffer(): AgentUpdateOffer =
        noLockedFiveOffer().let { offer ->
            val helper = offer.items.first()
            val node =
                helper.copy(
                    id = AgentId("node"),
                    name = "Node.js",
                    fromVersion = "22.11.0",
                    toVersion = "22.12.0",
                    downloadBytes = 40_000_000L,
                    installedBytes = 40_000_000L,
                    neededWhen = AgentNeedRule.Always,
                )
            offer.copy(items = listOf(helper, node) + offer.items.drop(1))
        }

    private fun KitUiTest.switchOff(name: String) {
        onNode(isToggleable() and hasText(name, substring = true)).performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
    }

    private fun KitUiTest.walkSwitch(name: String): Walk {
        val last = onNode(isToggleable() and hasText(name, substring = true))
        last.performScrollTo()
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
        val before = sheetWords()
        val heights = mutableListOf(sheetHeight())
        val frames = mutableListOf<Set<String>>()
        val buttonTops = mutableListOf<Float>()
        mainClock.autoAdvance = false
        last.performClick()
        repeat(FOLD_FRAMES) {
            mainClock.advanceTimeByFrame()
            heights += sheetHeight()
            val now = sheetWords()
            frames += now
            buttonTops +=
                MAIN_BUTTON_WORDS.filter { it in now }
                    .firstNotNullOfOrNull { label ->
                        onAllNodesWithText(label).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
                    }
                    ?: Float.NaN
        }
        mainClock.autoAdvance = true
        waitForIdle()
        return Walk(before, heights, frames, buttonTops)
    }

    private fun KitUiTest.sheetHeight(): Int =
        onNodeWithTag("kit-sheet", useUnmergedTree = true).fetchSemanticsNode().size.height

    private fun KitUiTest.sheetWords(): Set<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapTo(mutableSetOf()) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            }

    private class Walk(
        val before: Set<String>,
        val heights: List<Int>,
        val frames: List<Set<String>>,
        val buttonTops: List<Float>,
    ) {
        fun noRowForOneFrame(what: String) {
            (before + frames.flatten()).forEach { words ->
                val shown = booleanArrayOf(words in before) + BooleanArray(frames.size) { words in frames[it] }
                runs(shown).drop(1).dropLast(1).forEach { (value, start, end) ->
                    assertTrue(
                        end > start,
                        "$what: \"$words\" was ${if (value) "shown" else "gone"} for one frame (frame $start)",
                    )
                }
            }
        }

        fun buttonStaysPut() {
            val tops = buttonTops.filter { !it.isNaN() }
            assertTrue(tops.size > 1, "the main button was not found on the frames: $buttonTops")
            val low = tops.minOrNull() ?: 0f
            val high = tops.maxOrNull() ?: 0f
            assertTrue(high - low <= ONE_DP_PX, "the main button moved while its words changed: $tops")
        }

        private fun runs(shown: BooleanArray): List<Triple<Boolean, Int, Int>> {
            val found = mutableListOf<Triple<Boolean, Int, Int>>()
            var start = 0
            for (i in 1..shown.size) {
                if (i == shown.size || shown[i] != shown[start]) {
                    found += Triple(shown[start], start, i - 1)
                    start = i
                }
            }
            return found
        }
    }

    private companion object {
        const val HELPER_TOO_NEW = "Its version can't work with the new agent helper, so it can't be kept."
        const val METERED_LINE = "You're on mobile data. The updates download over it."
        const val FOLD_FRAMES = 40
        const val ONE_DP_PX = 3
        val ALL_AGENTS = listOf("Claude Code", "Codex", "Antigravity", "Gemini CLI", "Qwen Code")
        val MAIN_BUTTON_WORDS = listOf("Update 1 agent", "Keep these versions")
    }
}
