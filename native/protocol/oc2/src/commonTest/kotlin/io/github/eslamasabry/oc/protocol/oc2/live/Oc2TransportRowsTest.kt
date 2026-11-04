package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.AddressRules
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.io.encoding.Base64
import kotlin.test.*
import kotlinx.coroutines.test.runTest

/** Flutter test/api2_transport_test.dart, api2_health_identity_test.dart, product_repository_server_test.dart, message_delete_transport_test.dart. */
class Oc2TransportRowsTest {
    @Test
    fun `B-PROTO-037 server roots normalize trailing slashes and api`() = runTest {
        for (root in listOf("http://host:4097", "http://host:4097/", "http://host:4097/api", "http://host:4097/api/")) {
            val origin = assertIs<Outcome.Ok<ServerOrigin>>(AddressRules.origin(root)).value
            assertEquals(ServerOrigin("http", "host", 4097), origin, root)
            val server = RowServer(origin = origin) { json("""{"version":"2.0.10","pid":7}""") }
            assertIs<Outcome.Ok<*>>(server.gateway.health())
            // The /api prefix is added once, never doubled from the typed root.
            assertEquals("http://host:4097/api/info", server.seen.single().request.url.toString(), root)
            server.gateway.close()
        }
    }

    @Test
    fun `B-PROTO-037 Basic auth is built from the opencode user and the credential stays in the header only`() = runTest {
        val server = RowServer(Oc2Dialect.Beta, credentials = ConnectionCredentials(null, "secret")) {
            json("""{"healthy":true,"version":"0.0.0-beta-18600","pid":4242}""")
        }
        val health = assertIs<Outcome.Ok<io.github.eslamasabry.oc.core.model.Health>>(server.gateway.health()).value
        assertTrue(health.healthy)
        assertEquals("0.0.0-beta-18600", health.version)
        val request = server.seen.single().request
        val token = Base64.encode("opencode:secret".encodeToByteArray())
        assertEquals("Basic $token", request.headers[HttpHeaders.Authorization])
        assertEquals("/api/health", request.url.encodedPath)
        val url = request.url.toString()
        assertNull(request.url.user)
        assertFalse("secret" in url)
        assertFalse(token in url)
        assertFalse("auth_token" in url)
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-038 401 maps to sign-in even with an empty body`() = runTest {
        val server = RowServer { respondEmpty(HttpStatusCode.Unauthorized) }
        assertEquals(
            SignInWhy.PasswordRejected,
            assertIs<Failure.SignIn>(assertIs<Outcome.Failed>(server.gateway.session(SessionId("s"))).failure).why,
        )
        server.gateway.close()
        assertIs<Failure.SignIn>(Oc2Errors.failureFor(401, ""))
        assertIs<Failure.SignIn>(Oc2Errors.failureFor(401, """{"_tag":"Whatever"}"""))
        val anonymous = RowServer(credentials = ConnectionCredentials.None) { respondEmpty(HttpStatusCode.Unauthorized) }
        assertEquals(
            SignInWhy.PasswordMissing,
            assertIs<Failure.SignIn>(assertIs<Outcome.Failed>(anonymous.gateway.session(SessionId("s"))).failure).why,
        )
        anonymous.gateway.close()
    }

    @Test
    fun `B-PROTO-038 tagged error envelopes surface tag status and extras without server prose`() {
        val notFound =
            Oc2Errors.failureFor(
                404,
                """{"_tag":"SessionNotFoundError","sessionID":"ses_missing","message":"Session not found: ses_missing"}""",
            )
        assertEquals(WireTag("SessionNotFoundError"), assertIs<Failure.NotFound>(notFound).tag)
        val rejected =
            Oc2Errors.failureFor(
                400,
                """{"_tag":"FormInvalidAnswerError","sessionID":"ses_missing","message":"private server prose"}""",
            )
        val typed = assertIs<Failure.Rejected>(rejected)
        assertEquals(400, typed.status)
        assertEquals(WireTag("FormInvalidAnswerError"), typed.tag)
        assertEquals("ses_missing", Oc2Errors.detail(rejected, "sessionID"))
        assertNull(Oc2Errors.detail(rejected, "message"))
        assertFalse("private server prose" in rejected.toString())
        // An untagged or non-JSON body keeps the plain status mapping.
        assertNull(assertIs<Failure.Conflict>(Oc2Errors.failureFor(409, "<html>")).tag)
    }

    @Test
    fun `B-PROTO-038 503 boot bodies map to unavailable`() = runTest {
        val starting = assertIs<Failure.Busy>(Oc2Errors.failureFor(503, """{"code":"service_starting"}"""))
        assertEquals(BusyKind.Starting, starting.kind)
        assertEquals(WireTag("service_starting"), starting.tag)
        val server = RowServer { json("""{"code":"service_starting"}""", HttpStatusCode.ServiceUnavailable) }
        assertIs<Failure.Busy>(assertIs<Outcome.Failed>(server.gateway.session(SessionId("s"))).failure)
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-038 connection refusal maps to a network error`() = runTest {
        val server =
            RowServer(classify = { TransportError.ConnectFailed }) { throw IllegalStateException("connection refused") }
        val failure = assertIs<Failure.Network>(assertIs<Outcome.Failed>(server.gateway.session(SessionId("s"))).failure)
        assertEquals(NetworkWhy.Refused, failure.why)
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-021 an unsupported health reply is the redetection signal`() = runTest {
        for ((body, type) in
            listOf(
                """{"healthy":true}""" to "application/json",
                """{"healthy":true,"version":"1.19.0"}""" to "application/json",
                "<!doctype html><html>Server UI</html>" to "text/html",
            )) {
            val server = RowServer(dialect = null, credentials = ConnectionCredentials.None) {
                respond(body, HttpStatusCode.OK, io.ktor.http.headersOf("Content-Type", type))
            }
            val failure = assertIs<Outcome.Failed>(server.gateway.health()).failure
            assertEquals(Failure.Incompatible(IncompatibleKind.ProtocolMismatch), failure, body)
            server.gateway.close()
        }
    }

    @Test
    fun `B-PROTO-083 a server upgrade accepts only exact semantic versions`() {
        assertTrue(Oc2ServerVersion.isExact("2.0.29"))
        assertTrue(Oc2ServerVersion.isExact("2.0.29-beta.2+mobile"))
        assertFalse(Oc2ServerVersion.isExact("latest"))
        assertFalse(Oc2ServerVersion.isExact("2.0"))
        assertFalse(Oc2ServerVersion.isExact(" 2.0.29"))
        assertFalse(Oc2ServerVersion.isExact(""))
        assertFalse(Oc2ServerVersion.isExact("02.0.29"))
    }

    @Test
    fun `B-PROTO-060 a repository without message deletion stays a bounded error`() = runTest {
        val server = RowServer { error("message deletion must not reach the network") }
        assertFalse(server.gateway.capabilities.value.messageDelete)
        assertEquals(
            Failure.Unavailable(FeatureId("messageDelete")),
            assertIs<Outcome.Failed>(server.gateway.deleteMessage(SessionId("s"), MessageId("m"))).failure,
        )
        assertTrue(server.seen.isEmpty())
        server.gateway.close()
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondEmpty(status: HttpStatusCode) =
    respond("", status, io.ktor.http.headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"Secure Area\""))
