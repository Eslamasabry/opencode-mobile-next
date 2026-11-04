package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.notifications.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.attention.monitor.*
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.requests.consent.AutomationBehavior
import io.github.eslamasabry.oc.engine.requests.consent.AutomationPolicy
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Real saved-server enrollment, bounded private reads, opaque notification taps and deletion
 * admission.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ServerMonitoring>())
@Inject
class AndroidProfileMonitoring(
    private val application: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val factories: Set<GatewayFactory>,
    private val credentials: Set<ConnectionCredentialSource>,
    private val background: BackgroundHub,
    private val nav: AppNavigator,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    preferences: AppPreferences,
) : ServerMonitoring {
    private val main = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val changes = MutableStateFlow<Map<ProfileId, ServerMonitorUi>>(emptyMap())
    override val rows: StateFlow<Map<ProfileId, ServerMonitorUi>> = changes.asStateFlow()
    private val prepared = CompletableDeferred<ProfileMonitor>()
    private var engine: ProfileMonitor? = null
    private var started = false
    private var inFront = false
    private val stopped = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val identity = mutableMapOf<String, String>()
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val channels =
        ChannelRegistry(application.getSystemService(NotificationManager::class.java), words)
    private val shade = AndroidAlertShade(application, words, channels)

    private val source =
        object : MonitorProfiles {
            override fun profiles(): List<MonitorProfile> =
                stores.profiles.profiles.value.map { loaded ->
                    val p = loaded.profile
                    MonitorProfile(
                        id = p.id.raw,
                        baseUrl = p.baseUrl,
                        username = p.username,
                        flavor = "v2",
                        backend =
                            when (p.backend) {
                                BackendKind.OpenCode2 -> MonitorBackend.OpenCode
                                BackendKind.Paseo -> MonitorBackend.Paseo
                                BackendKind.Codex -> MonitorBackend.Codex
                            },
                        credential = identity[p.id.raw].orEmpty(),
                        usable =
                            loaded.toServerProfile() != null &&
                                !loaded.cleartextUnconfirmed &&
                                !loaded.reentry.password &&
                                !loaded.reentry.agentToken,
                        boundDirectory = p.codexDirectory.takeIf { it.isNotBlank() },
                    )
                }

            override fun location(profileId: String): MonitorLocation? {
                val live =
                    registry.live.value[ProfileId(profileId)]?.session?.server?.location?.value
                if (live != null) return MonitorLocation(live.directory, live.workspace)
                val json =
                    stores.prefs.string("oc.location.$profileId")?.let(DartJson::decodeOrNull)
                        as? Map<*, *> ?: return null
                return MonitorLocation(json["directory"] as? String, json["workspace"] as? String)
            }
        }
    private val consent =
        object : MonitorConsent {
            private val listeners = mutableMapOf<String, MutableSet<() -> Unit>>()

            override fun allows(profileId: String): Boolean =
                stores.prefs
                    .string("oc.automation.$profileId")
                    ?.let(AutomationPolicy::fromJson)
                    ?.allows(AutomationBehavior.MonitorOtherServers) == true

            override fun hasRecord(profileId: String): Boolean =
                stores.prefs.get("oc.automation.$profileId") != null

            override suspend fun set(profileId: String, allowed: Boolean): Boolean {
                val key = "oc.automation.$profileId"
                val old = stores.prefs.get(key)
                val policy =
                    (old as? PrefValue.Str)?.value?.let(AutomationPolicy::fromJson)
                        ?: AutomationPolicy()
                val result =
                    stores.prefs.compareAndCommit(
                        stores.lanes.forProfile(ProfileId(profileId)),
                        mapOf(key to old),
                    ) {
                        putString(
                            key,
                            policy
                                .withBehavior(AutomationBehavior.MonitorOtherServers, allowed)
                                .toJson()
                                .toString(),
                        )
                    }
                if (result.getOrNull() != true) return false
                changed(profileId)
                return true
            }

            override fun observe(profileId: String, listener: () -> Unit): () -> Unit {
                listeners.getOrPut(profileId) { mutableSetOf() }.add(listener)
                return {
                    listeners[profileId]?.remove(listener)
                }
            }

            fun changed(id: String) {
                listeners[id]?.toList()?.forEach { it() }
            }
        }
    private val prefs =
        object : MonitorPrefs {
            override fun getString(key: String): String? = stores.prefs.string(key)

            override fun getStringList(key: String): List<String>? = stores.prefs.stringList(key)

            override fun contains(key: String): Boolean = stores.prefs.get(key) != null

            private suspend fun edit(key: String, change: PrefEdit.() -> Unit): Boolean {
                val prefix =
                    listOf(
                            "oc.notifyRules.",
                            "oc.monitorAlerts.",
                            "oc.monitorRoutes.",
                            "oc.monitorBusy.",
                        )
                        .firstOrNull { key.startsWith(it) } ?: return false
                return stores.prefs.commit(
                    stores.lanes.forProfile(ProfileId(key.removePrefix(prefix))),
                    change,
                ) is Outcome.Ok
            }

            override suspend fun putString(key: String, value: String): Boolean =
                edit(key) { putString(key, value) }

            override suspend fun putStringList(key: String, value: List<String>): Boolean =
                edit(key) { putStringList(key, value) }

            override suspend fun remove(key: String): Boolean = edit(key) { remove(key) }
        }
    private val shared =
        object : MonitorSharedRules {
            override val requestsAllowed: Boolean
                get() = stores.prefs.bool("oc.notify.requests") != false

            override fun applyTo(own: MonitorNotifyRules): MonitorNotifyRules =
                own.copy(
                    wifiOnly = own.wifiOnly || stores.prefs.bool("oc.notify.wifiOnly") == true,
                    quietStart =
                        stores.prefs.long("oc.notify.quietStart")?.toInt()?.takeIf { it in 0..1439 }
                            ?: own.quietStart,
                    quietEnd =
                        stores.prefs.long("oc.notify.quietEnd")?.toInt()?.takeIf { it in 0..1439 }
                            ?: own.quietEnd,
                )
        }

    fun start() {
        if (started) return
        started = true
        main.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            channels.ensure()
            updateIdentity()
            val engine =
                ProfileMonitor(
                    prefs,
                    source,
                    consent,
                    ::gateway,
                    { id -> id !in stopped && stores.profiles.profile(ProfileId(id)) != null },
                    ::wifi,
                    object : MonitorAlerts {
                        override suspend fun alert(
                            profileId: String,
                            request: MonitoredRequest,
                            key: String,
                            token: String,
                        ): Boolean {
                            val alert =
                                CodingAlert(
                                    kind =
                                        when (request.kind) {
                                            MonitoredRequestKind.Permission -> AlertKind.Permission
                                            MonitoredRequestKind.Question,
                                            MonitoredRequestKind.Form -> AlertKind.Question
                                            MonitoredRequestKind.CheckIn -> AlertKind.CheckIn
                                        },
                                    sessionId = request.sessionId,
                                    key = key,
                                    requestId = request.id,
                                    profileId = profileId,
                                    monitorToken = token,
                                    allowActions = false,
                                )
                            val granted =
                                Build.VERSION.SDK_INT < 33 ||
                                    application.checkSelfPermission(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                            val facts =
                                AlertFacts(
                                    background.live.value.active,
                                    false,
                                    inFront,
                                    granted,
                                    shared.requestsAllowed,
                                    false,
                                    wifi(),
                                    false,
                                )
                            val route =
                                AlertPolicy.route(alert, facts) as? AlertRoute.Post ?: return false
                            return shade.show(alert, route) == AlertShown.Shown
                        }

                        override suspend fun dismiss(key: String): Boolean =
                            attempt({ Failure.Unexpected() }) {
                                    shade.dismiss(key)
                                    true
                                }
                                .getOrNull() == true
                    },
                    clock,
                    main,
                    { instant ->
                        java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds())
                            .atZone(ZoneId.systemDefault())
                            .let { it.hour * 60 + it.minute }
                    },
                    sharedRules = shared,
                )
            this@AndroidProfileMonitoring.engine = engine
            prepared.complete(engine)
            engine.setRuntime(inFront, background.live.value.active)
            launch { engine.revision.collect { publish(engine) } }
            launch { background.live.collect { engine.setRuntime(inFront, it.active) } }
            launch {
                stores.profiles.profiles.collect {
                    updateIdentity()
                    publish(engine)
                    engine.refresh()
                }
            }
            launch {
                stores.prefs.changes.collect { keys ->
                    if (keys.any { it.startsWith("oc.automation.") })
                        source.profiles().forEach { consent.changed(it.id) }
                    if (
                        keys.any {
                            it.startsWith("oc.location.") ||
                                it.startsWith("oc.notify.") ||
                                it == "oc.profiles"
                        }
                    ) {
                        updateIdentity()
                        if (keys.any { it.startsWith("oc.notify.") })
                            engine.sharedRulesChanged(checkInChanged = false)
                        publish(engine)
                        engine.refresh()
                    }
                }
            }
            // Refresh secret identities before each bounded poll; never retain the credential
            // itself.
            while (isActive) {
                // No secret reads while no server is enrolled (monitoring is off by default).
                if (stores.profiles.profiles.value.any { engine.rulesFor(it.id.raw).enabled })
                    updateIdentity()
                engine.refresh()
                publish(engine)
                delay(30.seconds)
            }
        }
    }

    private suspend fun updateIdentity() {
        for (p in stores.profiles.profiles.value) {
            if (p.profile.backend == BackendKind.Codex || p.id.raw in stopped) continue
            identity[p.id.raw] =
                stores.profiles.secret(p.id).getOrNull()?.let { DartJson.sha256Hex(it) }.orEmpty()
        }
        identity.keys.retainAll(stores.profiles.profiles.value.map { it.id.raw }.toSet())
    }

    private fun publish(engine: ProfileMonitor) {
        changes.value =
            source.profiles().associate { p ->
                val rules = engine.rulesFor(p.id)
                val snapshot = engine.snapshotFor(p.id)
                ProfileId(p.id) to
                    ServerMonitorUi(
                        rules.enabled,
                        engine.supportsProfile(p),
                        rules.wifiOnly,
                        rules.checkInAfterMinutes,
                        when (snapshot.status) {
                            MonitorStatus.Current -> ServerMonitorStatus.Current
                            MonitorStatus.Unavailable -> ServerMonitorStatus.Unavailable
                            MonitorStatus.WifiRequired -> ServerMonitorStatus.WifiRequired
                            MonitorStatus.Paused -> ServerMonitorStatus.Paused
                            else -> ServerMonitorStatus.Waiting
                        },
                        snapshot.pendingCount,
                    )
            }
    }

    fun visible(value: Boolean) {
        inFront = value
        engine?.setRuntime(value, background.live.value.active)
    }

    override suspend fun enabled(profile: ProfileId, enabled: Boolean): Outcome<Unit> {
        start()
        return withContext(dispatchers.main) {
            val e =
                withTimeoutOrNull(8.seconds) { prepared.await() }
                    ?: return@withContext Failure.TimedOut().failed()
            // The idle loop skips secret reads while nothing is enrolled: refresh before enrolling.
            if (enabled) updateIdentity()
            e.setEnabled(profile.raw, enabled).also { publish(e) }
        }
    }

    override suspend fun wifiOnly(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        change(profile) { it.copy(wifiOnly = enabled) }

    override suspend fun checkIn(profile: ProfileId, minutes: Int?): Outcome<Unit> =
        change(profile) {
            it.copy(
                checkInAfterMinutes = minutes?.takeIf { it in MonitorNotifyRules.CHECK_IN_CHOICES }
            )
        }

    private suspend fun change(
        profile: ProfileId,
        edit: (MonitorNotifyRules) -> MonitorNotifyRules,
    ): Outcome<Unit> {
        start()
        return withContext(dispatchers.main) {
            val e =
                withTimeoutOrNull(8.seconds) { prepared.await() }
                    ?: return@withContext Failure.TimedOut().failed()
            e.setRules(profile.raw, edit(e.rulesFor(profile.raw))).also { publish(e) }
        }
    }

    /** The one engine, for Conversations' watched-servers panel (it never builds a second one). */
    internal suspend fun awaitEngine(): ProfileMonitor {
        start()
        return prepared.await()
    }

    /**
     * Opens a watched request from Conversations after the same private preflight a notification
     * tap gets; [current] re-checks the request is still listed before and after connecting.
     */
    internal suspend fun openWatched(
        route: MonitoredRoute,
        current: (ProfileMonitor) -> Boolean,
    ): Outcome<ConversationRef> {
        start()
        return withContext(dispatchers.main) {
            val stale = Failure.Refused(RefusalReason.StaleScope)
            val engine =
                withTimeoutOrNull(8.seconds) { prepared.await() }
                    ?: return@withContext Failure.TimedOut().failed()
            updateIdentity()
            val opener =
                MonitorRequestOpener(
                    source,
                    ::gateway,
                    { engine.rulesFor(it).enabled && consent.allows(it) },
                    { it !in stopped && stores.profiles.profile(ProfileId(it)) != null },
                    clock,
                )
            if (!current(engine) || !opener.prepare(route) || !current(engine))
                return@withContext stale.failed()
            val id = ProfileId(route.profileId)
            when (val connected = application.appGraph.previewRuntime.connect(id)) {
                is Outcome.Failed -> connected
                is Outcome.Ok ->
                    if (current(engine)) ConversationRef(id, SessionId(route.sessionId)).ok()
                    else stale.failed()
            }
        }
    }

    /** Consumes opaque taps before the normal profile/session extras can bypass preflight. */
    fun route(intent: Intent): Boolean {
        val token = intent.getStringExtra(FrozenExtras.EXTRA_MONITOR_TOKEN) ?: return false
        val id = intent.getStringExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID)
        intent.removeExtra(FrozenExtras.EXTRA_MONITOR_TOKEN)
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_PROFILE_ID)
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_SESSION_ID)
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_KIND)
        intent.removeExtra(FrozenExtras.EXTRA_CODING_ALERT_REQUEST_ID)
        if (id == null) return true
        start()
        main.launch {
            updateIdentity()
            val engine = withTimeoutOrNull(8.seconds) { prepared.await() } ?: return@launch
            val route = engine.routeForToken(id, token) ?: return@launch
            val opener =
                MonitorRequestOpener(
                    source,
                    ::gateway,
                    { engine.rulesFor(it).enabled },
                    { it !in stopped && stores.profiles.profile(ProfileId(it)) != null },
                    clock,
                )
            if (!opener.prepare(route)) return@launch
            if (engine.routeForToken(id, token) != route) return@launch
            if (application.appGraph.previewRuntime.connect(ProfileId(id)) !is Outcome.Ok)
                return@launch
            if (engine.routeForToken(id, token) != route) return@launch
            nav.openChat(ConversationRef(ProfileId(id), SessionId(route.sessionId)))
        }
        return true
    }

    private suspend fun wifi(): Boolean? =
        attempt({ Failure.Unexpected() }) {
                val manager = application.getSystemService(ConnectivityManager::class.java)
                manager
                    .getNetworkCapabilities(manager.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
            .getOrNull()

    private fun gateway(profile: MonitorProfile): MonitorGateway = PrivateMonitorGateway(profile)

    /**
     * Connects on the first bounded read and owns exactly that transport, never the selected
     * connection.
     */
    private inner class PrivateMonitorGateway(private val captured: MonitorProfile) :
        MonitorGateway {
        private var pair: GatewayPair? = null
        private var connectionScope: ConnectionCoroutineScope? = null
        private var closed = false
        private var location = ServerLocation.Default
        private val creating = Mutex()
        override val directory: String?
            get() = location.directory

        override val workspace: String?
            get() = location.workspace

        override val formsSupported: Boolean
            get() = pair?.live?.capabilities?.value?.forms == true

        override fun setLocation(directory: String?, workspace: String?) {
            location = ServerLocation(directory, workspace)
            pair?.live?.setLocation(location)
        }

        private suspend fun opened(): Outcome<GatewayPair> = creating.withLock {
            if (closed || captured.id in stopped)
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            pair?.let {
                return@withLock it.ok()
            }
            val loaded =
                stores.profiles.profile(ProfileId(captured.id))
                    ?: return@withLock Failure.Refused(RefusalReason.ProfileDeleted).failed()
            val current = source.profiles().firstOrNull { it.id == captured.id }
            if (current != captured || loaded.cleartextUnconfirmed)
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            val server =
                loaded.toServerProfile()
                    ?: return@withLock Failure.Refused(RefusalReason.InvalidAddress).failed()
            val factory =
                factories.firstOrNull { it.backend == server.backend }
                    ?: return@withLock Failure.Unavailable(FeatureId("monitor")).failed()
            val auth =
                credentials.singleOrNull()?.resolve(server) ?: ConnectionCredentials.None.ok()
            if (auth is Outcome.Failed) return@withLock auth
            val resolved = (auth as Outcome.Ok).value
            val identityNow = resolved.secret?.let { DartJson.sha256Hex(it) }.orEmpty()
            if (
                identityNow != captured.credential ||
                    source.profiles().firstOrNull { it.id == captured.id } != captured
            ) {
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            }
            val cs =
                ConnectionCoroutineScope(
                    supervisedContext(
                        appScope.coroutineContext[Job],
                        dispatchers.default,
                        "monitor-read",
                    )
                )
            connectionScope = cs
            var accepted = false
            try {
                when (
                    val result =
                        factory.connect(
                            GatewaySpec(
                                server,
                                resolved,
                                cs,
                                location,
                                reconnectAllowed = MutableStateFlow(false),
                            )
                        )
                ) {
                    is Outcome.Failed -> result
                    is Outcome.Ok -> {
                        if (
                            closed ||
                                source.profiles().firstOrNull { it.id == captured.id } != captured
                        ) {
                            result.value.live.close()
                            Failure.Refused(RefusalReason.StaleScope).failed()
                        } else {
                            pair = result.value
                            accepted = true
                            result
                        }
                    }
                }
            } finally {
                if (!accepted) cs.coroutineContext[Job]?.cancel()
            }
        }

        private suspend fun <T> read(block: suspend (GatewayPair) -> Outcome<T>): Outcome<T> =
            when (val p = opened()) {
                is Outcome.Failed -> p
                is Outcome.Ok -> block(p.value)
            }

        override suspend fun sessionPage(limit: Int): Outcome<MonitorSessionPage> = read {
            it.live.sessionPage(limit = limit).map { p ->
                MonitorSessionPage(p.items.map(::sessionRow), p.nextCursor != null)
            }
        }

        override suspend fun pendingPermissions(): Outcome<List<MonitorPending>> = read {
            it.live.pendingPermissions().map { rows ->
                rows.map { r -> MonitorPending(r.id.raw, r.sessionId.raw) }
            }
        }

        // The neutral gateway owns version negotiation, so a second legacy read is unnecessary.
        override suspend fun pendingPermissionsV2(): Outcome<List<MonitorPending>> =
            Failure.Unavailable(FeatureId("legacyPermissions")).failed()

        override suspend fun listQuestions(): Outcome<List<MonitorPending>> = read {
            it.live.pendingQuestions().map { rows ->
                rows.map { r -> MonitorPending(r.id.raw, r.sessionId.raw) }
            }
        }

        override suspend fun pendingQuestionsV2(): Outcome<List<MonitorPending>> = read {
            it.operations.questions().map { rows ->
                rows.map { r -> MonitorPending(r.id.raw, r.sessionId.raw) }
            }
        }

        override suspend fun pendingForms(): Outcome<List<MonitorPending>> = read {
            it.live.pendingForms().map { rows ->
                rows.map { r -> MonitorPending(r.id.raw, r.sessionId.raw) }
            }
        }

        override suspend fun sessionStatuses(): Outcome<Map<String, String>> = read {
            it.live.sessionStatuses().map { rows ->
                rows.map { (id, status) -> id.raw to status.raw }.toMap()
            }
        }

        override suspend fun session(id: String): Outcome<MonitorSession> = read {
            it.live.session(SessionId(id)).map(::sessionRow)
        }

        override suspend fun messagePage(
            sessionId: String,
            limit: Int,
        ): Outcome<MonitorMessagePage> = read {
            it.live.messagePage(SessionId(sessionId), limit = limit).map { p ->
                MonitorMessagePage(
                    p.items.map { m ->
                        MonitorMessage(
                            m.info.id,
                            m.info.role.name.lowercase(),
                            m.info.finish,
                            m.info.errorKind != null,
                        )
                    },
                    p.nextCursor != null,
                )
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            connectionScope?.coroutineContext?.get(Job)?.cancel()
            pair?.let { p -> main.launch { p.live.close() } }
        }

        private fun sessionRow(session: Session): MonitorSession =
            MonitorSession(
                session.id.raw,
                session.title,
                session.directory,
                session.workspaceId?.raw,
            )
    }

    companion object {
        fun registerKeys(scope: ProfileScope) {
            for (prefix in
                listOf(
                    "oc.notifyRules.",
                    "oc.monitorRoutes.",
                    "oc.monitorBusy.",
                    "oc.automation.",
                )) {
                scope.register(
                    KeyShape(
                        prefix,
                        KeyScope.Profile,
                        PrefType.Str,
                        ":engine:attention",
                        "saved monitoring consent, rules and opaque route data",
                    )
                )
            }
            scope.register(
                KeyShape(
                    "oc.monitorAlerts.",
                    KeyScope.Profile,
                    PrefType.StrList,
                    ":engine:attention",
                    "posted alert retirement keys",
                )
            )
        }
    }

    fun stop(profile: ProfileId) {
        stopped += profile.raw
        main.launch { engine?.removeProfile(profile.raw) }
    }

    suspend fun drain(profile: ProfileId): Outcome<Unit> =
        withContext(dispatchers.main) {
            engine?.let {
                it.removeProfile(profile.raw)
                it.drain(profile.raw)
            }
            Unit.ok()
        }

    fun reopen(profile: ProfileId) {
        stopped -= profile.raw
        main.launch { engine?.cancelDeletion(profile.raw) }
    }
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class MonitorDeletion(private val monitoring: AndroidProfileMonitoring) : ProfileCloser {
    override val owner: String = ":engine:attention (monitor)"

    override fun stopAdmission(profile: ProfileId) = monitoring.stop(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = monitoring.drain(profile)

    override fun reopen(profile: ProfileId) = monitoring.reopen(profile)
}
