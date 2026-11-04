package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.WebSearchGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.core.model.WebSearchResponse
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2CoreProjection
import io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2EventAdapter
import io.github.eslamasabry.oc.protocol.oc2.events.Oc2Envelope
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Flutter test/web_search_test.dart, gateway and event halves. */
class Oc2WebSearchRowsTest {
    private val scoped = ServerLocation("/project", "workspace")

    @Test
    fun `B-PROTO-125 explicit search uses scoped Basic transport and no history mutation or cursor`() = runTest {
        val server = RowServer(location = scoped) { seen ->
            assertTrue(seen.request.headers[HttpHeaders.Authorization]!!.startsWith("Basic "))
            json(
                if (seen.method == "GET")
                    """{"location":{"directory":"/project","workspaceID":"workspace"},"data":[{"id":"one","name":"One"}]}"""
                else
                    """{"location":{"directory":"/project","workspaceID":"workspace"},"data":{"providerID":"one","results":[{"url":"https://example.com/doc","time":{}}]}}"""
            )
        }
        val g: WebSearchGateway = server.gateway
        assertTrue(server.gateway.capabilities.value.webSearch)
        assertEquals("one", assertIs<Outcome.Ok<ImmutableList<WebSearchProvider>>>(g.webSearchProviders()).value.single().id)
        val response = assertIs<Outcome.Ok<WebSearchResponse>>(g.searchWeb(" docs ", "one")).value
        assertEquals("https://example.com/doc", response.results.single().url)
        assertEquals(listOf("/api/websearch/provider", "/api/websearch"), server.seen.map { it.path })
        assertEquals(Json.parseToJsonElement("""{"query":"docs","providerID":"one"}""").jsonObject, server.seen.last().json)
        for (seen in server.seen)
            assertEquals(mapOf("location[directory]" to "/project", "location[workspace]" to "workspace"), seen.query)
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-125 provider and search reject another response location`() = runTest {
        for (path in listOf("provider", "search")) {
            val server = RowServer(location = ServerLocation("/project")) {
                json(
                    if (path == "provider") """{"location":{"directory":"/other"},"data":[{"id":"one","name":"One"}]}"""
                    else """{"location":{"directory":"/other"},"data":{"providerID":"one","results":[]}}"""
                )
            }
            val result = if (path == "provider") server.gateway.webSearchProviders() else server.gateway.searchWeb("docs", "one")
            assertIs<Failure.UnreadableReply>(assertIs<Outcome.Failed>(result).failure, path)
            server.gateway.close()
        }
    }

    @Test
    fun `B-PROTO-125 a 503 discards remote error copy and marks search unavailable`() = runTest {
        val server = RowServer {
            json("""{"_tag":"ServiceUnavailableError","message":"private remote body"}""", HttpStatusCode.ServiceUnavailable)
        }
        val failure = assertIs<Outcome.Failed>(server.gateway.searchWeb("docs", "one")).failure
        assertEquals(Failure.Unavailable(FeatureId("webSearch")), failure)
        assertFalse("private remote body" in failure.toString())
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-125 malformed provider catalog and wrong provider response fail closed`() = runTest {
        var catalog = true
        val server = RowServer(location = ServerLocation("/project")) {
            json(
                if (catalog) """{"location":{"directory":"/project"},"data":[{"id":"one","name":"One"},{"id":"one","name":"Duplicate"}]}"""
                else """{"location":{"directory":"/project"},"data":{"providerID":"silently-switched","results":[]}}"""
            )
        }
        assertIs<Outcome.Failed>(server.gateway.webSearchProviders())
        catalog = false
        assertIs<Outcome.Failed>(server.gateway.searchWeb("docs", "one"))
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-125 empty and excessive queries never reach the server`() = runTest {
        val server = RowServer { json("{}") }
        for (query in listOf("", "   ", "a".repeat(1001))) assertIs<Outcome.Failed>(server.gateway.searchWeb(query, "one"))
        assertTrue(server.seen.isEmpty())
        server.gateway.close()
    }

    @Test
    fun `B-PROTO-126 websearch updated becomes a payload-free freshness signal`() {
        val envelope =
            Oc2Envelope.parse(
                """{"id":"evt_search","created":1,"type":"websearch.updated","data":{"private":"ignored"},"location":{"directory":"/project"}}"""
            )
        val adapted = Oc2EventAdapter { 1L }.adapt(envelope)
        assertEquals(listOf<Any>(io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2Adapted.RefreshHint(
            io.github.eslamasabry.oc.protocol.oc2.adapter.Oc2HintKind.WebSearch)), adapted)
        val core = adapted.map(Oc2CoreProjection { io.github.eslamasabry.oc.core.common.Redacted.shape(null, null) }::toCore)
        // The domain event is the kind alone: no payload field exists to carry `private`.
        assertEquals(listOf<GatewayEvent>(GatewayEvent.RefreshHint(io.github.eslamasabry.oc.core.domain.events.HintKind("websearch"))), core)
    }
}
