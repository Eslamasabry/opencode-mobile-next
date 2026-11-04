package io.github.eslamasabry.oc.engine.delivery

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.engine.delivery.receipts.CommandReceiptState
import io.github.eslamasabry.oc.engine.delivery.receipts.PendingCommandJournal
import io.github.eslamasabry.oc.engine.delivery.receipts.ProfileEndpoints
import io.github.eslamasabry.oc.engine.delivery.receipts.SessionCreationReceipts
import io.github.eslamasabry.oc.engine.delivery.receipts.SessionCreationTransport
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port of `test/session_creation_receipts_test.dart` (row B-AGENT-226). */
class SessionCreationReceiptsTest {
    private class Server(override var endpoint: String = "http://127.0.0.1:4097") : SessionCreationTransport {
        override var directory: String? = "/project"
        override val workspace: String? = null
        override var closed: Boolean = false
        val createdIds = mutableListOf<String>()
        val lookups = mutableListOf<String>()
        val admitted = mutableSetOf<String>()
        var loseResponse = true
        var missing = false

        override suspend fun createSession(id: SessionId): Outcome<SessionId> {
            assertTrue(id.raw.startsWith("ses_"), "creation supplies a durable client session id")
            createdIds += id.raw
            admitted += id.raw
            return if (loseResponse) Outcome.Failed(Failure.Network(NetworkWhy.Reset)) else id.ok()
        }

        override suspend fun lookupSession(id: SessionId): Outcome<Boolean> {
            lookups += id.raw
            return (!missing && id.raw in admitted).ok()
        }
    }

    private val profile = ProfileId("profile_1")
    private var counter = 0
    private val addresses = mutableMapOf(profile to "http://127.0.0.1:4097")
    private val endpoints = ProfileEndpoints { addresses[it] }

    private fun TestScope.open(world: World, server: SessionCreationTransport?) =
        SessionCreationReceipts.open(profile, endpoints, server, world.journals, world.clock) { "ses_client_${++counter}" }

    private fun TestScope.world(disk: Disk = Disk()) = World(disk, this, profiles = setOf(profile))

    private fun World.stored(): String? =
        (disk.persisted()[FlutterPrefsCodec.physicalKey("${PendingCommandJournal.KEY_PREFIX}profile_1")] as? RawPref.Str)?.value

    @Test
    fun `B-AGENT-226 a lost create response is checked without creating another session`() = runTest {
        val w = world()
        val server = Server()
        val receipts = (open(w, server) as Outcome.Ok).value
        val sent = (receipts.send("create_1", "tab_1") as Outcome.Ok).value
        assertEquals(CommandReceiptState.Uncertain, sent.state)
        assertEquals(1, server.createdIds.size)
        assertEquals(SessionId(server.createdIds.single()), receipts.check("create_1"))
        val repeated = (receipts.send("create_1", "tab_1") as Outcome.Ok).value
        assertEquals(CommandReceiptState.Confirmed, repeated.state)
        assertEquals(1, server.createdIds.size)
        assertTrue(server.lookups.all { it == server.createdIds.single() })
    }

    @Test
    fun `B-AGENT-226 restart preserves the creation identity and the owning tab`() = runTest {
        val w = world()
        val server = Server()
        (open(w, server) as Outcome.Ok).value.send("create_1", "tab_1")
        val restarted = w.restart()
        val again = (open(restarted, server) as Outcome.Ok).value
        again.send("create_1", "tab_1")
        assertEquals(SessionId(server.createdIds.single()), again.check("create_1"))
        assertEquals(1, server.createdIds.size)
        val command = ((DartJson.decodeOrNull(restarted.stored()!!) as Map<*, *>)["commands"] as List<*>).single() as Map<*, *>
        assertEquals("tab_1", command["tabID"])
        assertEquals("confirmed", command["state"])
    }

    @Test
    fun `B-AGENT-226 not found stays uncertain and a repeated send cannot create again`() = runTest {
        val w = world()
        val server = Server().apply { missing = true }
        val receipts = (open(w, server) as Outcome.Ok).value
        receipts.send("create_1", "tab_1")
        assertNull(receipts.check("create_1"))
        assertEquals(CommandReceiptState.Uncertain, (receipts.send("create_1", "tab_1") as Outcome.Ok).value.state)
        assertEquals(1, server.createdIds.size)
    }

    @Test
    fun `B-AGENT-226 an unknown transport cannot create a receipt owner or journal`() = runTest {
        val w = world()
        assertIs<Outcome.Failed>(open(w, null))
        assertNull(w.stored())
    }

    @Test
    fun `B-AGENT-226 another tab cannot borrow the same creation command`() = runTest {
        val w = world()
        val server = Server()
        val receipts = (open(w, server) as Outcome.Ok).value
        receipts.send("create_1", "tab_1")
        assertIs<Outcome.Failed>(receipts.send("create_1", "tab_2"))
        assertEquals(1, server.createdIds.size)
    }

    @Test
    fun `B-AGENT-226 a profile endpoint that is not the gateway's refuses construction`() = runTest {
        val w = world()
        val server = Server(endpoint = "http://127.0.0.1:4098")
        assertIs<Outcome.Failed>(open(w, server))
        assertTrue(server.createdIds.isEmpty())
        assertTrue(server.lookups.isEmpty())
        assertNull(w.stored())
    }

    @Test
    fun `B-AGENT-226 a changed endpoint or location cannot check an old receipt in the new scope`() = runTest {
        for (change in listOf("endpoint", "location")) {
            addresses[profile] = "http://127.0.0.1:4097"
            val w = world()
            val server = Server()
            val receipts = (open(w, server) as Outcome.Ok).value
            receipts.send("create_1", "tab_1")
            if (change == "endpoint") addresses[profile] = "http://127.0.0.1:4098" else server.directory = "/another-project"
            assertNull(receipts.check("create_1"), change)
            assertTrue(server.lookups.isEmpty(), change)
            assertEquals(1, server.createdIds.size, change)
        }
    }

    @Test
    fun `B-AGENT-226 a closed connection neither creates nor confirms`() = runTest {
        val w = world()
        val server = Server()
        val receipts = (open(w, server) as Outcome.Ok).value
        server.closed = true
        assertIs<Outcome.Failed>(receipts.send("create_1", "tab_1"))
        assertNull(receipts.check("create_1"))
        assertTrue(server.createdIds.isEmpty() && server.lookups.isEmpty())
    }
}
