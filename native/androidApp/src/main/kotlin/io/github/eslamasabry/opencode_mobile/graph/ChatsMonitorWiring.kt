package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.attention.monitor.*
import io.github.eslamasabry.oc.feature.chats.presentation.*
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Conversations' watched-servers panel over the app's one saved-server monitor
 * ([AndroidProfileMonitoring]). It never owns a second engine: enrolment, polling, alerts, opaque
 * notification taps and profile deletion all stay with that monitor, so a watched server is read,
 * and alerted, exactly once.
 */
@SingleIn(AppScope::class)
@Inject
class AppChatsMonitor(
    private val runtime: PreviewRuntime,
    private val stores: PreviewStores,
    private val monitoring: AndroidProfileMonitoring,
    appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val mutable = MutableStateFlow(ChatsMonitoringUi())
    val snapshot: StateFlow<ChatsMonitoringUi> = mutable.asStateFlow()
    private var failure: Failure? = null
    private var engine: ProfileMonitor? = null
    private val routes = mutableMapOf<String, MonitoredRoute>()

    init {
        scope.launch {
            when (val loaded = stores.warm()) {
                is Outcome.Failed -> {
                    failure = loaded.failure
                    mutable.value = ChatsMonitoringUi(failure = loaded.failure)
                    return@launch
                }
                is Outcome.Ok -> Unit
            }
            val shared = monitoring.awaitEngine()
            engine = shared
            publish()
            launch { shared.revision.collect { publish() } }
            launch { runtime.profiles.profiles.collect { publish() } }
            launch { runtime.profiles.activeId.collect { publish() } }
            launch {
                stores.prefs.changes.collect { keys ->
                    if (
                        keys.any {
                            it.startsWith("oc.chatListShown.") ||
                                it.startsWith("oc.notifyRules.") ||
                                it.startsWith("oc.automation.")
                        }
                    )
                        publish()
                }
            }
        }
    }

    private fun publish() {
        val monitor = engine ?: return
        routes.clear()
        val sources =
            runtime.profiles.profiles.value
                .filter { it.profile.backend != BackendKind.Codex }
                .map { loaded ->
                    val state = monitor.snapshotFor(loaded.id.raw)
                    ChatsMonitorSourceUi(
                        loaded.id,
                        loaded.profile.name,
                        state.status != MonitorStatus.Disabled,
                        when (state.status) {
                            MonitorStatus.Disabled -> ChatsMonitorStatus.Off
                            MonitorStatus.Waiting -> ChatsMonitorStatus.Waiting
                            MonitorStatus.Checking -> ChatsMonitorStatus.Checking
                            MonitorStatus.Current -> ChatsMonitorStatus.Current
                            MonitorStatus.Unavailable -> ChatsMonitorStatus.Unavailable
                            MonitorStatus.WifiRequired -> ChatsMonitorStatus.WifiRequired
                            MonitorStatus.Paused -> ChatsMonitorStatus.Paused
                        },
                    )
                }
                .toImmutableList()
        val requests =
            runtime.profiles.profiles.value
                .flatMap { loaded ->
                    // A source already in the list shows its own needs-you rows.
                    if (runtime.listSourceShown(loaded.id)) return@flatMap emptyList()
                    val state = monitor.snapshotFor(loaded.id.raw)
                    val checked = state.checkedAt
                    if (!state.isCurrent || checked == null) return@flatMap emptyList()
                    state.requests
                        .filter { it.kind != MonitoredRequestKind.CheckIn }
                        .map { request ->
                            val key = "watched:${loaded.id.raw}:${request.identity}"
                            val p = loaded.profile
                            val profile = MonitorProfile(p.id.raw, p.baseUrl, p.username, "v2")
                            routes[key] =
                                MonitoredRoute(
                                    p.id.raw,
                                    request.id,
                                    request.sessionId,
                                    request.kind,
                                    checked,
                                    p.baseUrl,
                                    ProfileMonitor.routeSourceIdentity(profile),
                                    request.directory,
                                    request.workspace,
                                )
                            ChatsWatchedRequestUi(
                                key,
                                ConversationRef(loaded.id, SessionId(request.sessionId)),
                                SessionTitles.present(request.title),
                                p.name,
                                when (request.kind) {
                                    MonitoredRequestKind.Permission -> ChatsWatchedKind.Permission
                                    MonitoredRequestKind.Question -> ChatsWatchedKind.Question
                                    MonitoredRequestKind.Form -> ChatsWatchedKind.Form
                                    MonitoredRequestKind.CheckIn -> error("filtered check-in")
                                },
                                checked.toEpochMilliseconds(),
                            )
                        }
                }
                .toImmutableList()
        mutable.value = ChatsMonitoringUi(sources, requests, monitor.refreshing, failure)
    }

    /** The same enrolment the Servers page uses (it refreshes the credential identity first). */
    suspend fun setWatched(id: ProfileId, enabled: Boolean): Outcome<Unit> {
        val result = monitoring.enabled(id, enabled)
        return withContext(dispatchers.main) {
            failure = (result as? Outcome.Failed)?.failure
            if (result is Outcome.Ok && enabled) engine?.refresh()
            publish()
            result
        }
    }

    suspend fun refresh(): Outcome<Unit> =
        withContext(dispatchers.main) {
            val monitor = engine ?: return@withContext Failure.Busy(BusyKind.Starting).failed()
            monitor.sharedRulesChanged(checkInChanged = false)
            monitor.refresh()
            publish()
            Outcome.Ok(Unit)
        }

    /** A row opens only after the monitor's private preflight still finds that exact request. */
    suspend fun open(key: String): Outcome<ConversationRef> {
        val route: MonitoredRoute =
            withContext(dispatchers.main) { routes[key] }
                ?: return withContext(dispatchers.main) { refuse() }
        val opened =
            monitoring.openWatched(route) { monitor ->
                monitor.snapshotFor(route.profileId).requests.any {
                    it.id == route.requestId &&
                        it.sessionId == route.sessionId &&
                        it.kind == route.kind
                }
            }
        return withContext(dispatchers.main) {
            failure = (opened as? Outcome.Failed)?.failure
            publish()
            opened
        }
    }

    private fun refuse(): Outcome.Failed {
        val refused = Failure.Refused(RefusalReason.StaleScope)
        failure = refused
        publish()
        return Outcome.Failed(refused)
    }
}
