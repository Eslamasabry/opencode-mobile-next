package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import io.github.eslamasabry.oc.engine.connectors.search.ConnectorSearch
import io.github.eslamasabry.oc.engine.connectors.search.ConnectorSearchAnswer
import io.github.eslamasabry.oc.engine.connectors.text.Scalars
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectorSearchTest {
    private fun search(
        arguments: JsonElement?,
        entries: Iterable<RegistryEntry>?,
        connected: Map<String, Boolean>? = null,
    ): ConnectorSearchAnswer = ConnectorSearch.search(arguments, entries, connected, redactor)

    private fun reply(
        arguments: JsonElement?,
        entries: Iterable<RegistryEntry>?,
        connected: Map<String, Boolean>? = null,
    ) =
        assertIs<ConnectorSearchAnswer.Reply>(search(arguments, entries, connected)).json

    private fun query(q: String, limit: Int? = null) =
        buildJsonObject {
            put("query", q)
            if (limit != null) put("limit", limit)
        }

    @Test
    fun `B-AGENT-137 an unloaded catalogue is distinct from a loaded catalogue with no match`() {
        assertEquals(
            json(
                """{"status":"catalogue_not_loaded",""" +
                    """"message":"The connector catalogue could not be loaded right now. """ +
                    """Try again in a moment.","matches":[]}""",
            ),
            reply(query("design"), null),
        )
        assertEquals(json("""{"status":"ok","matches":[]}"""), reply(query("design"), emptyList()))
    }

    @Test
    fun `B-AGENT-137 matches all case-insensitive tokens and returns canonical sorted ids`() {
        val entries =
            listOf(
                searchEntry("io.test/zeta", title = "Design references", description = "Mobile"),
                searchEntry("io.test/alpha", title = "Mobile design", description = "References"),
                searchEntry("io.test/unrelated", title = "Design desktop"),
                searchEntry("invalid", title = "Mobile design"),
            )
        val result = reply(query("  DESIGN mobile  "), entries)
        assertEquals("ok", result["status"].str())
        assertEquals(listOf("io.test/alpha", "io.test/zeta"), result.matches().map { it["catalogId"].str() })
        assertEquals("Mobile design", result.matches().first()["name"].str())
        assertTrue(result.matches().all { it["connected"] == JsonNull })
    }

    @Test
    fun `B-AGENT-137 projects catalogue runtimes and never infers OAuth from keys`() {
        val entries =
            listOf(
                searchEntry("io.test/hosted", runtime = "hosted", needsKey = true),
                searchEntry("io.test/node", runtime = "npm"),
                searchEntry("io.test/python", runtime = "pypi"),
                searchEntry("io.test/container", runtime = "oci"),
                searchEntry("io.test/none"),
            )
        val inventory =
            entries.associate { McpCatalogItem.from(it).serverName to false } +
                mapOf("hosted" to true, "unrelated" to true)
        val rows = reply(query("io.test", 10), entries, inventory).matches().associateBy { it["catalogId"].str() }
        assertEquals("hosted", rows["io.test/hosted"]!!["runtime"].str())
        assertEquals("npx", rows["io.test/node"]!!["runtime"].str())
        assertEquals("uvx", rows["io.test/python"]!!["runtime"].str())
        assertEquals("docker", rows["io.test/container"]!!["runtime"].str())
        assertEquals("unavailable", rows["io.test/none"]!!["runtime"].str())
        assertTrue(rows.values.all { it["needsSignIn"].str() == "unknown" })
        assertEquals(JsonPrimitive(true), rows["io.test/hosted"]!!["connected"])
        assertEquals(JsonPrimitive(false), rows["io.test/node"]!!["connected"])
        assertEquals(true, inventory["unrelated"])
    }

    @Test
    fun `B-AGENT-137 default and requested limits apply after matching and deduplication`() {
        val entries =
            listOf(searchEntry("io.test/item0"), searchEntry("io.test/item0", version = "2.0.0")) +
                (1 until 12).map { searchEntry("io.test/item$it") }
        assertEquals(5, reply(query("io.test"), entries).matches().size)
        val rows = reply(query("io.test", 10), entries).matches()
        assertEquals(10, rows.size)
        assertEquals(10, rows.map { it["catalogId"].str() }.toSet().size)
    }

    @Test
    fun `B-AGENT-137 search inspects at most 100 records including nonmatching entries`() {
        var reads = 0
        val entries =
            Iterable {
                iterator {
                    for (i in 0 until 100) {
                        reads++
                        yield(searchEntry("io.test/miss$i"))
                    }
                    error("Search scanned beyond its input budget")
                }
            }
        val result = reply(query("target"), entries)
        assertTrue(result.matches().isEmpty())
        assertEquals(100, reads)
    }

    @Test
    fun `B-AGENT-137 prose is single-line, bounded by scalars, redacted and url-free`() {
        val result =
            reply(
                query("safe"),
                listOf(
                    searchEntry(
                        "io.test/safe",
                        title = "Safe https://title.example.test/path",
                        description =
                            "token=private-value\nhttps://secret.example.test/private www.example.test/page " +
                                "🎨".repeat(350),
                    ),
                ),
                connected = emptyMap(),
            )
        val encoded = result.toString()
        assertFalse("private-value" in encoded)
        assertFalse("secret.example.test" in encoded)
        assertFalse("title.example.test" in encoded)
        assertFalse("www.example.test" in encoded)
        val description = result.matches().single()["description"].str()!!
        assertTrue(Scalars.count(description) <= 300)
        assertFalse('\n' in description)
        assertEquals(JsonPrimitive(false), result.matches().single()["connected"])
    }

    @Test
    fun `B-AGENT-137 invalid requests fail with fixed copy and never echo input`() {
        val invalid: List<JsonElement?> =
            listOf(
                null,
                JsonNull,
                JsonArray(emptyList()),
                JsonObject(emptyMap()),
                query(""),
                query("   "),
                json("""{"query":1}"""),
                query("🎨".repeat(101)),
                query("safe", 0),
                query("safe", 11),
                json("""{"query":"safe","limit":1.0}"""),
                json("""{"query":"safe","extra":"private-value"}"""),
                query("token=private-value"),
                query("https://private.example.test/path"),
            )
        for (arguments in invalid) {
            assertEquals(ConnectorSearchAnswer.Invalid, search(arguments, null), "$arguments")
        }
        assertEquals("Invalid connector search request.", ConnectorSearch.INVALID_REQUEST_MESSAGE)
    }

    @Test
    fun `B-AGENT-137 100 unicode scalars are accepted regardless of utf-16 length`() {
        assertEquals(json("""{"status":"ok","matches":[]}"""), reply(query("🎨".repeat(100), 1), emptyList()))
    }

    @Test
    fun `B-AGENT-137 search covers description text beyond the returned prose limit`() {
        val long = searchEntry("io.test/long", description = "x".repeat(320) + " needle")
        val result = reply(query("needle"), listOf(long))
        val row = result.matches().single()
        assertEquals("io.test/long", row["catalogId"].str())
        assertEquals(300, Scalars.count(row["description"].str()!!))
    }
}
