package io.github.eslamasabry.opencode_mobile.graph

import android.text.format.DateUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.core.model.ImportableConversation
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.feature.computer.ComputerTools
import io.github.eslamasabry.oc.feature.computer.ImportItem
import io.github.eslamasabry.oc.feature.computer.ImportList
import io.github.eslamasabry.oc.feature.computer.UpdateAnswer
import io.github.eslamasabry.oc.feature.computer.UpdateStep
import io.github.eslamasabry.oc.protocol.codex.CodexEndpoint
import io.github.eslamasabry.oc.protocol.codex.CodexException
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGatewayFactory
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocket
import io.github.eslamasabry.oc.protocol.codex.transport.CodexSocketFactory
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.toImmutableList
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/*
 * Unit BUILD-PROTOCOLS: Android bindings for the Codex app-server backend (:protocol:codex). With this factory in the
 * ConnectionRegistry's set, a saved Codex server (Flutter format, carried over by the migration) connects like any
 * other: Servers → the server → Conversations → Chat (B-AGENT-067). Codex offers no Project tab tools (B-AGENT-068).
 */

/**
 * The app-server socket on OkHttp (Dart `connectCodexSocket`): redirects off, no proxy, ping every 20 s, a message cap,
 * and the token only as `Authorization: Bearer <secret>` to the host the endpoint rules checked.
 */
class OkHttpCodexSockets(private val maxMessageBytes: Int = CodexTransport.MAX_FRAME_BYTES) : CodexSocketFactory {
    private val client =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

    override suspend fun open(endpoint: CodexEndpoint, secret: String): CodexSocket {
        // OkHttp wants http(s) for a websocket request; the upgrade itself keeps the ws/wss meaning.
        val http = endpoint.url.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")
        val url = http.toHttpUrlOrNull()
        // B-SEC-113: the token goes only to the host the rules checked.
        if (url == null || url.host.lowercase().trim('[', ']') != endpoint.host.lowercase()) {
            throw CodexException(CodexFailureKind.InvalidEndpoint)
        }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $secret").build()
        val socket = OkHttpCodexSocket(maxMessageBytes)
        socket.ws = client.newWebSocket(request, socket.listener)
        socket.opened.await()
        return socket
    }
}

private class OkHttpCodexSocket(private val maxMessageBytes: Int) : CodexSocket {
    private val channel = Channel<String>(Channel.UNLIMITED)
    val opened = CompletableDeferred<Unit>()
    lateinit var ws: WebSocket
    override val incoming: ReceiveChannel<String> get() = channel

    val listener =
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > maxMessageBytes) {
                    webSocket.close(1009, null)
                    channel.close()
                } else {
                    channel.trySend(text)
                }
            }

            // The app-server speaks text frames only; a binary frame is a protocol error.
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                webSocket.close(1003, null)
                channel.close()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                channel.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) {
                    val status = response?.code
                    val kind =
                        when {
                            status == 401 || status == 403 -> CodexFailureKind.Authentication
                            response != null -> CodexFailureKind.InvalidResponse
                            else -> CodexFailureKind.Disconnected
                        }
                    opened.completeExceptionally(CodexException(kind))
                }
                response?.close()
                channel.close()
            }
        }

    override suspend fun send(text: String) {
        if (!ws.send(text)) throw CodexException(CodexFailureKind.Disconnected)
    }

    override suspend fun close() {
        if (!::ws.isInitialized) return
        if (!ws.close(1000, null)) ws.cancel()
        channel.close()
    }
}

/**
 * The Codex backend for the ConnectionRegistry. A saved Codex profile keeps its project folder in `codexDirectory`
 * (the Flutter format); the gateway is pinned to it unless the restored location names one, so threads from other
 * projects never enter the list (B-AGENT-063).
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())
class AppCodexGatewayFactory(dispatchers: AppDispatchers, clock: Clock, private val profiles: ProfileStore) :
    GatewayFactory {
    val codex = CodexGatewayFactory(OkHttpCodexSockets(), dispatchers, clock)

    override val backend: BackendKind = BackendKind.Codex

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> = codex.probe(target)

    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        val folder = profiles.profile(spec.profile.id)?.profile?.codexDirectory?.takeIf { it.isNotBlank() }
        val pinned =
            if (spec.location.directory == null && folder != null)
                spec.copy(location = ServerLocation(directory = folder, workspace = spec.location.workspace))
            else spec
        return codex.connect(pinned)
    }
}

/**
 * Settings › Agents › a Paseo computer (:feature:computer) over the live connection graph: the helper update
 * (B-AGENT-167) and the Claude Code import (B-AGENT-174). Connects first when the computer is not connected yet.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<ComputerTools>())
class AppComputerTools(
    private val profiles: ProfileStore,
    private val connections: ServerConnections,
    private val registry: ConnectionRegistry,
    private val clock: Clock,
) : ComputerTools {
    /** The last listed conversations per computer, so an import sends exactly what the person saw. */
    private val listed = ConcurrentHashMap<ProfileId, Map<String, ImportableConversation>>()

    private suspend fun paseo(id: ProfileId): Outcome<PaseoGateway> {
        if (!registry.live.value.containsKey(id)) {
            val connected = connections.connect(id)
            if (connected is Outcome.Failed) return connected
        }
        val gateway = registry.live.value[id]?.session?.server as? PaseoGateway
        return gateway?.ok() ?: Failure.Refused(RefusalReason.StaleScope).failed()
    }

    override fun name(profile: ProfileId): String? = profiles.profile(profile)?.profile?.name

    override suspend fun version(profile: ProfileId): Outcome<String?> =
        when (val gateway = paseo(profile)) {
            is Outcome.Failed -> gateway
            is Outcome.Ok -> gateway.value.transport.serverVersion.ok()
        }

    override suspend fun update(profile: ProfileId, onStep: (UpdateStep) -> Unit): Outcome<UpdateAnswer> {
        val gateway =
            when (val read = paseo(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        val result =
            gateway.updateHostDaemon { phase ->
                onStep(
                    when (phase) {
                        HostUpdatePhase.Starting -> UpdateStep.Starting
                        HostUpdatePhase.Downloading -> UpdateStep.Downloading
                        HostUpdatePhase.Installing -> UpdateStep.Installing
                        HostUpdatePhase.Complete -> UpdateStep.Finishing
                    },
                )
            }
        return when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                when {
                    result.value.alreadyUpToDate -> UpdateAnswer.Current
                    result.value.success -> UpdateAnswer.Done(result.value.newVersion)
                    // The computer's own reason stays out of the page (B-AGENT-167: a result, never its words).
                    else -> UpdateAnswer.Refused
                }.ok()
        }
    }

    override suspend fun importable(profile: ProfileId): Outcome<ImportList> {
        val gateway =
            when (val read = paseo(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        return when (val read = gateway.importableConversations()) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val now = clock.now().toEpochMilliseconds()
                val items = read.value.items.sortedByDescending { it.lastUsedMs }
                listed[profile] = items.associateBy(::keyOf)
                ImportList(
                        project = gateway.providerImportDirectory,
                        items =
                            items
                                .map {
                                    ImportItem(
                                        key = keyOf(it),
                                        title = it.title ?: it.firstPrompt,
                                        folder = it.directory,
                                        whenLabel =
                                            DateUtils.getRelativeTimeSpanString(
                                                    it.lastUsedMs,
                                                    now,
                                                    DateUtils.MINUTE_IN_MILLIS,
                                                )
                                                .toString(),
                                    )
                                }
                                .toImmutableList(),
                        alreadyHere = read.value.alreadyImported,
                    )
                    .ok()
            }
        }
    }

    override suspend fun import(profile: ProfileId, item: ImportItem): Outcome<ConversationRef> {
        val conversation = listed[profile]?.get(item.key) ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val gateway =
            when (val read = paseo(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        return when (val made = gateway.importConversation(conversation)) {
            is Outcome.Failed -> made
            is Outcome.Ok -> ConversationRef(profile, made.value.id).ok()
        }
    }

    private fun keyOf(conversation: ImportableConversation): String = conversation.agentId + "/" + conversation.handle
}
