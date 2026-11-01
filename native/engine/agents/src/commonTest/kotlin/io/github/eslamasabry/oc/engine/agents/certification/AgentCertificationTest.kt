package io.github.eslamasabry.oc.engine.agents.certification

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCapabilities
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentInstallRecipe
import io.github.eslamasabry.oc.engine.agents.catalog.AgentLimitation
import io.github.eslamasabry.oc.engine.agents.catalog.AgentUnavailableReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of test/agent_certification_test.dart (B-AGENT-009, 010, 011). */
class AgentCertificationTest {
    private val catalog = AgentCatalog.builtIn
    private val claude = catalog.byId(AgentId("claude"))!!
    private val proofCells = listOf("resume", "models", "permission", "images", "abort")

    /** Kotlin maps/lists/scalars to JSON, so rows read like the Dart test's literals. */
    private fun json(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k as String to json(v) })
        is List<*> -> JsonArray(value.map { json(it) })
        else -> error("unsupported")
    }

    private fun record(id: String = "claude"): MutableMap<String, Any?> = mutableMapOf(
        "id" to id,
        "agentVersion" to catalog.byId(AgentId(id))?.recipe?.version,
        "helperVersion" to "0.9.2",
        "architecture" to "arm64",
        "cells" to proofCells.associateWith { mapOf("state" to "pass", "evidence" to "docs/qa/example/README.md#proof") }
            .toMutableMap(),
    )

    private fun matrix(rows: Any?) = AgentCertification.fromJson(json(mapOf("agents" to rows)))

    private fun project(
        rows: List<Any?>,
        descriptor: AgentDescriptor = claude,
        architecture: AgentArchitecture? = AgentArchitecture.Arm64,
        helperVersion: String? = "0.9.2",
    ): AgentCapabilities = matrix(rows).capabilitiesFor(descriptor, architecture, helperVersion)

    private fun flags(c: AgentCapabilities) = listOf(c.resumeVerified, c.modelList, c.permissions, c.images, c.cancel)

    private fun assertClosed(c: AgentCapabilities, message: String? = null) = assertEquals(AgentCapabilities.None, c, message)

    private fun assertOpen(c: AgentCapabilities, message: String? = null) = assertTrue(flags(c).all { it }, message)

    @Test
    fun `B-AGENT-009 the bundled snapshot parses and is the reviewed JSON`() {
        val root = Json.parseToJsonElement(AGENT_CERTIFICATION_JSON).jsonObject
        assertTrue("agents" in root)
        assertTrue("rule" in root)
    }

    @Test
    fun `B-AGENT-009 bundled Claude evidence qualifies arm64 and x64 with exact helper`() {
        assertEquals("2.1.316", claude.recipe!!.version)
        for (arch in AgentArchitecture.entries) {
            assertOpen(AgentCertification.bundled.capabilitiesFor(claude, arch, "0.9.2"), arch.wire)
        }
        for (helper in listOf(null, "0.9.3")) {
            assertClosed(AgentCertification.bundled.capabilitiesFor(claude, AgentArchitecture.Arm64, helper))
        }
    }

    @Test
    fun `B-AGENT-009 evidence carries across the tested Paseo and Claude Code versions`() {
        assertOpen(AgentCertification.bundled.capabilitiesFor(claude, AgentArchitecture.Arm64, "0.11.2"))
        assertTrue(AgentCertification.bundled.certifiedForChat(AgentId("claude")))
        assertClosed(AgentCertification.bundled.capabilitiesFor(claude, AgentArchitecture.Arm64, "0.11.3"))
    }

    @Test
    fun `B-AGENT-009 bundled other agents remain unverified on arm64 and x64`() {
        for (descriptor in catalog.agents) {
            if (descriptor.id == AgentId.CLAUDE) continue
            for (arch in AgentArchitecture.entries) {
                assertClosed(AgentCertification.bundled.capabilitiesFor(descriptor, arch, "0.9.2"), descriptor.id.raw)
            }
        }
    }

    @Test
    fun `B-AGENT-009 each exact pass cell grants only its corresponding capability`() {
        for ((index, name) in proofCells.withIndex()) {
            val row = record()
            @Suppress("UNCHECKED_CAST")
            (row["cells"] as MutableMap<String, Any?>).keys.retainAll(setOf(name))
            assertEquals(List(proofCells.size) { it == index }, flags(project(listOf(row))), name)
        }
        assertOpen(project(listOf(record())))
        val installed = record()
        installed["cells"] = listOf("install", "version", "smoke", "cards")
            .associateWith { mapOf("state" to "pass", "evidence" to "docs/qa/example/README.md") }
        assertClosed(project(listOf(installed)))
    }

    @Test
    fun `B-AGENT-009 omitted architecture qualifies protocol capabilities across CPUs`() {
        val row = record().apply { remove("architecture") }
        for (arch in AgentArchitecture.entries + listOf(null)) assertOpen(project(listOf(row), architecture = arch))
        assertClosed(project(listOf(row), helperVersion = null))
        assertClosed(project(listOf(row), helperVersion = "0.9.3"))
        for (field in listOf("agentVersion", "helperVersion")) {
            assertClosed(project(listOf(row.toMutableMap().apply { remove(field) })))
            assertClosed(project(listOf(row.toMutableMap().apply { put(field, "different-version") })))
        }
    }

    @Test
    fun `B-AGENT-009 present architecture scopes evidence to that CPU`() {
        for (certified in AgentArchitecture.entries) {
            val row = record().apply { put("architecture", certified.wire) }
            for (observed in AgentArchitecture.entries + listOf(null)) {
                val expected = observed == certified
                assertEquals(List(5) { expected }, flags(project(listOf(row), architecture = observed)))
            }
        }
    }

    @Test
    fun `B-AGENT-009 photos require their own passing images cell`() {
        val row = record().apply { remove("architecture") }
        val cells = mutableMapOf<String, Any?>("cards" to mapOf("state" to "pass", "evidence" to "docs/qa/example/README.md"))
        row["cells"] = cells
        assertClosed(project(listOf(row)))
        for (state in listOf("pass", "partial", "fail", "off", "untested")) {
            cells["images"] = mapOf("state" to state, "evidence" to "docs/qa/example/README.md#photos")
            assertEquals(listOf(false, false, false, state == "pass", false), flags(project(listOf(row))))
        }
        cells["images"] = mapOf("state" to "pass", "evidence" to null)
        assertClosed(project(listOf(row)))
    }

    @Test
    fun `B-AGENT-010 missing or mismatched pins and unknown architecture invalidate proof`() {
        for (field in listOf("agentVersion", "helperVersion", "architecture")) {
            for (invalid in listOf(null, "", "unknown", 7, " arm64")) {
                val row = record().apply { put(field, invalid) }
                assertClosed(project(listOf(row)), "$field=$invalid")
            }
        }
        assertClosed(project(listOf(record().apply { put("agentVersion", "unknown") })))
        assertClosed(project(listOf(record()), helperVersion = null))
        assertClosed(project(listOf(record()), helperVersion = "0.9.3"))
        assertClosed(project(listOf(record()), architecture = null))
        assertClosed(project(listOf(record()), architecture = AgentArchitecture.X64))
        val upgraded = AgentDescriptor(
            id = claude.id, name = claude.name, iconKey = claude.iconKey, route = claude.route,
            providerId = claude.providerId, signInMethod = claude.signInMethod,
            recipe = AgentInstallRecipe("${claude.recipe!!.version}-next", claude.recipe!!.executable, claude.recipe!!.artifacts),
            limitation = claude.limitation, resumeReason = claude.resumeReason,
        )
        assertClosed(project(listOf(record()), descriptor = upgraded))
        val noRecipe = AgentDescriptor(
            id = claude.id, name = claude.name, iconKey = claude.iconKey, route = claude.route,
            providerId = claude.providerId, signInMethod = claude.signInMethod,
            availability = AgentAvailability.Hidden, unavailableReason = AgentUnavailableReason.RecipeUnverified,
            resumeReason = claude.resumeReason,
        )
        assertClosed(project(listOf(record()), descriptor = noRecipe))
    }

    @Test
    fun `B-AGENT-009 only exact pass with safe repository evidence grants a capability`() {
        fun resumeRow(state: Any?, evidence: Any?) =
            record().apply { put("cells", mapOf("resume" to mapOf("state" to state, "evidence" to evidence))) }
        for (evidence in listOf("docs/qa/example/README.md", "docs/qa/example/README.md#proof", "docs/qa/example/README.md #2")) {
            assertTrue(project(listOf(resumeRow("pass", evidence))).resumeVerified, evidence)
        }
        for (state in listOf("fail", "untested", "partial", "off", "n/a", "blocked", "blocked:OW1", "PASS", " pass", null, true)) {
            assertClosed(project(listOf(resumeRow(state, "docs/qa/example/README.md"))), "$state")
        }
        val unsafe = listOf(
            null, "", " ", "/docs/qa/README.md", "../docs/qa/README.md", "docs/../README.md", "docs//README.md",
            "docs/./README.md", "docs\\qa\\README.md", "https://example.test/README.md",
            "docs/qa/README.md?token=redacted", "docs/%2e%2e/README.md", "docs/qa/README.md#",
            "docs/qa/README.md#proof#other", "docs/qa/README.md (forms)", "docs/qa/README.md\u0000",
            "docs/qa/README.md\n", "#95 run, APK 2187", true,
        )
        for (evidence in unsafe) assertClosed(project(listOf(resumeRow("pass", evidence))), "$evidence")
    }

    @Test
    fun `B-AGENT-010 duplicate ids invalidate all records`() {
        assertClosed(project(listOf(record(), record())))
        assertClosed(project(listOf(record(), mapOf("id" to "claude", "cells" to null))))
        assertClosed(project(listOf(mapOf("id" to "claude"), record())))
    }

    @Test
    fun `B-AGENT-010 malformed and unrecognized records never grant capabilities`() {
        for (invalid in listOf(null, emptyList<Any>(), 3, "records")) {
            assertClosed(matrix(invalid).capabilitiesFor(claude, AgentArchitecture.Arm64, "0.9.2"))
        }
        assertClosed(project(listOf(null, 3, emptyList<Any>(), mapOf("id" to true), record("unknown"))))
        val badCells = listOf(
            null, emptyList<Any>(), true, mapOf("resume" to true), mapOf("resume" to null),
            mapOf("unexpected" to mapOf("state" to "pass", "evidence" to "docs/qa/README.md")),
        )
        for (cells in badCells) assertClosed(project(listOf(record().apply { put("cells", cells) })), "$cells")
        val unknown = AgentDescriptor(
            id = AgentId("unknown"), name = "Unknown", iconKey = "unknown", route = claude.route, providerId = "unknown",
            signInMethod = claude.signInMethod, recipe = claude.recipe,
            capabilities = AgentCapabilities(resumeVerified = true), limitation = AgentLimitation.NeedsHostSetup,
        )
        assertClosed(project(listOf(record("unknown")), descriptor = unknown))
        assertClosed(AgentCertification.parse("not json").capabilitiesFor(claude, AgentArchitecture.Arm64, "0.9.2"))
    }

    @Test
    fun `B-AGENT-010 projection freezes input and keeps unverified-resume copy`() {
        val row = record()
        val cells = row["cells"] as MutableMap<*, *>
        val frozen = matrix(listOf(row))
        cells.clear()
        row["agentVersion"] = "changed"
        assertOpen(frozen.capabilitiesFor(claude, AgentArchitecture.Arm64, "0.9.2"))
        assertTrue(claude.resumeUnverified)
    }

    private fun chatRow(
        id: String = "claude",
        agentVersion: String? = null,
        states: Map<String, String> = mapOf("install" to "pass", "smoke" to "pass"),
    ) = mapOf(
        "id" to id,
        "agentVersion" to (agentVersion ?: catalog.byId(AgentId(id))!!.recipe!!.version),
        "helperVersion" to "0.9.2",
        "cells" to states.mapValues { (_, v) -> mapOf("state" to v, "evidence" to "docs/qa/example/README.md") },
    )

    private fun certified(row: Map<String, Any?>, id: String = "claude") = matrix(listOf(row)).certifiedForChat(AgentId(id))

    @Test
    fun `B-AGENT-011 the bundled matrix certifies Claude Code and nothing else yet`() {
        assertTrue(AgentCertification.bundled.certifiedForChat(AgentId("claude")))
        for (id in listOf("codex", "gemini", "qwen", "kilo", "cline", "goose", "omp-acp", "fx", "unknown")) {
            assertFalse(AgentCertification.bundled.certifiedForChat(AgentId(id)), id)
        }
    }

    @Test
    fun `B-AGENT-011 chat certification needs the pinned version with install and smoke passed`() {
        assertTrue(certified(chatRow()))
        assertFalse(certified(chatRow(agentVersion = "2.1.282")))
        assertFalse(certified(chatRow(states = mapOf("install" to "pass", "smoke" to "untested"))))
        assertFalse(certified(chatRow(states = mapOf("install" to "pass", "smoke" to "blocked:OW1"))))
        assertFalse(certified(chatRow(states = mapOf("smoke" to "pass"))))
        assertFalse(certified(chatRow(id = "fx"), "claude"))
    }
}
