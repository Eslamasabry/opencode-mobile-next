package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class DockReaderTest {
    private val p = ProfileId("p1")
    private fun ref(n: Int) = ConversationRef(p, SessionId("s$n"))
    private val all = (1..10).map(::ref)

    private fun fixture(): List<DockEvent> =
        listOf<DockEvent>(DockEvent.PinsLoaded(p, emptyList())) +
            all.flatMap { listOf(DockEvent.RunStarted(it), DockEvent.Meta(it, "Chat ${it.sessionId.raw}", "build")) } +
            listOf(DockEvent.RequestOpened(all[3], "permission:r"), DockEvent.Tasks(all[1], 2, 5), DockEvent.RunEnded(all[6], DockRunEnd.Failed))

    private fun controller(scope: kotlinx.coroutines.CoroutineScope) = DockController(scope, DockNicknames(FakeAttentionKeyStore()), {})

    @Test
    fun `the rings are byte for byte the same with or without a second reader`() = runTest(StandardTestDispatcher()) {
        val alone = controller(this)
        val shared = controller(this)
        val early = shared.ui(DockReader.list())
        for (e in fixture()) {
            alone.feed(e)
            shared.feed(e)
        }
        val late = controller(this).also { c -> for (e in fixture()) c.feed(e) }.ui(DockReader.list())
        assertEquals(alone.ui.value, shared.ui.value)
        assertEquals(DockModel.DEFAULT_CAP, shared.ui.value.entries.size)
        assertEquals(3, shared.ui.value.overflow)
        assertEquals(10, early.value.entries.size, "the list reader asks for its own cap")
        assertEquals(0, early.value.overflow)
        assertEquals(10, late.value.entries.size, "a reader that arrives late starts from what is known")
    }

    @Test
    fun `a reader's cap is its own and the controller's constructor cap only sets the rings`() = runTest(StandardTestDispatcher()) {
        val c = DockController(this, DockNicknames(FakeAttentionKeyStore()), {}, cap = 3)
        for (e in fixture()) c.feed(e)
        assertEquals(3, c.ui.value.entries.size)
        assertEquals(4, c.ui(DockReader(cap = 4)).value.entries.size)
        assertEquals(3, c.ui.value.entries.size)
        assertEquals(c.ui(DockReader(cap = 4)), c.ui(DockReader(cap = 4)), "the same reader gets the same flow")
    }

    @Test
    fun `the rings show five pins while the list shows every pin the store holds`() = runTest(StandardTestDispatcher()) {
        val c = controller(this)
        val list = c.ui(DockReader.list())
        c.feed(DockEvent.PinsLoaded(p, all.take(8).map { it.sessionId }))
        for (r in all) c.feed(DockEvent.RunStarted(r))
        assertEquals(DockModel.MAX_PINNED, c.ui.value.entries.count { it.pinned })
        assertEquals(8, list.value.entries.count { it.pinned })
        assertEquals(all.take(5), c.ui.value.entries.filter { it.pinned }.map { it.ref }, "today's five, in order")
    }

    @Test
    fun `a pin the rings refuse is kept for the list and saved`() = runTest(StandardTestDispatcher()) {
        val shared = FakeSharedPins()
        val disk = FakeAttentionKeyStore()
        val c = DockController(this, DockNicknames(disk), {}, pins = DockPins(disk, shared))
        val list = c.ui(DockReader.list())
        c.loadPins(p)
        for (r in all.take(6)) c.feed(DockEvent.RunStarted(r))
        for (r in all.take(6)) c.pin(r)
        testScheduler.advanceUntilIdle()
        assertEquals(DockModel.MAX_PINNED, c.ui.value.entries.count { it.pinned }, "the rings keep their limit")
        assertEquals(6, list.value.entries.count { it.pinned })
        assertEquals(6, shared.ordered(p).size, "nothing the list pinned is lost on save")
        c.unpin(all[0])
        testScheduler.advanceUntilIdle()
        assertTrue(shared.ordered(p).none { it == all[0].sessionId })
        assertEquals(5, shared.ordered(p).size)
    }
}
