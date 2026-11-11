package io.github.eslamasabry.oc.feature.chats.dock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.dock.DockEntry
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockPlacement
import io.github.eslamasabry.oc.engine.attention.dock.DockPresence
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockState
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.feature.chats.ChatEnvironment
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.dock.KitDockInsets
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.UNDO_WINDOW_MS
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Device QA 2026-11-11, sweep B, through the real host in the chat's frame (Glass, full motion, the clock paused and
 * stepped): a ring removed from its long-press card and Undo tapped in the bar's first frames brings it back; and where
 * the dock's foot (its "+") lies under the bar's Undo, a tap there in the bar's first frame, at rest, in its last exit
 * frame or in the guard after it never starts a new conversation as well, nor twice.
 */
@OptIn(ExperimentalTestApi::class)
class EdgeDockUndoTimingTest {
    private val p = ProfileId("p1")

    private fun ref(id: String) = ConversationRef(p, SessionId(id))

    private class Port(entries: List<DockEntry>, placement: DockPlacement) : EdgeDockPort {
        val acts = mutableListOf<String>()
        private val model =
            MutableStateFlow(DockUi(entries = persistentListOf(*entries.toTypedArray()), presence = DockPresence.Out, placement = placement))
        override val ui: StateFlow<DockUi> = model
        override val peeks: StateFlow<Map<ConversationRef, DockPeek>> = MutableStateFlow(emptyMap())
        override val shown: StateFlow<Boolean> = MutableStateFlow(true)
        override val onScreen = MutableStateFlow<ConversationRef?>(null)

        override fun open(ref: ConversationRef) { acts += "open ${ref.sessionId.raw}" }

        override fun openRequest(ref: ConversationRef, requestId: String) {}

        override fun answer(ref: ConversationRef, request: DockRequestRef, allow: Boolean, onFailed: () -> Unit) {}

        override fun stop(ref: ConversationRef, onFailed: () -> Unit) {}

        override fun rename(ref: ConversationRef, name: String?) {}

        override fun remove(ref: ConversationRef) {
            acts += "remove ${ref.sessionId.raw}"
            model.value = model.value.copy(entries = persistentListOf(*model.value.entries.filter { it.ref != ref }.toTypedArray()))
        }

        override fun undoRemove(ref: ConversationRef) { acts += "undo ${ref.sessionId.raw}" }

        override fun restore(ref: ConversationRef) {}

        override fun pin(ref: ConversationRef) {}

        override fun unpin(ref: ConversationRef) {}

        override fun reorder(from: ConversationRef, to: ConversationRef) {}

        override fun setPlacement(side: DockSide, bias: Float) {}

        override fun tuck() {}

        override fun untuck() {}

        override fun newConversation() { acts += "new" }
    }

    private fun entries() =
        listOf(
            DockEntry(ref("a"), "Alpha", null, null, DockState.Idle, 0),
            DockEntry(ref("b"), "Beta", null, null, DockState.Idle, 0),
            DockEntry(ref("c"), "Gamma", null, null, DockState.Idle, 0),
            DockEntry(ref("d"), "Delta", null, null, DockState.Idle, 0),
        )

    private class Taps {
        var more = 0
    }

    /** The chat's frame (as EdgeDockUndoTapTest lays it out) with the dock host exactly as AppChatEdgeDock calls it. */
    private fun ComposeUiTest.chat(port: Port, taps: Taps) {
        mainClock.autoAdvance = false
        setContent {
            ChatEnvironment {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Full, style = Style.Glass),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = false,
                ) {
                    Box(Modifier.size(412.dp, 820.dp)) {
                        val frost = rememberKitFrostState()
                        KitScreen(
                            modifier = Modifier.fillMaxSize(),
                            page = true,
                            overlayChrome = true,
                            frost = frost,
                            topBar = { KitText("Conversation") },
                            bottom = { Box(Modifier.fillMaxWidth().height(96.dp)) { KitText("Message box") } },
                            edges = KitScreenEdges.Bleed,
                        ) {
                            val chrome = kitScreenChromePadding()
                            Box(Modifier.fillMaxSize().kitFrostSource(frost)) {
                                Row(
                                    Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(bottom = chrome.calculateBottomPadding() + 12.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    KitIconButton(KitIcons.more, "More for this reply", { taps.more++ })
                                }
                            }
                            EdgeDockHost(
                                port,
                                insets = KitDockInsets(top = chrome.calculateTopPadding(), bottom = chrome.calculateBottomPadding()),
                                undoBottom = chrome.calculateBottomPadding(),
                            )
                        }
                    }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
    }

    /** Removes [name]'s ring the way assistive technology does (the ring's own "Remove <name>" action). */
    private fun ComposeUiTest.removeByAction(name: String) {
        val label = "Remove $name"
        val action =
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .mapNotNull { node -> node.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label } }
                .first()
        runOnUiThread { action.action() }
    }

    /** Undo's box at rest, measured on a first removal (Delta) that is then undone; the bar is gone afterwards. */
    private fun ComposeUiTest.restingUndo(port: Port): DpRect {
        removeByAction("Delta")
        mainClock.advanceTimeBy(1_000)
        val b = onNodeWithText("Undo").getBoundsInRoot()
        tap(center(b))
        mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("remove d", "undo d"), port.acts, "calibration: Delta removed and brought back")
        port.acts.clear()
        return b
    }

    private fun center(r: DpRect): Pair<Dp, Dp> = ((r.left + r.right) / 2) to ((r.top + r.bottom) / 2)

    private fun ComposeUiTest.tap(at: Pair<Dp, Dp>) {
        onRoot().performTouchInput { click(Offset(at.first.toPx(), at.second.toPx())) }
    }

    @Test
    fun `B-DOCK-UNDO-T1 removed from its long-press card, Undo tapped in the bar's first frame brings the ring back`() =
        runComposeUiTest {
            val port = Port(entries(), DockPlacement())
            val taps = Taps()
            chat(port, taps)
            val undo = center(restingUndo(port))
            // Hold Alpha's ring until its card opens, and let go: the card stays.
            val ring = onAllNodes(hasContentDescription("Alpha"), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
            onRoot().performTouchInput { down(ring.center) }
            mainClock.advanceTimeBy(700)
            onRoot().performTouchInput { up() }
            mainClock.advanceTimeBy(600)
            val remove = onAllNodes(hasContentDescription("Remove Alpha"), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(remove.isNotEmpty(), "Alpha's card is open with its Remove (acts ${port.acts})")
            // The card's minus, by touch, as on the phone; the card is still closing when the bar appears.
            onRoot().performTouchInput { click(remove.first().boundsInRoot.center) }
            mainClock.advanceTimeByFrame()
            tap(undo)
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("remove a", "undo a"), port.acts, "the tap in the bar's first frame ran Undo, and only Undo")
            assertEquals(0, taps.more, "the reply's More under the bar")
        }

    @Test
    fun `B-DOCK-UNDO-T2 where the dock's foot lies under Undo a tap there never starts a new conversation too`() =
        runComposeUiTest {
            // The dock at the foot of its room, as with seven rings on the phone: its "+" band reaches under the bar.
            val port = Port(entries(), DockPlacement(DockSide.End, 1f))
            val taps = Taps()
            chat(port, taps)
            val undo = restingUndo(port)
            // A point inside Undo's target that is also inside the dock's foot (its "+" band), right of the strip's edge.
            val x = (maxOf(undo.left, 356.dp) + undo.right) / 2
            val at = x to (undo.top + 6.dp)
            // Without a bar, that point is the dock's own: it starts a new conversation (else this test means nothing).
            tap(at)
            mainClock.advanceTimeBy(500)
            assertEquals(listOf("new"), port.acts, "the point ($at) lies in the dock's foot when no bar is up (Undo $undo)")
            port.acts.clear()

            // First frame of the bar.
            removeByAction("Gamma")
            mainClock.advanceTimeByFrame()
            tap(at)
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("remove c", "undo c"), port.acts, "first frame: Undo, and nothing of the dock's")
            port.acts.clear()

            // At rest.
            removeByAction("Beta")
            mainClock.advanceTimeBy(2_000)
            tap(at)
            mainClock.advanceTimeBy(1_000)
            assertEquals(listOf("remove b", "undo b"), port.acts, "at rest: Undo, and nothing of the dock's")
            port.acts.clear()

            // The window runs out; the bar's last exit frame (QUICK, 150 ms: nine frames in).
            removeByAction("Alpha")
            mainClock.advanceTimeBy(UNDO_WINDOW_MS + 16)
            repeat(9) { mainClock.advanceTimeByFrame() }
            tap(at)
            mainClock.advanceTimeBy(50)
            assertEquals(listOf("remove a"), port.acts, "last exit frame: the leaving bar runs nothing, the dock nothing")
            // Inside the guard after the exit.
            mainClock.advanceTimeBy(100)
            tap(at)
            mainClock.advanceTimeBy(50)
            assertEquals(listOf("remove a"), port.acts, "inside the guard: nothing")
            // After the guard the dock's foot is live again: one tap, one new conversation.
            mainClock.advanceTimeBy(GUARD_MS + 200)
            tap(at)
            mainClock.advanceTimeBy(500)
            assertEquals(listOf("remove a", "new"), port.acts, "after the guard: the dock's own place again, once")
            assertEquals(0, taps.more, "the reply's More")
        }

    private companion object {
        /** The kit's UNDO_GUARD_MS (written out so the test also runs against a bar that has none). */
        const val GUARD_MS = 300L
    }
}
