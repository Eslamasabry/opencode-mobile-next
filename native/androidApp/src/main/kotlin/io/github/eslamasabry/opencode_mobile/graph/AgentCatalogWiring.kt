package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogHost
import io.github.eslamasabry.oc.engine.setupassist.setupRedact
import io.github.eslamasabry.oc.feature.setupreview.presentation.*
import io.github.eslamasabry.oc.feature.setupreview.presentation.SetupMcpState
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/**
 * A saved account and its exact live graph must still name the same server before any operation.
 */
internal fun ConnectionRegistry.savedGraph(stores: PreviewStores, id: ProfileId): ConnectionGraph? {
    val graph = live.value[id] ?: return null
    val stored = stores.profiles.profile(id)?.profile ?: return null
    val origin = (AddressRules.normalize(stored.baseUrl) as? Outcome.Ok)?.value ?: return null
    return graph.takeIf {
        it.profile.origin == origin &&
            it.profile.username == stored.username.ifEmpty { null } &&
            it.profile.backend == stored.backend
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppMcpCatalogHost(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val http: HttpClientFactory,
) : McpCatalogHost {
    private fun key(profile: String) = "oc.setupRegistry.$profile"

    override fun sourceIdentity(profile: String): Any? =
        registry.savedGraph(stores, ProfileId(profile))
            ?: stores.profiles.profile(ProfileId(profile))?.profile

    override fun current(profile: String, directory: String?, workspace: String?): Boolean {
        val graph =
            sourceIdentity(profile) as? ConnectionGraph
                ?: return stores.profiles.profile(ProfileId(profile)) != null &&
                    directory == null &&
                    workspace == null
        return graph.session.server.location.value == ServerLocation(directory, workspace)
    }

    override fun capabilities(profile: String): ServerCapabilities =
        (sourceIdentity(profile) as? ConnectionGraph)?.session?.capabilities?.value
            ?: ServerCapabilities()

    override suspend fun loadCache(profile: String): Outcome<JsonObject?> {
        when (val ready = stores.warm()) {
            is Outcome.Failed -> return ready
            is Outcome.Ok -> Unit
        }
        if (stores.profiles.profile(ProfileId(profile)) == null) return staleCatalog()
        val stored = stores.prefs.get(key(profile)) ?: return Outcome.Ok(null)
        val raw = (stored as? PrefValue.Str)?.value ?: return Failure.UnreadableReply().failed()
        return attempt({ Failure.UnreadableReply() }) { Json.parseToJsonElement(raw) as JsonObject }
    }

    override suspend fun saveCache(profile: String, value: JsonObject): Outcome<Unit> {
        val id = ProfileId(profile)
        if (stores.profiles.profile(id) == null) return staleCatalog()
        val result =
            stores.prefs.commit(stores.lanes.forProfile(id)) {
                putString(key(profile), value.toString())
            }
        return if (stores.profiles.profile(id) == null) staleCatalog() else result
    }

    override suspend fun saveRefreshedCache(profile: String, value: JsonObject): Outcome<Unit> {
        val id = ProfileId(profile)
        if (stores.profiles.profile(id) == null) return staleCatalog()
        val before = stores.prefs.get(key(profile)) as? PrefValue.Str ?: return staleCatalog()
        val parsed =
            (attempt({ Failure.UnreadableReply() }) {
                    Json.parseToJsonElement(before.value) as JsonObject
                }
                    as? Outcome.Ok)
                ?.value ?: return staleCatalog()
        if ((parsed["optedIn"] as? JsonPrimitive)?.booleanOrNull != true) return staleCatalog()
        return when (
            val saved =
                stores.prefs.compareAndCommit(
                    stores.lanes.forProfile(id),
                    mapOf(key(profile) to before),
                ) {
                    putString(key(profile), value.toString())
                }
        ) {
            is Outcome.Failed -> saved
            is Outcome.Ok ->
                if (saved.value && stores.profiles.profile(id) != null) Unit.ok()
                else staleCatalog()
        }
    }

    /**
     * A separate, public-only client: the selected server's authentication never reaches the
     * registry.
     */
    override suspend fun fetchRegistry(query: String?): Outcome<JsonObject> {
        val text = query?.trim()?.takeIf { it.isNotEmpty() }
        if (
            text != null && (text.codePointCount(0, text.length) > 100 || text.any { it.code < 32 })
        )
            return Failure.UnreadableReply().failed()
        val origin = ServerOrigin("https", "registry.modelcontextprotocol.io", 443)
        val client =
            http.create(
                TransportSpec(
                    origin,
                    AddressRules.classify(origin.host),
                    false,
                    readTimeoutMs = 10_000,
                )
            )
        return try {
            withTimeoutOrNull(10_000) {
                val result =
                    client.exchange({
                        method = HttpMethod.Get
                        url("https://registry.modelcontextprotocol.io/v0.1/servers")
                        url.parameters.append("limit", "100")
                        url.parameters.append("version", "latest")
                        text?.let { url.parameters.append("search", it) }
                        header(HttpHeaders.Accept, "application/json")
                    }) { response ->
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(512 * 1024 + 1)
                        var count = 0
                        while (count < buffer.size) {
                            val read = channel.readAvailable(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        if (count == buffer.size) null
                        else buffer.copyOf(count).decodeToString(throwOnInvalidSequence = true)
                    }
                when (result) {
                    is Outcome.Failed -> result
                    is Outcome.Ok -> {
                        val body =
                            result.value
                                ?: return@withTimeoutOrNull Failure.UnreadableReply().failed()
                        attempt({ Failure.UnreadableReply() }) {
                            Json.parseToJsonElement(body) as JsonObject
                        }
                    }
                }
            } ?: Failure.TimedOut().failed()
        } finally {
            client.close()
        }
    }

    override suspend fun inventory(
        profile: String,
        directory: String?,
        workspace: String?,
    ): Outcome<List<McpServerInfo>> {
        val graph = sourceIdentity(profile) as? ConnectionGraph ?: return staleCatalog()
        if (!current(profile, directory, workspace)) return staleCatalog()
        val gateway =
            graph.session.server.facet<McpGateway>()
                ?: return Failure.Unavailable(FeatureId("serverCatalog")).failed()
        val result = gateway.mcpServers()
        if (sourceIdentity(profile) !== graph || !current(profile, directory, workspace))
            return staleCatalog()
        return result.map { it.toList() }
    }

    override suspend fun add(
        profile: String,
        directory: String?,
        workspace: String?,
        draft: McpServerDraft,
    ): Outcome<Unit> {
        val graph = sourceIdentity(profile) as? ConnectionGraph ?: return staleCatalog()
        if (!current(profile, directory, workspace)) return staleCatalog()
        if (!graph.session.capabilities.value.mcpRuntimeAdds)
            return Failure.Unavailable(FeatureId("mcpRuntimeAdds")).failed()
        val gateway =
            graph.session.server.facet<McpGateway>()
                ?: return Failure.Unavailable(FeatureId("mcpRuntimeAdds")).failed()
        val result = gateway.addMcpServer(draft, McpConfigScope.RuntimeLocation)
        if (sourceIdentity(profile) !== graph || !current(profile, directory, workspace))
            return staleCatalog()
        return result
    }
}

private fun staleCatalog(): Outcome.Failed =
    Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppSetupReviewHost(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val redactor: Redactor,
) : SetupReviewHost {
    private val absent = Any()

    override fun contexts(profile: ProfileId): Flow<SetupReviewContext> =
        combine(registry.live, stores.profiles.profiles) { _, _ ->
                registry.savedGraph(stores, profile)
            }
            .flatMapLatest { graph ->
                if (graph == null)
                    flowOf(SetupReviewContext(profile, null, null, false, false, absent))
                else
                    combine(
                        graph.session.server.location,
                        graph.session.server.events.status,
                        graph.session.capabilities,
                    ) { location, status, capabilities ->
                        SetupReviewContext(
                            profile,
                            location.directory,
                            location.workspace,
                            status == StreamStatus.Connected,
                            capabilities.setupConfigRead &&
                                graph.session.server is SetupInspectionGateway,
                            graph,
                        )
                    }
            }
            .distinctUntilChanged()

    override fun plan(
        context: SetupReviewContext,
        intent: SetupGuidedIntent,
        inputs: Map<SetupGuidedField, String>,
    ): SetupGuidedReply {
        val graph =
            context.owner as? ConnectionGraph
                ?: return SetupGuidedReply(SetupGuidedPhase.Unavailable)
        if (
            registry.savedGraph(stores, context.profile) !== graph ||
                graph.session.server.location.value !=
                    ServerLocation(context.directory, context.workspace)
        ) {
            return SetupGuidedReply(SetupGuidedPhase.Unavailable)
        }
        val planner = io.github.eslamasabry.oc.engine.setupassist.SetupPlanner(redactor)
        try {
            val selected =
                when (intent) {
                    SetupGuidedIntent.ChooseModel ->
                        io.github.eslamasabry.oc.engine.setupassist.SetupIntent.ChooseModel
                    SetupGuidedIntent.DefaultAgent ->
                        io.github.eslamasabry.oc.engine.setupassist.SetupIntent.DefaultAgent
                    SetupGuidedIntent.Permission ->
                        io.github.eslamasabry.oc.engine.setupassist.SetupIntent.Permission
                    SetupGuidedIntent.RemoteMcp ->
                        io.github.eslamasabry.oc.engine.setupassist.SetupIntent.ConnectRemoteMcp
                    SetupGuidedIntent.LocalMcp ->
                        io.github.eslamasabry.oc.engine.setupassist.SetupIntent.AddLocalMcp
                    SetupGuidedIntent.RemoveMcp ->
                        io.github.eslamasabry.oc.engine.setupassist.SetupIntent.RemoveMcp
                }
            val values =
                inputs
                    .mapKeys { (field, _) -> field.name.lowercase() }
                    .mapValues { (field, value) ->
                        if (field == "command") JsonArray(value.lines().map(::JsonPrimitive))
                        else JsonPrimitive(value)
                    }
            val reply = planner.start(selected, inputs = values)
            return when (reply.state) {
                io.github.eslamasabry.oc.engine.setupassist.SetupPlannerState.NeedsInput ->
                    SetupGuidedReply(
                        SetupGuidedPhase.NeedsInput,
                        inputsInvalid = inputs.isNotEmpty(),
                    )
                io.github.eslamasabry.oc.engine.setupassist.SetupPlannerState.Unsupported ->
                    SetupGuidedReply(SetupGuidedPhase.Unavailable)
                io.github.eslamasabry.oc.engine.setupassist.SetupPlannerState.Proposed ->
                    SetupGuidedReply(
                        SetupGuidedPhase.Proposed,
                        reply.edits
                            .map { edit ->
                                SetupValueUi(
                                    edit.path.joinToString(" / ") { redactor.redact(it) },
                                    if (edit.remove) ""
                                    else setupRedact(redactor, edit.value).toString(),
                                )
                            }
                            .toImmutableList(),
                    )
            }
        } finally {
            planner.dispose()
        }
    }

    override suspend fun read(context: SetupReviewContext): Outcome<SetupReviewContent> {
        val graph = context.owner as? ConnectionGraph ?: return staleCatalog()
        fun current(): Boolean =
            registry.savedGraph(stores, context.profile) === graph &&
                graph.session.server.location.value ==
                    ServerLocation(context.directory, context.workspace) &&
                graph.session.server.events.status.value == StreamStatus.Connected
        if (!current()) return staleCatalog()
        val inspection =
            graph.session.server.facet<SetupInspectionGateway>()
                ?: return Failure.Unavailable(FeatureId("setupConfigRead")).failed()
        val sourceResult = inspection.setupConfigSources()
        if (!current()) return staleCatalog()
        val config =
            when (sourceResult) {
                is Outcome.Failed -> return sourceResult
                is Outcome.Ok -> sourceResult.value
            }
        redactor.registerCredentialValues(config)
        val sources = config["sources"] as? JsonArray ?: return Failure.UnreadableReply().failed()
        val tools =
            graph.session.server.facet<McpGateway>()
                ?: return Failure.Unavailable(FeatureId("setupMcpInventory")).failed()
        val inventory = tools.mcpServers()
        if (!current()) return staleCatalog()
        val servers =
            when (inventory) {
                is Outcome.Failed -> return inventory
                is Outcome.Ok -> inventory.value
            }
        val safeSources = ArrayList<SetupSourceUi>()
        for (raw in sources.take(100)) {
            val source = raw as? JsonObject ?: return Failure.UnreadableReply().failed()
            val type =
                (source["type"] as? JsonPrimitive)?.contentOrNull
                    ?: return Failure.UnreadableReply().failed()
            val info = setupRedact(redactor, source["info"]) as? JsonObject
            val values =
                info
                    .orEmpty()
                    .entries
                    .take(100)
                    .map { (name, value) ->
                        SetupValueUi(
                            redactor.redact(name).take(120),
                            redactor.redact(value.toString()).take(2000),
                        )
                    }
                    .toImmutableList()
            safeSources +=
                SetupSourceUi(
                    redactor.redact(type).take(120),
                    (source["path"] as? JsonPrimitive)?.contentOrNull?.let {
                        redactor.redact(it).take(1024)
                    },
                    values,
                )
        }
        return SetupReviewContent(
                safeSources.toImmutableList(),
                servers
                    .take(100)
                    .map { server ->
                        SetupMcpUi(
                            redactor.redact(server.name).take(120),
                            when (server.status) {
                                "connected" -> SetupMcpState.Connected
                                "pending" -> SetupMcpState.Pending
                                "disabled" -> SetupMcpState.Disabled
                                "failed" -> SetupMcpState.Failed
                                "needs_auth" -> SetupMcpState.NeedsAuth
                                else -> SetupMcpState.Unknown
                            },
                        )
                    }
                    .toImmutableList(),
            )
            .ok()
    }
}
