package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.ledger.StepState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.StepEntry
import io.github.eslamasabry.oc.feature.chat.presentation.StepFamily
import io.github.eslamasabry.oc.feature.chat.presentation.StepListRules
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Owner 2026-11-14 on a long agent turn: "Your tool calls grouping is shit … clipping flashing and icons are tiny and un
 * recognizable and organized. And when collapsed it takes a huge space." The owner's turn (StepsCraftScene, made-up
 * names of the same shapes): commands one after another are one row with a count that opens in place, every row says in
 * plain words what was done, never a raw command line or a tool id, the agent's words between steps are whole sentences,
 * and a folded run is exactly one row.
 */
@OptIn(ExperimentalTestApi::class)
class StepsCraftTest {
    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    @Composable
    private fun Root(content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = MotionLevel.Off), localeTag = "en", stillFrame = true) {
            CompositionLocalProvider(LocalKitRedactor provides Redactor(), content = content)
        }
    }

    @Test
    fun `STEPS-CRAFT-010 commands one after another fold into one row each run, words and other steps keep their place`() {
        val f = StepsCraftScene.doneFacts()
        val work = assertNotNull(f.work[StepsCraftScene.TURN])
        val rows = StepListRules.entries(work, f.reading.workEntries[StepsCraftScene.TURN].orEmpty())
        // w1, c1+c2, w2, c3..c5, w3, search, stop, c6, w4, c7, w5, c8+c9, w6, c10+c11
        assertEquals(14, rows.size, rows.joinToString { it.key })
        val runs = rows.filterIsInstance<StepEntry.Run>()
        assertEquals(listOf(2, 3, 2, 2), runs.map { it.steps.size })
        assertTrue(runs.all { it.family == StepFamily.Commands })
        assertEquals(listOf("step:c1", "step:c3", "step:c8", "step:c10"), runs.map { it.key }, "a run keeps its first step's row key")
        assertEquals(6, rows.count { it is StepEntry.Detail }, "the six sentences between steps")
        assertEquals(listOf("t1", "t2", "c6", "c7"), rows.filterIsInstance<StepEntry.Step>().map { it.step.partId })
        val fold = assertNotNull(StepListRules.cut(rows).fold)
        assertEquals(9, fold.hidden)
    }

    @Test
    fun `STEPS-CRAFT-010 an edit never folds into a run, one step is a row of its own, a step that waits stays alone`() {
        val f = StepsCraftScene.runningFacts(arrived = true)
        val work = assertNotNull(f.work[StepsCraftScene.TURN])
        val rows = StepListRules.entries(work, emptyList())
        // While it runs the words are the reply's prose: c1..c5, search, stop, c6..c11, the edit.
        assertEquals(listOf(5, 6), rows.filterIsInstance<StepEntry.Run>().map { it.steps.size })
        val edit = assertIs<StepEntry.Step>(rows.last())
        assertEquals("e1", edit.step.partId)
        val single = StepListRules.runs(listOf(StepEntry.Step(work.steps.first())))
        assertIs<StepEntry.Step>(single.single(), "one command is a step, not a run of one")
        // A step that waits for the person never hides inside a run: its question is drawn under it.
        val asking = work.steps.take(2).map { it.copy(state = StepState.Running) }
        val split = StepListRules.runs(asking.map { StepEntry.Step(it) }, waiting = true)
        assertTrue(split.all { it is StepEntry.Step }, split.toString())
    }

    @Test
    fun `STEPS-CRAFT-011 every row says what it did in plain words, tools by name, the words whole, and a run opens in place`() =
        runDesktopComposeUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(StepsCraftScene.doneState(), actions, composer, facts = StepsCraftScene.doneFacts()) } }
            onNodeWithText("Show 9 earlier steps").assertIsDisplayed().performClick()
            waitForIdle()
            // No raw command line and no tool id is ever a row's words.
            onAllNodesWithText("cd /home", substring = true).assertCountEquals(0)
            onAllNodesWithText("toolchain", substring = true).assertCountEquals(0)
            onAllNodesWithText("select:", substring = true).assertCountEquals(0)
            onAllNodesWithText("Taskstop", substring = true).assertCountEquals(0)
            onNodeWithText("Load tools").assertIsDisplayed()
            onNodeWithText("Task stop").assertIsDisplayed()
            onNodeWithText("Stopped a background task").assertIsDisplayed()
            onNodeWithText("Stopped a process").assertIsDisplayed()
            onNodeWithText("Read a file").assertIsDisplayed()
            // The agent's words between steps, each a whole sentence: its last words are there (owner: "I", "Two find").
            for (w in StepsCraftScene.words) onNodeWithText(w.takeLast(20), substring = true).assertIsDisplayed()
            // Runs: a count, and what the commands did.
            onAllNodesWithText("Ran 2 commands").assertCountEquals(3)
            onNodeWithText("Ran tests · Searched files").assertIsDisplayed()
            onNodeWithText("Ran 3 commands").assertIsDisplayed().performClick()
            waitForIdle()
            onNodeWithText("Built the project").assertIsDisplayed()
            onAllNodesWithText("Ran adb").assertCountEquals(2)
        }

    @Test
    fun `STEPS-CRAFT-012 a folded run is exactly one row, and the folded list ends right where its last row does`() =
        runDesktopComposeUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(StepsCraftScene.doneState(), actions, composer, facts = StepsCraftScene.doneFacts()) } }
            waitForIdle()
            val run = boundsOfTag("chat-step-run-c10")
            assertEquals(32f, run.height, 0.6f, "a folded run is one 32 dp row: $run")
            // Owner's picture: the folded line was followed by about 90 dp of nothing. The list ends on its last row.
            val list = boundsOfTag("chat-work-line")
            assertEquals(run.bottom, list.bottom, 0.6f, "nothing is kept under the last row: $run in $list")
            val reply = boundsOfText(StepsCraftScene.REPLY)
            assertTrue(reply.top - list.bottom < 48f, "the reply follows the steps without a gap: ${reply.top - list.bottom}")
        }

    @Test
    fun `STEPS-CRAFT-013 a running run says what runs now in plain words and keeps its count`() =
        runDesktopComposeUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(StepsCraftScene.runningState(), actions, composer, facts = StepsCraftScene.runningFacts()) } }
            waitForIdle()
            // The eleventh command is a search: the run says so while it runs.
            onNodeWithText("Searching files").assertIsDisplayed()
            onAllNodesWithText("Ran 5 commands").assertCountEquals(2)
            onAllNodesWithText("cd /home", substring = true).assertCountEquals(0)
        }
}
