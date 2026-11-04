package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.PartId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.MidTurnPromptGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEnvelope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.GatewayEvents
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.domain.unavailable
import io.github.eslamasabry.oc.core.model.AgentInfo
import io.github.eslamasabry.oc.core.model.EventLocation
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.MidTurnPrompt
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PermissionTool
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.model.ProviderInfo
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.Todo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.protocol.codex.CodexCapabilities
import io.github.eslamasabry.oc.protocol.codex.CodexException
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.account.CodexAccountSession
import io.github.eslamasabry.oc.protocol.codex.codexAttempt
import io.github.eslamasabry.oc.protocol.codex.codexFail
import io.github.eslamasabry.oc.protocol.codex.codexKind
import io.github.eslamasabry.oc.protocol.codex.codexList
import io.github.eslamasabry.oc.protocol.codex.codexObject
import io.github.eslamasabry.oc.protocol.codex.codexString
import io.github.eslamasabry.oc.protocol.codex.handoff.HandoffMessage
import io.github.eslamasabry.oc.protocol.codex.handoff.codexHandoffMessages
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.probe.CodexProbeGateway
import io.github.eslamasabry.oc.protocol.codex.stringOrNull
import io.github.eslamasabry.oc.protocol.codex.transport.CodexRpcEvent
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransportListener
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/**
 * The Codex gateway, pinned to app-server 0.153.4 (port of lib/codex/gateway.dart).
 *
 * One app-server *thread* is one conversation, scoped to one folder (the location's directory): a thread whose `cwd`
 * is another folder never enters a list, a send, a resume or a handoff (B-AGENT-063). Every operation captures the
 * location generation and the transport epoch; a read that crosses a location change never reaches its mutation, and
 * a mutation whose reply crossed one is uncertain (B-AGENT-065). A lost mutation reply is never re-sent: `prompt`
 * answers [PromptAdmission.Uncertain] and every later send to that thread is refused until its history is recovered
 * (B-AGENT-062). Approvals are one-shot and die with a location change or the socket (B-AGENT-064). Steering targets
 * only the authoritative active turn (B-AGENT-070).
 *
 * Everything runs on the transport's lane; public calls hop onto it and answer an [Outcome].
 */
public class CodexGateway(
    public val transport: CodexTransport,
    scope: CoroutineScope,
    private val clock: Clock,
    directory: String?,
) : ServerGateway, MidTurnPromptGateway, CodexProbeGateway {
    private val lane = transport.lane
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + job + lane)

    private var directory: String? = directory
    private var closed = false
    private val sessions = LinkedHashMap<String, Session>()
    private val statuses = HashMap<String, SessionStatus>()
    private val turns = HashMap<String, String>()
    private val resumed = LinkedHashSet<String>()
    private val approvals = LinkedHashMap<String, Approval>()
    private val uncertain = HashSet<String>()
    private val newEmptyThreads = HashSet<String>()
    private val fileChanges = LinkedHashMap<Pair<String, String>, JsonArray>()
    private var locationEpoch = 0
    private var listening = false
    private var recoveryDegraded = false
    private var recovering: Job? = null
    private var retry: Job? = null
    private var retryAttempt = 0

    private val stream = MutableSharedFlow<GatewayEnvelope>(extraBufferCapacity = 4096)
    private val streamStatus = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
    private val connectCount = MutableStateFlow(0)
    private val locationFlow = MutableStateFlow(ServerLocation(directory = directory))

    private class Approval(val request: CodexRpcEvent, val permission: PermissionRequest, val turnId: String)

    private class Operation(val scope: String, val locationEpoch: Int, val transportEpoch: Int)

    init {
        transport.addListener(
            object : CodexTransportListener {
                override fun onEvent(event: CodexRpcEvent) = onRpc(event)

                override fun onDisconnect(epoch: Int) {
                    approvals.clear()
                    turns.clear()
                    recoveryDegraded = true
                    emitState(reconnecting())
                    scheduleReconnect()
                }
            },
        )
    }

    override val capabilities: StateFlow<ServerCapabilities> = MutableStateFlow(CodexCapabilities.Base).asStateFlow()

    override val location: StateFlow<ServerLocation> = locationFlow.asStateFlow()

    override val events: GatewayEvents =
        object : GatewayEvents {
            override val status: StateFlow<StreamStatus> = streamStatus.asStateFlow()
            override val project: Flow<GatewayEnvelope> = stream.asSharedFlow()
            override val all: Flow<GatewayEnvelope> = stream.asSharedFlow()
            override val connects: StateFlow<Int> = connectCount.asStateFlow()

            override fun retryNow() {
                scope.launch {
                    retry?.cancel()
                    retry = null
                    recover()
                }
            }
        }

    /** Bound to the current location and transport epoch (B-AGENT-051…054). */
    public fun openAccountSession(): CodexAccountSession {
        val epoch = locationEpoch
        return CodexAccountSession(transport) { !closed && epoch == locationEpoch }
    }

    // ---- location and fences

    private fun scopeDirectory(): String {
        val value = directory
        if (value.isNullOrEmpty() || value.length > 4096 || CONTROL.containsMatchIn(value)) codexFail(CodexFailureKind.ScopeMismatch)
        return value
    }

    /** Codex has no workspaces: a workspace is ignored. A changed folder drops every scoped fact. */
    override fun setLocation(location: ServerLocation) {
        if (directory == location.directory) return
        directory = location.directory
        locationFlow.value = ServerLocation(directory = location.directory)
        locationEpoch++
        sessions.clear()
        statuses.clear()
        turns.clear()
        approvals.clear()
        resumed.clear()
        uncertain.clear()
        newEmptyThreads.clear()
        fileChanges.clear()
        recoveryDegraded = false
    }

    private fun checkLocation(scope: String, epoch: Int) {
        if (closed || scope != directory || epoch != locationEpoch) codexFail(CodexFailureKind.ScopeMismatch)
    }

    private suspend fun captureOperation(): Operation {
        val scope = scopeDirectory()
        val epoch = locationEpoch
        transport.connect()
        checkLocation(scope, epoch)
        return Operation(scope, epoch, transport.epoch)
    }

    private fun checkOperation(op: Operation, mutation: Boolean = false) {
        checkLocation(op.scope, op.locationEpoch)
        if (!transport.connected || transport.epoch != op.transportEpoch) {
            codexFail(if (mutation) CodexFailureKind.DeliveryUnknown else CodexFailureKind.Disconnected)
        }
    }

    private fun remember(thread: JsonObject, scope: String, epoch: Int): Session {
        checkLocation(scope, epoch)
        val session = codexSession(thread, scope)
        val id = session.id.raw
        sessions.remove(id)
        sessions[id] = session
        statuses[id] = codexSessionStatus(thread)
        while (sessions.size > 512) {
            val old = sessions.keys.firstOrNull { it !in resumed } ?: break
            sessions.remove(old)
            statuses.remove(old)
            turns.remove(old)
        }
        return session
    }

    private suspend fun readThread(id: String, withTurns: Boolean = false, operation: Operation? = null): JsonObject {
        codexString(id, max = 256)
        val op = operation ?: captureOperation()
        checkOperation(op)
        val result = transport.request("thread/read", jsonOf("threadId" to id, "includeTurns" to withTurns))
        checkOperation(op)
        val thread = codexObject(result["thread"])
        if (thread["id"].stringOrNull() != id) codexFail(CodexFailureKind.InvalidResponse)
        remember(thread, op.scope, op.locationEpoch)
        if (withTurns) {
            turns.remove(id)
            val history = codexList(thread["turns"])
            if (history.isNotEmpty()) newEmptyThreads.remove(id)
            for (raw in history) {
                val turn = codexObject(raw)
                if (turn["status"].stringOrNull() != "inProgress") continue
                turns[id] = codexString(turn["id"], max = 256)
                for (rawItem in codexList(turn["items"])) {
                    val item = codexObject(rawItem)
                    if (item["type"].stringOrNull() == "fileChange") {
                        fileChanges[id to codexString(item["id"], max = 256)] = codexList(item["changes"], max = 1000)
                    }
                }
            }
        }
        return thread
    }

    private suspend fun resume(id: String): JsonObject {
        val op = captureOperation()
        readThread(id, withTurns = true, operation = op)
        checkOperation(op)
        val result = transport.request("thread/resume", jsonOf("threadId" to id))
        checkOperation(op)
        val thread = codexObject(result["thread"])
        if (thread["id"].stringOrNull() != id) codexFail(CodexFailureKind.InvalidResponse)
        remember(thread, op.scope, op.locationEpoch)
        resumed.remove(id)
        resumed.add(id)
        while (resumed.size > MAX_RESUMED) {
            val old = resumed.first()
            resumed.remove(old)
            scope.launch {
                try {
                    transport.request("thread/unsubscribe", jsonOf("threadId" to old))
                } catch (_: CodexException) {
                    // Best effort.
                }
            }
        }
        for (raw in codexList(thread["turns"])) {
            val turn = codexObject(raw)
            if (turn["status"].stringOrNull() == "inProgress") turns[id] = codexString(turn["id"], max = 256)
        }
        return thread
    }

    private suspend inline fun <T> onLane(crossinline block: suspend () -> T): Outcome<T> =
        codexAttempt { withContext(lane) { block() } }

    // ---- health and sessions

    /** The app-server reports no version; health proves the authenticated handshake. */
    override suspend fun health(): Outcome<Health> =
        onLane {
            transport.connect()
            Health(healthy = true, version = null)
        }

    override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> =
        onLane {
            val scope = scopeDirectory()
            val epoch = locationEpoch
            val params = jsonOf(
                "cwd" to scope,
                "limit" to limit.coerceIn(1, 100),
                "cursor" to cursor?.let { codexString(it) },
                "archived" to false,
            ).let { o -> JsonObject(o.filterValues { it != kotlinx.serialization.json.JsonNull }) }
            val result = transport.request("thread/list", params)
            val items = codexList(result["data"], max = 100).map { remember(codexObject(it), scope, epoch) }
            val next = result["nextCursor"]?.takeUnless { it is kotlinx.serialization.json.JsonNull }
            ServerPage(items.toImmutableList(), next?.let { codexString(it) })
        }

    override suspend fun createSession(spec: NewSession): Outcome<Session> =
        onLane {
            val scope = scopeDirectory()
            val epoch = locationEpoch
            val result = transport.request(
                "thread/start",
                jsonOf("cwd" to scope, "approvalPolicy" to "on-request", "sandbox" to "read-only"),
                mutation = true,
            )
            val session = remember(codexObject(result["thread"]), scope, epoch)
            resumed.add(session.id.raw)
            newEmptyThreads.add(session.id.raw)
            session
        }

    override suspend fun session(id: SessionId): Outcome<Session> =
        onLane {
            val raw = id.raw
            // A new empty thread may not have a persisted rollout yet.
            val cached = sessions[raw]
            if (raw in resumed && cached != null && raw in newEmptyThreads) cached
            else codexSession(readThread(raw), scopeDirectory())
        }

    override suspend fun renameSession(id: SessionId, title: String): Outcome<Unit> =
        onLane {
            val op = captureOperation()
            readThread(id.raw, operation = op)
            checkOperation(op, mutation = true)
            transport.request("thread/name/set", jsonOf("threadId" to id.raw, "name" to codexString(title)), mutation = true)
            checkOperation(op, mutation = true)
        }

    override suspend fun deleteSession(id: SessionId): Outcome<Unit> =
        onLane {
            val raw = id.raw
            val op = captureOperation()
            readThread(raw, operation = op)
            checkOperation(op, mutation = true)
            transport.request("thread/delete", jsonOf("threadId" to raw), mutation = true)
            checkOperation(op, mutation = true)
            sessions.remove(raw)
            statuses.remove(raw)
            resumed.remove(raw)
            turns.remove(raw)
            newEmptyThreads.remove(raw)
            approvals.entries.removeAll { it.value.permission.sessionId == id }
        }

    override suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>> =
        onLane { statuses.entries.associate { SessionId(it.key) to it.value }.toImmutableMap() }

    /** Codex's compatibility history resumes the thread (authoritative); an untouched new thread is empty. */
    public suspend fun messages(id: SessionId): Outcome<List<MessageWithParts>> = onLane { messagesNow(id.raw) }

    private suspend fun messagesNow(id: String): List<MessageWithParts> {
        if (id in resumed && id in newEmptyThreads && sessions.containsKey(id) && id !in uncertain) {
            // thread/start does not persist an empty conversation; once a turn started, resume is authoritative.
            return emptyList()
        }
        val thread = resume(id)
        val result = codexMessages(thread)
        uncertain.remove(id)
        newEmptyThreads.remove(id)
        return result
    }

    override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> =
        onLane {
            if (cursor != null) codexFail(CodexFailureKind.Unavailable)
            ServerPage(messagesNow(id.raw).toImmutableList(), null)
        }

    override suspend fun todos(id: SessionId): Outcome<ImmutableList<Todo>> = Outcome.Ok(persistentListOf())

    override suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>> = Outcome.Ok(persistentListOf())

    /**
     * The full persisted thread for a handoff: one `thread/read(includeTurns)`, never `thread/resume` or a
     * subscription (B-AGENT-055). Project ownership is proven before any transcript text is mapped.
     */
    public suspend fun agentHandoffHistory(id: SessionId): Outcome<List<HandoffMessage>> =
        onLane {
            codexString(id.raw, max = 256)
            val op = captureOperation()
            checkOperation(op)
            val result = transport.requestInEpoch(
                "thread/read",
                jsonOf("threadId" to id.raw, "includeTurns" to true),
                epoch = op.transportEpoch,
            )
            checkOperation(op)
            val thread = codexObject(result["thread"])
            if (thread["id"].stringOrNull() != id.raw) codexFail(CodexFailureKind.InvalidResponse)
            codexSession(thread, op.scope)
            codexHandoffMessages(thread)
        }

    // ---- prompts, steering and stop

    /**
     * Sends one prompt. A busy thread (or [PromptDelivery.Steer]) is steered into its active turn, never queued and
     * never restarted. A dispatch whose reply was lost answers [PromptAdmission.Uncertain]; the thread then refuses
     * every send (`Refused(DispatchUncertain)`) until [messages] recovered its history.
     */
    override suspend fun prompt(session: SessionId, prompt: OutgoingPrompt, commandId: String?): Outcome<PromptAdmission> {
        var dispatched = false
        val outcome = onLane { promptNow(session.raw, prompt) { dispatched = true } }
        if (outcome is Outcome.Failed && dispatched && outcome.failure.codexKind() == CodexFailureKind.DeliveryUnknown) {
            return Outcome.Ok(PromptAdmission.Uncertain)
        }
        return outcome
    }

    private suspend fun promptNow(id: String, prompt: OutgoingPrompt, onDispatch: () -> Unit): PromptAdmission {
        val agent = prompt.agent
        if (prompt.attachments.isNotEmpty() || prompt.mentions.isNotEmpty() || prompt.delivery == PromptDelivery.Queue ||
            (agent != null && agent != "codex") || id in uncertain
        ) {
            codexFail(if (id in uncertain) CodexFailureKind.DeliveryUnknown else CodexFailureKind.Unavailable)
        }
        codexString(id, max = 256)
        val text = codexString(prompt.text, max = 1024 * 1024)
        if (prompt.delivery == PromptDelivery.Steer) return steer(id, text, onDispatch)
        if (id !in resumed) resume(id)
        if (!sessions.containsKey(id)) codexFail(CodexFailureKind.ScopeMismatch)
        if (statuses[id] == SessionStatus.Busy) return steer(id, text, onDispatch)
        val scope = scopeDirectory()
        val epoch = locationEpoch
        // Once dispatch begins, even an unacknowledged first turn must recover authoritatively.
        newEmptyThreads.remove(id)
        try {
            onDispatch()
            val result = transport.request("turn/start", startParams(id, text, prompt.model), mutation = true)
            checkLocation(scope, epoch)
            val turnId = codexString(codexObject(result["turn"])["id"], max = 256)
            turns[id] = turnId
            newEmptyThreads.remove(id)
            statuses[id] = SessionStatus.Busy
            return PromptAdmission.Confirmed(turnId)
        } catch (e: CodexException) {
            if (e.kind == CodexFailureKind.DeliveryUnknown) uncertain.add(id)
            throw e
        }
    }

    private fun startParams(id: String, text: String, model: ModelSelection?): JsonObject {
        val fields = linkedMapOf<String, JsonElement>(
            "threadId" to JsonPrimitive(id),
            "input" to inputOf(text),
        )
        model?.let { fields["model"] = JsonPrimitive(it.model.modelId) }
        model?.variant?.takeIf { it.isNotEmpty() }?.let { fields["effort"] = JsonPrimitive(it) }
        return JsonObject(fields)
    }

    private fun inputOf(text: String): JsonElement =
        JsonArray(listOf(jsonOf("type" to "text", "text" to text, "text_elements" to emptyList<Any>())))

    /**
     * Pinned 0.153.4 `turn/steer` takes input and an active-turn precondition. Reads authoritative state without
     * resuming; a stale target fails and is never retried as `turn/start` or `turn/interrupt` (B-AGENT-070).
     */
    private suspend fun steer(id: String, text: String, onDispatch: () -> Unit): PromptAdmission {
        val expected = if (statuses[id] == SessionStatus.Busy) turns[id] else null
        val op = captureOperation()
        checkOperation(op)
        val read = transport.requestInEpoch("thread/read", jsonOf("threadId" to id, "includeTurns" to true), epoch = op.transportEpoch)
        checkOperation(op)
        val thread = codexObject(read["thread"])
        if (thread["id"].stringOrNull() != id) codexFail(CodexFailureKind.InvalidResponse)
        codexSession(thread, op.scope)
        val active = codexList(thread["turns"]).map(::codexObject).filter { it["status"].stringOrNull() == "inProgress" }
        if (active.size != 1) codexFail(CodexFailureKind.StaleRequest)
        val turnId = codexString(active.single()["id"], max = 256)
        if (expected != null && (expected != turnId || turns[id] != expected || statuses[id] != SessionStatus.Busy)) {
            codexFail(CodexFailureKind.StaleRequest)
        }
        checkOperation(op)
        try {
            onDispatch()
            val result = transport.requestInEpoch(
                "turn/steer",
                JsonObject(mapOf("threadId" to JsonPrimitive(id), "input" to inputOf(text), "expectedTurnId" to JsonPrimitive(turnId))),
                epoch = op.transportEpoch,
                mutation = true,
            )
            // The steer may have been accepted even if the reply is malformed or the scope changed meanwhile.
            checkOperation(op, mutation = true)
            if (result["turnId"].stringOrNull() != turnId) codexFail(CodexFailureKind.DeliveryUnknown)
            // An acknowledgement is not a new turn: never overwrite an idle that arrived before it.
            return PromptAdmission.Confirmed(turnId)
        } catch (e: CodexException) {
            if (e.kind == CodexFailureKind.DeliveryUnknown || e.kind == CodexFailureKind.InvalidResponse ||
                e.kind == CodexFailureKind.ScopeMismatch
            ) {
                if (locationEpoch == op.locationEpoch && directory == op.scope) uncertain.add(id)
                codexFail(CodexFailureKind.DeliveryUnknown)
            }
            throw e
        }
    }

    override fun midTurnPrompt(session: SessionId): MidTurnPrompt? =
        if (sessions.containsKey(session.raw)) MidTurnPrompt.JoinsTurn else null

    override suspend fun shell(session: SessionId, command: String, agent: String, model: ModelSelection?): Outcome<Unit> =
        unavailable("conversationShell")

    override suspend fun slashCommand(session: SessionId, command: String, args: String, model: ModelSelection?): Outcome<Unit> =
        unavailable("slashCommands")

    /** Interrupts the one authoritative active turn; nothing running → [StopResult.NothingRunning]; lost → Uncertain. */
    override suspend fun stop(session: SessionId, resume: Boolean): Outcome<StopResult> {
        val outcome = onLane {
            val op = captureOperation()
            val thread = readThread(session.raw, withTurns = true, operation = op)
            val active = codexList(thread["turns"]).map(::codexObject).filter { it["status"].stringOrNull() == "inProgress" }
            when (active.size) {
                0 -> StopResult.NothingRunning
                1 -> {
                    checkOperation(op, mutation = true)
                    transport.request(
                        "turn/interrupt",
                        jsonOf("threadId" to session.raw, "turnId" to codexString(active.single()["id"], max = 256)),
                        mutation = true,
                    )
                    checkOperation(op, mutation = true)
                    StopResult.Stopped
                }
                else -> codexFail(CodexFailureKind.StaleRequest)
            }
        }
        if (outcome is Outcome.Failed && outcome.failure.codexKind() == CodexFailureKind.DeliveryUnknown) return Outcome.Ok(StopResult.Uncertain)
        return outcome
    }

    // ---- approvals

    override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> =
        onLane { approvals.values.filter { it.request.epoch == transport.epoch }.map { it.permission }.toImmutableList() }

    /** One-shot only: `Always` is refused; a stale, foreign or old-epoch approval fails `StaleRequest`. */
    override suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, message: String?): Outcome<Unit> =
        onLane {
            val approval = approvals[request.raw]
            if (approval == null || approval.request.epoch != transport.epoch || approval.permission.sessionId != session) {
                codexFail(CodexFailureKind.StaleRequest)
            }
            val decision = when (reply) {
                PermissionReply.Once -> "accept"
                PermissionReply.Reject -> "decline"
                PermissionReply.Always -> codexFail(CodexFailureKind.Unavailable)
            }
            transport.reply(approval.request, jsonOf("decision" to decision))
            approvals.remove(request.raw)
            emit(GatewayEvent.PermissionSettled(session, request))
        }

    // ---- catalogs

    override suspend fun providers(): Outcome<ProvidersResponse> =
        onLane {
            val models = mutableListOf<String>()
            var defaultModel: String? = null
            var cursor: String? = null
            val seen = HashSet<String>()
            for (page in 0 until 10) {
                val params = if (cursor == null) jsonOf("limit" to 100) else jsonOf("limit" to 100, "cursor" to cursor)
                val result = transport.request("model/list", params)
                for (raw in codexList(result["data"], max = 100)) {
                    val model = codexObject(raw)
                    if (model["hidden"]?.toString() == "true") continue
                    val id = codexString(model["model"], max = 256)
                    if (id !in models) models += id
                    if (model["isDefault"]?.toString() == "true") defaultModel = id
                }
                val next = result["nextCursor"]?.takeUnless { it is kotlinx.serialization.json.JsonNull } ?: break
                val nextCursor = codexString(next)
                if (!seen.add(nextCursor) || page == 9) codexFail(CodexFailureKind.InvalidResponse)
                cursor = nextCursor
            }
            val provider = ProviderInfo("codex", "Codex server models", models.toImmutableList(), persistentMapOf())
            ProvidersResponse(persistentListOf(provider), persistentListOf(provider), "codex", defaultModel)
        }

    override suspend fun configuredProviders(): Outcome<ProvidersResponse> = providers()

    override suspend fun agents(): Outcome<ImmutableList<AgentInfo>> =
        Outcome.Ok(persistentListOf(AgentInfo(name = "codex", mode = "primary", color = null, model = null)))

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> = unavailable("fileBrowsing")

    override suspend fun fileContent(path: String): Outcome<FileContent> = unavailable("fileBrowsing")

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> = unavailable("fileBrowsing")

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> = unavailable("textSearch")

    // ---- events

    private fun emit(event: GatewayEvent) {
        if (closed) return
        stream.tryEmit(GatewayEnvelope(event, EventLocation(directory, null, null), clock.now().toEpochMilliseconds()))
    }

    private fun emitState(state: StreamStatus) {
        if (closed || !listening) return
        streamStatus.value = state
        if (state == StreamStatus.Connected) {
            connectCount.value += 1
            emit(GatewayEvent.Connected)
        }
    }

    private fun reconnecting(): StreamStatus = StreamStatus.Reconnecting(retryAttempt, null)

    private fun onRpc(event: CodexRpcEvent) {
        if (closed || event.epoch != transport.epoch) return
        try {
            handleRpc(event)
        } catch (_: CodexException) {
            // Invalid requests never become approvals. No payload reaches logs.
            if (event.requestId != null) transport.rejectUnsupported(event)
        }
    }

    private fun handleRpc(event: CodexRpcEvent) {
        val p = event.params
        if (event.method == "thread/started") {
            val thread = codexObject(p["thread"])
            if (thread["cwd"].stringOrNull() != directory) return
            emit(GatewayEvent.SessionUpserted(remember(thread, scopeDirectory(), locationEpoch)))
            return
        }
        val idElement = p["threadId"]
        val id = idElement.stringOrNull()
        if (id == null || !sessions.containsKey(id)) {
            if (event.requestId != null && idElement == null) transport.rejectUnsupported(event)
            return
        }
        val sid = SessionId(id)
        when (event.method) {
            "thread/status/changed" -> {
                val status = codexSessionStatus(jsonOf("status" to p["status"]))
                statuses[id] = status
                emit(if (status == SessionStatus.Busy) GatewayEvent.RunStarted(sid) else GatewayEvent.RunEnded(sid, RunOutcome.Idle))
            }
            "turn/started" -> {
                turns[id] = codexString(codexObject(p["turn"])["id"], max = 256)
                newEmptyThreads.remove(id)
                statuses[id] = SessionStatus.Busy
                emit(GatewayEvent.RunStarted(sid))
            }
            "turn/completed" -> {
                val turn = codexObject(p["turn"])
                val turnId = codexString(turn["id"], max = 256)
                if (turns[id] != turnId) return
                statuses[id] = SessionStatus.Idle
                val settled = approvals.filterValues { it.permission.sessionId == sid && it.turnId == turnId }.keys
                approvals.keys.removeAll(settled)
                val outcome = when (turn["status"].stringOrNull()) {
                    "failed" -> RunOutcome.Failed(null, TURN_FAILED, null)
                    "interrupted" -> RunOutcome.Interrupted(InterruptReason.Unknown)
                    // Idle is never reported as a successful run.
                    else -> RunOutcome.Idle
                }
                emit(GatewayEvent.RunEnded(sid, outcome))
                // A completion asks for authoritative hydration.
                emit(GatewayEvent.TranscriptStale(sid))
            }
            "item/started", "item/completed" -> {
                if (p["turnId"].stringOrNull() != turns[id]) return
                val item = codexObject(p["item"])
                if (item["type"].stringOrNull() == "fileChange") {
                    fileChanges[id to codexString(item["id"], max = 256)] = codexList(item["changes"], max = 1000)
                    while (fileChanges.size > 64) fileChanges.remove(fileChanges.keys.first())
                }
                val completed = if (event.method == "item/completed") clock.now().toEpochMilliseconds() else null
                val message = codexItemMessage(id, item, completedMs = completed)
                emit(GatewayEvent.MessageUpserted(message.info))
                for (part in message.parts) emit(GatewayEvent.PartUpserted(part))
            }
            "item/agentMessage/delta", "item/reasoning/summaryTextDelta" -> {
                if (p["turnId"].stringOrNull() != turns[id]) return
                val itemId = codexString(p["itemId"], max = 256)
                emit(
                    GatewayEvent.PartDelta(
                        sid, MessageId(itemId), PartId("$itemId:0"),
                        if (event.method == "item/agentMessage/delta") DeltaField.Text else DeltaField.Reasoning,
                        codexString(p["delta"], max = 1024 * 1024, empty = true),
                    ),
                )
            }
            "item/commandExecution/requestApproval", "item/fileChange/requestApproval" -> addApproval(event, id)
            "serverRequest/resolved" -> {
                val resolved = p["requestId"]
                val removed = approvals.filterValues { it.request.requestId == resolved && it.permission.sessionId == sid }.keys.toList()
                for (key in removed) {
                    approvals.remove(key)
                    emit(GatewayEvent.PermissionSettled(sid, RequestId(key)))
                }
            }
            else -> if (event.requestId != null) transport.rejectUnsupported(event)
        }
    }

    private fun addApproval(event: CodexRpcEvent, sessionId: String) {
        val p = event.params
        val turnId = codexString(p["turnId"], max = 256)
        val rawId = event.requestId
        if (rawId == null || approvals.size >= 64 || statuses[sessionId] != SessionStatus.Busy || turns[sessionId] != turnId) {
            transport.rejectUnsupported(event)
            return
        }
        val itemId = codexString(p["itemId"], max = 256)
        val rawIsString = (rawId as? JsonPrimitive)?.isString == true
        val rawText = if (rawIsString) codexString(rawId, max = 256) else (rawId as JsonPrimitive).content
        val requestId = "codex:${event.epoch}:${if (rawIsString) "s" else "n"}:$rawText"
        val network = p["networkApprovalContext"] as? JsonObject
        val permission: String
        val patterns: List<String>
        val metadata: JsonObject
        if (event.method == "item/fileChange/requestApproval") {
            val changes = fileChanges[sessionId to itemId]
            if (changes == null || changes.isEmpty()) {
                transport.rejectUnsupported(event)
                return
            }
            permission = "patch"
            patterns = changes.map { codexString(codexObject(it)["path"]) }
            val diff = changes.joinToString("\n") { raw ->
                val change = codexObject(raw)
                "${codexString(change["path"])}\n${codexString(change["diff"], max = 1024 * 1024, empty = true)}"
            }
            metadata = if (patterns.size == 1) jsonOf("filePath" to patterns.single(), "diff" to diff) else jsonOf("diff" to diff)
        } else if (network != null) {
            permission = "network"
            val host = codexString(network["host"], max = 1024)
            patterns = listOf(host)
            metadata = jsonOf("host" to host, "protocol" to codexString(network["protocol"], max = 64))
        } else {
            permission = "shell"
            val preview = codexString(p["command"], max = 65_536)
            patterns = listOf(preview)
            metadata = jsonOf("command" to preview)
        }
        val reason = p["reason"].stringOrNull()?.let { codexString(it, max = 4096, empty = true) }
        val request = PermissionRequest(
            id = RequestId(requestId),
            sessionId = SessionId(sessionId),
            permission = permission,
            patterns = patterns.toImmutableList(),
            metadata = metadata,
            always = persistentListOf(),
            tool = PermissionTool(MessageId(itemId), itemId),
            canAlwaysAllow = false,
            message = reason,
        )
        approvals[requestId] = Approval(event, request, turnId)
        emit(GatewayEvent.PermissionAsked(request))
    }

    // ---- listening and recovery

    /** Starts the event loop (Dart `openEventChannel().start()`): Connecting, then connect and resume tracked threads. */
    public fun startListening() {
        scope.launch {
            if (closed || listening) return@launch
            listening = true
            emitState(StreamStatus.Connecting)
            recover()
        }
    }

    /** Stops reconnecting (Dart channel dispose). */
    public fun stopListening() {
        scope.launch {
            listening = false
            retry?.cancel()
            retry = null
        }
    }

    private fun scheduleReconnect() {
        if (!listening || closed || retry != null || recovering != null) return
        val delayMs = 1000L shl retryAttempt.coerceIn(0, 4)
        retryAttempt++
        retry = scope.launch {
            delay(delayMs)
            retry = null
            recover()
        }
    }

    private fun recover() {
        if (recovering != null || closed) return
        recovering = scope.launch {
            try {
                recoverNow()
            } finally {
                recovering = null
                if (!transport.connected || recoveryDegraded) scheduleReconnect()
            }
        }
    }

    private suspend fun recoverNow() {
        try {
            transport.connect()
            var failed = false
            for (id in resumed.toList()) {
                if (closed || !listening) return
                if (id in newEmptyThreads && id !in uncertain) {
                    // A never-dispatched thread may have no persisted rollout: retire it before any automatic RPC,
                    // keeping its metadata; explicit access reads the server again.
                    resumed.remove(id)
                    continue
                }
                try {
                    resume(id)
                } catch (_: CodexException) {
                    // Keep it tracked: a replacement socket is not a complete recovery while a thread needs resuming.
                    failed = true
                }
            }
            if (failed || !transport.connected) {
                recoveryDegraded = true
                emitState(reconnecting())
                return
            }
            recoveryDegraded = false
            retryAttempt = 0
            emitState(StreamStatus.Connected)
        } catch (e: CodexException) {
            recoveryDegraded = true
            if (e.kind == CodexFailureKind.Authentication) {
                // The error channel: a refused token stops the loop; retrying cannot fix it.
                emitState(StreamStatus.Stopped(Failure.SignIn(SignInWhy.TokenRejected)))
                listening = false
            } else {
                emitState(reconnecting())
            }
        }
    }

    /** Idempotent: stops the loop, forgets every scoped fact and closes the transport. */
    override suspend fun close() {
        withContext(lane) {
            if (closed) return@withContext
            closed = true
            listening = false
            locationEpoch++
            retry?.cancel()
            retry = null
            sessions.clear()
            approvals.clear()
            resumed.clear()
            turns.clear()
            statuses.clear()
            uncertain.clear()
            newEmptyThreads.clear()
            fileChanges.clear()
            transport.close()
            job.cancel()
        }
    }

    public val isClosed: Boolean get() = closed

    private companion object {
        const val MAX_RESUMED = 8
        val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
        val TURN_FAILED = io.github.eslamasabry.oc.core.common.WireTag("CodexTurnFailed")
    }
}
