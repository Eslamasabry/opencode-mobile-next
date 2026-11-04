package io.github.eslamasabry.oc.engine.agents.models

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The per-profile model preference keys (AGENTS rule 9: `oc.<what>.<profileId>`). [profileScoped] is what the profiles
 * owner registers with ProfileScope so the deletion sweep finds them.
 */
public object ModelKeys {
    public const val MODEL_LIBRARY_PREFIX: String = "oc.modelLibrary."
    public const val SESSION_MODELS_PREFIX: String = "oc.sessionModels."

    public fun modelLibrary(profile: ProfileId): String = MODEL_LIBRARY_PREFIX + profile.raw

    public fun sessionModels(profile: ProfileId): String = SESSION_MODELS_PREFIX + profile.raw

    public fun profileScoped(profile: ProfileId): Set<String> = setOf(modelLibrary(profile), sessionModels(profile))
}

/** One chat's own model choice; [variant] is `""` for the model's default. */
public data class SessionModelChoice(public val model: ModelRef, public val variant: String = "")

/**
 * Model shortcuts and per-session choices stored through [AgentPrefs] in the Flutter formats byte for byte
 * (`ProfileStore.modelLibraryFor` / `sessionModelsFor`). Reads never throw: malformed data reads as empty.
 */
public class ModelStore(private val prefs: AgentPrefs) {
    public fun modelLibrary(profile: ProfileId): ModelLibrary {
        val raw = prefs.string(ModelKeys.modelLibrary(profile)) ?: return ModelLibrary()
        return ModelLibrary.fromJson(AgentJson.parseOrNull(raw))
    }

    /** An empty library removes the key; a refused commit is `Failure.Storage`. */
    public suspend fun setModelLibrary(profile: ProfileId, library: ModelLibrary): Outcome<Unit> {
        val key = ModelKeys.modelLibrary(profile)
        return prefs.commit {
            if (library.isEmpty) remove(key) else putString(key, AgentJson.encode(library.toJson()))
        }
    }

    /**
     * `{sessionId: "provider|model|variant"}`, insertion order kept. Entries that are not strings, lack two parts or
     * have an empty provider or model are dropped; composite ids are normalised on the way in.
     */
    public fun sessionModels(profile: ProfileId): Map<String, SessionModelChoice> {
        val raw = prefs.string(ModelKeys.sessionModels(profile))
        if (raw.isNullOrEmpty()) return emptyMap()
        val obj = AgentJson.parseOrNull(raw) as? JsonObject ?: return emptyMap()
        val result = LinkedHashMap<String, SessionModelChoice>()
        for ((id, value) in obj) {
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            if (id.isEmpty()) continue
            val parts = text.split('|')
            if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) continue
            result[id] = SessionModelChoice(ModelRef(parts[0], parts[1]).normalized, parts.getOrElse(2) { "" })
        }
        return result
    }

    /** Keeps the [SESSION_MODELS_CAP] most recently written entries; an empty map removes the key. */
    public suspend fun setSessionModels(profile: ProfileId, choices: Map<String, SessionModelChoice>): Outcome<Unit> {
        val key = ModelKeys.sessionModels(profile)
        if (choices.isEmpty()) return prefs.commit { remove(key) }
        val kept = choices.entries.toList().takeLast(SESSION_MODELS_CAP)
        val encoded = JsonObject(
            kept.associateTo(LinkedHashMap()) { (id, choice) ->
                id to JsonPrimitive("${choice.model.providerId}|${choice.model.modelId}|${choice.variant}")
            },
        )
        return prefs.commit { putString(key, AgentJson.encode(encoded)) }
    }

    public companion object {
        public const val SESSION_MODELS_CAP: Int = 200
    }
}
