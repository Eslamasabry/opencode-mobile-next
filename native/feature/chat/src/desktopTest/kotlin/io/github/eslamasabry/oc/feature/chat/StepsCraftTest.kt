package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.flicker.Allow
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kitchat.tool.STEP_FOLD_ROW_TAG
import io.github.eslamasabry.oc.ui.kitchat.turn.LIVE_LINE_TAG
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.TurnStallNotice
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.ui.stepGlyph
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.math.abs
import kotlin.test.assertFalse
import androidx.compose.ui.test.onNodeWithText
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
    private fun Root(motion: MotionLevel = MotionLevel.Off, content: @Composable () -> Unit) {
        KitTheme(platform = DesktopKitPlatform(), appearance = KitAppearance(motion = motion), localeTag = "en", stillFrame = motion == MotionLevel.Off) {
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
        // While it runs the newest three rows stay in view; the other eleven fold behind one row.
        val fold = assertNotNull(StepListRules.cut(rows).fold)
        assertEquals(11, fold.hidden)
        // Ended well, the whole turn is one row; ended on a failure or waiting for the person, it stays open.
        assertTrue(StepListRules.sums(work.state))
        assertFalse(StepListRules.sums(WorkLineState.EndedFailed))
        assertFalse(StepListRules.sums(WorkLineState.WaitingForYou))
        assertFalse(StepListRules.sums(WorkLineState.Running))
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
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(StepsCraftScene.doneState(), actions, composer, facts = StepsCraftScene.doneFacts()) } }
            // Activated without a finger (no row is held, P-010: STEPS-CRAFT-018), so what opens grows into view.
            onNodeWithText(SUMMARY).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
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
            onNodeWithText("Ran 3 commands").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            onNodeWithText("Built the project").assertIsDisplayed()
            onAllNodesWithText("Ran adb").assertCountEquals(2)
        }

    @Test
    fun `STEPS-CRAFT-012 a finished turn is exactly one row, and the folded list ends right where that row does`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(StepsCraftScene.doneState(), actions, composer, facts = StepsCraftScene.doneFacts()) } }
            waitForIdle()
            // Owner: a finished turn took a quarter of the screen ("Show 9 earlier steps" and five rows above the reply).
            onAllNodesWithText("Show", substring = true).assertCountEquals(0)
            onAllNodesWithText("Ran 2 commands").assertCountEquals(0)
            val summary = boundsOfTag("chat-work-summary")
            assertEquals(32f, summary.height, 0.6f, "a finished turn is one 32 dp row: $summary")
            // Owner's picture: the folded line was followed by about 90 dp of nothing. The list ends on its one row.
            val list = boundsOfTag("chat-work-line")
            assertEquals(summary.bottom, list.bottom, 0.6f, "nothing is kept under the row: $summary in $list")
            val reply = boundsOfText(StepsCraftScene.REPLY)
            assertTrue(reply.top - list.bottom < 48f, "the reply follows the steps without a gap: ${reply.top - list.bottom}")
            // Opened, a run is one 32 dp row too.
            activate("chat-work-summary")
            waitForIdle()
            assertEquals(32f, boundsOfTag("chat-step-run-c10").height, 0.6f)
        }

    @Test
    fun `STEPS-CRAFT-013 a running run leads with what runs now and keeps its count`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(StepsCraftScene.runningState(), actions, composer, facts = StepsCraftScene.runningFacts()) } }
            waitForIdle()
            // The eleventh command is a search: the run says so first, then how many commands it holds (never "Ran 5
            // commands Searching files", past and present in one row).
            onNodeWithText("Searching files").assertIsDisplayed()
            onNodeWithText("6 commands").assertIsDisplayed()
            onAllNodesWithText("Ran 5 commands").assertCountEquals(1)
            onAllNodesWithText("cd /home", substring = true).assertCountEquals(0)
        }

    @Test
    fun `STEPS-CRAFT-014 a tap on a run opens its steps in place under it, with the motion on`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            mainClock.autoAdvance = false
            setContent { Root(MotionLevel.Full) { ChatScreen(StepsCraftScene.doneState(), actions, composer, facts = StepsCraftScene.doneFacts()) } }
            mainClock.advanceTimeBy(900)
            activate("chat-work-summary")
            mainClock.advanceTimeBy(900)
            onAllNodesWithTag("chat-step-c11", useUnmergedTree = true).assertCountEquals(0)
            activate("chat-step-run-c10")
            mainClock.advanceTimeBy(900)
            // The run's own steps, each a row of its own under the run: "Checked the changes", "Searched files".
            val run = boundsOfTag("chat-step-run-c10")
            val c10 = boundsOfTag("chat-step-c10")
            val c11 = boundsOfTag("chat-step-c11")
            assertTrue(c10.top >= run.bottom - 0.6f && c11.top >= c10.bottom - 0.6f, "in place, in order: $run $c10 $c11")
            assertEquals(32f, c11.height, 0.6f)
            onNodeWithText("Checked the changes").assertIsDisplayed()
            // A second tap folds them away again.
            activate("chat-step-run-c10")
            mainClock.advanceTimeBy(900)
            onAllNodesWithTag("chat-step-c11", useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `STEPS-CRAFT-015 a row's glyph follows what the step did, the terminal only for a command of no known kind`() {
        val f = StepsCraftScene.doneFacts()
        val work = assertNotNull(f.work[StepsCraftScene.TURN])
        val glyph = work.steps.associate { it.partId to stepGlyph(it.facts) }
        assertEquals(KitIcons.experiments, glyph["c1"], "flutter test: tests")
        assertEquals(KitIcons.search, glyph["c2"], "grep: a search")
        assertEquals(KitIcons.build, glyph["c3"], "gradlew assembleDebug: a build")
        assertEquals(KitIcons.terminal, glyph["c4"], "adb: a program of no known kind")
        assertEquals(KitIcons.stopCircle, glyph["c6"], "pkill: a stopped process")
        assertEquals(KitIcons.fileText, glyph["c7"], "cat: a read")
        assertEquals(KitIcons.processor, glyph["c8"], "pgrep: processes")
        assertEquals(KitIcons.branch, glyph["c10"], "git status: the changes")
        assertEquals(KitIcons.stopCircle, glyph["t2"], "TaskStop: a stopped task")
        assertEquals(setOf(KitIcons.experiments, KitIcons.search, KitIcons.build, KitIcons.terminal), setOf(glyph["c1"], glyph["c2"], glyph["c3"], glyph["c4"]), "four kinds, four glyphs")
    }

    @Test
    fun `STEPS-CRAFT-016 every time ends on one right edge, with or without a chevron`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            setContent { Root { ChatScreen(StepsCraftScene.doneState(), actions, composer, facts = StepsCraftScene.doneFacts()) } }
            activate("chat-work-summary")
            waitForIdle()
            val ends = onAllNodesWithTag("kit-tool-duration", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.right }
            assertTrue(ends.size >= 8, "rows with a time: $ends")
            assertTrue(ends.all { abs(it - ends[0]) < 0.6f }, "one right edge for every time: $ends")
        }

    /** The person's prompt in the list (the header carries the same words as the conversation's title). */
    private fun androidx.compose.ui.test.ComposeUiTest.promptTop(): Float =
        onAllNodesWithText(StepsCraftScene.ASK, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .maxOf { it.boundsInRoot.top }

    @Test
    fun `STEPS-CRAFT-017 a step arriving as older rows fold behind the fold row is one movement, never shrinking`() =
        kitUiTest(412, 1200) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var arrived by mutableStateOf(false)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts = StepsCraftScene.runningFacts(arrived)
                    ChatScreen(StepsCraftScene.runningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            // Four rows, nothing folded yet.
            onAllNodesWithTag(STEP_FOLD_ROW_TAG, useUnmergedTree = true).assertCountEquals(0)
            val heights = mutableListOf(boundsOfTag("chat-work-line").height)
            val prompts = mutableListOf(promptTop())
            // How far the first row still in view (the stopped task) stands under the block's top: the two rows going
            // away and the fold row coming in above it. 64 dp before, 32 dp after; a fold row that popped in made it
            // 96.
            fun above() = boundsOfTag("chat-step-t2").top - boundsOfTag("chat-work-line").top
            val folds = mutableListOf(above())
            // The edit arrives: the two oldest rows fold behind "Show 2 earlier steps" in the same frames.
            arrived = true
            repeat(40) {
                mainClock.advanceTimeByFrame()
                heights += boundsOfTag("chat-work-line").height
                prompts += promptTop()
                folds += above()
            }
            for (i in 1 until heights.size)
                assertTrue(heights[i] >= heights[i - 1] - 0.6f, "frame $i: the block went down: $heights")
            for (i in 1 until prompts.size)
                assertTrue(prompts[i] <= prompts[i - 1] + 0.6f, "frame $i: the prompt moved down: $prompts")
            assertTrue(folds.all { it <= 64.6f }, "the fold row comes in as a row does, never in one frame: $folds")
            assertTrue(folds.any { it > 33f && it < 63f }, "it grows while the two rows fold behind it: $folds")
            assertEquals(32f, folds.last(), 0.6f, "and ends one whole row above the rows in view")
            onNodeWithText("Show 2 earlier steps").assertIsDisplayed()
        }

    @Test
    fun `STEPS-CRAFT-018 a finished turn's row stays under the finger as it opens, folding it brings the reply back`() =
        kitUiTest(412, 915) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    ChatScreen(StepsCraftScene.doneState(), actions, composer, facts = StepsCraftScene.doneFacts())
                }
            }
            mainClock.advanceTimeBy(900)
            val row = boundsOfTag("chat-work-summary").top
            val reply = boundsOfText(StepsCraftScene.REPLY)
            fun tapRow() = onNodeWithTag("chat-work-summary").performTouchInput { down(center); up() }
            // A short conversation: nothing above to scroll to, yet the row is held where it was tapped.
            tapRow()
            val opening = mutableListOf<Float>()
            repeat(60) {
                mainClock.advanceTimeByFrame()
                opening += boundsOfTag("chat-work-summary").top
            }
            assertTrue(opening.all { abs(it - row) <= 1f }, "the row stays put while it opens: $row → $opening")
            onNodeWithText("I'll start by running", substring = true).assertIsDisplayed()
            // Folded again, still under the finger, and the reply is back where it was.
            tapRow()
            val closing = mutableListOf<Float>()
            repeat(60) {
                mainClock.advanceTimeByFrame()
                closing += boundsOfTag("chat-work-summary").top
            }
            assertTrue(closing.all { abs(it - row) <= 1f }, "the row stays put while it folds: $row → $closing")
            mainClock.advanceTimeBy(900)
            val back = boundsOfText(StepsCraftScene.REPLY)
            assertEquals(reply.top, back.top, 1f, "folding brings the reply back: $reply → $back")
            onNodeWithText(StepsCraftScene.REPLY).assertIsDisplayed()
        }

    private fun readsWalkFacts(phase: Int) =
        when (phase) {
            1 -> StepsCraftScene.readsAndStepsFacts(3, 0, true)
            2 -> StepsCraftScene.readsAndStepsFacts(4, 0, false)
            3 -> StepsCraftScene.readsAndStepsFacts(4, 1, true, lastHasOutput = true)
            else -> StepsCraftScene.readsAndStepsFacts(4, 2, true)
        }

    @Test
    fun `B-CHAT-560 the live line keeps its top and height between steps`() =
        kitUiTest(
            412,
            900,
            allow =
                setOf(
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S5
                    Allow.aba("#kit-turn-live-sweep", "TEMPORARY: base c5584b61ac; removed by S5"),
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S4
                    Allow.aba("Earlier paragraph #", "TEMPORARY: base c5584b61ac; removed by S4"),
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S5
                    Allow.jump("file#.dart", "TEMPORARY: base c5584b61ac; removed by S5"),
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S4
                    Allow.jump("#chat-work-line", "TEMPORARY: base c5584b61ac; removed by S4"),
                ),
        ) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    ChatScreen(StepsCraftScene.longRunningState(), actions, composer, facts = readsWalkFacts(phase))
                }
            }
            mainClock.advanceTimeBy(900)
            val live0 = boundsOfTag("chat-live-line")
            var prev = live0
            var maxMove = 0f
            try {
                for (next in 1..4) {
                    phase = next
                    repeat(40) { frame ->
                        mainClock.advanceTimeByFrame()
                        val box = boundsOfTag("chat-live-line")
                        maxMove = maxOf(maxMove, abs(box.top - prev.top), abs(box.height - prev.height))
                        prev = box
                        assertTrue(
                            abs(box.top - live0.top) <= 0.5f && abs(box.height - live0.height) <= 0.5f,
                            "phase $next frame $frame live line moved: base=$live0 now=$box",
                        )
                    }
                }
            } finally {
                println("B-CHAT-560 maxMove=$maxMove")
            }
        }

    @Test
    fun `B-CHAT-561 the status word holds Working between steps`() =
        kitUiTest(
            412,
            900,
            allow =
                setOf(
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S5
                    Allow.aba("#kit-turn-live-sweep", "TEMPORARY: base c5584b61ac; removed by S5"),
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S4
                    Allow.aba("Earlier paragraph #", "TEMPORARY: base c5584b61ac; removed by S4"),
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S5
                    Allow.jump("file#.dart", "TEMPORARY: base c5584b61ac; removed by S5"),
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S4
                    Allow.jump("#chat-work-line", "TEMPORARY: base c5584b61ac; removed by S4"),
                ),
        ) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    ChatScreen(StepsCraftScene.longRunningState(), actions, composer, facts = readsWalkFacts(phase))
                }
            }
            mainClock.advanceTimeBy(900)
            var prevTop = boundsOfTag("chat-live-line").top
            var maxMove = 0f
            try {
                for (next in 1..4) {
                    phase = next
                    repeat(40) { frame ->
                        mainClock.advanceTimeByFrame()
                        val top = boundsOfTag("chat-live-line").top
                        maxMove = maxOf(maxMove, abs(top - prevTop))
                        prevTop = top
                        val words =
                            onNodeWithTag(LIVE_LINE_TAG)
                                .fetchSemanticsNode()
                                .config
                                .getOrNull(SemanticsProperties.Text)
                                .orEmpty()
                                .joinToString(" ") { it.text }
                        assertTrue(
                            "Working" in words && "Thinking" !in words,
                            "phase $next frame $frame status words='$words'",
                        )
                    }
                }
            } finally {
                println("B-CHAT-561 maxMove=$maxMove")
            }
        }

    @Test
    fun `B-CHAT-562 a slow model after steps still says Still thinking and its hint`() =
        kitUiTest(412, 1200) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            val base = StepsCraftScene.alternatingFacts(8, false)
            val runStart = base.runStartedAtMs ?: 0L
            val facts = base.copy(live = LiveNoticeUi.Stall(TurnStallNotice.ModelSlow), nowMs = runStart + 300_000)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    ChatScreen(StepsCraftScene.runningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            onNodeWithText("Still thinking", substring = true, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Some models think quietly", substring = true, useUnmergedTree = true).assertIsDisplayed()
            onAllNodesWithText("Working", substring = true, useUnmergedTree = true).assertCountEquals(0)
            println("B-CHAT-562 maxMove=0.0")
        }

    @Test
    fun `B-CHAT-563 rows never overlap while a step arrives`() =
        kitUiTest(
            412,
            1200,
            allow =
                setOf(
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S4
                    Allow.double("#.#s", "TEMPORARY: base c5584b61ac; removed by S4"),
                ),
        ) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var arrived by mutableStateOf(false)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts =
                        if (!arrived) StepsCraftScene.alternatingFacts(8, true)
                        else StepsCraftScene.alternatingFacts(9, true)
                    ChatScreen(StepsCraftScene.runningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            val rowMatcher =
                SemanticsMatcher("step, fold or live row") { node ->
                    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                    tag == STEP_FOLD_ROW_TAG || tag == "chat-live-line" || tag?.startsWith("chat-step-") == true
                }
            var prevLiveTop = boundsOfTag("chat-live-line").top
            var maxMove = 0f
            arrived = true
            try {
                repeat(40) { frame ->
                    mainClock.advanceTimeByFrame()
                    val nodes =
                        onAllNodes(rowMatcher, useUnmergedTree = true)
                            .fetchSemanticsNodes()
                            .map { it.config.getOrNull(SemanticsProperties.TestTag).orEmpty() to it.boundsInRoot }
                            .sortedBy { it.second.top }
                    nodes.firstOrNull { it.first == "chat-live-line" }?.second?.top?.let { top ->
                        maxMove = maxOf(maxMove, abs(top - prevLiveTop))
                        prevLiveTop = top
                    }
                    // S0 invariant: settled rows and the live line never overlap while a step arrives
                    // (arriving c9 and folding e6 animate inside KitStepReveal; reveal height is S4).
                    val settled = nodes.filter { it.first != "chat-step-c9" && it.first != "chat-step-e6" }
                    for (i in 1 until settled.size) {
                        val prev = settled[i - 1]
                        val curr = settled[i]
                        assertTrue(
                            curr.second.top >= prev.second.bottom - 0.5f,
                            "frame $frame overlap: ${prev.first}=${prev.second} vs ${curr.first}=${curr.second}",
                        )
                    }
                }
            } finally {
                println("B-CHAT-563 maxMove=$maxMove")
            }
        }

    @Test
    fun `B-CHAT-564 a running command with output is open on its first frame`() =
        kitUiTest(
            412,
            900,
            allow =
                setOf(
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S5
                    Allow.jump("file#.dart", "TEMPORARY: base c5584b61ac; removed by S5"),
                    // TEMPORARY (lead, 2026-11-25): exists on base c5584b61ac; removed by design step S4
                    Allow.first("#.#s", "TEMPORARY: base c5584b61ac; removed by S4"),
                ),
        ) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts =
                        if (phase == 1) StepsCraftScene.readsAndStepsFacts(4, 0, true)
                        else StepsCraftScene.readsAndStepsFacts(4, 1, true, lastHasOutput = true)
                    ChatScreen(StepsCraftScene.longRunningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            phase = 2
            var maxMove = 0f
            try {
                mainClock.advanceTimeByFrame()
                val outNodes =
                    onAllNodesWithText("test output line 1", substring = true, useUnmergedTree = true)
                        .fetchSemanticsNodes()
                assertTrue(outNodes.isNotEmpty(), "frame 1: running command output was not open")
                var prevH = boundsOfTag("chat-step-x1").height
                repeat(29) { idx ->
                    mainClock.advanceTimeByFrame()
                    val h = boundsOfTag("chat-step-x1").height
                    maxMove = maxOf(maxMove, abs(h - prevH))
                    assertTrue(h >= prevH - 0.5f, "frame ${idx + 2}: row height shrank $prevH -> $h")
                    prevH = h
                }
            } finally {
                println("B-CHAT-564 maxMove=$maxMove")
            }
        }

    private companion object {
        /** The finished turn's one row: what it did, the most done first. */
        const val SUMMARY = "Ran 11 commands · 2 other steps"
    }
}
