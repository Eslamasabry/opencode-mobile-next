package io.github.eslamasabry.oc.feature.chats.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
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
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The host reports the Undo bar's room to its screen while an offer stands, and 0 after (the chat keeps that room clear). */
@OptIn(ExperimentalTestApi::class)
class EdgeDockUndoRoomTest {
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
        runOnUiThread { action.action() }
        mainClock.advanceTimeBy(1_000)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithCustomActions() =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions), useUnmergedTree = true)
            .fetchSemanticsNodes()

    @Test
    fun `B-DOCK-UNDO-07 the host reports the Undo bar's height while an offer stands and 0 after`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val port = Port(entries())
        val heights = mutableListOf<Int>()
        setContent {
            ChatEnvironment {
                KitTheme(
                    appearance = KitAppearance(motion = MotionLevel.Off),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    Box(Modifier.size(412.dp, 820.dp)) { EdgeDockHost(port, onUndoHeight = { heights += it }) }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        assertTrue(heights.all { it == 0 }, "no offer, no room: $heights")
        removeRing("Alpha")
        mainClock.advanceTimeBy(1_000)
        val bar = onNodeWithText("Undo").getBoundsInRoot()
        val room = heights.last()
        assertTrue(room > 0, "the host reported the bar's room: $heights")
        assertTrue(room >= (bar.bottom - bar.top).value.toInt(), "the room covers the bar (bar $bar, room $room px)")
        onNodeWithText("Undo").performTouchInput { click(center) }
        mainClock.advanceTimeBy(2_000)
        assertEquals(0, heights.last(), "the room went with the bar: $heights")
    }
}
