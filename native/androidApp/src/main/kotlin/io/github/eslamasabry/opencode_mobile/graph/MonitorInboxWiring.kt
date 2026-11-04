package io.github.eslamasabry.opencode_mobile.graph

import android.content.Intent
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.notifications.FrozenExtras
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.long
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.attention.monitor.*
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.feature.chats.monitor.*
import io.github.eslamasabry.oc.ui.routes.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Inbox rows retain their exact observed target until fresh request validation succeeds. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MonitorInboxSource>())
@Inject
class NativeMonitorInbox(
    private val monitoring: NativeProfileMonitoring,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val connections: ServerConnections,
    private val nav: AppNavigator,
    private val preferences: AppPreferences,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : MonitorInboxSource {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)

    private data class Target(val route: MonitoredRoute, val failedRun: Boolean)

    private val targets = linkedMapOf<String, Target>()
    private val tokens = linkedMapOf<String, Target>()
    private val mutable = MutableStateFlow(MonitorInboxFacts())
    override val facts: StateFlow<MonitorInboxFacts> = mutable.asStateFlow()

    init {
        monitoring.start()
        scope.launch {
            combine(
                    monitoring.snapshots,
                    monitoring.profileSettings,
                    monitoring.loaded,
                    stores.profiles.activeId,
                ) { _, _, _, _ ->
                    Unit
                }
                .collect { publish() }
        }
    }

    private fun publish() {
        if (!monitoring.loaded.value) return
        val enabled = monitoring.profileSettings.value.associateBy { it.id.raw }
        val saved = stores.profiles.profiles.value.associateBy { it.id.raw }
        val active = stores.profiles.activeId.value?.raw
        val rows = mutableListOf<MonitoredInboxRow>()
        val unreachable = mutableListOf<MonitoredUnreachable>()
        val next = linkedMapOf<String, Target>()
        val locale = preferences.localeTag.value?.let(Locale::forLanguageTag) ?: Locale.getDefault()
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
        for ((id, snapshot) in
            monitoring.snapshots.value.entries.sortedByDescending { it.value.checkedAt }) {
            val server = saved[id]?.profile?.name ?: continue
            if (
                snapshot.status == MonitorStatus.Unavailable &&
                    id != active &&
                    enabled[id]?.enabled == true
            )
                unreachable += MonitoredUnreachable(id, server)
            if (!snapshot.isCurrent) continue
            val at = snapshot.checkedAt ?: continue
            fun row(request: MonitoredRequest, kind: MonitoredRowKind, failed: Boolean = false) {
                val route = monitoring.route(id, request, at) ?: return
                val key =
                    DartJson.sha256Hex(
                        listOf(
                                id,
                                request.identity,
                                route.sourceIdentity,
                                route.directory,
                                route.workspace,
                                at.toEpochMilliseconds(),
                            )
                            .joinToString("\u0000")
                    )
                next[key] = Target(route, failed)
                rows +=
                    MonitoredInboxRow(
                        key,
                        request.title,
                        server,
                        kind,
                        format.format(Date(at.toEpochMilliseconds())),
                    )
            }
            if (id != active)
                for (request in snapshot.requests) row(
                    request,
                    when (request.kind) {
                        MonitoredRequestKind.Permission -> MonitoredRowKind.Permission
                        MonitoredRequestKind.Question -> MonitoredRowKind.Question
                        MonitoredRequestKind.Form -> MonitoredRowKind.Form
                        MonitoredRequestKind.CheckIn -> MonitoredRowKind.CheckIn
                    },
                )
            val after =
                stores.prefs
                    .long("oc.notify.checkInAfterMinutes")
                    ?.takeIf { it in 1..1440 }
                    ?.toInt()
            for (interval in
                snapshot.dueCheckIns(MonitorNotifyRules(checkInAfterMinutes = after))) row(
                interval.toRequest(),
                MonitoredRowKind.CheckIn,
            )
            for (item in snapshot.attention) {
                val sessionId = item.sessionId
                if (
                    item.isFresh && item.kind == MonitorAttentionKind.FailedRun && sessionId != null
                )
                    row(
                        MonitoredRequest(
                            item.id,
                            sessionId,
                            MonitoredRequestKind.CheckIn,
                            item.title,
                            item.directory,
                            item.workspace,
                        ),
                        MonitoredRowKind.FailedRun,
                        true,
                    )
            }
        }
        targets.clear()
        targets.putAll(next)
        mutable.value =
            MonitorInboxFacts(
                true,
                rows.toImmutableList(),
                unreachable.toImmutableList(),
                monitoring.startupFailure,
            )
    }

    override suspend fun refresh(): Outcome<Unit> = monitoring.refresh()

    override suspend fun token(
        profileId: String,
        sessionId: String,
        token: String,
    ): Outcome<String> =
        withContext(dispatchers.main) {
            if (!TOKEN.matches(token)) return@withContext stale()
            val route = monitoring.routeForToken(profileId, token) ?: return@withContext stale()
            if (route.sessionId != sessionId) return@withContext stale()
            val key = "token:$profileId:$token"
            while (tokens.size >= 32) tokens.remove(tokens.keys.first())
            tokens[key] = Target(route, false)
            key.ok()
        }

    override suspend fun open(key: String, allowSwitch: Boolean): Outcome<MonitoredOpen> =
        withContext(dispatchers.main) {
            val target = targets[key] ?: tokens[key] ?: return@withContext stale()
            val route = target.route
            val id = ProfileId(route.profileId)
            val saved = stores.profiles.profile(id) ?: return@withContext stale()
            if (!allowSwitch && stores.profiles.activeId.value != id) {
                val active = stores.profiles.activeId.value?.let { registry.live.value[it] }
                val status = active?.session?.server?.sessionStatuses()
                if (
                    status is Outcome.Failed ||
                        (status as? Outcome.Ok)?.value?.values?.any { it.raw != "idle" } == true
                ) {
                    return@withContext MonitoredOpen.Confirm(
                            key,
                            saved.profile.name,
                            stores.profiles.activeId.value?.let {
                                stores.profiles.profile(it)?.profile?.name
                            },
                        )
                        .ok()
                }
            }
            if (!monitoring.prepare(route) || stores.profiles.profile(id) != saved)
                return@withContext stale()
            when (val connected = connections.connect(id)) {
                is Outcome.Failed -> return@withContext connected
                is Outcome.Ok -> Unit
            }
            if (stores.profiles.profile(id) != saved || !monitoring.prepare(route))
                return@withContext stale()
            val graph = registry.live.value[id] ?: return@withContext stale()
            val location = ServerLocation(route.directory, route.workspace)
            graph.session.server.setLocation(location)
            if (route.sessionId == "global") {
                if (route.kind != MonitoredRequestKind.Form) return@withContext stale()
                val forms = graph.session.server.pendingForms()
                if (
                    forms !is Outcome.Ok ||
                        forms.value.none {
                            it.id.raw == route.requestId && it.sessionId.raw == "global"
                        } ||
                        stores.profiles.profile(id) != saved ||
                        registry.live.value[id] !== graph ||
                        graph.session.server.location.value != location
                )
                    return@withContext stale()
                tokens.remove(key)
                return@withContext MonitoredOpen.GlobalForm(
                        id,
                        route.requestId,
                        route.directory,
                        route.workspace,
                    )
                    .ok()
            }
            val session = graph.session.server.session(SessionId(route.sessionId))
            if (
                session !is Outcome.Ok ||
                    session.value.id.raw != route.sessionId ||
                    (session.value.directory != null &&
                        session.value.directory != route.directory) ||
                    session.value.workspaceId?.raw != route.workspace ||
                    stores.profiles.profile(id) != saved ||
                    registry.live.value[id] !== graph ||
                    graph.session.server.location.value != location
            )
                return@withContext stale()
            tokens.remove(key)
            MonitoredOpen.Chat(
                    ConversationRef(id, SessionId(route.sessionId)),
                    route.requestId.takeUnless { route.kind == MonitoredRequestKind.CheckIn },
                    target.failedRun,
                )
                .ok()
        }

    /**
     * Called before PlatformHub.alertTap. Consume once; a malformed/stale token never falls through
     * to a bare chat.
     */
    fun handleIntent(intent: Intent): Boolean {
        if (!intent.hasExtra(FrozenExtras.EXTRA_MONITOR_TOKEN)) return false
        if (
            intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND) in
                setOf("quota", "quota_reset")
        )
            return false
        val token = intent.getStringExtra(FrozenExtras.EXTRA_MONITOR_TOKEN)
        val id = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID)
        val session = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID)
        listOf(
                FrozenExtras.EXTRA_MONITOR_TOKEN,
                FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID,
                FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID,
                FrozenExtras.EXTRA_CODING_ALERT_KIND,
                FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID,
            )
            .forEach(intent::removeExtra)
        if (
            token != null &&
                id != null &&
                session != null &&
                TOKEN.matches(token) &&
                id.isNotBlank() &&
                session.isNotBlank()
        ) {
            nav.open(MonitorInboxRoute(id, session, token))
        } else nav.open(MonitorInboxRoute())
        return true
    }

    private fun <T> stale(): Outcome<T> = Failure.Refused(RefusalReason.StaleScope).failed()

    companion object {
        private val TOKEN = Regex("^[a-f0-9]{64}$")
    }
}
