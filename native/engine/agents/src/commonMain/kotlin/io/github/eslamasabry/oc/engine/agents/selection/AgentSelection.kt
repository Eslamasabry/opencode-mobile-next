package io.github.eslamasabry.oc.engine.agents.selection

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AgentHostProblem
import io.github.eslamasabry.oc.engine.agents.readiness.AgentRow
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The agent chip, the selected agent and the model per agent (AGENTS-BACKENDS-03, plan 17 §1.6). Ported from
 * lib/state/connection/phone_agents.dart (`selectedChatAgentId`, `selectChatAgent`, `selectedAgentModel`,
 * `selectAgentModel`) and lib/state/connection/models.dart (`knownModelName`, `rememberModelName`). Formats are the
 * Flutter ones: `oc.chatAgent.<profile>` = agent id, `oc.agentModel.<profile>` = JSON `{agentId: modelId}`,
 * `oc.modelNames.<profile>` = JSON `{"provider/model": name}`, at most [MODEL_NAMES_CAP], oldest first out.
 */
public class AgentSelection(
    private val prefs: AgentPrefs,
    private val catalog: AgentCatalog = AgentCatalog.builtIn,
) {
    /**
     * The chip's agent. Falls back to OpenCode only once the first row read ([rows] non-empty) says the saved agent is
     * not selectable, so the chip does not flicker before rows load.
     */
    public fun selected(profile: ProfileId, rows: List<AgentRow>, phoneAgentsAvailable: Boolean): AgentId {
        val saved = prefs.string(AgentKeys.chatAgent(profile))?.let(::AgentId) ?: return AgentId.OPENCODE
        if (saved == AgentId.OPENCODE || !phoneAgentsAvailable) return AgentId.OPENCODE
        if (rows.isEmpty()) return saved
        return if (rowFor(saved, rows)?.chatSelectable == true) saved else AgentId.OPENCODE
    }

    /** Saves the chip's agent. A row that is not selectable is refused (AgentUnavailable); nothing is written. */
    public suspend fun select(
        profile: ProfileId,
        agent: AgentId,
        rows: List<AgentRow>,
        phoneAgentsAvailable: Boolean,
    ): Outcome<Unit> {
        if (agent != AgentId.OPENCODE) {
            val row = if (phoneAgentsAvailable) rowFor(agent, rows) else null
            if (row == null || !row.chatSelectable) return AgentHostProblem.AgentUnavailable.asFailure().failed()
        }
        return prefs.commit { putString(AgentKeys.chatAgent(profile), agent.raw) }
    }

    public fun selectedModel(profile: ProfileId, agent: AgentId): String? = savedModels(profile)[agent.raw]

    /** Saves (or with a null/empty [modelId], forgets) the model for [agent]. */
    public suspend fun selectModel(profile: ProfileId, agent: AgentId, modelId: String?): Outcome<Unit> {
        val models = LinkedHashMap(savedModels(profile))
        if (modelId.isNullOrEmpty()) models.remove(agent.raw) else models[agent.raw] = modelId
        return prefs.commit { putString(AgentKeys.agentModel(profile), encode(models)) }
    }

    /** The remembered display name ("Sonnet") for a model, so chips never show an id before the catalog loads. */
    public fun knownModelName(profile: ProfileId, providerId: String, modelId: String): String? =
        modelNames(profile)["$providerId/$modelId"]

    /** Remembers a model's display name; re-remembering moves it to the newest end; the oldest go past 40. */
    public suspend fun rememberModelName(
        profile: ProfileId,
        providerId: String,
        modelId: String,
        name: String,
    ): Outcome<Unit> {
        val key = "$providerId/$modelId"
        val names = LinkedHashMap(modelNames(profile))
        if (name.isBlank() || names[key] == name) return Outcome.Ok(Unit)
        names.remove(key)
        names[key] = name
        while (names.size > MODEL_NAMES_CAP) names.remove(names.keys.first())
        return prefs.commit { putString(AgentKeys.modelNames(profile), encode(names)) }
    }

    /** By id first, then by the catalog provider id (Dart `_paRowFor`). */
    private fun rowFor(agent: AgentId, rows: List<AgentRow>): AgentRow? {
        rows.firstOrNull { it.id == agent }?.let { return it }
        val byProvider = catalog.agents.firstOrNull { it.providerId == agent.raw } ?: return null
        return rows.firstOrNull { it.id == byProvider.id }
    }

    private fun savedModels(profile: ProfileId): Map<String, String> =
        decode(prefs.string(AgentKeys.agentModel(profile)))

    private fun modelNames(profile: ProfileId): Map<String, String> =
        decode(prefs.string(AgentKeys.modelNames(profile)))

    public companion object {
        public const val MODEL_NAMES_CAP: Int = 40

        /** Agents on this phone work only in projects under this folder (the shared bind). */
        public const val PHONE_PROJECTS_PREFIX: String = "/root/projects/"

        /**
         * Whether Send is allowed for a phone agent in [directory]; when false the composer says why
         * (words key `agentFolderOutsideProjects`: "Agents on this phone work in projects under /root/projects.
         * Choose one of those."). OpenCode has no such rule.
         */
        public fun folderAllowed(agent: AgentId, directory: String?): Boolean =
            agent == AgentId.OPENCODE || (directory != null && directory.startsWith(PHONE_PROJECTS_PREFIX))

        /** "In a separate copy" (worktree launch) is hidden for phone agents. */
        public fun separateCopyOffered(agent: AgentId): Boolean = agent == AgentId.OPENCODE

        private fun decode(raw: String?): Map<String, String> {
            if (raw == null) return emptyMap()
            val obj = try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: return emptyMap()
            val out = LinkedHashMap<String, String>()
            for ((k, v) in obj) if (v is JsonPrimitive && v.isString) out[k] = v.content
            return out
        }

        private fun encode(map: Map<String, String>): String =
            JsonObject(map.mapValues { JsonPrimitive(it.value) }).toString()
    }
}
