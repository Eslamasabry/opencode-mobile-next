package io.github.eslamasabry.oc.feature.chats.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.dock.DockEntry
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockPresence
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockState
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.feature.chats.ChatEnvironment
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.dock.KitDockInsets
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Device run 5, problem 2: in a conversation, "Undo" on the "<name> left the dock" bar opened the reply's "…" menu
 * under it instead of bringing the ring back. Laid out the way the chat lays the dock out (over a recorded transcript
 * inside the screen frame, the Undo bar lifted by the frame's bottom room), a tap on Undo's centre must run Undo and
 * nothing under the bar.
 */
@OptIn(ExperimentalTestApi::class)
class EdgeDockUndoTapTest {
    private val p = ProfileId("p1")

    private fun ref(id: String) = ConversationRef(p, SessionId(id))

    private class Port(entries: List<DockEntry>) : EdgeDockPort {
        val acts = mutableListOf<String>()
        private val model = MutableStateFlow(DockUi(entries = persistentListOf(*entries.toTypedArray()), presence = DockPresence.Out))
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

        override fun newConversation() {}
    }

    private fun entries() =
        listOf(
            DockEntry(ref("a"), "Alpha", null, null, DockState.Idle, 0),
            DockEntry(ref("b"), "Beta", null, null, DockState.Idle, 0),
            DockEntry(ref("c"), "Gamma", null, null, DockState.Idle, 0),
        )

    /** Removes [name]'s ring the way assistive technology does (the ring's own "Remove <name>" action). */
    private fun androidx.compose.ui.test.ComposeUiTest.removeRing(name: String) {
        val label = "Remove $name"
        val action =
            onAllNodesWithCustomActions()
                .mapNotNull { node -> node.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == label } }
                .first()
        runOnIdle { action.action() }
        waitForIdle()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithCustomActions() =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .fetchSemanticsNodes()

    @Test
    fun `B-DOCK-UNDO-01 in a conversation Undo on the bar runs Undo, not the reply's More under it`() = runComposeUiTest {
        val port = Port(entries())
        var more = 0
        setContent {
            ChatEnvironment {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Box(Modifier.size(412.dp, 820.dp)) {
                        val frost = rememberKitFrostState()
                        // The chat's frame: page, overlaid chrome, a recorded transcript, edge to edge (ChatScreen.kt).
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
                                // The newest reply's footer rests right above the message box, its More at the end.
                                Row(
                                    Modifier.align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(bottom = chrome.calculateBottomPadding() + 12.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    KitIconButton(KitIcons.more, "More for this reply", { more++ })
                                }
                            }
                            // The dock host exactly as AppChatEdgeDock calls it.
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
        waitForIdle()
        removeRing("Alpha")
        val undo = onNodeWithText("Undo").getBoundsInRoot()
        val under = onNodeWithContentDescription("More for this reply").getBoundsInRoot()
        assertTrue(overlaps(undo, under), "the reply's More must lie under Undo for this test to mean anything: Undo $undo, More $under")
        onNodeWithText("Undo").performTouchInput { click(center) }
        waitForIdle()
        assertEquals(0, more, "the tap on Undo reached the reply's More under the bar (Undo $undo, More $under; acts ${port.acts})")
        assertEquals(listOf("remove a", "undo a"), port.acts, "Undo ran (Undo $undo, More $under)")
    }

    private fun overlaps(a: DpRect, b: DpRect): Boolean = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}
