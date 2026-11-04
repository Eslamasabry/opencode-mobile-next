package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.github.eslamasabry.oc.feature.integrations.presentation.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@ContributesTo(ConnectionScope::class)
interface IntegrationsGraphAccess {
    val integrationOperations: ServerOperationsGateway
}

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<IntegrationsSource>())
class AndroidIntegrationsSource(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val redactor: Redactor,
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
    private val logos: IntegrationLogos,
    appScope: AppCoroutineScope,
) : IntegrationsSource {
    private val scope = CoroutineScope(appScope.coroutineContext)
    override val servers: StateFlow<ImmutableList<IntegrationServer>> =
        registry.live
            .map { graphs ->
                graphs
                    .map { (id, graph) -> IntegrationServer(id, clean(graph.profile.name)) }
                    .toImmutableList()
            }
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf())
    override val active: StateFlow<ProfileId?> = stores.profiles.activeId

    private fun clean(value: String) =
        redactor.redact(value).replace(Regex("[\\x00-\\x1f\\x7f-\\x9f]"), " ").take(160).ifBlank {
            "…"
        }

    override fun open(profile: ProfileId): IntegrationSession? {
        val graph = registry.live.value[profile] ?: return null
        val operations = (graph as? IntegrationsGraphAccess)?.integrationOperations ?: return null
        return PinnedSession(graph, operations)
    }

    override fun keyPage(provider: String): String? = providerKeyPageUrl(provider)

    override fun openLink(url: String, confirmed: Boolean): Outcome<IntegrationLinkPrompt?> {
        val origin =
            if (url == providerKeyPageUrl("anthropic") || url == providerKeyPageUrl("google"))
                LinkOrigin.AppAuthored
            else LinkOrigin.ServerContent
        return when (val result = policy.check(url, origin)) {
            is LinkDecision.Refuse -> Failure.Rejected(400).failed()
            is LinkDecision.Open -> opener.open(result.link).map { null }
            is LinkDecision.AskFirst ->
                if (confirmed) opener.open(result.link).map { null }
                else IntegrationLinkPrompt(result.shownHost, result.insecure).ok()
        }
    }

    private inner class PinnedSession(
        private val graph: ConnectionGraph,
        private val operations: ServerOperationsGateway,
    ) : IntegrationSession {
        override val profile = graph.profile.id
        private val gateway = graph.session.server
        private val location = gateway.location.value
        private var closed = false
        private var invalidated = false
        private var controller: McpChatConnect? = null
        private var connectorJob: Job? = null
        private val connectorState = MutableStateFlow<McpChatSnapshot?>(null)
        override val connector: StateFlow<McpChatSnapshot?> = connectorState.asStateFlow()
        private val methods = LinkedHashMap<Pair<String, String>, IntegrationMethodInfo>()
        private val oauth = LinkedHashSet<String>()
        private val observer = scope.launch {
            registry.live.collect { if (it[profile] !== graph) invalidate() }
        }
        private val skill =
            SkillConversation(
                object : SkillConversationPorts {
                    override val isClosed: Boolean
                        get() = !current()

                    override val canUseSkill: Boolean
                        get() = false

                    override val canAskForSkill: Boolean
                        get() = gateway.capabilities.value.serverCatalog

                    override suspend fun createSession(selection: SkillSelection): Outcome<String> =
                        guarded {
                            gateway.createSession(NewSession(location = location)).map { it.id.raw }
                        }

                    override suspend fun prompt(
                        sessionId: String,
                        text: String,
                        selection: SkillSelection,
                    ): Outcome<Unit> = guarded {
                        gateway
                            .prompt(
                                SessionId(sessionId),
                                OutgoingPrompt(text = text, agent = selection.agent),
                            )
                            .let { result ->
                                when (result) {
                                    is Outcome.Failed -> result
                                    is Outcome.Ok ->
                                        if (result.value is PromptAdmission.Confirmed) Unit.ok()
                                        else Failure.Unexpected().failed()
                                }
                            }
                    }

                    override suspend fun activateSkill(
                        sessionId: String,
                        skillId: String,
                        resume: Boolean,
                    ): Outcome<Unit> = unavailable()
                },
                redactor,
                ::current,
            )

        private fun current(): Boolean {
            val good =
                !closed &&
                    !invalidated &&
                    registry.live.value[profile] === graph &&
                    gateway.location.value == location &&
                    stores.profiles.profile(profile) != null
            if (!good && !closed) invalidate()
            return good
        }

        private fun invalidate() {
            invalidated = true
            controller?.invalidate()
            oauth.clear()
        }

        private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

        private fun unavailable(): Outcome<Nothing> =
            Failure.Unavailable(FeatureId("serverCatalog")).failed()

        private suspend fun <T> guarded(action: suspend () -> Outcome<T>): Outcome<T> {
            if (!current()) return stale()
            val answer = attempt({ Failure.Device() }) { action() }
            if (!current()) return stale()
            return when (answer) {
                is Outcome.Failed -> answer
                is Outcome.Ok -> answer.value
            }
        }

        override suspend fun read(): Outcome<IntegrationsData> = guarded {
            val caps = gateway.capabilities.value
            val providers =
                if (caps.integrationCredentials) operations.integrations()
                else persistentListOf<IntegrationInfo>().ok()
            val connectors =
                if (caps.serverCatalog) operations.mcpServers()
                else persistentListOf<McpServerInfo>().ok()
            if (providers is Outcome.Failed && connectors is Outcome.Failed)
                return@guarded providers
            methods.clear()
            val rows =
                (providers as? Outcome.Ok)
                    ?.value
                    .orEmpty()
                    .map { provider ->
                        val supported =
                            provider.methods
                                .mapNotNull { method ->
                                    if (method.type != "key" && method.type != "oauth")
                                        return@mapNotNull null
                                    if (
                                        method.prompts.any {
                                            (it["required"]
                                                    as? kotlinx.serialization.json.JsonPrimitive)
                                                ?.content == "true"
                                        }
                                    )
                                        return@mapNotNull null
                                    val id = method.id ?: method.type
                                    methods[provider.id to id] = method
                                    ProviderMethodUi(
                                        id,
                                        clean(method.label),
                                        method.type == "oauth",
                                    )
                                }
                                .toImmutableList()
                        ProviderUi(
                            provider.id,
                            clean(provider.name),
                            provider.connectionCount > 0,
                            supported,
                            logos.svg(provider.id),
                            providerKeyPageUrl(provider.id) != null,
                        )
                    }
                    .toImmutableList()
            IntegrationsData(
                    rows,
                    (connectors as? Outcome.Ok)
                        ?.value
                        .orEmpty()
                        .map { ConnectorUi(clean(it.name), it.status == "connected") }
                        .toImmutableList(),
                    providerSupported = caps.integrationCredentials && providers is Outcome.Ok,
                    connectorSupported = caps.mcpRuntimeAdds && connectors is Outcome.Ok,
                )
                .ok()
        }

        override suspend fun saveKey(provider: String, key: String): Outcome<Unit> = guarded {
            if (!gateway.capabilities.value.integrationCredentials) return@guarded unavailable()
            if (
                methods.none { (target, method) ->
                    target.first == provider && method.type == "key"
                } || key.isBlank()
            )
                return@guarded Failure.Rejected(400).failed()
            redactor.registerSecret(key)
            operations.connectIntegrationKey(provider, key)
        }

        override suspend fun startOAuth(
            provider: String,
            method: String,
        ): Outcome<ProviderAuthorization> = guarded {
            val selected =
                methods[provider to method] ?: return@guarded Failure.Rejected(400).failed()
            if (!gateway.capabilities.value.integrationCredentials || selected.type != "oauth")
                return@guarded unavailable()
            when (
                val result =
                    operations.startIntegrationOAuth(
                        provider,
                        selected.id ?: method,
                        selected.presetAnswers,
                    )
            ) {
                is Outcome.Failed -> result
                is Outcome.Ok -> {
                    val launch = result.value
                    val url =
                        parseAuthorizationUrl(launch.url)
                            ?: return@guarded Failure.Rejected(400).failed()
                    // A raw empty userinfo delimiter must not disappear in a parser.
                    if (
                        '@' in
                            url.substringAfter("://")
                                .substringBefore('/')
                                .substringBefore('?')
                                .substringBefore('#')
                    )
                        return@guarded Failure.Rejected(400).failed()
                    oauth += launch.attemptId
                    ProviderAuthorization(launch.attemptId, url).ok()
                }
            }
        }

        override suspend fun completeOAuth(attempt: String, input: String): Outcome<Unit> =
            guarded {
                if (attempt !in oauth || !gateway.capabilities.value.integrationCredentials)
                    return@guarded unavailable()
                if (input.length > 8192 || input.any { it.code < 32 })
                    return@guarded Failure.Rejected(400).failed()
                redactor.registerSecret(input)
                val code = providerOAuthCompletionCode(input)
                if (code.isBlank()) return@guarded Failure.Rejected(400).failed()
                redactor.registerSecret(code)
                operations.completeIntegrationOAuth(attempt, code).also {
                    if (it is Outcome.Ok) oauth.remove(attempt)
                }
            }

        override suspend fun saveConnector(
            name: String,
            url: String,
            headerName: String,
            headerValue: String,
        ): Outcome<Unit> = guarded {
            if (!gateway.capabilities.value.mcpRuntimeAdds) return@guarded unavailable()
            // Remote connectors may receive header secrets: require HTTPS even on private hosts.
            if (
                parseAuthorizationUrl(url) == null ||
                    name.isBlank() ||
                    name.length > 80 ||
                    name.any { it.code < 32 }
            )
                return@guarded Failure.Rejected(400).failed()
            val headers = McpSetupHeaders(redactor)
            headers.edit(0, headerName, headerValue)
            if (headerName.isBlank() && headerValue.isNotEmpty())
                return@guarded Failure.Rejected(400).failed()
            val draft =
                headers.draft(name.trim(), url.trim()).first
                    ?: return@guarded Failure.Rejected(400).failed()
            val before = operations.mcpServers()
            if (before !is Outcome.Ok) return@guarded before.map { Unit }
            if (!current()) return@guarded stale()
            if (before.value.any { it.name == name.trim() })
                return@guarded Failure.Conflict().failed()
            if (draft.headers.isNotEmpty()) {
                operations.addMcpServer(draft, McpConfigScope.RuntimeLocation)
            } else {
                controller?.dispose()
                connectorJob?.cancel()
                val next =
                    McpChatConnect(
                        McpChatSuggestion(name.trim(), draft, hosted = true),
                        { operations },
                        { gateway.capabilities.value },
                        ::current,
                    )
                controller = next
                connectorJob = scope.launch { next.snapshot.collect { connectorState.value = it } }
                next.connect(name.trim())
                when (next.snapshot.value.phase) {
                    McpChatPhase.Failed,
                    McpChatPhase.Unavailable -> Failure.Device().failed()
                    else -> Unit.ok()
                }
            }
        }

        override suspend fun startConnectorSignIn() {
            if (!closed) controller?.startOAuth()
        }

        override suspend fun completeConnectorSignIn(input: String) {
            if (closed || input.length > 8192) return
            redactor.registerSecret(input)
            controller?.completeOAuth(input)
        }

        override suspend fun cancelConnectorSignIn() {
            if (!closed) controller?.cancelOAuth()
        }

        override suspend fun writeSkill(requirements: String): SkillDraftResult {
            val result = skill.askAgentToWriteSkill(requirements)
            return SkillDraftResult(
                result,
                result.sessionId?.let { ConversationRef(profile, SessionId(it)) },
            )
        }

        override fun close() {
            closed = true
            observer.cancel()
            connectorJob?.cancel()
            controller?.dispose()
            oauth.clear()
            methods.clear()
        }
    }
}
