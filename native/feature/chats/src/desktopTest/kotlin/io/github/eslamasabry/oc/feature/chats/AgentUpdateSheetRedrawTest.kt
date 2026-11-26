package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetModel
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSubmit
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitPrimaryButtonKey
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
    ): Unit = runSheetInLocale(offer, "en", SheetConfig(fontScale, motion), offered, block)

    private fun runSheetInLocale(
        offer: AgentUpdateOffer,
        localeTag: String,
        config: SheetConfig,
        offered: RedrawOffered = RedrawOffered(offer),
        block: suspend KitUiTest.() -> Unit,
    ) {
        val previous = Locale.getDefault()
        val scope = CoroutineScope(Job())
        try {
            Locale.setDefault(Locale.forLanguageTag(localeTag))
            val model = AgentUpdateSheetModel(offered, scope)
            val actions = actionsFor(model)
            kitUiTest(900, 2000) {
                setContent {
                    val updates by model.ui.collectAsState(initial = remember { model.current() })
                    CompositionLocalProvider(LocalDensity provides Density(2.5f, config.fontScale)) {
                        ChatEnvironment {
                            KitTheme(
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = config.motion),
                                platform = DesktopKitPlatform(),
                                localeTag = localeTag,
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

                    override fun proceedAgentUpdates(submit: AgentUpdateSubmit) {
                        model.applyChoices(submit)
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
            onNodeWithText("Update 4 selected · ${PhoneSizes.text(612_000_000L)}").assertExists()
        }

    @Test
    fun `B-UPD-009 an agent that cannot be kept shows a locked on switch and says why`() =
        runSheet(redrawFiveOffer()) {
            val gemini = onNode(isToggleable() and hasText("Gemini CLI", substring = true))
            gemini.assertIsOn().assertIsNotEnabled()
            onNodeWithText(HELPER_TOO_NEW, substring = true, useUnmergedTree = true).assertExists()
            gemini.performScrollTo().performClick()
            waitForIdle()
            onNodeWithText(updateAllFive()).assertExists()
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
            onNodeWithText("Update 4 selected · ${PhoneSizes.text(612_000_000L)}").assertExists()
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
            onNodeWithText(updateAllFive()).performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastWaitForWifi != null }
            assertEquals(true, offered.lastWaitForWifi)
        }
    }

    @Test
    fun `B-UPD-014 turning Wi-Fi only off reveals mobile notice without flicker and decides mobile download`() {
        val offered = RedrawOffered(redrawFiveOffer())
        runSheet(redrawFiveOffer(), offered = offered, motion = MotionLevel.Full) {
            val wifi = onNode(isToggleable() and hasText("Wi-Fi only", substring = true))
            wifi.performClick()
            waitForIdle()
            wifi.assertIsOff()
            onNodeWithText(METERED_LINE, substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText(updateAllFive()).performClick()
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
            onNodeWithText("Update all · ${PhoneSizes.text(422_000_000L)}").performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastWaitForWifi != null }
            assertEquals(false, offered.lastWaitForWifi)
        }
    }

    @Test
    fun `B-UPD-016 at text 1_3 Wi-Fi only row has no empty band when on and toggles smoothly`() =
        runSheet(redrawFiveOffer(), fontScale = 1.3f, motion = MotionLevel.Full) {
            val wifi = onNode(isToggleable() and hasText("Wi-Fi only", substring = true))
            val onHeight = wifi.fetchSemanticsNode().size.height
            wifi.performClick()
            waitForIdle()
            val offHeight = wifi.fetchSemanticsNode().size.height
            assertTrue(
                onHeight < offHeight,
                "expected compact 2-line Wi-Fi row when on ($onHeight) < 3-line metered notice when off ($offHeight)",
            )
            mainClock.advanceTimeBy(1_500)
            wifi.performClick()
            waitForIdle()
            assertEquals(onHeight, wifi.fetchSemanticsNode().size.height, "Wi-Fi only height did not return when on")
        }

    @Test
    fun `B-UPD-017 on mobile data with every agent off the Wi-Fi only row is gone`() {
        runSheet(redrawFiveOffer()) {
            listOf("Claude Code", "Codex", "Antigravity", "Qwen Code").forEach { switchOff(it) }
            onNodeWithText("Update 1 selected · ${PhoneSizes.text(401_000_000L)}").assertExists()
            onNode(isToggleable() and hasText("Wi-Fi only", substring = true)).assertExists()
        }
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText(updateAllFive()).assertExists()
            onNodeWithText("Keep these versions").assertDoesNotExist()
            onNodeWithText("Skip these versions").assertExists()
            onNodeWithText("Wi-Fi only").assertDoesNotExist()
            onNodeWithText("Will update on Wi-Fi", substring = true, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `B-UPD-018 turning the last agent off folds the total, Wi-Fi only and the helper away without a jump`() =
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.dropLast(1).forEach { switchOff(it) }
            val notNowBefore = onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot
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
            assertEquals(notNowBefore, onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot)
            onNodeWithText(updateAllFive()).assertExists()
            onNodeWithText("Keep these versions").assertDoesNotExist()
            onNodeWithText("Download").assertDoesNotExist()
            onNodeWithText("Wi-Fi only").assertDoesNotExist()
            onNodeWithText("Agent helper", substring = true).assertDoesNotExist()
        }

    @Test
    fun `B-UPD-019 turning an agent back on unfolds the total, Wi-Fi only and the helper the same way`() =
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            val notNowBefore = onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot
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
            assertEquals(notNowBefore, onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot)
            onNodeWithText("Update 1 selected · ${PhoneSizes.text(385_000_000L)}").assertExists()
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
            codex.performScrollTo()
            waitForIdle()
            val pairOn = onNodeWithText("0.171.0", substring = true, useUnmergedTree = true).fetchSemanticsNode()
            val spokenOn = spokenLabel(pairOn)
            assertTrue(
                spokenOn.contains("From 0.171.0 to 0.174.0", ignoreCase = true),
                "expected spoken 'From 0.171.0 to 0.174.0' when ON, got: '$spokenOn'",
            )
            val onPeak = peakLuminance(pairOn.boundsInRoot)

            codex.performClick()
            waitForIdle()
            codex.assertIsOff()
            onNodeWithText("Stays", substring = true, useUnmergedTree = true).assertDoesNotExist()
            val pairOff = onNodeWithText("0.171.0", substring = true, useUnmergedTree = true).fetchSemanticsNode()
            val spokenOff = spokenLabel(pairOff)
            assertTrue(
                spokenOff.contains("From 0.171.0 to 0.174.0", ignoreCase = true),
                "expected spoken 'From 0.171.0 to 0.174.0' when OFF, got: '$spokenOff'",
            )
            onNodeWithText("0.174.0", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText(PhoneSizes.text(48_000_000L), substring = true, useUnmergedTree = true).assertExists()
            val offPeak = peakLuminance(pairOff.boundsInRoot)
            assertTrue(
                onPeak > offPeak + 0.05f,
                "expected ON version pair ($onPeak) brighter than quieter OFF colour ($offPeak)",
            )
        }

    @Test
    fun `B-UPD-023 two buttons follow selection with size and Keep these versions is gone`() {
        runSheet(noLockedFiveOffer(), motion = MotionLevel.Full) {
            val allBtn = onNodeWithText(updateAllFive())
            val notNow = onNodeWithText("Not now")
            allBtn.assertExists()
            notNow.assertExists()
            onNodeWithText("Keep these versions").assertDoesNotExist()
            val primarySlot = allBtn.fetchSemanticsNode().boundsInRoot
            val notNowSlot = notNow.fetchSemanticsNode().boundsInRoot
            assertTrue(primarySlot.bottom <= notNowSlot.top, "expected Update all above Not now")

            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Stays", substring = true, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("Keep these versions").assertDoesNotExist()
            onNodeWithText("Skip these versions").assertExists()
            assertEquals(primarySlot, onNodeWithText(updateAllFive()).fetchSemanticsNode().boundsInRoot)
            assertEquals(notNowSlot, onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot)

            switchOff("Claude Code")
            val oneSize = PhoneSizes.text(394_000_000L)
            onNodeWithText("About $oneSize", useUnmergedTree = true).assertExists()
            val oneBtn = onNodeWithText("Update 1 selected · $oneSize")
            oneBtn.assertExists()
            assertEquals(primarySlot, oneBtn.fetchSemanticsNode().boundsInRoot)
            assertEquals(notNowSlot, onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot)

            switchOff("Codex")
            val twoSize = PhoneSizes.text(442_000_000L)
            onNodeWithText("About $twoSize", useUnmergedTree = true).assertExists()
            val twoBtn = onNodeWithText("Update 2 selected · $twoSize")
            twoBtn.assertExists()
            assertEquals(primarySlot, twoBtn.fetchSemanticsNode().boundsInRoot)
            assertEquals(notNowSlot, onNodeWithText("Not now").fetchSemanticsNode().boundsInRoot)
        }
        assertRtlWrapBoundsFixed()
    }

    private fun assertRtlWrapBoundsFixed() =
        runSheetInLocale(partialUnknownOffer(), "ar", SheetConfig(fontScale = 1.3f, motion = MotionLevel.Full)) {
            ALL_AGENTS.filter { it != "Claude Code" }.forEach { switchOff(it) }
            val primary = SemanticsMatcher.keyIsDefined(KitPrimaryButtonKey)
            val notNowAr = "ليس الآن"
            val primaryBefore = onNode(primary).fetchSemanticsNode().boundsInRoot
            val notNowBefore = onNodeWithText(notNowAr).fetchSemanticsNode().boundsInRoot
            val codex = onNode(isToggleable() and hasText("Codex", substring = true))
            codex.performScrollTo()
            waitForIdle()
            mainClock.advanceTimeBy(1_500)
            mainClock.autoAdvance = false
            codex.performClick()
            repeat(SWAP_FRAMES) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(
                    primaryBefore,
                    onNode(primary).fetchSemanticsNode().boundsInRoot,
                    "primary button bounds moved on Arabic 1.3x swap frame $frame",
                )
                assertEquals(
                    notNowBefore,
                    onNodeWithText(notNowAr).fetchSemanticsNode().boundsInRoot,
                    "Not now button bounds moved on Arabic 1.3x swap frame $frame",
                )
            }
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(primaryBefore, onNode(primary).fetchSemanticsNode().boundsInRoot)
            assertEquals(notNowBefore, onNodeWithText(notNowAr).fetchSemanticsNode().boundsInRoot)
            val layouts = mutableListOf<TextLayoutResult>()
            onNode(primary).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(
                layouts.any { it.lineCount >= 2 },
                "expected wrapped Arabic primary label at 360 dp text 1.3, got lines=${layouts.map { it.lineCount }}",
            )
        }

    @Test
    fun `B-UPD-024 tapping Update all when every agent is off turns all on and starts`() {
        val offered = RedrawOffered(noLockedFiveOffer())
        runSheet(noLockedFiveOffer(), offered = offered, motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText(updateAllFive()).performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastChoices != null }
            assertEquals(5, offered.lastChoices?.size)
            assertTrue(offered.lastChoices?.values?.all { it == AgentUpdateChoice.Update } == true)
            assertEquals(true, offered.lastWaitForWifi)
        }
    }

    @Test
    fun `B-UPD-025 tapping Skip these versions under the list keeps all`() {
        val offered = RedrawOffered(noLockedFiveOffer())
        runSheet(noLockedFiveOffer(), offered = offered, motion = MotionLevel.Full) {
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Skip these versions").performScrollTo().performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastChoices != null }
            assertEquals(5, offered.lastChoices?.size)
            assertTrue(offered.lastChoices?.values?.all { it == AgentUpdateChoice.Keep } == true)
        }
    }

    @Test
    fun `B-UPD-026 Update all from zero selections waits for Wi-Fi when the choice is hidden`() {
        val offered = RedrawOffered(noLockedFiveOffer())
        runSheet(noLockedFiveOffer(), offered = offered, motion = MotionLevel.Full) {
            val wifi = onNode(isToggleable() and hasText("Wi-Fi only", substring = true))
            wifi.performClick()
            waitForIdle()
            wifi.assertIsOff()
            mainClock.advanceTimeBy(1_500)
            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Wi-Fi only").assertDoesNotExist()
            onNodeWithText(updateAllFive()).performClick()
            waitUntil(timeoutMillis = 2_000) { offered.lastWaitForWifi != null }
            assertEquals(true, offered.lastWaitForWifi)
            assertEquals(5, offered.lastChoices?.size)
            assertTrue(offered.lastChoices?.values?.all { it == AgentUpdateChoice.Update } == true)
        }
    }

    @Test
    fun `B-UPD-027 a partial download size is labelled as a lower bound`() {
        runSheet(partialUnknownOffer()) {
            val allKnown = PhoneSizes.text(612_000_000L)
            onNodeWithText("At least $allKnown", useUnmergedTree = true).assertExists()
            onNodeWithText("Update all · At least $allKnown").assertExists()

            ALL_AGENTS.forEach { switchOff(it) }
            onNodeWithText("Update all · At least $allKnown").assertExists()

            switchOff("Claude Code")
            switchOff("Codex")
            val partialSelected = PhoneSizes.text(394_000_000L)
            onNodeWithText("At least $partialSelected", useUnmergedTree = true).assertExists()
            onNodeWithText("Update ${Numbers.format(2)} selected · At least $partialSelected").assertExists()
        }
        runSheet(allUnknownOffer()) {
            onNodeWithText("0 B", substring = true, useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("Update all · Download size not known").assertExists()
        }
    }

    @Test
    fun `B-UPD-028 needed rows keep their own reason and refreshed offer order`() {
        val initial = alwaysNeededOffer()
        val offered = RedrawOffered(initial)
        runSheet(initial, offered = offered, motion = MotionLevel.Full) {
            onNodeWithText("Needed for these updates", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText(
                "Your agents need these to keep working.",
                substring = true,
                useUnmergedTree = true,
            ).assertExists()
            val helperTop =
                onNodeWithText("Agent helper", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            val nodeTop =
                onNodeWithText("Node.js", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            assertTrue(helperTop < nodeTop, "expected Agent helper ($helperTop) above Node.js ($nodeTop)")

            val needed = initial.items.filter { it.group == AgentUpdateGroup.Needed }
            val optional = initial.items.filter { it.group == AgentUpdateGroup.Optional }
            offered.mutableOffer.value = initial.copy(items = needed.reversed() + optional)
            waitForIdle()
            val nodeTopAfter =
                onNodeWithText("Node.js", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            val helperTopAfter =
                onNodeWithText("Agent helper", substring = true, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
            assertTrue(
                nodeTopAfter < helperTopAfter,
                "expected refreshed order Node.js ($nodeTopAfter) above Agent helper ($helperTopAfter)",
            )
            onNodeWithText("Needed for these updates", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText(
                "Your agents need these to keep working.",
                substring = true,
                useUnmergedTree = true,
            ).assertExists()

            ALL_AGENTS.dropLast(1).forEach { switchOff(it) }
            val fold = walkSwitch("Qwen Code")
            val helperFrames = fold.frames.count { words -> words.any { it.contains("Agent helper") } }
            assertTrue(helperFrames >= 2, "Agent helper left in $helperFrames frame(s) after the tap")
            onNodeWithText("Node.js", substring = true).assertExists()
            onNodeWithText(
                "Your agents need these to keep working.",
                substring = true,
                useUnmergedTree = true,
            ).assertExists()
        }
    }

    private fun KitUiTest.switchOff(name: String) {
        onNode(isToggleable() and hasText(name, substring = true)).performScrollTo().performClick()
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
    }

    private fun KitUiTest.walkSwitch(name: String): RedrawWalk {
        val last = onNode(isToggleable() and hasText(name, substring = true))
        last.performScrollTo()
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
        val before = sheetWords()
        val heights = mutableListOf(sheetHeight())
        val frames = mutableListOf<Set<String>>()
        val buttonTops = mutableListOf(mainButtonTop(before))
        mainClock.autoAdvance = false
        last.performClick()
        repeat(FOLD_FRAMES) {
            mainClock.advanceTimeByFrame()
            heights += sheetHeight()
            val now = sheetWords()
            frames += now
            buttonTops += mainButtonTop(now)
        }
        mainClock.autoAdvance = true
        waitForIdle()
        return RedrawWalk(before, heights, frames, buttonTops)
    }

    private fun KitUiTest.mainButtonTop(words: Set<String>): Float =
        mainButtonWords()
            .filter { it in words }
            .firstNotNullOfOrNull { label ->
                onAllNodesWithText(label).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top
            }
            ?: Float.NaN

    private fun KitUiTest.sheetHeight(): Int =
        onNodeWithTag("kit-sheet", useUnmergedTree = true).fetchSemanticsNode().size.height

    private fun KitUiTest.sheetWords(): Set<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapTo(mutableSetOf()) { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            }

    private companion object {
        const val HELPER_TOO_NEW = "Its version can't work with the new agent helper, so it can't be kept."
        const val METERED_LINE = "You're on mobile data. The updates download over it."
        const val FOLD_FRAMES = 40
        const val SWAP_FRAMES = 20
        const val ONE_DP_PX = 3
        val ALL_AGENTS = listOf("Claude Code", "Codex", "Antigravity", "Gemini CLI", "Qwen Code")
    }
}

private data class SheetConfig(val fontScale: Float = 1f, val motion: MotionLevel = MotionLevel.Off)

private fun updateAllFive(): String = "Update all · ${PhoneSizes.text(660_000_000L)}"

private fun mainButtonWords(): List<String> =
    listOf("Update 1 selected · ${PhoneSizes.text(385_000_000L)}", updateAllFive())

private fun partialUnknownOffer(): AgentUpdateOffer =
    noLockedFiveOffer().let { offer ->
        offer.copy(
            items = offer.items.map { if (it.id == AgentId("codex")) it.copy(downloadBytes = null) else it },
        )
    }

private fun allUnknownOffer(): AgentUpdateOffer =
    noLockedFiveOffer().let { offer ->
        offer.copy(items = offer.items.map { it.copy(downloadBytes = null) })
    }

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

private fun spokenLabel(node: SemanticsNode): String =
    node.config
        .getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(" ")
        ?.replace("\u2066", "")
        ?.replace("\u2068", "")
        ?.replace("\u2069", "")
        .orEmpty()

@OptIn(ExperimentalTestApi::class)
private fun KitUiTest.peakLuminance(bounds: Rect): Float {
    val map = onRoot().captureToImage().toPixelMap()
    val x0 = bounds.left.toInt().coerceIn(0, map.width - 1)
    val x1 = bounds.right.toInt().coerceIn(x0, map.width - 1)
    val y0 = bounds.top.toInt().coerceIn(0, map.height - 1)
    val y1 = bounds.bottom.toInt().coerceIn(y0, map.height - 1)
    var peak = 0f
    for (y in y0..y1) {
        for (x in x0..x1) {
            val c = map[x, y]
            val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            if (lum > peak) peak = lum
        }
    }
    return peak
}

private class RedrawWalk(
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
        val first = tops.first()
        tops.forEach { top ->
            assertTrue(
                kotlin.math.abs(top - first) <= 3,
                "the main button moved from $first to $top while crossing 1-on <-> 0-on: $tops",
            )
        }
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
