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
import kotlinx.coroutines.withTimeoutOrNull
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
    private val requireDispatchAdmission: Boolean = false,
) :
    ServerGateway,
    ServerOperationsGateway,
    TerminalCommandGateway,
    CorrelatedPromptGateway,
    SessionSelectionGateway,
    StagedRevertGateway,
    SessionReadStateGateway,
    LocatedFileGateway,
    LocatedVcsGateway,
    LocatedManagedShellGateway,
    WebSearchGateway,
    SessionPermissionModeGateway,
    SessionTransferGateway,
    SessionNotesGateway,
    SetupInspectionGateway,
    LibraryGateway,
    SessionDispatchAdmissionGateway,
    SessionTransferImportGateway,
    SessionSkillGateway,
    SessionNoteGateway,
    McpRemovalGateway,
    IntegrationCredentialGateway,
    IntegrationCommandGateway,
    IntegrationAuthRecoveryGateway,
    ApiRouteGateway,
    SessionCreationGateway,
    WorktreeInspectionGateway,
    ConfigurationGateway,
    FencedSessionReadStateGateway,
    FencedSlashCommandGateway,
    FencedNewSessionPromptGateway,
    PromptRunIdentityGateway,
    ServerInspectionGateway {
    // AGENT-2: worktree inspection reads (WorktreeInspectionGateway). Its MCP calls are served once
    // by PROTO's Oc2Library and SEC-2's integrations below.
    private val inspection = Oc2WorktreeInspection(this)

    // STATE: the safe setup summary of the current location (beside PROTO's pinned inspector
    // below).
    override suspend fun inspectSetup(): Outcome<SetupInspection> =
        Oc2SetupSnapshot.read(this, redactor)

    private val transfer by lazy {
        Oc2SessionTransfer(this, { location.value }) { feature ->
            val caps = capabilities as MutableStateFlow<ServerCapabilities>
            caps.value =
                when (feature) {
                    "sessionNotes" -> caps.value.copy(sessionNotes = false)
                    else -> caps.value.copy(sessionImportExport = false)
                }
        }
    }

    override suspend fun exportSession(session: SessionId, sanitize: Boolean): Outcome<ByteArray> =
        transfer.exportSession(session, sanitize)

    override suspend fun importSession(body: JsonObject): Outcome<Session> =
        transfer.importSession(body)

    override suspend fun loadSessionNote(session: SessionId): Outcome<String?> =
        transfer.loadSessionNote(session)

    override suspend fun saveSessionNote(
        session: SessionId,
        text: String,
    ): Outcome<SessionNoteSaveResult> = transfer.saveSessionNote(session, text)

    override suspend fun removeSessionNote(session: SessionId): Outcome<Unit> =
        transfer.removeSessionNote(session)

    override fun setupInspection(location: ServerLocation): SetupInspector =
        Oc2SetupInspection(this, redactor, location)

    private val signIns by lazy { Oc2ProviderSignIn(this, { location.value }, redactor) }

    override suspend fun integrations(): Outcome<ImmutableList<IntegrationInfo>> =
        signIns.integrations()

    override suspend fun connectIntegrationKey(
        id: String,
        key: String,
        label: String?,
    ): Outcome<Unit> = signIns.connectIntegrationKey(id, key, label)

    override suspend fun disconnectIntegration(integration: IntegrationInfo): Outcome<Unit> =
        signIns.disconnectIntegration(integration)

    override suspend fun startIntegrationOAuth(
        id: String,
        methodId: String,
        inputs: ImmutableMap<String, String>,
        label: String?,
    ): Outcome<IntegrationAuthLaunch> = signIns.startIntegrationOAuth(id, methodId, inputs, label)

    override suspend fun integrationOAuthStatus(attemptId: String): Outcome<IntegrationAuthStatus> =
        signIns.integrationOAuthStatus(attemptId)

    override suspend fun completeIntegrationOAuth(attemptId: String, code: String?): Outcome<Unit> =
        signIns.completeIntegrationOAuth(attemptId, code)

    override suspend fun cancelIntegrationOAuth(attemptId: String): Outcome<Unit> =
        signIns.cancelIntegrationOAuth(attemptId)

    private var skillsSupported = true

    private fun library() = Oc2Library(this, location.value, redactor)

    override suspend fun plugins(): Outcome<ImmutableList<LibraryPlugin>> = library().plugins()

    override suspend fun skills(): Outcome<ImmutableList<SkillInfo>> = library().skills()

    override suspend fun mcpServers(): Outcome<ImmutableList<McpServerInfo>> =
        library().mcpServers()

    override suspend fun disconnectMcp(name: String): Outcome<Unit> = library().disconnectMcp(name)

    override suspend fun connectMcp(name: String): Outcome<Unit> = library().connectMcp(name)

    override suspend fun addMcpServer(draft: McpServerDraft, scope: McpConfigScope): Outcome<Unit> =
        library().addMcpServer(draft, scope)

    override suspend fun activateSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
    ): Outcome<Unit> {
        if (!skillsSupported) return Failure.Unavailable(FeatureId("sessionSkills")).failed()
        val result = library().activateSkill(session, skillId, resume)
        if (result is Outcome.Failed && result.failure is Failure.Unavailable)
            skillsSupported = false
        return result
    }

    private fun terminals() =
        Oc2Terminals(this, location.value) { id, cursor, at -> connectTerminalAt(id, cursor, at) }

    override suspend fun listTerminals(): Outcome<ImmutableList<TerminalProcess>> =
        terminals().listTerminals()

    override suspend fun createTerminal(title: String?): Outcome<TerminalProcess> =
        terminals().createTerminal(title)

    override suspend fun createCommandTerminal(
        command: String,
        arguments: List<String>,
        title: String,
    ): Outcome<TerminalProcess> = terminals().createCommandTerminal(command, arguments, title)

    override suspend fun rerunTerminalCommand(
        ended: TerminalProcess,
        location: ServerLocation,
    ): Outcome<TerminalProcess> = terminals().rerunTerminalCommand(ended, location)

    override suspend fun renameTerminal(id: String, title: String): Outcome<Unit> =
        terminals().renameTerminal(id, title)

    override suspend fun resizeTerminal(id: String, rows: Int, cols: Int): Outcome<Unit> =
        terminals().resizeTerminal(id, rows, cols)

    override suspend fun removeTerminal(id: String): Outcome<Unit> = terminals().removeTerminal(id)

    override suspend fun connectTerminal(id: String, cursor: Long?): Outcome<TerminalChannel> {
        val scope = terminalScope ?: return Failure.Unavailable(FeatureId("terminal")).failed()
        val opened = terminals().connectTerminal(id, cursor)
        if (opened is Outcome.Failed) return opened
        val channel = (opened as Outcome.Ok).value
        // A screen that closes its channel releases the gateway's hold on it too, so reconnects
        // do not accumulate closed sockets for the connection's lifetime; close() ends the rest.
        val tracked =
            object : TerminalChannel by channel {
                override suspend fun close() {
                    terminalLane.withLock { terminalChannels.remove(this) }
                    channel.close()
                }
            }
        val admitted =
            terminalLane.withLock {
                if (terminalScope !== scope) false
                else {
                    terminalChannels.add(tracked)
                    true
                }
            }
        if (!admitted) {
            channel.close()
            return Failure.Refused(RefusalReason.StaleScope).failed()
        }
        return tracked.ok()
    }

    internal fun terminalRedact(value: String): String = redactor.redact(value)

    internal fun terminalRegisterSecret(value: String) {
        redactor.registerSecret(value)
    }

    internal val terminalNet: NetClient
        get() = net

    internal val terminalOrigin: ServerOrigin
        get() = origin

    internal val terminalAuthHeaders: Map<String, String>
        get() = auth?.let { mapOf(HttpHeaders.Authorization to it) } ?: emptyMap()

    internal val terminalClock: Clock
        get() = clock

    internal var terminalScope: kotlinx.coroutines.CoroutineScope? = null
        private set

    internal var terminalDispatchers: AppDispatchers? = null
        private set

    private data class DispatchAdmission(
        val enter: suspend (SessionId) -> Outcome<Unit>,
        val settle: (SessionId) -> Unit,
    )

    private val dispatchAdmission = MutableStateFlow<DispatchAdmission?>(null)

    override fun setSessionDispatchAdmission(
        admit: suspend (SessionId) -> Outcome<Unit>,
        settled: (SessionId) -> Unit,
    ) {
        dispatchAdmission.value = DispatchAdmission(admit, settled)
    }

    private suspend fun <T> admitted(
        session: SessionId,
        action: suspend () -> Outcome<T>,
    ): Outcome<T> {
        // The phone-server owner installs its admission right after the graph goes live; a send
        // in that gap waits briefly for it instead of failing.
        val admission =
            dispatchAdmission.value
                ?: if (requireDispatchAdmission)
                    withTimeoutOrNull(ADMISSION_WAIT_MS) {
                        dispatchAdmission.filterNotNull().first()
                    } ?: return Failure.Refused(RefusalReason.ConsentMissing).failed()
                else return action()
        val accepted = admission.enter(session)
        if (accepted is Outcome.Failed) return accepted
        return try {
            action()
        } finally {
            admission.settle(session)
        }
    }

    private val worktrees = Oc2Worktrees(this)

    override suspend fun listProjects(): Outcome<ImmutableList<WorkspaceProject>> =
        worktrees.projects()

    override suspend fun currentProject(): Outcome<WorkspaceProject?> = worktrees.current()

    override suspend fun listProjectDirectories(
        projectId: String
    ): Outcome<ImmutableList<ProjectDirectoryInfo>> = worktrees.directories(projectId)

    override suspend fun listWorktrees(
        projectDirectory: String,
        projectId: String?,
    ): Outcome<ImmutableList<WorktreeInfo>> = worktrees.list(projectDirectory, projectId)

    override suspend fun removeWorktree(
        projectDirectory: String,
        directory: String,
    ): Outcome<Unit> = worktrees.remove(projectDirectory, directory)

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
    override suspend fun syncSessionPermissions(session: SessionId): Outcome<Unit> =
        preparePermissions(session)

    override fun setAutomaticApprovalResolver(resolver: (SessionId) -> Boolean) {
        serverRunsTools = resolver
    }

    private val mutableCapabilities = MutableStateFlow(Capabilities)
    override val capabilities: StateFlow<ServerCapabilities> = mutableCapabilities.asStateFlow()
    override val location = MutableStateFlow(initialLocation)
    private val status = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
    private val connects = MutableStateFlow(0)
    private val envelopes = MutableSharedFlow<GatewayEnvelope>()
    private var loop: EventStreamLoop? = null
    private var stream: Job? = null
    private val terminalChannels = mutableSetOf<TerminalChannel>()
    private val terminalLane = Mutex()

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

    private var locationRevision = 0L
    override val scopeRevision: Long
        get() = (locationRevision shl 32) + connects.value

    override fun setLocation(location: ServerLocation) {
        if (this.location.value != location) locationRevision++
        this.location.value = location
    }

    internal fun start(
        spec: GatewaySpec,
        dispatchers: AppDispatchers,
        network: NetworkStatus = UnknownNetworkStatus,
    ) {
        terminalScope = spec.scope
        terminalDispatchers = dispatchers
        val connector = KtorSseConnector(net.http) { request("/event", HttpMethod.Get) }
        val live =
            EventStreamLoop(
                connector,
                BackoffPolicy(500, 16_000, true, null, stallTimeoutMs = 45_000),
                AddressRules.classify(origin.host),
                NetworkGate(network),
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
        headers: Map<String, String> = emptyMap(),
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
        return rawJson(route.path, route.method, route.body, route.query, headers).map {
            settled.response(verb, path, it)
        }
    }

    /** Complete response bytes; dialect is already settled before this read-only download. */
    internal suspend fun rawBytes(
        path: String,
        query: Map<String, String?> = emptyMap(),
    ): Outcome<ByteArray> {
        if (dialect.value == null) {
            val health = health()
            if (health is Outcome.Failed) return health
        }
        val settled = dialect.value ?: return Failure.UnreadableReply().failed()
        val route = settled.route(path, HttpMethod.Get, null, query)
        // dialect.dart Api2RemovedInStable: fail clearly, nothing is sent.
        route.removed?.let {
            return Failure.Unavailable(FeatureId(it)).failed()
        }
        return net.exchange(
            { request(route.path, route.method, route.query) },
            readFailure = ::readFailure,
        ) {
            it.bodyAsBytes()
        }
    }

    private suspend fun rawJson(
        path: String,
        verb: HttpMethod = HttpMethod.Get,
        body: JsonObject? = null,
        query: Map<String, String?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): Outcome<JsonElement> {
        val response =
            net.exchange(
                {
                    request(path, verb, query)
                    headers.forEach { (name, value) -> header(name, value) }
                    header(HttpHeaders.Accept, "application/json")
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body.toString())
                    }
                },
                readFailure = ::readFailure,
            ) { reply ->
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

    /**
     * Only symbolic tags and documented bounded fields survive error envelopes; server prose is
     * discarded.
     */
    private suspend fun readFailure(reply: io.ktor.client.statement.HttpResponse): Failure? {
        val status = reply.status.value
        val fallback = StatusMapping.failureFor(status, auth != null)
        if (status == 401 || status == 403) return fallback
        val bytes = reply.bodyAsBytes()
        if (bytes.size > 65_536) return fallback
        val obj = Oc2Wire.parse(bytes.decodeToString()) as? JsonObject ?: return fallback
        val text = obj.str("_tag") ?: obj.str("code")
        val tag =
            text
                ?.takeIf { it.length <= 100 && it.all { c -> c.isLetterOrDigit() || c == '_' } }
                ?.let(::WireTag)
        val extras =
            buildMap<String, String> {
                    (obj["maxBytes"] as? JsonPrimitive)
                        ?.intOrNull
                        ?.takeIf { it > 0 }
                        ?.let { put("maxBytes", it.toString()) }
                }
                .toImmutableMap()
        return when (fallback) {
            is Failure.NotFound -> fallback.copy(tag = tag)
            is Failure.Conflict -> fallback.copy(tag = tag)
            is Failure.Server -> fallback.copy(tag = tag)
            is Failure.Rejected -> fallback.copy(tag = tag, extras = WireExtras(extras))
            is Failure.Busy ->
                fallback.copy(
                    tag = tag,
                    kind =
                        when (tag?.raw) {
                            "service_starting" -> BusyKind.Starting
                            "service_stopping" -> BusyKind.Stopping
                            "service_failed" -> BusyKind.Failed
                            else -> fallback.kind
                        },
                )
            else -> fallback
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

    /** B-STATE-113: only mobile.note crosses the protocol boundary, in the captured location. */
    override suspend fun loadSessionNote(
        session: SessionId,
        location: ServerLocation,
    ): Outcome<String?> {
        val result =
            noteRequest("/session/${sid(session)}/instructions/entries", HttpMethod.Get, location)
        if (result is Outcome.Failed) return result
        val rows =
            ((result as Outcome.Ok).value as? JsonObject)?.get("data") as? JsonArray
                ?: return Failure.UnreadableReply().failed()
        val owned =
            rows.filterIsInstance<JsonObject>().filter { it["key"] == JsonPrimitive("mobile.note") }
        if (owned.isEmpty()) return Outcome.Ok(null)
        val value = owned.singleOrNull()?.get("value") as? JsonPrimitive
        return if (value?.isString == true) Outcome.Ok(value.content)
        else Failure.UnreadableReply().failed()
    }

    override suspend fun saveSessionNote(
        session: SessionId,
        location: ServerLocation,
        value: String?,
        beforeSend: () -> Boolean,
    ): Outcome<Unit> {
        if (value != null && JsonPrimitive(value).toString().encodeToByteArray().size > 8192)
            return Failure.Rejected(413).failed()
        // Negotiate before the final admission check; a wake must not redirect this mutation.
        if (dialect.value == null) {
            val ready = health()
            if (ready is Outcome.Failed) return ready
        }
        if (!beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
        return noteRequest(
                "/session/${sid(session)}/instructions/entries/mobile.note",
                if (value == null) HttpMethod.Delete else HttpMethod.Put,
                location,
                value?.let { buildJsonObject { put("value", it) } },
            )
            .map {}
    }

    private suspend fun noteRequest(
        path: String,
        method: HttpMethod,
        at: ServerLocation,
        body: JsonObject? = null,
    ): Outcome<JsonElement> {
        val result = json(path, method, body, locationQuery(at))
        val failure = (result as? Outcome.Failed)?.failure
        val unsupported =
            (failure is Failure.NotFound && failure.tag?.raw != "SessionNotFoundError") ||
                (failure is Failure.Rejected && failure.status in listOf(405, 501)) ||
                (failure is Failure.Server && failure.status == 501)
        if (unsupported) {
            (capabilities as MutableStateFlow<ServerCapabilities>).update {
                it.copy(sessionNotes = false)
            }
            return Failure.Unavailable(FeatureId("sessionNotes")).failed()
        }
        return result
    }

    @kotlin.concurrent.Volatile private var skillsAvailable = true

    override val sessionSkillsAvailable: Boolean
        get() = skillsAvailable

    override suspend fun sessionSkills(): Outcome<ImmutableList<SkillInfo>> =
        Oc2SessionSkills.list(this, locationQuery())

    override suspend fun activateSessionSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
        beforeSend: () -> Boolean,
    ): Outcome<Unit> {
        if (!skillsAvailable) return Failure.Unavailable(FeatureId("sessionSkills")).failed()
        // Resolve the dialect before admission: a wake cannot redirect a skill into a changed
        // location.
        if (dialect.value == null) {
            val ready = health()
            if (ready is Outcome.Failed) return ready
        }
        if (!beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
        val result = Oc2SessionSkills.activate(this, session, skillId, resume)
        if (Oc2SessionSkills.unsupported(result)) {
            skillsAvailable = false
            return Failure.Unavailable(FeatureId("sessionSkills")).failed()
        }
        return result
    }

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

    override suspend fun inspectionProject(location: ServerLocation): Outcome<WorkspaceProject?> =
        inspection.inspectionProject(location)

    override suspend fun inspectionWorktrees(
        projectId: String
    ): Outcome<ImmutableList<WorktreeInfo>> = inspection.inspectionWorktrees(projectId)

    override suspend fun inspectionChanges(
        location: ServerLocation
    ): Outcome<ImmutableList<VersionControlFile>> = inspection.inspectionChanges(location)

    override suspend fun inspectionSessions(
        cursor: String?,
        limit: Int,
        includeArchived: Boolean,
    ): Outcome<ServerPage<Session>> = inspection.inspectionSessions(cursor, limit, includeArchived)

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

    override fun newSessionCreationId(): SessionId = SessionId(ids.session())

    override suspend fun createSessionWithId(
        id: SessionId,
        location: ServerLocation,
    ): Outcome<SessionId> {
        if (!id.raw.startsWith("ses_") || !Oc2ClientIds.isClientId(id.raw))
            return Failure.Rejected(400).failed()
        return when (
            val answer =
                json(
                    "/session",
                    HttpMethod.Post,
                    buildJsonObject {
                        put("id", id.raw)
                        if (location.directory != null || location.workspace != null)
                            put(
                                "location",
                                buildJsonObject {
                                    location.directory?.let { put("directory", it) }
                                    location.workspace?.let { put("workspaceID", it) }
                                },
                            )
                    },
                )
        ) {
            is Outcome.Failed -> answer
            is Outcome.Ok -> {
                val admitted = sessionValue(data(answer.value))?.id
                if (admitted == id) admitted.ok() else Failure.UnreadableReply().failed()
            }
        }
    }

    override suspend fun lookupCreatedSession(id: SessionId): Outcome<Boolean> {
        if (!id.raw.startsWith("ses_") || !Oc2ClientIds.isClientId(id.raw))
            return Failure.Rejected(400).failed()
        return when (val found = session(id)) {
            is Outcome.Ok -> (found.value.id == id).ok()
            is Outcome.Failed -> if (found.failure is Failure.NotFound) false.ok() else found
        }
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
        return admitted(session) { sendPreparedPrompt(session, prompt, clientId, beforeSend) }
    }

    private suspend fun sendPreparedPrompt(
        session: SessionId,
        prompt: OutgoingPrompt,
        clientId: String,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission> {
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
        return admitted(session) { sendPreparedShell(session, command) }
    }

    private suspend fun sendPreparedShell(session: SessionId, command: String): Outcome<Unit> {
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
    ): Outcome<Unit> = slashCommandFenced(session, command, args) { true }

    override suspend fun slashCommandFenced(
        session: SessionId,
        command: String,
        args: String,
        beforeSend: () -> Boolean,
    ): Outcome<Unit> {
        val at = location.value
        if (!beforeSend()) return Failure.Refused(RefusalReason.StaleScope).failed()
        return admitted(session) { sendPreparedCommand(session, command, args, at, beforeSend) }
    }

    private suspend fun sendPreparedCommand(
        session: SessionId,
        command: String,
        args: String,
        at: ServerLocation,
        beforeSend: () -> Boolean,
    ): Outcome<Unit> {
        val prepared = preparePermissions(session)
        if (prepared is Outcome.Failed) return prepared
        // Preparation negotiates the dialect and may suspend for policy installation/readback.
        // The destination and the caller's admission must still be the captured ones afterwards.
        if (location.value != at || !beforeSend())
            return Failure.Refused(RefusalReason.StaleScope).failed()
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

    // Migration 40: the native Tools and Providers pages use the same location-bound contracts as
    // Flutter. Mass merge: skills, the MCP list and connect/disconnect/add, and the sign-in methods
    // are
    // implemented once above (PROTO's Oc2ProviderSignIn and Oc2Library; mcpResources by SEC-2's
    // Oc2Integrations). Migration 40 adds what only it provides.
    private val libraryOps = Oc2LibraryOps(this, redactor)

    override suspend fun references() = libraryOps.references()

    override suspend fun removeMcpServer(name: String) = libraryOps.removeMcpServer(name)

    override suspend fun renameCredential(id: String, label: String) =
        libraryOps.renameCredential(id, label)

    override suspend fun activateCredential(id: String) = libraryOps.activateCredential(id)

    override suspend fun removeCredential(id: String) = libraryOps.removeCredential(id)

    override suspend fun startIntegrationCommand(
        integrationId: String,
        methodId: String,
        label: String?,
    ) = libraryOps.startIntegrationCommand(integrationId, methodId, label)

    override suspend fun integrationCommandStatus(integrationId: String, attemptId: String) =
        libraryOps.integrationCommandStatus(integrationId, attemptId)

    override suspend fun cancelIntegrationCommand(integrationId: String, attemptId: String) =
        libraryOps.cancelIntegrationCommand(integrationId, attemptId)

    override fun restoreIntegrationAuthAttempt(
        integrationId: String,
        attemptId: String,
        command: Boolean,
        directory: String?,
        workspace: String?,
    ) =
        libraryOps.restoreIntegrationAuthAttempt(
            integrationId,
            attemptId,
            command,
            directory,
            workspace,
        )

    override val sessionSkillsSupported: Boolean
        get() = libraryOps.sessionSkillsSupported

    override suspend fun activateSessionSkill(
        session: SessionId,
        skillId: String,
        resume: Boolean,
    ) = libraryOps.activateSessionSkill(session, skillId, resume)

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

    override suspend fun inbox(session: SessionId): Outcome<ImmutableList<InboxItem>> =
        Oc2Inbox.list(this, session)

    override suspend fun cancelInboxItem(session: SessionId, item: InboxItemId): Outcome<Unit> =
        Oc2Inbox.cancel(this, session, item)

    override suspend fun setInboxDelivery(
        session: SessionId,
        item: InboxItemId,
        delivery: InboxDelivery,
    ): Outcome<Unit> = Oc2Inbox.delivery(this, session, item, delivery)

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

    override suspend fun pendingForms(): Outcome<ImmutableList<FormSpec>> =
        read("/form/request", query = locationQuery(), decode = ::decodeForms)

    override suspend fun sessionForms(session: SessionId): Outcome<ImmutableList<FormSpec>> =
        read("/session/${sid(session)}/form", decode = ::decodeForms)

    /** Decode through the same projection as live form events, so schema identities agree. */
    private fun decodeForms(root: JsonElement): ImmutableList<FormSpec>? {
        val rows = data(root) as? JsonArray ?: return null
        val projection = Oc2CoreProjection(redactor::details)
        return rows
            .map { value ->
                val wire = (value as? JsonObject)?.let { Oc2FormInfo.fromJson(it) } ?: return null
                if (wire.id.isBlank() || wire.sessionID.isBlank()) return null
                (projection.toCore(Oc2Adapted.FormCreated(wire)) as GatewayEvent.FormCreated).form
            }
            .toImmutableList()
    }

    override suspend fun formState(session: SessionId, form: FormId): Outcome<FormState> =
        read("/session/${sid(session)}/form/${form.raw.encodeURLPathPart()}/state") { root ->
            val raw = data(root) as? JsonObject ?: return@read null
            val wire = Oc2FormState.fromJson(raw)
            FormState(
                status =
                    when (wire.status) {
                        Oc2FormStatus.Pending -> FormStatus.Pending
                        Oc2FormStatus.Answered -> FormStatus.Answered
                        Oc2FormStatus.Cancelled -> FormStatus.Cancelled
                        Oc2FormStatus.Unknown -> FormStatus.Unknown
                    },
                answer = wire.answer,
            )
        }

    override suspend fun replyForm(
        session: SessionId,
        form: FormId,
        answer: FormAnswer,
    ): Outcome<Unit> =
        unit(
            "/session/${sid(session)}/form/${form.raw.encodeURLPathPart()}/reply",
            HttpMethod.Post,
            buildJsonObject { put("answer", JsonObject(answer.values)) },
        )

    override suspend fun cancelForm(session: SessionId, form: FormId): Outcome<Unit> =
        unit(
            "/session/${sid(session)}/form/${form.raw.encodeURLPathPart()}/cancel",
            HttpMethod.Post,
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

    override suspend fun initializeGitRepository(): Outcome<Unit> =
        Oc2HostTools(this, location.value).initializeGit()

    override suspend fun createWorktree(
        projectDirectory: String,
        name: String?,
    ): Outcome<WorktreeInfo> =
        Oc2HostTools(this, location.value).createWorktree(projectDirectory, name)

    override suspend fun terminalShellSettings(): Outcome<TerminalShellSettings> =
        Oc2HostTools(this, location.value).shellSettings()

    override suspend fun selectTerminalShell(value: String): Outcome<Unit> =
        Oc2HostTools(this, location.value).selectShell(value)

    // AGENT-2: the setup review's raw config sources (SetupInspectionGateway, one interface).
    override suspend fun setupConfigSources(): Outcome<JsonObject> = readSetupSources(redactor)

    internal fun locationQuery(at: ServerLocation = location.value): Map<String, String?> =
        mapOf(
            "location[directory]" to at.directory,
            "location[workspace]" to at.workspace,
        )

    // SEC-2's integration reads. The sign-in and MCP overrides above (PROTO: Oc2ProviderSignIn,
    // Oc2Library) are the
    // one implementation of those methods; SEC-2 alone adds the MCP resource list.
    private val integrationOps = Oc2Integrations(this, { location.value }, redactor)

    override suspend fun mcpResources(): Outcome<ImmutableList<McpResourceInfo>> =
        integrationOps.mcpResources()

    override suspend fun providers(): Outcome<ProvidersResponse> {
        val scope = locationQuery()
        val providerReply = json("/provider", query = scope)
        if (providerReply is Outcome.Failed) return providerReply
        val providerJson = (providerReply as Outcome.Ok).value
        redactor.registerCredentialValues(providerJson)
        val providerPayload = data(providerJson)
        // Older provider inventories include their models and an explicit connected list. An empty
        // connected list is authoritative, never a reason to offer the catalog's models.
        if (
            providerPayload is JsonObject &&
                ("all" in providerPayload || "providers" in providerPayload)
        )
            return Oc2ProviderInventory.parse(providerPayload) ?: Failure.UnreadableReply().failed()
        val modelReply = json("/model", query = scope)
        if (modelReply is Outcome.Failed) return modelReply
        val defaultReply = json("/model/default", query = scope)
        if (defaultReply is Outcome.Failed) return defaultReply
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

    override suspend fun configurationPreview(
        location: ServerLocation
    ): Outcome<ConfigurationPreview> =
        read(
            "/config",
            query = locationQuery(location),
            decode = Oc2ConfigurationPreview(redactor)::read,
        )

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
        val scope = locationQuery()
        val providerReply = json("/provider", query = scope)
        if (providerReply is Outcome.Failed) return providerReply
        val modelReply = json("/model", query = scope)
        if (modelReply is Outcome.Failed) return modelReply
        val agentReply = json("/agent", query = scope)
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
        val downloaded =
            when (
                val reply =
                    rawFile(
                        "/fs/read/" + path.split('/').joinToString("/") { it.encodeURLPathPart() },
                        at,
                    )
            ) {
                is Outcome.Failed -> return reply
                is Outcome.Ok -> reply.value
            }
        val (bytes, mime) = downloaded
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
        return if (text != null) FileContent(text, "text", null, mime).ok()
        else FileContent(Base64.encode(bytes), "binary", "base64", mime).ok()
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

    /** Raw file download preserves the server MIME type alongside its complete response bytes. */
    private suspend fun rawFile(
        path: String,
        at: ServerLocation,
    ): Outcome<Pair<ByteArray, String?>> {
        if (dialect.value == null) {
            when (val checked = health()) {
                is Outcome.Failed -> return checked
                is Outcome.Ok ->
                    if (!checked.value.healthy) return Failure.UnreadableReply().failed()
            }
        }
        val settled = dialect.value ?: return Failure.UnreadableReply().failed()
        val route = settled.route(path, HttpMethod.Get, null, locationQuery(at))
        route.removed?.let {
            return Failure.Unavailable(FeatureId(it)).failed()
        }
        val response =
            net.exchange(
                { request(route.path, route.method, route.query) },
                readFailure = ::readFailure,
            ) { reply ->
                reply.bodyAsBytes() to
                    reply.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()
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

    /**
     * An optional `/api` route with its own origin and Basic credentials (no location query: its
     * routes name their session) (the active-context inspector, session export/import). Any status
     * is an answer; a mutation is one request and never retried.
     */
    override suspend fun apiRoute(
        method: String,
        path: String,
        jsonBody: String?,
    ): Outcome<ApiRouteReply> {
        val verb = HttpMethod.parse(method.uppercase())
        return net.exchangeAnswer({
            request(path, verb)
            header(HttpHeaders.Accept, "application/json")
            if (jsonBody != null) {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
        }) { reply ->
            ApiRouteReply(reply.status.value, reply.bodyAsText().takeIf { it.isNotEmpty() })
        }
    }

    override suspend fun savedPermissions(): Outcome<ImmutableList<SavedPermission>> = savedGrants()

    override suspend fun removeSavedPermission(id: String): Outcome<Unit> = revokeGrant(id)

    // ---- Stable-line features and operations (new files Oc2Operations / Oc2WebSearch /
    // Oc2HistoryMigration /
    // Oc2Terminal; Flutter lib/api2/gateway_operations). Each delegates; the request shapes live
    // there.

    override suspend fun readSetupConfig(location: ServerLocation): Outcome<JsonElement> =
        inspectConfig(location, redactor)

    override suspend fun readSetupMcp(location: ServerLocation): Outcome<JsonElement> =
        inspectMcp(location)

    override suspend fun readPluginInventory(location: ServerLocation): Outcome<JsonElement> =
        inspectPlugins(location)

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
        val channels = terminalLane.withLock {
            terminalScope = null
            terminalDispatchers = null
            terminalChannels.toList().also { terminalChannels.clear() }
        }
        try {
            channels.forEach { channel -> channel.close() }
            stream?.cancelAndJoin()
        } finally {
            stream = null
            net.close()
        }
    }

    /** Flutter ops_extras: source ids are retained; location is explicit and never a query. */
    override suspend fun importSessionDocument(body: JsonObject): Outcome<Session> {
        if (!capabilities.value.sessionImportExport) return unavailable("sessionImportExport")
        val info =
            body["info"] as? JsonObject
                ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val id = info.str("id") ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val target =
            body["location"] as? JsonObject
                ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val directory =
            target.str("directory")?.takeIf { it.isNotBlank() }
                ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val workspace = target.str("workspaceID")
        val response = json("/session/import", HttpMethod.Post, body)
        if (response is Outcome.Failed) {
            val f = response.failure
            val status =
                when (f) {
                    is Failure.Server -> f.status
                    is Failure.Rejected -> f.status
                    else -> null
                }
            if (
                status == 405 ||
                    status == 501 ||
                    (f is Failure.NotFound && f.tag?.raw != "SessionNotFoundError")
            ) {
                mutableCapabilities.update { it.copy(sessionImportExport = false) }
                return unavailable("sessionImportExport")
            }
            return response
        }
        val imported =
            sessionValue(data((response as Outcome.Ok).value))
                ?: return Failure.UnreadableReply().failed()
        if (
            imported.id.raw != id ||
                imported.directory != directory ||
                imported.workspaceId?.raw != workspace
        ) {
            return Failure.UnreadableReply().failed()
        }
        return imported.ok()
    }

    internal fun sessionValue(value: JsonElement): Session? {
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
                commandReceipts = true,
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
                sessionImportExport = true,
                sessionNotes = true,
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
                mcpChatConnect = true,
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
                worktreeInspection = true,
                legacyQuestionRequests = false,
                forms = true,
                inbox = true,
                cliSessionResume =
                    true, // Flutter api2: opencode2 --session resumes by id (P12-BUILD-AGENTS)
                projectsAreGitRepositories = false,
                remoteShell = false,
                claudeAgent = false,
            )
    }
}

private const val AGENT_BOOT_RETRY_MS = 500L
private const val ADMISSION_WAIT_MS = 5_000L
