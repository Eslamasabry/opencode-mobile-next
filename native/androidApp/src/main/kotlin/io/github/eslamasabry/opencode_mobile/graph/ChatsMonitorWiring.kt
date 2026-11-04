package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.notifications.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.monitor.*
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.engine.requests.consent.*
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationKeys
import java.time.ZoneId
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Independent deletion fence: the runtime can collect this closer without a dependency cycle
 * through itself.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class ChatsMonitorFence : ProfileCloser {
    override val owner: String = ":engine:attention:profile-monitor"
    private val blocked = MutableStateFlow<Set<ProfileId>>(emptySet())
    private var close: (suspend (ProfileId) -> Unit)? = null
    private var reopenOwner: ((ProfileId) -> Unit)? = null

    fun admits(id: ProfileId): Boolean = id !in blocked.value

    fun attach(close: suspend (ProfileId) -> Unit, reopen: (ProfileId) -> Unit) {
        this.close = close
        reopenOwner = reopen
    }

    override fun stopAdmission(profile: ProfileId) {
        blocked.update { it + profile }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> {
        close?.invoke(profile)
        return Outcome.Ok(Unit)
    }

    override fun reopen(profile: ProfileId) {
        blocked.update { it - profile }
        reopenOwner?.invoke(profile)
    }
}

/**
 * Actual saved-server monitoring. Every engine call is main-confined, every read uses a throwaway
 * authenticated gateway.
 */
@SingleIn(AppScope::class)
@Inject
class AppChatsMonitor(
    private val application: Application,
    private val runtime: PreviewRuntime,
    private val stores: PreviewStores,
    private val factories: Set<GatewayFactory>,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val fence: ChatsMonitorFence,
    private val redactor: Redactor,
    private val clock: Clock,
    private val background: BackgroundHub,
    private val activities: ResumedActivities,
    appPreferences: AppPreferences,
    appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val ready = CompletableDeferred<Outcome<Unit>>()
    private val mutable = MutableStateFlow(ChatsMonitoringUi())
    val snapshot: StateFlow<ChatsMonitoringUi> = mutable.asStateFlow()
    private val foreground = MutableStateFlow(activities.current != null)
    private val salt = UUID.randomUUID().toString()
    private val authIdentities = mutableMapOf<ProfileId, String>()
    private var failure: Failure? = null
    private lateinit var monitor: ProfileMonitor
    private lateinit var opener: MonitorRequestOpener
    private lateinit var policies: AutomationPolicies
    private val routes = mutableMapOf<String, MonitoredRoute>()
    private val words = NotifyWords(application) { appPreferences.localeTag.value }
    private val channels =
        ChannelRegistry(application.getSystemService(NotificationManager::class.java), words)
    private val shade = AndroidAlertShade(application, words, channels)

    init {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    foreground.value = true
                }

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) {
                    if (activities.current == null) foreground.value = false
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
        scope.launch {
            when (val loaded = stores.warm()) {
                is Outcome.Failed -> {
                    failure = loaded.failure
                    mutable.value = ChatsMonitoringUi(failure = loaded.failure)
                    ready.complete(loaded)
                    return@launch
                }
                is Outcome.Ok -> Unit
            }
            for (loaded in runtime.profiles.profiles.value) {
                val p = loaded.toServerProfile() ?: continue
                val auth = credentialSources.singleOrNull()?.resolve(p)
                if (auth is Outcome.Ok) credentialIdentity(loaded.id, auth.value.secret)
            }
            construct()
            ready.complete(Outcome.Ok(Unit))
            launch { monitor.revision.collect { publish() } }
            launch {
                combine(foreground, background.live) { visible, live ->
                        visible to (live.enabled && live.active)
                    }
                    .collect { (visible, live) -> monitor.setRuntime(visible, live) }
            }
            launch {
                runtime.profiles.profiles.collect {
                    for (loaded in it) {
                        val profile = loaded.toServerProfile() ?: continue
                        val auth = credentialSources.singleOrNull()?.resolve(profile)
                        if (auth is Outcome.Ok) credentialIdentity(loaded.id, auth.value.secret)
                    }
                    publish()
                    monitor.sharedRulesChanged(checkInChanged = false)
                }
            }
            launch { runtime.profiles.activeId.collect { publish() } }
            launch {
                stores.prefs.changes.collect { keys ->
                    if (
                        keys.any {
                            it.startsWith("oc.notify.") ||
                                it.startsWith("oc.location.") ||
                                it.startsWith("oc.chatListShown.")
                        }
                    ) {
                        monitor.sharedRulesChanged(checkInChanged = false)
                        publish()
                    }
                }
            }
            channels.ensure()
            monitor.setRuntime(
                foreground.value,
                background.live.value.let { it.enabled && it.active },
            )
            monitor.runLoop()
        }
    }

    private fun readable(id: String): Boolean {
        val profile = ProfileId(id)
        val loaded = runtime.profiles.profile(profile) ?: return false
        return fence.admits(profile) &&
            !loaded.cleartextUnconfirmed &&
            !loaded.reentry.password &&
            !loaded.reentry.agentToken
    }

    private fun construct() {
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
        policies =
            AutomationPolicies(
                object : AutomationStore {
                    override fun read(key: String): String? = stores.prefs.string(key)

                    override suspend fun write(key: String, value: String): Boolean =
                        this@AppChatsMonitor.write(key) { putString(key, value) }
                },
                redactor::containsSecret,
            )
        val consent =
            object : MonitorConsent {
                override fun hasRecord(profileId: String): Boolean =
                    stores.prefs.get(AutomationPolicyOwner.keyFor(profileId)) != null

                override fun allows(profileId: String): Boolean =
                    hasRecord(profileId) &&
                        policies
                            .forProfile(profileId)
                            .value
                            .value
                            .allows(AutomationBehavior.MonitorOtherServers)

                override suspend fun set(profileId: String, allowed: Boolean): Boolean =
                    policies
                        .forProfile(profileId)
                        .setBehavior(AutomationBehavior.MonitorOtherServers, allowed) is Outcome.Ok

                override fun observe(profileId: String, listener: () -> Unit): () -> Unit {
                    val job = scope.launch {
                        policies.forProfile(profileId).value.drop(1).collect { listener() }
                    }
                    return { job.cancel() }
                }
            }
        val profiles =
            object : MonitorProfiles {
                override fun profiles(): List<MonitorProfile> =
                    runtime.profiles.profiles.value.map { loaded ->
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
                            credential = authIdentities[p.id].orEmpty(),
                            usable = readable(p.id.raw),
                            boundDirectory = p.codexDirectory.takeIf { it.isNotEmpty() },
                        )
                    }

                override fun location(profileId: String): MonitorLocation? {
                    val raw =
                        stores.prefs.string("oc.location.$profileId")?.let(DartJson::decodeOrNull)
                            as? Map<*, *> ?: return null
                    return MonitorLocation(raw["directory"] as? String, raw["workspace"] as? String)
                }
            }
        val createGateway: (MonitorProfile) -> MonitorGateway = { profile ->
            WatchedGateway(
                profile,
                stores,
                factories,
                credentialSources,
                scope,
                dispatchers,
                ::credentialIdentity,
                ::readable,
            )
        }
        val shared =
            object : MonitorSharedRules {
                override val requestsAllowed: Boolean
                    get() = stores.prefs.bool(NotificationKeys.REQUESTS) != false

                override fun applyTo(own: MonitorNotifyRules): MonitorNotifyRules =
                    own.copy(
                        wifiOnly = stores.prefs.bool(NotificationKeys.WIFI_ONLY) ?: own.wifiOnly,
                        quietStart =
                            stores.prefs
                                .long(NotificationKeys.QUIET_START)
                                ?.takeIf { it in 0..1439 }
                                ?.toInt() ?: own.quietStart,
                        quietEnd =
                            stores.prefs
                                .long(NotificationKeys.QUIET_END)
                                ?.takeIf { it in 0..1439 }
                                ?.toInt() ?: own.quietEnd,
                    )
            }
        monitor =
            ProfileMonitor(
                prefs,
                profiles,
                consent,
                createGateway,
                ::readable,
                networkWifi = {
                    val connectivity = application.getSystemService(ConnectivityManager::class.java)
                    connectivity
                        .getNetworkCapabilities(connectivity.activeNetwork)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                },
                alerts =
                    object : MonitorAlerts {
                        override suspend fun alert(
                            profileId: String,
                            request: MonitoredRequest,
                            key: String,
                            token: String,
                        ): Boolean {
                            val kind =
                                when (request.kind) {
                                    MonitoredRequestKind.Permission -> AlertKind.Permission
                                    MonitoredRequestKind.Question,
                                    MonitoredRequestKind.Form -> AlertKind.Question
                                    MonitoredRequestKind.CheckIn -> AlertKind.CheckIn
                                }
                            val quiet =
                                monitor.rulesFor(profileId).quietAt(minuteOfDay(clock.now()))
                            return shade.show(
                                CodingAlert(
                                    kind,
                                    request.sessionId,
                                    key,
                                    request.id,
                                    profileId,
                                    token,
                                    subtext =
                                        redactor.redact(
                                            runtime.profiles
                                                .profile(ProfileId(profileId))
                                                ?.profile
                                                ?.name
                                                .orEmpty()
                                        ),
                                    chatTitle = redactor.redact(request.title.orEmpty()),
                                    allowActions = false,
                                ),
                                AlertRoute.Post(
                                    if (quiet) NotifyChannel.CodingActionQuiet
                                    else NotifyChannel.CodingAction,
                                    AlertPolicy.groupOf(kind),
                                ),
                            ) == AlertShown.Shown
                        }

                        override suspend fun dismiss(key: String): Boolean {
                            shade.dismiss(key)
                            return !shade.isShowing(key)
                        }
                    },
                clock = clock,
                scope = scope,
                minuteOfDay = ::minuteOfDay,
                sharedRules = shared,
            )
        opener =
            MonitorRequestOpener(
                profiles,
                createGateway,
                monitorEnabled = { monitor.rulesFor(it).enabled && consent.allows(it) },
                isReadable = ::readable,
                clock = clock,
            )
        fence.attach(
            close = { id ->
                withContext(dispatchers.main) {
                    monitor.removeProfile(id.raw)
                    monitor.drain(id.raw)
                    policies.closeProfile(id.raw)
                    routes.entries.removeAll { it.value.profileId == id.raw }
                    publish()
                }
            },
            reopen = { id ->
                scope.launch {
                    monitor.cancelDeletion(id.raw)
                    publish()
                }
            },
        )
    }

    private fun minuteOfDay(at: Instant): Int =
        java.time.Instant.ofEpochMilli(at.toEpochMilliseconds())
            .atZone(ZoneId.systemDefault())
            .let { it.hour * 60 + it.minute }

    private fun credentialIdentity(id: ProfileId, secret: String?): String {
        val trust = runtime.profiles.profile(id)?.toServerProfile()?.trust
        val value =
            DartJson.sha256Hex(
                DartJson.encode(
                    listOf(salt, secret, trust?.pinnedLeafSha256, trust?.cleartextConsentAtMs)
                )
            )
        authIdentities[id] = value
        return value
    }

    private suspend fun write(key: String, edit: PrefEdit.() -> Unit): Boolean {
        val profile =
            runtime.profiles.profiles.value.firstOrNull { key.endsWith(".${it.id.raw}") }?.id
                ?: return false
        if (!fence.admits(profile)) return false
        var wrote = false
        val result =
            stores.prefs.commit(stores.lanes.forProfile(profile)) {
                if (fence.admits(profile) && runtime.profiles.profile(profile) != null) {
                    edit()
                    wrote = true
                }
            }
        return result is Outcome.Ok && wrote
    }

    private fun publish() {
        routes.clear()
        val sources =
            runtime.profiles.profiles.value
                .filter { it.profile.backend != BackendKind.Codex }
                .map { loaded ->
                    val id = loaded.id.raw
                    val state = monitor.snapshotFor(id)
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

    suspend fun setWatched(id: ProfileId, enabled: Boolean): Outcome<Unit> =
        withContext(dispatchers.main) {
            when (val start = ready.await()) {
                is Outcome.Failed -> return@withContext start
                is Outcome.Ok -> Unit
            }
            val result = monitor.setEnabled(id.raw, enabled)
            failure = (result as? Outcome.Failed)?.failure
            publish()
            if (result is Outcome.Ok && enabled) monitor.refresh()
            result
        }

    suspend fun refresh(): Outcome<Unit> =
        withContext(dispatchers.main) {
            when (val start = ready.await()) {
                is Outcome.Failed -> return@withContext start
                is Outcome.Ok -> Unit
            }
            monitor.sharedRulesChanged(checkInChanged = false)
            monitor.refresh()
            publish()
            Outcome.Ok(Unit)
        }

    suspend fun open(key: String): Outcome<ConversationRef> =
        withContext(dispatchers.main) {
            when (val start = ready.await()) {
                is Outcome.Failed -> return@withContext start
                is Outcome.Ok -> Unit
            }
            val route = routes[key] ?: return@withContext refuse()
            openRoute(route)
        }

    suspend fun openToken(profileId: String, token: String): Outcome<ConversationRef> =
        withContext(dispatchers.main) {
            when (val start = ready.await()) {
                is Outcome.Failed -> return@withContext start
                is Outcome.Ok -> Unit
            }
            val route = monitor.routeForToken(profileId, token) ?: return@withContext refuse()
            openRoute(route)
        }

    private suspend fun openRoute(route: MonitoredRoute): Outcome<ConversationRef> {
        val id = ProfileId(route.profileId)
        val expected = runtime.profiles.profile(id)?.profile ?: return refuse()
        if (!opener.prepare(route)) return refuse()
        return when (val connected = runtime.connectWatched(id, expected)) {
            is Outcome.Failed -> {
                failure = connected.failure
                publish()
                connected
            }
            is Outcome.Ok -> Outcome.Ok(ConversationRef(id, SessionId(route.sessionId)))
        }
    }

    private fun refuse(): Outcome.Failed {
        failure = Failure.Refused(RefusalReason.StaleScope)
        publish()
        return Outcome.Failed(failure!!)
    }
}

/**
 * Lazy, short-lived gateway pair. No ConnectionRegistry admission, shared location change or prompt
 * command.
 */
private class WatchedGateway(
    private val target: MonitorProfile,
    private val stores: PreviewStores,
    private val factories: Set<GatewayFactory>,
    private val credentials: Set<ConnectionCredentialSource>,
    appScope: CoroutineScope,
    dispatchers: AppDispatchers,
    private val identify: (ProfileId, String?) -> String,
    private val readable: (String) -> Boolean,
) : MonitorGateway {
    private val id = ProfileId(target.id)
    private val parent = SupervisorJob(appScope.coroutineContext[Job])
    private val cleanupScope = appScope
    private val connectionScope =
        ConnectionCoroutineScope(appScope.coroutineContext + parent + dispatchers.main)
    private var pair: GatewayPair? = null
    private var closed = false
    private var location = ServerLocation.Default
    private var permissionRead: Outcome<List<MonitorPending>>? = null
    private var questionRead: Outcome<List<MonitorPending>>? = null
    override val formsSupported: Boolean
        get() = pair?.live?.capabilities?.value?.forms == true

    override val directory: String?
        get() = location.directory

    override val workspace: String?
        get() = location.workspace

    override fun setLocation(directory: String?, workspace: String?) {
        location = ServerLocation(directory, workspace)
    }

    private suspend fun validate(): Outcome<ConnectionCredentials> {
        if (closed || !readable(target.id))
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val loaded =
            stores.profiles.profile(id)
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
        val p =
            loaded.toServerProfile()
                ?: return Outcome.Failed(Failure.Refused(RefusalReason.InvalidAddress))
        val expectedBackend =
            when (target.backend) {
                MonitorBackend.OpenCode -> BackendKind.OpenCode2
                MonitorBackend.Paseo -> BackendKind.Paseo
                MonitorBackend.Codex -> BackendKind.Codex
            }
        if (
            loaded.profile.baseUrl != target.baseUrl ||
                loaded.profile.username != target.username ||
                loaded.profile.backend != expectedBackend ||
                loaded.profile.codexDirectory.takeIf { it.isNotEmpty() } != target.boundDirectory
        )
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val result =
            credentials.singleOrNull()?.resolve(p)
                ?: return Outcome.Failed(Failure.SecretUnavailable())
        if (result is Outcome.Ok) {
            val before = target.credential
            val now = identify(id, result.value.secret)
            if (before.isNotEmpty() && before != now)
                return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        }
        return result
    }

    private suspend fun <T> read(block: suspend (ServerGateway) -> Outcome<T>): Outcome<T> {
        val auth = validate()
        if (auth is Outcome.Failed) return auth
        val live =
            pair?.live
                ?: run {
                    val loaded =
                        stores.profiles.profile(id)
                            ?: return Outcome.Failed(Failure.Refused(RefusalReason.ProfileDeleted))
                    val profile =
                        loaded.toServerProfile()
                            ?: return Outcome.Failed(Failure.Refused(RefusalReason.InvalidAddress))
                    val factory =
                        factories.singleOrNull { it.backend == profile.backend }
                            ?: return Outcome.Failed(
                                Failure.Unavailable(FeatureId("profileMonitor"))
                            )
                    when (
                        val opened =
                            factory.connect(
                                GatewaySpec(
                                    profile,
                                    (auth as Outcome.Ok).value,
                                    connectionScope,
                                    location,
                                    reconnectAllowed = MutableStateFlow(false),
                                )
                            )
                    ) {
                        is Outcome.Failed -> return opened
                        is Outcome.Ok -> {
                            pair = opened.value
                            opened.value.live
                        }
                    }
                }
        val result = block(live)
        val current = validate()
        return if (current is Outcome.Failed) current else result
    }

    override suspend fun sessionPage(limit: Int): Outcome<MonitorSessionPage> = read { gateway ->
        gateway.sessionPage(limit = limit).map { page ->
            MonitorSessionPage(
                page.items.map {
                    MonitorSession(it.id.raw, it.title, it.directory, it.workspaceId?.raw)
                },
                page.nextCursor != null,
            )
        }
    }

    override suspend fun pendingPermissions(): Outcome<List<MonitorPending>> =
        permissionRead
            ?: read { gateway ->
                if (!gateway.capabilities.value.permissionRequests) Outcome.Ok(emptyList())
                else
                    gateway.pendingPermissions().map { rows ->
                        rows.map { MonitorPending(it.id.raw, it.sessionId.raw) }
                    }
            }
                .also { permissionRead = it }

    override suspend fun pendingPermissionsV2(): Outcome<List<MonitorPending>> =
        pendingPermissions()

    override suspend fun listQuestions(): Outcome<List<MonitorPending>> =
        questionRead
            ?: read { gateway ->
                if (!gateway.capabilities.value.legacyQuestionRequests) Outcome.Ok(emptyList())
                else
                    gateway.pendingQuestions().map { rows ->
                        rows.map { MonitorPending(it.id.raw, it.sessionId.raw) }
                    }
            }
                .also { questionRead = it }

    override suspend fun pendingQuestionsV2(): Outcome<List<MonitorPending>> = listQuestions()

    override suspend fun pendingForms(): Outcome<List<MonitorPending>> = read { gateway ->
        gateway.pendingForms().map { rows ->
            rows.map { MonitorPending(it.id.raw, it.sessionId.raw) }
        }
    }

    override suspend fun sessionStatuses(): Outcome<Map<String, String>> = read { gateway ->
        gateway.sessionStatuses().map { rows ->
            rows.mapKeys { it.key.raw }.mapValues { it.value.raw }
        }
    }

    override suspend fun session(id: String): Outcome<MonitorSession> = read { gateway ->
        gateway.session(SessionId(id)).map {
            MonitorSession(it.id.raw, it.title, it.directory, it.workspaceId?.raw)
        }
    }

    override suspend fun messagePage(sessionId: String, limit: Int): Outcome<MonitorMessagePage> =
        read { gateway ->
            gateway.messagePage(SessionId(sessionId), limit = limit).map { page ->
                MonitorMessagePage(
                    page.items.map {
                        MonitorMessage(
                            it.info.id,
                            it.info.role.name.lowercase(),
                            it.info.finish,
                            it.info.errorKind != null || it.info.errorText != null,
                        )
                    },
                    page.nextCursor != null,
                )
            }
        }

    override fun close() {
        if (closed) return
        closed = true
        val live = pair?.live
        pair = null
        // Cancellation is synchronous; close runs outside the cancelled child and releases the
        // socket without retry.
        parent.cancel()
        if (live != null) cleanupScope.launch { live.close() }
    }
}
