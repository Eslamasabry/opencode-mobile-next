package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.*
import io.github.eslamasabry.oc.core.net.*
import io.github.eslamasabry.oc.core.net.gate.NetworkGate
import io.github.eslamasabry.oc.core.net.stream.*
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.oc2.adapter.*
import io.github.eslamasabry.oc.protocol.oc2.events.*
import io.github.eslamasabry.oc.protocol.oc2.send.Oc2ClientIds
import io.github.eslamasabry.oc.protocol.oc2.send.Oc2PromptSend
import io.github.eslamasabry.oc.protocol.oc2.wire.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Every mutation makes exactly one request. An uncertain prompt receipt is never replayed. */
internal class Oc2Gateway(
    private val net: NetClient,
    private val origin: ServerOrigin,
    private val credentials: ConnectionCredentials,
    private val clock: Clock,
    private val redactor: Redactor,
    initialLocation: ServerLocation = ServerLocation.Default,
    random: Random = Random.Default,
    initialDialect: Oc2Dialect? = null,
) : ServerGateway, ServerOperationsGateway, CorrelatedPromptGateway, SessionSelectionGateway {
    private val ids = Oc2ClientIds(clock, random)
    private val dialect = MutableStateFlow(initialDialect)
    private val negotiation = Mutex()

    override fun createPromptMessageId(): MessageId = MessageId(ids.message())

    override val capabilities: StateFlow<ServerCapabilities> = MutableStateFlow(Capabilities)
    override val location = MutableStateFlow(initialLocation)
    private val status = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
    private val connects = MutableStateFlow(0)
    private val envelopes = MutableSharedFlow<GatewayEnvelope>()
    private var loop: EventStreamLoop? = null
    private var stream: Job? = null

    init {
        credentials.secret?.let(redactor::registerSecret)
    }

    private val auth =
        credentials.secret?.let {
            val token =
                Base64.encode("${credentials.username ?: "opencode"}:$it".encodeToByteArray())
            redactor.registerSecret(token)
            "Basic $token"
        }
    override val events: GatewayEvents =
        object : GatewayEvents {
            override val status = this@Oc2Gateway.status
            override val connects = this@Oc2Gateway.connects
            override val all: Flow<GatewayEnvelope> = envelopes
            override val project: Flow<GatewayEnvelope> = envelopes.filter { e ->
                val here = location.value
                val source = e.location
                source == null ||
                    (here.directory == null && here.workspace == null) ||
                    (source.directory == here.directory && source.workspace == here.workspace)
            }

            override fun retryNow() {
                loop?.retryNow()
            }
        }

    override fun setLocation(location: ServerLocation) {
        this.location.value = location
    }

    internal fun start(spec: GatewaySpec, dispatchers: AppDispatchers) {
        val connector = KtorSseConnector(net.http) { request("/event", HttpMethod.Get) }
        val live =
            EventStreamLoop(
                connector,
                BackoffPolicy(500, 16_000, true, null, stallTimeoutMs = 45_000),
                AddressRules.classify(origin.host),
                NetworkGate(UnknownNetworkStatus),
                clock,
                Random.Default,
                reconnectAllowed = spec.reconnectAllowed,
            )
        loop = live
        stream =
            spec.scope.launch(dispatchers.default) {
                val adapter = Oc2EventAdapter { clock.now().toEpochMilliseconds() }
                val projection = Oc2CoreProjection(redactor::details)
                live.signals().collect { signal ->
                    when (signal) {
                        is StreamSignal.Status -> {
                            status.value = signal.status
                            if (signal.status == StreamStatus.Connected) {
                                connects.value += 1
                                envelopes.emit(
                                    GatewayEnvelope(
                                        GatewayEvent.Connected,
                                        null,
                                        clock.now().toEpochMilliseconds(),
                                    )
                                )
                            }
                        }
                        is StreamSignal.Frame -> {
                            val wire = Oc2Envelope.parse(signal.frame.data)
                            val place =
                                wire.location?.let {
                                    EventLocation(it.directory, it.project?.id, it.workspaceID)
                                }
                            for (event in adapter.adapt(wire)) envelopes.emit(
                                GatewayEnvelope(
                                    projection.toCore(event),
                                    place,
                                    clock.now().toEpochMilliseconds(),
                                )
                            )
                        }
                    }
                }
            }
    }

    private fun HttpRequestBuilder.request(
        path: String,
        verb: HttpMethod,
        query: Map<String, String?> = emptyMap(),
    ) {
        method = verb
        url(origin.toString() + "/api" + path)
        for ((key, value) in query) if (value != null) url.parameters.append(key, value)
        auth?.let { header(HttpHeaders.Authorization, it) }
    }

    internal suspend fun json(
        path: String,
        verb: HttpMethod = HttpMethod.Get,
        body: JsonObject? = null,
        query: Map<String, String?> = emptyMap(),
    ): Outcome<JsonElement> {
        if (dialect.value == null) {
            when (val health = health()) {
                is Outcome.Failed -> return health
                is Outcome.Ok ->
                    if (!health.value.healthy) return Failure.UnreadableReply().failed()
            }
        }
        val settled = dialect.value ?: return Failure.UnreadableReply().failed()
        val route = settled.route(path, verb, body, query)
        return rawJson(route.path, route.method, route.body, route.query)
    }

    private suspend fun rawJson(
        path: String,
        verb: HttpMethod = HttpMethod.Get,
        body: JsonObject? = null,
        query: Map<String, String?> = emptyMap(),
    ): Outcome<JsonElement> {
        val response =
            net.exchange({
                request(path, verb, query)
                header(HttpHeaders.Accept, "application/json")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }) { reply ->
                if (reply.status == HttpStatusCode.NoContent) "null" else reply.bodyAsText()
            }
        return when (response) {
            is Outcome.Failed -> {
                val failure = response.failure
                if (
                    auth == null &&
                        failure is Failure.SignIn &&
                        failure.why == SignInWhy.PasswordRejected
                )
                    Failure.SignIn(SignInWhy.PasswordMissing, failure.details).failed()
                else response
            }
            is Outcome.Ok ->
                Oc2Wire.parse(response.value)?.ok() ?: Failure.UnreadableReply().failed()
        }
    }

    private fun data(element: JsonElement): JsonElement =
        (element as? JsonObject)?.get("data") ?: element

    private fun entries(element: JsonElement): List<JsonElement> =
        (data(element) as? JsonArray)?.toList() ?: emptyList()

    private fun pageEntries(element: JsonElement): List<JsonElement>? =
        when (val value = data(element)) {
            is JsonArray -> value.toList()
            is JsonObject -> (value["items"] as? JsonArray)?.toList()
            else -> null
        }

    private fun cursor(element: JsonElement): String? =
        (element as? JsonObject)?.obj("cursor")?.str("next")
            ?: (element as? JsonObject)?.str("next")
            ?: (element as? JsonObject)?.str("nextCursor")

    private suspend fun <T> read(
        path: String,
        verb: HttpMethod = HttpMethod.Get,
        body: JsonObject? = null,
        query: Map<String, String?> = emptyMap(),
        decode: (JsonElement) -> T?,
    ): Outcome<T> =
        when (val result = json(path, verb, body, query)) {
            is Outcome.Failed -> result
            is Outcome.Ok -> decode(result.value)?.ok() ?: Failure.UnreadableReply().failed()
        }

    private suspend fun unit(
        path: String,
        verb: HttpMethod,
        body: JsonObject? = null,
    ): Outcome<Unit> = json(path, verb, body).map {}

    private fun sid(id: SessionId): String = id.raw.encodeURLPathPart()

    override suspend fun health(): Outcome<Health> = negotiation.withLock {
        val known = dialect.value
        var fromInfo = known == Oc2Dialect.Stable
        var response = rawJson(if (fromInfo) "/info" else "/health")
        // Only a clean read-only 404 may try the stable facts endpoint. Mutations are never
        // retried.
        if (known == null && response is Outcome.Failed && response.failure is Failure.NotFound) {
            fromInfo = true
            response = rawJson("/info")
        }
        when (val result = response) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                val obj =
                    data(result.value) as? JsonObject
                        ?: return@withLock Failure.UnreadableReply().failed()
                val version = obj.str("version")
                val detected =
                    Oc2Dialect.ofVersion(version)
                        ?: return@withLock Failure.UnreadableReply().failed()
                if (fromInfo && detected != Oc2Dialect.Stable)
                    return@withLock Failure.UnreadableReply().failed()
                val healthy =
                    if (fromInfo) true
                    else
                        (obj["healthy"] as? JsonPrimitive)?.booleanOrNull
                            ?: return@withLock Failure.UnreadableReply().failed()
                // A changed generation is not silently installed under an already pinned
                // connection.
                if (known != null && known != detected)
                    return@withLock Failure.UnreadableReply().failed()
                if (healthy) dialect.value = detected
                Health(healthy, version).ok()
            }
        }
    }

    override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> =
        read(
            "/session",
            query =
                if (cursor != null) mapOf("cursor" to cursor)
                else
                    mapOf(
                        "limit" to limit.toString(),
                        "order" to "desc",
                        "directory" to location.value.directory,
                        "workspace" to location.value.workspace,
                    ),
        ) { root ->
            val rows = pageEntries(root) ?: return@read null
            val decoded = rows.map { sessionValue(data(it)) ?: return@read null }
            ServerPage(
                decoded.toImmutableList(),
                cursor(root)?.takeIf { it.isNotEmpty() && rows.size >= limit },
            )
        }

    override suspend fun createSession(spec: NewSession): Outcome<Session> =
        read(
            "/session",
            HttpMethod.Post,
            buildJsonObject {
                spec.title?.let { put("title", it) }
                val place = spec.location ?: location.value
                if (place.directory != null)
                    put(
                        "location",
                        buildJsonObject {
                            put("directory", place.directory)
                            place.workspace?.let { put("workspaceID", it) }
                        },
                    )
            },
        ) {
            sessionValue(data(it))
        }

    override suspend fun session(id: SessionId): Outcome<Session> =
        read("/session/${sid(id)}") { sessionValue(data(it)) }

    // Model and agent are persistent session state (stable 2.2.5: POST /session/{id}/model and
    // /agent). A refused default is reported as its typed failure, never silently dropped; the
    // readback returns what the server actually latched.
    override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> {
        val created =
            when (val result = createSession(NewSession())) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value
            }
        val model = defaults.model
        if (model != null) {
            val modelResult = setSessionModel(created.id, model, defaults.variant)
            if (modelResult is Outcome.Failed) return modelResult
        }
        val agent = defaults.agent?.takeIf { it.isNotEmpty() }
        if (agent != null) {
            val agentResult = setSessionAgent(created.id, agent)
            if (agentResult is Outcome.Failed) return agentResult
        }
        return session(created.id)
    }

    override suspend fun setSessionModel(
        session: SessionId,
        model: ModelRef,
        variant: String,
    ): Outcome<Unit> =
        unit(
            "/session/${sid(session)}/model",
            HttpMethod.Post,
            Oc2SessionSelection.modelBody(model, variant),
        )

    override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> =
        unit(
            "/session/${sid(session)}/agent",
            HttpMethod.Post,
            buildJsonObject { put("agent", agent) },
        )

    override suspend fun renameSession(id: SessionId, title: String): Outcome<Unit> =
        unit("/session/${sid(id)}/rename", HttpMethod.Post, buildJsonObject { put("title", title) })

    override suspend fun deleteSession(id: SessionId): Outcome<Unit> =
        unit("/session/${sid(id)}", HttpMethod.Delete)

    override suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>> =
        read("/session/active") { root ->
            val obj = data(root) as? JsonObject ?: return@read null
            val output = mutableMapOf<SessionId, SessionStatus>()
            for ((key, value) in obj) {
                if (
                    key.isBlank() ||
                        key.length > 256 ||
                        key.any { it.isWhitespace() } ||
                        (value as? JsonObject)?.str("type") != "running"
                )
                    return@read null
                output[SessionId(key)] = SessionStatus.Busy
            }
            output.toImmutableMap()
        }

    override suspend fun messagePage(
        id: SessionId,
        cursor: String?,
        limit: Int,
    ): Outcome<ServerPage<MessageWithParts>> =
        read(
            "/session/${sid(id)}/message",
            query =
                if (cursor != null) mapOf("cursor" to cursor)
                else mapOf("limit" to limit.toString(), "order" to "desc"),
        ) { root ->
            val rows = pageEntries(root) ?: return@read null
            val history = Oc2History(redactor)
            val messages =
                rows.reversed().map { history.message(id.raw, data(it)) ?: return@read null }
            ServerPage(
                messages.toImmutableList(),
                cursor(root)?.takeIf { it.isNotEmpty() && rows.size >= limit },
            )
        }

    override suspend fun prompt(
        session: SessionId,
        prompt: OutgoingPrompt,
        commandId: String?,
    ): Outcome<PromptAdmission> =
        sendPrompt(
            session,
            prompt,
            prompt.clientMessageId?.raw ?: commandId ?: createPromptMessageId().raw,
        ) {
            true
        }

    override suspend fun promptWithMessageId(
        session: SessionId,
        messageId: MessageId,
        prompt: OutgoingPrompt,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission> = sendPrompt(session, prompt, messageId.raw, beforeSend)

    private suspend fun sendPrompt(
        session: SessionId,
        prompt: OutgoingPrompt,
        clientId: String,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission> {
        if (prompt.delivery == PromptDelivery.Queue)
            return Failure.Unavailable(FeatureId("queue")).failed()
        val before = this.session(session)
        if (before is Outcome.Failed) return before
        if ((before as Outcome.Ok).value.reverted)
            return Failure.Refused(RefusalReason.UndoUnresolved).failed()
        if (!beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
        return when (
            val result =
                json(
                    "/session/${sid(session)}/prompt",
                    HttpMethod.Post,
                    Oc2PromptSend.body(prompt, clientId),
                )
        ) {
            is Outcome.Failed ->
                if (
                    ((result.failure as? Failure.Refused)?.reason ==
                        RefusalReason.DispatchUncertain ||
                        result.failure is Failure.UnreadableReply)
                )
                    PromptAdmission.Uncertain.ok()
                else result
            is Outcome.Ok -> {
                val receipt = (data(result.value) as? JsonObject)?.let { Oc2InboxItem.fromJson(it) }
                if (
                    receipt != null &&
                        Oc2PromptSend.receiptConfirms(receipt, session.raw, clientId) &&
                        receipt.sessionID == session.raw
                )
                    PromptAdmission.Confirmed(receipt.id).ok()
                else PromptAdmission.Uncertain.ok()
            }
        }
    }

    override suspend fun stop(session: SessionId, resume: Boolean): Outcome<StopResult> =
        when (
            val result =
                json(
                    "/session/${sid(session)}/interrupt",
                    HttpMethod.Post,
                    query = if (resume) mapOf("continue" to "true") else emptyMap(),
                )
        ) {
            is Outcome.Failed ->
                if (
                    (result.failure as? Failure.Refused)?.reason ==
                        RefusalReason.DispatchUncertain || result.failure is Failure.UnreadableReply
                )
                    StopResult.Uncertain.ok()
                else result
            is Outcome.Ok ->
                when ((result.value as? JsonObject)?.get("interrupted")) {
                    JsonPrimitive(true) -> StopResult.Stopped.ok()
                    JsonPrimitive(false) -> StopResult.NothingRunning.ok()
                    else -> StopResult.Uncertain.ok()
                }
        }

    override suspend fun shell(
        session: SessionId,
        command: String,
        agent: String,
        model: ModelSelection?,
    ): Outcome<Unit> = unavailable("conversationShell")

    override suspend fun slashCommand(
        session: SessionId,
        command: String,
        args: String,
        model: ModelSelection?,
    ): Outcome<Unit> = unavailable("slashCommands")

    override suspend fun todos(id: SessionId): Outcome<ImmutableList<Todo>> =
        unavailable("sessionTodos")

    override suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>> =
        unavailable("sessionDiff")

    override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> =
        read("/permission/request", query = locationQuery()) { root ->
            val rows = data(root) as? JsonArray ?: return@read null
            val projection = Oc2CoreProjection(redactor::details)
            rows
                .map { row ->
                    val wire =
                        (row as? JsonObject)?.let { Oc2PermissionRequest.fromJson(it) }
                            ?: return@read null
                    if (wire.sessionID.isNullOrBlank()) return@read null
                    (projection.toCore(Oc2Adapted.PermissionAsked(wire, wire.source?.id))
                            as GatewayEvent.PermissionAsked)
                        .request
                }
                .toImmutableList()
        }

    override suspend fun replyPermission(
        session: SessionId,
        request: RequestId,
        reply: PermissionReply,
        message: String?,
    ): Outcome<Unit> =
        unit(
            "/session/${sid(session)}/permission/${request.raw.encodeURLPathPart()}/reply",
            HttpMethod.Post,
            buildJsonObject {
                put(
                    "reply",
                    when (reply) {
                        PermissionReply.Once -> "once"
                        PermissionReply.Always -> "always"
                        PermissionReply.Reject -> "reject"
                    },
                )
                if (message != null && reply == PermissionReply.Reject) put("message", message)
            },
        )

    private fun locationQuery(): Map<String, String?> =
        mapOf(
            "location[directory]" to location.value.directory,
            "location[workspace]" to location.value.workspace,
        )

    override suspend fun providers(): Outcome<ProvidersResponse> {
        val providerReply = json("/provider", query = locationQuery())
        if (providerReply is Outcome.Failed) return providerReply
        val modelReply = json("/model", query = locationQuery())
        if (modelReply is Outcome.Failed) return modelReply
        val defaultReply = json("/model/default", query = locationQuery())
        if (defaultReply is Outcome.Failed) return defaultReply
        val providerJson = (providerReply as Outcome.Ok).value
        val modelJson = (modelReply as Outcome.Ok).value
        val defaultJson = (defaultReply as Outcome.Ok).value
        redactor.registerCredentialValues(providerJson)
        val providerRows =
            data(providerJson) as? JsonArray ?: return Failure.UnreadableReply().failed()
        val modelRows = data(modelJson) as? JsonArray ?: return Failure.UnreadableReply().failed()
        val models = modelRows.map {
            (it as? JsonObject)?.let { obj -> Oc2ModelInfo.fromJson(obj) }
                ?: return Failure.UnreadableReply().failed()
        }
        val names = LinkedHashMap<String, String>()
        for (row in providerRows) {
            val provider =
                (row as? JsonObject)?.let { Oc2ProviderInfo.fromJson(it) }
                    ?: return Failure.UnreadableReply().failed()
            names[provider.id] = provider.name ?: provider.id
        }
        for (model in models) model.providerID?.let { if (!names.containsKey(it)) names[it] = it }
        val providers =
            names
                .map { (id, name) ->
                    val listed = models.filter { it.providerID == id }
                    ProviderInfo(
                        id,
                        name,
                        listed
                            .filter { it.enabled }
                            .map { it.modelID ?: it.id.removePrefix("$id/") }
                            .toImmutableList(),
                        listed
                            .associate { (it.modelID ?: it.id.removePrefix("$id/")) to it.raw }
                            .toImmutableMap(),
                    )
                }
                .toImmutableList()
        val default = (data(defaultJson) as? JsonObject)?.let { Oc2ModelInfo.fromJson(it) }
        return ProvidersResponse(
                providers,
                providers,
                default?.providerID,
                default?.let { it.modelID ?: it.id.removePrefix("${it.providerID}/") },
            )
            .ok()
    }

    // The public provider inventory suffices; never request config/provider (which contains API
    // keys).
    override suspend fun configuredProviders(): Outcome<ProvidersResponse> = providers()

    override suspend fun agents(): Outcome<ImmutableList<AgentInfo>> =
        read("/agent", query = locationQuery()) { root ->
            entries(root)
                .mapNotNull { value ->
                    val obj = value as? JsonObject ?: return@mapNotNull null
                    obj.str("name")?.let { AgentInfo(it, obj.str("mode"), obj.str("color"), null) }
                }
                .toImmutableList()
        }

    /** Safe catalog rows only (GET /provider + /model + /agent, all confirmed 200); no raw provider config is kept. */
    override suspend fun catalog(): Outcome<CatalogSnapshot> {
        val providerReply = json("/provider", query = locationQuery())
        if (providerReply is Outcome.Failed) return providerReply
        val modelReply = json("/model", query = locationQuery())
        if (modelReply is Outcome.Failed) return modelReply
        val agentReply = json("/agent", query = locationQuery())
        if (agentReply is Outcome.Failed) return agentReply
        val providerJson = (providerReply as Outcome.Ok).value
        val modelJson = (modelReply as Outcome.Ok).value
        val agentJson = (agentReply as Outcome.Ok).value
        redactor.registerCredentialValues(providerJson)
        val providerRows =
            data(providerJson) as? JsonArray ?: return Failure.UnreadableReply().failed()
        val modelRows = data(modelJson) as? JsonArray ?: return Failure.UnreadableReply().failed()
        val agentRows = data(agentJson) as? JsonArray ?: return Failure.UnreadableReply().failed()
        val providers = LinkedHashMap<String, CatalogProvider>()
        for (row in providerRows) {
            val provider =
                (row as? JsonObject)?.let { Oc2ProviderInfo.fromJson(it) }
                    ?: return Failure.UnreadableReply().failed()
            providers.getOrPut(provider.id) { Oc2SessionSelection.catalogProvider(provider) }
        }
        val models = mutableListOf<CatalogModel>()
        for (row in modelRows) {
            val model =
                (row as? JsonObject)?.let { Oc2ModelInfo.fromJson(it) }
                    ?: return Failure.UnreadableReply().failed()
            // Defensive against catalog skew: a provider seen only on models is still listed.
            model.providerID?.let { providers.getOrPut(it) { CatalogProvider(it, it, true, null) } }
            models += Oc2SessionSelection.catalogModel(model)
        }
        val agents = mutableListOf<CatalogAgent>()
        for (row in agentRows) {
            val agent =
                (row as? JsonObject)?.let { Oc2AgentInfo.fromJson(it) }
                    ?: return Failure.UnreadableReply().failed()
            agents += Oc2SessionSelection.catalogAgent(agent)
        }
        return CatalogSnapshot(
                providers.values.toImmutableList(),
                models.toImmutableList(),
                agents.toImmutableList(),
            )
            .ok()
    }

    /** GET /model/default only; there is no confirmed default-agent endpoint, so the agent stays null. */
    override suspend fun chatDefaults(): Outcome<ChatDefaults> {
        val reply = json("/model/default", query = locationQuery())
        if (reply is Outcome.Failed) return reply
        val raw = data((reply as Outcome.Ok).value)
        if (raw == JsonNull) return ChatDefaults(null, null).ok()
        val info = (raw as? JsonObject)?.let { Oc2ModelInfo.fromJson(it) }
            ?: return Failure.UnreadableReply().failed()
        val ref = Oc2SessionSelection.defaultModelRef(info)
            ?: return Failure.UnreadableReply().failed()
        return ChatDefaults(ref, null).ok()
    }

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> =
        unavailable("fileBrowsing")

    override suspend fun fileContent(path: String): Outcome<FileContent> =
        unavailable("fileBrowsing")

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> =
        unavailable("fileBrowsing")

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> =
        unavailable("fileBrowsing")

    override suspend fun close() {
        try {
            stream?.cancelAndJoin()
        } finally {
            stream = null
            net.close()
        }
    }

    private fun sessionValue(value: JsonElement): Session? {
        val wire = Oc2Session.fromJson(value as? JsonObject ?: return null) ?: return null
        return Session(
            SessionId(wire.id),
            title = wire.title,
            projectId = wire.projectID?.let(::ProjectId),
            parentId = wire.parentID?.let(::SessionId),
            directory = wire.location?.directory,
            workspaceId = wire.location?.workspaceID?.let(::WorkspaceId),
            path = wire.subpath,
            reverted = wire.reverted,
            time =
                SessionTime(
                    wire.time.created,
                    wire.time.updated,
                    wire.time.archived,
                    wire.time.idle,
                    wire.time.viewed,
                ),
            agent = wire.agent,
            model = wire.model?.let { "${it.providerID}/${it.id}" },
            // A session read knows the truth even when model/agent are absent: that IS the
            // server's actual default (modelKnown/agentKnown true, variant sentinel normalized).
            selection = Oc2SessionSelection.selection(wire.model, wire.agent),
        )
    }

    companion object {
        val Capabilities =
            ServerCapabilities(
                scheduledPrompts = false,
                phoneTeamExecution = false,
                genUi = false,
                sessionAddressHandoff = false,
                agentHandoffFiles = false,
                setupConfigRead = false,
                setupConfigWrite = false,
                setupMcpInventory = false,
                setupAssistantSession = false,
                clientPromptMessageID = true,
                commandReceipts = false,
                agentAccount = false,
                hostAgentProviders = false,
                hostAgentPermissionActions = false,
                promptAttachments = true,
                promptImagesOnly = false,
                promptEchoTextOnly = false,
                subagentReplies = true,
                subagentSessions = false,
                promptAgentMentions = true,
                promptSteering = true,
                offlinePromptQueue = false,
                fileBrowsing = false,
                terminal = false,
                conversationShell = false,
                projectManagement = false,
                globalSessionSearch = false,
                sessionDiff = false,
                sessionFork = false,
                sessionCompact = false,
                persistentPermissionGrants = false,
                permissionRequests = true,
                savedPermissionList = false,
                messageCompletionEndsRun = false,
                sessionRevert = false,
                sessionImportExport = false,
                sessionNotes = false,
                serverCatalog = true,
                sessionModelProviderSwitching = true,
                agentSelection = false,
                slashCommands = false,
                profileAttentionPolling = false,
                managedWorkspaces = false,
                workspaceWarp = false,
                sessionSteal = false,
                consoleOrganizations = false,
                mcpOAuth = false,
                mcpChatConnect = false,
                mcpChatOAuth = false,
                mcpChatToolRefresh = false,
                mcpConfigWrites = false,
                mcpRuntimeAdds = false,
                mcpRuntimeRemovals = false,
                integrationCredentials = false,
                integrationCommandAuth = false,
                pluginInventory = false,
                webSearch = false,
                sessionShare = false,
                sessionArchive = false,
                sessionDelete = false,
                sessionTodos = false,
                messageDelete = false,
                workspaceSymbols = false,
                textSearch = false,
                languageServiceStatus = false,
                formatterStatus = false,
                toolInventory = false,
                experimentalCapabilities = false,
                shellSettings = false,
                developmentServices = false,
                remoteUpgrade = false,
                clientDiagnostics = false,
                gitInit = false,
                providerRuntimeRefresh = false,
                configuredProviderFallback = false,
                globalEventStream = false,
                worktreeReset = false,
                worktreeCreate = false,
                worktreeBrowsing = false,
                legacyQuestionRequests = false,
                forms = false,
                inbox = false,
                cliSessionResume = false,
                projectsAreGitRepositories = false,
                remoteShell = false,
                claudeAgent = false,
            )
    }
}
