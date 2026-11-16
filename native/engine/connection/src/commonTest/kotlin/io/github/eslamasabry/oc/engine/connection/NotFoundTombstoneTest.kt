package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.testing.FakeServerGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest

class NotFoundTombstoneTest {
    private val id = SessionId("late-listed")

    @Test
    fun `B-STATE-107 startup retry rechecks a session listed after its early NotFound`() = runTest {
        val gateway = FakeServerGateway()
        val reads =
            ConnectionReads(gateway, ConnectionCoroutineScope(backgroundScope.coroutineContext))
        assertIs<Failure.NotFound>(assertIs<Outcome.Failed>(reads.session(id)).failure)
        gateway.sessionList = persistentListOf(Session(id))
        assertEquals(
            id,
            assertIs<Outcome.Ok<ServerPage<Session>>>(gateway.sessionPage())
                .value
                .items
                .single()
                .id,
        )
        reads.unforget(id)
        assertEquals(id, assertIs<Outcome.Ok<Session>>(reads.session(id)).value.id)
        assertTrue(reads.missing.value.isEmpty())
    }

    @Test
    fun `B-STATE-107 startup retry rechecks history after its early NotFound`() = runTest {
        val gateway = FakeServerGateway()
        val reads =
            ConnectionReads(gateway, ConnectionCoroutineScope(backgroundScope.coroutineContext))
        gateway.failNext = Failure.NotFound()
        assertIs<Failure.NotFound>(assertIs<Outcome.Failed>(reads.tail(id)).failure)
        gateway.sessionList = persistentListOf(Session(id))
        assertIs<Outcome.Ok<ServerPage<Session>>>(gateway.sessionPage())
        reads.unforget(id)
        assertIs<Outcome.Ok<*>>(reads.tail(id))
        assertTrue(reads.missing.value.isEmpty())
    }

    @Test
    fun `B-STATE-107 a connected server with its list read keeps an authoritative NotFound without startup retry`() =
        runTest {
            val gateway = FakeServerGateway()
            gateway.events.connect()
            assertIs<Outcome.Ok<ServerPage<Session>>>(gateway.sessionPage())
            val reads =
                ConnectionReads(gateway, ConnectionCoroutineScope(backgroundScope.coroutineContext))
            assertIs<Failure.NotFound>(assertIs<Outcome.Failed>(reads.session(id)).failure)
            gateway.sessionList = persistentListOf(Session(id))
            assertIs<Failure.NotFound>(
                assertIs<Outcome.Failed>(reads.session(id, fresh = true)).failure
            )
            assertIs<Failure.NotFound>(assertIs<Outcome.Failed>(reads.tail(id)).failure)
            assertEquals(1, gateway.calls.count { it == "session ${id.raw}" })
        }

    @Test
    fun `B-STATE-107 a confirmed remote deletion cannot be undone by startup retry or a late page`() =
        runTest {
            val gateway = FakeServerGateway(listOf(Session(id)))
            gateway.events.connect()
            assertIs<Outcome.Ok<ServerPage<Session>>>(gateway.sessionPage())
            val reads =
                ConnectionReads(gateway, ConnectionCoroutineScope(backgroundScope.coroutineContext))
            assertIs<Outcome.Ok<Session>>(reads.session(id))
            // The list's confirmed-delete hook is a regular callback, not a suspend command.
            val forget: (SessionId) -> Unit = reads::forget
            forget(id)
            reads.unforget(id)
            assertIs<Failure.NotFound>(
                assertIs<Outcome.Failed>(reads.session(id, fresh = true)).failure
            )
            assertIs<Failure.NotFound>(assertIs<Outcome.Failed>(reads.tail(id)).failure)
            assertTrue(id in reads.missing.value)
        }
}
