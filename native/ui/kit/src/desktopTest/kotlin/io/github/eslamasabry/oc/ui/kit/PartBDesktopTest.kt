package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitRuleBroken
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource

/** Drives the back dispatcher the way Android's OnBackInvokedCallback input does. */
private class FakeBack : NavigationEventInput() {
    fun start() = dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f))

    fun progress(p: Float) = dispatchOnBackProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, p))

    fun complete() = dispatchOnBackCompleted()

    fun cancel() = dispatchOnBackCancelled()
}

/**
 * Part-B parts on desktop (`runComposeUiTest`, headless Skia): taps, callbacks, haptics through the kit seam, semantics
 * and Back. Visual sign-off is the Robolectric goldens (`PartBGoldensTest`).
 */
@OptIn(ExperimentalTestApi::class)
class PartBDesktopTest {
    private val w = PartBWords.En
    private val still = KitAppearance(motion = MotionLevel.Off)
    private val back = FakeBack()
    private val dispatcher = NavigationEventDispatcher().also { it.addInput(back) }
    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
    }

    @Composable
    private fun Themed(platform: FakeKitPlatform, fontScale: Float = 1f, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f, fontScale),
            LocalNavigationEventDispatcherOwner provides owner,
        ) {
            KitTheme(appearance = still, platform = platform, localeTag = "en", stillFrame = true, content = content)
        }
    }

    @Test
    fun `B-KIT-011 a working button keeps its colour but ignores taps and says it is not enabled`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        var sent = 0
        setContent { Themed(platform) { ActionsScene(w, onPrimary = { sent++ }) } }
        onAllNodesWithText(w.send)[0].performClick()
        assertEquals(1, sent)
        assertEquals(listOf(KitHaptic.Send), platform.haptics, "the primary plays its meaning's haptic through the seam")
        onAllNodesWithText(w.run)[0].assertIsNotEnabled() // working
        onAllNodesWithText(w.run)[1].assertIsNotEnabled() // disabled with a reason
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, w.reason))
    }

    @Test
    fun `B-KIT-011 a disabled action shows its reason and the extras go into More`() = runComposeUiTest {
        setContent { Themed(FakeKitPlatform()) { ActionsScene(w) } }
        onNodeWithText(w.reason).assertExists() // the secondary's reason, as a muted line (STATE-8)
        onNodeWithText(w.export).assertDoesNotExist()
        onNodeWithTag("kit-actions-more").performClick()
        onNodeWithText(w.export).assertExists()
    }

    @Test
    fun `B-KIT-011 a button without words is refused in debug`() {
        assertFailsWith<KitRuleBroken> {
            runComposeUiTest { setContent { Themed(FakeKitPlatform()) { KitButton(" ", onClick = {}) } } }
        }
    }

    @Test
    fun `B-KIT-019 an icon button is named by its label and plays nothing on a plain tap`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        var taps = 0
        setContent { Themed(platform) { ActionsScene(w, onIcon = { taps++ }) } }
        onNode(hasContentDescription(w.copy)).performClick()
        assertEquals(1, taps)
        assertTrue(platform.haptics.isEmpty())
        onAllNodes(hasContentDescription(w.more))[0].assertIsNotEnabled()
    }

    @Test
    fun `B-KIT-014 one tap is one callback, and a disabled choice says why and does nothing`() = runComposeUiTest {
        val picked = mutableListOf<String>()
        setContent { Themed(FakeKitPlatform()) { ChoicesScene(w, onChoice = { picked += it }) } }
        onNodeWithText(w.deep).performClick()
        assertEquals(listOf("deep"), picked)
        onNodeWithText(w.needsDownload).assertExists()
        onNodeWithText(w.local).performClick()
        assertEquals(listOf("deep"), picked)
        onNodeWithText(w.fast).assertExists()
        onNodeWithText("Current").assertExists() // "deep" is in use while "fast" is selected
    }

    @Test
    fun `B-KIT-031 a segment tap selects once with Select, the chosen segment does nothing`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        var seg by mutableStateOf("week")
        val changes = mutableListOf<String>()
        setContent {
            Themed(platform) {
                ChoicesScene(w, segment = seg, onSegment = { changes += it; seg = it })
            }
        }
        onNodeWithText(w.week).performClick()
        assertTrue(changes.isEmpty())
        onNodeWithText(w.month).performClick()
        assertEquals(listOf("month"), changes)
        assertEquals(listOf(KitHaptic.Select), platform.haptics)
        onNodeWithText(w.month).assertIsSelected()
    }

    @Test
    fun `B-KIT-031 at 2x text the segments stack into choice rows`() = runComposeUiTest {
        setContent { Themed(FakeKitPlatform(), fontScale = 2f) { ChoicesScene(w) } }
        // Stacked rows are radio rows: each segment is its own row with the radio mark.
        onNodeWithText(w.week).assertIsSelected()
        onNodeWithText(w.day).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.RadioButton))
    }

    @Test
    fun `B-KIT-027 a switch flips with ToggleOn and ToggleOff, a risky one asks first, a locked one refuses false`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        var keep by mutableStateOf(true)
        var allow by mutableStateOf(false)
        setContent {
            Themed(platform) { ChoicesScene(w, keepOn = keep, allowAll = allow, onKeep = { keep = it }, onAllow = { allow = it }) }
        }
        onNodeWithText(w.keepRunning).performClick()
        assertEquals(false, keep)
        onNodeWithText(w.keepRunning).performClick()
        assertEquals(listOf(KitHaptic.ToggleOff, KitHaptic.ToggleOn), platform.haptics)

        onNodeWithText(w.allowAll).performClick()
        assertEquals(false, allow, "a risky step never turns on from the first tap")
        onNodeWithText(w.risk).assertExists()
        onNodeWithText("Turn on").performClick()
        assertEquals(true, allow)

        onNodeWithText(w.always).performClick() // locked: stays on
        onNodeWithText(w.always).assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, androidx.compose.ui.state.ToggleableState.On))
    }

    @Test
    fun `B-KIT-027 an expand row unfolds and folds its content`() = runComposeUiTest {
        var open by mutableStateOf(false)
        setContent { Themed(FakeKitPlatform()) { ChoicesScene(w, expanded = open, onExpand = { open = it }) } }
        onNodeWithText(w.advancedBody).assertDoesNotExist()
        onNodeWithText(w.advanced).performClick()
        onNodeWithText(w.advancedBody).assertExists()
    }

    @Test
    fun `B-KIT-015 a destructive confirm waits for the exact name and plays Commit`() = runComposeUiTest {
        val platform = FakeKitPlatform()
        var confirmed = 0
        setContent { Themed(platform) { ConfirmScene(w, onConfirm = { confirmed++ }) } }
        onNodeWithText(w.c1).assertExists()
        onNodeWithText(w.confirmAct).assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextReplacement("office")
        onNodeWithText(w.confirmAct).assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextReplacement(w.serverName)
        onNodeWithText(w.confirmAct).assertIsEnabled().performClick()
        assertEquals(1, confirmed)
        assertEquals(listOf(KitHaptic.Commit), platform.haptics)
    }

    @Test
    fun `B-KIT-015 Back cancels a confirm through the predictive back gesture`() = runComposeUiTest {
        var cancelled = 0
        setContent { Themed(FakeKitPlatform()) { ConfirmScene(w, onCancel = { cancelled++ }) } }
        runOnIdle { back.start() }
        runOnIdle { back.progress(0.5f) }
        onNodeWithText(w.confirmTitle).assertExists() // still up mid-gesture
        runOnIdle { back.complete() }
        waitForIdle()
        assertEquals(1, cancelled)
    }

    @Test
    fun `B-KIT-016 an input dialog answers null when unchanged and the new text when changed`() = runComposeUiTest {
        val results = mutableListOf<String?>()
        var visible by mutableStateOf(true)
        setContent { Themed(FakeKitPlatform()) { InputDialogScene(w, visible = visible, onResult = { results += it; visible = false }) } }
        onNodeWithText(w.rename).performClick()
        assertEquals(listOf<String?>(null), results)
        visible = true
        waitForIdle()
        onNode(hasSetTextAction()).performTextReplacement("Login fix")
        onNodeWithText(w.rename).performClick()
        assertEquals(listOf(null, "Login fix"), results)
    }

    @Test
    fun `B-KIT-032 a cancelled back gesture keeps the sheet and a completed one closes it`() = runComposeUiTest {
        var open by mutableStateOf(true)
        var dismissed = 0
        setContent { Themed(FakeKitPlatform()) { SheetV2Scene(w, visible = open, onDismiss = { dismissed++; open = false }) } }
        onNodeWithTag("kit-sheet").assertExists()
        runOnIdle { back.start() }
        runOnIdle { back.progress(0.4f) }
        runOnIdle { back.cancel() }
        waitForIdle()
        assertEquals(0, dismissed)
        runOnIdle { back.start() }
        runOnIdle { back.complete() }
        waitForIdle()
        assertEquals(1, dismissed)
        onNodeWithTag("kit-sheet").assertDoesNotExist()
    }

    @Test
    fun `B-KIT-032 unsaved input without a draft asks before the sheet closes`() = runComposeUiTest {
        var open by mutableStateOf(true)
        var dismissed = 0
        setContent { Themed(FakeKitPlatform()) { SheetV2Scene(w, visible = open, dirty = true, onDismiss = { dismissed++; open = false }) } }
        onNodeWithTag("kit-sheet-scrim").performTouchInput { click(Offset(8f, 8f)) } // above the sheet
        assertEquals(0, dismissed)
        onNodeWithText("Discard your changes?").assertExists()
        onNodeWithText("Keep editing").performClick()
        onNodeWithText("Discard your changes?").assertDoesNotExist()
        assertEquals(0, dismissed)
        onNodeWithText("Close").performClick()
        onNodeWithText("Discard changes").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `B-KIT-034 a wait escalates once at 8 s and unfolds its ways out`() = runComposeUiTest {
        val time = TestTimeSource()
        val since = time.markNow()
        setContent {
            Themed(FakeKitPlatform()) {
                KitStateView(
                    kind = KitStateKind.Loading,
                    title = w.loading,
                    since = since,
                    onSlow = listOf(KitAction(w.switchServer, {})),
                )
            }
        }
        onNodeWithText(w.switchServer).assertDoesNotExist()
        time += kotlin.time.Duration.parse("8s")
        mainClock.advanceTimeBy(8_000)
        waitForIdle()
        onNode(hasText("Still waiting after", substring = true)).assertExists()
        onNodeWithText(w.loading).assertExists() // the title stays: a slow wait is never a failure
        onNodeWithText(w.switchServer).assertExists()
    }

    @Test
    fun `B-KIT-034 a network error offers Try again and Switch server, never Report`() = runComposeUiTest {
        setContent { Themed(FakeKitPlatform()) { StatesScene(w) } }
        onNodeWithText(w.retry).assertExists()
        onNodeWithText(w.switchServer).assertExists()
        onNodeWithText("Report a problem").assertDoesNotExist()
    }

    @Test
    fun `B-SEC-204 a secret field reports every keystroke before the host hears it and is never prefilled`() = runComposeUiTest {
        val registered = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink { registered += it }) {
                Themed(FakeKitPlatform()) { FieldsScene(w) }
            }
        }
        onNodeWithTag("kit-field-secret").performTextReplacement("sk-test-1")
        waitForIdle()
        assertEquals("sk-test-1", registered.last())
        onNodeWithText("Saved").assertExists() // the saved one shows no input until Replace
        onNodeWithTag("kit-field-replace").assertExists()
    }

    @Test
    fun `KitChip a removable chip names its remove control`() = runComposeUiTest {
        var removed = 0
        setContent { Themed(FakeKitPlatform()) { ChipsScene(w, onRemove = { removed++ }) } }
        onNode(hasContentDescription(w.removeFile)).performClick()
        assertEquals(1, removed)
        onNodeWithText("Needs your decision").assertExists()
    }

    @Test
    fun `KitUndoBar times out after 8 s unless accessible navigation is on`() = runComposeUiTest {
        var timedOut = 0
        var offer by mutableStateOf<KitUndoOffer?>(KitUndoOffer("1", w.undone))
        val platform = FakeKitPlatform()
        setContent { Themed(platform) { UndoScene(w, offer = offer, onTimeout = { timedOut++; offer = null }) } }
        onNodeWithText(w.undone).assertExists()
        mainClock.advanceTimeBy(8_100)
        waitForIdle()
        assertEquals(1, timedOut)

        platform.signals.value = KitSystemSignals(accessibleNavigation = true)
        offer = KitUndoOffer("2", w.undone)
        waitForIdle()
        mainClock.advanceTimeBy(30_000)
        waitForIdle()
        assertEquals(1, timedOut)
        onNodeWithText("Dismiss").performClick()
        assertEquals(2, timedOut)
    }

    @Test
    fun `B-KIT-022 choosing a menu item runs it once and the panel keeps the layout order`() = runComposeUiTest {
        var chosen: String? = null
        setContent { Themed(FakeKitPlatform()) { MenuScene(w, onChoose = { chosen = it.label }) } }
        onNodeWithText(w.moveTo).assertExists()
        onNodeWithText(w.noServer).assertExists()
        onNodeWithText(w.archive).performClick()
        assertNull(chosen, "a disabled item runs nothing")
        onNodeWithText(w.pin).performClick()
        assertEquals(w.pin, chosen)
    }

    @Test
    fun `B-KIT-023 under motion Off a reveal and a sheet are final after one frame`() = runComposeUiTest {
        var open by mutableStateOf(false)
        var sheet by mutableStateOf(false)
        setContent {
            Themed(FakeKitPlatform()) {
                androidx.compose.foundation.layout.Box {
                    SheetV2Scene(w, visible = sheet)
                    if (!sheet) ChoicesScene(w, expanded = open)
                }
            }
        }
        waitForIdle() // fonts and resources settle first: they change sizes, not motion
        mainClock.autoAdvance = false
        open = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithText(w.advancedBody).assertExists()
        sheet = true
        // The sheet registers with its host in an effect, so it appears one frame after the state changes; that first
        // frame on screen is already final.
        var frames = 0
        while (onAllNodes(androidx.compose.ui.test.hasTestTag("kit-sheet")).fetchSemanticsNodes().isEmpty() && frames < 3) {
            mainClock.advanceTimeByFrame()
            frames++
        }
        val top1 = onNodeWithTag("kit-sheet").fetchSemanticsNode().boundsInRoot.top
        mainClock.advanceTimeBy(1_000)
        val top2 = onNodeWithTag("kit-sheet").fetchSemanticsNode().boundsInRoot.top
        assertEquals(top2, top1, "the sheet does not move after the first frame under Off")
        mainClock.autoAdvance = true
    }
}
