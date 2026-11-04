package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.paseo.*
import io.github.eslamasabry.oc.protocol.paseo.requests.isPaseoProviderId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The daemon's offered options, explicit import handles and host updater; every await retains its
 * source.
 */
internal class PaseoAgentTools(private val g: PaseoGateway) {
    private val draftValues = HashMap<String, JsonObject>()
    private var imports = emptyMap<String, ImportableConversation>()
    private var importsEpoch = -1
    private val details = Redactor()

    private suspend inline fun <T> call(crossinline block: suspend () -> T): Outcome<T> =
        withContext(g.lane) { paseoAttempt { block() } }

    fun featureValues(id: String): JsonObject? = draftValues[id]?.takeIf { it.isNotEmpty() }

    fun forget(id: String) {
        draftValues.remove(id)
    }

    fun clear() {
        draftValues.clear()
        imports = emptyMap()
        importsEpoch = -1
    }

    private suspend fun features(id: String): ImmutableList<AgentFeature> {
        if (g.isReadOnlySession(SessionId(id))) paseoFail(PaseoFailureKind.Unavailable)
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        val raw =
            if (id in g.drafts) {
                val provider = g.draftProviders[id] ?: PaseoGateway.DEFAULT_PROVIDER
                if (!isPaseoProviderId(provider)) paseoFail(PaseoFailureKind.Unavailable)
                g.transport
                    .request(
                        "list_provider_features_request",
                        jsonOf(
                            "draftConfig" to
                                jsonOf(
                                        "provider" to provider,
                                        "cwd" to scope,
                                        "model" to
                                            g.draftModels[id]?.modelId?.takeIf {
                                                it.isNotEmpty() && it != PaseoGateway.DEFAULT_MODEL
                                            },
                                        "featureValues" to featureValues(id),
                                    )
                                    .withoutNulls()
                        ),
                    )["features"]
            } else g.fetchAgent(id)["features"]
        g.checkLocation(scope, epoch)
        return parseFeatures(raw as? kotlinx.serialization.json.JsonArray).toImmutableList()
    }

    suspend fun read(session: SessionId): Outcome<ImmutableList<AgentFeature>> = call {
        features(g.app(session.raw))
    }

    suspend fun set(
        session: SessionId,
        featureId: String,
        value: JsonPrimitive,
    ): Outcome<ImmutableList<AgentFeature>> = call {
        val id = g.app(session.raw)
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        if (!FEATURE_ID.matches(featureId)) paseoFail(PaseoFailureKind.Unavailable)
        val known =
            features(id).firstOrNull { it.id == featureId }
                ?: paseoFail(PaseoFailureKind.Unavailable)
        val valid =
            when (known.kind) {
                AgentFeatureKind.Toggle -> value.boolOrNull() != null
                AgentFeatureKind.Choice ->
                    value.stringOrNull()?.let { selected ->
                        known.options.any { it.id == selected }
                    } == true
            }
        if (!valid) paseoFail(PaseoFailureKind.Unavailable)
        g.checkLocation(scope, epoch)
        if (id in g.drafts) {
            draftValues[id] = (draftValues[id] ?: jsonOf()).with(featureId, value)
        } else {
            val real = g.real(id)
            val response =
                g.transport.request(
                    "set_agent_feature_request",
                    jsonOf("agentId" to real, "featureId" to featureId, "value" to value),
                    mutation = true,
                    beforeSend = {
                        g.checkLocation(scope, epoch)
                        if (g.real(id) != real) paseoFail(PaseoFailureKind.StaleRequest)
                    },
                )
            g.checkLocation(scope, epoch)
            if (response["accepted"].boolOrNull() == false) paseoFail(PaseoFailureKind.Unavailable)
        }
        features(id)
    }

    suspend fun listImports(): Outcome<ImportableConversations> = call {
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        val response =
            g.transport.request(
                "fetch_recent_provider_sessions_request",
                jsonOf("cwd" to scope, "providers" to listOf("claude"), "limit" to 50),
                timeoutMs = 45_000,
            )
        g.checkLocation(scope, epoch)
        val entries = paseoList(response["entries"], max = 200)
        val rows =
            entries
                .mapNotNull { raw ->
                    val item = raw.objectOrNull() ?: return@mapNotNull null
                    val provider =
                        item.str("providerId")?.takeIf(::isPaseoProviderId)
                            ?: return@mapNotNull null
                    val handle =
                        item.str("providerHandleId")?.takeIf {
                            it.isNotEmpty() &&
                                it.length <= 256 &&
                                it.none { c -> c.code < 32 || c.code == 127 }
                        } ?: return@mapNotNull null
                    val dir = item.str("cwd")?.takeIf { it == scope } ?: return@mapNotNull null
                    val at = paseoMillis(item["lastActivityAt"]) ?: return@mapNotNull null
                    ImportableConversation(
                        provider,
                        handle,
                        dir,
                        at,
                        clean(item.str("title"), 120),
                        clean(item.str("firstPromptPreview"), 120),
                    )
                }
                .sortedByDescending { it.lastUsedMs }
                .distinctBy { it.agentId to it.handle }
        imports = rows.associateBy { "${it.agentId}:${it.handle}" }
        importsEpoch = epoch
        ImportableConversations(
            rows.toImmutableList(),
            response["filteredAlreadyImportedCount"]
                .longOrNull()
                ?.coerceIn(0, Int.MAX_VALUE.toLong())
                ?.toInt() ?: 0,
        )
    }

    suspend fun importOne(conversation: ImportableConversation): Outcome<Session> = call {
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        val known = imports["${conversation.agentId}:${conversation.handle}"]
        if (
            importsEpoch != epoch ||
                conversation.directory != scope ||
                known == null ||
                known.lastUsedMs != conversation.lastUsedMs ||
                known.directory != conversation.directory
        )
            paseoFail(PaseoFailureKind.StaleRequest)
        val response =
            g.transport.request(
                "import_agent_request",
                jsonOf(
                    "providerId" to conversation.agentId,
                    "providerHandleId" to conversation.handle,
                    "cwd" to scope,
                ),
                mutation = true,
                timeoutMs = 90_000,
                beforeSend = { g.checkLocation(scope, epoch) },
            )
        g.checkLocation(scope, epoch)
        val id = response.obj("agent")?.str("id")
        if (response.str("status") != "agent_resumed" || id.isNullOrEmpty())
            paseoFail(PaseoFailureKind.Unavailable)
        g.fetchAgent(id)
        g.checkLocation(scope, epoch)
        imports = imports - "${conversation.agentId}:${conversation.handle}"
        g.sessions[g.app(id)] ?: paseoFail(PaseoFailureKind.ScopeMismatch)
    }

    suspend fun update(onProgress: (HostUpdatePhase) -> Unit): Outcome<HostUpdateResult> = call {
        val scope = g.scopeDir
        val epoch = g.locationEpoch
        val before = clean(g.transport.serverVersion, 64)
        val response =
            g.transport.request(
                "daemon.update.request",
                jsonOf(),
                mutation = true,
                timeoutMs = 600_000,
                beforeSend = { g.checkLocation(scope, epoch) },
                answerErrors = true,
                onProgress = { _, payload ->
                    if (!g.closed && g.locationEpoch == epoch && g.directory == scope)
                        when (payload.str("phase")) {
                            "starting" -> onProgress(HostUpdatePhase.Starting)
                            "downloading" -> onProgress(HostUpdatePhase.Downloading)
                            "installing" -> onProgress(HostUpdatePhase.Installing)
                            "complete" -> onProgress(HostUpdatePhase.Complete)
                        }
                },
            )
        g.checkLocation(scope, epoch)
        val success = response["success"].boolOrNull() == true
        HostUpdateResult(
            success,
            clean(response.str("previousVersion"), 64) ?: before,
            clean(response.str("newVersion"), 64),
            if (success) null else response.str("error")?.let { details.details(it.take(600)) },
        )
    }

    private fun parseFeatures(raw: kotlinx.serialization.json.JsonArray?): List<AgentFeature> =
        raw.orEmpty()
            .take(16)
            .mapNotNull { value ->
                val item = value.objectOrNull() ?: return@mapNotNull null
                val id = item.str("id")?.takeIf(FEATURE_ID::matches) ?: return@mapNotNull null
                val label = clean(item.str("label"), 80) ?: return@mapNotNull null
                val description = clean(item.str("description"), 200)
                when (item.str("type")) {
                    "toggle" ->
                        item["value"].boolOrNull()?.let {
                            AgentFeature(id, label, description, AgentFeatureKind.Toggle, on = it)
                        }
                    "select" -> {
                        val options =
                            item
                                .arr("options")
                                .orEmpty()
                                .take(32)
                                .mapNotNull optionMap@{ entry ->
                                    val option = entry.objectOrNull() ?: return@optionMap null
                                    val key =
                                        option.str("id")?.takeIf {
                                            it.isNotEmpty() && it.length <= 128
                                        } ?: return@optionMap null
                                    val text =
                                        clean(option.str("label"), 80) ?: return@optionMap null
                                    AgentFeatureOption(key, text)
                                }
                                .distinctBy { it.id }
                                .toImmutableList()
                        if (options.isEmpty()) null
                        else
                            AgentFeature(
                                id,
                                label,
                                description,
                                AgentFeatureKind.Choice,
                                selected =
                                    item.str("value")?.takeIf { v -> options.any { it.id == v } },
                                options = options,
                            )
                    }
                    else -> null
                }
            }
            .distinctBy { it.id }

    private fun clean(text: String?, max: Int): String? =
        text
            ?.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(max)

    private companion object {
        val FEATURE_ID = Regex("[A-Za-z0-9_.:-]{1,64}")
    }
}
