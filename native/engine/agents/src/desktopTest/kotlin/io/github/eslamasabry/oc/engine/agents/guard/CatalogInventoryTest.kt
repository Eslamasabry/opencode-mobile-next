package io.github.eslamasabry.oc.engine.agents.guard

import io.github.eslamasabry.oc.engine.agents.catalog.AgentArtifact
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.scripts.repoRoot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
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

    @Test
    fun `B-BUILD-006 the phone agent catalog inventory file is the live catalog`() {
        val saved = Json.parseToJsonElement(
            File(repoRoot, "test/fixtures/coverage/phone_agent_catalog_source.json").readText(),
        ).jsonObject.getValue("agents").jsonArray.map { e ->
            JsonObject(e.jsonObject.filterKeys { it != "limitation" && it != "resumeReason" })
        }
        val live: List<JsonElement> = AgentCatalog.builtIn.agents.map { entry(it) }
        assertEquals(saved.size, live.size)
        for ((s, l) in saved.zip(live)) assertEquals(s, l, (s["id"] as JsonPrimitive).content)
    }
}
