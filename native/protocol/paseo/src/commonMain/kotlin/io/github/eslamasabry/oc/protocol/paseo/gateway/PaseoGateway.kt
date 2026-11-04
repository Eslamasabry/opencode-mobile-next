package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.CorrelatedPromptGateway
import io.github.eslamasabry.oc.core.domain.MidTurnPromptGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.WorkspaceTransferGateway
import io.github.eslamasabry.oc.core.domain.WorkspaceTransferRequest
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEnvelope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.GatewayEvents
import io.github.eslamasabry.oc.core.domain.events.HintKind
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.domain.unavailable
import io.github.eslamasabry.oc.core.model.AgentInfo
import io.github.eslamasabry.oc.core.model.ChatDefaults
import io.github.eslamasabry.oc.core.model.EventLocation
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PendingQuestion
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ProviderInfo
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.Todo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.longOrNull
import io.github.eslamasabry.oc.protocol.paseo.mappers.PaseoImages
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoImages
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoRecordBusyUntilMs
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoSession
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoSessionStatus
import io.github.eslamasabry.oc.protocol.paseo.mappers.paseoTimeline
import io.github.eslamasabry.oc.protocol.paseo.numberOrNull
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.paseoAttempt
import io.github.eslamasabry.oc.protocol.paseo.paseoCapabilities
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.paseoList
import io.github.eslamasabry.oc.protocol.paseo.paseoObject
import io.github.eslamasabry.oc.protocol.paseo.paseoReply
import io.github.eslamasabry.oc.protocol.paseo.paseoString
import io.github.eslamasabry.oc.protocol.paseo.requests.HostAgentCatalog
import io.github.eslamasabry.oc.protocol.paseo.requests.HostAgentContinuation
import io.github.eslamasabry.oc.protocol.paseo.requests.HostAgentPermissionRequest
import io.github.eslamasabry.oc.protocol.paseo.requests.PaseoPermissionAnswer
import io.github.eslamasabry.oc.protocol.paseo.requests.PaseoQuestionSignal
import io.github.eslamasabry.oc.protocol.paseo.requests.PaseoRequestBook
import io.github.eslamasabry.oc.protocol.paseo.requests.PaseoRequestHost
import io.github.eslamasabry.oc.protocol.paseo.requests.isPaseoProviderId
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoHostAgentCatalog
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoHostAgentContinuation
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoHostAgentName
import io.github.eslamasabry.oc.protocol.paseo.requests.paseoProviderCanStart
import io.github.eslamasabry.oc.protocol.paseo.requests.toPaseo
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoPush
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransportListener
import io.github.eslamasabry.oc.protocol.paseo.work.PaseoWorkInventory
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The Paseo gateway (protocol 1), a port of `lib/paseo/gateway.dart` and its parts `sessions`,
 * `prompts`, `events`, `providers` and `lifecycle` (plan 04 §8). One daemon *agent* is one
 * conversation, shown as a session; the model picker's provider chooses the runtime; the daemon's
 * modes stand where OpenCode offers agents.
 *
 * Everything runs on [lane]; every public call hops onto it and answers an [Outcome] (module
 * failures are [PaseoException] inside, translated once by [paseoAttempt]). Events are neutral
 * [GatewayEvent]s.
 *
 * Listening: the event loop and reconnect run only after [startListening] (Dart
 * `openEventChannel().start()`); the connection engine calls it once the pair is open. Reconnect
 * follows plan 04 §5.5.
 *
 * Not ported here (S3-PASEO report): permissions/questions/ACP live in `requests/` (slice C), the
 * chat feed and the helper-wide work inventory in `feed/` and `work/` (slice D); browser launch,
 * gen-UI, handoff, files, changes, worktrees, terminal, sub-agents, background tasks, import, host
 * update and usage are S5-PASEOP1.
 */
public class PaseoGateway(
    public val transport: PaseoTransport,
    scope: CoroutineScope,
    internal val lane: CoroutineDispatcher,
    /** Where long histories are mapped (AppDispatchers.default). */
    private val mapDispatcher: CoroutineDispatcher = lane,
    private val clock: Clock,
    directory: String? = null,
    /**
     * Mode a runtime's new agent must start in (phone helper: `claude → default`, always asking).
     */
    defaultProviderModes: Map<String, String> = emptyMap(),
    /**
     * Keep the helper-wide work inventory (the phone helper's gateway; plan 04 §8 `trackLocalWork =
     * true`).
     */
    trackLocalWork: Boolean = false,
    internal val random: Random = Random.Default,
    /** Overridable so tests do not wait for a warming daemon. */
    internal val providerWarmupAttempts: Int = 8,
    internal val providerWarmupIntervalMs: Long = 1_500,
) :
    ServerGateway,
    SessionSelectionGateway,
    CorrelatedPromptGateway,
    MidTurnPromptGateway,
    WorkspaceTransferGateway {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    internal val scope: CoroutineScope = CoroutineScope(scope.coroutineContext + job + lane)
    public val defaultProviderModes: Map<String, String> = defaultProviderModes.toMap()

    // ---- state (lane-confined; Dart field names)
    internal var directoryValue: String? = directory
    internal var closed = false
    internal var locationEpoch = 0
    internal val agents = LinkedHashMap<String, JsonObject>()
    internal val sessions = LinkedHashMap<String, Session>()
    internal val statuses = LinkedHashMap<String, SessionStatus>()
    internal val drafts = LinkedHashSet<String>()
    internal val draftProviders = HashMap<String, String>()
    internal val draftModels = HashMap<String, ModelRef>()
    internal val draftVariants = HashMap<String, String>()
    internal val liveAgentSessions = HashSet<String>()
    internal val uncertain = HashSet<String>()

    /**
     * A draft's first send whose create answer was lost (B-AGENT-188): what a read needs to settle
     * it.
     */
    internal val uncertainStarts = HashMap<String, PaseoUncertainStart>()

    /** Prompt accepted, turn not started yet: a spawn-time idle snapshot must not end it. */
    internal val awaitingTurn = HashSet<String>()

    /** The stream says a turn runs: it owns busy state while open (snapshots lag). */
    internal val turnActive = HashSet<String>()
    internal val recordBusyExpiry = HashMap<String, Job>()
    internal val live = HashMap<String, PaseoLive>()
    internal val realIds = HashMap<String, String>()
    internal val appIds = HashMap<String, String>()
    internal val titleHints = HashMap<String, String>()
    internal val archivedCopies = HashSet<String>()

    /**
     * The title the app saved for a conversation (by `provider:sessionId`): a record without a
     * title of its own shows it, in every list and header (B-AGENT-180, 181). Installed by the
     * owner of the saved titles.
     */
    public var savedTitle: ((String) -> String?)? = null

    internal var creating = 0
    internal val heldEvents = ArrayList<PaseoPush>()
    internal var providerEntries: List<JsonObject>? = null
    internal var providerRevision = 0

    // ---- events
    private val allEvents =
        MutableSharedFlow<GatewayEnvelope>(
            extraBufferCapacity = 16_384,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )
    private val streamStatus = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
    private val connectCount = MutableStateFlow(0)
    private val capabilityState = MutableStateFlow(paseoCapabilities(false, false))
    private val locationState = MutableStateFlow(ServerLocation(directory = directory))

    // ---- lifecycle (plan 04 §5.5)
    internal var listening = false
    private var retry: Job? = null
    private var retryAttempt = 0
    private var recovering: Job? = null
    private var recoveryDegraded = false
    private var reconciledEpoch: Int? = null

    /** Fixed failure of the last recovery that could not finish (Details only). */
    public var lastRecoveryFailure: Failure? = null
        private set

    /** Helper-wide work truth (slice D's inventory), only when tracking local work. */
    private val localWork: PaseoWorkInventory? = if (trackLocalWork) PaseoWorkInventory() else null

    /**
     * true = known work on the helper; false = a complete, current inventory proves idle; null =
     * unknown (never idle).
     */
    public val localWorkBusy: Boolean?
        get() =
            if (closed || localWork == null) {
                null
            } else {
                localWork.read(
                    transport.connected,
                    transport.epoch,
                    awaitingTurn.isNotEmpty() || turnActive.isNotEmpty() || uncertain.isNotEmpty(),
                )
            }

    /**
     * Changes to [localWorkBusy]'s inputs (inventory only; collecting never touches the transport).
     */
    public val localWorkChanges: Flow<Unit> =
        localWork?.changes ?: kotlinx.coroutines.flow.emptyFlow()

    /**
     * Optional hooks the owners of the requests book and the work inventory install (S3-PASEO
     * integration).
     */
    internal val hooks = mutableListOf<PaseoGatewayHooks>()

    private val removeListener =
        transport.addListener(
            object : PaseoTransportListener {
                override fun onPush(push: PaseoPush) = onEvent(push)

                override fun onDisconnect(epoch: Int) = onTransportLost()
            }
        )

    public val directory: String?
        get() = directoryValue

    public val isClosed: Boolean
        get() = closed

    override val transferEpoch: Long
        get() = workspaceTransferEpoch()

    override val transferConnected: Boolean
        get() = !closed && transport.connected && transport.workspaceFileEditing

    override suspend fun transferFileRequest(
        directory: String,
        epoch: Long,
        request: WorkspaceTransferRequest,
    ): Outcome<JsonObject> = workspaceTransferRequest(directory, epoch, request)

    override fun transferSessionId(id: SessionId): SessionId = daemonSessionId(id)

    override fun transferNavigationId(id: SessionId): SessionId = SessionId(app(id.raw))

    override suspend fun promptTransfer(
        session: SessionId,
        prompt: OutgoingPrompt,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission> = workspaceTransferPrompt(session, prompt, beforeSend)

    override val capabilities: StateFlow<ServerCapabilities> = capabilityState.asStateFlow()
    override val location: StateFlow<ServerLocation> = locationState.asStateFlow()

    override val events: GatewayEvents =
        object : GatewayEvents {
            override val status: StateFlow<StreamStatus> = streamStatus.asStateFlow()
            override val all: Flow<GatewayEnvelope> = allEvents.asSharedFlow()
            override val project: Flow<GatewayEnvelope> =
                allEvents.asSharedFlow().filter {
                    it.location == null || it.location?.directory == directoryValue
                }
            override val connects: StateFlow<Int> = connectCount.asStateFlow()

            override fun retryNow() {
                scope.launch {
                    if (closed || !listening) return@launch
                    retry?.cancel()
                    retry = null
                    retryAttempt = 0
                    recover()
                }
            }
        }

    internal fun nowMs(): Long = clock.now().toEpochMilliseconds()

    // ---- scope fences

    /** The pinned folder, or ScopeMismatch when none/invalid (Dart `_scope`). */
    internal val scopeDir: String
        get() {
            val value = directoryValue
            if (
                value.isNullOrEmpty() ||
                    value.length > 4096 ||
                    value.any { it.code <= 0x1f || it.code == 0x7f }
            ) {
                paseoFail(PaseoFailureKind.ScopeMismatch)
            }
            return value
        }

    internal fun checkLocation(scopeDir: String, epoch: Int) {
        if (closed || scopeDir != directoryValue || epoch != locationEpoch)
            paseoFail(PaseoFailureKind.ScopeMismatch)
    }

    internal fun real(appId: String): String = realIds[appId] ?: appId

    internal fun app(realId: String): String = appIds[realId] ?: realId

    /**
     * The daemon's own id for [appId] once its agent exists (another gateway on the daemon opens it
     * by this).
     */
    public fun daemonSessionId(appId: SessionId): SessionId = SessionId(real(appId.raw))

    private suspend inline fun <T> call(crossinline block: suspend () -> T): Outcome<T> =
        withContext(lane) { paseoAttempt { block() } }

    // ---- location

    override fun setLocation(location: ServerLocation) {
        scope.launch { applyLocation(location) }
    }

    /** Suspending form of [setLocation]; a workspace is refused (Paseo has none). */
    public suspend fun changeLocation(location: ServerLocation): Outcome<Unit> = call {
        applyLocation(location)
    }

    private fun applyLocation(location: ServerLocation) {
        if (!location.workspace.isNullOrEmpty()) paseoFail(PaseoFailureKind.Unavailable)
        if (directoryValue == location.directory) return
        directoryValue = location.directory
        locationState.value = ServerLocation(directory = location.directory)
        locationEpoch++
        providerRevision++
        agents.clear()
        sessions.clear()
        statuses.clear()
        cancelRecordBusyExpiry()
        drafts.clear()
        draftProviders.clear()
        draftModels.clear()
        draftVariants.clear()
        liveAgentSessions.clear()
        uncertain.clear()
        uncertainStarts.clear()
        awaitingTurn.clear()
        turnActive.clear()
        live.clear()
        realIds.clear()
        appIds.clear()
        heldEvents.clear()
        providerEntries = null
        recoveryDegraded = false
        requests.clear()
        hooks.forEach { it.onLocationChanged() }
    }

    // ---- health

    override suspend fun health(): Outcome<Health> = call {
        transport.connect()
        refreshCapabilities()
        // The daemon's own number ("Version 0.11.2"), never an internal label.
        Health(healthy = true, version = transport.serverVersion)
    }

    private fun refreshCapabilities() {
        capabilityState.value =
            paseoCapabilities(transport.connected, transport.workspaceFileEditing)
    }

    // ---- sessions

    /** Every page of this folder's agents (at most 20 pages of 200). */
    public suspend fun allSessions(): Outcome<ImmutableList<Session>> = call { readAllSessions() }

    internal suspend fun readAllSessions(): ImmutableList<Session> {
        val result = ArrayList<Session>()
        var cursor: String? = null
        for (page in 0 until 20) {
            val next = readSessionPage(cursor, 200)
            result += next.items
            cursor = next.nextCursor ?: break
        }
        return result.toImmutableList()
    }

    override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> =
        call {
            readSessionPage(cursor, limit)
        }

    internal suspend fun readSessionPage(cursor: String?, limit: Int): ServerPage<Session> {
        val scope = scopeDir
        val epoch = locationEpoch
        val workRevision = localWork?.revision
        val result =
            transport.request(
                "fetch_agents_request",
                jsonOf(
                    "sort" to listOf(mapOf("key" to "updated_at", "direction" to "desc")),
                    "page" to
                        buildMap {
                            put("limit", limit.coerceIn(1, 200))
                            if (cursor != null) put("cursor", paseoString(cursor, max = 4096))
                        },
                    // One fixed id keeps repeated list reads on a single subscription.
                    "subscribe" to mapOf("subscriptionId" to "opencode-mobile"),
                ),
            )
        refreshCapabilities()
        if (localWork != null && workRevision != null) {
            val socket = transport.epoch
            localWork.observePage(
                result = result,
                cursor = cursor,
                epoch = socket,
                requestRevision = workRevision,
                listChildren = { parent ->
                    transport.request(
                        "agent.provider_subagents.list.request",
                        jsonOf("parentAgentId" to parent),
                        timeoutMs = 2_000,
                        expectedEpoch = socket,
                    )
                },
                stillCurrent = { !closed && transport.epoch == socket },
            )
        }
        checkLocation(scope, epoch)
        hooks.forEach { it.onAgentPage(result, cursor) }
        val items = ArrayList<Session>()
        paseoReply("fetch_agents_response") {
            for (raw in paseoList(result["entries"], max = 200)) {
                val entry = paseoObject(raw)
                remember(paseoObject(entry["agent"]))?.let(items::add)
            }
        }
        // Conversations opened here but not yet sent to the daemon come first.
        if (cursor == null) items.addAll(0, drafts.mapNotNull { sessions[it] })
        val pageInfo = result.obj("pageInfo")
        val next =
            if (pageInfo?.get("hasMore").boolOrNull() == true) pageInfo?.str("nextCursor") else null
        return ServerPage(items.toImmutableList(), next?.takeIf { it.isNotEmpty() })
    }

    /**
     * The runtime is only known once the composer names a model: a new conversation is local until
     * its first prompt.
     */
    override suspend fun createSession(spec: NewSession): Outcome<Session> = call {
        createDraft(spec.title)
    }

    internal suspend fun createDraft(title: String? = null): Session {
        val scope = scopeDir
        transport.connect()
        val now = nowMs()
        val session =
            Session(
                id = SessionId(paseoUuid(random)),
                title = title?.takeIf { it.isNotBlank() } ?: NEW_CONVERSATION_TITLE,
                directory = scope,
                time = SessionTime(createdMs = now, updatedMs = now),
            )
        sessions[session.id.raw] = session
        statuses[session.id.raw] = SessionStatus.Idle
        drafts += session.id.raw
        return session
    }

    override suspend fun createSelectedSession(defaults: SessionSelection): Outcome<Session> =
        call {
            val draft = createDraft()
            val model = defaults.model
            if (model != null && isPaseoProviderId(model.providerId)) {
                draftModels[draft.id.raw] = model
                draftProviders[draft.id.raw] = model.providerId
            }
            val session =
                draft.copy(
                    selection =
                        defaults.copy(
                            modelKnown = model != null,
                            agentKnown = defaults.agent != null,
                        )
                )
            sessions[session.id.raw] = session
            session
        }

    /**
     * Keeps the agent chip selection through an empty draft's first prompt (rechecked when that
     * prompt is sent).
     */
    public suspend fun seedDraftProvider(session: SessionId, providerId: String): Outcome<Unit> =
        call {
            if (session.raw !in drafts || !isPaseoProviderId(providerId))
                paseoFail(PaseoFailureKind.Unavailable)
            draftProviders[session.raw] = providerId
        }

    /** Runtime identity of a known agent or a local draft. */
    public fun providerIdForSession(session: SessionId): String? =
        agents[session.raw]?.str("provider") ?: draftProviders[session.raw]

    override suspend fun session(id: SessionId): Outcome<Session> = call {
        if (id.raw in drafts) return@call sessions.getValue(id.raw)
        fetchAgent(id.raw)
        sessions[id.raw] ?: paseoFail(PaseoFailureKind.NotFound)
    }

    override suspend fun renameSession(id: SessionId, title: String): Outcome<Unit> = call {
        val name = paseoString(title.trim(), max = 200)
        if (id.raw in drafts) {
            sessions[id.raw] = sessions.getValue(id.raw).copy(title = name)
            return@call
        }
        if (id.raw !in sessions) fetchAgent(id.raw)
        transport.request(
            "update_agent_request",
            jsonOf("agentId" to real(id.raw), "name" to name),
            mutation = true,
        )
    }

    /** Archives rather than deletes: the daemon keeps the provider's own session (B-AGENT-164). */
    override suspend fun deleteSession(id: SessionId): Outcome<Unit> = call {
        if (id.raw !in drafts) {
            if (id.raw !in sessions) fetchAgent(id.raw)
            transport.request(
                "archive_agent_request",
                jsonOf("agentId" to real(id.raw)),
                mutation = true,
            )
        }
        forget(id.raw)
    }

    /** Drops a conversation that never reached the daemon (a start that failed). */
    public suspend fun discardDraft(id: SessionId) {
        withContext(lane) { if (id.raw in drafts) forget(id.raw) }
    }

    override suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>> = call {
        statuses.entries.associate { SessionId(it.key) to it.value }.toImmutableMap()
    }

    // ---- history

    override suspend fun messagePage(
        id: SessionId,
        cursor: String?,
        limit: Int,
    ): Outcome<ServerPage<MessageWithParts>> = call {
        if (cursor != null) paseoFail(PaseoFailureKind.Unavailable)
        ServerPage(readMessages(id.raw), null)
    }

    internal suspend fun readMessages(id: String): ImmutableList<MessageWithParts> {
        if (id in drafts && !draftStarted(id)) return persistentListOf()
        val scope = scopeDir
        val epoch = locationEpoch
        val agent = fetchAgent(id)
        val busy = paseoSessionStatus(agent, nowMs()) == SessionStatus.Busy
        // Later frames wait until the history is in place, as if mapped inline (Dart
        // holdingMessages).
        return transport.requestThen(
            "fetch_agent_timeline_request",
            jsonOf(
                "agentId" to real(id),
                "direction" to "tail",
                "limit" to 0,
                "projection" to "projected",
            ),
            timeoutMs = HISTORY_TIMEOUT_MS,
        ) { result ->
            val entries = result["entries"]
            val timeline =
                if (
                    (entries as? kotlinx.serialization.json.JsonArray)?.size ?: 0 >=
                        BACKGROUND_TIMELINE_ENTRIES
                ) {
                    withContext(mapDispatcher) { paseoTimeline(id, result, busy) }
                } else {
                    paseoTimeline(id, result, busy)
                }
            checkLocation(scope, epoch)
            noteLoadedAgent(id, result)
            if (busy) watchUntilIdle(id)
            // Hydrated items are known from here on: later stream events for them are deltas and
            // snapshots.
            val state = live.getOrPut(id) { PaseoLive() }
            state.tools.clear()
            state.tools.putAll(timeline.tools)
            state.announced.clear()
            state.runId = null
            state.runType = null
            for (message in timeline.messages) {
                state.announced[message.info.id] =
                    StringBuilder(message.parts.firstOrNull()?.text.orEmpty())
            }
            result.str("epoch")?.let { state.epoch = it }
            result.obj("endCursor")?.get("seq")?.longOrNull()?.let { state.lastSeq = it }
            uncertain.remove(id)
            timeline.messages
        }
    }

    /**
     * Reading a timeline loads the agent under its own id; the answer's loaded snapshot is kept.
     */
    private fun noteLoadedAgent(id: String, timeline: JsonObject) {
        val loaded = timeline.obj("agent") ?: return
        if (loaded.str("id") != real(id) || !daemonHolds(loaded)) return
        if (agents[app(id)]?.str("status") == "error")
            live.getOrPut(app(id)) { PaseoLive() }.failedBeforeLoad = true
        remember(loaded)
    }

    /** Only a loaded agent's snapshot names its `activeTurn` (a turn or null). */
    internal fun daemonHolds(agent: JsonObject): Boolean =
        "activeTurn" in agent && agent.str("status") in setOf("initializing", "running", "idle")

    private val watching = HashSet<String>()

    /**
     * A reply loaded while still running can finish before this gateway hears its stream: ask every
     * 2 s for up to 3 min; two idle answers in a row end the turn here too.
     */
    private fun watchUntilIdle(id: String) {
        if (!watching.add(id)) return
        scope.launch {
            try {
                var idle = 0
                repeat(90) {
                    delay(2_000)
                    if (closed || id in turnActive) return@launch
                    val agent =
                        try {
                            fetchAgent(id, reconcileStatus = false)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: PaseoException) {
                            return@repeat
                        }
                    if (paseoSessionStatus(agent, nowMs()) == SessionStatus.Busy) {
                        idle = 0
                        return@repeat
                    }
                    if (++idle < 2) return@repeat
                    emitStatus(id, SessionStatus.Idle)
                    emit(GatewayEvent.RunEnded(SessionId(id), RunOutcome.Succeeded))
                    return@launch
                }
            } finally {
                watching.remove(id)
            }
        }
    }

    override suspend fun todos(id: SessionId): Outcome<ImmutableList<Todo>> =
        Outcome.Ok(persistentListOf())

    override suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>> =
        unavailable("sessionDiff")

    // ---- prompts

    override fun createPromptMessageId(): MessageId = MessageId(paseoUuid(random))

    override suspend fun prompt(
        session: SessionId,
        prompt: OutgoingPrompt,
        commandId: String?,
    ): Outcome<PromptAdmission> =
        send(
            session.raw,
            prompt.clientMessageId?.raw ?: paseoUuid(random),
            prompt,
            existingOnly = false,
            beforeSend = null,
        )

    /**
     * Exact dispatch correlation (B-AGENT-155): the caller's id is forwarded after the final fence.
     */
    override suspend fun promptWithMessageId(
        session: SessionId,
        messageId: MessageId,
        prompt: OutgoingPrompt,
        beforeSend: () -> Boolean,
    ): Outcome<PromptAdmission> =
        send(session.raw, messageId.raw, prompt, existingOnly = true, beforeSend = beforeSend)

    private suspend fun send(
        sessionId: String,
        messageId: String,
        prompt: OutgoingPrompt,
        existingOnly: Boolean,
        beforeSend: (() -> Boolean)?,
    ): Outcome<PromptAdmission> {
        val images = paseoImages(prompt.attachments)
        if (images is PaseoImages.Refused) {
            return Outcome.Failed(
                Failure.Unavailable(
                    FeatureId("promptAttachments"),
                    DETAILS.details("picture ${images.problem.name}"),
                )
            )
        }
        val outcome = call {
            sendPrompt(
                sessionId,
                messageId,
                prompt,
                (images as PaseoImages.Ok).images,
                existingOnly,
                beforeSend,
            )
        }
        // A lost answer is Uncertain: look it up, never resend (plan 04 §11.1).
        val failure = (outcome as? Outcome.Failed)?.failure
        return if (
            failure is Failure.Refused &&
                failure.reason ==
                    io.github.eslamasabry.oc.core.common.RefusalReason.DispatchUncertain
        ) {
            Outcome.Ok(PromptAdmission.Uncertain)
        } else {
            outcome
        }
    }

    internal suspend fun sendPrompt(
        rawSessionId: String,
        messageId: String,
        prompt: OutgoingPrompt,
        images: List<JsonObject>,
        existingOnly: Boolean,
        beforeSend: (() -> Boolean)?,
    ): PromptAdmission {
        // A card observed by another gateway carries the daemon id; local state stays keyed by the
        // app id.
        val sessionId = app(rawSessionId)
        if (existingOnly && sessionId in drafts) paseoFail(PaseoFailureKind.Unavailable)
        if (
            messageId.isEmpty() ||
                messageId.length > 256 ||
                messageId.any { it.code <= 0x1f || it.code == 0x7f }
        ) {
            paseoFail(PaseoFailureKind.InvalidResponse)
        }
        if (prompt.mentions.isNotEmpty() || prompt.delivery != null)
            paseoFail(PaseoFailureKind.Unavailable)
        if (sendIsUncertain(sessionId)) paseoFail(PaseoFailureKind.DeliveryUnknown)
        val scope = scopeDir
        val epoch = locationEpoch
        val text = paseoString(prompt.text, max = 1024 * 1024, empty = true)
        val model = prompt.model?.model
        val variant = prompt.model?.variant
        val fence: () -> Unit = {
            if (beforeSend != null && !beforeSend()) paseoFail(PaseoFailureKind.StaleRequest)
        }
        awaitingTurn += sessionId
        try {
            if (sessionId in drafts) {
                createAgent(
                    sessionId,
                    text,
                    messageId,
                    model ?: draftModels[sessionId],
                    prompt.agent,
                    variant ?: draftVariants[sessionId],
                    images,
                    fence,
                )
            } else {
                if (sessionId !in agents) fetchAgent(sessionId)
                val runtime =
                    agents[sessionId]?.str("provider") ?: paseoFail(PaseoFailureKind.Unavailable)
                requireProviderAvailable(runtime)
                checkLocation(scope, epoch)
                if (model != null && model.providerId != runtime)
                    paseoFail(PaseoFailureKind.Unavailable)
                // B-AGENT-144: a newly created/live session continues without restoration proof. A
                // reopened ACP row
                // (or one whose persistence handle is gone) needs an explicit, acknowledged new
                // chat: the host could
                // otherwise fall back to a fresh native session behind this id and lose the context
                // silently.
                val agent = agents[sessionId] ?: paseoFail(PaseoFailureKind.Unavailable)
                val continuation =
                    paseoHostAgentContinuation(
                        sessionId,
                        agent,
                        live = sessionId in liveAgentSessions || daemonHolds(agent),
                    )
                if (continuation.requiresNewChat) paseoFail(PaseoFailureKind.NewChatRequired)
                checkLocation(scope, epoch)
                applySelection(sessionId, model, prompt.agent, variant)
                val realId = real(sessionId)
                transport.request(
                    "send_agent_message_request",
                    jsonOf(
                            "agentId" to realId,
                            "text" to text,
                            "messageId" to messageId,
                            // A message sent while the agent works joins that turn (agent-prompt.js
                            // steerOrReplace).
                            "activeTurnBehavior" to "steer",
                            "images" to images.takeIf { it.isNotEmpty() },
                        )
                        .withoutNulls(),
                    mutation = true,
                    timeoutMs = SEND_TIMEOUT_MS,
                    beforeSend = {
                        checkLocation(scope, epoch)
                        if (real(sessionId) != realId) paseoFail(PaseoFailureKind.StaleRequest)
                        fence()
                    },
                )
            }
            checkLocation(scope, epoch)
            statuses[sessionId] = SessionStatus.Busy
            return PromptAdmission.Confirmed(null)
        } catch (e: PaseoException) {
            awaitingTurn.remove(sessionId)
            if (e.kind == PaseoFailureKind.DeliveryUnknown) uncertain += sessionId
            throw e
        }
    }

    private suspend fun createAgent(
        id: String,
        prompt: String,
        messageId: String,
        model: ModelRef?,
        requestedMode: String?,
        variant: String?,
        images: List<JsonObject>,
        fence: () -> Unit,
    ) {
        val scope = scopeDir
        val epoch = locationEpoch
        val provider =
            if (model == null || model.providerId.isEmpty()) draftProviders[id] ?: DEFAULT_PROVIDER
            else model.providerId
        requireProviderAvailable(provider)
        checkLocation(scope, epoch)
        val modes = modesFor(provider)
        val mode = requestedMode ?: defaultProviderModes[provider]
        // A runtime with a required mode must list it (0.9.2 may list none on a phone: then it is
        // sent by name).
        if (
            provider in defaultProviderModes &&
                (mode == null || (modes.isNotEmpty() && mode !in modes))
        ) {
            paseoFail(PaseoFailureKind.Unavailable)
        }
        val sendMode =
            mode != null &&
                (mode in modes || (modes.isEmpty() && defaultProviderModes[provider] == mode))
        val title = sessions[id]?.title
        val sentAt = nowMs()
        creating++
        val result =
            try {
                transport.request(
                    "create_agent_request",
                    jsonOf(
                            "config" to
                                jsonOf(
                                        "provider" to provider,
                                        "cwd" to scope,
                                        "model" to
                                            model?.modelId?.takeIf {
                                                it.isNotEmpty() && it != DEFAULT_MODEL
                                            },
                                        "modeId" to mode?.takeIf { sendMode },
                                        "thinkingOptionId" to variant?.takeIf { it.isNotEmpty() },
                                        "title" to title?.takeIf { it != NEW_CONVERSATION_TITLE },
                                    )
                                    .withoutNulls(),
                            "initialPrompt" to prompt,
                            "images" to images.takeIf { it.isNotEmpty() },
                            "clientMessageId" to messageId,
                            "labels" to emptyMap<String, String>(),
                        )
                        .withoutNulls(),
                    mutation = true,
                    timeoutMs = CREATE_TIMEOUT_MS,
                    beforeSend = {
                        checkLocation(scope, epoch)
                        fence()
                    },
                )
            } catch (e: PaseoException) {
                if (e.kind == PaseoFailureKind.DeliveryUnknown)
                    uncertainStarts[id] = PaseoUncertainStart(messageId, provider, sentAt)
                throw e
            } finally {
                creating--
                if (creating == 0 && closed) heldEvents.clear()
            }
        val agent = paseoReply("create_agent_response") { paseoObject(result["agent"]) }
        checkLocation(scope, epoch)
        if (agent.str("provider") != provider) paseoFail(PaseoFailureKind.InvalidResponse)
        val realId = paseoString(agent["id"], max = 256)
        realIds[id] = realId
        appIds[realId] = id
        remember(agent) ?: paseoFail(PaseoFailureKind.ScopeMismatch)
        if (agent.str("status") != "error" && agent.str("status") != "closed")
            liveAgentSessions += id
        drafts.remove(id)
        draftProviders.remove(id)
        draftModels.remove(id)
        draftVariants.remove(id)
        // Pushes that raced ahead of the create answer (B-AGENT-163).
        if (creating == 0) {
            val held = heldEvents.toList()
            heldEvents.clear()
            held.forEach(::onEvent)
        }
    }

    /**
     * Applies a changed mode, model or thinking level before a follow-up turn. Options the runtime
     * lacks are ignored.
     */
    private suspend fun applySelection(
        id: String,
        model: ModelRef?,
        mode: String?,
        variant: String?,
    ) {
        val scope = scopeDir
        val epoch = locationEpoch
        var agent = agents[id] ?: return
        val modeIds =
            agent.arr("availableModes").orEmpty().mapNotNull { it.objectOrNull()?.str("id") }
        if (mode != null && mode != agent.str("currentModeId") && mode in modeIds) {
            transport.request(
                "set_agent_mode_request",
                jsonOf("agentId" to real(id), "modeId" to mode),
                mutation = true,
            )
            agent = agent.with("currentModeId", JsonPrimitive(mode))
        }
        if (
            model != null &&
                model.providerId == agent.str("provider") &&
                model.modelId.isNotEmpty() &&
                model.modelId != DEFAULT_MODEL &&
                model.modelId != agent.str("model")
        ) {
            transport.request(
                "set_agent_model_request",
                jsonOf("agentId" to real(id), "modelId" to model.modelId),
                mutation = true,
            )
            agent = agent.with("model", JsonPrimitive(model.modelId))
        }
        checkLocation(scope, epoch)
        agents[id] = agent
        if (variant != null) {
            agent = setThinking(id, agent, variant)
            checkLocation(scope, epoch)
            remember(agent)
        }
    }

    private suspend fun setThinking(id: String, agent: JsonObject, variant: String): JsonObject {
        val thinking = variant.ifEmpty { null }
        if (
            thinking == null &&
                (agent["thinkingOptionId"] == null || agent["thinkingOptionId"] is JsonNull)
        )
            return agent
        transport.request(
            "set_agent_thinking_request",
            JsonObject(
                mapOf(
                    "agentId" to JsonPrimitive(real(id)),
                    "thinkingOptionId" to (thinking?.let(::JsonPrimitive) ?: JsonNull),
                )
            ),
            mutation = true,
        )
        return agent.with("thinkingOptionId", thinking?.let(::JsonPrimitive) ?: JsonNull).also {
            agents[id] = it
        }
    }

    override suspend fun shell(
        session: SessionId,
        command: String,
        agent: String,
        model: ModelSelection?,
    ): Outcome<Unit> = unavailable("conversationShell")

    /**
     * The runtime's own commands run as `/name args` in an ordinary prompt (Dart commands.dart).
     */
    override suspend fun slashCommand(
        session: SessionId,
        command: String,
        args: String,
        model: ModelSelection?,
    ): Outcome<Unit> {
        val name = command.trim().removePrefix("/")
        if (name.isEmpty() || name.startsWith("__")) return unavailable("slashCommands")
        val text = if (args.isBlank()) "/$name" else "/$name $args"
        return when (val sent = prompt(session, OutgoingPrompt(text = text, model = model))) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Failed -> sent
        }
    }

    override suspend fun stop(session: SessionId, resume: Boolean): Outcome<StopResult> = call {
        if (session.raw in drafts) return@call StopResult.NothingRunning
        transport.request(
            "cancel_agent_request",
            jsonOf("agentId" to real(paseoString(session.raw, max = 256))),
            mutation = true,
        )
        StopResult.Stopped
    }

    /**
     * Claude Code, Codex, OpenCode and Pi steer a running turn; ACP agents restart it. Null until
     * the agent is known.
     */
    override fun midTurnPrompt(session: SessionId): MidTurnPrompt? {
        val provider = agents[app(session.raw)]?.str("provider") ?: return null
        return if (provider in STEERING_PROVIDERS) MidTurnPrompt.JoinsTurn
        else MidTurnPrompt.RestartsTurn
    }

    // ---- selection

    override suspend fun setSessionModel(
        session: SessionId,
        model: ModelRef,
        variant: String,
    ): Outcome<Unit> = call {
        val id = session.raw
        if (id in drafts) {
            val provider = providerIdForSession(session)
            if (provider != null && provider != model.providerId)
                paseoFail(PaseoFailureKind.Unavailable)
            draftProviders[id] = model.providerId
            draftModels[id] = model
            draftVariants[id] = variant
            sessions[id]?.let { draft ->
                sessions[id] =
                    draft.copy(
                        selection =
                            SessionSelection(
                                model,
                                variant,
                                draft.selection?.agent,
                                modelKnown = true,
                                agentKnown = draft.selection?.agentKnown ?: false,
                            )
                    )
            }
            return@call
        }
        if (id !in agents) fetchAgent(id)
        val agent = agents[id]
        if (agent == null || model.providerId != agent.str("provider"))
            paseoFail(PaseoFailureKind.Unavailable)
        val scope = scopeDir
        val epoch = locationEpoch
        transport.request(
            "set_agent_model_request",
            jsonOf("agentId" to real(id), "modelId" to model.modelId),
            mutation = true,
        )
        checkLocation(scope, epoch)
        // A successful model change is kept even if the thinking request that follows fails.
        var updated = agent.with("model", JsonPrimitive(model.modelId))
        remember(updated)
        updated = setThinking(id, updated, variant)
        checkLocation(scope, epoch)
        remember(updated)
    }

    override suspend fun setSessionAgent(session: SessionId, agent: String): Outcome<Unit> = call {
        val id = session.raw
        if (id in drafts) return@call
        if (id !in agents) fetchAgent(id)
        val current = agents[id] ?: paseoFail(PaseoFailureKind.Unavailable)
        transport.request(
            "set_agent_mode_request",
            jsonOf("agentId" to real(id), "modeId" to agent),
            mutation = true,
        )
        remember(current.with("currentModeId", JsonPrimitive(agent)))
    }

    // ---- providers, models and modes

    override suspend fun providers(): Outcome<ProvidersResponse> = call { readProviders() }

    override suspend fun configuredProviders(): Outcome<ProvidersResponse> = providers()

    internal suspend fun readProviders(): ProvidersResponse {
        val providers = ArrayList<ProviderInfo>()
        var defaultProvider: String? = null
        var defaultModel: String? = null
        for (entry in providerSnapshot()) {
            val id = entry.str("provider") ?: continue
            if (!paseoProviderCanStart(entry)) continue
            val modelIds = ArrayList<String>()
            val modelData = LinkedHashMap<String, JsonObject>()
            var providerDefault: String? = null
            for (raw in entry.arr("models").orEmpty().take(500)) {
                val m = raw.objectOrNull() ?: continue
                val modelId =
                    m.str("id")?.takeIf { it.isNotEmpty() && it.length <= 256 } ?: continue
                if (m["isSelectable"].boolOrNull() == false || modelId in modelIds) continue
                modelIds += modelId
                val window =
                    m["contextWindowMaxTokens"].numberOrNull()?.takeIf { it > 0 && it.isFinite() }
                var data =
                    jsonOf(
                            "id" to modelId,
                            "name" to (m.str("label")?.takeIf { it.isNotEmpty() } ?: modelId),
                            "limit" to window?.let { mapOf("context" to it.toLong()) },
                        )
                        .withoutNulls()
                data = JsonObject(data + thinkingModelData(m))
                if (m["isDefault"].boolOrNull() == true) {
                    providerDefault = providerDefault ?: modelId
                    data = data.with("isDefault", JsonPrimitive(true))
                }
                modelData[modelId] = data
            }
            if (modelIds.isEmpty()) {
                modelIds += DEFAULT_MODEL
                modelData[DEFAULT_MODEL] = jsonOf("id" to DEFAULT_MODEL, "name" to "Default model")
            }
            providers +=
                ProviderInfo(
                    id,
                    paseoHostAgentName(id),
                    modelIds.toImmutableList(),
                    modelData.toImmutableMap(),
                )
            if (defaultProvider == null || id == DEFAULT_PROVIDER) {
                defaultProvider = id
                defaultModel = providerDefault ?: modelIds.first()
            }
        }
        val list = providers.toImmutableList()
        return ProvidersResponse(list, list, defaultProvider, defaultModel)
    }

    /**
     * Paseo 0.9.2 sends per-model thinking options; their ids are kept verbatim for
     * set_agent_thinking_request.
     */
    private fun thinkingModelData(model: JsonObject): Map<String, JsonElement> {
        val variants = LinkedHashMap<String, JsonObject>()
        for (raw in model.arr("thinkingOptions").orEmpty().take(64)) {
            val option = raw.objectOrNull() ?: continue
            val id =
                option.str("id")?.takeIf { it.isNotEmpty() && it.length <= 256 && it !in variants }
                    ?: continue
            variants[id] =
                jsonOf(
                        "label" to option.str("label")?.takeIf { it.isNotEmpty() },
                        "isDefault" to
                            (option["isDefault"].boolOrNull() == true ||
                                    model.str("defaultThinkingOptionId") == id)
                                .takeIf { it },
                    )
                    .withoutNulls()
        }
        if (variants.isEmpty()) return emptyMap()
        return mapOf(
            "variants" to JsonObject(variants),
            "capabilities" to jsonOf("reasoning" to true),
        )
    }

    /**
     * The daemon's modes stand where OpenCode's agents do (the default runtime's, its own default
     * first).
     */
    override suspend fun agents(): Outcome<ImmutableList<AgentInfo>> = call {
        providerSnapshot()
        val names = modesFor(DEFAULT_PROVIDER).toMutableList()
        if (names.isEmpty()) names += "default"
        val preferred = defaultModeFor(DEFAULT_PROVIDER) ?: defaultProviderModes[DEFAULT_PROVIDER]
        if (preferred != null && names.remove(preferred)) names.add(0, preferred)
        names
            .map { AgentInfo(name = it, mode = "primary", color = null, model = null) }
            .toImmutableList()
    }

    /** The composer's starting model and mode. */
    public suspend fun chatDefaults(): Outcome<ChatDefaults> = call {
        val response = readProviders()
        val provider = response.defaultProviderId
        val model = response.defaultModelId
        val modes = if (provider == null) emptyList() else modesFor(provider)
        ChatDefaults(
            model = if (provider == null || model == null) null else ModelRef(provider, model),
            agent = if ("default" in modes || modes.isEmpty()) "default" else modes.first(),
        )
    }

    /**
     * Just after the daemon starts every runtime reports `loading` while it asks each CLI for its
     * models: wait briefly for a settled snapshot; past the limit answer with what is ready and
     * cache nothing (B-AGENT-162).
     */
    internal suspend fun providerSnapshot(): List<JsonObject> {
        providerEntries?.let {
            return it
        }
        val scope = scopeDir
        val epoch = locationEpoch
        val revision = providerRevision
        var entries = emptyList<JsonObject>()
        for (attempt in 0 until providerWarmupAttempts) {
            if (attempt > 0) delay(providerWarmupIntervalMs)
            val result =
                transport.request(
                    "get_providers_snapshot_request",
                    jsonOf("cwd" to scope),
                    timeoutMs = PROVIDERS_TIMEOUT_MS,
                )
            checkLocation(scope, epoch)
            entries =
                paseoReply("get_providers_snapshot_response") {
                    paseoList(result["entries"], max = 256).mapNotNull { it.objectOrNull() }
                }
            if (entries.none { it.str("status") == "loading" }) {
                if (revision == providerRevision) providerEntries = entries
                break
            }
        }
        return entries
    }

    internal suspend fun requireProviderAvailable(id: String) {
        if (!isPaseoProviderId(id)) paseoFail(PaseoFailureKind.Unavailable)
        val matches = providerSnapshot().filter { it.str("provider") == id }
        if (matches.size != 1) paseoFail(PaseoFailureKind.Unavailable)
        val entry = matches.single()
        if (!paseoProviderCanStart(entry)) {
            // The helper's own word for why ("AuthRequired", "not installed"), for Details.
            val reason =
                entry.str("error")?.takeIf { it.isNotBlank() }
                    ?: if (entry["enabled"].boolOrNull() == false) "Provider $id is disabled"
                    else "Provider $id is not ready (${entry.str("status")})"
            throw PaseoException(PaseoFailureKind.Unavailable, reason)
        }
    }

    internal fun modesFor(provider: String): List<String> =
        providerEntries
            ?.firstOrNull { it.str("provider") == provider }
            ?.arr("modes")
            .orEmpty()
            .mapNotNull { it.objectOrNull()?.str("id") }

    private fun defaultModeFor(provider: String): String? =
        providerEntries
            ?.firstOrNull { it.str("provider") == provider }
            ?.str("defaultModeId")
            ?.takeIf { it.isNotEmpty() }

    // ---- permissions, questions and ACP host agents (requests/PaseoRequestBook, slice C)

    /**
     * The permissions and questions of this gateway's folder (lane-confined; read through the calls
     * below).
     */
    public val requests: PaseoRequestBook =
        PaseoRequestBook(
            object : PaseoRequestHost {
                override val epoch: Int
                    get() = transport.epoch

                override val connected: Boolean
                    get() = transport.connected

                override val scope: String?
                    get() = directoryValue

                override val locationEpoch: Int
                    get() = this@PaseoGateway.locationEpoch

                override fun realId(appId: String): String = real(appId)

                override fun providerOf(appId: String): String? =
                    agents[appId]?.str("provider") ?: draftProviders[appId]

                override suspend fun request(
                    type: String,
                    body: JsonObject,
                    mutation: Boolean,
                    expectedEpoch: Int,
                ): JsonObject =
                    transport.request(
                        type,
                        body,
                        mutation = mutation,
                        expectedEpoch = expectedEpoch,
                    )

                override suspend fun send(type: String, body: JsonObject, expectedEpoch: Int) {
                    transport.send(type, body, expectedEpoch)
                }

                override fun emit(event: GatewayEvent) = this@PaseoGateway.emit(event)

                // TODO(contract): GatewayEvent.QuestionAsked/QuestionSettled (plan 04 §8); a
                // refresh hint until then.
                override fun questionChanged(signal: PaseoQuestionSignal) =
                    this@PaseoGateway.emit(GatewayEvent.RefreshHint(QUESTION_HINT))
            }
        )

    override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> = call {
        requests.pendingPermissions()
    }

    override suspend fun replyPermission(
        session: SessionId,
        request: RequestId,
        reply: PermissionReply,
        message: String?,
    ): Outcome<Unit> = call {
        requests.replyPermission(request.raw, reply.toPaseo(), session.raw, message)
    }

    /** Deny and interrupt the turn (Paseo "Cancel"; core PermissionReply has no such value yet). */
    public suspend fun cancelPermission(session: SessionId, request: RequestId): Outcome<Unit> =
        call {
            requests.replyPermission(request.raw, PaseoPermissionAnswer.Cancel, session.raw, null)
        }

    override suspend fun pendingQuestions(): Outcome<ImmutableList<PendingQuestion>> = call {
        requests.pendingQuestions()
    }

    override suspend fun answerQuestion(
        session: SessionId,
        request: RequestId,
        answers: ImmutableList<ImmutableList<String>>,
    ): Outcome<Unit> = call { requests.answerQuestion(session.raw, request.raw, answers) }

    override suspend fun rejectQuestion(session: SessionId, request: RequestId): Outcome<Unit> =
        call {
            requests.rejectQuestion(session.raw, request.raw)
        }

    /**
     * ACP agents the host offers (B-AGENT-142, 143). [refresh] asks the host to recheck before
     * reading.
     */
    public suspend fun hostAgentProviders(refresh: Boolean = false): Outcome<HostAgentCatalog> =
        call {
            if (refresh) {
                providerEntries = null
                providerRevision++
                val scope = scopeDir
                val epoch = locationEpoch
                transport.request("refresh_providers_snapshot_request", jsonOf("cwd" to scope))
                checkLocation(scope, epoch)
            }
            paseoHostAgentCatalog(providerSnapshot())
        }

    /**
     * Whether reopening [session] continues a live agent or needs an explicit new chat
     * (B-AGENT-144).
     */
    public suspend fun hostAgentContinuation(session: SessionId): Outcome<HostAgentContinuation> =
        call {
            val scope = scopeDir
            val epoch = locationEpoch
            if (session.raw !in agents) fetchAgent(session.raw)
            checkLocation(scope, epoch)
            val agent = agents[session.raw] ?: paseoFail(PaseoFailureKind.Unavailable)
            paseoHostAgentContinuation(
                session.raw,
                agent,
                live = session.raw in liveAgentSessions || daemonHolds(agent),
            )
        }

    public suspend fun pendingHostPermissions():
        Outcome<ImmutableList<HostAgentPermissionRequest>> = call {
        requests.pendingHostPermissions()
    }

    /** Exactly the action the agent offered (B-SEC-109); null denies (reject-once or cancel). */
    public suspend fun replyHostPermission(
        request: RequestId,
        selectedActionId: String?,
    ): Outcome<Unit> = call { requests.replyHostPermission(request.raw, selectedActionId) }

    // ---- files (P1 extras, S5-PASEOP1)

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> =
        unavailable("fileBrowsing")

    override suspend fun fileContent(path: String): Outcome<FileContent> =
        unavailable("fileBrowsing")

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> =
        unavailable("fileBrowsing")

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> =
        unavailable("textSearch")

    // ---- agent records

    /**
     * Records a daemon agent snapshot (Dart `_remember`). Null when it belongs to another folder or
     * is archived. Snapshot reads are authoritative for status, except a running status from a
     * saved record (bounded).
     */
    internal fun remember(raw: JsonObject, reconcileStatus: Boolean = true): Session? {
        var agent = raw
        agent.str("id")?.let { realId ->
            appIds[realId]?.let { agent = agent.with("id", JsonPrimitive(it)) }
        }
        val id0 = agent.str("id")
        // A reloaded record can come back without its title: the known one stays.
        val known = id0?.let { agents[it]?.str("title") ?: titleHints[it] }
        agent =
            io.github.eslamasabry.oc.protocol.paseo.feed.PaseoTitles.withTitle(
                agent,
                known,
                savedTitle,
            )
        val session = paseoReply("agent") { paseoSession(agent) }
        if (session.directory != directoryValue || agent["archivedAt"].stringOrNull() != null)
            return null
        val id = session.id.raw
        agents[id] = agent
        sessions.remove(id)
        sessions[id] = session
        val status = agent.str("status")
        if (status == "error" || status == "closed") {
            liveAgentSessions.remove(id)
            awaitingTurn.remove(id)
            turnActive.remove(id)
        }
        if (reconcileStatus) {
            val now = nowMs()
            val turn = id in awaitingTurn || id in turnActive
            val next = if (turn) SessionStatus.Busy else paseoSessionStatus(agent, now)
            recordBusyExpiry.remove(id)?.cancel()
            val until = if (turn) null else paseoRecordBusyUntilMs(agent)
            if (next == SessionStatus.Busy && until != null) {
                recordBusyExpiry[id] = scope.launch {
                    delay((until - now).coerceAtLeast(0))
                    recordBusyExpired(id)
                }
            }
            val previous = statuses[id]
            emitStatus(id, next)
            if (previous != null && previous != next && next == SessionStatus.Idle) {
                emit(GatewayEvent.RunEnded(SessionId(id), RunOutcome.Succeeded))
            }
        }
        drafts.remove(id)
        draftProviders.remove(id)
        requests.syncSnapshot(id, agent["pendingPermissions"])
        hooks.forEach { it.onAgentRecorded(id, agent) }
        while (sessions.size > MAX_SESSIONS) forget(sessions.keys.first())
        return session
    }

    /** A record's running status outlived its bound with nothing newer: the session reads idle. */
    private fun recordBusyExpired(id: String) {
        recordBusyExpiry.remove(id)
        if (closed || id in awaitingTurn || id in turnActive || statuses[id] != SessionStatus.Busy)
            return
        emitStatus(id, SessionStatus.Idle)
        emit(GatewayEvent.RunEnded(SessionId(id), RunOutcome.Succeeded))
    }

    private fun cancelRecordBusyExpiry() {
        recordBusyExpiry.values.forEach { it.cancel() }
        recordBusyExpiry.clear()
    }

    internal fun forget(id: String) {
        agents.remove(id)
        sessions.remove(id)
        statuses.remove(id)
        recordBusyExpiry.remove(id)?.cancel()
        drafts.remove(id)
        draftProviders.remove(id)
        draftModels.remove(id)
        draftVariants.remove(id)
        liveAgentSessions.remove(id)
        uncertain.remove(id)
        uncertainStarts.remove(id)
        awaitingTurn.remove(id)
        turnActive.remove(id)
        live.remove(id)
        requests.retireSession(id)
        hooks.forEach { it.onSessionForgotten(id) }
        realIds.remove(id)?.let(appIds::remove)
    }

    internal suspend fun fetchAgent(id: String, reconcileStatus: Boolean = true): JsonObject {
        val scope = scopeDir
        val epoch = locationEpoch
        val result =
            transport.request(
                "fetch_agent_request",
                jsonOf("agentId" to real(paseoString(id, max = 256))),
            )
        checkLocation(scope, epoch)
        // An id the daemon doesn't know is answered with `agent: null`: a missing conversation.
        val raw = result["agent"]
        if (raw == null || raw is JsonNull)
            throw PaseoException(PaseoFailureKind.NotFound, "fetch_agent_request: no such agent")
        val agent = paseoReply("fetch_agent_response") { paseoObject(raw) }
        if (agent.str("id") != real(id))
            throw PaseoException(
                PaseoFailureKind.InvalidResponse,
                "fetch_agent_response: another agent",
            )
        remember(agent, reconcileStatus) ?: paseoFail(PaseoFailureKind.ScopeMismatch)
        return agent
    }

    // ---- live events (Dart events.dart)

    internal fun emit(event: GatewayEvent) {
        if (closed) return
        allEvents.tryEmit(
            GatewayEnvelope(event, EventLocation(directoryValue, null, null), nowMs())
        )
    }

    internal fun emitStatus(id: String, status: SessionStatus) {
        if (statuses[id] == status) return
        statuses[id] = status
        if (status == SessionStatus.Idle) live[id]?.let(::finishOpen)
        if (status == SessionStatus.Busy) emit(GatewayEvent.RunStarted(SessionId(id)))
    }

    private fun onEvent(push: PaseoPush) {
        if (closed || push.epoch != transport.epoch) return
        if (push.type == "status" && push.payload.str("status") == "server_info") {
            refreshCapabilities()
            return
        }
        try {
            localWork?.event(push)
            hooks.forEach { it.onPush(push) }
            val p = push.payload
            val rawId = p.str("agentId") ?: p.obj("agent")?.str("id")
            if (
                creating > 0 && rawId != null && app(rawId) !in sessions && heldEvents.size < 2048
            ) {
                heldEvents += push
                return
            }
            val agentId = rawId?.let(::app)
            when (push.type) {
                "agent_update" ->
                    if (p.str("kind") == "remove") {
                        removed(agentId)
                    } else {
                        p.obj("agent")?.let(::upserted)
                    }
                "agent_stream" -> {
                    val inner = p.obj("event")
                    if (agentId != null && agentId in sessions && inner != null)
                        onStream(agentId, inner, p)
                }
                "agent_permission_request" -> {
                    val request = p.obj("request")
                    if (agentId != null && agentId in sessions && request != null)
                        requests.onPermissionRequest(agentId, request)
                }
                "agent_permission_resolved" ->
                    p.str("requestId")?.let { requests.onPermissionResolved(it) }
                "agent_deleted",
                "agent_archived" -> removed(agentId)
                "providers_snapshot_update" -> {
                    providerRevision++
                    providerEntries = null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A malformed push is dropped. No payload reaches logs.
        } finally {
            localWork?.changed()
        }
    }

    private fun upserted(agent: JsonObject) {
        val realId = agent.str("id") ?: return
        val id = app(realId)
        if (agent["archivedAt"].stringOrNull() != null) {
            removed(id)
            return
        }
        val session = remember(agent) ?: return
        emit(GatewayEvent.SessionUpserted(session))
    }

    private fun removed(id: String?) {
        if (id == null || id !in sessions) return
        forget(id)
        emit(GatewayEvent.SessionDeleted(SessionId(id)))
    }

    // ---- lifecycle (Dart lifecycle.dart, plan 04 §5.5)

    /**
     * Starts listening: connects, reads the list (re-subscribes) and reconnects on loss until
     * [stopListening].
     */
    public fun startListening() {
        scope.launch {
            if (closed || listening) return@launch
            listening = true
            streamStatus.value = StreamStatus.Connecting
            recover()
        }
    }

    public fun stopListening() {
        scope.launch {
            listening = false
            retry?.cancel()
            retry = null
        }
    }

    private fun onTransportLost() {
        liveAgentSessions.clear()
        providerEntries = null
        providerRevision++
        requests.disconnect()
        localWork?.invalidate()
        hooks.forEach { it.onDisconnect() }
        refreshCapabilities()
        recoveryDegraded = true
        if (listening && !closed)
            streamStatus.value = StreamStatus.Reconnecting(retryAttempt + 1, null)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!listening || closed || retry != null || recovering != null) return
        val delayMs = 1_000L shl retryAttempt.coerceIn(0, 4)
        retryAttempt++
        val at = nowMs() + delayMs
        streamStatus.value =
            if (retryAttempt > 6) StreamStatus.Disconnected(retryAttempt, at)
            else StreamStatus.Reconnecting(retryAttempt, at)
        retry = scope.launch {
            delay(delayMs)
            retry = null
            recover()
        }
    }

    private fun recover() {
        if (recovering != null) return
        recovering = scope.launch {
            try {
                recoverNow()
            } finally {
                recovering = null
                if (listening && !closed && (!transport.connected || recoveryDegraded))
                    scheduleReconnect()
            }
        }
    }

    private suspend fun recoverNow() {
        try {
            transport.connect()
        } catch (e: PaseoException) {
            lastRecoveryFailure = e.toFailure()
            recoveryDegraded = true
            if (
                e.kind == PaseoFailureKind.Authentication || e.kind == PaseoFailureKind.HostRefused
            ) {
                // Terminal: no more tries until Try again or a saved password (plan 04 §5.5).
                streamStatus.value = StreamStatus.Stopped(e.toFailure())
                listening = false
            }
            return
        }
        refreshCapabilities()
        // Turn boundaries may have been missed while away: snapshots read next are the authority.
        val fresh = reconciledEpoch != transport.epoch
        if (fresh) {
            reconciledEpoch = transport.epoch
            awaitingTurn.clear()
            turnActive.clear()
        }
        var announced = !fresh
        // "Connected" after the recovery read or 3 s, whichever is first.
        var failure: PaseoException? = null
        val listJob = scope.launch {
            try {
                readRecoveryList()
            } catch (e: PaseoException) {
                failure = e
            }
        }
        if (fresh) {
            withTimeoutOrNull(ANNOUNCE_GRACE_MS) { listJob.join() }
            if (listJob.isActive && !closed && listening) {
                announced = true
                announceConnected()
            }
        }
        listJob.join()
        if (closed || !listening) return
        val failed = failure
        if (failed != null) {
            lastRecoveryFailure = failed.toFailure()
            recoveryDegraded = true
            if (!transport.connected) {
                streamStatus.value = StreamStatus.Reconnecting(retryAttempt + 1, null)
            } else if (!announced) {
                announceConnected()
            }
            return
        }
        lastRecoveryFailure = null
        recoveryDegraded = false
        retryAttempt = 0
        if (!announced) announceConnected()
    }

    private fun announceConnected() {
        streamStatus.value = StreamStatus.Connected
        connectCount.value += 1
        emit(GatewayEvent.Connected)
    }

    private suspend fun readRecoveryList() {
        if (directoryValue == null) return
        // Listing re-subscribes and makes the daemon report pending permissions (list before
        // requests).
        if (localWork != null || hooks.any { it.wantsAllPages }) readAllSessions()
        else readSessionPage(null, 200)
    }

    override suspend fun close() {
        withContext(lane) {
            if (closed) return@withContext
            closed = true
            listening = false
            locationEpoch++
            providerRevision++
            retry?.cancel()
            retry = null
            agents.clear()
            sessions.clear()
            statuses.clear()
            cancelRecordBusyExpiry()
            drafts.clear()
            live.clear()
            realIds.clear()
            appIds.clear()
            heldEvents.clear()
            requests.disconnect()
            hooks.forEach { it.onClosed() }
            removeListener()
            transport.close()
            job.cancel()
        }
    }

    public companion object {
        /** The runtime a conversation uses when the composer names no model. */
        public const val DEFAULT_PROVIDER: String = "claude"

        /** Placeholder model id for a provider that reports no model list. */
        public const val DEFAULT_MODEL: String = "default"

        /** The stand-in title of a draft (Dart writes the same words into the session). */
        public const val NEW_CONVERSATION_TITLE: String = "New conversation"
        public const val HISTORY_TIMEOUT_MS: Long = 45_000
        public const val SEND_TIMEOUT_MS: Long = 60_000
        public const val CREATE_TIMEOUT_MS: Long = 90_000
        public const val PROVIDERS_TIMEOUT_MS: Long = 45_000
        public const val ANNOUNCE_GRACE_MS: Long = 3_000

        /** Timelines this long are mapped off the lane (docs/qa/json-decode-2026-10-28). */
        public const val BACKGROUND_TIMELINE_ENTRIES: Int = 100
        private const val MAX_SESSIONS = 1024
        private val STEERING_PROVIDERS = setOf("claude", "codex", "opencode", "pi")
        private val DETAILS = Redactor()

        /** Questions changed (until core has QuestionAsked/QuestionSettled). */
        public val QUESTION_HINT: HintKind = HintKind("question")
    }
}

/** Live stream bookkeeping for one agent (Dart `_PaseoLive`). */
internal class PaseoLive {
    /** Message ids already announced, with the text streamed so far. */
    val announced = LinkedHashMap<String, StringBuilder>()

    /**
     * Reasoning and id-less assistant text arrive as runs of deltas sharing the id of their first
     * delta.
     */
    var runId: String? = null
    var runType: String? = null
    var lastSeq: Long = 0
    var epoch: String? = null

    /** The replies of the turn in progress, announced without a finish time. */
    val open = LinkedHashMap<String, io.github.eslamasabry.oc.core.model.transcript.MessageInfo>()

    /** Each tool call as reported so far, by call id, so a later report merges into it. */
    val tools = LinkedHashMap<String, JsonObject>()

    /**
     * The turn in progress already showed the agent's own error row: its failure needs no second
     * notice.
     */
    var turnErrorShown = false

    /** The saved record said its last turn failed before the helper loaded it. */
    var failedBeforeLoad = false
}

/**
 * Observers the integration installs (work inventory, chat feed). Every method runs on the
 * gateway's lane.
 */
internal interface PaseoGatewayHooks {
    val wantsAllPages: Boolean
        get() = false

    fun onPush(push: PaseoPush) {}

    fun onAgentPage(result: JsonObject, cursor: String?) {}

    fun onAgentRecorded(id: String, agent: JsonObject) {}

    fun onSessionForgotten(id: String) {}

    fun onLocationChanged() {}

    fun onDisconnect() {}

    fun onClosed() {}
}

// ---- small helpers

internal fun JsonObject.with(key: String, value: JsonElement): JsonObject =
    JsonObject(LinkedHashMap(this).apply { put(key, value) })

internal fun JsonObject.withoutNulls(): JsonObject = JsonObject(filterValues { it != JsonNull })

/** A random version-4 UUID for a new conversation or message id. */
internal fun paseoUuid(random: Random): String {
    val b = random.nextBytes(16)
    b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
    b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
    val h = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
}
