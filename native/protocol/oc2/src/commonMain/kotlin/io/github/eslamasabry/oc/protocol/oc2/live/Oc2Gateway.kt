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
import io.ktor.client.statement.bodyAsBytes
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
    internal val net: NetClient,
    internal val origin: ServerOrigin,
    private val credentials: ConnectionCredentials,
    private val clock: Clock,
    private val redactor: Redactor,
    initialLocation: ServerLocation = ServerLocation.Default,
    random: Random = Random.Default,
    initialDialect: Oc2Dialect? = null,
) :
    ServerGateway,
    ServerOperationsGateway,
    CorrelatedPromptGateway,
    FencedSessionReadStateGateway,
    FencedNewSessionPromptGateway,
    PromptRunIdentityGateway,
    SessionSelectionGateway,
    StagedRevertGateway,
    LocatedFileGateway,
    LocatedVcsGateway,
    WebSearchGateway,
    LocatedManagedShellGateway,
    IntegrationCommandGateway,
    ServerInspectionGateway,
    McpRemovalGateway {
    private val integrationOps = Oc2Integrations(this, redactor)
    private val ids = Oc2ClientIds(clock, random)
    private val dialect = MutableStateFlow(initialDialect)
    private val negotiation = Mutex()

    override fun createPromptMessageId(): MessageId = MessageId(ids.message())

    /**
     * B-PROTO-028 (Flutter `Api2Gateway.serverRunsTools`): true when the person approves this
     * session automatically. Such a session gets its original rules back before each send, so the
     * server runs what its own rules allow without waiting for this phone; asks and denials still
     * apply.
     */
    var serverRunsTools: (SessionId) -> Boolean = { false }

    /**
     * Re-installs the session's permission layer now (automatic approval switched on or off
     * mid-reply).
     */
    suspend fun syncSessionPermissions(session: SessionId): Outcome<Unit> =
        preparePermissions(session)

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

    internal val auth =
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
        // dialect.dart Api2RemovedInStable: fail clearly, nothing is sent.
        route.removed?.let {
            return Failure.Unavailable(FeatureId(it)).failed()
        }
        return rawJson(route.path, route.method, route.body, route.query).map {
            settled.response(verb, path, it)
        }
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
        // B-PROTO-021 (Flutter unexpectedHealthShapeTag): an answer that is not an OpenCode 2
        // health shape (no
        // version, an OpenCode 1 version, an HTML page) is the re-detection signal, not a plain
        // unreadable reply.
        val mismatch = Failure.Incompatible(IncompatibleKind.ProtocolMismatch)
        when (val result = response) {
            is Outcome.Failed ->
                if (result.failure is Failure.UnreadableReply) mismatch.failed() else result
            is Outcome.Ok -> {
                val obj = data(result.value) as? JsonObject ?: return@withLock mismatch.failed()
                val version = obj.str("version")
                val detected = Oc2Dialect.ofVersion(version) ?: return@withLock mismatch.failed()
                if (fromInfo && detected != Oc2Dialect.Stable) return@withLock mismatch.failed()
                val healthy =
                    if (fromInfo) true
                    else
                        (obj["healthy"] as? JsonPrimitive)?.booleanOrNull
                            ?: return@withLock mismatch.failed()
                // A changed generation is not silently installed under an already pinned
                // connection.
                if (known != null && known != detected) return@withLock mismatch.failed()
                if (healthy) dialect.value = detected
                Health(healthy, version).ok()
            }
        }
    }

    /**
     * The add-server probe verdict (Flutter `probeServerConnection`, row B-PROTO-094): a healthy
     * answer connects with its version; an unhealthy one fails as `Busy(Failed)` without any v1
     * fallback; a 503 while booting is `Busy(Starting)`; 401 keeps its password verdict. Only when
     * no OpenCode 2 health answer exists (404, or a 200 that is not the v2 health JSON) is the
     * OpenCode 1 route read, and a real v1 server is `Incompatible(LegacyOpenCode1)`, never read as
     * v2.
     */
    internal suspend fun probe(): Outcome<Health> =
        when (val result = health()) {
            is Outcome.Ok ->
                if (result.value.healthy) result else Failure.Busy(BusyKind.Failed).failed()
            is Outcome.Failed -> {
                val failure = result.failure
                when {
                    failure is Failure.Busy && failure.kind == BusyKind.Overloaded ->
                        Failure.Busy(BusyKind.Starting).failed()
                    failure is Failure.NotFound ||
                        failure is Failure.UnreadableReply ||
                        (failure is Failure.Incompatible &&
                            failure.kind == IncompatibleKind.ProtocolMismatch) ->
                        if (isLegacyServer())
                            Failure.Incompatible(IncompatibleKind.LegacyOpenCode1).failed()
                        else result
                    else -> result
                }
            }
        }

    /**
     * `GET /global/health` (no `/api` prefix): an OpenCode 1 server answers `{healthy: true,
     * version}` there.
     */
    private suspend fun isLegacyServer(): Boolean {
        val response =
            net.exchange({
                method = HttpMethod.Get
                url("$origin/global/health")
                header(HttpHeaders.Accept, "application/json")
                auth?.let { header(HttpHeaders.Authorization, it) }
            }) { reply ->
                reply.bodyAsText()
            }
        val obj =
            (response as? Outcome.Ok)?.value?.let { Oc2Wire.parse(it) } as? JsonObject
                ?: return false
        return (obj["healthy"] as? JsonPrimitive)?.booleanOrNull == true &&
            obj.str("version") != null
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

    override suspend fun viewSession(session: SessionId, idleMs: Long): Outcome<Unit> =
        viewSession(session, idleMs) { true }

    override suspend fun viewSession(
        session: SessionId,
        idleMs: Long,
        beforeSend: () -> Boolean,
    ): Outcome<Unit> {
        val at = location.value
        if (idleMs <= 0 || !beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
        if (dialect.value == null) {
            when (val checked = health()) {
                is Outcome.Failed -> return checked
                is Outcome.Ok ->
                    if (!checked.value.healthy) return Failure.UnreadableReply().failed()
            }
        }
        if (location.value != at || !beforeSend())
            return Failure.Refused(RefusalReason.StaleScope).failed()
        return json(
                "/session/${session.raw.encodeURLPathPart()}/view",
                HttpMethod.Post,
                buildJsonObject { put("idle", idleMs) },
                locationQuery(at),
            )
            .map { Unit }
    }

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

    override suspend fun lastPromptIdentity(session: SessionId): Outcome<PromptRunIdentity?> =
        messagePage(session, limit = 100).map { lastPromptIdentity(it.items) }

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

    override suspend fun promptNewSessionWithMessageId(
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
        val prepared = preparePermissions(session)
        if (prepared is Outcome.Failed) return prepared
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

    /**
     * Codex chat-header BLOCKER 1 (Flutter `_prepareSessionDispatch`): before a prompt, command or
     * shell can run, the session's permission layer turns every allow into an ask ("Ask first": no
     * tool runs without a card), and it is read back. A failed or unconfirmed install sends
     * nothing. A staged revert refuses too (the server would commit it on admission).
     */
    private suspend fun preparePermissions(session: SessionId): Outcome<Unit> {
        val path = "/session/${sid(session)}"
        val read = json(path)
        if (read is Outcome.Failed) return read
        val policy =
            Oc2PermissionPolicy.fromSession((read as Outcome.Ok).value, session.raw)
                ?: return Failure.Refused(RefusalReason.ConsentMissing).failed()
        if (policy.reverted) return Failure.Refused(RefusalReason.UndoUnresolved).failed()
        val agentData =
            when (val agent = policy.agent) {
                null ->
                    when (val list = json("/agent", query = locationQuery())) {
                        is Outcome.Failed -> return list
                        is Outcome.Ok -> Oc2PermissionPolicy.defaultAgent(list.value)
                    }
                else ->
                    when (val one = readAgent(agent)) {
                        is Outcome.Failed -> return one
                        is Outcome.Ok -> data(one.value) as? JsonObject
                    }
            } ?: return Failure.Refused(RefusalReason.ConsentMissing).failed()
        val update =
            policy.updateForAgent(agentData, serverRuns = serverRunsTools(session))
                ?: return Failure.Refused(RefusalReason.ConsentMissing).failed()
        if (policy.confirms(update)) return Outcome.Ok(Unit)
        val patched = json(path, HttpMethod.Patch, update)
        if (patched is Outcome.Failed) return patched
        // A successful PATCH alone does not establish the mode: it is read back before anything can
        // run.
        val back = json(path)
        if (back is Outcome.Failed) return back
        val confirmed = Oc2PermissionPolicy.fromSession((back as Outcome.Ok).value, session.raw)
        if (confirmed == null || confirmed.agent != policy.agent || !confirmed.confirms(update))
            return Failure.Refused(RefusalReason.ConsentMissing).failed()
        if (confirmed.reverted) return Failure.Refused(RefusalReason.UndoUnresolved).failed()
        return Outcome.Ok(Unit)
    }

    /**
     * The server answers 404 for an existing agent for a moment after an idle restart; a short
     * retry rides it.
     */
    private suspend fun readAgent(agent: String): Outcome<JsonElement> {
        var attempt = 0
        while (true) {
            val result = json("/agent/${agent.encodeURLPathPart()}", query = locationQuery())
            if (result !is Outcome.Failed || result.failure !is Failure.NotFound || attempt >= 2)
                return result
            attempt++
            kotlinx.coroutines.delay(AGENT_BOOT_RETRY_MS)
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

    /**
     * `POST /session/{id}/shell {command}`: v2 runs it as the session's own agent (Flutter api2).
     */
    override suspend fun shell(
        session: SessionId,
        command: String,
        agent: String,
        model: ModelSelection?,
    ): Outcome<Unit> {
        val prepared = preparePermissions(session)
        if (prepared is Outcome.Failed) return prepared
        return unit(
            "/session/${sid(session)}/shell",
            HttpMethod.Post,
            buildJsonObject { put("command", command) },
        )
    }

    /**
     * `POST /session/{id}/command {command, text}` (Flutter `Api2Gateway.slashCommand`). The model
     * is session state on v2, so it never rides the body.
     */
    override suspend fun slashCommand(
        session: SessionId,
        command: String,
        args: String,
        model: ModelSelection?,
    ): Outcome<Unit> {
        val prepared = preparePermissions(session)
        if (prepared is Outcome.Failed) return prepared
        return unit(
            "/session/${sid(session)}/command",
            HttpMethod.Post,
            buildJsonObject {
                put("command", command.removePrefix("/"))
                put("text", args)
            },
        )
    }

    /** `GET /command`: the server's own slash commands, name order (Flutter chat_commands.dart). */
    override suspend fun commands(): Outcome<ImmutableList<CommandInfo>> =
        read("/command", query = locationQuery()) { root ->
            entries(root)
                .mapNotNull { value ->
                    val obj = value as? JsonObject ?: return@mapNotNull null
                    val name = obj.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    CommandInfo(name, obj.str("description"), obj.str("agent"), false)
                }
                .sortedBy { it.name }
                .toImmutableList()
        }

    override suspend fun todos(id: SessionId): Outcome<ImmutableList<Todo>> =
        unavailable("sessionTodos")

    override suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>> =
        unavailable("sessionDiff")

    // ---- Staged revert (P12-REVIEW; lib/api2/gateway_operations/ops_sessions.dart, protocol notes
    // "Revert") ----
    // POST .../revert/stage {messageID, files} → {"data": Revert}; POST .../revert/clear (stable:
    // DELETE
    // .../revert) and POST .../revert/commit, both 204, 409 SessionBusyError while running. One
    // request each.

    override suspend fun sessionRevertPrompt(
        session: SessionId,
        message: MessageId,
    ): Outcome<String?> =
        when (
            val read = json("/session/${sid(session)}/message/${message.raw.encodeURLPathPart()}")
        ) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val row = Oc2History(redactor).message(session.raw, data(read.value))
                if (row == null || row.info.id != message.raw || row.info.role != Role.User) {
                    Outcome.Ok(null)
                } else {
                    val text =
                        row.parts
                            .filter { it.type == PartType.Text && !it.synthetic }
                            .joinToString("\n") { it.text }
                    Outcome.Ok(
                        text.ifEmpty {
                            row.parts
                                .filter { it.type == PartType.File }
                                .mapNotNull { it.filename?.takeIf(String::isNotEmpty) }
                                .joinToString(", ")
                        }
                    )
                }
            }
        }

    override suspend fun stageSessionRevert(
        session: SessionId,
        message: MessageId,
        applyFiles: Boolean,
    ): Outcome<SessionRevert> =
        read(
            "/session/${sid(session)}/revert/stage",
            HttpMethod.Post,
            buildJsonObject {
                put("messageID", message.raw)
                put("files", applyFiles)
            },
        ) { root ->
            Oc2SessionRevert.fromJson(data(root))?.let(::revertOf)
        }

    override suspend fun clearSessionRevert(session: SessionId): Outcome<Unit> =
        unit("/session/${sid(session)}/revert/clear", HttpMethod.Post)

    override suspend fun commitSessionRevert(session: SessionId): Outcome<Unit> =
        unit("/session/${sid(session)}/revert/commit", HttpMethod.Post)

    private fun revertOf(wire: Oc2SessionRevert): SessionRevert =
        SessionRevert(
            MessageId(wire.messageID),
            wire.partID?.let(::PartId),
            wire.snapshot,
            wire.files
                ?.mapNotNull { file ->
                    val path =
                        (file["file"] ?: file["path"])
                            ?.let { (it as? JsonPrimitive)?.contentOrNull }
                            ?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    fun text(key: String) =
                        (file[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    fun count(key: String) =
                        (file[key] as? JsonPrimitive)
                            ?.takeIf { !it.isString }
                            ?.intOrNull
                            ?.takeIf { it >= 0 }
                    FileDiff(
                        path,
                        text("before"),
                        text("after"),
                        text("patch"),
                        count("additions"),
                        count("deletions"),
                        text("status"),
                    )
                }
                ?.toImmutableList(),
        )

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

    /**
     * Each service panel keeps its own location; it never moves the connection's shared project.
     */
    override fun managedShellsAt(location: ServerLocation): ManagedShellGateway =
        Oc2ManagedShells(this, location)

    override suspend fun startManagedShell(
        command: String,
        directory: String,
        ownerToken: String,
    ): Outcome<ManagedShell> =
        managedShellsAt(location.value).startManagedShell(command, directory, ownerToken)

    override suspend fun runningShells(): Outcome<ManagedShellList> =
        managedShellsAt(location.value).runningShells()

    override suspend fun managedShell(id: String): Outcome<ManagedShell?> =
        managedShellsAt(location.value).managedShell(id)

    override suspend fun managedShellOutput(
        id: String,
        cursor: Long,
        limit: Int,
    ): Outcome<ManagedShellOutput> =
        managedShellsAt(location.value).managedShellOutput(id, cursor, limit)

    override suspend fun stopManagedShell(id: String): Outcome<Unit> =
        managedShellsAt(location.value).stopManagedShell(id)

    override suspend fun setManagedShellTimeout(
        id: String,
        timeout: kotlin.time.Duration?,
    ): Outcome<ManagedShell> = managedShellsAt(location.value).setManagedShellTimeout(id, timeout)

    override suspend fun managedShellServerIdentity(): Outcome<String?> =
        managedShellsAt(location.value).managedShellServerIdentity()

    private fun locationQuery(at: ServerLocation = location.value): Map<String, String?> =
        mapOf(
            "location[directory]" to at.directory,
            "location[workspace]" to at.workspace,
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
    override suspend fun integrations(): Outcome<ImmutableList<IntegrationInfo>> =
        integrationOps.list()

    override suspend fun connectIntegrationKey(
        id: String,
        key: String,
        label: String?,
    ): Outcome<Unit> = integrationOps.connectKey(id, key, label)

    override suspend fun disconnectIntegration(integration: IntegrationInfo): Outcome<Unit> =
        integrationOps.disconnect(integration)

    override suspend fun startIntegrationOAuth(
        id: String,
        methodId: String,
        inputs: ImmutableMap<String, String>,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> = integrationOps.start(id, methodId, inputs, label)

    override suspend fun integrationOAuthStatus(attemptId: String): Outcome<IntegrationAuthStatus> =
        integrationOps.status(attemptId)

    override suspend fun completeIntegrationOAuth(attemptId: String, code: String?): Outcome<Unit> =
        integrationOps.complete(attemptId, code)

    override suspend fun cancelIntegrationOAuth(attemptId: String): Outcome<Unit> =
        integrationOps.cancel(attemptId)

    override suspend fun startIntegrationCommand(
        integrationId: String,
        methodId: String,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> = integrationOps.startCommand(integrationId, methodId, label)

    override suspend fun integrationCommandStatus(
        integrationId: String,
        attemptId: String,
    ): Outcome<IntegrationAuthStatus> = integrationOps.commandStatus(integrationId, attemptId)

    override suspend fun cancelIntegrationCommand(
        integrationId: String,
        attemptId: String,
    ): Outcome<Unit> = integrationOps.cancelCommand(integrationId, attemptId)

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

    /**
     * Safe catalog rows only (GET /provider + /model + /agent, all confirmed 200); no raw provider
     * config is kept.
     */
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

    /**
     * GET /model/default only; there is no confirmed default-agent endpoint, so the agent stays
     * null.
     */
    override suspend fun chatDefaults(): Outcome<ChatDefaults> {
        val reply = json("/model/default", query = locationQuery())
        if (reply is Outcome.Failed) return reply
        val raw = data((reply as Outcome.Ok).value)
        if (raw == JsonNull) return ChatDefaults(null, null).ok()
        val info =
            (raw as? JsonObject)?.let { Oc2ModelInfo.fromJson(it) }
                ?: return Failure.UnreadableReply().failed()
        val ref =
            Oc2SessionSelection.defaultModelRef(info) ?: return Failure.UnreadableReply().failed()
        return ChatDefaults(ref, null).ok()
    }

    /**
     * GET /fs/list (lib/api2/client.dart fsList): `{path, type}` entries, folders with a trailing
     * `/`.
     */
    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> =
        listFiles(path, location.value)

    /**
     * The files of one conversation's folder: every request carries [at], never the shared
     * location.
     */
    override fun filesAt(location: ServerLocation): FileGateway =
        object : FileGateway {
            override suspend fun listFiles(path: String) = listFiles(path, location)

            override suspend fun fileContent(path: String) = fileContent(path, location)

            override suspend fun findFile(query: String) = findFile(query, location)

            override suspend fun findText(pattern: String) = this@Oc2Gateway.findText(pattern)
        }

    private suspend fun listFiles(
        path: String,
        at: ServerLocation,
    ): Outcome<ImmutableList<FileNode>> =
        read("/fs/list", query = locationQuery(at) + ("path" to path.ifEmpty { "." })) { reply ->
            entries(reply)
                .map { entry ->
                    val obj = entry as? JsonObject ?: return@read null
                    val raw = obj.str("path") ?: return@read null
                    val normal = raw.removeSuffix("/")
                    FileNode(
                        name = normal.substringAfterLast('/'),
                        path = normal,
                        isDir = obj.str("type") == "directory" || raw.endsWith("/"),
                    )
                }
                .toImmutableList()
        }

    /**
     * GET /fs/read/<path> (raw bytes, no JSON envelope). Text only when the bytes are strict UTF-8
     * without a NUL in the first 8 KiB; anything else stays exact as base64 (B-MEDIA-001).
     */
    override suspend fun fileContent(path: String): Outcome<FileContent> =
        fileContent(path, location.value)

    private suspend fun fileContent(path: String, at: ServerLocation): Outcome<FileContent> {
        val bytes =
            when (
                val reply =
                    rawBytes(
                        "/fs/read/" + path.split('/').joinToString("/") { it.encodeURLPathPart() },
                        at,
                    )
            ) {
                is Outcome.Failed -> return reply
                is Outcome.Ok -> reply.value
            }
        val sniff = minOf(bytes.size, 8192)
        var binary = false
        for (i in 0 until sniff) if (bytes[i] == 0.toByte()) binary = true
        val text =
            if (binary) null
            else
                try {
                    bytes.decodeToString(throwOnInvalidSequence = true)
                } catch (_: CharacterCodingException) {
                    null
                }
        return if (text != null) FileContent(text, "text", null, null).ok()
        else FileContent(Base64.encode(bytes), "binary", "base64", null).ok()
    }

    /** `GET /fs/find?query=&type=file` (Flutter `Api2Gateway.findFile`): project-relative paths. */
    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> =
        findFile(query, location.value)

    private suspend fun findFile(
        query: String,
        at: ServerLocation,
    ): Outcome<ImmutableList<String>> =
        read(
            "/fs/find",
            query = locationQuery(at) + mapOf("query" to query, "type" to "file"),
        ) { root ->
            entries(root)
                .mapNotNull { value ->
                    val obj = value as? JsonObject ?: return@mapNotNull null
                    obj.str("path")?.takeIf { obj.str("type") != "directory" }
                }
                .toImmutableList()
        }

    /**
     * `GET /vcs/diff?mode=working` (protocol notes "VCS"): the folder's uncommitted changes vs
     * HEAD, `{file, patch, additions, deletions, status}` per file, for the conversation's own
     * [location].
     */
    override suspend fun workingTreeAt(location: ServerLocation): Outcome<ImmutableList<FileDiff>> =
        read("/vcs/diff", query = locationQuery(location) + ("mode" to "working")) { root ->
            entries(root)
                .mapNotNull { value ->
                    val obj = value as? JsonObject ?: return@mapNotNull null
                    val path =
                        (obj.str("file") ?: obj.str("path"))?.takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                    fun count(key: String) =
                        (obj[key] as? JsonPrimitive)
                            ?.takeIf { !it.isString }
                            ?.intOrNull
                            ?.takeIf { it >= 0 }
                    FileDiff(
                        path,
                        null,
                        null,
                        obj.str("patch"),
                        count("additions"),
                        count("deletions"),
                        obj.str("status"),
                    )
                }
                .toImmutableList()
        }

    /** OpenCode 2 has no workspace text search (capability textSearch stays false, as in Dart). */
    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> =
        unavailable("textSearch")

    /** One GET whose body is raw bytes, through the same dialect routing as [json]. */
    private suspend fun rawBytes(
        path: String,
        at: ServerLocation = location.value,
    ): Outcome<ByteArray> {
        if (dialect.value == null) {
            when (val health = health()) {
                is Outcome.Failed -> return health
                is Outcome.Ok ->
                    if (!health.value.healthy) return Failure.UnreadableReply().failed()
            }
        }
        val settled = dialect.value ?: return Failure.UnreadableReply().failed()
        val route = settled.route(path, HttpMethod.Get, null, locationQuery(at))
        route.removed?.let {
            return Failure.Unavailable(FeatureId(it)).failed()
        }
        val response =
            net.exchange({ request(route.path, route.method, route.query) }) { reply ->
                reply.bodyAsBytes()
            }
        if (response is Outcome.Failed) {
            val failure = response.failure
            if (
                auth == null &&
                    failure is Failure.SignIn &&
                    failure.why == SignInWhy.PasswordRejected
            ) {
                return Failure.SignIn(SignInWhy.PasswordMissing, failure.details).failed()
            }
        }
        return response
    }

    // ---- Stable-line features and operations (new files Oc2Operations / Oc2WebSearch /
    // Oc2HistoryMigration /
    // Oc2Terminal; Flutter lib/api2/gateway_operations). Each delegates; the request shapes live
    // there.

    override suspend fun initializeGitRepository(): Outcome<Unit> = gitInit()

    override suspend fun createWorktree(
        projectDirectory: String,
        name: String?,
    ): Outcome<WorktreeInfo> = worktreeCreate(projectDirectory, name)

    override suspend fun savedPermissions(): Outcome<ImmutableList<SavedPermission>> = savedGrants()

    override suspend fun removeSavedPermission(id: String): Outcome<Unit> = revokeGrant(id)

    override suspend fun listTerminals(): Outcome<ImmutableList<TerminalProcess>> = ptyList()

    override suspend fun createTerminal(title: String?): Outcome<TerminalProcess> = ptyCreate(title)

    override suspend fun createCommandTerminal(
        command: String,
        arguments: ImmutableList<String>,
        title: String?,
    ): Outcome<TerminalProcess> = ptyCreate(title, command, arguments)

    override suspend fun renameTerminal(id: String, title: String): Outcome<Unit> =
        ptyRename(id, title)

    override suspend fun resizeTerminal(id: String, rows: Int, cols: Int): Outcome<Unit> =
        ptyResize(id, rows, cols)

    override suspend fun removeTerminal(id: String): Outcome<Unit> = ptyRemove(id)

    override suspend fun connectTerminal(id: String, cursor: Long?): Outcome<TerminalChannel> =
        ptyConnect(id, cursor)

    override suspend fun terminalShellSettings(): Outcome<TerminalShellSettings> = shellSettings()

    override suspend fun selectTerminalShell(value: String): Outcome<Unit> = selectShell(value)

    override suspend fun readSetupConfig(location: ServerLocation): Outcome<JsonElement> =
        inspectConfig(location, redactor)

    override suspend fun readSetupMcp(location: ServerLocation): Outcome<JsonElement> =
        inspectMcp(location)

    override suspend fun readPluginInventory(location: ServerLocation): Outcome<JsonElement> =
        inspectPlugins(location)

    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> =
        mcpReadServers(redactor)

    override suspend fun mcpResources(): Outcome<ImmutableList<McpResourceInfo>> =
        mcpReadResources(redactor)

    override suspend fun connectMcp(name: String): Outcome<Unit> =
        mcpRuntimeAction(name, "connect", redactor)

    override suspend fun disconnectMcp(name: String): Outcome<Unit> =
        mcpRuntimeAction(name, "disconnect", redactor)

    override suspend fun removeMcpServer(name: String): Outcome<Unit> =
        mcpRuntimeAction(name, "remove", redactor)

    override suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> =
        mcpAdd(draft, scope)

    override suspend fun webSearchProviders(): Outcome<ImmutableList<WebSearchProvider>> =
        searchProviders()

    override suspend fun searchWeb(query: String, providerId: String): Outcome<WebSearchResponse> =
        search(query, providerId)

    /**
     * B-PROTO-063: `GET /experimental/migration/v1`; null when the server has nothing to report.
     */
    suspend fun historyMigrationStatus(): Outcome<HistoryMigrationStatus?> =
        historyMigrationStatusRead()

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
            stagedRevert = wire.revert?.let(::revertOf),
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
                scheduledPrompts =
                    true, // Flutter api2 parity (gateway_mappers.dart): schedules run client-side
                // over the normal session API
                phoneTeamExecution = false,
                genUi = false,
                sessionAddressHandoff = false,
                agentHandoffFiles = false,
                setupConfigRead = true,
                setupConfigWrite = false,
                setupMcpInventory = true,
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
                offlinePromptQueue = true,
                fileBrowsing = true,
                terminal = true,
                conversationShell = true,
                projectManagement = false,
                globalSessionSearch = false,
                sessionDiff = false,
                sessionFork = false,
                sessionCompact = false,
                persistentPermissionGrants = false,
                permissionRequests = true,
                savedPermissionList = true,
                messageCompletionEndsRun = false,
                sessionRevert = true,
                sessionImportExport = false,
                sessionNotes = false,
                serverCatalog = true,
                sessionModelProviderSwitching = true,
                agentSelection = true,
                slashCommands = true,
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
                mcpRuntimeAdds = true,
                mcpRuntimeRemovals = true,
                integrationCredentials = true,
                integrationCommandAuth = true,
                pluginInventory = true,
                webSearch = true,
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
                shellSettings = true,
                developmentServices = true,
                remoteUpgrade = false,
                clientDiagnostics = false,
                gitInit = true,
                providerRuntimeRefresh = false,
                configuredProviderFallback = false,
                globalEventStream = true,
                worktreeReset = false,
                worktreeCreate = true,
                worktreeBrowsing = false,
                legacyQuestionRequests = false,
                forms = false,
                inbox = false,
                cliSessionResume =
                    true, // Flutter api2: opencode2 --session resumes by id (P12-BUILD-AGENTS)
                projectsAreGitRepositories = false,
                remoteShell = false,
                claudeAgent = false,
            )
    }
}

private const val AGENT_BOOT_RETRY_MS = 500L
