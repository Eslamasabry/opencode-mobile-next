package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectorPluginTest {
    // ---- B-PROTO-049 (test/connector_search_step_test.dart:60)

    @Test
    fun `B-PROTO-049 catalogue_off is a status of its own`() {
        val result = parseConnectorSearchResult(text = """{"status":"catalogue_off","message":"x","matches":[]}""")
        assertEquals(ConnectorSearchStatus.CatalogueOff, result?.status)
        assertTrue(result!!.matches.isEmpty())
    }

    @Test
    fun `B-PROTO-049 every helper status is distinct and anything else is not a search result`() {
        assertEquals(ConnectorSearchStatus.CatalogueNotLoaded, parseConnectorSearchResult(text = """{"status":"catalogue_not_loaded"}""")?.status)
        assertEquals(ConnectorSearchStatus.Unavailable, parseConnectorSearchResult(text = """{"status":"unavailable"}""")?.status)
        assertNull(parseConnectorSearchResult(text = """{"status":"refused"}"""))
        assertNull(parseConnectorSearchResult(text = "not json"))
        assertNull(parseConnectorSearchResult(text = ""))
        val ok =
            parseConnectorSearchResult(
                value = json("""{"status":"ok","matches":[{"name":"  GitHub ","runtime":"hosted","connected":true},{"catalogId":"io.x/y"},{"name":""},5]}"""),
            )!!
        assertEquals(listOf(ConnectorSearchMatch("GitHub", "hosted", true), ConnectorSearchMatch("io.x/y", "unavailable", null)), ok.matches)
    }

    // ---- B-PROTO-078 (test/plugin_inventory_test.dart:42, :81)

    @Test
    fun `B-PROTO-078 plugin metadata keeps declared source and never exposes raw diagnostics`() {
        val plugin =
            mapPluginInfo(
                json(
                    """{"id":"reviewer","status":"failed","tui":true,"source":{"type":"package","package":"@example/reviewer@1.2.3"},
                    "error":"token=synthetic-secret","options":{"apiKey":"synthetic-secret"}}""",
                ) as JsonObject,
            )!!
        assertEquals("reviewer", plugin.id)
        assertEquals(PluginStatus.Failed, plugin.status)
        assertEquals(PluginSourceKind.Package, plugin.source)
        assertEquals("@example/reviewer@1.2.3", plugin.packageName)
        assertTrue(plugin.terminalUi)
        assertFalse(plugin.toString().contains("synthetic-secret"))
        val unsafe =
            mapPluginInfo(
                json(
                    """{"id":"https://user:synthetic-secret@example.com","status":"failed","tui":false,
                    "source":{"type":"package","package":"https://user:synthetic-secret@example.com/plugin?token=secret"},"error":"synthetic-secret"}""",
                ) as JsonObject,
            )!!
        assertNull(unsafe.id)
        assertNull(unsafe.packageName)
        assertFalse(unsafe.toString().contains("synthetic-secret"))
        val local = mapPluginInfo(json("""{"status":"failed","tui":false,"source":{"type":"local","path":"/private/credentials/plugin.js"}}""") as JsonObject)!!
        assertEquals(PluginSourceKind.Local, local.source)
        assertFalse(local.toString().contains("/private"))
    }

    @Test
    fun `B-PROTO-078 stable rows nest status and tui, an absent feature is false`() {
        val stable = mapPluginInfo(json("""{"id":"x","source":{"type":"sdk"},"state":{"status":"active"},"features":{"server":true}}""") as JsonObject)!!
        assertEquals(PluginStatus.Active, stable.status)
        assertEquals(PluginSourceKind.Sdk, stable.source)
        assertFalse(stable.terminalUi)
    }

    @Test
    fun `B-PROTO-078 plugin GET pins location and preserves failure and unknown metadata`() =
        runTest {
            val transport =
                FakeTransport(
                    payload =
                        """{"data":[{"id":"builtin","source":{"type":"builtin"},"status":"active","tui":false},
                        {"source":{"type":"local","path":"/private/plugin.js"},"status":"failed","error":"synthetic-secret","tui":true},
                        {"id":"future","source":{"type":"future"},"status":"paused","tui":false}]}""",
                )
            transport.gate = CompletableDeferred()
            var location = CatalogLocation("/first", "wrk_one")
            val reader = PluginInventoryReader(transport) { location }
            val pending = async { reader.listPlugins() }
            yield()
            location = CatalogLocation("/second", "wrk_two")
            transport.gate!!.complete(Unit)
            val plugins = assertIs<Outcome.Ok<List<PluginInfo>>>(pending.await()).value
            val request = transport.requests.single()
            assertEquals("GET", request.method)
            assertEquals("/plugin", request.path)
            assertEquals(mapOf("location[directory]" to "/first", "location[workspace]" to "wrk_one"), request.query)
            assertNull(plugins[1].id)
            assertEquals(PluginStatus.Failed, plugins[1].status)
            assertEquals(PluginStatus.Unknown, plugins[2].status)
            assertEquals(PluginSourceKind.Unknown, plugins[2].source)
        }

    // ---- B-PROTO-079 (test/plugin_inventory_test.dart:136, :185)

    @Test
    fun `B-PROTO-079 invalid inventories and server failures never become empty success or raw errors`() =
        runTest {
            val transport = FakeTransport()
            val reader = PluginInventoryReader(transport) { CatalogLocation(null, null) }
            for (payload in listOf("""{"unexpected":[]}""", """{"data":[null]}""", """{"data":[{"status":"active"}]}""")) {
                transport.payload = payload
                val failed = assertIs<Outcome.Failed>(reader.listPlugins())
                assertIs<Failure.UnreadableReply>(failed.failure)
                assertNull(failed.failure.details)
            }
            transport.status = 500
            transport.payload = """{"message":"synthetic-secret"}"""
            val failed = assertIs<Outcome.Failed>(reader.listPlugins())
            assertEquals(Failure.Server(500), failed.failure)
            assertFalse(failed.toString().contains("synthetic-secret"))
        }

    @Test
    fun `B-PROTO-079 plugin events only forward an inventory refresh hint`() {
        for (type in listOf("plugin.added", "plugin.updated")) {
            val hint = assertNotNull(pluginEventHint(type, json("""{"id":"plugin","error":"synthetic-secret"}""")))
            assertEquals(type, hint.type)
            assertTrue(hint.properties.isEmpty())
        }
        assertNull(pluginEventHint("session.updated", null))
    }
}
