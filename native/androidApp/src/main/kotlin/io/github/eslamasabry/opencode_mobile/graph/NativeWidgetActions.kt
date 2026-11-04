package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.widgets.WidgetFeed
import io.github.eslamasabry.oc.android.widgets.WidgetLiveDispatcher
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.CorrelatedPromptGateway
import io.github.eslamasabry.oc.core.domain.FencedNewSessionPromptGateway
import io.github.eslamasabry.oc.core.domain.FencedPromptGateway
import io.github.eslamasabry.oc.core.domain.PromptRunIdentityGateway
import io.github.eslamasabry.oc.core.domain.MidTurnPromptGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.SessionSelection
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.StopResult
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.SendRoute
import io.github.eslamasabry.oc.engine.delivery.SteerPolicy
import java.util.UUID
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class NativeWidgetSnapshot(val snapshot: String, val target: String?)

/**
 * The live widget boundary owns no connection. A tap can use only the exact already-live graph captured in its
 * public scope and app-private target. Durable admission and the 6.5 s outer deadline remain in :android:widgets.
 */
@Inject
@SingleIn(AppScope::class)
class NativeWidgetActions(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
) : WidgetLiveDispatcher {
    private val lane = dispatchers.serial("widget-live-actions")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val commands = Mutex()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    @Volatile private var target: Target? = null
    @Volatile private var runs: Map<String, Run> = emptyMap()
    private var epochs: Map<String, Long> = emptyMap()
    private val graphFingerprints = IdentityHashMap<ConnectionGraph, String>()
    private var started = false

    private data class Key(val graph: ConnectionGraph, val location: ServerLocation, val connects: Int, val fingerprint: String)
    private data class Target(val key: Key, val token: String, val connected: Boolean) {
        val graph: ConnectionGraph get() = key.graph
        val gateway: ServerGateway get() = graph.session.server
        val profile: ProfileId get() = graph.profile.id
        val project: String get() =
            if (key.location.directory.isNullOrBlank() && key.location.workspace.isNullOrBlank()) ""
            else DartJson.sha256Hex("$token|project")
    }
    private data class Run(val marker: String, val started: Long)

    fun stamp(): Long = mutableRevision.value

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        WidgetFeed.liveDispatcher = this
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            combine(registry.live, stores.profiles.activeId, stores.profiles.profiles) { live, id, _ ->
                graphFingerprints.keys.removeAll { graph -> live.values.none { it === graph } }
                id?.let(live::get)
            }
                .collectLatest { graph ->
                    if (graph == null) {
                        setTarget(null)
                        return@collectLatest
                    }
                    coroutineScope {
                        launch {
                            combine(graph.session.server.location, graph.session.server.events.connects, graph.session.server.events.status) { location, connects, status ->
                                val fingerprint = fingerprint(graph.profile.id)
                                val loaded = stores.profiles.profile(graph.profile.id)
                                val matchesProfile = loaded?.toServerProfile(managedOnPhone = graph.profile.managedOnPhone)
                                    ?.let { it.copy(name = graph.profile.name) == graph.profile } == true
                                val original = graphFingerprints[graph]
                                val firstDirectoryMatches = original != null || loaded?.profile?.usesAgentSocket != true ||
                                    loaded.profile.codexDirectory.ifBlank { null } == location.directory?.ifBlank { null }
                                if (fingerprint == null || !matchesProfile || !firstDirectoryMatches || (original != null && original != fingerprint)) null
                                else {
                                    graphFingerprints[graph] = fingerprint
                                    Key(graph, location, connects, fingerprint) to (status == StreamStatus.Connected)
                                }
                            }.collect { state ->
                                val previous = target
                                val next = state?.let { (key, connected) ->
                                    if (previous?.key == key) previous.copy(connected = connected)
                                    else Target(key, UUID.randomUUID().toString(), connected)
                                }
                                if (previous != next) setTarget(next)
                            }
                        }
                        launch {
                            graph.session.server.events.all.collect { envelope ->
                                val id = when (val event = envelope.event) {
                                    is GatewayEvent.RunStarted -> event.sessionId.raw
                                    is GatewayEvent.RunEnded -> event.sessionId.raw
                                    is GatewayEvent.MessageUpserted -> event.message.takeIf { it.role == Role.User }?.sessionId
                                    else -> null
                                } ?: return@collect
                                epochs = epochs + (id to ((epochs[id] ?: 0) + 1))
                                runs = runs - id
                                bump()
                            }
                        }
                    }
                }
        }
    }

    private fun setTarget(next: Target?) {
        val previous = target
        target = next
        if (previous?.key != next?.key) {
            runs = emptyMap()
            epochs = emptyMap()
        }
        WidgetFeed.onConnection(app, next?.connected == true)
        bump()
    }
    private fun bump() { mutableRevision.value += 1 }

    /** Called from the one widget publisher; raw directories remain in the returned private target only. */
    internal suspend fun decorate(raw: String, feed: ChatFeedSnapshot): NativeWidgetSnapshot = withContext(lane) {
        val json = JSONObject(raw)
        val t = target?.takeIf { it.profile.raw == json.optString("profileID") }
        json.put("scopeToken", t?.token.orEmpty())
        json.put("lastProjectID", t?.project.orEmpty())
        json.put("connected", t?.let(::current) == true)
        json.remove("onDemand") // This live-only adapter never promises a headless connection.
        val rows = json.optJSONArray("sessions") ?: JSONArray()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val item = feed.items.firstOrNull { it.profileId.raw == row.optString("profileID") && it.sessionId.raw == row.optString("id") }
            val own = t != null && item != null && item.profileId == t.profile && item.directory == t.key.location.directory
            if (!own) {
                row.remove("requestID")
                row.put("startedAt", 0L)
                row.put("steerMode", "unknown")
                continue
            }
            if (!row.optBoolean("busy")) runs = runs - row.optString("id")
            val run = if (t != null && row.optBoolean("busy") && current(t)) cachedRun(t, row.optString("id")) else null
            row.put("startedAt", run?.started ?: 0L)
            row.put("steerMode", if (run == null || t == null) "unknown" else steerMode(t, SessionId(row.optString("id"))))
            run?.let { row.put("runMarker", it.marker) }
        }
        val privateTarget = t?.let {
            JSONObject().put("profileID", it.profile.raw).put("scopeToken", it.token).put("fingerprint", it.key.fingerprint)
                .put("directory", it.key.location.directory).put("workspace", it.key.location.workspace).toString()
        }
        NativeWidgetSnapshot(json.toString(), privateTarget)
    }

    private suspend fun cachedRun(t: Target, session: String): Run? {
        runs[session]?.let { return it }
        val epoch = epochs[session] ?: 0
        val read = withTimeoutOrNull(1200) { readRun(t, SessionId(session)) } ?: return null
        if (!current(t) || (epochs[session] ?: 0) != epoch) return null
        runs = runs + (session to read)
        return read
    }

    private suspend fun readRun(t: Target, session: SessionId): Run? {
        val reader = t.gateway as? PromptRunIdentityGateway ?: return null
        val proof = (reader.lastPromptIdentity(session) as? Outcome.Ok)?.value ?: return null
        return Run(proof.marker, proof.startedAtMs)
    }

    override fun dispatch(payload: Map<String, Any?>, complete: (Map<String, Any?>) -> Unit) {
        scope.launch {
            val result = try {
                withTimeoutOrNull(6000) { execute(payload) } ?: result(payload, "uncertain", "uncertainDelivery")
            } catch (cancelled: CancellationException) {
                complete(result(payload, "uncertain", "uncertainDelivery"))
                throw cancelled
            } catch (_: Exception) {
                result(payload, "uncertain", "uncertainDelivery")
            }
            complete(result)
        }
    }

    private suspend fun execute(payload: Map<String, Any?>): Map<String, Any?> {
        if (!commands.tryLock()) return result(payload, "rejected", "busy")
        try {
            val t = target ?: return result(payload, "rejected", "unavailable")
            if (!current(t) || !privateMatches(t) || payload["profileID"] != t.profile.raw || payload["scopeToken"] != t.token)
                return result(payload, "rejected", "staleRequest")
            val action = payload["action"] as? String
            val text = (payload["text"] as? String).orEmpty().trim()
            if (action in setOf("voice", "steer") && (text.isEmpty() || text.length > 8000)) return result(payload, "rejected", "invalidRequest")
            if (t.gateway.awaitActionTransport() is Outcome.Failed || !current(t) || !privateMatches(t))
                return result(payload, "rejected", "unavailable")
            val session = SessionId((payload["sessionID"] as? String).orEmpty())
            return when (action) {
                "allowOnce", "reject" -> {
                    val request = RequestId((payload["requestID"] as? String).orEmpty())
                    val pending = (t.gateway.pendingPermissions() as? Outcome.Ok)?.value ?: return result(payload, "rejected", "unavailable")
                    if (!current(t) || !privateMatches(t) || pending.none { it.id == request && it.sessionId == session }) return result(payload, "rejected", "staleRequest")
                    mutationResult(payload, t.gateway.replyPermission(session, request, if (action == "allowOnce") PermissionReply.Once else PermissionReply.Reject))
                }
                "stop", "steer" -> {
                    val expected = (payload["expectedStartedAt"] as? Number)?.toLong() ?: 0
                    val statuses = (t.gateway.sessionStatuses() as? Outcome.Ok)?.value ?: return result(payload, "rejected", "unavailable")
                    if (statuses[session]?.raw !in setOf("busy", "retry")) return result(payload, "rejected", "staleRequest")
                    val run = readRun(t, session) ?: return result(payload, "rejected", "unavailable")
                    if (!current(t) || !privateMatches(t) || expected <= 0 || run.started != expected || runs[session.raw] != run) {
                        // A missed volatile transition never reuses an old turn; one explicit stale tap refreshes proof.
                        runs = runs - session.raw
                        bump()
                        return result(payload, "rejected", "staleRequest")
                    }
                    if (action == "stop") {
                        when (val stopped = t.gateway.stop(session)) {
                            is Outcome.Failed -> result(payload, "uncertain", "uncertainDelivery")
                            is Outcome.Ok -> result(payload, if (stopped.value == StopResult.Uncertain) "uncertain" else "accepted", if (stopped.value == StopResult.Uncertain) "uncertainDelivery" else null)
                        }
                    } else {
                        val route = route(t, session)
                        if (route !in setOf(SendRoute.Steer, SendRoute.JoinTurn, SendRoute.AfterReply)) return result(payload, "rejected", "unsupported")
                        val correlated = t.gateway as? CorrelatedPromptGateway
                        val fenced = t.gateway as? FencedPromptGateway
                        val check = { current(t) && privateMatches(t) && runs[session.raw] == run }
                        val prompt = OutgoingPrompt(text, delivery = route.wireDelivery)
                        val admission = when {
                            correlated != null -> correlated.promptWithMessageId(session, correlated.createPromptMessageId(), prompt, check)
                            fenced != null -> fenced.promptFenced(session, prompt, check)
                            else -> return result(payload, "rejected", "unsupported")
                        }
                        promptResult(payload, admission)
                    }
                }
                "voice" -> {
                    if (t.project.isEmpty() || payload["lastProjectID"] != t.project) return result(payload, "rejected", "staleRequest")
                    val fenced = t.gateway as? FencedNewSessionPromptGateway
                    val uncorrelated = t.gateway as? FencedPromptGateway
                    if (fenced == null && uncorrelated == null) return result(payload, "rejected", "unsupported")
                    val defaultsRead = (t.graph as? ChatGraphAccessors)?.chatOperations?.chatDefaults()
                    if (t.gateway.capabilities.value.serverCatalog && defaultsRead is Outcome.Failed) return result(payload, "rejected", "unavailable")
                    val defaults = (defaultsRead as? Outcome.Ok)?.value
                    if (!current(t) || !privateMatches(t)) return result(payload, "rejected", "staleRequest")
                    val selection = t.gateway as? SessionSelectionGateway
                    val created = if (selection != null && defaults != null) {
                        selection.createSelectedSession(SessionSelection(defaults.model, "", defaults.agent, modelKnown = true, agentKnown = true))
                    } else t.gateway.createSession(NewSession(location = t.key.location))
                    if (created !is Outcome.Ok || !current(t) || !privateMatches(t)) return result(payload, "uncertain", "uncertainDelivery")
                    val prompt = OutgoingPrompt(text, model = defaults?.model?.let { ModelSelection(it) }, agent = defaults?.agent)
                    val check = { current(t) && privateMatches(t) && payload["lastProjectID"] == t.project }
                    val admission = if (fenced != null) {
                        fenced.promptNewSessionWithMessageId(created.value.id, fenced.createPromptMessageId(), prompt, check)
                    } else uncorrelated!!.promptFenced(created.value.id, prompt, check)
                    promptResult(payload, admission)
                }
                else -> result(payload, "rejected", "unsupported")
            }
        } finally {
            commands.unlock()
        }
    }

    private fun route(t: Target, session: SessionId): SendRoute {
        val facet = t.gateway as? MidTurnPromptGateway
        return SteerPolicy.route(true, true, t.gateway.capabilities.value, facet != null, facet?.midTurnPrompt(session))
    }
    private fun steerMode(t: Target, session: SessionId): String = when (route(t, session)) {
        SendRoute.Steer, SendRoute.JoinTurn -> "steer"
        SendRoute.AfterReply -> "afterReply"
        SendRoute.RestartTurn -> "restart"
        else -> "unknown"
    }
    private fun current(t: Target): Boolean =
        target === t && t.connected && registry.live.value[t.profile] === t.graph && stores.profiles.activeId.value == t.profile &&
            t.gateway.events.status.value == StreamStatus.Connected && t.gateway.location.value == t.key.location &&
            t.gateway.events.connects.value == t.key.connects && fingerprint(t.profile) == t.key.fingerprint

    private fun fingerprint(profile: ProfileId): String? {
        val loaded = stores.profiles.profile(profile) ?: return null
        val server = loaded.toServerProfile() ?: return null
        return DartJson.sha256Hex(DartJson.encode(listOf(server.backend.name, server.origin.toString(), server.username,
            server.trust.pinnedLeafSha256, server.trust.cleartextConsentAtMs, loaded.profile.codexDirectory)))
    }
    private fun privateMatches(t: Target): Boolean {
        val raw = stores.prefs.string("oc.widgetTarget.${t.profile.raw}") ?: return false
        val value = try { JSONObject(raw) } catch (_: Exception) { return false }
        return value.optString("profileID") == t.profile.raw && value.optString("scopeToken") == t.token &&
            value.optString("fingerprint") == t.key.fingerprint && value.optString("directory").ifBlank { null } == t.key.location.directory?.ifBlank { null } &&
            value.optString("workspace").ifBlank { null } == t.key.location.workspace?.ifBlank { null }
    }
    private fun promptResult(payload: Map<String, Any?>, response: Outcome<PromptAdmission>): Map<String, Any?> = when (response) {
        is Outcome.Failed -> result(payload, "uncertain", "uncertainDelivery")
        is Outcome.Ok -> if (response.value is PromptAdmission.Confirmed) result(payload, "accepted") else result(payload, "uncertain", "uncertainDelivery")
    }
    private fun mutationResult(payload: Map<String, Any?>, response: Outcome<Unit>): Map<String, Any?> =
        if (response is Outcome.Ok) result(payload, "accepted") else result(payload, "uncertain", "uncertainDelivery")
    private fun result(payload: Map<String, Any?>, status: String, error: String? = null): Map<String, Any?> =
        mapOf("id" to payload["id"], "action" to payload["action"], "status" to status) + if (error == null) emptyMap() else mapOf("error" to error)
}
