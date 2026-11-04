package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.os.Bundle
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.notifications.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.monitor.*
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.requests.consent.*
import io.github.eslamasabry.oc.feature.settings.monitor.*
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-lifetime monitor, admitted only by saved per-server consent and the existing foreground/live
 * runtime.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MonitoringHost>())
@Inject
class MonitorRuntime(
    private val application: Application,
    private val stores: PreviewStores,
    private val policies: AppAutomationPolicies,
    private val registry: ConnectionRegistry,
    private val factories: Set<GatewayFactory>,
    private val credentials: Set<ConnectionCredentialSource>,
    private val network: NetworkStatus,
    private val background: BackgroundHub,
    private val preferences: AppPreferences,
    private val redactor: Redactor,
    private val clock: Clock,
    appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : MonitoringHost {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val ready = CompletableDeferred<ProfileMonitor?>()
    private val mutable = MutableStateFlow(MonitoringUi())
    override val state: StateFlow<MonitoringUi> = mutable.asStateFlow()
    private val commands = Mutex()
    private var monitor: ProfileMonitor? = null
    private var started = false
    private var shown = 0
    private val policyListeners = mutableMapOf<String, MutableSet<() -> Unit>>()
    private var knownProfiles = emptySet<String>()
    private val blockedProfiles = ConcurrentHashMap.newKeySet<String>()
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val shade =
        AndroidAlertShade(
            application,
            words,
            ChannelRegistry(application.getSystemService(NotificationManager::class.java), words),
        )

    fun start() {
        if (started) return
        started = true
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    shown++
                    runtimeChanged()
                }

                override fun onActivityStopped(activity: Activity) {
                    shown = (shown - 1).coerceAtLeast(0)
                    runtimeChanged()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
                    Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
        scope.launch {
            if (stores.warm() is Outcome.Failed) {
                mutable.value = MonitoringUi(saveFailed = true)
                ready.complete(null)
                return@launch
            }
            val profiles =
                object : MonitorProfiles {
                    override fun profiles(): List<MonitorProfile> =
                        stores.profiles.profiles.value.map { loaded ->
                            val p = loaded.profile
                            MonitorProfile(
                                id = p.id.raw,
                                baseUrl = p.baseUrl,
                                username = p.username,
                                backend =
                                    when (p.backend) {
                                        BackendKind.OpenCode2 -> MonitorBackend.OpenCode
                                        BackendKind.Paseo -> MonitorBackend.Paseo
                                        BackendKind.Codex -> MonitorBackend.Codex
                                    },
                                usable =
                                    !loaded.cleartextUnconfirmed &&
                                        !loaded.reentry.password &&
                                        !loaded.reentry.agentToken &&
                                        loaded.toServerProfile() != null,
                                boundDirectory = p.codexDirectory.takeIf { it.isNotBlank() },
                                hasPhoneEngine =
                                    phoneMonitorConfig(p.orchestration?.encoded) != null,
                            )
                        }

                    override fun location(profileId: String): MonitorLocation? {
                        val live =
                            registry.live.value[ProfileId(profileId)]
                                ?.session
                                ?.server
                                ?.location
                                ?.value
                        val bound =
                            stores.profiles
                                .profile(ProfileId(profileId))
                                ?.profile
                                ?.codexDirectory
                                ?.takeIf { it.isNotBlank() }
                        return MonitorLocation(live?.directory ?: bound, live?.workspace)
                    }
                }
            val prefs =
                object : MonitorPrefs {
                    override fun getString(key: String): String? = stores.prefs.string(key)

                    override fun getStringList(key: String): List<String>? =
                        stores.prefs.stringList(key)

                    override fun contains(key: String): Boolean = stores.prefs.get(key) != null

                    override suspend fun putString(key: String, value: String): Boolean =
                        write(key) { putString(key, value) }

                    override suspend fun putStringList(key: String, value: List<String>): Boolean =
                        write(key) { putStringList(key, value) }

                    override suspend fun remove(key: String): Boolean = write(key) { remove(key) }
                }
            val consent =
                object : MonitorConsent {
                    override fun allows(profileId: String): Boolean =
                        policies.allows(
                            ProfileId(profileId),
                            AutomationBehavior.MonitorOtherServers,
                        )

                    override fun hasRecord(profileId: String): Boolean =
                        policies.hasRecord(ProfileId(profileId))

                    override suspend fun set(profileId: String, allowed: Boolean): Boolean {
                        if (
                            policies.setBehavior(
                                ProfileId(profileId),
                                AutomationBehavior.MonitorOtherServers,
                                allowed,
                            ) !is Outcome.Ok
                        )
                            return false
                        policyListeners[profileId]?.toList()?.forEach { it() }
                        return true
                    }

                    override fun observe(profileId: String, listener: () -> Unit): () -> Unit {
                        policyListeners.getOrPut(profileId) { linkedSetOf() }.add(listener)
                        return {
                            policyListeners[profileId]?.remove(listener)
                            Unit
                        }
                    }
                }
            val shared =
                object : MonitorSharedRules {
                    override val requestsAllowed: Boolean
                        get() = stores.prefs.bool("oc.notify.requests") != false

                    override fun applyTo(own: MonitorNotifyRules): MonitorNotifyRules =
                        own.copy(
                            quietStart = stores.prefs.long("oc.notify.quietStart")?.toInt(),
                            quietEnd = stores.prefs.long("oc.notify.quietEnd")?.toInt(),
                            wifiOnly = stores.prefs.bool("oc.notify.wifiOnly") == true,
                        )
                }
            val built =
                ProfileMonitor(
                    prefs,
                    profiles,
                    consent,
                    createGateway = { profile ->
                        ReadOnlyMonitorGateway(
                            profile.id,
                            stores,
                            factories,
                            credentials,
                            scope,
                            dispatchers,
                        )
                    },
                    isReadable = { id ->
                        id !in blockedProfiles &&
                            stores.profiles.profile(ProfileId(id))?.let {
                                !it.cleartextUnconfirmed &&
                                    !it.reentry.password &&
                                    !it.reentry.agentToken
                            } == true
                    },
                    networkWifi = { network.state.value.onWifi },
                    alerts =
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
                                                MonitoredRequestKind.Permission ->
                                                    AlertKind.Permission
                                                MonitoredRequestKind.Question,
                                                MonitoredRequestKind.Form -> AlertKind.Question
                                                MonitoredRequestKind.CheckIn -> AlertKind.CheckIn
                                            },
                                        sessionId = request.sessionId,
                                        key = key,
                                        profileId = profileId,
                                        requestId = request.id,
                                        monitorToken = token,
                                        allowActions = false,
                                    )
                                return shade.show(
                                    alert,
                                    AlertRoute.Post(
                                        NotifyChannel.CodingAction,
                                        AlertPolicy.groupOf(alert.kind),
                                    ),
                                ) == AlertShown.Shown
                            }

                            override suspend fun dismiss(key: String): Boolean {
                                shade.dismiss(key)
                                return true
                            }
                        },
                    clock = clock,
                    scope = scope,
                    minuteOfDay = { at ->
                        java.time.Instant.ofEpochMilli(at.toEpochMilliseconds())
                            .atZone(ZoneId.systemDefault())
                            .let { it.hour * 60 + it.minute }
                    },
                    readAttention =
                        MonitorAttentionReader(
                            clock,
                            scope,
                            redactor,
                            phoneEngine = { profile ->
                                AndroidMonitorPhoneEngine(profile.id, stores, dispatchers, redactor)
                            },
                            resolvePhoneCredentials = true,
                        ),
                    sharedRules = shared,
                )
            monitor = built
            runtimeChanged()
            ready.complete(built)
            launch { built.revision.collect { publish() } }
            launch { background.live.collect { runtimeChanged() } }
            launch {
                stores.profiles.profiles.collect { list ->
                    val ids = list.map { it.id.raw }.toSet()
                    for (removed in knownProfiles - ids) built.removeProfile(removed)
                    knownProfiles = ids
                    publish()
                }
            }
            launch {
                stores.prefs.changes.collect { keys ->
                    keys
                        .filter { it.startsWith("oc.automation.") }
                        .forEach { key ->
                            policyListeners[key.removePrefix("oc.automation.")]?.toList()?.forEach {
                                it()
                            }
                        }
                    if (keys.any { it.startsWith("oc.notify.") })
                        built.sharedRulesChanged(checkInChanged = false)
                    publish()
                }
            }
            launch { built.runLoop() }
        }
    }

    private suspend fun write(key: String, edit: PrefEdit.() -> Unit): Boolean {
        val id =
            stores.profiles.profiles.value.firstOrNull { key.endsWith(".${it.id.raw}") }?.id
                ?: return false
        return stores.prefs.commit(stores.lanes.forProfile(id), edit) is Outcome.Ok
    }

    private fun runtimeChanged() {
        monitor?.setRuntime(
            shown > 0,
            background.live.value.active && background.live.value.enabled,
        )
    }

    private fun publish() {
        val m = monitor ?: return
        mutable.value =
            mutable.value.copy(
                servers =
                    stores.profiles.profiles.value
                        .filter { it.profile.backend != BackendKind.Codex }
                        .map { p ->
                            val rules = m.rulesFor(p.id.raw)
                            val snapshot = m.snapshotFor(p.id.raw)
                            MonitoringServer(
                                p.id.raw,
                                p.profile.name,
                                rules.enabled &&
                                    policies.allows(p.id, AutomationBehavior.MonitorOtherServers),
                                rules.checkInAfterMinutes,
                                MonitoringState.valueOf(
                                    if (snapshot.status == MonitorStatus.Disabled) "Off"
                                    else snapshot.status.name
                                ),
                                snapshot.pendingCount,
                            )
                        }
                        .toImmutableList()
            )
    }

    override suspend fun setEnabled(id: String, on: Boolean): Outcome<Unit> = command {
        it.setEnabled(id, on)
    }

    override suspend fun setCheckIn(id: String, minutes: Int?): Outcome<Unit> = command {
        if (minutes != null && minutes !in MonitorNotifyRules.CHECK_IN_CHOICES)
            return@command Failure.StorageRefused().failed()
        it.setRules(id, it.rulesFor(id).copy(checkInAfterMinutes = minutes))
    }

    private suspend fun command(action: suspend (ProfileMonitor) -> Outcome<Unit>): Outcome<Unit> =
        withContext(dispatchers.main) {
            start()
            commands.withLock {
                mutable.value = mutable.value.copy(busy = true, saveFailed = false)
                val active = ready.await()
                val result =
                    if (active == null) Failure.StorageRefused().failed() else action(active)
                mutable.value =
                    mutable.value.copy(busy = false, saveFailed = result is Outcome.Failed)
                publish()
                result
            }
        }

    fun stopAdmission(id: String) {
        blockedProfiles += id
        scope.launch { monitor?.removeProfile(id) }
    }

    suspend fun drain(id: String) =
        withContext(dispatchers.main) {
            monitor?.removeProfile(id)
            monitor?.drain(id)
            Unit
        }

    fun reopen(id: String) {
        blockedProfiles -= id
        scope.launch { monitor?.cancelDeletion(id) }
    }

    private fun policyKey(id: String): String = AutomationPolicyOwner.keyFor(id)
}

@ContributesTo(AppScope::class)
interface MonitorRuntimeAccessors {
    val monitorRuntime: MonitorRuntime
}

@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class MonitorRuntimeProfileCloser(private val runtime: MonitorRuntime) : ProfileCloser {
    override val owner: String = ":engine:attention"

    override fun stopAdmission(profile: ProfileId) = runtime.stopAdmission(profile.raw)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        runtime.drain(profile.raw)
        return Unit.ok()
    }

    override fun reopen(profile: ProfileId) = runtime.reopen(profile.raw)
}

/**
 * A throwaway transport; no event collector, mutations, or active-connection changes. Closing
 * interrupts every read.
 */
private class ReadOnlyMonitorGateway(
    private val id: String,
    private val stores: PreviewStores,
    private val factories: Set<GatewayFactory>,
    private val credentials: Set<ConnectionCredentialSource>,
    private val cleanup: CoroutineScope,
    dispatchers: AppDispatchers,
) : MonitorGateway {
    private val scope =
        ConnectionCoroutineScope(
            supervisedContext(cleanup.coroutineContext[Job], dispatchers.io, "monitor-read")
        )
    private val lane = Mutex()
    private var pair: GatewayPair? = null
    private var closed = false
    private var location = ServerLocation.Default
    override val formsSupported: Boolean
        get() = pair?.live?.capabilities?.value?.forms == true

    override val directory: String?
        get() = location.directory

    override val workspace: String?
        get() = location.workspace

    override fun setLocation(directory: String?, workspace: String?) {
        location = ServerLocation(directory, workspace)
        pair?.live?.setLocation(location)
    }

    private suspend fun open(): Outcome<GatewayPair> = lane.withLock {
        if (closed) return@withLock Failure.NotFound().failed()
        pair?.let {
            return@withLock it.ok()
        }
        val loaded =
            stores.profiles.profile(ProfileId(id)) ?: return@withLock Failure.NotFound().failed()
        if (loaded.cleartextUnconfirmed || loaded.reentry.password || loaded.reentry.agentToken)
            return@withLock Failure.SecretUnavailable().failed()
        val profile = loaded.toServerProfile() ?: return@withLock Failure.NotFound().failed()
        val source =
            credentials.singleOrNull() ?: return@withLock Failure.SecretUnavailable().failed()
        val secret =
            when (val result = source.resolve(profile)) {
                is Outcome.Ok -> result.value
                is Outcome.Failed -> return@withLock result
            }
        val factory =
            factories.singleOrNull { it.backend == profile.backend }
                ?: return@withLock Failure.NotFound().failed()
        when (
            val connected =
                withContext(scope.coroutineContext) {
                    factory.connect(GatewaySpec(profile, secret, scope, location = location))
                }
        ) {
            is Outcome.Failed -> connected
            is Outcome.Ok -> {
                if (closed) {
                    connected.value.live.close()
                    Failure.NotFound().failed()
                } else {
                    pair = connected.value
                    connected
                }
            }
        }
    }

    private suspend fun <T> read(action: suspend (GatewayPair) -> Outcome<T>): Outcome<T> {
        return try {
            val opened = open()
            if (opened is Outcome.Failed) return opened
            withContext(scope.coroutineContext) {
                if (closed) Failure.NotFound().failed() else action((opened as Outcome.Ok).value)
            }
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (closed) Failure.NotFound().failed() else throw cancelled
        }
    }

    override suspend fun sessionPage(limit: Int): Outcome<MonitorSessionPage> = read {
        it.live.sessionPage(limit = limit).map { page ->
            MonitorSessionPage(page.items.map(::sessionRow), page.nextCursor != null)
        }
    }

    override suspend fun pendingPermissions(): Outcome<List<MonitorPending>> = read {
        it.live.pendingPermissions().map { rows ->
            rows.map { row -> MonitorPending(row.id.raw, row.sessionId.raw) }
        }
    }

    override suspend fun pendingPermissionsV2(): Outcome<List<MonitorPending>> =
        pendingPermissions()

    override suspend fun listQuestions(): Outcome<List<MonitorPending>> = read {
        it.live.pendingQuestions().map { rows ->
            rows.map { row -> MonitorPending(row.id.raw, row.sessionId.raw) }
        }
    }

    override suspend fun pendingQuestionsV2(): Outcome<List<MonitorPending>> = read {
        it.operations.questions().map { rows ->
            rows.map { row -> MonitorPending(row.id.raw, row.sessionId.raw) }
        }
    }

    override suspend fun pendingForms(): Outcome<List<MonitorPending>> = read {
        it.live.pendingForms().map { rows ->
            rows.map { row -> MonitorPending(row.id.raw, row.sessionId.raw) }
        }
    }

    override suspend fun sessionStatuses(): Outcome<Map<String, String>> = read {
        it.live.sessionStatuses().map { rows ->
            rows.entries.associate { row -> row.key.raw to row.value.raw }
        }
    }

    override suspend fun session(id: String): Outcome<MonitorSession> = read {
        it.live.session(SessionId(id)).map(::sessionRow)
    }

    override suspend fun messagePage(sessionId: String, limit: Int): Outcome<MonitorMessagePage> =
        read {
            it.live.messagePage(SessionId(sessionId), limit = limit).map { page ->
                MonitorMessagePage(
                    page.items.map { row ->
                        MonitorMessage(
                            row.info.id,
                            row.info.role.name.lowercase(),
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
        scope.coroutineContext[Job]?.cancel()
        val live = pair?.live
        if (live != null) cleanup.launch { live.close() }
    }

    private fun sessionRow(session: Session): MonitorSession =
        MonitorSession(session.id.raw, session.title, session.directory, session.workspaceId?.raw)
}

/**
 * Registered beside the profile sweep; these are the Flutter monitor records, with the same owner
 * and types.
 */
internal fun registerMonitorKeys(scope: ProfileScope) {
    listOf("oc.notifyRules.", "oc.monitorRoutes.", "oc.monitorBusy.").forEach {
        scope.register(
            KeyShape(it, KeyScope.Profile, PrefType.Str, ":engine:attention", "saved monitor state")
        )
    }
    scope.register(
        KeyShape(
            "oc.monitorAlerts.",
            KeyScope.Profile,
            PrefType.StrList,
            ":engine:attention",
            "posted monitor alerts",
        )
    )
    scope.register(
        KeyShape(
            "oc.automation.",
            KeyScope.Profile,
            PrefType.Str,
            ":engine:settings",
            "per-server automation consent",
        )
    )
}
