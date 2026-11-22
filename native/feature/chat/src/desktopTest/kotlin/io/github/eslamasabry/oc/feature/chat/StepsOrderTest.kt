package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StepsOrderTest {
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(
            platform = DesktopKitPlatform(),
            appearance = KitAppearance(motion = MotionLevel.Off),
            localeTag = "en",
            stillFrame = true,
        ) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
        }
    }

    @Test
    fun `STEPS-ORDER-001 work after a paragraph is drawn under it, before the next`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent {
                Root {
                    ChatScreen(StepsOrderScene.doneState(), actions, composer, facts = StepsOrderScene.doneFacts())
                }
            }
            waitForIdle()
            val summaries =
                onAllNodesWithTag("chat-work-summary", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .map { it.boundsInRoot }
                    .sortedBy { it.top }
            assertEquals(2, summaries.size, "expected two work summary rows: $summaries")
            val a1 = boundsOfText(StepsOrderScene.A1)
            val a2 = boundsOfText(StepsOrderScene.A2)
            assertTrue(summaries[0].bottom <= a1.top, "first summary above A1: ${summaries[0]} vs $a1")
            assertTrue(summaries[1].top >= a1.bottom, "second summary below A1: ${summaries[1]} vs $a1")
            assertTrue(summaries[1].bottom <= a2.top, "second summary above A2: ${summaries[1]} vs $a2")
        }

    @Test
    fun `STEPS-ORDER-002 a folded group says what it did in the order it did it`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent {
                Root {
                    ChatScreen(StepsOrderScene.doneState(), actions, composer, facts = StepsOrderScene.doneFacts())
                }
            }
            waitForIdle()
            onNodeWithText("Ran 1 command · read 1 file").assertIsDisplayed()
            onNodeWithText("Edited 1 file · ran 1 command").assertIsDisplayed()
        }

    @Test
    fun `STEPS-ORDER-003 while the reply runs a new step appears under the newest paragraph`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent {
                Root {
                    ChatScreen(
                        StepsOrderScene.runningState(),
                        actions,
                        composer,
                        facts = StepsOrderScene.runningFacts(),
                    )
                }
            }
            waitForIdle()
            val a1 = boundsOfText(StepsOrderScene.A1)
            val edit = boundsOfText("Edit", substring = true)
            assertTrue(edit.top >= a1.bottom, "running edit below A1: $edit vs $a1")
            val summaries =
                onAllNodesWithTag("chat-work-summary", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .map { it.boundsInRoot }
            assertEquals(1, summaries.size, "expected one folded summary above A1: $summaries")
            assertTrue(summaries[0].bottom <= a1.top, "summary above A1: ${summaries[0]} vs $a1")
        }

    @Test
    fun `STEPS-ORDER-004 a thought with no step of its own is not hidden`() =
        kitUiTest(412, 2600) {
            val facts = StepsOrderScene.thoughtAloneFacts()
            assertFalse(
                "m2/r0" in facts.reading.hiddenPartKeys,
                "thought without a step must stay visible: ${facts.reading.hiddenPartKeys}",
            )
        }

    @Test
    fun `STEPS-ORDER-005 an opened folded group keeps one identity while steps arrive during a running turn`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var stage by mutableStateOf(0)
            setContent {
                Root {
                    ChatScreen(
                        StepsOrderScene.liveReadsState(stage),
                        actions,
                        composer,
                        facts = StepsOrderScene.liveReadsFacts(stage),
                    )
                }
            }
            waitForIdle()
            // Stage 0: 8 completed reads while the model thinks; one folded line, opened by a single tap.
            onAllNodesWithText("Read 8 files", useUnmergedTree = true).assertCountEquals(1)
            onNodeWithText("Read 8 files").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            // Opening never repeats "Read 8 files" as its own child, and exposes the first file step directly.
            onAllNodesWithText("Read 8 files", useUnmergedTree = true).assertCountEquals(1)
            onNodeWithTag("chat-step-r1", useUnmergedTree = true).assertIsDisplayed()
            // Stage 1: more reads and a running command arrive; the same group grows in count, never swaps control.
            stage = 1
            waitForIdle()
            onAllNodesWithText("Hide earlier steps").assertCountEquals(0)
            onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag("chat-step-run-r1", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Read 15 files").assertIsDisplayed()
            onNodeWithTag("chat-step-r1", useUnmergedTree = true).assertIsDisplayed()
            // Stage 2: command ends while the turn still runs; the opened group stays itself, never flips to summary.
            stage = 2
            waitForIdle()
            onAllNodesWithText("Hide earlier steps").assertCountEquals(0)
            onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag("chat-step-run-r1", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Read 15 files").assertIsDisplayed()
            onNodeWithTag("chat-step-r1", useUnmergedTree = true).assertIsDisplayed()
        }

    @Test
    fun `STEPS-ORDER-006 a finished single-run summary opens to its steps without repeating itself`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent {
                Root {
                    ChatScreen(
                        StepsOrderScene.doneSingleRunState(),
                        actions,
                        composer,
                        facts = StepsOrderScene.doneSingleRunFacts(),
                    )
                }
            }
            waitForIdle()
            onAllNodesWithText("Read 8 files", useUnmergedTree = true).assertCountEquals(1)
            activate("chat-work-summary")
            waitForIdle()
            onAllNodesWithText("Read 8 files", useUnmergedTree = true).assertCountEquals(1)
            onNodeWithTag("chat-step-r1", useUnmergedTree = true).assertIsDisplayed()
        }
}
