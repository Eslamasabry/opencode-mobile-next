package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arrayOrNull
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.requests.isPaseoProviderId
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoAgentProductName
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * An agent's own switches (fast mode and the like) and the model list's own ids, a port of
 * lib/paseo/gateway/agent_features.dart and `_canonicalModelId` (sessions.dart) — B-AGENT-146, 182. Before the first
 * message there is no agent yet: the daemon lists what the chosen agent and model would offer, and a choice made then
 * is sent when the conversation starts.
 */

public enum class AgentFeatureKind {
    Toggle,
    Choice,
}

public data class AgentFeatureOption(val id: String, val label: String)

public data class AgentFeature(
    val id: String,
    val label: String,
    val description: String?,
    val kind: AgentFeatureKind,
    /** A toggle's state. */
    val on: Boolean = false,
    /** A choice's selected option id. */
    val selected: String? = null,
    val options: List<AgentFeatureOption> = emptyList(),
)

private val FEATURE_ID = Regex("^[A-Za-z0-9_.:-]{1,64}$")
private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
private val BLANKS = Regex("\\s+")

private fun featureText(value: JsonElement?, max: Int): String? {
    val clean =
        value.stringOrNull()?.replace(CONTROL, " ")?.replace(BLANKS, " ")?.trim() ?: return null
    if (clean.isEmpty()) return null
    return if (clean.length > max) clean.substring(0, max) else clean
}

/** The switches an agent snapshot or a provider listing carries; anything malformed is skipped. */
internal fun parseAgentFeatures(raw: JsonElement?): List<AgentFeature> {
    val list = raw.arrayOrNull() ?: return emptyList()
    val out = ArrayList<AgentFeature>()
    for (itemRaw in list.take(16)) {
        val item = itemRaw.objectOrNull() ?: continue
        val id = item.str("id")
        val label = featureText(item["label"], 80)
        if (id == null || !FEATURE_ID.matches(id) || label == null) continue
        val description = featureText(item["description"], 200)
        when (item.str("type")) {
            "toggle" -> {
                val on = item["value"].boolOrNull() ?: continue
                out += AgentFeature(id, label, description, AgentFeatureKind.Toggle, on = on)
            }
            "select" -> {
                val options =
                    item["options"].arrayOrNull().orEmpty().take(32).mapNotNull { o ->
                        val option = o.objectOrNull() ?: return@mapNotNull null
                        val optionId =
                            option.str("id")?.takeIf { it.isNotEmpty() && it.length <= 128 }
                                ?: return@mapNotNull null
                        AgentFeatureOption(
                            optionId,
                            featureText(option["label"], 80) ?: return@mapNotNull null,
                        )
                    }
                if (options.isEmpty()) continue
                out +=
                    AgentFeature(
                        id,
                        label,
                        description,
                        AgentFeatureKind.Choice,
                        selected = item.str("value"),
                        options = options,
                    )
            }
        }
    }
    return out
}

/**
 * The model list's own id for [model] of [provider] (an alias or `<provider>/<id>` names it); null
 * when unknown.
 */
internal fun PaseoGateway.canonicalModelId(provider: String?, model: String?): String? {
    if (provider == null || model == null) return null
    for (entry in providerEntries.orEmpty()) {
        if (entry.str("provider") != provider) continue
        val models = entry["models"].arrayOrNull() ?: return null
        val items = models.mapNotNull { it.objectOrNull() }
        if (items.any { it.str("id") == model }) return model
        items
            .firstOrNull { item ->
                item.str("id") != null &&
                    item["aliases"].arrayOrNull()?.any { it.stringOrNull() == model } == true
            }
            ?.let {
                return it.str("id")
            }
        // Some runtimes (Kilo) report the model under their own name as well: "kilo/google/x" for
        // the list's "google/x".
        val own = "$provider/"
        if (model.startsWith(own) && model.length > own.length)
            return canonicalModelId(provider, model.substring(own.length))
    }
    return null
}

/**
 * Agents read before the model list arrived carry the daemon's long id; once the list is known they
 * show its id.
 */
internal fun PaseoGateway.refreshCanonicalModels() {
    for (agent in agents.values.toList()) {
        val canonical = canonicalModelId(agent.str("provider"), agent.str("model"))
        if (canonical == null || canonical == agent.str("model")) continue
        remember(agent, reconcileStatus = false)?.let { emit(GatewayEvent.SessionUpserted(it)) }
    }
}

private suspend fun PaseoGateway.readAgentFeatures(id: String): List<AgentFeature> {
    if (isSubagent(id)) return emptyList()
    if (id in drafts) return readDraftFeatures(id)
    return parseAgentFeatures(fetchAgent(id)["features"])
}

private suspend fun PaseoGateway.readDraftFeatures(id: String): List<AgentFeature> {
    val scope = scopeDir
    val epoch = locationEpoch
    val provider = draftProviders[id] ?: PaseoGateway.DEFAULT_PROVIDER
    if (!isPaseoProviderId(provider)) return emptyList()
    val model = draftModels[id]?.modelId
    val values = draftFeatureValues[id]
    val result =
        transport.request(
            "list_provider_features_request",
            jsonOf(
                "draftConfig" to
                    jsonOf(
                            "provider" to provider,
                            "cwd" to scope,
                            "model" to
                                model?.takeIf {
                                    it.isNotEmpty() && it != PaseoGateway.DEFAULT_MODEL
                                },
                            "featureValues" to
                                values?.takeIf { it.isNotEmpty() }?.let(::JsonObject),
                        )
                        .withoutNulls()
            ),
        )
    checkLocation(scope, epoch)
    return parseAgentFeatures(result["features"])
}

/** The catalog's product name, or the helper's bounded label for a custom runtime. */
public fun PaseoGateway.agentFeaturesOwner(session: SessionId): String? {
    val id = app(session.raw)
    val provider =
        agents[id]?.str("provider") ?: draftProviders[id] ?: PaseoGateway.DEFAULT_PROVIDER
    return paseoAgentProductName(provider)
        ?: providerEntries
            ?.firstOrNull { it.str("provider") == provider }
            ?.let { featureText(it["label"], 40) }
}

/**
 * The agent's switches as the daemon reports them; a draft's as its chosen agent and model would
 * offer them.
 */
public suspend fun PaseoGateway.agentFeatures(session: SessionId): Outcome<List<AgentFeature>> =
    laneCall {
        readAgentFeatures(session.raw)
    }

/**
 * Changes one switch ([value]: Boolean for a toggle, an option id for a choice) and answers the
 * switches as the daemon then reports them. On a draft the choice is kept and sent with the
 * conversation's creation.
 */
public suspend fun PaseoGateway.setAgentFeature(
    session: SessionId,
    featureId: String,
    value: Any,
): Outcome<List<AgentFeature>> = laneCall {
    val id = session.raw
    if (!FEATURE_ID.matches(featureId) || isSubagent(id)) paseoFail(PaseoFailureKind.Unavailable)
    val scope = scopeDir
    val epoch = locationEpoch
    val known = readAgentFeatures(id).firstOrNull { it.id == featureId }
    checkLocation(scope, epoch)
    val wire: JsonPrimitive =
        when {
            known == null -> paseoFail(PaseoFailureKind.Unavailable)
            known.kind == AgentFeatureKind.Toggle && value is Boolean -> JsonPrimitive(value)
            known.kind == AgentFeatureKind.Choice &&
                value is String &&
                known.options.any { it.id == value } -> JsonPrimitive(value)
            else -> paseoFail(PaseoFailureKind.Unavailable)
        }
    if (id in drafts) {
        draftFeatureValues[id] = draftFeatureValues[id].orEmpty() + (featureId to wire)
        return@laneCall readDraftFeatures(id)
    }
    val result =
        workspaceRequest(
            "set_agent_feature_request",
            JsonObject(
                mapOf(
                    "agentId" to JsonPrimitive(real(id)),
                    "featureId" to JsonPrimitive(featureId),
                    "value" to wire,
                )
            ),
            mutation = true,
        )
    checkLocation(scope, epoch)
    if (result["accepted"].boolOrNull() == false) paseoFail(PaseoFailureKind.Unavailable)
    // The agent's own record is the answer, not what was asked for.
    readAgentFeatures(id)
}
