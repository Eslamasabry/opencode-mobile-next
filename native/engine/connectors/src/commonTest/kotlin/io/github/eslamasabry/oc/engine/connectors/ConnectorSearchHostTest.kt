package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.engine.connectors.bridge.BridgeReply
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueCache
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStore
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryClient
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import io.github.eslamasabry.oc.engine.connectors.search.ConnectorSearchEnvironment
import io.github.eslamasabry.oc.engine.connectors.search.ConnectorSearchHost
import io.github.eslamasabry.oc.engine.connectors.search.ConnectorSearchPublisher
import io.github.eslamasabry.oc.engine.connectors.search.ConnectorSearchService
import io.github.eslamasabry.oc.engine.connectors.search.SearchAgent
import io.github.eslamasabry.oc.engine.connectors.search.SearchContext
import io.github.eslamasabry.oc.engine.connectors.search.enableConnectorCatalogue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HOUR = 60 * 60 * 1000L

internal class MemoryCache : CatalogueCache {
    val values = HashMap<String, String>()

    override fun read(profileId: String): String? = values[CatalogueCache.key(profileId)]

    override suspend fun write(profileId: String, value: String): Outcome<Unit> {
        values[CatalogueCache.key(profileId)] = value
        return Unit.ok()
    }

    override suspend fun remove(profileId: String): Outcome<Unit> {
        values.remove(CatalogueCache.key(profileId))
        return Unit.ok()
    }
}

/** The public registry, offline (Dart `_Registry`): the default list is Design, a search answers Designer. */
internal class FakeRegistry : RegistryClient {
    val requests = ArrayList<String?>()
    var fail = false
    var searchHits = false
    var delayMs = 0L
    var started: CompletableDeferred<Unit>? = null
    var hold: CompletableDeferred<Unit>? = null

    private fun server(name: String, title: String) =
        entryOf(
            """{"name":"$name","version":"1","title":"$title","description":"$title reference",
               "remotes":[{"type":"streamable-http","url":"https://example.com/mcp"}]}""",
        )

    override suspend fun fetch(search: String?): Outcome<List<RegistryEntry>> {
        requests += search
        started?.complete(Unit)
        hold?.await()
        if (delayMs > 0) delay(delayMs)
        if (fail) return Failure.Network().failed()
        return when {
            search == null -> listOf(server("com.example/design", "Design"))
            searchHits -> listOf(server("org.example/designer", "Designer"))
            else -> emptyList()
        }.ok()
    }
}

private val CLAUDE = SearchAgent("claude", usesServerInventory = false)
private val OPENCODE = SearchAgent("opencode2", usesServerInventory = true)

private class Connection(var agents: List<SearchAgent>) : ConnectorSearchEnvironment {
    var ctx: SearchContext? =
        SearchContext(
            profileId = "phone",
            baseUrl = "http://127.0.0.1:4097",
            username = null,
            generation = 1,
            transport = 1,
            activeProfileId = "phone",
            directory = "/work/project",
            workspace = null,
            locationRevision = 0,
            connected = true,
            cardsEnabled = true,
            deleting = false,
            serverCatalog = true,
            agents = agents,
        )
    val repository = FakeMcp()

    override fun context(): SearchContext? = ctx

    override suspend fun operations(): McpGateway = repository

    fun change(block: SearchContext.() -> SearchContext) {
        ctx = ctx!!.block()
    }
}

private class Publisher : ConnectorSearchPublisher {
    var calls = 0
    var succeeds = true
    var endpoint: String? = null
    var bearer: String? = null
    var agent: String? = null

    override suspend fun publish(profileId: String, agentId: String, endpoint: String, bearer: String): Boolean {
        calls++
        check(profileId == "phone")
        agent = agentId
        this.endpoint = endpoint
        this.bearer = bearer
        return succeeds
    }
}

class ConnectorSearchHostTest {
    private val cache = MemoryCache()
    private val registry = FakeRegistry()
    private val socket = FakeLoopback()
    private val publisher = Publisher()
    private var now = Instant.parse("2026-10-09T12:00:00Z").toEpochMilliseconds()

    private inner class H(scope: TestScope, agent: SearchAgent) {
        val connection = Connection(listOf(agent))
        val service = ConnectorSearchService(connection, cache, registry, { now }, redactor)
        val host =
            ConnectorSearchHost(connection, service, socket, secureRandom, publisher, scope.backgroundScope) { now }

        /** POSTs like the helper: `{"arguments":{"query":…},"directory":…}`. Null: connection refused. */
        suspend fun search(directory: String = "/work/project", query: String = "design"): BridgeReply? {
            val body = buildJsonObject {
                put("arguments", buildJsonObject { put("query", query) })
                put("directory", directory)
            }
            return socket.send(bridgeRequest(publisher.bearer, body.toString()))
        }
    }

    private suspend fun TestScope.harness(agent: SearchAgent = CLAUDE): H = H(this, agent).also { it.host.sync() }

    private fun saveCache(ageMs: Long = HOUR, optedIn: Boolean = true) {
        cache.values[CatalogueCache.key("phone")] =
            """{"version":1,"optedIn":$optedIn,"cachedAt":"${Instant.fromEpochMilliseconds(now - ageMs)}",
               "entries":[{"name":"com.example/design","version":"1","title":"Design","description":"Design reference",
               "remotes":[{"type":"streamable-http","url":"https://example.com/mcp"}]}]}"""
    }

    private fun BridgeReply.json(): JsonObject = json(bodyText) as JsonObject

    @Test
    fun `B-AGENT-132 Claude search uses the cached catalogue without borrowing OpenCode inventory`() = runTest {
        val h = harness()
        saveCache()
        h.connection.repository.inventory = listOf(McpServerInfo("design", "connected", null))
        val reply = h.search()!!
        assertEquals(200, reply.status)
        val match = reply.json().matches().single()
        assertEquals("com.example/design", match["catalogId"].str())
        assertNull(match["connected"].str())
        assertEquals("unknown", match["needsSignIn"].str())
        assertEquals("claude", publisher.agent)
        assertEquals(0, h.connection.repository.lists + h.connection.repository.adds)
    }

    @Test
    fun `B-AGENT-132 agent search never applies another project inventory`() = runTest {
        val h = harness(OPENCODE)
        saveCache()
        val reply = h.search(directory = "/different/project")!!
        assertEquals(200, reply.status)
        assertNull(reply.json().matches().single()["connected"].str())
        assertEquals(0, h.connection.repository.lists)
        // The exact project of a server-side agent does read its list.
        assertEquals("false", h.search()!!.json().matches().single()["connected"].str())
        assertEquals(1, h.connection.repository.lists)
    }

    @Test
    fun `B-AGENT-132 agent search reports a missing cache without an inventory request`() = runTest {
        val h = harness()
        assertEquals("catalogue_off", h.search()!!.json()["status"].str())
        assertEquals(0, h.connection.repository.lists)
    }

    @Test
    fun `B-AGENT-132 catalogue off gives the fixed message and makes no registry request`() = runTest {
        val h = harness()
        saveCache(optedIn = false)
        val reply = h.search()!!
        assertEquals(200, reply.status)
        assertEquals(
            json(
                """{"status":"catalogue_off",""" +
                    """"message":"The connector catalogue is off. Ask the person to tap Turn on """ +
                    """in this step, then search again.","matches":[]}""",
            ),
            reply.json(),
        )
        assertTrue(registry.requests.isEmpty())
    }

    @Test
    fun `B-AGENT-132 opted in with a stale list refreshes it first and merges the registry's own answer`() = runTest {
        val h = harness()
        saveCache(ageMs = 30 * HOUR)
        registry.searchHits = true
        val reply = h.search()!!
        assertEquals(200, reply.status)
        assertEquals(
            setOf("com.example/design", "org.example/designer"),
            reply.json().matches().map { it["catalogId"].str() }.toSet(),
        )
        assertEquals(2, registry.requests.size)
        assertEquals(1, registry.requests.count { it == null })
        val saved = json(cache.read("phone")!!) as JsonObject
        val cachedAt = CatalogueStore.parseInstantMs(saved["cachedAt"].str()!!)!!
        assertTrue(cachedAt > now - 5 * 60_000)
        // Search answers are never saved as the default list.
        val names = (saved["entries"] as JsonArray).map { (it as JsonObject)["name"].str() }
        assertEquals(listOf("com.example/design"), names)
    }

    @Test
    fun `B-AGENT-132 a fresh list is not refreshed, only the query is asked`() = runTest {
        val h = harness()
        saveCache()
        h.search()
        assertEquals(listOf<String?>("design"), registry.requests)
    }

    @Test
    fun `B-AGENT-132 a registry failure falls back to the saved list`() = runTest {
        val h = harness()
        saveCache(ageMs = 30 * HOUR)
        registry.fail = true
        val reply = h.search()!!
        assertEquals(200, reply.status)
        assertEquals("ok", reply.json()["status"].str())
        assertEquals("com.example/design", reply.json().matches().single()["catalogId"].str())
        assertFalse("private upstream" in reply.bodyText)
    }

    @Test
    fun `B-AGENT-132 opted in with nothing saved and no network says not loaded`() = runTest {
        val h = harness()
        cache.values[CatalogueCache.key("phone")] = """{"version":1,"optedIn":true,"entries":[]}"""
        registry.fail = true
        val body = h.search()!!.json()
        assertEquals("catalogue_not_loaded", body["status"].str())
        assertFalse("Tools > MCP" in body["message"].str()!!)
    }

    @Test
    fun `B-AGENT-132 a slow registry cannot hold the search past its budget`() = runTest {
        val h = harness()
        saveCache(ageMs = 30 * HOUR)
        registry.delayMs = 8_000
        val start = currentTime
        val reply = h.search()!!
        assertEquals(200, reply.status)
        assertTrue(currentTime - start < 5_000)
        assertEquals("com.example/design", reply.json().matches().single()["catalogId"].str())
    }

    @Test
    fun `B-AGENT-132 search observes cache changes without a helper restart`() = runTest {
        val h = harness()
        assertEquals("catalogue_off", h.search()!!.json()["status"].str())
        saveCache()
        assertEquals("ok", h.search()!!.json()["status"].str())
        cache.values.clear()
        assertEquals("catalogue_off", h.search()!!.json()["status"].str())
    }

    @Test
    fun `B-AGENT-133 turning the catalogue on saves consent and loads the list`() = runTest {
        assertTrue(enableConnectorCatalogue("phone", cache, registry, { now }, redactor))
        val saved = json(cache.read("phone")!!) as JsonObject
        assertEquals("true", saved["optedIn"].str())
        assertTrue((saved["entries"] as JsonArray).isNotEmpty())
    }

    @Test
    fun `B-AGENT-133 agent search rejects source changes during a registry read`() = runTest {
        val h = harness()
        saveCache()
        registry.started = CompletableDeferred()
        registry.hold = CompletableDeferred()
        val request = async { h.search() }
        registry.started!!.await()
        h.connection.change { copy(directory = "/changed") }
        registry.hold!!.complete(Unit)
        assertEquals(403, request.await()!!.status)
    }

    @Test
    fun `B-AGENT-133 the old search endpoint cannot serve after cards are disabled`() = runTest {
        val h = harness()
        saveCache()
        assertEquals(200, h.search()!!.status)
        h.connection.change { copy(cardsEnabled = false) }
        h.host.sync()
        assertNull(h.search(), "connection refused")
        assertTrue(h.host.openBridges.isEmpty())
    }

    @Test
    fun `B-AGENT-133 failed descriptor publishing can retry after the cooldown`() = runTest {
        publisher.succeeds = false
        val h = harness()
        assertEquals(1, publisher.calls)
        assertNull(h.search(), "the unpublished bridge was closed")
        h.host.sync()
        assertEquals(1, publisher.calls, "no retry inside the cooldown")
        now += 6_000
        publisher.succeeds = true
        h.host.sync()
        assertEquals(2, publisher.calls)
        assertEquals(200, h.search()!!.status)
    }
}
