package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
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
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.requests.consent.*
import io.github.eslamasabry.oc.feature.servers.presentation.ServerMonitorStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServerMonitorUi
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationProfile
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

@ContributesTo(AppScope::class)
interface MonitorAccessors {
    val profileMonitoring: NativeProfileMonitoring
}

/** One app-owned, consent-gated reader. It never opens or switches an active connection. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class NativeProfileMonitoring(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val factories: Set<GatewayFactory>,
    private val credentials: Set<ConnectionCredentialSource>,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val redactor: io.github.eslamasabry.oc.core.security.Redactor,
    private val background: BackgroundHub,
    private val activities: ResumedActivities,
    private val preferences: AppPreferences,
    appScope: AppCoroutineScope,
) : ProfileCloser, MonitorProfiles {
    private val main = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val cleanup = CoroutineScope(appScope.coroutineContext + dispatchers.io)
    private val ready = CompletableDeferred<Outcome<Unit>>()
    private val blocked = ConcurrentHashMap.newKeySet<String>()
    private val cached = linkedMapOf<String, MonitorProfile>()
    private val salt = UUID.randomUUID().toString()
    private var monitor: ProfileMonitor? = null
    private var started = false
    private val mutableSnapshots =
        MutableStateFlow<Map<String, ProfileAttentionSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, ProfileAttentionSnapshot>> = mutableSnapshots.asStateFlow()
    private val mutableLoaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = mutableLoaded.asStateFlow()
    var startupFailure: Failure? = null
        private set

    private val mutableSettings =
        MutableStateFlow<ImmutableList<NotificationProfile>>(persistentListOf())
    val profileSettings: StateFlow<ImmutableList<NotificationProfile>> =
        mutableSettings.asStateFlow()
    private val mutableServerRows = MutableStateFlow<Map<ProfileId, ServerMonitorUi>>(emptyMap())
    /** The Servers-list rows (NativeServerMonitoring): opt-in, support and last read status. */
    val serverRows: StateFlow<Map<ProfileId, ServerMonitorUi>> = mutableServerRows.asStateFlow()
    override val owner = "attention-monitor"

    override fun profiles(): List<MonitorProfile> = cached.values.filter { it.id !in blocked }

    override fun location(profileId: String): MonitorLocation? {
        val current = registry.live.value[ProfileId(profileId)]?.session?.server?.location?.value
        return MonitorLocation(current?.directory, current?.workspace)
    }

    private val consent =
        object : MonitorConsent {
            override fun hasRecord(profileId: String): Boolean =
                stores.prefs.get(policyKey(profileId)) != null

            override fun allows(profileId: String): Boolean =
                stores.prefs
                    .string(policyKey(profileId))
                    ?.let(AutomationPolicy::fromJson)
                    ?.allows(AutomationBehavior.MonitorOtherServers) == true

            override suspend fun set(profileId: String, allowed: Boolean): Boolean {
                if (profileId in blocked || stores.profiles.profile(ProfileId(profileId)) == null)
                    return false
                val key = policyKey(profileId)
                val old = stores.prefs.get(key)
                val raw = (old as? PrefValue.Str)?.value
                val before =
                    if (raw == null && old == null) AutomationPolicy().toJson()
                    else
                        try {
                            raw?.let { Json.parseToJsonElement(it) as? JsonObject }
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                if (before == null || (before["version"] as? JsonPrimitive)?.intOrNull != 1)
                    return false
                val behaviors = before["behaviors"] as? JsonObject ?: return false
                val next =
                    JsonObject(
                        before.toMutableMap().apply {
                            put(
                                "behaviors",
                                JsonObject(
                                    behaviors.toMutableMap().apply {
                                        put("monitorOtherServers", JsonPrimitive(allowed))
                                    }
                                ),
                            )
                        }
                    )
                val saved =
                    stores.prefs.compareAndCommit(
                        stores.lanes.forProfile(ProfileId(profileId)),
                        mapOf(key to old),
                    ) {
                        putString(key, next.toString())
                    }
                return saved is Outcome.Ok && saved.value
            }

            override fun observe(profileId: String, listener: () -> Unit): () -> Unit {
                val job = main.launch {
                    stores.prefs.changes.collect { keys ->
                        if (policyKey(profileId) in keys) listener()
                    }
                }
                return { job.cancel() }
            }
        }

    private val prefs =
        object : MonitorPrefs {
            override fun getString(key: String): String? = stores.prefs.string(key)

            override fun getStringList(key: String): List<String>? = stores.prefs.stringList(key)

            override fun contains(key: String): Boolean = stores.prefs.get(key) != null

            private suspend fun write(key: String, edit: PrefEdit.() -> Unit): Boolean {
                val id =
                    cached.keys.firstOrNull { key in ProfileMonitor.profileScopedKeys(it) }
                        ?: return false
                if (id in blocked || stores.profiles.profile(ProfileId(id)) == null) return false
                return stores.prefs.commit(stores.lanes.forProfile(ProfileId(id)), edit) is
                    Outcome.Ok
            }

            override suspend fun putString(key: String, value: String): Boolean =
                write(key) { putString(key, value) }

            override suspend fun putStringList(key: String, value: List<String>): Boolean =
                write(key) { putStringList(key, value) }

            override suspend fun remove(key: String): Boolean = write(key) { remove(key) }
        }

    private val sharedRules =
        object : MonitorSharedRules {
            override val requestsAllowed: Boolean
                get() = stores.prefs.bool("oc.notify.requests") != false

            override fun applyTo(own: MonitorNotifyRules): MonitorNotifyRules =
                own.copy(
                    wifiOnly = stores.prefs.bool("oc.notify.wifiOnly") ?: own.wifiOnly,
                    quietStart =
                        stores.prefs
                            .long("oc.notify.quietStart")
                            ?.takeIf { it in 0..1439 }
                            ?.toInt(),
                    quietEnd =
                        stores.prefs.long("oc.notify.quietEnd")?.takeIf { it in 0..1439 }?.toInt(),
                    checkInAfterMinutes =
                        stores.prefs
                            .long("oc.notify.checkInAfterMinutes")
                            ?.takeIf { it in 1..1440 }
                            ?.toInt(),
                )
        }

    /**
     * Called from OcApplication after the graph exists; no default grants permission to read a
     * server.
     */
    fun start() {
        if (started) return
        started = true
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    main.launch { runtimeChanged() }
                }

                override fun onActivityPaused(activity: Activity) {
                    main.launch { runtimeChanged() }
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
        main.launch {
            val warm = stores.warm()
            if (warm is Outcome.Failed) {
                startupFailure = warm.failure
                mutableLoaded.value = true
                ready.complete(warm)
                return@launch
            }
            rebuildProfiles()
            val value =
                ProfileMonitor(
                    prefs,
                    this@NativeProfileMonitoring,
                    consent,
                    ::gateway,
                    isReadable = { id -> cached[id]?.usable == true && id !in blocked },
                    networkWifi = ::wifi,
                    alerts = alerts(),
                    clock = clock,
                    scope = main,
                    minuteOfDay = { at ->
                        java.time.Instant.ofEpochMilli(at.toEpochMilliseconds())
                            .atZone(ZoneId.systemDefault())
                            .let { it.hour * 60 + it.minute }
                    },
                    readAttention = MonitorAttentionReader(clock, cleanup, redactor),
                    sharedRules = sharedRules,
                )
            monitor = value
            ready.complete(Outcome.Ok(Unit))
            mutableLoaded.value = true
            runtimeChanged()
            launch { value.revision.collect { publish() } }
            launch {
                combine(stores.profiles.profiles, registry.live) { _, _ -> Unit }
                    .collect {
                        rebuildProfiles()
                        publish()
                        value.refresh()
                    }
            }
            launch {
                stores.prefs.changes.collect { keys ->
                    if (
                        keys.any {
                            it.startsWith("oc.notify.") ||
                                it.startsWith("oc.notifyRules.") ||
                                it.startsWith("oc.automation.")
                        }
                    ) {
                        value.sharedRulesChanged("oc.notify.checkInAfterMinutes" in keys)
                        publish()
                    }
                }
            }
            launch { background.live.collect { runtimeChanged() } }
            value.runLoop()
        }
    }

    private suspend fun rebuildProfiles() {
        val next = linkedMapOf<String, MonitorProfile>()
        for (loaded in stores.profiles.profiles.value) {
            if (loaded.id.raw in blocked) continue
            val p = loaded.profile
            val secret = stores.profiles.secret(loaded.id)
            val usable =
                secret is Outcome.Ok &&
                    !loaded.cleartextUnconfirmed &&
                    !loaded.reentry.password &&
                    !loaded.reentry.agentToken &&
                    !p.requiresOpenCode2Migration &&
                    !p.openCode2HistoryPending &&
                    loaded.toServerProfile() != null
            next[p.id.raw] =
                MonitorProfile(
                    p.id.raw,
                    p.baseUrl,
                    p.username,
                    "v2",
                    when (p.backend) {
                        BackendKind.OpenCode2 -> MonitorBackend.OpenCode
                        BackendKind.Paseo -> MonitorBackend.Paseo
                        BackendKind.Codex -> MonitorBackend.Codex
                    },
                    credential =
                        if (secret is Outcome.Ok) DartJson.sha256Hex(salt + secret.value.orEmpty())
                        else "",
                    usable = usable,
                    boundDirectory = p.codexDirectory.takeIf { it.isNotBlank() },
                )
        }
        cached.clear()
        cached.putAll(next)
    }

    private fun runtimeChanged() {
        monitor?.setRuntime(activities.current != null, background.live.value.active)
        publish()
    }

    private fun publish() {
        val value = monitor ?: return
        mutableSnapshots.value = value.snapshots()
        mutableSettings.value =
            stores.profiles.profiles.value
                .filter { it.id.raw !in blocked }
                .map { loaded ->
                    val p = cached[loaded.id.raw]
                    val rule = value.rulesFor(loaded.id.raw)
                    NotificationProfile(
                        loaded.id,
                        loaded.profile.name,
                        rule.enabled && consent.allows(loaded.id.raw),
                        rule.notifications,
                        p != null && value.supportsProfile(p),
                    )
                }
                .toImmutableList()
        mutableServerRows.value =
            cached.values
                .filter { it.id !in blocked }
                .associate { p ->
                    val snapshot = value.snapshotFor(p.id)
                    ProfileId(p.id) to
                        ServerMonitorUi(
                            enabled = value.rulesFor(p.id).enabled && consent.allows(p.id),
                            available = value.supportsProfile(p),
                            status =
                                when (snapshot.status) {
                                    MonitorStatus.Current -> ServerMonitorStatus.Current
                                    MonitorStatus.Unavailable -> ServerMonitorStatus.Unavailable
                                    MonitorStatus.WifiRequired -> ServerMonitorStatus.WifiRequired
                                    MonitorStatus.Paused -> ServerMonitorStatus.Paused
                                    MonitorStatus.Disabled,
                                    MonitorStatus.Waiting,
                                    MonitorStatus.Checking -> ServerMonitorStatus.Waiting
                                },
                            pending = snapshot.pendingCount,
                        )
                }
    }

    suspend fun setEnabled(profile: ProfileId, on: Boolean): Outcome<Unit> =
        withContext(dispatchers.main) {
            start()
            when (val result = ready.await()) {
                is Outcome.Failed -> return@withContext result
                is Outcome.Ok -> Unit
            }
            val result = monitor!!.setEnabled(profile.raw, on)
            publish()
            if (result is Outcome.Ok && on && !consent.allows(profile.raw))
                Failure.StorageRefused().failed()
            else result
        }

    suspend fun setNotifications(profile: ProfileId, on: Boolean): Outcome<Unit> =
        withContext(dispatchers.main) {
            start()
            when (val result = ready.await()) {
                is Outcome.Failed -> return@withContext result
                is Outcome.Ok -> Unit
            }
            val value = monitor!!
            value.setRules(profile.raw, value.rulesFor(profile.raw).copy(notifications = on)).also {
                publish()
            }
        }

    suspend fun sharedRulesChanged(checkInChanged: Boolean) =
        withContext(dispatchers.main) {
            start()
            if (ready.await() is Outcome.Ok) monitor!!.sharedRulesChanged(checkInChanged)
            publish()
        }

    suspend fun refresh(): Outcome<Unit> =
        withContext(dispatchers.main) {
            start()
            when (val result = ready.await()) {
                is Outcome.Failed -> return@withContext result
                is Outcome.Ok -> Unit
            }
            rebuildProfiles()
            monitor!!.refresh(force = true)
            publish()
            Unit.ok()
        }

    suspend fun routeForToken(profile: String, token: String): MonitoredRoute? =
        withContext(dispatchers.main) {
            start()
            if (ready.await() is Outcome.Failed) return@withContext null
            rebuildProfiles()
            monitor!!.routeForToken(profile, token)
        }

    suspend fun prepare(route: MonitoredRoute): Boolean =
        withContext(dispatchers.main) {
            start()
            if (ready.await() is Outcome.Failed) return@withContext false
            rebuildProfiles()
            MonitorRequestOpener(
                    this@NativeProfileMonitoring,
                    ::gateway,
                    { id -> monitor!!.rulesFor(id).enabled && consent.allows(id) },
                    { id -> cached[id]?.usable == true && id !in blocked },
                    clock,
                )
                .prepare(route)
        }

    fun route(profile: String, request: MonitoredRequest, at: Instant): MonitoredRoute? =
        cached[profile]?.let { p ->
            MonitoredRoute(
                profile,
                request.id,
                request.sessionId,
                request.kind,
                at,
                p.baseUrl,
                ProfileMonitor.routeSourceIdentity(p),
                request.directory,
                request.workspace,
            )
        }

    override fun stopAdmission(profile: ProfileId) {
        blocked += profile.raw
        main.launch {
            monitor?.removeProfile(profile.raw)
            publish()
        }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> =
        withContext(dispatchers.main) {
            monitor?.drain(profile.raw)
            Unit.ok()
        }

    override fun reopen(profile: ProfileId) {
        blocked.remove(profile.raw)
        main.launch {
            monitor?.cancelDeletion(profile.raw)
            rebuildProfiles()
            publish()
        }
    }

    private fun gateway(profile: MonitorProfile): MonitorGateway =
        NativeMonitorGateway(
            profile,
            this,
            stores,
            factories,
            credentials,
            dispatchers,
            cleanup,
            redactor,
            salt,
        )

    private fun wifi(): Boolean? {
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager
            .getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun alerts(): MonitorAlerts {
        val manager = app.getSystemService(NotificationManager::class.java)
        val words = NotifyWords(app) { preferences.localeTag.value }
        val shade = AndroidAlertShade(app, words, ChannelRegistry(manager, words))
        return object : MonitorAlerts {
            override suspend fun alert(
                profileId: String,
                request: MonitoredRequest,
                key: String,
                token: String,
            ): Boolean {
                val rule = monitor?.rulesFor(profileId) ?: return false
                if (
                    profileId in blocked ||
                        !consent.allows(profileId) ||
                        !rule.enabled ||
                        !rule.notifications
                )
                    return false
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
                val minute =
                    java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds())
                        .atZone(ZoneId.systemDefault())
                        .let { it.hour * 60 + it.minute }
                val route =
                    AlertPolicy.route(
                        alert,
                        AlertFacts(
                            liveModeActive = background.live.value.active,
                            replyWatchActive = false,
                            appVisible = activities.current != null,
                            permissionGranted = manager.areNotificationsEnabled(),
                            serverAlertsOn = rule.notifications,
                            quietHours = rule.quietAt(minute),
                            onWifi = wifi(),
                            wifiOnly = rule.wifiOnly,
                        ),
                    )
                return route is AlertRoute.Post && shade.show(alert, route) == AlertShown.Shown
            }

            override suspend fun dismiss(key: String): Boolean {
                shade.dismiss(key)
                return true
            }
        }
    }

    companion object {
        fun register(scope: ProfileScope) {
            listOf(
                    "oc.notifyRules.",
                    "oc.monitorAlerts.",
                    "oc.monitorRoutes.",
                    "oc.monitorBusy.",
                    "oc.automation.",
                )
                .forEach {
                    scope.register(
                        KeyShape(
                            it,
                            KeyScope.Profile,
                            if (it == "oc.monitorAlerts.") PrefType.StrList else PrefType.Str,
                            "attention-monitor",
                            "Saved monitoring consent, rules and opaque routing",
                        )
                    )
                }
        }

        private fun policyKey(id: String) = "oc.automation.$id"
    }
}

/** One disposable read client with its own scope and location; never borrowed from a live graph. */
private class NativeMonitorGateway(
    private val target: MonitorProfile,
    private val source: NativeProfileMonitoring,
    private val stores: PreviewStores,
    private val factories: Set<GatewayFactory>,
    private val credentials: Set<ConnectionCredentialSource>,
    private val dispatchers: AppDispatchers,
    private val cleanup: CoroutineScope,
    private val redactor: io.github.eslamasabry.oc.core.security.Redactor,
    private val salt: String,
) : MonitorGateway {
    private val job = SupervisorJob(cleanup.coroutineContext[Job])
    private val scope = ConnectionCoroutineScope(cleanup.coroutineContext + job)
    private var pair: GatewayPair? = null
    private var closed = false
    private var here = ServerLocation.Default
    private var permissions: Outcome<List<MonitorPending>>? = null
    private var questions: Outcome<List<MonitorPending>>? = null
    override val formsSupported: Boolean
        get() = pair?.live?.capabilities?.value?.forms == true

    override val directory: String?
        get() = here.directory

    override val workspace: String?
        get() = here.workspace

    override fun setLocation(directory: String?, workspace: String?) {
        here = ServerLocation(directory, workspace)
        pair?.live?.setLocation(here)
    }

    private fun current(): Boolean = !closed && source.profiles().any { it == target }

    private suspend fun live(): Outcome<ServerGateway> {
        if (!current()) return Failure.Refused(RefusalReason.StaleScope).failed()
        pair?.let {
            return it.live.ok()
        }
        val saved =
            stores.profiles.profile(ProfileId(target.id))
                ?: return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val profile =
            saved.toServerProfile() ?: return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val resolver =
            credentials.singleOrNull()
                ?: return Failure.Unavailable(FeatureId("connectionCredentials")).failed()
        val credential =
            when (val read = resolver.resolve(profile)) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        if (
            DartJson.sha256Hex(salt + credential.secret.orEmpty()) != target.credential ||
                !current()
        )
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val factory =
            factories.firstOrNull { it.backend == profile.backend }
                ?: return Failure.Unavailable(FeatureId("profileMonitor")).failed()
        val pending = scope.async {
            factory.connect(GatewaySpec(profile, credential, scope, here, observeEvents = false))
        }
        var received = false
        val opened =
            try {
                pending.await().also { received = true }
            } catch (cancelled: CancellationException) {
                if (closed || !job.isActive)
                    return Failure.Refused(RefusalReason.StaleScope).failed()
                throw cancelled
            } finally {
                if (!received) {
                    pending.cancel()
                    cleanup.launch {
                        try {
                            val late = pending.await()
                            if (late is Outcome.Ok) late.value.live.close()
                        } catch (_: CancellationException) {
                            // The client construction was cancelled and its factory owns cleanup.
                        }
                    }
                }
            }
        if (opened is Outcome.Failed) return opened
        val got = (opened as Outcome.Ok).value
        if (!current() || stores.profiles.profile(profile.id) != saved) {
            cleanup.launch { got.live.close() }
            return Failure.Refused(RefusalReason.StaleScope).failed()
        }
        pair = got
        return got.live.ok()
    }

    private suspend fun <T> read(block: suspend (ServerGateway) -> Outcome<T>): Outcome<T> {
        val gateway =
            when (val opened = live()) {
                is Outcome.Failed -> return opened
                is Outcome.Ok -> opened.value
            }
        val answer =
            try {
                block(gateway)
            } catch (cancelled: CancellationException) {
                if (closed || !job.isActive)
                    return Failure.Refused(RefusalReason.StaleScope).failed()
                throw cancelled
            }
        return if (current()) answer else Failure.Refused(RefusalReason.StaleScope).failed()
    }

    override suspend fun sessionPage(limit: Int): Outcome<MonitorSessionPage> = read {
        it.sessionPage(limit = limit).map { page ->
            MonitorSessionPage(page.items.map(::session), page.nextCursor != null)
        }
    }

    override suspend fun pendingPermissions(): Outcome<List<MonitorPending>> =
        permissions
            ?: read { gateway ->
                gateway.pendingPermissions().map { values ->
                    values.map { MonitorPending(it.id.raw, it.sessionId.raw) }
                }
            }
                .also { permissions = it }

    override suspend fun pendingPermissionsV2(): Outcome<List<MonitorPending>> =
        pendingPermissions()

    override suspend fun listQuestions(): Outcome<List<MonitorPending>> =
        questions
            ?: read { gateway ->
                gateway.pendingQuestions().map { values ->
                    values.map { MonitorPending(it.id.raw, it.sessionId.raw) }
                }
            }
                .also { questions = it }

    override suspend fun pendingQuestionsV2(): Outcome<List<MonitorPending>> = listQuestions()

    override suspend fun pendingForms(): Outcome<List<MonitorPending>> = read { gateway ->
        gateway.pendingForms().map { values ->
            values.map { MonitorPending(it.id.raw, it.sessionId.raw) }
        }
    }

    override suspend fun sessionStatuses(): Outcome<Map<String, String>> = read { gateway ->
        gateway.sessionStatuses().map {
            it.entries.associate { row -> row.key.raw to row.value.raw }
        }
    }

    override suspend fun session(id: String): Outcome<MonitorSession> = read { gateway ->
        gateway.session(SessionId(id)).map(::session)
    }

    override suspend fun messagePage(sessionId: String, limit: Int): Outcome<MonitorMessagePage> =
        read { gateway ->
            gateway.messagePage(SessionId(sessionId), limit = limit).map { page ->
                MonitorMessagePage(
                    page.items.map { row ->
                        MonitorMessage(
                            row.info.id,
                            if (
                                row.info.role ==
                                    io.github.eslamasabry.oc.core.model.transcript.Role.Assistant
                            )
                                "assistant"
                            else "user",
                            row.info.finish,
                            row.info.errorKind != null || row.info.errorText != null,
                        )
                    },
                    page.nextCursor != null,
                )
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        job.cancel()
        val gateway = pair?.live
        pair = null
        if (gateway != null) cleanup.launch { gateway.close() }
    }

    private fun session(value: Session): MonitorSession =
        MonitorSession(value.id.raw, value.title, value.directory, value.workspaceId?.raw)
}
