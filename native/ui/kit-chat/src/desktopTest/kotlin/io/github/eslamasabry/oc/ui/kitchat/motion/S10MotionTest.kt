package io.github.eslamasabry.oc.ui.kitchat.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerPrimaryAction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitPrimaryFace
import io.github.eslamasabry.oc.ui.kitchat.composer.MotionTheme
import io.github.eslamasabry.oc.ui.kitchat.composer.RecordingPlatform
import io.github.eslamasabry.oc.ui.kitchat.request.KitDenyReply
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import io.github.eslamasabry.oc.ui.kitchat.request.KitRequestAnsweredRow
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolKind
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolRow
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolStatus
import io.github.eslamasabry.oc.ui.kitchat.tool.KitToolView
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyFooter
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.kitKeepsTranscriptAtBottom
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkLine
import io.github.eslamasabry.oc.ui.kitchat.turn.KitWorkState
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * S10 motion audit (native build 5ff3ac113 recorded beside Flutter 2.1.4+62): the kit-chat parts no longer pop, flip or
 * swap in one frame. Each test drives the test clock by hand (autoAdvance off), checks a mid-animation frame, the end
 * state and the reduced-motion path (Animations: Off), and Calm where it differs.
 */
@OptIn(ExperimentalTestApi::class)
class S10MotionTest {
    private fun ComposeUiTest.width(tag: String): Float =
        onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().let { (it.right - it.left).value }

    private fun ComposeUiTest.top(tag: String): Dp = onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top

    // ---- M-002: + → × ------------------------------------------------------------------------------------------

    private fun ComposeUiTest.plus(level: MotionLevel, open: () -> Boolean) = setContent {
        MotionTheme(level = level) { KitComposerAdd(label = "Attach and more", onClick = {}, open = open()) }
    }

    @Test
    fun `M-002 the plus turns an eighth of a turn over QUICK and is part-way at 32 ms`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var open by mutableStateOf(false)
        plus(MotionLevel.Full) { open }
        mainClock.advanceTimeByFrame()
        val square = width("kit-composer-add")
        open = true
        mainClock.advanceTimeByFrame()
        // QUICK rides Flutter's easeOutCubic (`Enter`), which has covered ~88 % of the turn by half time (75 ms):
        // too close to √2 to tell apart from a one-frame flip, so the in-between frame is read at 32 ms.
        mainClock.advanceTimeBy(32)
        // A square turned by θ is (|cos θ| + |sin θ|) times as wide: 1 at rest, √2 at 45°.
        val mid = width("kit-composer-add-open") / square
        assertTrue(mid > 1.02f && mid < sqrt(2f) - 0.02f, "part-way through the turn at 32 ms (x$mid)")
        mainClock.advanceTimeBy(200)
        assertEquals(sqrt(2f), width("kit-composer-add-open") / square, 0.02f, "a whole eighth of a turn after QUICK")
        open = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(200)
        assertEquals(1f, width("kit-composer-add") / square, 0.02f, "back to + when the tray closes")
    }

    @Test
    fun `M-002 with reduced motion the plus is a cross at once`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var open by mutableStateOf(false)
        plus(MotionLevel.Off) { open }
        mainClock.advanceTimeByFrame()
        val square = width("kit-composer-add")
        open = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(sqrt(2f), width("kit-composer-add-open") / square, 0.02f)
    }

    @Test
    fun `M-002 under Calm the plus and the cross cross-fade instead of turning`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var open by mutableStateOf(false)
        plus(MotionLevel.Calm) { open }
        mainClock.advanceTimeByFrame()
        open = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        // Both glyphs are drawn while they fade; neither is at an angle in between.
        val faces = onAllNodesWithTag("kit-composer-add-open", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(2, faces.size, "the old and the new glyph while they cross-fade")
        val widths = faces.map { it.boundsInRoot.width }.sorted()
        assertEquals(sqrt(2f), widths[1] / widths[0], 0.02f, "one is the +, one the ×; no in-between angle")
        mainClock.advanceTimeBy(200)
        onAllNodesWithTag("kit-composer-add-open", useUnmergedTree = true).assertCountEquals(1)
    }

    // ---- Send / Stop / mic ------------------------------------------------------------------------------------

    @Test
    fun `M-020 Send, Stop and the mic cross-fade over 150 ms and swap at once with reduced motion`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var level by mutableStateOf(MotionLevel.Full)
        var face by mutableStateOf(KitPrimaryFace.Send)
        setContent {
            MotionTheme(level = level) {
                KitComposerPrimaryAction(
                    face = face,
                    mic = { Box(Modifier.size(48.dp).testTag("mic")) },
                    send = { Box(Modifier.size(48.dp).testTag("send")) },
                    stop = { Box(Modifier.size(48.dp).testTag("stop")) },
                )
            }
        }
        mainClock.advanceTimeByFrame()
        face = KitPrimaryFace.Stop
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithTag("send", useUnmergedTree = true).assertExists() // both while they cross-fade: nothing swaps in one frame
        onNodeWithTag("stop", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(150)
        onNodeWithTag("send", useUnmergedTree = true).assertDoesNotExist()
        level = MotionLevel.Off
        mainClock.advanceTimeByFrame()
        face = KitPrimaryFace.Mic
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("stop", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("mic", useUnmergedTree = true).assertExists()
    }

    // ---- M-005: approval card -----------------------------------------------------------------------------------

    @Test
    fun `M-005 Allow, Always and Deny each play the light Send haptic`() = runComposeUiTest {
        val platform = RecordingPlatform()
        setContent {
            MotionTheme(level = MotionLevel.Off, platform = platform) {
                KitPermissionCard(
                    title = "Run npm test?", summary = null, subject = "npm test",
                    allowOnceLabel = "Allow once", alwaysLabel = "Always allow", denyLabel = "Deny",
                    onAllowOnce = {}, onAlways = {}, onDeny = {},
                )
            }
        }
        onNodeWithText("Allow once").performClick()
        onNodeWithText("Always allow").performClick()
        onNodeWithText("Deny").performClick()
        assertEquals(listOf(KitHaptic.Send, KitHaptic.Send, KitHaptic.Send), platform.haptics)
    }

    @Test
    fun `M-005 Deny that opens the reply field is silent, and the reply's answers play Send`() = runComposeUiTest {
        val platform = RecordingPlatform()
        setContent {
            MotionTheme(level = MotionLevel.Off, platform = platform) {
                KitPermissionCard(
                    title = "Run npm test?", summary = null, subject = "npm test",
                    allowOnceLabel = "Allow once", alwaysLabel = null, denyLabel = "Deny",
                    onAllowOnce = {}, onAlways = null, onDeny = {},
                    denyReply = KitDenyReply("Tell it why", "Deny and send", "Just deny", "Back", "Write a reason first") {},
                )
            }
        }
        onNodeWithText("Deny").performClick()
        assertEquals(emptyList<KitHaptic>(), platform.haptics, "opening the reply is not an answer")
        onNodeWithTag("permission-card-reply-just-deny").performClick()
        assertEquals(listOf(KitHaptic.Send), platform.haptics)
    }

    @Test
    fun `M-005 the receipt's Undo and Sending cross-fade, and swap at once with reduced motion`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var level by mutableStateOf(MotionLevel.Full)
        var outcome by mutableStateOf("Allowed · Undo")
        setContent {
            MotionTheme(level = level) {
                KitRequestAnsweredRow(KitIcons.terminal, "Run npm test", outcomeKey = outcome) { KitText(it) }
            }
        }
        mainClock.advanceTimeByFrame()
        outcome = "Sending…"
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithText("Allowed · Undo", useUnmergedTree = true).assertExists()
        onNodeWithText("Sending…", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(200)
        onAllNodesWithText("Allowed · Undo", useUnmergedTree = true).assertCountEquals(0)
        level = MotionLevel.Off
        mainClock.advanceTimeByFrame()
        outcome = "Not confirmed yet"
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onAllNodesWithText("Sending…", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun ComposeUiTest.transcriptWithSlot(slot: () -> Dp): () -> TranscriptListState {
        lateinit var state: TranscriptListState
        setContent {
            MotionTheme(level = MotionLevel.Off) {
                state = rememberTranscriptListState()
                Column(Modifier.size(320.dp, 600.dp)) {
                    KitTranscriptList(state, "row0", "Latest", KitMotionLevel.Off, Modifier.weight(1f)) {
                        items(40, key = { "row$it" }) { Box(Modifier.fillMaxWidth().height(80.dp)) }
                    }
                    Box(Modifier.fillMaxWidth().height(slot()).kitKeepsTranscriptAtBottom(state))
                }
            }
        }
        return { state }
    }

    @Test
    fun `M-005 a following transcript stays on its newest row while the slot under it changes height`() =
        runComposeUiTest {
            var slot by mutableStateOf(0.dp)
            val state = transcriptWithSlot { slot }
            waitForIdle()
            // A nudge within the follow slack: still following, but the newest row sits 30 px below the edge.
            runOnIdle { state().lazy.dispatchRawDelta(30f) }
            waitForIdle()
            assertTrue(state().following)
            assertEquals(30, state().lazy.firstVisibleItemScrollOffset)
            for (h in listOf(40.dp, 120.dp, 220.dp, 60.dp)) {
                slot = h
                waitForIdle()
                assertEquals(0, state().lazy.firstVisibleItemIndex, "slot at $h")
                assertEquals(0, state().lazy.firstVisibleItemScrollOffset, "pinned to the newest row with the slot at $h")
            }
        }

    @Test
    fun `M-005 a reader in history is never moved by the slot`() = runComposeUiTest {
        var slot by mutableStateOf(0.dp)
        val state = transcriptWithSlot { slot }
        waitForIdle()
        runOnIdle {
            state().lazy.dispatchRawDelta(900f)
            state().following = false
        }
        waitForIdle()
        val index = state().lazy.firstVisibleItemIndex
        val offset = state().lazy.firstVisibleItemScrollOffset
        slot = 200.dp
        waitForIdle()
        assertEquals(index to offset, state().lazy.firstVisibleItemIndex to state().lazy.firstVisibleItemScrollOffset)
    }

    // ---- M-009: the reply footer ------------------------------------------------------------------------------

    private fun ComposeUiTest.footer(level: MotionLevel) = setContent {
        MotionTheme(level = level) {
            Box(Modifier.width(360.dp)) {
                KitReplyFooter("Copy reply", "More", onCopy = {}, onMore = {}, arriving = true)
            }
        }
    }

    @Test
    fun `M-009 an arriving footer rises 6 dp into place over 250 ms`() = runComposeUiTest {
        mainClock.autoAdvance = false
        footer(MotionLevel.Full)
        mainClock.advanceTimeByFrame()
        val start = top("kit-reply-actions")
        mainClock.advanceTimeBy(100)
        val mid = top("kit-reply-actions")
        mainClock.advanceTimeBy(300)
        val end = top("kit-reply-actions")
        assertEquals(6f, (start - end).value, 0.75f, "it starts about 6 dp low")
        assertTrue(mid < start && mid > end, "part-way up at 100 ms ($start → $mid → $end)")
    }

    @Test
    fun `M-009 under Calm the footer only fades, and with reduced motion it is simply there`() {
        for (level in listOf(MotionLevel.Calm, MotionLevel.Off)) runComposeUiTest {
            mainClock.autoAdvance = false
            footer(level)
            mainClock.advanceTimeByFrame()
            val start = top("kit-reply-actions")
            mainClock.advanceTimeBy(400)
            assertEquals(top("kit-reply-actions"), start, "$level: no rise")
        }
    }

    // ---- M-016 / M-020: the work line and the step rows -----------------------------------------------------------

    private fun ComposeUiTest.workLine(level: MotionLevel, state: () -> KitWorkState, expanded: () -> Boolean) =
        setContent {
            MotionTheme(level = level) {
                Box(Modifier.width(360.dp)) {
                    KitWorkLine(
                        label = "Working", stateWords = null, state = state(), expanded = expanded(),
                        onToggle = {},
                    ) {}
                }
            }
        }

    /**
     * The work line is Flutter's step timeline now (kit_step_timeline.dart `_Line.build`): its lead is the step light's
     * tile while the folded line runs and the bare glyph otherwise, swapped in place with no switcher, at every motion
     * level. The live tile is the only `kit-step-node` a lone line draws.
     */
    @Test
    fun `M-020 the work line's lead goes from the live tile to the glyph at once, as Flutter's timeline does`() {
        for (level in listOf(MotionLevel.Full, MotionLevel.Off)) runComposeUiTest {
            mainClock.autoAdvance = false
            var state by mutableStateOf(KitWorkState.Running)
            workLine(level, { state }, { false })
            mainClock.advanceTimeByFrame()
            onNodeWithTag("kit-step-node", useUnmergedTree = true).assertExists()
            state = KitWorkState.Done
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onNodeWithTag("kit-step-node", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `M-016 the work line's chevron turns through in-between angles instead of flipping`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var open by mutableStateOf(false)
        workLine(MotionLevel.Full, { KitWorkState.Done }, { open })
        mainClock.advanceTimeByFrame()
        val folded = onNodeWithTag("kit-step-timeline-chevron", useUnmergedTree = true).captureToImage().toPixelMap()
        open = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(32)
        val mid = onNodeWithTag("kit-step-timeline-chevron", useUnmergedTree = true).captureToImage().toPixelMap()
        mainClock.advanceTimeBy(600)
        val opened = onNodeWithTag("kit-step-timeline-chevron", useUnmergedTree = true).captureToImage().toPixelMap()
        fun pixels(m: androidx.compose.ui.graphics.PixelMap) =
            (0 until m.width).flatMap { x -> (0 until m.height).map { y -> m[x, y] } }
        assertNotEquals(pixels(folded), pixels(opened), "down when folded, up when open")
        assertNotEquals(pixels(folded), pixels(mid), "already turning at 32 ms")
        assertNotEquals(pixels(opened), pixels(mid), "not yet up at 32 ms: no one-frame flip")
    }

    private fun step(title: String, status: KitToolStatus, added: Int? = null) = KitToolView(
        id = "call-1", kind = KitToolKind.Shell, title = title, subject = null, detail = null, status = status,
        durationLabel = null, added = added, removed = null, statusLabel = if (status == KitToolStatus.Done) "Done" else "Running",
    )

    @Test
    fun `M-020 a step's new title cross-fades over the old one`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var view by mutableStateOf(step("Running", KitToolStatus.Running))
        setContent { MotionTheme(level = MotionLevel.Full) { Box(Modifier.width(360.dp)) { KitToolRow(view, opened = false, onToggle = {}) } } }
        mainClock.advanceTimeByFrame()
        view = step("Running the date", KitToolStatus.Running)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithText("Running", useUnmergedTree = true).assertExists()
        onNodeWithText("Running the date", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(200)
        onAllNodesWithText("Running", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `M-020 a step's count rolls to its new value and the live dot fades out`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var view by mutableStateOf(step("Edited", KitToolStatus.Running, added = 3))
        setContent { MotionTheme(level = MotionLevel.Full) { Box(Modifier.width(360.dp)) { KitToolRow(view, opened = false, onToggle = {}) } } }
        mainClock.advanceTimeByFrame()
        view = step("Edited", KitToolStatus.Done, added = 4)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(64)
        onNodeWithText("+3", useUnmergedTree = true).assertExists()
        onNodeWithText("+4", useUnmergedTree = true).assertExists()
        onNodeWithTag("kit-tool-live-dot", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(200)
        onAllNodesWithText("+3", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("kit-tool-live-dot", useUnmergedTree = true).assertDoesNotExist()
    }
}
