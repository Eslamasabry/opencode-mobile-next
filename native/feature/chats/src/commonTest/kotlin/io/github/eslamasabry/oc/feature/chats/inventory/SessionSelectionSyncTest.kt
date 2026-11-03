package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionSelectionSyncTest {
    private val p = ProfileId("p")

    private fun ref(id: String) = ModelRef("p", id)

    private fun selected(id: String, model: String = "one", agent: String = "build", variant: String = ""): Session =
        Session(
            id = SessionId(id),
            directory = "/one",
            title = id,
            cost = 3.0,
            reverted = true,
            selection = SessionSelection(ref(model), variant, agent, modelKnown = true, agentKnown = true),
        )

    /** The server: records keyed by id, writes recorded and optionally held or failed. */
    private class SelectionServer(val source: FakeSource) : SessionSelectionWriter {
        val values = linkedMapOf<SessionId, Session>()
        val writes = mutableListOf<String>()
        var beforeWrite: (suspend () -> Outcome<Unit>)? = null
        var read: (suspend (SessionId) -> Session)? = null

        init {
            source.page = { Outcome.Ok(page(*values.values.toTypedArray())) }
            source.one = { id -> Outcome.Ok(read?.invoke(id) ?: values.getValue(id)) }
        }

        override suspend fun setSessionModel(id: SessionId, model: ModelRef, variant: String): Outcome<Unit> {
            writes += "model:${id.raw}:${model.providerId}/${model.modelId}:$variant"
            beforeWrite?.invoke()?.let { if (it is Outcome.Failed) return it }
            val now = values.getValue(id)
            values[id] = now.copy(selection = now.selection!!.copy(model = model, variant = variant, modelKnown = true))
            return Outcome.Ok(Unit)
        }

        override suspend fun setSessionAgent(id: SessionId, agent: String): Outcome<Unit> {
            writes += "agent:${id.raw}:$agent"
            beforeWrite?.invoke()?.let { if (it is Outcome.Failed) return it }
            val now = values.getValue(id)
            values[id] = now.copy(selection = now.selection!!.copy(agent = agent, agentKnown = true))
            return Outcome.Ok(Unit)
        }
    }

    private class Harness(val inventory: SessionInventory, val server: SelectionServer, val sync: SessionSelectionSync) {
        var connected = true
    }

    private suspend fun TestScope.harness(
        storage: FakeStorage = FakeStorage(),
        seed: (SelectionServer) -> Unit = {},
    ): Harness {
        val source = FakeSource()
        val server = SelectionServer(source).apply {
            values[SessionId("a")] = selected("a")
            values[SessionId("b")] = selected("b")
            seed(this)
        }
        val inventory = SessionInventory(backgroundScope).apply { connect(source, ServerLocation(directory = "/one")) }
        lateinit var h: Harness
        val sync = SessionSelectionSync(inventory, storage, backgroundScope, profile = { p }, writer = { server.takeIf { h.connected } })
        h = Harness(inventory, server, sync)
        inventory.refresh()
        return h
    }

    /** A cold start: no gateway yet, an empty inventory, the same preference file. */
    private fun TestScope.restarted(storage: FakeStorage): Pair<SessionInventory, SessionSelectionSync> {
        val inventory = SessionInventory(backgroundScope).apply { connect(null) }
        return inventory to SessionSelectionSync(inventory, storage, backgroundScope, profile = { p }, writer = { null })
    }

    @Test
    fun `B-STATE-117 the cold start model fallback is per conversation and survives restart`() = runTest {
        val storage = FakeStorage()
        val h = harness(storage) { it.values[SessionId("b")] = selected("b", model = "two") }
        assertEquals("one", h.sync.displayModelFor(SessionId("a"))?.modelId)
        assertEquals("two", h.sync.displayModelFor(SessionId("b"))?.modelId)
        runCurrent()

        val (inventory, restored) = restarted(storage)
        assertFalse(restored.selectionFor(SessionId("a")).modelKnown)
        assertNull(restored.modelFor(SessionId("a")))
        assertEquals("one", restored.displayModelFor(SessionId("a"))?.modelId)
        assertEquals("two", restored.displayModelFor(SessionId("b"))?.modelId)
        assertNull(restored.displayModelFor(SessionId("unknown")))
        // A confirmed server default invalidates, rather than borrows, a remembered model.
        inventory.adopt(listOf(Session(id = SessionId("a"), selection = SessionSelection(null, "", null, modelKnown = true, agentKnown = true))))
        assertNull(restored.displayModelFor(SessionId("a")))
        assertEquals("two", restored.displayModelFor(SessionId("b"))?.modelId)
        runCurrent()

        val (_, afterDefault) = restarted(storage)
        assertNull(afterDefault.displayModelFor(SessionId("a")))
        assertEquals("two", afterDefault.displayModelFor(SessionId("b"))?.modelId)
        assertTrue(SessionSelectionSync.keyFor(p) in storage.values)
    }

    @Test
    fun `B-STATE-117 a cold start selection stays unknown before its gateway exists`() = runTest {
        val storage = FakeStorage()
        val h = harness(storage)
        assertEquals("one", h.sync.displayModelFor(SessionId("a"))?.modelId)
        h.sync.displayModelFor(SessionId("b"))
        runCurrent()
        val (inventory, offline) = restarted(storage)
        assertNull(inventory.currentSource)
        assertFalse(offline.selectionFor(SessionId("a")).modelKnown)
        assertNull(offline.modelFor(SessionId("a")))
        assertEquals("one", offline.displayModelFor(SessionId("a"))?.modelId)
        assertEquals("one", offline.displayModelFor(SessionId("b"))?.modelId)
    }

    @Test
    fun `B-STATE-117 a status change does not discard selection hydration`() = runTest {
        val h = harness()
        val pending = CompletableDeferred<Session>()
        h.server.read = { pending.await() }
        val read = async { h.inventory.ensureSession(SessionId("a")) }
        runCurrent()
        h.inventory.applyStatus(SessionId("a"), SessionStatus.Busy)
        pending.complete(selected("a", model = "fresh"))
        read.await()
        assertTrue(SessionId("a") in h.inventory.busy)
        assertEquals("fresh", h.sync.modelFor(SessionId("a"))?.modelId)
    }

    @Test
    fun `B-STATE-118 offline metadata stays server-owned and writes need a connection`() = runTest {
        val h = harness()
        h.connected = false
        assertEquals(ref("one"), h.sync.modelFor(SessionId("a")))
        assertEquals("build", h.sync.agentFor(SessionId("a")))
        val result = h.sync.selectAgent(SessionId("a"), "plan")
        assertIs<Outcome.Failed>(result)
        assertIs<Failure.Network>(result.failure)
        assertEquals("build", h.sync.agentFor(SessionId("a")))
        assertTrue(h.server.writes.isEmpty())
    }

    @Test
    fun `B-STATE-118 pending metadata cannot overwrite a newer remote selection`() = runTest {
        val h = harness()
        val pending = CompletableDeferred<Session>()
        h.server.read = { pending.await() }
        val read = async { h.inventory.ensureSession(SessionId("a")) }
        runCurrent()
        // A live `session.agent.selected` event.
        h.inventory.applyPatch(SessionId("a")) { it.copy(selection = it.selection!!.copy(agent = "remote", agentKnown = true)) }
        pending.complete(selected("a"))
        read.await()
        assertEquals("remote", h.sync.agentFor(SessionId("a")))
    }

    @Test
    fun `B-STATE-118 intentional writes serialize and confirmation adopts server state`() = runTest {
        val h = harness()
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        h.server.beforeWrite = {
            started.complete(Unit)
            gate.await()
            Outcome.Ok(Unit)
        }
        val model = async { h.sync.selectModel(SessionId("a"), ref("two")) }
        val agent = async { h.sync.selectAgent(SessionId("a"), "plan") }
        started.await()
        assertEquals(listOf("model:a:p/two:"), h.server.writes)
        assertTrue(h.sync.saving(SessionId("a")))
        gate.complete(Unit)
        assertEquals(Outcome.Ok(Unit), model.await())
        assertEquals(Outcome.Ok(Unit), agent.await())
        assertEquals(listOf("model:a:p/two:", "agent:a:plan"), h.server.writes)
        assertEquals(ref("two"), h.sync.modelFor(SessionId("a")))
        assertEquals("plan", h.sync.agentFor(SessionId("a")))
        assertFalse(h.sync.saving(SessionId("a")))
        h.sync.selectModel(SessionId("a"), ref("two"))
        h.sync.selectAgent(SessionId("a"), "plan")
        assertEquals(2, h.server.writes.size)
        assertEquals(ref("one"), h.sync.modelFor(SessionId("b")))
    }

    @Test
    fun `B-STATE-118 failed writes keep the confirmed selection and allow retry`() = runTest {
        val h = harness()
        val unavailable = Failure.Server(503)
        h.server.beforeWrite = { Outcome.Failed(unavailable) }
        assertEquals(Outcome.Failed(unavailable), h.sync.selectModel(SessionId("a"), ref("two")))
        assertEquals(ref("one"), h.sync.modelFor(SessionId("a")))
        assertEquals(unavailable, h.sync.errors[SessionId("a")])
        h.server.beforeWrite = null
        assertEquals(Outcome.Ok(Unit), h.sync.selectModel(SessionId("a"), ref("two")))
        assertEquals(ref("two"), h.sync.modelFor(SessionId("a")))
        assertTrue(h.sync.errors.isEmpty())
    }
}
