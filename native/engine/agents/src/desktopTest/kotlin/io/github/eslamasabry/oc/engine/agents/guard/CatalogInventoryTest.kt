package io.github.eslamasabry.oc.engine.agents.guard

import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.scripts.freezeTipSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Port of "the inventory file is the live catalog" (test/coverage/phone_agent_catalog_coverage_test.dart): the native
 * catalog equals the Flutter inventory dump field for field. `limitation` and `resumeReason` are English copy in Dart
 * and words keys here, so they are left out; everything else is compared as the Dart dump writes it.
 */
class CatalogInventoryTest {
    private fun wire(name: String) = name.replaceFirstChar { it.lowercaseChar() }

    private fun artifact(a: AgentArtifact): JsonObject = JsonObject(
        buildMap {
            put("url", JsonPrimitive(a.url))
            put("sha256", JsonPrimitive(a.sha256))
            put("format", JsonPrimitive(wire(a.format.name)))
            a.archiveMember?.let { put("archiveMember", JsonPrimitive(it)) }
            a.downloadBytes?.let { put("downloadBytes", JsonPrimitive(it)) }
            a.installedBytes?.let { put("installedBytes", JsonPrimitive(it)) }
        },
    )

    private fun entry(d: AgentDescriptor): JsonObject = JsonObject(
        buildMap {
            put("id", JsonPrimitive(d.id.raw))
            put("name", JsonPrimitive(d.name))
            put("iconKey", JsonPrimitive(d.iconKey))
            put("route", JsonPrimitive(wire(d.route.name)))
            put("providerId", JsonPrimitive(d.providerId))
            put("signInMethod", JsonPrimitive(wire(d.signInMethod.name)))
            put("availability", JsonPrimitive(wire(d.availability.name)))
            d.unavailableReason?.let { put("unavailableReason", JsonPrimitive(wire(it.name))) }
            put(
                "capabilities",
                JsonObject(
                    mapOf(
                        "resumeVerified" to JsonPrimitive(d.capabilities.resumeVerified),
                        "modelList" to JsonPrimitive(d.capabilities.modelList),
                        "permissions" to JsonPrimitive(d.capabilities.permissions),
                        "images" to JsonPrimitive(d.capabilities.images),
                        "cancel" to JsonPrimitive(d.capabilities.cancel),
                    ),
                ),
            )
            d.recipe?.let { r ->
                put(
                    "recipe",
                    JsonObject(
                        mapOf(
                            "version" to JsonPrimitive(r.version),
                            "executable" to JsonPrimitive(r.executable),
                            "launchArgs" to JsonArray(r.launchArgs.map { JsonPrimitive(it) }),
                            "signInArgs" to JsonArray(r.signInArgs.map { JsonPrimitive(it) }),
                            "artifacts" to JsonObject(r.artifacts.entries.associate { (k, v) -> k.wire to artifact(v) }),
                        ),
                    ),
                )
            }
        },
    )

    /**
     * Pins that moved on native after the Flutter freeze, old text to new, applied to that agent's saved entry only:
     * OpenCode 2 2.2.5 -> 2.2.6 (93fcf4049; the values AgentPinsParityTest checks literally). Every other field still
     * has to equal the inventory byte for byte.
     */
    private val postFreezeMoves = mapOf(
        "opencode2" to listOf(
            "2.2.5" to "2.2.6",
            "f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053" to
                "22352e200630ec61a48a65b9f5c2b627328ae5002658d81c067f67c5fb689898",
            "873856acbaa71c5ed8df6433e312dfa0c82a86e2cddfb9d80f0a31123a917a08" to
                "00d8b036689fc35927d8f20f57000d5c091fe7a5a3345fef5326f330bfe6d0ce",
        ),
    )

    @Test
    fun `B-BUILD-006 the phone agent catalog inventory file is the live catalog`() {
        // The freeze tip's inventory: since the master merge test/ holds the released 2.1.5 app's older one.
        val saved = Json.parseToJsonElement(
            freezeTipSource("test/fixtures/coverage/phone_agent_catalog_source.json"),
        ).jsonObject.getValue("agents").jsonArray.map { e ->
            val id = e.jsonObject.getValue("id").jsonPrimitive.content
            val moved = postFreezeMoves[id].orEmpty().fold(e.toString()) { text, (old, new) ->
                check(old in text) { "$id no longer holds $old" }
                text.replace(old, new)
            }
            JsonObject(Json.parseToJsonElement(moved).jsonObject.filterKeys { it != "limitation" && it != "resumeReason" })
        }
        val live: List<JsonElement> = AgentCatalog.builtIn.agents.map { entry(it) }
        assertEquals(saved.size, live.size)
        for ((s, l) in saved.zip(live)) assertEquals(s, l, (s["id"] as JsonPrimitive).content)
    }
}
