package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** The home's pin store, in memory, as the dock sees it. */
internal class FakeSharedPins : SharedPinOrder {
    val store = mutableMapOf<ProfileId, List<SessionId>>()
    var refuse = false
    var writes = 0
    private val signal = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    override val changes: Flow<Unit> = signal

    override fun ordered(profile: ProfileId): List<SessionId> = store[profile].orEmpty()

    override suspend fun replace(profile: ProfileId, ids: List<SessionId>): Boolean {
        if (refuse) return false
        if (store[profile].orEmpty() == ids) return true
        writes++
        if (ids.isEmpty()) store.remove(profile) else store[profile] = ids
        signal.tryEmit(Unit)
        return true
    }
}

class DockOnePinTest {
    private val p = ProfileId("p1")
    private fun ids(vararg n: String) = n.map(::SessionId)
    private fun ref(n: String) = ConversationRef(p, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c = ref("c")

    private fun legacy(disk: FakeAttentionKeyStore, vararg n: String) {
        disk.data[DockKeys.pinsKey(p)] = n.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    @Test
    fun `migration - only dock pins carry over in the dock's order and the old key goes`() = runTest {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins()
        legacy(disk, "b", "a")
        assertEquals(ids("b", "a"), DockPins(disk, shared).load(p))
        assertEquals(ids("b", "a"), shared.ordered(p))
        assertFalse(DockKeys.pinsKey(p) in disk.data, "the dock's own key is retired")
    }

    @Test
    fun `migration - only home pins stay as they are and nothing is written`() = runTest {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins().apply { store[p] = ids("x", "y") }
        assertEquals(ids("x", "y"), DockPins(disk, shared).load(p))
        assertEquals(0, shared.writes)
    }

    @Test
    fun `migration - both with overlap puts dock pins first then the home pins not already there`() = runTest {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins().apply { store[p] = ids("b", "c") }
        legacy(disk, "a", "b")
        assertEquals(ids("a", "b", "c"), DockPins(disk, shared).load(p))
        assertEquals(ids("a", "b", "c"), shared.ordered(p))
        assertEquals(ids("a", "b", "c"), DockPins.merge(ids("a", "b"), ids("b", "c")))
    }

    @Test
    fun `migration - none is none`() = runTest {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins()
        assertEquals(emptyList(), DockPins(disk, shared).load(p))
        assertEquals(0, shared.writes)
    }

    @Test
    fun `migration runs once - a second load changes and writes nothing`() = runTest {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins().apply { store[p] = ids("b", "c") }
        legacy(disk, "a", "b")
        val first = DockPins(disk, shared).load(p)
        val writesAfterFirst = shared.writes
        assertEquals(first, DockPins(disk, shared).load(p))
        assertEquals(writesAfterFirst, shared.writes)
    }

    @Test
    fun `a refused write keeps the old key so the next load tries again`() = runTest {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins().apply { refuse = true }
        legacy(disk, "a")
        assertEquals(ids("a"), DockPins(disk, shared).load(p), "the pins are still shown")
        assertTrue(DockKeys.pinsKey(p) in disk.data, "no pin lost: the old key waits")
        shared.refuse = false
        assertEquals(ids("a"), DockPins(disk, shared).load(p))
        assertEquals(ids("a"), shared.ordered(p))
        assertFalse(DockKeys.pinsKey(p) in disk.data)
    }

    @Test
    fun `pin unpin and reorder on the dock land in the shared store`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins()
        val dock = DockController(this, DockNicknames(disk), {}, pins = DockPins(disk, shared))
        dock.loadPins(p)
        for (r in listOf(a, b, c)) dock.feed(DockEvent.RunStarted(r))
        dock.pin(a)
        dock.pin(b)
        dock.pin(c)
        testScheduler.advanceUntilIdle()
        assertEquals(ids("a", "b", "c"), shared.ordered(p))
        dock.reorder(c, a)
        testScheduler.advanceUntilIdle()
        assertEquals(ids("c", "a", "b"), shared.ordered(p))
        dock.unpin(a)
        testScheduler.advanceUntilIdle()
        assertEquals(ids("c", "b"), shared.ordered(p))
        assertNull(disk.data[DockKeys.pinsKey(p)], "the dock keeps no pins of its own")
    }

    @Test
    fun `a pin or an order change made on the home list shows on the dock`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins()
        val dock = DockController(this, DockNicknames(disk), {}, pins = DockPins(disk, shared))
        dock.loadPins(p)
        for (r in listOf(a, b, c)) dock.feed(DockEvent.RunStarted(r))
        fun pinned() = dock.ui.value.entries.filter { it.pinned }.map { it.ref }

        shared.replace(p, ids("b")) // the home pins b
        dock.refreshPins(p)
        assertEquals(listOf(b), pinned())
        shared.replace(p, ids("b", "c", "a")) // the home pins two more
        dock.refreshPins(p)
        assertEquals(listOf(b, c, a), pinned())
        shared.replace(p, ids("c", "b")) // the home unpins a and reorders
        dock.refreshPins(p)
        assertEquals(listOf(c, b), pinned())
        assertFalse(dock.ui.value.entries.first { it.ref == a }.pinned)
        // Reading again changes nothing and saves nothing.
        testScheduler.advanceUntilIdle()
        val writes = shared.writes
        dock.refreshPins(p)
        testScheduler.advanceUntilIdle()
        assertEquals(writes, shared.writes)
    }

    @Test
    fun `pins survive a restart through the shared store`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val shared = FakeSharedPins()
        val first = DockController(this, DockNicknames(disk), {}, pins = DockPins(disk, shared))
        first.loadPins(p)
        first.feed(DockEvent.RunStarted(a))
        first.feed(DockEvent.RunStarted(b))
        first.pin(b)
        first.pin(a)
        testScheduler.advanceUntilIdle()
        val second = DockController(this, DockNicknames(disk), {}, pins = DockPins(disk, shared))
        assertEquals(ids("b", "a"), second.loadPins(p))
        assertEquals(listOf(b, a), second.ui.value.entries.map { it.ref })
    }
}
