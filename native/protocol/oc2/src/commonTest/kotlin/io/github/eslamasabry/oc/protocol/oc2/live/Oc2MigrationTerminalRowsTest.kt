package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** Flutter test/opencode_history_copy_test.dart (migration status) and api2_terminal_auth_test.dart. */
class Oc2MigrationTerminalRowsTest {
    private fun read(text: String) = HistoryMigrationStatus.fromJson(Json.parseToJsonElement(text))

    @Test
    fun `B-PROTO-063 the migration status maps its four answers`() {
        assertEquals(HistoryMigrationPhase.Required, read("""{"status":"required"}""")!!.phase)
        assertFalse(read("""{"status":"completed"}""")!!.pending)
        val running = read("""{"status":"running","progress":{"label":"Migrating sessions","numerator":3,"denominator":12}}""")!!
        assertEquals(HistoryMigrationPhase.Running, running.phase)
        assertEquals("Migrating sessions", running.label)
        assertEquals(3 to 12, running.done to running.total)
        assertTrue(running.pending)
        val failed = read("""{"status":"error","error":"disk full"}""")!!
        assertEquals(HistoryMigrationPhase.Error, failed.phase)
        assertEquals("disk full", failed.error)
        assertFalse(failed.pending)
    }

    @Test
    fun `B-PROTO-063 the data envelope is accepted and unknown shapes are rejected`() {
        assertEquals(HistoryMigrationPhase.Completed, read("""{"data":{"status":"completed"}}""")!!.phase)
        assertNull(read("""{"status":"weird"}"""))
        assertNull(HistoryMigrationStatus.fromJson(JsonPrimitive("x")))
        assertNull(HistoryMigrationStatus.fromJson(null))
    }

    @Test
    fun `B-PROTO-063 the page line is plain words and keeps the server text out`() {
        assertNull(HistoryMigrationStatus(HistoryMigrationPhase.Completed).line)
        assertEquals(HistoryMigrationLine.Copying(null, null), HistoryMigrationStatus(HistoryMigrationPhase.Required).line)
        assertEquals(
            HistoryMigrationLine.Copying(3, 12),
            HistoryMigrationStatus(HistoryMigrationPhase.Running, label = "Migrating sessions", done = 3, total = 12).line,
        )
        val error = HistoryMigrationStatus(HistoryMigrationPhase.Error, error = "SQLITE_FULL").line
        assertEquals(HistoryMigrationLine.Failed, error)
        assertFalse("SQLITE" in error.toString())
    }

    @Test
    fun `B-PROTO-063 the gateway reads the migration endpoint`() = runTest {
        val server = RowServer {
            json("""{"status":"running","progress":{"label":"Migrating sessions","numerator":1,"denominator":2}}""")
        }
        val status = assertIs<Outcome.Ok<HistoryMigrationStatus?>>(server.gateway.historyMigrationStatus()).value
        assertEquals(listOf("/api/experimental/migration/v1"), server.seen.map { it.path })
        assertEquals(1, status!!.done)
        server.gateway.close()
        // A server without the route (the beta) has nothing to report.
        val beta = RowServer(Oc2Dialect.Beta) { json("{}", HttpStatusCode.NotFound) }
        assertNull(assertIs<Outcome.Ok<HistoryMigrationStatus?>>(beta.gateway.historyMigrationStatus()).value)
        beta.gateway.close()
    }

    // ---- B-PROTO-036

    private class Echo : TerminalChannel {
        val lines = MutableSharedFlow<String>(replay = 1)
        override val output: Flow<String> = lines
        override val cursor: Long? = null

        override suspend fun write(value: String) {
            lines.emit(value)
        }

        override suspend fun close() {}
    }

    /** Stands in for the proxy in front of the websocket: it checks the Basic header and may have revoked the phone. */
    private class Proxy(val expected: String, val revoked: Boolean) : Oc2TerminalSocketOpener {
        val opened = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun open(url: String, headers: Map<String, String>): Outcome<TerminalChannel> {
            opened += url to headers
            return if (revoked || headers[HttpHeaders.Authorization] != expected)
                Failure.SignIn(SignInWhy.PasswordRejected).failed()
            else Echo().ok()
        }
    }

    private suspend fun checkTerminalAuth(username: String?, revokeAfterTicket: Boolean) {
        val expected = "Basic " + Base64.encode("${username ?: "opencode"}:fixture-token".encodeToByteArray())
        val server =
            RowServer(
                location = ServerLocation("/work/project"),
                credentials = ConnectionCredentials(username, "fixture-token"),
                origin = io.github.eslamasabry.oc.core.model.ServerOrigin("http", "127.0.0.1", 4096),
            ) { seen ->
                if (seen.request.headers[HttpHeaders.Authorization] != expected) json("", HttpStatusCode.Unauthorized)
                else if (seen.method == "POST" && seen.path == "/api/pty/pty_fixture/connect-token")
                    json("""{"data":{"ticket":"one-use-fixture-ticket"}}""")
                else json("{}", HttpStatusCode.NotFound)
            }
        val proxy = Proxy(expected, revokeAfterTicket)
        val result = server.gateway.connectTerminalWith(proxy, "pty_fixture", if (revokeAfterTicket) null else 12)
        val ticket = server.seen.single()
        assertEquals(expected, ticket.request.headers[HttpHeaders.Authorization])
        assertEquals("1", ticket.request.headers["x-opencode-ticket"])
        val (url, headers) = proxy.opened.single()
        assertEquals(expected, headers[HttpHeaders.Authorization])
        val connect = Url(url)
        assertEquals("ws", connect.protocol.name)
        assertEquals("/api/pty/pty_fixture/connect", connect.encodedPath)
        assertEquals("one-use-fixture-ticket", connect.parameters["ticket"])
        assertEquals("/work/project", connect.parameters["location[directory]"])
        assertFalse("auth_token" in connect.parameters.names())
        assertFalse("fixture-token" in url)
        assertFalse(expected.removePrefix("Basic ") in url)
        if (revokeAfterTicket) {
            assertIs<Failure.SignIn>(assertIs<Outcome.Failed>(result).failure)
        } else {
            assertEquals("12", connect.parameters["cursor"])
            val channel = assertIs<Outcome.Ok<TerminalChannel>>(result).value
            channel.write("fixture-output")
            assertEquals("fixture-output", (channel as Echo).lines.replayCache.single())
        }
        assertFalse("fixture-token" in Oc2TerminalHandshake(url, headers).toString())
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-036 the terminal keeps its default Basic header and a single-use ticket`() = runTest {
        checkTerminalAuth(null, revokeAfterTicket = false)
    }

    @Test
    fun `B-PROTO-036 a bring-your-own-server proxy gets the per-phone Basic header`() = runTest {
        checkTerminalAuth("phone-fixture", revokeAfterTicket = false)
    }

    @Test
    fun `B-PROTO-036 revocation after ticket issuance still rejects the websocket`() = runTest {
        checkTerminalAuth("phone-fixture", revokeAfterTicket = true)
    }
}
