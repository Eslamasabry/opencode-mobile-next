package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.model.CatalogAgent
import io.github.eslamasabry.oc.core.model.CatalogModel
import io.github.eslamasabry.oc.core.model.CatalogProvider
import io.github.eslamasabry.oc.core.model.CatalogVariant
import io.github.eslamasabry.oc.core.model.ModelCost
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.protocol.oc2.wire.EmptyObject
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2AgentInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ModelRef
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2ProviderInfo
import io.github.eslamasabry.oc.protocol.oc2.wire.obj
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Session selection and catalog mapping for the stable 2.2.5 contract (ports of the Flutter api2
 * mappers in gateway_mappers.dart). The server stores "no variant" as the string "default"; the
 * app's convention is the empty string, and an empty variant is never sent back on the wire.
 */
internal object Oc2SessionSelection {
    /** null / "" / "default" all mean: no variant (B-PROTO-018). */
    fun variant(wire: String?): String =
        when (wire) {
            null, "", "default" -> ""
            else -> wire
        }

    /** The variant that rides the wire body, or null when the key must be omitted. */
    fun sendableVariant(variant: String): String? = variant(variant).takeIf { it.isNotEmpty() }

    /**
     * What the conversation will use next, straight from a session read: a session that arrives
     * without a model or agent carries the server's actual default, so both are known even absent.
     */
    fun selection(model: Oc2ModelRef?, agent: String?): SessionSelection =
        SessionSelection(
            model = model?.let { ModelRef(it.providerID, it.id) },
            variant = variant(model?.variant),
            agent = agent,
            modelKnown = true,
            agentKnown = true,
        )

    /** `POST /session/{id}/model` body: `{model:{id, providerID, variant?}}`, variant omitted when empty. */
    fun modelBody(model: ModelRef, variant: String): JsonObject =
        buildJsonObject {
            put(
                "model",
                buildJsonObject {
                    put("id", model.modelId)
                    put("providerID", model.providerId)
                    sendableVariant(variant)?.let { put("variant", it) }
                },
            )
        }

    /** The id the app selects, sends and compares a model by: the server's own row id, peeled of its own provider prefix. */
    fun catalogModelId(model: Oc2ModelInfo): String {
        val provider = model.providerID
        if (provider != null && model.id.startsWith("$provider/")) return model.id.substring(provider.length + 1)
        return model.id
    }

    /** A mode's name, told apart from its base model: "Name · mode" unless the name already carries the mode. */
    private fun catalogModelName(model: Oc2ModelInfo): String {
        val id = catalogModelId(model)
        val name = model.name ?: id
        val base = model.modelID ?: return name
        if (base == id || !id.startsWith("$base-")) return name
        val mode = id.substring(base.length + 1)
        return if (name.lowercase().contains(mode.lowercase())) name else "$name · $mode"
    }

    /** Safe display metadata only: no raw provider config is kept or returned (mapApi2CatalogModel). */
    fun catalogModel(model: Oc2ModelInfo): CatalogModel =
        CatalogModel(
            id = catalogModelId(model),
            providerId = model.providerID ?: "",
            name = catalogModelName(model),
            family = model.family,
            enabled = model.enabled,
            status = model.status ?: "unknown",
            contextLimit = (model.limit.context ?: 0L).toInt(),
            outputLimit = (model.limit.output ?: 0L).toInt(),
            reasoning = false,
            attachments = model.capabilities.input.any { it != "text" },
            tools = model.capabilities.tools,
            variants = catalogVariants(model).toImmutableList(),
            cost =
                model.baseCost?.let {
                    ModelCost(it.input, it.output, it.cacheRead, it.cacheWrite)
                },
            releasedAtMs = model.released?.takeIf { it > 0 },
        )

    private fun catalogVariants(model: Oc2ModelInfo): List<CatalogVariant> =
        when (val raw = model.raw["variants"]) {
            is JsonArray ->
                raw.mapNotNull { row ->
                    val obj = row as? JsonObject ?: return@mapNotNull null
                    CatalogVariant(
                        id = obj.str("id") ?: return@mapNotNull null,
                        disabled = false,
                        options = obj.obj("settings") ?: EmptyObject,
                    )
                }
            else -> emptyList()
        }

    fun catalogProvider(provider: Oc2ProviderInfo): CatalogProvider =
        CatalogProvider(
            id = provider.id,
            name = provider.name ?: provider.id,
            enabled = provider.activation != "disabled",
            integrationId = provider.integrationID,
        )

    fun catalogAgent(agent: Oc2AgentInfo): CatalogAgent =
        CatalogAgent(
            id = agent.id,
            mode = agent.mode ?: "primary",
            description = agent.description,
            hidden = agent.hidden,
            maxSteps = null,
            color = agent.color,
            model = agent.model?.let { "${it.providerID}/${it.id}" },
        )

    /** `GET /model/default` row to a bare ref; a row without a provider id names no default. */
    fun defaultModelRef(info: Oc2ModelInfo?): ModelRef? {
        if (info == null) return null
        val provider = info.providerID ?: return null
        return ModelRef(provider, info.modelID ?: info.id)
    }
}
