package io.github.eslamasabry.oc.ui.kit

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.foundation.KitRuleBroken
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.input.SearchRules
import io.github.eslamasabry.oc.ui.kit.modal.KitModalMotion
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetStep
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedRows
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedValue
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.motion.KitSwapCount
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.nav.KitTabSwitcher
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.teaching.TipRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class KitbBack : NavigationEventInput() {
    fun start() = dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_RIGHT, 0f))

    fun complete() = dispatchOnBackCompleted()
}

/**
 * S2-KITB parts on desktop (`runComposeUiTest`, the clock driven by hand). Reduced motion is checked the plan 08 §9.3
 * way: change the state, advance one frame, and the final state is the only one composed. "Composed" is tracked with a
 * [DisposableEffect] per child, because a leaving child is hidden from semantics by design.
 */
@OptIn(ExperimentalTestApi::class)
class KitbDesktopTest {
    private val w = KitbWords.En
    private val back = KitbBack()
    private val dispatcher = NavigationEventDispatcher().also { it.addInput(back) }
    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
    }

    @Composable
    private fun Themed(level: MotionLevel, platform: FakeKitPlatform = FakeKitPlatform(), still: Boolean = true, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
            KitTheme(appearance = KitAppearance(motion = level), platform = platform, localeTag = "en", stillFrame = still, content = content)
        }
    }

    /** Records which children are composed right now. */
    @Composable
    private fun Live(tag: String, live: MutableSet<String>) {
        DisposableEffect(tag) {
            live += tag
            onDispose { live -= tag }
        }
        KitText(tag)
    }

    // --- B-KIT-023: reduced motion settles in one frame; Full keeps the leaving child for its fade -------------------

    private fun swapCase(level: MotionLevel, expectAfterOneFrame: Set<String>) = runComposeUiTest {
        mainClock.autoAdvance = false
        var target by mutableStateOf("A")
        val live = mutableSetOf<String>()
        setContent { Themed(level) { KitSwap(target) { Live(it, live) } } }
        mainClock.advanceTimeByFrame()
        assertEquals(setOf("A"), live, "first build is final")
        target = "B"
        mainClock.advanceTimeByFrame()
        assertEquals(expectAfterOneFrame, live)
        mainClock.advanceTimeBy(MotionTokens.STANDARD.toLong() + 50)
        assertEquals(setOf("B"), live)
        onNodeWithText("B").assertExists()
    }

    @Test
    fun `B-KIT-023 KitSwap under Off is final after one frame`() = swapCase(MotionLevel.Off, setOf("B"))

    @Test
    fun `B-KIT-023 KitSwap under Full keeps the leaving child for its QUICK fade, hidden from TalkBack`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var target by mutableStateOf("A")
            val live = mutableSetOf<String>()
            setContent { Themed(MotionLevel.Full) { KitSwap(target) { Live(it, live) } } }
            mainClock.advanceTimeByFrame()
            target = "B"
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            assertEquals(setOf("A", "B"), live)
            onNodeWithText("A").assertDoesNotExist() // leaving: out of semantics
            mainClock.advanceTimeBy(MotionTokens.QUICK.toLong() + 50)
            assertEquals(setOf("B"), live)
        }

    @Test
    fun `B-KIT-023 KitSwapCount rolls at Full, cross-fades at Calm and is instant at Off`() {
        for ((level, expect) in listOf(MotionLevel.Off to setOf("2"), MotionLevel.Calm to setOf("1", "2"), MotionLevel.Full to setOf("1", "2"))) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var n by mutableStateOf(1)
                val live = mutableSetOf<String>()
                setContent { Themed(level) { KitSwapCount(n) { Live(it.toString(), live) } } }
                mainClock.advanceTimeByFrame()
                n = 2
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                assertEquals(expect, live, "level $level")
                mainClock.advanceTimeBy(MotionTokens.QUICK.toLong() + 50)
                assertEquals(setOf("2"), live)
            }
        }
    }

    @Test
    fun `B-KIT-023 KitReveal and KitPresence leave at once under Off and fold out under Full`() {
        for ((level, stillThere) in listOf(MotionLevel.Off to false, MotionLevel.Full to true)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var shown by mutableStateOf(true)
                val live = mutableSetOf<String>()
                setContent {
                    Themed(level) {
                        Column {
                            KitReveal(shown) { Live("reveal", live) }
                            KitPresence(shown) { Live("presence", live) }
                        }
                    }
                }
                mainClock.advanceTimeByFrame()
                assertEquals(setOf("reveal", "presence"), live, "first build is final")
                shown = false
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                assertEquals(stillThere, "reveal" in live, "reveal at $level")
                assertEquals(stillThere, "presence" in live, "presence at $level")
                mainClock.advanceTimeBy(MotionTokens.STANDARD.toLong() + 200)
                assertTrue(live.isEmpty())
            }
        }
    }

    @Test
    fun `B-KIT-023 KitAnimatedValue shows its first value at once, eases at Full and jumps at Off`() {
        for (level in listOf(MotionLevel.Full, MotionLevel.Off)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var v by mutableStateOf(0.2f)
                val seen = mutableListOf<Float>()
                setContent { Themed(level) { KitAnimatedValue(v) { seen += it } } }
                mainClock.advanceTimeByFrame()
                assertEquals(0.2f, seen.last(), "first build is final")
                v = 0.8f
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                if (level == MotionLevel.Off) assertEquals(0.8f, seen.last()) else assertTrue(seen.last() < 0.8f, "easing at Full")
                mainClock.advanceTimeBy(MotionTokens.STANDARD.toLong() + 50)
                assertEquals(0.8f, seen.last())
            }
        }
    }

    @Test
    fun `B-KIT-023 KitAnimatedRows - first load plays nothing, a removed row folds at Full and goes at once at Off`() {
        for ((level, stillThere) in listOf(MotionLevel.Off to false, MotionLevel.Full to true)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var items by mutableStateOf(listOf("a", "b", "c"))
                val live = mutableSetOf<String>()
                setContent { Themed(level) { KitAnimatedRows(items, key = { it }) { Live(it, live) } } }
                mainClock.advanceTimeByFrame()
                assertEquals(setOf("a", "b", "c"), live)
                onNodeWithText("b").assertExists()
                items = listOf("a", "c", "d")
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                assertEquals(stillThere, "b" in live, "removed row at $level")
                assertTrue("d" in live)
                mainClock.advanceTimeBy(MotionTokens.STANDARD.toLong() + 200)
                assertEquals(setOf("a", "c", "d"), live)
            }
        }
    }

    @Test
    fun `B-KIT-023 KitEntrance under Off shows its content on the first frame`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Themed(MotionLevel.Off) { KitEntrance { KitText("late") } } }
        mainClock.advanceTimeByFrame()
        onNodeWithText("late").assertExists()
    }

    @Test
    fun `B-KIT-023 the motion parts compose with final values on the first frame`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Themed(MotionLevel.Full) { MotionPartsScene(w) } }
        mainClock.advanceTimeByFrame()
        onNodeWithText(w.done).assertExists()
        onNodeWithText("12").assertExists()
        onNodeWithText(w.stepOne).assertExists()
    }

    // --- B-KIT-050: the tab switcher ----------------------------------------------------------------------------------

    @Test
    fun `B-KIT-050 a tab tap selects a different tab once, and the chosen tab does nothing`() = runComposeUiTest {
        var index by mutableStateOf(0)
        val picked = mutableListOf<Int>()
        setContent { Themed(MotionLevel.Off) { TabsScene(w, index) { picked += it; index = it } } }
        onNode(hasContentDescription("${w.working}, 3")).performClick()
        assertTrue(picked.isEmpty())
        onNode(hasContentDescription("${w.done}, 12")).performClick()
        assertEquals(listOf(2), picked)
        onNode(hasContentDescription("${w.done}, 12")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNodeWithText(w.done + " · " + w.row).assertExists()
    }

    @Test
    fun `B-KIT-050 counts loading show no number, and the needs-you marker shows its count`() = runComposeUiTest {
        setContent { Themed(MotionLevel.Off) { TabsScene(w) } }
        onNode(hasContentDescription(w.waiting)).assertExists() // no ", 0"
        onNodeWithText("0").assertDoesNotExist()
        onNodeWithText("2").assertExists()
    }

    @Test
    fun `B-KIT-050 every body keeps its state while another is shown`() = runComposeUiTest {
        var index by mutableStateOf(0)
        setContent { Themed(MotionLevel.Off) { TabsScene(w, index) { index = it } } }
        onNodeWithText("${w.taps}: 0").performClick()
        onNodeWithText("${w.taps}: 1").assertExists()
        onNode(hasContentDescription("${w.done}, 12")).performClick()
        onNodeWithText("${w.taps}: 1").assertDoesNotExist()
        onNode(hasContentDescription("${w.working}, 3")).performClick()
        onNodeWithText("${w.taps}: 1").assertExists()
    }

    @Test
    fun `B-KIT-050 the body switch is instant under Off and fades through under Full`() {
        for ((level, both) in listOf(MotionLevel.Off to false, MotionLevel.Full to true)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var index by mutableStateOf(0)
                val live = mutableSetOf<String>()
                setContent {
                    Themed(level) { KitTabSwitcher(index, listOf({ Live("one", live) }, { Live("two", live) })) }
                }
                mainClock.advanceTimeByFrame()
                index = 1
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                assertEquals(both, "one" in live, "at $level")
                assertTrue("two" in live)
                mainClock.advanceTimeBy(MotionTokens.STANDARD.toLong() + 50)
                assertEquals(setOf("two"), live)
            }
        }
    }

    @Test
    fun `B-KIT-050 mismatched tab and body counts, and a one-tab strip, are refused in debug`() {
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest {
                setContent { Themed(MotionLevel.Off) { KitTabSwitcher(listOf(KitTab("A"), KitTab("B")), 0, {}, listOf({ KitText("a") })) } }
            }
        }
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest { setContent { Themed(MotionLevel.Off) { KitTabStrip(listOf(KitTab("A")), 0, {}) } } }
        }
    }

    // --- B-KIT-030: the search field ------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-030 onSearch runs once after typing settles, and at once on Clear`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val calls = mutableListOf<String>()
        setContent { Themed(MotionLevel.Off) { KitSearchField(label = w.searchSettings, onSearch = { calls += it }) } }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("kit-search-field").performTextInput("op")
        mainClock.advanceTimeBy(SearchRules.SETTLE_MS - 100)
        assertTrue(calls.isEmpty(), "not per keystroke")
        mainClock.advanceTimeBy(200)
        assertEquals(listOf("op"), calls)
        onNode(hasContentDescription("Clear search")).performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf("op", ""), calls)
    }

    @Test
    fun `B-KIT-030 the count shows once typing settles, and a partial count says so`() = runComposeUiTest {
        setContent { Themed(MotionLevel.Off) { SearchScene(w) } }
        onNodeWithText("12 results").assertExists()
        onNodeWithText("4 loaded · searching the server…").assertExists()
        onNodeWithText("Filter: ${w.symbols}").assertExists()
        onNodeWithText(w.notReady).assertExists()
    }

    @Test
    fun `B-KIT-030 Esc clears the query first`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { Themed(MotionLevel.Off) { KitSearchField(label = w.searchSettings, onSearch = { calls += it }) } }
        onNodeWithTag("kit-search-field").performTextInput("opus")
        mainClock.advanceTimeBy(SearchRules.SETTLE_MS + 50)
        assertEquals(listOf("opus"), calls)
        onNodeWithTag("kit-search-field").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("", calls.last())
        onNodeWithText(w.searchSettings).assertExists() // the hint is back
    }

    @Test
    fun `B-KIT-030 the back gesture clears a query before the screen leaves`() = runComposeUiTest {
        val calls = mutableListOf<String>()
        setContent { Themed(MotionLevel.Off) { KitSearchField(label = w.searchSettings, onSearch = { calls += it }) } }
        onNodeWithTag("kit-search-field").performTextInput("opus")
        mainClock.advanceTimeBy(SearchRules.SETTLE_MS + 50)
        assertEquals(listOf("opus"), calls)
        runOnIdle { back.start() }
        runOnIdle { back.complete() }
        waitForIdle()
        assertEquals("", calls.last())
    }

    @Test
    fun `B-KIT-030 no match isolates the query and Clear search clears`() = runComposeUiTest {
        var cleared = 0
        setContent { Themed(MotionLevel.Off) { KitSearchNoMatch(query = w.query, onClear = { cleared++ }) } }
        onNodeWithText("Nothing matches ⁨${w.query}⁩").assertExists()
        onNodeWithText("Clear search").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun `B-KIT-030 a disabled search field must say why`() {
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest { setContent { Themed(MotionLevel.Off) { KitSearchField(label = w.searchSettings, onSearch = {}, enabled = false) } } }
        }
    }

    // --- Jump pill, tip, refresh -------------------------------------------------------------------------------------

    @Test
    fun `B-KIT-023 the jump pill says its count in words, acts once, and when hidden is out of semantics`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var jumps = 0
        setContent { Themed(MotionLevel.Off) { JumpPillScene(w, newCount = 3, visible = visible, onJump = { jumps++ }) } }
        onNode(hasContentDescription("3 new · Jump to latest")).performClick()
        assertEquals(1, jumps)
        visible = false
        waitForIdle()
        onNode(hasContentDescription("3 new · Jump to latest")).assertDoesNotExist()
        onNode(hasContentDescription(w.earlier)).assertDoesNotExist()
    }

    @Test
    fun `B-NAV-038 a tip shows its sentence and fades by itself after its hold, once`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var visible by mutableStateOf(true)
        var dismissed = 0
        setContent {
            Themed(MotionLevel.Off) {
                KitTip(message = w.tip, visible = visible, onDismissed = { dismissed++; visible = false }) { KitText(w.row) }
            }
        }
        mainClock.advanceTimeByFrame()
        onNodeWithText(w.tip).assertExists()
        mainClock.advanceTimeBy(TipRules.HOLD_MS - 500)
        assertEquals(0, dismissed)
        mainClock.advanceTimeBy(600)
        mainClock.advanceTimeByFrame()
        assertEquals(1, dismissed)
        onNodeWithText(w.tip).assertDoesNotExist()
    }

    @Test
    fun `B-KIT-054 pull to refresh has a Refresh action without a finger, and refreshing at Off settles`() = runComposeUiTest {
        var runs = 0
        setContent { Themed(MotionLevel.Off) { RefreshScene(w, refreshing = false, onRefresh = { runs++ }) } }
        val actions = onNodeWithTag("refresh").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        runOnIdle { actions.first { it.label == "Refresh" }.action() }
        assertEquals(1, runs)
    }

    @Test
    fun `B-KIT-054 a refreshing indicator at Full draws a still frame in tests and settles`() = runComposeUiTest {
        setContent { Themed(MotionLevel.Full, still = true) { RefreshScene(w, refreshing = true) } }
        waitForIdle()
        onNodeWithTag("kit-refresh").assertExists()
    }

    // --- B-KIT-015/016/022/032: sheet and dialog motion -------------------------------------------------------------

    @Test
    fun `B-KIT-032 a sheet step swaps at once under Off and cross-fades under Full`() {
        for ((level, both) in listOf(MotionLevel.Off to false, MotionLevel.Full to true)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var second by mutableStateOf(false)
                val live = mutableSetOf<String>()
                setContent { Themed(level) { KitSheetStep(second) { Live(if (it) "two" else "one", live) } } }
                mainClock.advanceTimeByFrame()
                second = true
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
                assertEquals(both, "one" in live, "at $level")
                mainClock.advanceTimeBy(MotionTokens.STANDARD.toLong() + 300)
                assertEquals(setOf("two"), live)
            }
        }
    }

    @Test
    fun `B-KIT-032 the side sheet closes from its scrim and from Back, and leaves at once under Off`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var asks = 0
        setContent { Themed(MotionLevel.Off) { SideSheetScene(w, visible) { asks++ } } }
        onNodeWithText(w.sideTitle).assertExists()
        onNodeWithTag("kit-side-sheet-scrim").performClick()
        assertEquals(1, asks)
        runOnIdle { back.start() }
        runOnIdle { back.complete() }
        waitForIdle()
        assertEquals(2, asks)
        mainClock.autoAdvance = false
        visible = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText(w.sideTitle).assertDoesNotExist()
    }

    @Test
    fun `B-KIT-016 B-KIT-022 modal transitions are None under Off`() = runComposeUiTest {
        val enters = mutableListOf<EnterTransition>()
        val exits = mutableListOf<ExitTransition>()
        setContent {
            Themed(MotionLevel.Off) {
                enters += KitModalMotion.dialogEnter()
                enters += KitModalMotion.menuEnter(above = false)
                enters += KitModalMotion.sheetEnter()
                enters += KitModalMotion.scrimEnter()
                exits += KitModalMotion.dialogExit()
                exits += KitModalMotion.menuExit()
                exits += KitModalMotion.sheetExit()
                exits += KitModalMotion.scrimExit()
            }
        }
        waitForIdle()
        enters.forEach { assertSame(EnterTransition.None, it) }
        exits.forEach { assertSame(ExitTransition.None, it) }
    }
}
