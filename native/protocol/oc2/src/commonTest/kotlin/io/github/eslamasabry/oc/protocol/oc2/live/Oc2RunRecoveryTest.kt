package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*

class Oc2RunRecoveryTest {
    @Test fun `B-435 lost OC2 end is recovered by one active read and history refetch before idle`() = runTest {
        val requests = mutableListOf<String>()
        val sid = SessionId("session")
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests += path
            respond(when {
                path.endsWith("/active") -> "{}"
                path.endsWith("/message") -> "[]"
                else -> error("unexpected read")
            }, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val g = Oc2Gateway(NetClient(HttpClient(engine), { TransportError.Other }), ServerOrigin("https", "example.test", 443),
            ConnectionCredentials.None, io.github.eslamasabry.oc.core.testing.TestClock(), Redactor(), initialDialect = Oc2Dialect.Stable)
        val events = mutableListOf<GatewayEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { g.events.all.collect { events += it.event } }
        assertIs<Outcome.Ok<*>>(g.reconcileRuns(setOf(sid)))
        assertEquals(listOf("/api/session/active", "/api/session/session/message"), requests)
        val ended = assertIs<GatewayEvent.RunEnded>(events.single())
        assertEquals(sid, ended.sessionId)
        assertEquals(io.github.eslamasabry.oc.core.domain.events.RunOutcome.Idle, ended.outcome)
        assertEquals(emptyList(), ended.reconciledHistory)
        g.close()
    }
    @Test fun `B-435 failed OC2 status never refetches or invents idle`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.ServiceUnavailable) }
        val g = Oc2Gateway(NetClient(HttpClient(engine), { TransportError.Other }), ServerOrigin("https", "example.test", 443),
            ConnectionCredentials.None, io.github.eslamasabry.oc.core.testing.TestClock(), Redactor(), initialDialect = Oc2Dialect.Stable)
        assertIs<Outcome.Failed>(g.reconcileRuns(setOf(SessionId("session"))))
        g.close()
    }
    @Test fun `B-435 recovery advances past a page hidden by staged undo`() = runTest {
        val sid = SessionId("session")
        var pages = 0
        val engine = MockEngine { request ->
            val body = when {
                request.url.encodedPath.endsWith("/active") -> "{}"
                request.url.encodedPath.endsWith("/message") -> {
                    pages++
                    if (request.url.parameters["cursor"] == "older")
                        """{"data":[{"id":"msg_a","type":"user","text":"visible","time":{"created":1}}]}"""
                    else """{"data":[""" + (1..100).joinToString(",") {
                        """{"id":"msg_z$it","type":"user","text":"undone","time":{"created":2}}"""
                    } + """],"cursor":{"next":"older"}}"""
                }
                else -> """{"data":{"id":"session","revert":{"messageID":"msg_m"}}}"""
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val g = Oc2Gateway(NetClient(HttpClient(engine), { TransportError.Other }), ServerOrigin("https", "example.test", 443),
            ConnectionCredentials.None, io.github.eslamasabry.oc.core.testing.TestClock(), Redactor(), initialDialect = Oc2Dialect.Stable)
        val ends = mutableListOf<GatewayEvent.RunEnded>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { g.events.all.collect { (it.event as? GatewayEvent.RunEnded)?.let(ends::add) } }
        assertIs<Outcome.Ok<*>>(g.reconcileRuns(setOf(sid)))
        assertEquals(2, pages, "recovery uses the ordinary staged-boundary history path")
        assertEquals(listOf("msg_a"), ends.single().reconciledHistory?.map { it.info.id })
        g.close()
    }

}
