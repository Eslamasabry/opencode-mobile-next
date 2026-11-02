package io.github.eslamasabry.oc.engine.agents.certification

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCapabilities
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of lib/domain/agent_tools/agent_certification.dart (AGENTS-BACKENDS-34, plan 17 §1.9). Qualification for exact
 * agent/helper versions, optionally scoped to a CPU. Installation, identity and non-pass cells never grant runtime
 * capabilities. Missing or mismatched pins, unknown architecture, duplicate ids or malformed records grant nothing.
 */
public class AgentCertification private constructor(
    private val records: Map<AgentId, Record>,
    private val catalog: AgentCatalog,
    private val testedVersions: TestedVersions,
) {
    /** Runtime capabilities proven for [descriptor] on [architecture] through helper [helperVersion]. */
    public fun capabilitiesFor(
        descriptor: AgentDescriptor,
        architecture: AgentArchitecture?,
        helperVersion: String?,
    ): AgentCapabilities {
        val record = records[descriptor.id]
        if (record == null ||
            helperVersion == null ||
            !sameAgentVersion(descriptor.id, record.agentVersion, descriptor.recipe?.version) ||
            !sameHelperVersion(record.helperVersion, helperVersion) ||
            (record.architecture != null && record.architecture != architecture)
        ) {
            return AgentCapabilities.None
        }
        return AgentCapabilities(
            resumeVerified = "resume" in record.passed,
            modelList = "models" in record.passed,
            permissions = "permission" in record.passed,
            images = "images" in record.passed,
            cancel = "abort" in record.passed,
        )
    }

    /**
     * Whether the version this app installs (the catalog pin) is certified for chat: the matrix has its row at exactly
     * that version and both install and smoke passed with evidence. The agent picker's gate; runtime capabilities still
     * come from [capabilitiesFor].
     */
    public fun certifiedForChat(agentId: AgentId): Boolean {
        val record = records[agentId] ?: return false
        val pinned = catalog.byId(agentId)?.recipe?.version ?: return false
        return sameAgentVersion(agentId, record.agentVersion, pinned) && record.passed.containsAll(CHAT_CELLS)
    }

    /** Evidence on one tested Paseo release carries to the others until the matrix is re-run on the new pair. */
    private fun sameHelperVersion(recorded: String, current: String): Boolean =
        recorded == current || (recorded in testedVersions.paseo && current in testedVersions.paseo)

    private fun sameAgentVersion(id: AgentId, recorded: String, current: String?): Boolean =
        recorded == current ||
            (id == AgentId.CLAUDE && recorded in testedVersions.claudeCode && current in testedVersions.claudeCode)

    private class Record(
        val agentVersion: String,
        val helperVersion: String,
        val architecture: AgentArchitecture?,
        val passed: Set<String>,
    )

    /** The shared tested-version sets (lib/paseo/tested_versions.dart). */
    public class TestedVersions(public val paseo: Set<String>, public val claudeCode: Set<String>) {
        public companion object {
            /** `paseoTestedVersions`, from the pins file (AgentPinsParityTest compares it with the Dart source). */
            public val PASEO: Set<String> get() = AgentPins.builtIn.paseoTestedVersions.toCollection(LinkedHashSet())

            public val builtIn: TestedVersions by lazy {
                TestedVersions(PASEO, AgentPins.builtIn.claudeCodeTestedVersions.toSet())
            }
        }
    }

    public companion object {
        private val CHAT_CELLS = setOf("install", "smoke")
        private val CELLS = setOf(
            "install", "version", "signedOut", "signIn", "models", "smoke", "tools", "permission", "abort", "resume",
            "network", "cards", "images",
        )
        private val STATES = setOf("pass", "fail", "untested", "partial", "off", "n/a", "blocked")
        private val BLOCKED = Regex("^blocked:[A-Za-z0-9_-]+$")
        private val VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]*$")
        private val CITATION = Regex("^(docs/[A-Za-z0-9_./-]+\\.md)(?:\\s*#([A-Za-z0-9_-]+))?$")
        private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")

        /** The bundled snapshot ([AGENT_CERTIFICATION_JSON]) over the bundled catalog. */
        public val bundled: AgentCertification by lazy { parse(AGENT_CERTIFICATION_JSON) }

        /** A malformed document grants nothing; it never throws. */
        public fun parse(
            text: String,
            catalog: AgentCatalog = AgentCatalog.builtIn,
            testedVersions: TestedVersions = TestedVersions.builtIn,
        ): AgentCertification {
            val element = try {
                Json.parseToJsonElement(text)
            } catch (_: IllegalArgumentException) {
                JsonNull
            }
            return fromJson(element, catalog, testedVersions)
        }

        public fun fromJson(
            json: JsonElement,
            catalog: AgentCatalog = AgentCatalog.builtIn,
            testedVersions: TestedVersions = TestedVersions.builtIn,
        ): AgentCertification {
            val records = mutableMapOf<AgentId, Record>()
            val seen = mutableSetOf<AgentId>()
            val duplicated = mutableSetOf<AgentId>()
            val rows = (json as? JsonObject)?.get("agents") as? JsonArray
                ?: return AgentCertification(emptyMap(), catalog, testedVersions)
            for (row in rows) {
                if (row !is JsonObject) continue
                val idValue = row["id"] as? JsonPrimitive ?: continue
                if (!idValue.isString) continue
                val id = AgentId(idValue.content)
                if (catalog.byId(id) == null) continue
                if (!seen.add(id)) duplicated += id
                parseRecord(row)?.let { records[id] = it }
            }
            duplicated.forEach { records.remove(it) }
            return AgentCertification(records.toMap(), catalog, testedVersions)
        }

        private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun parseRecord(row: JsonObject): Record? {
            val agentVersion = row["agentVersion"].stringOrNull()?.takeIf { VERSION.matches(it) } ?: return null
            val helperVersion = row["helperVersion"].stringOrNull()?.takeIf { VERSION.matches(it) } ?: return null
            val architecture = if ("architecture" in row) {
                AgentArchitecture.entries.firstOrNull { it.wire == row["architecture"].stringOrNull() } ?: return null
            } else {
                null
            }
            val cells = row["cells"] as? JsonObject ?: return null
            val passed = mutableSetOf<String>()
            for ((cell, value) in cells) {
                if (cell !in CELLS || value !is JsonObject) return null
                val state = value["state"].stringOrNull() ?: return null
                val evidenceElement = value["evidence"]
                if (state !in STATES && !BLOCKED.matches(state)) return null
                val evidence = when {
                    evidenceElement == null || evidenceElement is JsonNull -> null
                    evidenceElement is JsonPrimitive && evidenceElement.isString -> evidenceElement.content
                    else -> return null
                }
                if (state == "pass" && safeEvidence(evidence)) passed += cell
            }
            return Record(agentVersion, helperVersion, architecture, passed.toSet())
        }

        /** A repository document path with an optional anchor. No URL, absolute path, query, traversal or prose. */
        private fun safeEvidence(value: String?): Boolean {
            if (value == null || value.isEmpty() || value != value.trim()) return false
            val citation = CITATION.find(value) ?: return false
            if (CONTROL.containsMatchIn(value)) return false
            return citation.groupValues[1].split('/').all { it.isNotEmpty() && it != "." && it != ".." }
        }
    }
}
