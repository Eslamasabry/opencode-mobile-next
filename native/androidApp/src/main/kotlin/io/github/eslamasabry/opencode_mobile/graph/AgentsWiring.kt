package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.domain.ProbeResult
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.agents.memory.ConversationTitleMemory
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefsEdit
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputer
import io.github.eslamasabry.oc.feature.agents.hub.AgentComputers
import io.github.eslamasabry.oc.feature.agents.hub.ComputerCheck
import io.github.eslamasabry.oc.feature.agents.hub.NoPhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentsState
import io.github.eslamasabry.oc.protocol.paseo.PaseoEndpoint
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.PaseoProfileRules
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGatewayFactory
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoProbeResult
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoFrame
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocket
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoSocketFactory
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/*
 * Agents unit (P12) Android bindings: the Paseo daemon socket, the Paseo GatewayFactory the ConnectionRegistry picks
 * for `BackendKind.Paseo` profiles (saved here or carried over from the Flutter app), and the Agents hub's ports.
 */

/**
 * The daemon socket on OkHttp (plan 04 §5.2): redirects off, no proxy, WS ping every 20 s, a message cap, and the
 * password only as `Sec-WebSocket-Protocol: paseo.bearer.<pw>` + `Authorization: Bearer <pw>` when non-empty.
 */
class OkHttpPaseoSockets(private val maxMessageBytes: Int = PaseoTransport.MAX_FRAME_BYTES) : PaseoSocketFactory {
    private val client =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

    override suspend fun open(endpoint: PaseoEndpoint, password: String): PaseoSocket {
        // B-SEC-113: the password goes only to the host the rules checked. If OkHttp reads the address as another
        // host (an authority it ends earlier than our parser did), refuse before any header is attached.
        val url = endpoint.httpUrl.toHttpUrlOrNull()
        if (url == null || url.host.lowercase() != endpoint.host.lowercase()) {
            throw PaseoException(PaseoFailureKind.InvalidEndpoint)
        }
        val request =
            Request.Builder().url(url).apply {
                if (password.isNotEmpty()) {
                    header("Sec-WebSocket-Protocol", "paseo.bearer.$password")
                    header("Authorization", "Bearer $password")
                }
            }.build()
        val socket = OkHttpPaseoSocket(maxMessageBytes, password.isNotEmpty())
        socket.ws = client.newWebSocket(request, socket.listener)
        socket.opened.await()
        return socket
    }
}

private class OkHttpPaseoSocket(private val maxMessageBytes: Int, private val sentPassword: Boolean) : PaseoSocket {
    private val channel = Channel<PaseoFrame>(Channel.UNLIMITED)
    val opened = CompletableDeferred<Unit>()
    lateinit var ws: WebSocket
    override val incoming: ReceiveChannel<PaseoFrame> get() = channel

    @Volatile override var closeCode: Int? = null

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
                    channel.trySend(PaseoFrame.Text(text))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size > maxMessageBytes) {
                    webSocket.close(1009, null)
                    channel.close()
                } else {
                    channel.trySend(PaseoFrame.Binary(bytes.toByteArray()))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closeCode = code
                webSocket.close(code, null)
                channel.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                closeCode = closeCode ?: code
                channel.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) {
                    val status = response?.code
                    val kind =
                        when {
                            status == 401 -> PaseoFailureKind.Authentication
                            // 403: a refused password when one was sent, else a host outside the daemon's allowlist.
                            status == 403 ->
                                if (sentPassword) PaseoFailureKind.Authentication else PaseoFailureKind.HostRefused
                            response != null -> PaseoFailureKind.InvalidResponse
                            else -> PaseoFailureKind.Disconnected
                        }
                    opened.completeExceptionally(PaseoException(kind, passwordMissing = status == 401 && !sentPassword))
                }
                response?.close()
                channel.close()
            }
        }

    override suspend fun send(text: String) {
        if (!ws.send(text)) throw PaseoException(PaseoFailureKind.Disconnected)
    }

    override suspend fun close() {
        if (!::ws.isInitialized) return
        if (!ws.close(1000, null)) ws.cancel()
        channel.close()
    }
}

/**
 * The Paseo backend for the ConnectionRegistry. A saved Paseo profile keeps its project folder in `codexDirectory`
 * (the Flutter format); the gateway is pinned to it unless the restored location names one.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())
class AppPaseoGatewayFactory(
    dispatchers: AppDispatchers,
    clock: Clock,
    private val profiles: ProfileStore,
    stores: PreviewStores,
) : GatewayFactory {
    /** B-AGENT-181: titles the app saved per server (`oc.conversationTitles.<id>`, carried over from Flutter). */
    private val titles = ConversationTitleMemory(KvAgentPrefs(stores.prefs, stores.lanes.app))

    val paseo =
        PaseoGatewayFactory(
            OkHttpPaseoSockets(),
            dispatchers,
            clock,
            savedTitles = { profile, key -> runCatching { titles.read(profile, key) }.getOrNull() },
        )

    override val backend: BackendKind = BackendKind.Paseo

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeResult> = paseo.probe(target)

    override suspend fun connect(spec: GatewaySpec): Outcome<GatewayPair> {
        val folder = profiles.profile(spec.profile.id)?.profile?.codexDirectory?.takeIf { it.isNotBlank() }
        val pinned =
            if (spec.location.directory == null && folder != null)
                spec.copy(location = ServerLocation(directory = folder, workspace = spec.location.workspace))
            else spec
        return paseo.connect(pinned)
    }
}

/** The agents engine's preferences port over the app's one key-value store (the Flutter file on the real package). */
class KvAgentPrefs(private val store: KeyValueStore, private val lane: WriterLane) : AgentPrefs {
    override fun string(key: String): String? = (store.get(key) as? PrefValue.Str)?.value

    override fun bool(key: String): Boolean? = (store.get(key) as? PrefValue.Bool)?.value

    override fun stringList(key: String): List<String>? = (store.get(key) as? PrefValue.StrList)?.value

    override fun keys(prefix: String): Set<String> = store.keys(prefix)

    override val changes: Flow<Set<String>> get() = store.changes

    override suspend fun commit(edit: AgentPrefsEdit.() -> Unit): Outcome<Unit> =
        store.commit(lane) {
            val target = this
            object : AgentPrefsEdit {
                override fun putString(key: String, value: String) = target.putString(key, value)

                override fun putBool(key: String, value: Boolean) = target.putBool(key, value)

                override fun putStringList(key: String, value: List<String>) = target.putStringList(key, value)

                override fun remove(key: String) = target.remove(key)
            }.edit()
        }
}

/** The Agents hub's computers: the saved Paseo profiles, checked, saved and connected through the real graph. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<AgentComputers>())
class AppAgentComputers(
    private val profiles: ProfileStore,
    private val connections: ServerConnections,
    private val registry: ConnectionRegistry,
    private val factory: AppPaseoGatewayFactory,
    appScope: AppCoroutineScope,
) : AgentComputers {
    override val computers: StateFlow<ImmutableList<AgentComputer>> =
        combine(profiles.profiles, registry.live) { saved, live ->
                saved
                    .filter { it.profile.backend == BackendKind.Paseo }
                    .map {
                        AgentComputer(
                            id = it.id,
                            name = it.profile.name,
                            address = it.profile.baseUrl,
                            folder = it.profile.codexDirectory,
                            connected = live.containsKey(it.id),
                        )
                    }
                    .toImmutableList()
            }
            .stateIn(appScope, SharingStarted.Eagerly, kotlinx.collections.immutable.persistentListOf())

    override suspend fun check(address: String, password: String, folder: String): Outcome<ComputerCheck> =
        when (val result = factory.paseo.checkConnection(address, password, folder)) {
            is PaseoProbeResult.Verified -> ComputerCheck.Ready(result.version, result.runtimes).ok()
            is PaseoProbeResult.InvalidInput ->
                ComputerCheck.Invalid(address = result.url != null, folder = result.directory, password = result.password).ok()
            is PaseoProbeResult.Refused -> Outcome.Failed(result.failure)
        }

    override suspend fun add(name: String, address: String, password: String, folder: String): Outcome<ProfileId> {
        val url = PaseoProfileRules.normalizeUrl(address)
        if (PaseoProfileRules.validateUrl(url) != null) return Failure.Refused(RefusalReason.InvalidAddress).failed()
        if (!PaseoProfileRules.passwordAcceptable(password) || !folder.startsWith("/"))
            return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val id = ProfileId(UUID.randomUUID().toString())
        val saved =
            profiles.upsert(
                StoredProfile(
                    id = id,
                    name = name.ifEmpty { "Paseo" },
                    baseUrl = url,
                    backend = BackendKind.Paseo,
                    codexDirectory = folder,
                ),
                if (password.isEmpty()) SecretEdit.Clear else SecretEdit.Set(password),
            )
        if (saved is Outcome.Failed) return saved
        // Saved even when the computer is away right now: the person can connect later from the list.
        return when (val connected = connections.connect(id)) {
            is Outcome.Ok -> id.ok()
            is Outcome.Failed -> connected
        }
    }

    override suspend fun agents(id: ProfileId): Outcome<ImmutableList<String>> {
        if (!registry.live.value.containsKey(id)) {
            val connected = connections.connect(id)
            if (connected is Outcome.Failed) return connected
        }
        val graph = registry.live.value[id] ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        return when (val providers = graph.session.server.providers()) {
            is Outcome.Ok -> providers.value.providers.map { it.name }.toImmutableList().ok()
            is Outcome.Failed -> providers
        }
    }

    override suspend fun newConversation(id: ProfileId): Outcome<ConversationRef> = connections.createConversation(id)
}

/** No phone agent host in this build yet: the hub says where agents run instead of showing an empty list. */
@Inject
@ContributesBinding(AppScope::class, binding = binding<PhoneAgents>())
class AppPhoneAgents : PhoneAgents by NoPhoneAgents(MutableStateFlow(PhoneAgentsState()))
