package io.github.eslamasabry.oc.core.testing

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEnvelope
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.GatewayEvents
import io.github.eslamasabry.oc.core.model.AgentInfo
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.FileContent
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.Todo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

/** Scripted event stream: tests push envelopes and set the status. */
public class FakeGatewayEvents(public var currentDirectory: String? = null) : GatewayEvents {
    override val status: MutableStateFlow<StreamStatus> = MutableStateFlow(StreamStatus.Connecting)
    override val connects: MutableStateFlow<Int> = MutableStateFlow(0)
    private val bus = MutableSharedFlow<GatewayEnvelope>(extraBufferCapacity = 256)
    public var retries: Int = 0
        private set

    override val all: Flow<GatewayEnvelope> = bus.asSharedFlow()
    override val project: Flow<GatewayEnvelope> =
        bus.filter { it.location?.directory == null || it.location?.directory == currentDirectory }

    public fun emit(event: GatewayEvent, directory: String? = null, atMs: Long = 0) {
        val location = directory?.let { io.github.eslamasabry.oc.core.model.EventLocation(it, null, null) }
        check(bus.tryEmit(GatewayEnvelope(event, location, atMs))) { "event buffer full" }
    }

    public fun connect() {
        status.value = StreamStatus.Connected
        connects.value += 1
        emit(GatewayEvent.Connected)
    }

    override fun retryNow() {
        retries += 1
    }
}

/**
 * Records every call (`calls`) and answers from scripted state. Unscripted reads answer an empty value; a test sets
 * [failNext] to make the next call fail with that Failure.
 */
public class FakeServerGateway(sessions: List<Session> = emptyList()) : ServerGateway {
    public val calls: MutableList<String> = mutableListOf()
    public var sessionList: ImmutableList<Session> = sessions.toImmutableList()
    public var statuses: ImmutableMap<SessionId, SessionStatus> = persistentMapOf()
    public var permissions: ImmutableList<PermissionRequest> = persistentListOf()
    public var admission: PromptAdmission = PromptAdmission.Confirmed(null)
    public var failNext: Failure? = null
    public val sentPrompts: MutableList<Pair<SessionId, OutgoingPrompt>> = mutableListOf()
    public var closed: Boolean = false
        private set

    override val events: FakeGatewayEvents = FakeGatewayEvents()
    override val capabilities: MutableStateFlow<ServerCapabilities> = MutableStateFlow(ServerCapabilities.Unknown)
    private val mutableLocation = MutableStateFlow(ServerLocation.Default)
    override val location: StateFlow<ServerLocation> = mutableLocation

    override fun setLocation(location: ServerLocation) {
        calls += "setLocation"
        mutableLocation.value = location
        events.currentDirectory = location.directory
    }

    private inline fun <T> answer(call: String, value: () -> T): Outcome<T> {
        calls += call
        failNext?.let {
            failNext = null
            return it.failed()
        }
        return value().ok()
    }

    override suspend fun health(): Outcome<Health> = answer("health") { Health(true, "fake") }

    override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> =
        answer("sessionPage") { ServerPage(sessionList, null) }

    override suspend fun createSession(spec: NewSession): Outcome<Session> =
        answer("createSession") { Session(SessionId("s${sessionList.size + 1}"), spec.title).also { sessionList = (sessionList + it).toImmutableList() } }

    override suspend fun session(id: SessionId): Outcome<Session> {
        calls += "session ${id.raw}"
        failNext?.let {
            failNext = null
            return it.failed()
        }
        return sessionList.firstOrNull { it.id == id }?.ok() ?: Failure.NotFound().failed()
    }

    override suspend fun renameSession(id: SessionId, title: String): Outcome<Unit> = answer("renameSession ${id.raw}") {}

    override suspend fun deleteSession(id: SessionId): Outcome<Unit> =
        answer("deleteSession ${id.raw}") { sessionList = sessionList.filterNot { it.id == id }.toImmutableList() }

    override suspend fun sessionStatuses(): Outcome<ImmutableMap<SessionId, SessionStatus>> = answer("sessionStatuses") { statuses }

    override suspend fun messagePage(id: SessionId, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>> =
        answer("messagePage ${id.raw}") { ServerPage(persistentListOf(), null) }

    override suspend fun todos(id: SessionId): Outcome<ImmutableList<Todo>> = answer("todos") { persistentListOf() }

    override suspend fun diff(id: SessionId): Outcome<ImmutableList<FileDiff>> = answer("diff") { persistentListOf() }

    override suspend fun prompt(session: SessionId, prompt: OutgoingPrompt, commandId: String?): Outcome<PromptAdmission> =
        answer("prompt ${session.raw}") { admission.also { sentPrompts += session to prompt } }

    override suspend fun shell(session: SessionId, command: String, agent: String, model: ModelSelection?): Outcome<Unit> =
        answer("shell ${session.raw}") {}

    override suspend fun slashCommand(session: SessionId, command: String, args: String, model: ModelSelection?): Outcome<Unit> =
        answer("slashCommand ${session.raw} $command") {}

    override suspend fun stop(session: SessionId, resume: Boolean): Outcome<StopResult> = answer("stop ${session.raw}") { StopResult.Stopped }

    override suspend fun pendingPermissions(): Outcome<ImmutableList<PermissionRequest>> = answer("pendingPermissions") { permissions }

    override suspend fun replyPermission(session: SessionId, request: RequestId, reply: PermissionReply, message: String?): Outcome<Unit> =
        answer("replyPermission ${request.raw} $reply") {}

    override suspend fun providers(): Outcome<ProvidersResponse> = answer("providers") { EmptyProviders }

    override suspend fun configuredProviders(): Outcome<ProvidersResponse> = answer("configuredProviders") { EmptyProviders }

    override suspend fun agents(): Outcome<ImmutableList<AgentInfo>> = answer("agents") { persistentListOf() }

    override suspend fun listFiles(path: String): Outcome<ImmutableList<FileNode>> = answer("listFiles") { persistentListOf() }

    override suspend fun fileContent(path: String): Outcome<FileContent> = answer("fileContent") { FileContent("", "text", null, null) }

    override suspend fun findFile(query: String): Outcome<ImmutableList<String>> = answer("findFile") { persistentListOf() }

    override suspend fun findText(pattern: String): Outcome<ImmutableList<FindMatch>> = answer("findText") { persistentListOf() }

    override suspend fun close() {
        closed = true
        calls += "close"
    }

    private companion object {
        val EmptyProviders = ProvidersResponse(persistentListOf(), persistentListOf(), null, null)
    }
}

/** Every operation answers its Unavailable default; override in an anonymous subclass to script one. */
public open class FakeOperationsGateway : ServerOperationsGateway

/** One fake backend. [probeResult] null = the probe fails as unreachable. */
public class FakeGatewayFactory(
    override val backend: BackendKind = BackendKind.OpenCode2,
    public val gateway: FakeServerGateway = FakeServerGateway(),
    public val operations: ServerOperationsGateway = FakeOperationsGateway(),
    public var probeResult: ProbeResult? = ProbeResult(backend, "fake", ServerCapabilities.Unknown),
    /** Non-null: the next connect fails with it (a Paseo/Codex hello refusal), then is cleared. */
    public var failNextConnect: Failure? = null,
) : GatewayFactory {
    public val opened: MutableList<GatewaySpec> = mutableListOf()
    public val probed: MutableList<ProbeTarget> = mutableListOf()

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> {
        probed += target
        return probeResult?.ok() ?: Failure.Network(NetworkWhy.Refused).failed()
    }

    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        failNextConnect?.let {
            failNextConnect = null
            return it.failed()
        }
        opened += spec
        return GatewayPair(gateway, operations).ok()
    }
}
