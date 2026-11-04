package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.*
import kotlin.time.Clock
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** One immutable persisted read supplies both metadata and full review history. */
public data class CodexHandoffSnapshot(
    val session: Session,
    val messages: ImmutableList<MessageWithParts>,
)

/** Codex 0.153.4 app-server adapter. Scope and socket epochs fence every asynchronous operation. */
public class CodexGateway(
    public val transport: CodexTransport,
    private val scope: CoroutineScope,
    private val lane: CoroutineDispatcher,
    private val clock: Clock,
    location: ServerLocation,
    private val reconnectAllowed: StateFlow<Boolean>,
) : ServerGateway, MidTurnPromptGateway {
    private data class Context(
        val directory: String,
        val locationEpoch: Int,
        val transportEpoch: Int,
    )

    private data class Approval(
        val push: CodexPush,
        val request: PermissionRequest,
        val turn: String,
    )

    private val mutableLocation = MutableStateFlow(location)
    override val location: StateFlow<ServerLocation> = mutableLocation.asStateFlow()
    private val mutableCapabilities = MutableStateFlow(CodexCapabilities.Base)
    override val capabilities: StateFlow<ServerCapabilities> = mutableCapabilities.asStateFlow()
    override val scopeRevision: Long
        get() = (locationEpoch.toLong() shl 32) + transport.epoch

    private var locationEpoch = 0
    private var closed = false
    private val sessions = linkedMapOf<String, Session>()
    private val statuses = linkedMapOf<String, SessionStatus>()
    private val turns = mutableMapOf<String, String>()
    private val resumed = linkedSetOf<String>()
    private val emptyThreads = mutableSetOf<String>()
    private val uncertain = mutableSetOf<String>()
    private val approvals = mutableMapOf<String, Approval>()
    private val changes = linkedMapOf<Pair<String, String>, JsonArray>()
    private val flow = MutableSharedFlow<GatewayEnvelope>()
    private val settledTurns = linkedSetOf<String>()
    private val mutableStatus = MutableStateFlow<StreamStatus>(StreamStatus.Connecting)
    private val mutableConnects = MutableStateFlow(0)
    private var recovery: Job? = null
    private var recoveredEpoch = -1
    private val recoveringEpoch = kotlinx.coroutines.sync.Mutex()
    private val accountSessions = mutableListOf<CodexAccountSession>()
    override val events: GatewayEvents =
        object : GatewayEvents {
            override val status: StateFlow<StreamStatus> = mutableStatus.asStateFlow()
            override val connects: StateFlow<Int> = mutableConnects.asStateFlow()
            override val project: Flow<GatewayEnvelope> = flow.asSharedFlow()
            override val all: Flow<GatewayEnvelope> = flow.asSharedFlow()

            override fun retryNow() {
                scheduleRecovery(immediate = true)
            }
        }

    init {
        transport.onPush = { push ->
            for (account in accountSessions.toList()) account.onPush(push)
            onPush(push)
        }
        transport.onDisconnect = {
            approvals.clear()
            for (account in accountSessions.toList()) account.disconnected()
            mutableCapabilities.value = CodexCapabilities.Base
            mutableStatus.value = StreamStatus.Reconnecting(1, null)
            if (reconnectAllowed.value) scheduleRecovery()
        }
    }

    public suspend fun start(): Outcome<Unit> = call {
        transport.connect()
        recoveredEpoch = transport.epoch
        mutableCapabilities.value =
            CodexCapabilities.Base.copy(agentAccount = transport.accountSupported)
        mutableStatus.value = StreamStatus.Connected
        mutableConnects.value++
        emit(GatewayEvent.Connected)
    }

    /**
     * Each account panel owns its own login handle, tied to this exact project and transport epoch.
     */
    public fun accountSession(): CodexAccountSession {
        val generation = locationEpoch
        lateinit var account: CodexAccountSession
        account =
            CodexAccountSession(
                transport,
                lane,
                { !closed && generation == locationEpoch },
                { accountSessions.remove(account) },
            )
        accountSessions += account
        return account
    }

    override fun setLocation(location: ServerLocation) {
        if (location == mutableLocation.value) return
        mutableLocation.value = location
        locationEpoch++
        sessions.clear()
        statuses.clear()
        turns.clear()
        approvals.clear()
        resumed.clear()
        uncertain.clear()
        emptyThreads.clear()
        changes.clear()
        settledTurns.clear()
        for (account in accountSessions.toList()) account.invalidate()
    }

    private fun directory(): String =
        mutableLocation.value.directory?.takeIf {
            it.startsWith('/') &&
                it.length <= 4096 &&
                it.none { ch -> ch.code < 32 || ch.code == 127 }
        } ?: stale()

    private suspend fun capture(restore: Boolean = true): Context {
        val dir = directory()
        val epoch = locationEpoch
        transport.connect()
        if (closed || epoch != locationEpoch || dir != directory()) stale()
        val context = Context(dir, epoch, transport.epoch)
        recoveringEpoch.lock()
        try {
            if (restore && recoveredEpoch != transport.epoch) {
                restoreTracked(context)
                check(context)
                recoveredEpoch = transport.epoch
            }
        } finally {
            recoveringEpoch.unlock()
        }
        return context
    }

    private fun check(context: Context, mutation: Boolean = false) {
        if (closed || context.locationEpoch != locationEpoch || context.directory != directory()) {
            if (mutation) unknown() else stale()
        }
        if (!transport.connected || context.transportEpoch != transport.epoch) {
            if (mutation) unknown() else throw CodexException(Failure.ComputerDidNotAnswer())
        }
    }

    private fun remember(thread: JsonObject, context: Context): Session {
        check(context)
        val session = codexSession(thread, context.directory)
        val id = session.id.raw
        sessions.remove(id)
        sessions[id] = session
        statuses[id] = codexStatus(thread)
        while (sessions.size > 512) {
            val old = sessions.keys.firstOrNull { it !in resumed } ?: break
            sessions.remove(old)
            statuses.remove(old)
            turns.remove(old)
        }
        return session
    }

    private suspend fun readThread(
        id: String,
        context: Context,
        withTurns: Boolean = false,
    ): JsonObject {
        if (id.isBlank() || id.length > 256) malformed()
        check(context)
        val response =
            transport.request(
                "thread/read",
                json("threadId" to s(id), "includeTurns" to JsonPrimitive(withTurns)),
                expectedEpoch = context.transportEpoch,
            )
        check(context)
        val thread = response["thread"].obj()
        if (thread["id"].str(256) != id) malformed()
        remember(thread, context)
        if (withTurns) rememberTurns(id, thread)
        return thread
    }

    private fun rememberTurns(id: String, thread: JsonObject) {
        turns.remove(id)
        val active =
            thread["turns"].list().map { it.obj() }.filter { it.text("status") == "inProgress" }
        if (active.size > 1) malformed()
        active.singleOrNull()?.let { turn ->
            turns[id] = turn["id"].str(256)
            for (raw in turn["items"].list()) {
                val item = raw.obj()
                if (item.text("type") == "fileChange")
                    changes[id to item["id"].str(256)] = item["changes"].list(1000)
            }
        }
    }

    private suspend fun resume(id: String, context: Context): JsonObject {
        readThread(id, context, true)
        check(context)
        val result =
            transport.request(
                "thread/resume",
                json("threadId" to s(id)),
                expectedEpoch = context.transportEpoch,
            )
        check(context)
        val thread = result["thread"].obj()
        if (thread["id"].str(256) != id) malformed()
        remember(thread, context)
        rememberTurns(id, thread)
        resumed.remove(id)
        resumed.add(id)
        while (resumed.size > 8) {
            val old = resumed.first()
            resumed.remove(old)
            transport.request(
                "thread/unsubscribe",
                json("threadId" to s(old)),
                expectedEpoch = context.transportEpoch,
            )
            check(context)
        }
        return thread
    }

    override suspend fun health(): Outcome<Health> = call {
        transport.connect()
        Health(true, transport.serverVersion)
    }

    override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> =
        call {
            val context = capture()
            val result =
                transport.request(
                    "thread/list",
                    json(
                        "cwd" to s(context.directory),
                        "limit" to JsonPrimitive(limit.coerceIn(1, 100)),
                        "archived" to JsonPrimitive(false),
                        "cursor" to cursor?.let(::s),
                    ),
                    expectedEpoch = context.transportEpoch,
                )
            check(context)
            val rows =
                result["data"].list(100).map { remember(it.obj(), context) }.toImmutableList()
            val next = result["nextCursor"]?.takeUnless { it == JsonNull }?.str()
            ServerPage(rows, next)
        }

    override suspend fun createSession(spec: NewSession): Outcome<Session> = call {
        val context = capture()
        if (spec.parentId != null || (spec.location != null && spec.location != location.value))
            unavailable("sessionFork")
        val result =
            transport.request(
                "thread/start",
                json(
                    "cwd" to s(context.directory),
                    "approvalPolicy" to s("on-request"),
                    "sandbox" to s("read-only"),
                ),
                mutation = true,
                expectedEpoch = context.transportEpoch,
            )
        check(context, true)
        val session = remember(result["thread"].obj(), context)
        resumed.add(session.id.raw)
        emptyThreads.add(session.id.raw)
        session
    }

    override suspend fun session(id: SessionId): Outcome<Session> = call {
        val context = capture()
        if (id.raw in emptyThreads && id.raw in resumed && id.raw !in uncertain)
            sessions[id.raw] ?: malformed()
        else codexSession(readThread(id.raw, context), context.directory)
    }

    override suspend fun renameSession(id: SessionId, title: String): Outcome<Unit> = call {
        val context = capture()
        readThread(id.raw, context)
        check(context)
        if (title.isBlank() || title.length > 4096) malformed()
        transport.request(
            "thread/name/set",
            json("threadId" to s(id.raw), "name" to s(title)),
            true,
            context.transportEpoch,
        )
        check(context, true)
    }

    override suspend fun deleteSession(id: SessionId): Outcome<Unit> = call {
        val context = capture()
        readThread(id.raw, context)
        check(context)
        transport.request(
            "thread/delete",
            json("threadId" to s(id.raw)),
            true,
            context.transportEpoch,
        )
        check(context, true)
        sessions.remove(id.raw)
        statuses.remove(id.raw)
        turns.remove(id.raw)
        resumed.remove(id.raw)
        emptyThreads.remove(id.raw)
        approvals.entries.removeAll { it.value.request.sessionId == id }
        emit(GatewayEvent.SessionDeleted(id))
    }

    override suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>> = call {
        statuses.mapKeys { SessionId(it.key) }.toImmutableMap()
    }

    override suspend fun messagePage(
        id: SessionId,
        cursor: String?,
        limit: Int,
    ): Outcome<ServerPage<MessageWithParts>> = call {
        if (cursor != null) unavailable("historyPagination")
        val context = capture()
        if (id.raw in emptyThreads && id.raw in resumed && id.raw !in uncertain)
            ServerPage(persistentListOf(), null)
        else {
            val thread = resume(id.raw, context)
            val messages = codexMessages(thread)
            uncertain.remove(id.raw)
            emptyThreads.remove(id.raw)
            ServerPage(messages, null)
        }
    }

    /** Full persisted snapshot; deliberately never resumes, subscribes or writes. */
    public suspend fun handoffHistory(id: SessionId): Outcome<ImmutableList<MessageWithParts>> =
        handoffSnapshot(id).map { it.messages }

    public suspend fun handoffSnapshot(id: SessionId): Outcome<CodexHandoffSnapshot> = call {
        val context = capture(restore = false)
        if (id.raw.isBlank() || id.raw.length > 256) malformed()
        val result =
            transport.request(
                "thread/read",
                json("threadId" to s(id.raw), "includeTurns" to JsonPrimitive(true)),
                expectedEpoch = context.transportEpoch,
            )
        check(context)
        val thread = result["thread"].obj()
        if (thread["id"].str(256) != id.raw) malformed()
        CodexHandoffSnapshot(codexSession(thread, context.directory), codexHandoffMessages(thread))
    }

    override fun midTurnPrompt(session: SessionId): MidTurnPrompt? =
        if (session.raw in sessions) MidTurnPrompt.JoinsTurn else null

    override suspend fun prompt(
        session: SessionId,
        prompt: OutgoingPrompt,
        commandId: String?,
    ): Outcome<PromptAdmission> = call {
        val id = session.raw
        if (id in uncertain) unknown()
        if (
            prompt.attachments.isNotEmpty() ||
                prompt.mentions.isNotEmpty() ||
                prompt.delivery == PromptDelivery.Queue ||
                (prompt.agent != null && prompt.agent != "codex")
        )
            unavailable("promptAttachments")
        if (
            prompt.text.isEmpty() ||
                prompt.text.length > 1024 * 1024 ||
                id.isBlank() ||
                id.length > 256
        )
            malformed()
        val context = capture()
        try {
            if (prompt.delivery == PromptDelivery.Steer || statuses[id] == SessionStatus.Busy) {
                steer(id, prompt.text, context)
            } else {
                if (id !in resumed) resume(id, context)
                if (id !in sessions) stale()
                if (statuses[id] == SessionStatus.Busy) steer(id, prompt.text, context)
                else {
                    check(context)
                    emptyThreads.remove(id)
                    val result =
                        transport.request(
                            "turn/start",
                            json(
                                "threadId" to s(id),
                                "input" to input(prompt.text),
                                "model" to prompt.model?.model?.modelId?.let(::s),
                                "effort" to
                                    prompt.model?.variant?.takeIf { it.isNotEmpty() }?.let(::s),
                            ),
                            true,
                            context.transportEpoch,
                        )
                    check(context, true)
                    val acceptedTurn = result["turn"].obj()["id"].str(256)
                    if (acceptedTurn !in settledTurns) {
                        turns[id] = acceptedTurn
                        statuses[id] = SessionStatus.Busy
                    }
                }
            }
            PromptAdmission.Confirmed(null)
        } catch (e: CodexException) {
            if (
                e.failure is Failure.Refused && e.failure.reason == RefusalReason.DispatchUncertain
            ) {
                if (context.locationEpoch == locationEpoch) uncertain += id
                PromptAdmission.Uncertain
            } else throw e
        }
    }

    private fun input(text: String): JsonArray =
        JsonArray(
            listOf(
                json(
                    "type" to s("text"),
                    "text" to s(text),
                    "text_elements" to JsonArray(emptyList()),
                )
            )
        )

    private suspend fun steer(id: String, text: String, context: Context) {
        val expected = turns[id].takeIf { statuses[id] == SessionStatus.Busy }
        check(context)
        val result =
            transport.request(
                "thread/read",
                json("threadId" to s(id), "includeTurns" to JsonPrimitive(true)),
                expectedEpoch = context.transportEpoch,
            )
        check(context)
        val thread = result["thread"].obj()
        if (thread["id"].str(256) != id) malformed()
        codexSession(thread, context.directory)
        val active =
            thread["turns"].list().map { it.obj() }.filter { it.text("status") == "inProgress" }
        if (active.size != 1) stale()
        val turn = active.single()["id"].str(256)
        if (turn in settledTurns) stale()
        if (
            expected != null &&
                (turn != expected || turns[id] != expected || statuses[id] != SessionStatus.Busy)
        )
            stale()
        check(context)
        try {
            val response =
                transport.request(
                    "turn/steer",
                    json("threadId" to s(id), "input" to input(text), "expectedTurnId" to s(turn)),
                    true,
                    context.transportEpoch,
                )
            check(context, true)
            if (response.text("turnId") != turn) unknown()
        } catch (e: CodexException) {
            if (
                e.failure is Failure.UnreadableReply ||
                    (e.failure is Failure.Refused && e.failure.reason == RefusalReason.StaleScope)
            )
                unknown()
            throw e
        }
    }

    override suspend fun stop(session: SessionId, resume: Boolean): Outcome<StopResult> = call {
        if (resume) unavailable("resumeAfterStop")
        val context = capture()
        val thread = readThread(session.raw, context, true)
        val active =
            thread["turns"].list().map { it.obj() }.filter { it.text("status") == "inProgress" }
        if (active.isEmpty()) return@call StopResult.NothingRunning
        if (active.size != 1) stale()
        check(context)
        try {
            transport.request(
                "turn/interrupt",
                json("threadId" to s(session.raw), "turnId" to s(active.single()["id"].str(256))),
                true,
                context.transportEpoch,
            )
            check(context, true)
            StopResult.Stopped
        } catch (e: CodexException) {
            if (e.failure is Failure.Refused && e.failure.reason == RefusalReason.DispatchUncertain)
                StopResult.Uncertain
            else throw e
        }
    }

    override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> = call {
        approvals.values
            .filter { it.push.epoch == transport.epoch }
            .map { it.request }
            .toImmutableList()
    }

    override suspend fun replyPermission(
        session: SessionId,
        request: RequestId,
        reply: PermissionReply,
        message: String?,
    ): Outcome<Unit> = call {
        val approval = approvals[request.raw] ?: stale()
        if (reply == PermissionReply.Always) unavailable("persistentPermissionGrants")
        if (
            approval.request.sessionId != session ||
                approval.push.epoch != transport.epoch ||
                turns[session.raw] != approval.turn ||
                statuses[session.raw] != SessionStatus.Busy
        )
            stale()
        transport.reply(
            approval.push,
            json("decision" to s(if (reply == PermissionReply.Once) "accept" else "decline")),
        )
        approvals.remove(request.raw)
        emit(GatewayEvent.PermissionSettled(session, request))
    }

    override suspend fun providers(): Outcome<ProvidersResponse> = call {
        val context = capture()
        val models = linkedMapOf<String, JsonObject>()
        val seen = mutableSetOf<String>()
        var cursor: String? = null
        var default: String? = null
        for (page in 0 until 10) {
            val result =
                transport.request(
                    "model/list",
                    json("limit" to JsonPrimitive(100), "cursor" to cursor?.let(::s)),
                    expectedEpoch = context.transportEpoch,
                )
            check(context)
            for (raw in result["data"].list(100)) {
                val model = raw.obj()
                if ((model["hidden"] as? JsonPrimitive)?.booleanOrNull == true) continue
                val id = model["model"].str(256)
                models[id] = model
                if ((model["isDefault"] as? JsonPrimitive)?.booleanOrNull == true) default = id
            }
            cursor = result["nextCursor"]?.takeUnless { it == JsonNull }?.str()
            if (cursor == null) break
            if (!seen.add(cursor) || page == 9) malformed()
        }
        val provider =
            ProviderInfo("codex", "Codex", models.keys.toImmutableList(), models.toImmutableMap())
        ProvidersResponse(persistentListOf(provider), persistentListOf(provider), "codex", default)
    }

    override suspend fun configuredProviders(): Outcome<ProvidersResponse> = providers()

    override suspend fun agents(): Outcome<ImmutableList<AgentInfo>> =
        Outcome.Ok(persistentListOf(AgentInfo("codex", "primary", null, null)))

    override suspend fun todos(id: SessionId): Outcome<ImmutableList<Todo>> =
        Outcome.Ok(persistentListOf())

    override suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>> =
        Outcome.Ok(persistentListOf())

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> =
        no("fileBrowsing")

    override suspend fun fileContent(path: String): Outcome<FileContent> = no("fileBrowsing")

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> =
        no("fileBrowsing")

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> =
        no("textSearch")

    override suspend fun shell(
        session: SessionId,
        command: String,
        agent: String,
        model: ModelSelection?,
    ): Outcome<Unit> = no("conversationShell")

    override suspend fun slashCommand(
        session: SessionId,
        command: String,
        args: String,
        model: ModelSelection?,
    ): Outcome<Unit> = no("slashCommands")

    private fun <T> no(feature: String): Outcome<T> =
        Outcome.Failed(Failure.Unavailable(FeatureId(feature)))

    private suspend fun <T> call(block: suspend () -> T): Outcome<T> =
        withContext(lane) { codexAttempt(block) }

    private suspend fun emit(event: GatewayEvent) {
        flow.emit(
            GatewayEnvelope(
                event,
                EventLocation(location.value.directory, null, null),
                clock.now().toEpochMilliseconds(),
            )
        )
    }

    private suspend fun restoreTracked(context: Context) {
        // Flutter `_recoverNow`: one thread failing to resume still lets the others resume; the
        // recovery stays degraded (thrown at the end) and the failed thread stays tracked.
        var failure: CodexException? = null
        for (id in resumed.toList()) {
            check(context)
            if (id in emptyThreads && id !in uncertain) {
                // Flutter: a never-dispatched thread may have no rollout. Retire it from automatic
                // recovery only; keep its metadata (a local draft is keyed to it) and never emit a
                // deletion. Explicit session/history/prompt access reads the server again.
                resumed.remove(id)
            } else {
                try {
                    resume(id, context)
                    emit(GatewayEvent.TranscriptStale(SessionId(id)))
                } catch (e: CodexException) {
                    failure = failure ?: e
                }
            }
        }
        failure?.let { throw it }
    }

    /**
     * Flutter `_scheduleReconnect`/`_recover`: retries with 1, 2, 4, 8, 16 s backoff until the
     * socket and every tracked thread are restored; a sign-in failure stops the loop.
     */
    private fun scheduleRecovery(immediate: Boolean = false) {
        if (closed) return
        if (recovery?.isActive == true) {
            if (!immediate) return
            recovery?.cancel()
        }
        recovery =
            scope.launch(lane) {
                var attempt = 0
                var wait = !immediate
                while (!closed) {
                    if (wait) delay(1000L shl attempt.coerceIn(0, 4))
                    wait = true
                    attempt++
                    mutableStatus.value = StreamStatus.Reconnecting(attempt, null)
                    val result = codexAttempt {
                        transport.connect()
                        val context = capture()
                        check(context)
                        mutableCapabilities.value =
                            CodexCapabilities.Base.copy(agentAccount = transport.accountSupported)
                        mutableConnects.value++
                        emit(GatewayEvent.Connected)
                    }
                    when (result) {
                        is Outcome.Ok -> {
                            mutableStatus.value = StreamStatus.Connected
                            return@launch
                        }
                        is Outcome.Failed ->
                            if (result.failure is Failure.SignIn) {
                                mutableStatus.value = StreamStatus.Stopped(result.failure)
                                return@launch
                            } else {
                                // Degraded (socket or a tracked thread not restored): retry later.
                                val next =
                                    clock.now().toEpochMilliseconds() +
                                        (1000L shl attempt.coerceIn(0, 4))
                                mutableStatus.value =
                                    if (attempt >= 6) StreamStatus.Disconnected(attempt, next)
                                    else StreamStatus.Reconnecting(attempt, next)
                            }
                    }
                    if (!reconnectAllowed.value) {
                        mutableStatus.value = StreamStatus.Disconnected(attempt, null)
                        return@launch
                    }
                }
            }
    }

    override suspend fun close(): Unit =
        withContext(lane) {
            if (closed) return@withContext
            closed = true
            recovery?.cancel()
            for (account in accountSessions.toList()) account.close()
            approvals.clear()
            transport.close()
            mutableStatus.value = StreamStatus.Suspended
        }

    private suspend fun onPush(push: CodexPush) {
        if (closed || push.epoch != transport.epoch) return
        try {
            val p = push.params
            if (push.method == "thread/started") {
                val thread = p["thread"].obj()
                if (thread.text("cwd") != directory()) return
                val context = Context(directory(), locationEpoch, transport.epoch)
                emit(GatewayEvent.SessionUpserted(remember(thread, context)))
                return
            }
            val id = p.text("threadId")
            if (id == null || id !in sessions) {
                if (push.id != null) transport.reject(push)
                return
            }
            val session = SessionId(id)
            when (push.method) {
                "thread/status/changed" -> {
                    statuses[id] = codexStatus(json("status" to p["status"]))
                    if (statuses[id] == SessionStatus.Busy) emit(GatewayEvent.RunStarted(session))
                }
                "turn/started" -> {
                    turns[id] = p["turn"].obj()["id"].str(256)
                    emptyThreads.remove(id)
                    statuses[id] = SessionStatus.Busy
                    emit(GatewayEvent.RunStarted(session))
                }
                "turn/completed" -> {
                    val turn = p["turn"].obj()
                    val turnId = turn["id"].str(256)
                    if (turns[id] != turnId) return
                    statuses[id] = SessionStatus.Idle
                    turns.remove(id)
                    settledTurns += turnId
                    while (settledTurns.size > 256) settledTurns.remove(settledTurns.first())
                    val settled =
                        approvals
                            .filterValues { it.request.sessionId == session && it.turn == turnId }
                            .keys
                            .toList()
                    for (request in settled) {
                        approvals.remove(request)
                        emit(GatewayEvent.PermissionSettled(session, RequestId(request)))
                    }
                    val outcome =
                        when (turn["status"].str(32)) {
                            "failed" ->
                                RunOutcome.Failed(
                                    MessageErrorKind.Unknown,
                                    WireTag("CodexTurnFailed"),
                                    null,
                                )
                            "interrupted" -> RunOutcome.Interrupted(InterruptReason.User)
                            "completed" -> RunOutcome.Succeeded
                            else -> malformed()
                        }
                    emit(GatewayEvent.RunEnded(session, outcome))
                    emit(GatewayEvent.TranscriptStale(session))
                }
                "item/started",
                "item/completed" -> {
                    if (p.text("turnId") != turns[id]) return
                    val item = p["item"].obj()
                    if (item.text("type") == "fileChange") {
                        changes[id to item["id"].str(256)] = item["changes"].list(1000)
                        while (changes.size > 64) changes.remove(changes.keys.first())
                    }
                    val message =
                        codexItem(
                            id,
                            item,
                            completed =
                                if (push.method == "item/completed")
                                    clock.now().toEpochMilliseconds()
                                else null,
                        )
                    emit(GatewayEvent.MessageUpserted(message.info))
                    for (part in message.parts) emit(GatewayEvent.PartUpserted(part))
                }
                "item/agentMessage/delta",
                "item/reasoning/summaryTextDelta" -> {
                    if (p.text("turnId") != turns[id]) return
                    val item = p["itemId"].str(256)
                    emit(
                        GatewayEvent.PartDelta(
                            session,
                            MessageId(item),
                            PartId("$item:0"),
                            if (push.method.contains("reasoning")) DeltaField.Reasoning
                            else DeltaField.Text,
                            p["delta"].str(1024 * 1024, true),
                        )
                    )
                }
                "item/commandExecution/requestApproval",
                "item/fileChange/requestApproval" -> addApproval(push, id)
                "serverRequest/resolved" -> {
                    val requests =
                        approvals
                            .filterValues {
                                it.request.sessionId == session && it.push.id == p["requestId"]
                            }
                            .keys
                            .toList()
                    for (request in requests) {
                        approvals.remove(request)
                        emit(GatewayEvent.PermissionSettled(session, RequestId(request)))
                    }
                }
                else -> if (push.id != null) transport.reject(push)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (push.id != null) transport.reject(push)
        }
    }

    private suspend fun addApproval(push: CodexPush, id: String) {
        val p = push.params
        val turn = p["turnId"].str(256)
        if (
            push.id == null ||
                approvals.size >= 64 ||
                turns[id] != turn ||
                statuses[id] != SessionStatus.Busy
        ) {
            transport.reject(push)
            return
        }
        val item = p["itemId"].str(256)
        val file = push.method == "item/fileChange/requestApproval"
        val network = p["networkApprovalContext"] as? JsonObject
        val patterns: ImmutableList<String>
        val permission: String
        val metadata: JsonObject
        if (file) {
            val values = changes[id to item]
            if (values.isNullOrEmpty()) {
                transport.reject(push)
                return
            }
            patterns = values.map { it.obj()["path"].str() }.toImmutableList()
            permission = "patch"
            metadata =
                json(
                    "diff" to
                        s(
                            values.joinToString("\n") {
                                it.obj()["path"].str() + "\n" + (it.obj().text("diff") ?: "")
                            }
                        )
                )
        } else if (network != null) {
            permission = "network"
            patterns = persistentListOf(network["host"].str(1024))
            metadata = network
        } else {
            val command = p["command"].str(65536)
            permission = "shell"
            patterns = persistentListOf(command)
            metadata = json("command" to s(command))
        }
        val raw = push.id as JsonPrimitive
        val requestId =
            RequestId("codex:${push.epoch}:${if (raw.isString) "s" else "n"}:${raw.content}")
        val request =
            PermissionRequest(
                requestId,
                SessionId(id),
                permission,
                patterns,
                metadata,
                persistentListOf(),
                PermissionTool(MessageId(item), item),
                false,
                p.text("reason"),
            )
        approvals[requestId.raw] = Approval(push, request, turn)
        emit(GatewayEvent.PermissionAsked(request))
    }
}
