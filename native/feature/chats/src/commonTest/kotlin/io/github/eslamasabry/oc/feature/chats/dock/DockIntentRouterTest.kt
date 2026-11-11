package io.github.eslamasabry.oc.feature.chats.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.dock.DockChild
import io.github.eslamasabry.oc.engine.attention.dock.DockEntry
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockPresence
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestKind
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockState
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.ui.kit.dock.KitDockSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DockIntentRouterTest {
    private val p = ProfileId("p1")

    private fun ref(id: String) = ConversationRef(p, SessionId(id))

    private fun key(id: String) = DockKitMapper.key(ref(id))

    /** Every act the router asks for, in order, as plain words; a failing act reports failure when [fail] is set. */
    private class FakePort(var fail: Boolean = false) : EdgeDockPort {
        val acts = mutableListOf<String>()
        override val ui: StateFlow<DockUi> = MutableStateFlow(DockUi())
        override val peeks: StateFlow<Map<ConversationRef, DockPeek>> = MutableStateFlow(emptyMap())
        override val shown: StateFlow<Boolean> = MutableStateFlow(true)
        override val onScreen = MutableStateFlow<ConversationRef?>(null)

        override fun open(ref: ConversationRef) { acts += "open ${ref.sessionId.raw}" }

        override fun openRequest(ref: ConversationRef, requestId: String) { acts += "openRequest ${ref.sessionId.raw} $requestId" }

        override fun answer(ref: ConversationRef, request: DockRequestRef, allow: Boolean, onFailed: () -> Unit) {
            acts += "answer ${ref.sessionId.raw} ${request.id} $allow"
            if (fail) onFailed()
        }

        override fun stop(ref: ConversationRef, onFailed: () -> Unit) {
            acts += "stop ${ref.sessionId.raw}"
            if (fail) onFailed()
        }

        override fun rename(ref: ConversationRef, name: String?) { acts += "rename ${ref.sessionId.raw} $name" }

        override fun remove(ref: ConversationRef) { acts += "remove ${ref.sessionId.raw}" }

        override fun undoRemove(ref: ConversationRef) { acts += "undo ${ref.sessionId.raw}" }

        override fun restore(ref: ConversationRef) { acts += "restore ${ref.sessionId.raw}" }

        override fun pin(ref: ConversationRef) { acts += "pin ${ref.sessionId.raw}" }

        override fun unpin(ref: ConversationRef) { acts += "unpin ${ref.sessionId.raw}" }

        override fun reorder(from: ConversationRef, to: ConversationRef) { acts += "reorder ${from.sessionId.raw} ${to.sessionId.raw}" }

        override fun setPlacement(side: DockSide, bias: Float) { acts += "place $side $bias" }

        override fun tuck() { acts += "tuck" }

        override fun untuck() { acts += "untuck" }

        override fun newConversation() { acts += "new" }
    }

    private class Told : DockFeedback {
        val said = mutableListOf<String>()

        override fun removed(key: String, name: String) { said += "removed $name" }

        override fun staysWhileOpen(name: String) { said += "stays $name" }

        override fun stopFailed(name: String) { said += "stopFailed $name" }

        override fun answerFailed(name: String) { said += "answerFailed $name" }
    }

    private val permission = DockRequestRef(DockRequestKind.Permission, "perm")
    private val question = DockRequestRef(DockRequestKind.Question, "q")
    private val child = DockChild(ref("c1"), "child", "explore", DockState.Working, 0)

    private fun frame(): DockKitFrame {
        val entries =
            persistentListOf(
                DockEntry(ref("a"), "Alpha", null, null, DockState.NeedsYou, 1, requests = persistentListOf(permission), children = persistentListOf(child)),
                DockEntry(ref("b"), "Beta", null, null, DockState.NeedsYou, 1, requests = persistentListOf(question)),
                DockEntry(ref("c"), "Gamma", null, null, DockState.Working, 0, removable = false),
            )
        return DockKitMapper.map(DockUi(entries = entries, presence = DockPresence.Out), emptyMap(), "U")
    }

    private fun router(port: FakePort, told: Told = Told()) = DockIntentRouter(port, { frame() }, told)

    @Test
    fun open_goes_to_the_ring_and_a_sub_agent_to_its_own_conversation() {
        val port = FakePort()
        val r = router(port)
        r.onOpen(key("a"))
        r.onOpenChild(key("a"), key("c1"))
        r.onOpen("unknown")
        assertEquals(listOf("open a", "open c1"), port.acts)
    }

    @Test
    fun deny_answers_a_permission_at_once() {
        val port = FakePort()
        router(port).onDeny(key("a"))
        assertEquals(listOf("answer a perm false"), port.acts)
    }

    @Test
    fun allow_away_from_the_conversation_opens_it_at_the_request() {
        val port = FakePort()
        router(port).onAllow(key("a"))
        assertEquals(listOf("openRequest a perm"), port.acts)
    }

    @Test
    fun allow_while_the_conversation_is_on_screen_answers_at_once() {
        val port = FakePort().also { it.onScreen.value = ref("a") }
        router(port).onAllow(key("a"))
        assertEquals(listOf("answer a perm true"), port.acts)
    }

    @Test
    fun a_question_opens_at_the_request_for_allow_and_deny() {
        val port = FakePort()
        val r = router(port)
        r.onAllow(key("b"))
        r.onDeny(key("b"))
        assertEquals(listOf("openRequest b q", "openRequest b q"), port.acts)
    }

    @Test
    fun a_failed_answer_or_stop_says_so_by_name() {
        val port = FakePort(fail = true)
        val told = Told()
        val r = router(port, told)
        r.onDeny(key("a"))
        r.onStop(key("c"))
        assertEquals(listOf("answer a perm false", "stop c"), port.acts)
        assertEquals(listOf("answerFailed Alpha", "stopFailed Gamma"), told.said)
    }

    @Test
    fun remove_offers_undo_and_undo_brings_it_back() {
        val port = FakePort()
        val told = Told()
        val r = router(port, told)
        r.onRemove(key("a"))
        r.undoRemove(key("a"))
        r.onRestore(key("b"))
        assertEquals(listOf("remove a", "undo a", "restore b"), port.acts)
        assertEquals(listOf("removed Alpha"), told.said)
    }

    @Test
    fun undo_works_after_the_ring_has_left_the_frame() {
        val port = FakePort()
        var gone = false
        val shown = frame()
        val without = DockKitFrame(shown.model, shown.refs - key("a"), shown.asks, shown.removable)
        val r = DockIntentRouter(port, { if (gone) without else shown }, Told())
        r.onRemove(key("a"))
        gone = true
        r.undoRemove(key("a"))
        assertEquals(listOf("remove a", "undo a"), port.acts)
        // A sub-agent too.
        gone = false
        val k = key("c1")
        r.onRemove(k)
        gone = true
        r.undoRemove(k)
        assertEquals(listOf("remove a", "undo a", "remove c1", "undo c1"), port.acts)
    }

    @Test
    fun the_conversation_on_screen_stays_and_the_person_is_told_why() {
        val port = FakePort()
        val told = Told()
        router(port, told).onRemove(key("c"))
        assertEquals(emptyList<String>(), port.acts)
        assertEquals(listOf("stays Gamma"), told.said)
    }

    @Test
    fun rename_trims_and_an_empty_name_clears_the_nickname() {
        val port = FakePort()
        val r = router(port)
        r.onRename(key("a"), "  Bank API ")
        r.onRename(key("a"), "   ")
        assertEquals(listOf("rename a Bank API", "rename a null"), port.acts)
    }

    @Test
    fun reorder_pin_tuck_placement_and_new_conversation_reach_the_port() {
        val port = FakePort()
        val r = router(port)
        r.onReorder(0, 2)
        r.onReorder(0, 9)
        r.onPinChange(key("b"), true)
        r.onPinChange(key("b"), false)
        r.onTuckChange(true)
        r.onTuckChange(false)
        r.onPlacementChange(KitDockSide.Start, 0.25f)
        r.onNewConversation()
        assertEquals(
            listOf("reorder a c", "pin b", "unpin b", "tuck", "untuck", "place Start 0.25", "new"),
            port.acts,
        )
    }
}
