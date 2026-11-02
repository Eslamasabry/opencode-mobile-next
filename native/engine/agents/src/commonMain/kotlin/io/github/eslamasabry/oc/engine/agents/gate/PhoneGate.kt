package io.github.eslamasabry.oc.engine.agents.gate

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Why a phone check is asked for again (`PhoneAgentRuntime.checkRequiredReason`; closed, Flutter wire names). */
public enum class CheckRequiredReason(public val wire: String) {
    FirstCheck("firstCheck"),
    AgentVersion("agentVersion"),
    HelperVersion("helperVersion"),
    InstallationChanged("installationChanged"),
}

/**
 * The phone-check proof `oc.agentPhoneGate.<owner>` = `{"<agentId>": {fingerprint, architecture, activeVersion}}`
 * (AGENTS-BACKENDS-09, 05 §2.5). The fingerprint is Dart `jsonEncode([agentId, recipeVersion, artifactSha256,
 * paseoVersion, lockSha256, nodeVersion])`: stored proof survives the migration only if these bytes are identical.
 */
public object PhoneGate {
    public fun fingerprint(agent: AgentDescriptor, architecture: AgentArchitecture, pins: AgentPins): String =
        AgentJson.encodeStrings(
            listOf(
                agent.id.raw,
                agent.recipe?.version,
                agent.artifactFor(architecture)?.sha256,
                pins.paseo.version,
                pins.paseo.packageLockSha256,
                pins.node.version,
            ),
        )

    /** The owner's gates as stored; malformed storage reads as no proof (never as qualification). */
    public fun read(prefs: AgentPrefs, owner: OwnerId): Map<String, JsonObject> {
        val root = AgentJson.parseOrNull(prefs.string(AgentKeys.agentPhoneGate(owner))) as? JsonObject ?: return emptyMap()
        return root.entries.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }.toMap(LinkedHashMap())
    }

    /** Raw entries kept as stored (a non-object entry is kept too, so a rewrite never drops data it did not own). */
    internal fun readRaw(prefs: AgentPrefs, owner: OwnerId): LinkedHashMap<String, kotlinx.serialization.json.JsonElement> =
        (AgentJson.parseOrNull(prefs.string(AgentKeys.agentPhoneGate(owner))) as? JsonObject)?.let { LinkedHashMap(it) }
            ?: LinkedHashMap()

    public suspend fun write(prefs: AgentPrefs, owner: OwnerId, gates: Map<String, kotlinx.serialization.json.JsonElement>): Outcome<Unit> =
        prefs.commit { putString(AgentKeys.agentPhoneGate(owner), AgentJson.encode(JsonObject(gates))) }

    public fun entry(fingerprint: String, architecture: AgentArchitecture, activeVersion: String): JsonObject =
        JsonObject(
            linkedMapOf(
                "fingerprint" to JsonPrimitive(fingerprint),
                "architecture" to JsonPrimitive(architecture.wire),
                "activeVersion" to JsonPrimitive(activeVersion),
            ),
        )

    /** Port of `_gateChangeReason`. */
    public fun changeReason(pin: JsonObject?, agent: AgentDescriptor, pins: AgentPins): CheckRequiredReason {
        if (pin == null) return CheckRequiredReason.FirstCheck
        val old = (pin["fingerprint"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.let { AgentJson.parseOrNull(it) as? JsonArray }
        if (old != null && old.size >= 6) {
            fun at(i: Int) = (old[i] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (at(1) != agent.recipe?.version) return CheckRequiredReason.AgentVersion
            if (at(3) != pins.paseo.version || at(4) != pins.paseo.packageLockSha256 || at(5) != pins.node.version) {
                return CheckRequiredReason.HelperVersion
            }
        }
        return CheckRequiredReason.InstallationChanged
    }

    /** The version an older stored proof names when only the agent's version moved (same helper pins, same CPU). */
    public fun olderQualifiedVersion(
        pin: JsonObject?,
        agent: AgentDescriptor,
        architecture: AgentArchitecture,
        pins: AgentPins,
    ): String? {
        if (pin == null || (pin["architecture"] as? JsonPrimitive)?.content != architecture.wire) return null
        if (changeReason(pin, agent, pins) != CheckRequiredReason.AgentVersion) return null
        val old = (pin["fingerprint"] as? JsonPrimitive)?.content?.let { AgentJson.parseOrNull(it) as? JsonArray } ?: return null
        val version = (old[1] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (version == agent.recipe?.version) return null
        if (agent.id == AgentId.CLAUDE && version !in pins.claudeCodeTestedVersions) return null
        return version
    }
}
