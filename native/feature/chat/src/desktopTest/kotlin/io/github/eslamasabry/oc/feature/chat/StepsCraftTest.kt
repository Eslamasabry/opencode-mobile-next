package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kitchat.tool.STEP_FOLD_ROW_TAG
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

    @Test
    fun `STEPS-CRAFT-019 a folded list stays folded while steps arrive, between steps too`() =
        kitUiTest(412, 1200) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts =
                        when (phase) {
                            1 -> StepsCraftScene.alternatingFacts(8, true)
                            2 -> StepsCraftScene.alternatingFacts(8, false)
                            else -> StepsCraftScene.alternatingFacts(9, true)
                        }
                    ChatScreen(StepsCraftScene.runningState(), actions, composer, facts = facts)
                }
            }
            val stepTag = Regex("chat-step-[ce]\\d+")
            val stepMatcher =
                SemanticsMatcher("step row") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.matches(stepTag) == true
                }
            val rows = mutableListOf<Int>()
            val summaries = mutableListOf<Int>()
            val folds = mutableListOf<Int>()
            fun sample() {
                rows += onAllNodes(stepMatcher, useUnmergedTree = true).fetchSemanticsNodes().size
                summaries += onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).fetchSemanticsNodes().size
                folds += onAllNodesWithTag(STEP_FOLD_ROW_TAG, useUnmergedTree = true).fetchSemanticsNodes().size
            }
            mainClock.advanceTimeBy(900)
            sample()
            phase = 2
            repeat(40) {
                mainClock.advanceTimeByFrame()
                sample()
            }
            val fold5Err = runCatching { onNodeWithText("Show 5 earlier steps").assertIsDisplayed() }.exceptionOrNull()
            phase = 3
            repeat(40) {
                mainClock.advanceTimeByFrame()
                sample()
            }
            assertTrue(rows.all { it == 3 }, "rows stayed 3: $rows (summaries=$summaries, folds=$folds)")
            assertTrue(summaries.all { it == 0 }, "summary stayed 0: $summaries (rows=$rows, folds=$folds)")
            assertTrue(folds.all { it == 1 }, "fold row stayed 1: $folds (rows=$rows, summaries=$summaries)")
            if (fold5Err != null) throw fold5Err
            onNodeWithText("Show 6 earlier steps").assertIsDisplayed()
        }

    /** Drag the transcript list down by [dp] so older messages move down into view (no fling). */
    private fun androidx.compose.ui.test.ComposeUiTest.scrollTranscriptUp(dp: Float) {
        val steps = maxOf(8, (dp / 10f).toInt())
        val step = dp / steps
        onNodeWithTag("transcript-list").performTouchInput {
            down(center)
            repeat(steps) { moveBy(Offset(0f, step)) }
            advanceEventTime(300)
            up()
        }
        mainClock.advanceTimeBy(300)
    }

    @Test
    fun `STEPS-CRAFT-020 scrolled up by 40 dp - nothing on screen moves while steps arrive`() =
        kitUiTest(412, 900) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts =
                        when (phase) {
                            1 -> StepsCraftScene.readsAndStepsFacts(reads = 3, extras = 0, lastRuns = true)
                            2 -> StepsCraftScene.readsAndStepsFacts(reads = 4, extras = 0, lastRuns = false)
                            3 ->
                                StepsCraftScene.readsAndStepsFacts(
                                    reads = 4,
                                    extras = 1,
                                    lastRuns = true,
                                    lastHasOutput = true,
                                )
                            else -> StepsCraftScene.readsAndStepsFacts(reads = 4, extras = 2, lastRuns = true)
                        }
                    ChatScreen(StepsCraftScene.longRunningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            scrollTranscriptUp(40f)
            val earlierText = StepsCraftScene.earlierParagraphs.last()
            val prompt0 = promptTop()
            val earlier0 = boundsOfText(earlierText).top
            val prompts = mutableListOf(prompt0)
            val earliers = mutableListOf(earlier0)
            val jumps = mutableListOf<Int>()
            fun sample() {
                prompts += promptTop()
                earliers += boundsOfText(earlierText).top
                jumps += onAllNodesWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNodes().size
            }
            sample()
            for (next in 2..4) {
                phase = next
                repeat(30) {
                    mainClock.advanceTimeByFrame()
                    sample()
                }
            }
            assertTrue(
                prompts.all { abs(it - prompt0) <= 1f },
                "prompt moved while scrolled up 40 dp: start=$prompt0 samples=$prompts",
            )
            assertTrue(
                earliers.all { abs(it - earlier0) <= 1f },
                "earlier answer moved while scrolled up 40 dp: start=$earlier0 samples=$earliers",
            )
            assertTrue(
                jumps.all { it == 0 },
                "jump-to-latest drew over text while scrolled up 40 dp: $jumps",
            )
        }

    @Test
    fun `STEPS-CRAFT-021 scrolled up far - nothing on screen moves`() =
        kitUiTest(412, 900) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val state =
                        if (phase < 4) StepsCraftScene.longRunningState() else StepsCraftScene.longDoneState()
                    val facts =
                        when (phase) {
                            1 -> StepsCraftScene.readsAndStepsFacts(reads = 18, extras = 0, lastRuns = true)
                            2 ->
                                StepsCraftScene.readsAndStepsFacts(
                                    reads = 18,
                                    extras = 1,
                                    lastRuns = true,
                                    lastHasOutput = true,
                                )
                            3 -> StepsCraftScene.readsAndStepsFacts(reads = 18, extras = 2, lastRuns = false)
                            else -> StepsCraftScene.doneFacts()
                        }
                    ChatScreen(state, actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            activate("chat-step-run-r1")
            mainClock.advanceTimeBy(900)
            scrollTranscriptUp(540f)
            val anchorText = StepsCraftScene.earlierParagraphs.last()
            val anchor0 = boundsOfText(anchorText).top
            val tops = mutableListOf(anchor0)
            val jumpTops = mutableListOf<Float>()
            fun sampleJump() {
                onAllNodesWithTag("jump-to-latest", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .firstOrNull()
                    ?.let { jumpTops += it.boundsInRoot.top }
            }
            for (next in 2..3) {
                phase = next
                repeat(30) {
                    mainClock.advanceTimeByFrame()
                    tops += boundsOfText(anchorText).top
                    sampleJump()
                }
            }
            assertTrue(
                tops.all { abs(it - anchor0) <= 1f },
                "earlier paragraph moved while scrolled up far: start=$anchor0 samples=$tops",
            )
            // Scrolled past the grown work block too: when the reply finishes, nothing on screen moves either.
            scrollTranscriptUp(420f)
            val farText = StepsCraftScene.earlierParagraphs[6]
            val far0 = boundsOfText(farText).top
            val farTops = mutableListOf(far0)
            phase = 4
            repeat(30) {
                mainClock.advanceTimeByFrame()
                farTops += boundsOfText(farText).top
                sampleJump()
            }
            assertTrue(
                farTops.all { abs(it - far0) <= 1f },
                "earlier paragraph moved when reply finished far below: start=$far0 samples=$farTops",
            )
            val jump = boundsOfTag("jump-to-latest")
            val boxTop = boundsOfText("Composer slot").top
            assertTrue(jump.bottom <= boxTop + 1f, "jump control sat below box top: $jump vs box=$boxTop")
            assertTrue(
                jumpTops.isNotEmpty() && jumpTops.all { abs(it - jumpTops.first()) <= 1f },
                "jump control moved from its fixed place: $jumpTops",
            )
        }

    @Suppress("MaxLineLength")
    @Test
    fun `STEPS-CRAFT-022 at the newest line - a group opened by a tap stays put while steps arrive and its count changes`() =
        kitUiTest(412, 900) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts =
                        when (phase) {
                            1 -> StepsCraftScene.readsAndStepsFacts(reads = 8, extras = 0, lastRuns = false)
                            2 -> StepsCraftScene.readsAndStepsFacts(reads = 9, extras = 0, lastRuns = true)
                            3 -> StepsCraftScene.readsAndStepsFacts(reads = 9, extras = 0, lastRuns = false)
                            else -> StepsCraftScene.readsAndStepsFacts(reads = 10, extras = 4, lastRuns = true)
                        }
                    ChatScreen(StepsCraftScene.longRunningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag("chat-step-run-r1").performTouchInput { down(center); up() }
            mainClock.advanceTimeBy(900)
            val group0 = boundsOfTag("chat-step-run-r1").top
            val prompt0 = promptTop()
            val earlierText = StepsCraftScene.earlierParagraphs.last()
            val earlier0 = boundsOfText(earlierText).top
            val groupTops = mutableListOf(group0)
            val prompts = mutableListOf(prompt0)
            val earliers = mutableListOf(earlier0)
            val summaries = mutableListOf<Int>()
            val hideFolds = mutableListOf<Int>()
            fun sample() {
                groupTops += boundsOfTag("chat-step-run-r1").top
                prompts += promptTop()
                earliers += boundsOfText(earlierText).top
                summaries += onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).fetchSemanticsNodes().size
                hideFolds +=
                    onAllNodesWithText("Hide earlier steps", useUnmergedTree = true).fetchSemanticsNodes().size
            }
            for (next in 2..4) {
                phase = next
                repeat(40) {
                    mainClock.advanceTimeByFrame()
                    sample()
                }
            }
            assertTrue(
                groupTops.all { abs(it - group0) <= 1f },
                "opened group row moved: start=$group0 samples=$groupTops",
            )
            assertTrue(
                prompts.all { abs(it - prompt0) <= 1f },
                "prompt moved above opened group: start=$prompt0 samples=$prompts",
            )
            assertTrue(
                earliers.all { abs(it - earlier0) <= 1f },
                "earlier answer moved above opened group: start=$earlier0 samples=$earliers",
            )
            assertTrue(summaries.all { it == 0 }, "summary row flashed: $summaries")
            assertTrue(hideFolds.all { it == 0 }, "fold header replaced group: $hideFolds")
            onNodeWithText("Read 10 files").assertIsDisplayed()
        }

    @Test
    fun `STEPS-CRAFT-023 the go-to-newest control does not appear and vanish after a step arrives`() =
        kitUiTest(412, 900) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var phase by mutableStateOf(1)
            mainClock.autoAdvance = false
            setContent {
                Root(MotionLevel.Full) {
                    val facts =
                        when (phase) {
                            1 -> StepsCraftScene.readsAndStepsFacts(reads = 3, extras = 0, lastRuns = true)
                            2 -> StepsCraftScene.readsAndStepsFacts(reads = 3, extras = 0, lastRuns = false)
                            3 -> StepsCraftScene.readsAndStepsFacts(reads = 4, extras = 0, lastRuns = true)
                            else -> StepsCraftScene.readsAndStepsFacts(reads = 5, extras = 0, lastRuns = false)
                        }
                    ChatScreen(StepsCraftScene.longRunningState(), actions, composer, facts = facts)
                }
            }
            mainClock.advanceTimeBy(900)
            val jumps = mutableListOf<Int>()
            val liveTops = mutableListOf<Float>()
            fun sample(checkLive: Boolean) {
                jumps += onAllNodesWithTag("jump-to-latest", useUnmergedTree = true).fetchSemanticsNodes().size
                if (checkLive) liveTops += boundsOfTag("chat-live-line").top
            }
            sample(checkLive = true)
            phase = 2
            repeat(30) {
                mainClock.advanceTimeByFrame()
                sample(checkLive = true)
            }
            phase = 3
            repeat(30) {
                mainClock.advanceTimeByFrame()
                sample(checkLive = true)
            }
            val live0 = liveTops.first()
            assertTrue(liveTops.all { abs(it - live0) <= 1f }, "live line jumped between steps: $liveTops")
            // Tap to open the 3-file group: FoldAnchor scrolls past the 26 dp live line, still well below 480 dp.
            onNodeWithTag("chat-step-run-r1").performTouchInput { down(center); up() }
            repeat(40) {
                mainClock.advanceTimeByFrame()
                sample(checkLive = false)
            }
            phase = 4
            repeat(40) {
                mainClock.advanceTimeByFrame()
                sample(checkLive = false)
            }
            assertTrue(jumps.all { it == 0 }, "jump-to-latest appeared while near the newest line: $jumps")
        }

    @Test
    fun `STEPS-CRAFT-024 an opened folded group stays clean across live stage 0 to 1 to 2`() =
        kitUiTest(412, 2600) {
            val actions = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
            var stage by mutableStateOf(0)
            setContent {
                Root {
                    ChatScreen(
                        StepsCraftScene.liveStageState(stage),
                        actions,
                        composer,
                        facts = StepsCraftScene.liveStageFacts(stage),
                    )
                }
            }
            waitForIdle()
            onAllNodesWithText("Read 8 files", useUnmergedTree = true).assertCountEquals(1)
            onNodeWithText("Read 8 files").performSemanticsAction(SemanticsActions.OnClick)
            waitForIdle()
            onAllNodesWithText("Read 8 files", useUnmergedTree = true).assertCountEquals(1)
            onNodeWithTag("chat-step-r1", useUnmergedTree = true).assertIsDisplayed()
            stage = 1
            waitForIdle()
            onAllNodesWithText("Hide earlier steps").assertCountEquals(0)
            onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag("chat-step-run-r1", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Read 15 files").assertIsDisplayed()
            onNodeWithTag("chat-step-r1", useUnmergedTree = true).assertIsDisplayed()
            stage = 2
            waitForIdle()
            onAllNodesWithText("Hide earlier steps").assertCountEquals(0)
            onAllNodesWithTag("chat-work-summary", useUnmergedTree = true).assertCountEquals(0)
            onNodeWithTag("chat-step-run-r1", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("Read 15 files").assertIsDisplayed()
            onNodeWithTag("chat-step-r1", useUnmergedTree = true).assertIsDisplayed()
        }

    private companion object {
        /** The finished turn's one row: what it did, the most done first. */
        const val SUMMARY = "Ran 11 commands · 2 other steps"
    }
}
