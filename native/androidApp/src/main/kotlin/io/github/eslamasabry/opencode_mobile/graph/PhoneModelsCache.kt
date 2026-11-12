package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationPhoneModels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The last model list each phone agent answered, kept across app starts so New conversation shows it at once while the
 * helper reads it again (its first read takes about ten seconds). Stored under `oc.agentModelList.<profileId>` (the
 * profile sweep removes it with the profile). Names and ids only: no secret, no address. A fresh read always replaces
 * the agent's entry, so a list read before an agent was installed or signed in never sticks.
 */
internal object PhoneModelsCache {
    fun key(profile: ProfileId): String = "oc.agentModelList.${profile.raw}"

    private const val MAX_AGENTS = 32
    private const val MAX_MODELS = 300
    private val JSON = Json { ignoreUnknownKeys = true }

    /** [stored] with [agentId]'s entry replaced by [fresh]; null [stored] or an unreadable one starts empty. */
    fun put(stored: String?, agentId: String, fresh: NewConversationPhoneModels): String {
        val all = LinkedHashMap(decodeAll(stored))
        all.remove(agentId)
        all[agentId] = fresh
        while (all.size > MAX_AGENTS) all.remove(all.keys.first())
        return JsonObject(all.mapValues { (_, v) -> encode(v) }).toString()
    }

    /** [agentId]'s last list, or null when none is stored or the text cannot be read. */
    fun get(stored: String?, agentId: String): NewConversationPhoneModels? = decodeAll(stored)[agentId]

    private fun encode(models: NewConversationPhoneModels): JsonElement =
        JsonObject(
            buildMap {
                models.model?.let { put("model", ref(it)) }
                put(
                    "models",
                    JsonArray(
                        models.models.take(MAX_MODELS).map { m ->
                            JsonObject(
                                buildMap {
                                    put("ref", ref(m.model))
                                    put("name", JsonPrimitive(m.name))
                                    m.runtime?.let { put("runtime", JsonPrimitive(it)) }
                                    m.providerName?.let { put("providerName", JsonPrimitive(it)) }
                                    if (m.variants.isNotEmpty()) put("variants", JsonArray(m.variants.map(::JsonPrimitive)))
                                }
                            )
                        }
                    ),
                )
            }
        )

    private fun ref(model: ModelRef): JsonElement =
        JsonArray(listOf(JsonPrimitive(model.providerId), JsonPrimitive(model.modelId)))

    private fun refOf(element: JsonElement?): ModelRef? {
        val parts = (element as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        return if (parts?.size == 2) ModelRef(parts[0], parts[1]) else null
    }

    private fun decodeAll(stored: String?): Map<String, NewConversationPhoneModels> {
        if (stored.isNullOrBlank()) return emptyMap()
        return try {
            JSON.parseToJsonElement(stored).jsonObject.mapNotNull { (agent, value) ->
                val entry = value as? JsonObject ?: return@mapNotNull null
                val models =
                    entry["models"]?.jsonArray?.mapNotNull { raw ->
                        val m = raw as? JsonObject ?: return@mapNotNull null
                        val ref = refOf(m["ref"]) ?: return@mapNotNull null
                        NewConversationModel(
                            ref,
                            m["name"]?.jsonPrimitive?.content ?: ref.modelId,
                            runtime = (m["runtime"] as? JsonPrimitive)?.content,
                            providerName = (m["providerName"] as? JsonPrimitive)?.content,
                            variants =
                                (m["variants"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
                        )
                    }.orEmpty()
                if (models.isEmpty()) null
                else agent to NewConversationPhoneModels(models, refOf(entry["model"]))
            }.toMap()
        } catch (_: Exception) {
            emptyMap() // an unreadable cache is no cache: the fresh read fills it again
        }
    }
}
