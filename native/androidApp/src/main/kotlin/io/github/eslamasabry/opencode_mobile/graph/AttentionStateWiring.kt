package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActKind
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActivity
import io.github.eslamasabry.oc.engine.attention.activity.AutomaticActivityRegistry
import io.github.eslamasabry.oc.engine.attention.elsewhere.AttentionEvent
import io.github.eslamasabry.oc.engine.attention.feed.*
import io.github.eslamasabry.oc.engine.attention.monitor.MonitorNotifyRules
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chats.attention.*
import java.text.DateFormat
import java.util.Date
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Activity uses the adopted preferences and the profile deletion lane; no optimistic writes reach
 * history.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoAttentionHistory::class])
@Inject
class AppAttentionState(
    private val stores: PreviewStores,
    private val connections: ConnectionRegistry,
    private val redactor: Redactor,
    private val clock: Clock,
    private val scope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : AttentionHistory {
    private val mutableState = MutableStateFlow(AttentionUi())
    override val state: StateFlow<AttentionUi> = mutableState.asStateFlow()
    private var started = false
    private var settingsSaveFailed = false
    private val refreshLane = Mutex()
    private val sourceLanes = mutableMapOf<ProfileId, Mutex>()
    private val trackers = mutableMapOf<ProfileId, SelectedTransportAttention>()
    private val facts = mutableMapOf<ProfileId, MutableMap<String, Session>>()
    private val graphs = mutableMapOf<ProfileId, ConnectionGraph>()
    private val jobs = mutableMapOf<ProfileId, List<Job>>()
    private val histories = mutableMapOf<ProfileId, AutomaticActivity>()
    private val historyJobs = mutableMapOf<ProfileId, Job>()
    private val historyRegistry =
        AutomaticActivityRegistry(
            object : AttentionKeyStore {
                override suspend fun read(key: String): String? =
                    (stores.prefs.get(key) as? PrefValue.Str)?.value

                override suspend fun write(key: String, value: String): Boolean {
                    val id = ProfileId(key.removePrefix("oc.automaticActivity."))
                    if (stores.profiles.profile(id) == null) return false
                    return stores.prefs.commit(stores.lanes.forProfile(id)) {
                        putString(key, value)
                    } is Outcome.Ok
                }

                override suspend fun remove(key: String): Boolean {
                    val id = ProfileId(key.removePrefix("oc.automaticActivity."))
                    return stores.prefs.commit(stores.lanes.forProfile(id)) { remove(key) } is
                        Outcome.Ok
                }
            },
            redactor,
        )

    fun start() {
        if (started) return
        started = true
        scope.launch(dispatchers.main) {
            if (stores.warm() !is Outcome.Ok) {
                mutableState.value = AttentionUi(loading = false, incomplete = true)
                return@launch
            }
            combine(connections.live, stores.profiles.profiles, stores.profiles.activeId) {
                    live,
                    saved,
                    active ->
                    Triple(live, saved, active)
                }
                .collect { (live, saved, _) ->
                    val ids = saved.map { it.id }.toSet()
                    for (id in (histories.keys - ids).toList()) {
                        historyJobs.remove(id)?.cancel()
                        histories.remove(id)?.dispose()
                        historyRegistry.forgetProfile(id.raw)
                    }
                    for (owner in saved) {
                        if (owner.id !in histories) {
                            val history =
                                historyRegistry.forProfile(owner.id.raw) {
                                    stores.profiles.profile(owner.id) != null &&
                                        stores.lanes.forProfile(owner.id).isAdmitting
                                }
                            if (history != null) {
                                histories[owner.id] = history
                                historyJobs[owner.id] =
                                    scope.launch(dispatchers.main) {
                                        history.state.collect { publish() }
                                    }
                            }
                        }
                    }
                    for (id in (graphs.keys - live.keys.intersect(ids)).toList()) detach(id)
                    for ((id, graph) in live) if (id in ids && graphs[id] !== graph)
                        attach(id, graph)
                    publish()
                }
        }
    }

    private fun detach(id: ProfileId) {
        jobs.remove(id)?.forEach { it.cancel() }
        graphs.remove(id)
        trackers.remove(id)
        facts.remove(id)
        sourceLanes.remove(id)
    }

    private fun attach(id: ProfileId, graph: ConnectionGraph) {
        detach(id)
        graphs[id] = graph
        sourceLanes[id] = Mutex()
        val tracker = SelectedTransportAttention(clock)
        trackers[id] = tracker
        facts[id] = mutableMapOf()
        val server = graph.session.server
        jobs[id] =
            listOf(
                graph.scope.launch(dispatchers.main) {
                    var attached = false
                    var lost = false
                    var count = 0L
                    server.events.status.collect { status ->
                        if (graphs[id] !== graph) return@collect
                        val connected = status == StreamStatus.Connected
                        tracker.streamConnected(connected)
                        if (connected) {
                            if (attached && lost) {
                                count++
                                val owner = stores.profiles.profile(id)
                                if (owner != null)
                                    histories[id]?.record(
                                        eventId =
                                            "stream.reconnect:${clock.now().toEpochMilliseconds()}:$count",
                                        location = DartJson.encode(listOf(owner.profile.baseUrl)),
                                        kind = AutomaticActKind.Reconnect,
                                        summary = owner.profile.name,
                                        occurredAt = clock.now(),
                                    )
                            }
                            attached = true
                            lost = false
                        } else if (
                            attached &&
                                (status is StreamStatus.Reconnecting ||
                                    status is StreamStatus.Disconnected ||
                                    status == StreamStatus.WaitingForNetwork)
                        )
                            lost = true
                        publish()
                    }
                },
                graph.scope.launch(dispatchers.main) {
                    server.events.all.collect { envelope ->
                        if (graphs[id] !== graph) return@collect
                        val event = envelope.event
                        when (event) {
                            is GatewayEvent.SessionUpserted ->
                                facts[id]?.set(event.session.id.raw, event.session)
                            is GatewayEvent.SessionDeleted -> {
                                facts[id]?.remove(event.sessionId.raw)
                                tracker.handle(wire("session.deleted", event.sessionId.raw))
                            }
                            is GatewayEvent.RunStarted ->
                                tracker.handle(wire("session.status", event.sessionId.raw, "busy"))
                            is GatewayEvent.RunRetrying ->
                                tracker.handle(wire("session.status", event.sessionId.raw, "retry"))
                            is GatewayEvent.RunEnded -> {
                                val outcome = event.outcome
                                tracker.handle(
                                    if (outcome is RunOutcome.Failed)
                                        // The error name lets the tracker skip a person's own Stop
                                        // (sessionErrorIsStop); no error prose is carried.
                                        wire(
                                            "session.error",
                                            event.sessionId.raw,
                                            errorName = outcome.tag?.raw ?: outcome.kind?.name,
                                        )
                                    else wire("session.idle", event.sessionId.raw)
                                )
                            }
                            is GatewayEvent.MessageUpserted ->
                                if (
                                    event.message.role ==
                                        io.github.eslamasabry.oc.core.model.transcript.Role.User
                                ) {
                                    tracker.handle(
                                        AttentionEvent(
                                            "message.updated",
                                            null,
                                            JsonObject(
                                                mapOf(
                                                    "info" to
                                                        JsonObject(
                                                            mapOf(
                                                                "role" to JsonPrimitive("user"),
                                                                "sessionID" to
                                                                    JsonPrimitive(
                                                                        event.message.sessionId
                                                                    ),
                                                            )
                                                        )
                                                )
                                            ),
                                        )
                                    )
                                }
                            GatewayEvent.Connected -> refreshGraph(id, graph)
                            is GatewayEvent.PermissionAsked,
                            is GatewayEvent.PermissionSettled,
                            is GatewayEvent.FormCreated,
                            is GatewayEvent.FormSettled -> refreshGraph(id, graph)
                            else -> Unit
                        }
                        publish()
                    }
                },
                graph.scope.launch(dispatchers.main) {
                    refreshGraph(id, graph)
                    publish()
                },
            )
    }

    private fun wire(
        type: String,
        session: String,
        status: String? = null,
        errorName: String? = null,
    ): AttentionEvent =
        AttentionEvent(
            type,
            null,
            JsonObject(
                buildMap {
                    put("sessionID", JsonPrimitive(session))
                    if (status != null) put("status", JsonPrimitive(status))
                    if (errorName != null)
                        put("error", JsonObject(mapOf("name" to JsonPrimitive(errorName))))
                }
            ),
        )

    private fun monitored(id: ProfileId): Boolean {
        val rules =
            (stores.prefs.get("oc.notifyRules.${id.raw}") as? PrefValue.Str)
                ?.value
                ?.let(MonitorNotifyRules::fromJson)
        if (rules?.enabled != true) return false
        val raw =
            (stores.prefs.get("oc.automation.${id.raw}") as? PrefValue.Str)?.value ?: return false
        val policy =
            try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: return false
        return (policy["version"] as? JsonPrimitive)?.intOrNull == 1 &&
            ((policy["behaviors"] as? JsonObject)?.get("monitorOtherServers") as? JsonPrimitive)
                ?.booleanOrNull == true
    }

    private suspend fun refreshGraph(id: ProfileId, graph: ConnectionGraph) {
        val lane = sourceLanes[id] ?: return
        lane.withLock {
            if (graphs[id] !== graph || (id != stores.profiles.activeId.value && !monitored(id)))
                return@withLock
            val tracker = trackers[id] ?: return@withLock
            val server = graph.session.server
            tracker.streamConnected(server.events.status.value == StreamStatus.Connected)
            val location = server.location.value
            fun current(): Boolean =
                graphs[id] === graph &&
                    server.location.value == location &&
                    stores.profiles.profile(id) != null
            when (val read = server.pendingPermissions()) {
                is Outcome.Ok ->
                    if (current())
                        tracker.requestsRead(
                            AttentionKind.Permission,
                            read.value.map {
                                AttentionRequest(
                                    it.id.raw,
                                    it.sessionId.raw,
                                    AttentionRequestKind.Permission,
                                )
                            },
                        )
                is Outcome.Failed ->
                    if (current()) tracker.requestsReadFailed(AttentionKind.Permission)
            }
            when (val read = server.pendingQuestions()) {
                is Outcome.Ok ->
                    if (current())
                        tracker.requestsRead(
                            AttentionKind.Question,
                            read.value.map {
                                AttentionRequest(
                                    it.id.raw,
                                    it.sessionId.raw,
                                    AttentionRequestKind.Question,
                                )
                            },
                        )
                is Outcome.Failed ->
                    if (current()) tracker.requestsReadFailed(AttentionKind.Question)
            }
            if (server.capabilities.value.forms)
                when (val read = server.pendingForms()) {
                    is Outcome.Ok ->
                        if (current())
                            tracker.requestsRead(
                                AttentionKind.Form,
                                read.value.map {
                                    AttentionRequest(
                                        it.id.raw,
                                        it.sessionId.raw,
                                        AttentionRequestKind.Form,
                                    )
                                },
                            )
                    is Outcome.Failed ->
                        if (current()) tracker.requestsReadFailed(AttentionKind.Form)
                }
        }
    }

    override suspend fun refresh(): Outcome<Unit> =
        withContext(dispatchers.main) {
            start()
            refreshLane.withLock {
                if (stores.warm() !is Outcome.Ok)
                    return@withLock Outcome.Failed(Failure.StorageRefused())
                for (owner in stores.profiles.profiles.value) {
                    if (owner.id != stores.profiles.activeId.value && !monitored(owner.id)) continue
                    val graph =
                        connections.live.value[owner.id]
                            ?: owner.toServerProfile()?.let {
                                (connections.open(it) as? Outcome.Ok)?.value
                            }
                    if (graph != null) {
                        if (graphs[owner.id] !== graph) attach(owner.id, graph)
                        refreshGraph(owner.id, graph)
                    }
                }
                publish()
                Outcome.Ok(Unit)
            }
        }

    override suspend fun setMonitored(profile: String, enabled: Boolean): Outcome<Unit> =
        withContext(dispatchers.main) {
            val id = ProfileId(profile)
            if (stores.profiles.profile(id) == null)
                return@withContext Outcome.Failed(Failure.StorageRefused())
            val key = "oc.automation." + profile
            val existing = stores.prefs.get(key)
            val raw = (existing as? PrefValue.Str)?.value
            val previous =
                if (raw == null) null
                else
                    try {
                        Json.parseToJsonElement(raw) as? JsonObject
                    } catch (_: IllegalArgumentException) {
                        null
                    }
            if (
                existing != null &&
                    (previous == null || (previous["version"] as? JsonPrimitive)?.intOrNull != 1)
            ) {
                settingsSaveFailed = true
                publish()
                return@withContext Outcome.Failed(Failure.StorageRefused())
            }
            val values: MutableMap<String, JsonElement> =
                previous?.toMutableMap()
                    ?: mutableMapOf(
                        "version" to JsonPrimitive(1),
                        "supervision" to JsonPrimitive("high"),
                        "autoApprove" to JsonPrimitive(false),
                        "autoMergeOnGreen" to JsonPrimitive(false),
                    )
            val behaviors = (values["behaviors"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            behaviors["monitorOtherServers"] = JsonPrimitive(enabled)
            values["behaviors"] = JsonObject(behaviors)
            val rulesKey = "oc.notifyRules." + profile
            val oldRules = (stores.prefs.get(rulesKey) as? PrefValue.Str)?.value
            val rules = oldRules?.let(MonitorNotifyRules::fromJson)
            if (oldRules != null && rules == null) {
                settingsSaveFailed = true
                publish()
                return@withContext Outcome.Failed(Failure.StorageRefused())
            }
            val result =
                stores.prefs.commit(stores.lanes.forProfile(id)) {
                    putString(key, JsonObject(values).toString())
                    putString(
                        rulesKey,
                        (rules ?: MonitorNotifyRules()).copy(enabled = enabled).toJson(),
                    )
                }
            settingsSaveFailed = result is Outcome.Failed
            if (result is Outcome.Ok && enabled) refresh()
            publish()
            result
        }

    override suspend fun acknowledge(shown: List<HistoryIdentity>): Outcome<Unit> {
        val captured = shown.toList()
        return withContext(dispatchers.main) {
            var failed = false
            for ((profile, rows) in captured.groupBy { it.profile }) {
                if (histories[ProfileId(profile)]?.acknowledge(rows.map { it.id }) != true)
                    failed = true
            }
            publish()
            if (failed) Outcome.Failed(Failure.StorageRefused()) else Outcome.Ok(Unit)
        }
    }

    private fun publish() {
        val saved = stores.profiles.profiles.value
        val selected = stores.profiles.activeId.value
        val included = saved.filter { it.id == selected || monitored(it.id) }
        val feed =
            AttentionFeed.fromServers(
                included.map { owner ->
                    val graph = graphs[owner.id]
                    val location = graph?.session?.server?.location?.value
                    val snapshot =
                        trackers[owner.id]?.snapshot(
                            owner.id.raw,
                            location?.directory,
                            location?.workspace,
                            graph?.session?.server?.capabilities?.value?.forms == true,
                            sessions = { id ->
                                facts[owner.id]?.get(id)?.let {
                                    AttentionSessionFacts(
                                        it.title,
                                        it.directory,
                                        it.workspaceId?.raw,
                                    )
                                }
                            },
                        )
                            ?: AttentionServerSnapshot(
                                owner.id.raw,
                                AttentionSourceStatus.Unavailable,
                            )
                    AttentionServer(owner.id.raw, owner.profile.name, snapshot)
                },
                clock.now(),
                redactor,
            )
        val historyRows =
            saved
                .flatMap { owner ->
                    val history = histories[owner.id] ?: return@flatMap emptyList()
                    val graphLocation = graphs[owner.id]?.session?.server?.location?.value
                    val places =
                        setOf(
                            history.locationKey(DartJson.encode(listOf(owner.profile.baseUrl))),
                            history.locationKey(
                                DartJson.encode(
                                    listOf(
                                        owner.profile.baseUrl,
                                        graphLocation?.directory,
                                        graphLocation?.workspace,
                                    )
                                )
                            ),
                        )
                    history.state.value.acts
                        .filter { !it.acknowledged && it.locationKey in places }
                        .map { act ->
                            act.occurredAt to
                                HistoryRow(
                                    HistoryIdentity(owner.id.raw, act.id),
                                    act.kind,
                                    act.summary,
                                    DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT,
                                            DateFormat.SHORT,
                                        )
                                        .format(Date(act.occurredAt.toEpochMilliseconds())),
                                )
                        }
                }
                .sortedByDescending { it.first }
                .map { it.second }
                .toImmutableList()
        mutableState.value =
            AttentionUi(
                loading = false,
                requests =
                    feed.items
                        .map { item ->
                            AttentionRow(
                                item.identity,
                                item.kind,
                                item.serverName,
                                item.title,
                                item.isFresh,
                                item.target.sessionId
                                    ?.takeIf { it != "global" }
                                    ?.let {
                                        ConversationRef(ProfileId(item.profileId), SessionId(it))
                                    },
                            )
                        }
                        .toImmutableList(),
                history = historyRows,
                incomplete =
                    included.any { graphs[it.id] == null } || feed.servers.any { !it.isFresh },
                corrupt = histories.values.any { it.state.value.corruptHistory },
                saveFailed =
                    settingsSaveFailed || histories.values.any { it.state.value.persistenceFailed },
                monitors =
                    saved
                        .filter { it.id != selected }
                        .map {
                            MonitorRow(
                                it.id.raw,
                                redactor.redact(it.profile.name),
                                monitored(it.id),
                            )
                        }
                        .toImmutableList(),
            )
    }
}

@ContributesTo(AppScope::class)
interface AttentionStateAccessors {
    val attentionState: AppAttentionState
}
