package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.widgets.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.*
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

@ContributesTo(AppScope::class)
interface AgentWidgetAccessors {
    val agentWidgets: AgentWidgetWiring
}

/**
 * Real widget publications and one-shot commands; neither path starts a runtime or replays a write.
 */
@SingleIn(AppScope::class)
@Inject
@OptIn(ExperimentalCoroutinesApi::class)
class AgentWidgetWiring(
    private val application: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val factories: Set<GatewayFactory>,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    redactor: Redactor,
    private val widgetTeams: NativeWidgetTeam,
) {
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("widget-actions"))
    private val publications = Mutex()
    private val presentation = WidgetPresentation(application, redactor)
    private var started = false
    private var identity: List<Any?>? = null
    private var scopeToken = ""
    private var lastBase: Pair<String, String>? = null

    fun start() {
        if (started) return
        started = true
        widgetTeams.start()
        WidgetFeed.liveDispatcher = WidgetLiveDispatcher { payload, complete ->
            scope.launch {
                val deadline =
                    (payload["deadlineEpochMs"] as? Number)?.toLong()
                        ?: (clock.now().toEpochMilliseconds() + LIVE_BUDGET_MS)
                val attempt =
                    Action(payload, statusOnly = false, live = true, deadlineMs = deadline)
                val answer =
                    withTimeoutOrNull(
                        (deadline - clock.now().toEpochMilliseconds()).coerceIn(1, LIVE_BUDGET_MS)
                    ) {
                        attempt.run()
                    }
                        ?: if (attempt.acknowledged) attempt.acknowledgedWithoutStatus()
                        else
                            result(
                                payload,
                                if (attempt.acknowledged) "accepted"
                                else if (attempt.dispatched) "uncertain" else "rejected",
                                if (attempt.acknowledged) null
                                else if (attempt.dispatched) "uncertainDelivery" else "unavailable",
                            )
                complete(answer)
            }
        }
        WidgetFeed.backgroundRunner =
            object : WidgetBackgroundRunner {
                override fun start(
                    context: Context,
                    payload: Map<String, Any?>,
                    snapshotJson: String,
                    deadlineEpochMs: Long,
                    operation: String,
                    complete: (Map<*, *>?) -> Unit,
                ): WidgetBackgroundRunner.Handle {
                    if (
                        operation !in setOf("execute", "checkStatus") ||
                            deadlineEpochMs <= clock.now().toEpochMilliseconds()
                    ) {
                        complete(
                            if (operation == "checkStatus") mapOf("status" to "unavailable")
                            else result(payload, "rejected", "unavailable")
                        )
                        return WidgetBackgroundRunner.Handle {}
                    }
                    val job = scope.launch {
                        val attempt =
                            Action(
                                payload,
                                operation == "checkStatus",
                                live = false,
                                deadlineMs = deadlineEpochMs,
                            )
                        val budget =
                            (deadlineEpochMs - clock.now().toEpochMilliseconds()).coerceIn(
                                1,
                                BACKGROUND_BUDGET_MS,
                            )
                        val answer =
                            withTimeoutOrNull(budget) { attempt.run() }
                                ?: if (operation == "checkStatus") mapOf("status" to "unavailable")
                                else if (attempt.acknowledged) attempt.acknowledgedWithoutStatus()
                                else
                                    result(
                                        payload,
                                        if (attempt.acknowledged) "accepted"
                                        else if (attempt.dispatched) "uncertain" else "rejected",
                                        if (attempt.acknowledged) null
                                        else if (attempt.dispatched) "uncertainDelivery"
                                        else "unavailable",
                                    )
                        complete(answer)
                    }
                    return WidgetBackgroundRunner.Handle { job.cancel() }
                }
            }
        scope.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            combine(registry.live, stores.profiles.activeId) { live, active ->
                    active?.let(live::get)
                }
                .flatMapLatest { graph ->
                    if (graph == null) flowOf(false)
                    else
                        combine(
                            graph.session.server.events.status,
                            graph.session.server.location,
                            graph.session.server.events.connects,
                        ) { status, _, _ ->
                            status == StreamStatus.Connected
                        }
                }
                .collect { active ->
                    WidgetFeed.onConnection(application, active)
                    lastBase?.let { (json, profile) -> publish(json, profile) }
                }
        }
    }

    /**
     * PlatformHub is the sole caller/publication owner; the private route is acknowledged before
     * the public copy.
     */
    /** Drain publications before removing one profile's public copy and private routing. */
    suspend fun clearForProfile(profileId: String): ClearOutcome = publications.withLock {
        val owner = (identity?.firstOrNull() as? ConnectionGraph)?.profile?.id?.raw
        if (lastBase?.second == profileId || owner == profileId) {
            lastBase = null
            identity = null
            scopeToken = ""
        }
        val persistence = WidgetFeed.persistence ?: return@withLock ClearOutcome.Failed
        AndroidWidgetSurface(application, persistence).clearProfile(profileId)
    }

    suspend fun publish(snapshotJson: String, profileId: String) = publications.withLock {
        if (stores.profiles.activeId.value?.raw != profileId) return@withLock
        lastBase = snapshotJson to profileId
        val persistence = WidgetFeed.persistence ?: return@withLock
        val surface = AndroidWidgetSurface(application, persistence)
        val profile = stores.profiles.profile(ProfileId(profileId)) ?: return@withLock
        val graph = registry.live.value[ProfileId(profileId)]
        // A suspended UI must not erase its last safe on-demand target or mark its cached rows
        // live.
        if (graph == null) {
            surface.invalidateConnection()
            return@withLock
        }
        val gateway = graph.session.server
        val location = gateway.location.value
        val fingerprint = fingerprint(profileId) ?: return@withLock
        val currentIdentity = listOf(graph, location, gateway.events.connects.value, fingerprint)
        if (identity != currentIdentity) {
            identity = currentIdentity
            scopeToken = UUID.randomUUID().toString()
        }
        val snapshot = parse(snapshotJson) ?: return@withLock
        val connected = gateway.events.status.value == StreamStatus.Connected
        snapshot.put("scopeToken", scopeToken).put("connected", connected)
        val savedReadable =
            !profile.profile.transientTransport &&
                !profile.profile.requiresOpenCode2Migration &&
                !profile.reentry.password &&
                !profile.reentry.agentToken &&
                !profile.cleartextUnconfirmed
        val allowCold = savedReadable && graph.profile.backend == BackendKind.OpenCode2
        if (savedReadable) snapshot.put("onDemand", JSONObject().put("fingerprint", fingerprint))
        else snapshot.remove("onDemand")
        val rows = snapshot.optJSONArray("sessions") ?: JSONArray()
        val readUntil = clock.now().toEpochMilliseconds() + PUBLISH_BUDGET_MS
        suspend fun <T> widgetRead(block: suspend () -> T): T? {
            val remaining = readUntil - clock.now().toEpochMilliseconds()
            return if (remaining <= 0) null else withTimeoutOrNull(remaining) { block() }
        }
        val statuses = (widgetRead { gateway.sessionStatuses() } as? Outcome.Ok)?.value
        val permissions = (widgetRead { gateway.pendingPermissions() } as? Outcome.Ok)?.value
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            row.put("onDemandActions", JSONArray()).put("steerMode", "unknown")
            row.remove("startedAt")
            row.remove("runMarker")
            val sid = row.optString("id")
            val matches =
                runtime.snapshot.value.items.filter {
                    it.profileId.raw == profileId &&
                        it.sessionId.raw == sid &&
                        it.directory == location.directory
                }
            val own =
                row.optString("profileID", profileId) == profileId &&
                    matches.size == 1 &&
                    (matches.single().locationKey == null ||
                        placeMatches(
                            matches.single().locationKey,
                            graph.profile.origin.toString(),
                            location,
                        ))
            if (!own) {
                row.remove("requestID")
                continue
            }
            row.put("steerMode", steerMode(gateway, SessionId(sid)))
            val busy = statuses?.get(SessionId(sid))?.let { it != SessionStatus.Idle } ?: false
            val actions = mutableListOf<String>()
            val permission = permissions?.firstOrNull {
                it.id.raw == row.optString("requestID") && it.sessionId.raw == sid
            }
            if (permission == null) row.remove("requestID")
            if (allowCold && permission != null) actions += listOf("allowOnce", "reject")
            if (busy && connected) {
                val page = widgetRead { gateway.messagePage(SessionId(sid), limit = HISTORY_LIMIT) }
                val marker = (page as? Outcome.Ok)?.value?.items?.let(::runMarker)
                if (marker != null) {
                    row.put("runMarker", marker.first)
                        .put(
                            "startedAt",
                            marker.second.takeIf { it > 0 } ?: clock.now().toEpochMilliseconds(),
                        )
                    if (allowCold) actions += "stop"
                }
            }
            row.put("onDemandActions", JSONArray(actions))
        }
        val target =
            if (savedReadable)
                JSONObject()
                    .put("profileID", profileId)
                    .put("scopeToken", scopeToken)
                    .put("fingerprint", fingerprint)
                    .put("directory", location.directory ?: JSONObject.NULL)
                    .put("workspace", location.workspace ?: JSONObject.NULL)
                    .toString()
            else null
        if (
            stores.profiles.activeId.value?.raw != profileId ||
                registry.live.value[ProfileId(profileId)] !== graph ||
                gateway.location.value != location ||
                gateway.events.connects.value != currentIdentity[2] ||
                this@AgentWidgetWiring.fingerprint(profileId) != fingerprint
        )
            return@withLock
        val stillConnected = gateway.events.status.value == StreamStatus.Connected
        snapshot.put("connected", stillConnected)
        surface.publish(snapshot.toString(), profileId, target)
        WidgetFeed.onConnection(application, stillConnected)
    }

    private fun fingerprint(profile: String): String? {
        val saved = stores.profiles.profile(ProfileId(profile)) ?: return null
        return widgetFingerprint(saved)
    }

    private inner class Action(
        val payload: Map<String, Any?>,
        val statusOnly: Boolean,
        val live: Boolean,
        val deadlineMs: Long,
    ) {
        var dispatched = false
        var acknowledged = false
        private var owned: ServerGateway? = null
        private var ownedScope: ConnectionCoroutineScope? = null
        private var graph: ConnectionGraph? = null
        private var location: ServerLocation? = null
        private var fingerprint: String = ""
        private lateinit var row: JSONObject
        private lateinit var snapshot: JSONObject
        private val profile: String
            get() = payload["profileID"] as? String ?: ""

        private val sid: String
            get() = payload["sessionID"] as? String ?: ""

        private val action: String
            get() = payload["action"] as? String ?: ""

        suspend fun run(): Map<String, Any?> {
            try {
                if (stores.warm() !is Outcome.Ok) throw Refused("unavailable")
                if (profile.isBlank() || (sid.isBlank() && !(live && action == "voice")))
                    throw Refused("invalidRequest")
                if (
                    !statusOnly &&
                        action !in
                            setOf(
                                "allowOnce",
                                "reject",
                                "stop",
                                "steer",
                                "voice",
                                "approvePlan",
                                "resume",
                            )
                )
                    throw Refused("unsupported")
                snapshot = parse(stored("oc.widgetSessions")) ?: throw Refused("staleRequest")
                val rows = snapshot.optJSONArray("sessions") ?: throw Refused("staleRequest")
                row =
                    if (live && action == "voice") JSONObject()
                    else
                        (0 until rows.length()).mapNotNull(rows::optJSONObject).singleOrNull {
                            it.optString("id") == sid &&
                                it.optString("profileID", profile) == profile
                        } ?: throw Refused("staleRequest")
                fingerprint =
                    this@AgentWidgetWiring.fingerprint(profile) ?: throw Refused("staleRequest")
                val target = parse(stored("oc.widgetTarget.$profile"))
                if (
                    target == null ||
                        target.optString("fingerprint") != fingerprint ||
                        target.optString("scopeToken") != snapshot.optString("scopeToken")
                )
                    throw Refused("staleRequest")
                location =
                    ServerLocation(target.nullable("directory"), target.nullable("workspace"))
                guard()
                if (!statusOnly && action in setOf("approvePlan", "resume")) {
                    if (action !in row.optJSONArray("onDemandActions").strings())
                        throw Refused("unsupported")
                    guardTarget()
                    val status =
                        widgetTeams.execute(
                            payload,
                            row,
                            {
                                currentCoroutineContext().ensureActive()
                                guardTarget()
                            },
                            { dispatched = true },
                        )
                    if (status == "accepted") acknowledged = true
                    return result(
                        payload,
                        status,
                        if (status == "rejected") "staleRequest" else null,
                    ) +
                        if (status == "accepted")
                            mapOf(
                                "snapshot" to
                                    mapOf(
                                        "version" to 2,
                                        "profileID" to profile,
                                        "scopeToken" to payload["scopeToken"],
                                        "connected" to false,
                                        "sessions" to
                                            listOf(
                                                mapOf(
                                                    "id" to sid,
                                                    "team" to emptyMap<String, String>(),
                                                    "onDemandActions" to emptyList<String>(),
                                                )
                                            ),
                                    )
                            )
                        else emptyMap()
                }
                val gateway =
                    if (live) {
                        graph =
                            registry.live.value[ProfileId(profile)] ?: throw Refused("unavailable")
                        val server = graph!!.session.server
                        if (
                            server.events.status.value != StreamStatus.Connected ||
                                server.location.value != location
                        )
                            throw Refused("staleRequest")
                        server
                    } else {
                        if (!statusOnly && action !in setOf("allowOnce", "reject", "stop"))
                            throw Refused("unsupported")
                        if (!statusOnly && action !in row.optJSONArray("onDemandActions").strings())
                            throw Refused("unsupported")
                        openPrivate()
                    }
                guard()
                if (!read { gateway.health() }.healthy) throw Refused("unavailable")
                if (statusOnly) return status(gateway)
                if (action == "voice") return voice(gateway)
                return execute(gateway)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Refused) {
                return if (statusOnly)
                    mapOf("status" to if (e.code == "staleRequest") "stale" else "unavailable")
                else if (acknowledged) acknowledgedWithoutStatus()
                else
                    result(
                        payload,
                        if (acknowledged) "accepted"
                        else if (dispatched) "uncertain" else "rejected",
                        if (acknowledged) null else if (dispatched) "uncertainDelivery" else e.code,
                    )
            } catch (e: Exception) {
                return if (statusOnly) mapOf("status" to "unavailable")
                else if (acknowledged) acknowledgedWithoutStatus()
                else
                    result(
                        payload,
                        if (acknowledged) "accepted"
                        else if (dispatched) "uncertain" else "rejected",
                        if (acknowledged) null
                        else if (dispatched) "uncertainDelivery" else "unavailable",
                    )
            } finally {
                withContext(NonCancellable) {
                    try {
                        try {
                            withTimeoutOrNull(1_000) { owned?.close() }
                        } catch (_: Exception) {
                            /* Cleanup never changes an acknowledged command. */
                        }
                    } finally {
                        ownedScope?.coroutineContext?.get(Job)?.cancel()
                    }
                }
            }
        }

        fun acknowledgedWithoutStatus(): Map<String, Any?> =
            result(payload, "accepted") +
                mapOf(
                    "snapshot" to
                        mapOf(
                            "version" to 2,
                            "profileID" to profile,
                            "scopeToken" to payload["scopeToken"],
                            "connected" to false,
                            "sessions" to emptyList<Map<String, Any?>>(),
                        )
                )

        private fun guard() {
            if (clock.now().toEpochMilliseconds() >= deadlineMs) throw Refused("unavailable")
            if (!stores.lanes.forProfile(ProfileId(profile)).isAdmitting)
                throw Refused("staleRequest")
            if (
                stores.profiles.activeId.value?.raw != profile ||
                    this@AgentWidgetWiring.fingerprint(profile) != fingerprint
            )
                throw Refused("staleRequest")
            val current = parse(stored("oc.widgetSessions")) ?: throw Refused("staleRequest")
            if (
                current.optString("profileID") != profile ||
                    current.optString("scopeToken") != payload["scopeToken"]
            )
                throw Refused("staleRequest")
            val target = parse(stored("oc.widgetTarget.$profile")) ?: throw Refused("staleRequest")
            if (
                target.optString("profileID") != profile ||
                    target.optString("fingerprint") != fingerprint ||
                    target.optString("scopeToken") != payload["scopeToken"]
            )
                throw Refused("staleRequest")
            if (
                location != null &&
                    ServerLocation(target.nullable("directory"), target.nullable("workspace")) !=
                        location
            )
                throw Refused("staleRequest")
            val activeGraph = registry.live.value[ProfileId(profile)]
            if (
                location != null &&
                    activeGraph != null &&
                    activeGraph.session.server.location.value != location
            )
                throw Refused("staleRequest")
            if (
                live &&
                    graph != null &&
                    (registry.live.value[ProfileId(profile)] !== graph ||
                        graph!!.session.server.location.value != location ||
                        graph!!.session.server.events.status.value != StreamStatus.Connected)
            )
                throw Refused("staleRequest")
        }

        /** Identity is checked again immediately before mutation, after the final server read. */
        private fun guardTarget() {
            guard()
            val current = parse(stored("oc.widgetSessions")) ?: throw Refused("staleRequest")
            val rows = current.optJSONArray("sessions") ?: throw Refused("staleRequest")
            val now =
                (0 until rows.length()).mapNotNull(rows::optJSONObject).singleOrNull {
                    it.optString("id") == sid && it.optString("profileID", profile) == profile
                } ?: throw Refused("staleRequest")
            if (
                (row.has("team") &&
                    now.optJSONObject("team")?.toString() !=
                        row.optJSONObject("team")?.toString()) ||
                    listOf("requestID", "runMarker").any {
                        now.optString(it) != row.optString(it)
                    } ||
                    now.optLong("startedAt") != row.optLong("startedAt")
            )
                throw Refused("staleRequest")
        }

        private suspend fun <T> read(block: suspend () -> Outcome<T>): T {
            currentCoroutineContext().ensureActive()
            guard()
            val reply = block()
            currentCoroutineContext().ensureActive()
            guard()
            return when (reply) {
                is Outcome.Ok -> reply.value
                is Outcome.Failed -> throw Refused("unavailable")
            }
        }

        private suspend fun openPrivate(): ServerGateway {
            val loaded =
                stores.profiles.profile(ProfileId(profile)) ?: throw Refused("staleRequest")
            if (
                loaded.profile.transientTransport ||
                    loaded.profile.requiresOpenCode2Migration ||
                    loaded.reentry.password ||
                    loaded.cleartextUnconfirmed
            )
                throw Refused("unsupported")
            val server = loaded.toServerProfile() ?: throw Refused("unsupported")
            if (loaded.profile.usesAgentSocket || loaded.reentry.agentToken)
                throw Refused("unsupported")
            val credentials =
                when (val found = credentialSources.singleOrNull()?.resolve(server)) {
                    is Outcome.Ok -> found.value
                    else -> throw Refused("unavailable")
                }
            guard()
            val factory =
                factories.singleOrNull { it.backend == server.backend }
                    ?: throw Refused("unsupported")
            val connectionScope =
                ConnectionCoroutineScope(
                    supervisedContext(
                        appScope.coroutineContext[Job],
                        dispatchers.default,
                        "widget-private",
                    )
                )
            ownedScope = connectionScope
            val pair = read {
                factory
                    .connect(
                        GatewaySpec(
                            server,
                            credentials,
                            connectionScope,
                            location!!,
                            reconnectAllowed = MutableStateFlow(false),
                        )
                    )
                    .also {
                        if (it is Outcome.Ok) owned = it.value.live
                    }
            }
            return pair.live
        }

        private suspend fun voice(gateway: ServerGateway): Map<String, Any?> {
            val project = payload["lastProjectID"] as? String ?: throw Refused("staleRequest")
            val text = payload["text"] as? String ?: throw Refused("invalidRequest")
            if (
                project.isBlank() ||
                    project != snapshot.optString("lastProjectID") ||
                    location?.directory.isNullOrBlank() ||
                    text.isBlank() ||
                    text.length > 8000
            )
                throw Refused("staleRequest")
            guard()
            dispatched = true
            val created = gateway.createSession(NewSession(location = location))
            if (created !is Outcome.Ok) throw Refused("uncertainDelivery")
            guard()
            if (parse(stored("oc.widgetSessions"))?.optString("lastProjectID") != project)
                throw Refused("uncertainDelivery")
            val sent =
                gateway.prompt(created.value.id, OutgoingPrompt(text), payload["id"] as? String)
            if (sent !is Outcome.Ok || sent.value is PromptAdmission.Uncertain)
                throw Refused("uncertainDelivery")
            acknowledged = true
            return result(payload, "accepted")
        }

        private suspend fun execute(gateway: ServerGateway): Map<String, Any?> {
            val session = SessionId(sid)
            read { gateway.session(session) }
                .let {
                    if (
                        (location?.directory != null && it.directory != location?.directory) ||
                            (location?.workspace != null &&
                                it.workspaceId?.raw != location?.workspace)
                    )
                        throw Refused("staleRequest")
                }
            var marker = row.optString("runMarker")
            when (action) {
                "allowOnce",
                "reject" -> {
                    if (!gateway.capabilities.value.permissionRequests) throw Refused("unsupported")
                    val request = payload["requestID"] as? String ?: throw Refused("staleRequest")
                    if (request.isBlank() || request != row.optString("requestID"))
                        throw Refused("staleRequest")
                    if (
                        read { gateway.pendingPermissions() }
                            .none { it.id.raw == request && it.sessionId == session }
                    )
                        throw Refused("staleRequest")
                    marker =
                        runMarker(
                                read { gateway.messagePage(session, limit = HISTORY_LIMIT) }.items
                            )
                            ?.first
                            .orEmpty()
                    guardTarget()
                    dispatched = true
                    if (
                        gateway.replyPermission(
                            session,
                            RequestId(request),
                            if (action == "allowOnce") PermissionReply.Once
                            else PermissionReply.Reject,
                        ) is Outcome.Failed
                    )
                        throw Refused("uncertainDelivery")
                }
                "stop",
                "steer" -> {
                    if (
                        row.optLong("startedAt") <= 0 ||
                            row.optLong("startedAt") !=
                                (payload["expectedStartedAt"] as? Number)?.toLong()
                    )
                        throw Refused("staleRequest")
                    val busy =
                        read { gateway.sessionStatuses() }[session]?.let {
                                it != SessionStatus.Idle
                            } == true
                    if (
                        !busy ||
                            marker.isBlank() ||
                            runMarker(
                                    read { gateway.messagePage(session, limit = HISTORY_LIMIT) }
                                        .items
                                )
                                ?.first != marker
                    )
                        throw Refused("staleRequest")
                    if (action == "steer") {
                        val text = payload["text"] as? String ?: throw Refused("invalidRequest")
                        if (text.isBlank() || text.length > 8000) throw Refused("invalidRequest")
                        val mode = steerMode(gateway, session)
                        if (mode == "unknown" || mode == "restart") throw Refused("unsupported")
                        guardTarget()
                        dispatched = true
                        val sent =
                            gateway.prompt(
                                session,
                                OutgoingPrompt(
                                    text,
                                    delivery =
                                        if (gateway.capabilities.value.promptSteering)
                                            PromptDelivery.Steer
                                        else null,
                                ),
                            )
                        if (sent !is Outcome.Ok || sent.value is PromptAdmission.Uncertain)
                            throw Refused("uncertainDelivery")
                    } else {
                        guardTarget()
                        dispatched = true
                        val stopped = gateway.stop(session)
                        if (stopped !is Outcome.Ok || stopped.value == StopResult.Uncertain)
                            throw Refused("uncertainDelivery")
                    }
                }
                else -> throw Refused("unsupported")
            }
            // Once acknowledged, a failed or timed-out reconciliation never permits a resend.
            acknowledged = true
            val statuses = withTimeoutOrNull(2_000) { gateway.sessionStatuses() } as? Outcome.Ok
            val busy = statuses?.value?.get(session)?.let { it != SessionStatus.Idle }
            guardTarget()
            return result(payload, "accepted") +
                mapOf(
                    "snapshot" to
                        mapOf(
                            "version" to 2,
                            "profileID" to profile,
                            "scopeToken" to payload["scopeToken"],
                            "connected" to (statuses != null),
                            "sessions" to
                                listOf(
                                    mapOf(
                                        "id" to sid,
                                        "busy" to busy,
                                        "runMarker" to marker,
                                        "requestID" to
                                            if (action in setOf("allowOnce", "reject")) ""
                                            else row.optString("requestID"),
                                        "onDemandActions" to emptyList<String>(),
                                    )
                                ),
                        )
                )
        }

        private suspend fun status(gateway: ServerGateway): Map<String, Any?> {
            val marker = payload["runMarker"] as? String ?: throw Refused("staleRequest")
            fun ticketCurrent() {
                guardTarget()
                val saved = parse(stored("oc.widgetFollowup.$profile"))?.optJSONObject(sid)
                if (
                    payload["token"] !is String ||
                        saved?.optString("token") != payload["token"] ||
                        payload["fingerprint"] != fingerprint ||
                        marker.isBlank() ||
                        marker != row.optString("runMarker") ||
                        !row.optBoolean("busy") ||
                        row.optString("requestID").isNotEmpty() ||
                        (payload["startedAt"] as? Number)?.toLong() != row.optLong("startedAt")
                )
                    throw Refused("staleRequest")
            }
            ticketCurrent()
            val session = SessionId(sid)
            val messages = read { gateway.messagePage(session, limit = HISTORY_LIMIT) }.items
            if (runMarker(messages)?.first != marker) throw Refused("staleRequest")
            val statuses = read { gateway.sessionStatuses() }
            if (read { gateway.pendingPermissions() }.any { it.sessionId == session })
                throw Refused("staleRequest")
            val finalMessages = read { gateway.messagePage(session, limit = HISTORY_LIMIT) }.items
            if (runMarker(finalMessages)?.first != marker) throw Refused("staleRequest")
            ticketCurrent()
            return when (statuses[session]) {
                SessionStatus.Idle ->
                    mapOf(
                        "status" to "idle",
                        "preview" to presentation.assistantPreview(finalMessages),
                    )
                SessionStatus.Busy,
                SessionStatus.Retry -> mapOf("status" to "busy")
                else -> mapOf("status" to "unavailable")
            }
        }
    }

    private class Refused(val code: String) : Exception()

    private fun steerMode(gateway: ServerGateway, session: SessionId): String {
        if (gateway.capabilities.value.promptSteering) return "steer"
        val facet = gateway.facet<MidTurnPromptGateway>() ?: return "afterReply"
        return when (facet.midTurnPrompt(session)) {
            MidTurnPrompt.JoinsTurn -> "steer"
            MidTurnPrompt.RestartsTurn -> "restart"
            null -> "unknown"
        }
    }

    private fun runMarker(messages: List<MessageWithParts>): Pair<String, Long>? =
        messages
            .filter {
                it.info.role == Role.User && it.parts.none { p -> p.rawType.startsWith("v2:") }
            }
            .maxByOrNull { it.info.time?.createdMs ?: 0 }
            ?.info
            ?.let { if (it.id.isBlank()) null else it.id to (it.time?.createdMs ?: 0) }

    private fun placeMatches(raw: String?, origin: String, location: ServerLocation): Boolean {
        if (raw == null) return false
        val parts =
            try {
                JSONArray(raw)
            } catch (e: Exception) {
                return false
            }
        return parts.optString(0) == origin &&
            parts.opt(1).let { if (it == JSONObject.NULL) null else it as? String } ==
                location.directory &&
            parts.opt(2).let { if (it == JSONObject.NULL) null else it as? String } ==
                location.workspace
    }

    private fun stored(key: String): String? = (stores.prefs.get(key) as? PrefValue.Str)?.value

    private fun parse(raw: String?): JSONObject? =
        try {
            raw?.let(::JSONObject)
        } catch (e: Exception) {
            null
        }

    private fun JSONObject.nullable(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONArray?.strings(): Set<String> =
        if (this == null) emptySet() else (0 until length()).map { optString(it) }.toSet()

    private fun result(
        payload: Map<String, Any?>,
        status: String,
        error: String? = null,
    ): Map<String, Any?> = buildMap {
        put("id", payload["id"])
        put("action", payload["action"])
        put("status", status)
        if (error != null) put("error", error)
    }

    private companion object {
        const val LIVE_BUDGET_MS = 6_000L
        const val BACKGROUND_BUDGET_MS = 29_000L
        const val PUBLISH_BUDGET_MS = 2_000L
        const val HISTORY_LIMIT = 50
    }
}
