package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal val redactor = Redactor()

internal fun json(text: String): JsonElement = Json.parseToJsonElement(text)

internal fun entryOf(listing: JsonElement): RegistryEntry = RegistryEntry.fromJson(listing, redactor)!!

internal fun entryOf(text: String): RegistryEntry = entryOf(json(text))

/** The connector-search test fixture (Dart `_entry`). */
internal fun searchEntry(
    id: String,
    title: String = "",
    description: String = "",
    runtime: String? = null,
    version: String = "1.0.0",
    needsKey: Boolean = false,
): RegistryEntry =
    entryOf(
        buildJsonObject {
            put("name", id)
            put("version", version)
            put("title", title)
            put("description", description)
            if (runtime == "hosted") {
                put(
                    "remotes",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "streamable-http")
                                put("url", "https://connector.example.test/mcp")
                                if (needsKey) {
                                    put(
                                        "headers",
                                        buildJsonArray {
                                            add(json("""{"name":"Authorization","isRequired":true,"isSecret":true}"""))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
            if (runtime != null && runtime != "hosted") {
                put(
                    "packages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("registryType", runtime)
                                put("identifier", "test-package")
                                put("version", version)
                            },
                        )
                    },
                )
            }
        },
    )

/** A hosted listing with no headers (Dart `_item` in the chat tests). */
internal fun hostedItem(name: String = "com.example/design", key: Boolean = false): McpCatalogItem =
    McpCatalogItem.from(
        entryOf(
            """
            {"name":"$name","version":"1.0.0","remotes":[{"type":"streamable-http","url":"https://example.com/mcp"
            ${if (key) ""","headers":[{"name":"X-Api-Key","isRequired":true,"isSecret":true}]""" else ""}}]}
            """.trimIndent(),
        ),
    )

internal fun JsonObject.matches(): List<JsonObject> =
    (this["matches"] as kotlinx.serialization.json.JsonArray).map { it as JsonObject }

internal fun JsonElement?.str(): String? =
    if (this is kotlinx.serialization.json.JsonNull) {
        null
    } else {
        (this as? kotlinx.serialization.json.JsonPrimitive)?.content
    }
