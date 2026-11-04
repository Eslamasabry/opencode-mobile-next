package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.attention.monitor.*
import io.github.eslamasabry.oc.engine.connection.ConnectionCredentialSource
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.feature.monitor.presentation.*
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicit, foreground-only checks. This owner never starts a server or a background service. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<MonitorHost>())
@Inject
class AppProfileMonitor(
    private val application: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val factories: Set<GatewayFactory>,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val redactor: Redactor,
    appScope: AppCoroutineScope,
) : MonitorHost {
    private val lane = dispatchers.serial("profile-monitor")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val cleanup = CoroutineScope(appScope.coroutineContext + dispatchers.io)
    private val mutable = MutableStateFlow(MonitorUi())
    override val state: StateFlow<MonitorUi> = mutable.asStateFlow()
    private val viewers = AtomicInteger(0)
    private val holders: Int
        get() = viewers.get()

    private val gateways = ConcurrentHashMap.newKeySet<OwnedMonitorGateway>()
    @Volatile private var records: Map<String, MonitorRecord> = emptyMap()
    private val blocked = ConcurrentHashMap.newKeySet<String>()
    private var monitor: ProfileMonitor? = null
    private var reader: MonitorAttentionReader? = null
    private val listeners = HashMap<String, MutableSet<() -> Unit>>()
    private var observing = false
    private var checking = false

    private fun consentKey(id: String) = "oc.monitorConsent.$id"

    private fun consent(id: String): Boolean =
        (stores.prefs.get(consentKey(id)) as? PrefValue.Bool)?.value == true

    private fun allowed(id: String): Boolean = holders > 0 && id !in blocked && consent(id)

    private suspend fun ready(): Boolean {
        if (stores.warm() !is Outcome.Ok) return false
        if (monitor == null) {
            val prefs =
                object : MonitorPrefs {
                    override fun getString(key: String): String? = stores.prefs.string(key)

                    override fun getStringList(key: String): List<String>? =
                        (stores.prefs.get(key) as? PrefValue.StrList)?.value

                    override fun contains(key: String): Boolean = stores.prefs.get(key) != null

                    private fun id(key: String): ProfileId? {
                        val prefix =
                            listOf(
                                    "oc.notifyRules.",
                                    "oc.monitorAlerts.",
                                    "oc.monitorRoutes.",
                                    "oc.monitorBusy.",
                                )
                                .firstOrNull { key.startsWith(it) } ?: return null
                        val id = ProfileId(key.removePrefix(prefix))
                        return stores.profiles.profile(id)?.id
                    }

                    override suspend fun putString(key: String, value: String): Boolean {
                        val profile = id(key) ?: return false
                        return stores.prefs.commit(stores.lanes.forProfile(profile)) {
                            putString(key, value)
                        } is Outcome.Ok
                    }

                    override suspend fun putStringList(key: String, value: List<String>): Boolean {
                        val profile = id(key) ?: return false
                        return stores.prefs.commit(stores.lanes.forProfile(profile)) {
                            putStringList(key, value)
                        } is Outcome.Ok
                    }

                    override suspend fun remove(key: String): Boolean {
                        val profile = id(key) ?: return false
                        return stores.prefs.commit(stores.lanes.forProfile(profile)) {
                            remove(key)
                        } is Outcome.Ok
                    }
                }
            val profiles =
                object : MonitorProfiles {
                    override fun profiles(): List<MonitorProfile> =
                        records.values.map { it.monitor }

                    override fun location(profileId: String): MonitorLocation? =
                        records[profileId]?.location
                }
            val policy =
                object : MonitorConsent {
                    override fun allows(profileId: String): Boolean = consent(profileId)

                    override fun hasRecord(profileId: String): Boolean =
                        stores.prefs.get(consentKey(profileId)) != null

                    override suspend fun set(profileId: String, allowed: Boolean): Boolean {
                        val id = ProfileId(profileId)
                        if (profileId in blocked || stores.profiles.profile(id) == null)
                            return false
                        val saved =
                            stores.prefs.commit(stores.lanes.forProfile(id)) {
                                putBool(consentKey(profileId), allowed)
                            }
                        if (saved is Outcome.Failed) return false
                        listeners[profileId]?.toList()?.forEach { it() }
                        return true
                    }

                    override fun observe(profileId: String, listener: () -> Unit): () -> Unit {
                        listeners.getOrPut(profileId) { LinkedHashSet() }.add(listener)
                        return {
                            listeners[profileId]?.remove(listener)
                            Unit
                        }
                    }
                }
            val alerts =
                object : MonitorAlerts {
                    // This page does not enroll background notification delivery. Refuse any
                    // attempted enrollment.
                    override suspend fun alert(
                        profileId: String,
                        request: MonitoredRequest,
                        key: String,
                        token: String,
                    ): Boolean = false

                    override suspend fun dismiss(key: String): Boolean {
                        application
                            .getSystemService(NotificationManager::class.java)
                            .cancel("profile-monitor", key.hashCode() and Int.MAX_VALUE)
                        return true
                    }
                }
            val attention = MonitorAttentionReader(clock, cleanup, redactor)
            reader = attention
            monitor =
                ProfileMonitor(
                        prefs,
                        profiles,
                        policy,
                        createGateway = { p ->
                            val record =
                                records[p.id]
                                    ?: throw IllegalStateException("retired monitor source")
                            OwnedMonitorGateway(
                                    record,
                                    factories,
                                    credentialSources,
                                    dispatchers,
                                    cleanup,
                                    { recordCurrent(record) && allowed(p.id) },
                                    { gateways.remove(it) },
                                )
                                .also { gateways.add(it) }
                        },
                        isReadable = { id ->
                            records[id]?.monitor?.usable == true && id !in blocked
                        },
                        networkWifi = {
                            attempt({ Failure.Device() }) {
                                    val manager =
                                        application.getSystemService(
                                            ConnectivityManager::class.java
                                        )
                                    val network = manager.activeNetwork
                                    network?.let {
                                        manager
                                            .getNetworkCapabilities(it)
                                            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                                    }
                                }
                                .getOrNull()
                        },
                        alerts = alerts,
                        clock = clock,
                        scope = scope,
                        minuteOfDay = { instant ->
                            val date =
                                ZonedDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(instant.toEpochMilliseconds()),
                                    ZoneId.systemDefault(),
                                )
                            date.hour * 60 + date.minute
                        },
                        readAttention = attention,
                    )
                    .also { engine ->
                        engine.setRuntime(holders > 0, false)
                        scope.launch { engine.revision.collect { publish() } }
                    }
        }
        if (!observing) {
            observing = true
            scope.launch {
                stores.profiles.profiles.drop(1).collect {
                    if (holders > 0) {
                        val known = records.keys
                        val saved = it.mapTo(HashSet()) { row -> row.id.raw }
                        (known - saved).forEach { id ->
                            monitor?.removeProfile(id)
                            reader?.forget(id)
                        }
                        prepare()
                        publish()
                    }
                }
            }
            scope.launch {
                stores.prefs.changes.collect { keys ->
                    if (holders > 0 && keys.any { it.startsWith("oc.monitorConsent.") }) {
                        listeners.values.flatMap { it.toList() }.forEach { it() }
                        publish()
                    }
                }
            }
        }
        return true
    }

    private suspend fun prepare() {
        val next = LinkedHashMap<String, MonitorRecord>()
        for (loaded in stores.profiles.profiles.value) {
            if (loaded.profile.transientTransport) continue
            val server = loaded.toServerProfile() ?: continue
            val supported =
                server.backend != BackendKind.Codex &&
                    factories.any { it.backend == server.backend }
            val credentials =
                if (
                    supported &&
                        !loaded.cleartextUnconfirmed &&
                        !loaded.reentry.password &&
                        !loaded.reentry.agentToken
                ) {
                    (credentialSources.singleOrNull()?.resolve(server) as? Outcome.Ok)?.value
                } else null
            val location =
                registry.live.value[loaded.id]?.session?.server?.location?.value
                    ?: ServerLocation(loaded.profile.codexDirectory.takeIf { it.isNotBlank() })
            val p =
                MonitorProfile(
                    id = loaded.id.raw,
                    baseUrl = loaded.profile.baseUrl,
                    username = loaded.profile.username,
                    backend =
                        when (server.backend) {
                            BackendKind.OpenCode2 -> MonitorBackend.OpenCode
                            BackendKind.Paseo -> MonitorBackend.Paseo
                            BackendKind.Codex -> MonitorBackend.Codex
                        },
                    credential =
                        credentials
                            ?.let {
                                DartJson.sha256Hex(DartJson.encode(listOf(it.username, it.secret)))
                            }
                            .orEmpty(),
                    usable =
                        supported &&
                            credentials != null &&
                            !loaded.profile.requiresOpenCode2Migration,
                    boundDirectory = loaded.profile.codexDirectory.takeIf { it.isNotBlank() },
                )
            next[p.id] =
                MonitorRecord(
                    p,
                    server,
                    credentials,
                    MonitorLocation(location.directory, location.workspace),
                    supported,
                )
        }
        records = next
    }

    private fun recordCurrent(record: MonitorRecord): Boolean {
        val id = record.server.id
        val saved = stores.profiles.profile(id)?.toServerProfile() ?: return false
        val selected =
            registry.live.value[id]?.session?.server?.location?.value
                ?: ServerLocation(
                    stores.profiles.profile(id)?.profile?.codexDirectory?.takeIf { it.isNotBlank() }
                )
        return records[id.raw]?.monitor == record.monitor &&
            saved == record.server &&
            selected.directory == record.location.directory &&
            selected.workspace == record.location.workspace &&
            id.raw !in blocked
    }

    override suspend fun open(): Unit =
        withContext(lane) {
            viewers.incrementAndGet()
            if (!ready()) {
                mutable.value = mutable.value.copy(failed = true)
                return@withContext
            }
            prepare()
            monitor?.setRuntime(true, false)
            publish()
        }

    override suspend fun setEnabled(profile: ProfileId, enabled: Boolean): Outcome<Unit> =
        withContext(lane) {
            if (holders <= 0 || !ready())
                return@withContext Failure.Refused(RefusalReason.StaleScope).failed()
            prepare()
            val engine =
                monitor
                    ?: return@withContext Failure.Unavailable(FeatureId("profileMonitor")).failed()
            engine.setRuntime(false, false)
            val result =
                try {
                    engine.setEnabled(profile.raw, enabled)
                } finally {
                    engine.setRuntime(holders > 0, false)
                }
            // This UI is explicitly foreground only: no notifications or check-in enrollment is
            // implied by the switch.
            if (result is Outcome.Ok)
                engine.setRules(
                    profile.raw,
                    engine
                        .rulesFor(profile.raw)
                        .copy(notifications = false, checkInAfterMinutes = null),
                )
            mutable.value = mutable.value.copy(failed = result is Outcome.Failed)
            publish()
            result
        }

    override suspend fun check(): Outcome<Unit> =
        withContext(lane) {
            if (holders <= 0 || !ready())
                return@withContext Failure.Refused(RefusalReason.StaleScope).failed()
            if (checking) return@withContext Unit.ok()
            checking = true
            mutable.value = mutable.value.copy(checking = true, failed = false)
            try {
                prepare()
                monitor?.setRuntime(true, false)
                monitor?.refreshNow()
                Unit.ok()
            } finally {
                checking = false
                publish()
            }
        }

    private fun publish() {
        val engine = monitor ?: return
        val rows =
            records.values
                .map { record ->
                    val p = record.monitor
                    val snap = engine.snapshotFor(p.id)
                    MonitorProfileUi(
                        ProfileId(p.id),
                        redactor.redact(record.server.name).take(160),
                        engine.rulesFor(p.id).enabled,
                        record.supported,
                        p.usable,
                        when {
                            !engine.rulesFor(p.id).enabled -> MonitorRowStatus.Off
                            snap.status == MonitorStatus.Checking -> MonitorRowStatus.Checking
                            snap.status == MonitorStatus.Current &&
                                snap.complete &&
                                snap.attentionComplete -> MonitorRowStatus.Current
                            snap.status == MonitorStatus.Current -> MonitorRowStatus.Partial
                            snap.status == MonitorStatus.Waiting -> MonitorRowStatus.Waiting
                            snap.status == MonitorStatus.Paused -> MonitorRowStatus.Paused
                            else -> MonitorRowStatus.Unknown
                        },
                        pending = if (snap.complete) snap.requests.size else null,
                        running = snap.runningCount,
                        sessions =
                            (snap.attention.mapNotNull { item ->
                                    item.sessionId?.let {
                                        MonitorSessionUi(
                                            it,
                                            redactor.redact(item.title.orEmpty()).take(160),
                                            item.kind == MonitorAttentionKind.FailedRun,
                                            item.isFresh,
                                        )
                                    }
                                } +
                                    snap.requests
                                        .filter { it.sessionId != "global" }
                                        .map { item ->
                                            MonitorSessionUi(
                                                item.sessionId,
                                                redactor.redact(item.title.orEmpty()).take(160),
                                                false,
                                                snap.complete,
                                            )
                                        })
                                .distinctBy { it.id }
                                .toImmutableList(),
                    )
                }
                .toImmutableList()
        mutable.value = mutable.value.copy(rows = rows, checking = checking)
    }

    override fun sessionTarget(profile: ProfileId, session: String): ConversationRef? {
        val record = records[profile.raw] ?: return null
        if (!recordCurrent(record) || !allowed(profile.raw)) return null
        if (
            state.value.rows.none {
                it.id == profile && it.sessions.any { row -> row.id == session }
            }
        )
            return null
        return ConversationRef(profile, SessionId(session))
    }

    override fun close() {
        if (holders <= 0) return
        viewers.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (holders == 0) {
            gateways.toList().forEach { it.close() }
            scope.launch {
                if (holders == 0) {
                    monitor?.setRuntime(false, false)
                    records = emptyMap()
                    publish()
                }
            }
        }
    }

    fun stopAdmission(profile: ProfileId) {
        blocked += profile.raw
        gateways.filter { it.profileId == profile.raw }.forEach { it.close() }
        scope.launch {
            if (profile.raw in blocked) {
                monitor?.removeProfile(profile.raw)
                reader?.forget(profile.raw)
                publish()
            }
        }
    }

    suspend fun drain(profile: ProfileId): Outcome<Unit> =
        withContext(lane) {
            monitor?.removeProfile(profile.raw)
            monitor?.drain(profile.raw)
            gateways
                .filter { it.profileId == profile.raw }
                .forEach {
                    it.close()
                    it.awaitClosed()
                }
            Unit.ok()
        }

    fun reopen(profile: ProfileId) {
        blocked -= profile.raw
        scope.launch {
            monitor?.cancelDeletion(profile.raw)
            publish()
        }
    }
}

private class MonitorRecord(
    val monitor: MonitorProfile,
    val server: ServerProfile,
    val credentials: ConnectionCredentials?,
    val location: MonitorLocation,
    val supported: Boolean,
)

/**
 * A lazy owned read-only connection. Close revokes before suspension and disposes late handshakes
 * too.
 */
private class OwnedMonitorGateway(
    private val record: MonitorRecord,
    private val factories: Set<GatewayFactory>,
    private val credentialSources: Set<ConnectionCredentialSource>,
    private val dispatchers: AppDispatchers,
    private val cleanup: CoroutineScope,
    private val admitted: () -> Boolean,
    private val released: (OwnedMonitorGateway) -> Unit,
) : MonitorGateway {
    val profileId: String
        get() = record.monitor.id

    private val closed = AtomicBoolean(false)
    private val finished = CompletableDeferred<Unit>()
    private var permissionsRead: Outcome<List<MonitorPending>>? = null
    private var questionsRead: Outcome<List<MonitorPending>>? = null
    private val lock = Mutex()
    private var gateway: ServerGateway? = null
    private val owner =
        ConnectionCoroutineScope(
            supervisedContext(
                cleanup.coroutineContext[Job],
                dispatchers.default,
                "profile-monitor-read",
            )
        )
    private var location = ServerLocation(record.location.directory, record.location.workspace)
    override val formsSupported: Boolean
        get() = gateway?.capabilities?.value?.forms == true

    override val directory: String?
        get() = location.directory

    override val workspace: String?
        get() = location.workspace

    override fun setLocation(directory: String?, workspace: String?) {
        location = ServerLocation(directory, workspace)
    }

    private fun current(): Boolean = !closed.get() && admitted()

    private suspend fun credentialsCurrent(): Boolean {
        if (!current()) return false
        val currentCredentials =
            (credentialSources.singleOrNull()?.resolve(record.server) as? Outcome.Ok)?.value
        return current() && currentCredentials != null && currentCredentials == record.credentials
    }

    private suspend fun server(): Outcome<ServerGateway> = lock.withLock {
        if (!credentialsCurrent()) return@withLock staleMonitor()
        gateway?.let {
            return@withLock it.ok()
        }
        val factory =
            factories.firstOrNull { it.backend == record.server.backend }
                ?: return@withLock Failure.Unavailable(FeatureId("profileMonitor")).failed()
        val credentials = record.credentials ?: return@withLock staleMonitor()
        val result =
            factory.connect(
                GatewaySpec(
                    record.server,
                    credentials,
                    owner,
                    location,
                    reconnectAllowed = MutableStateFlow(false),
                )
            )
        if (result is Outcome.Failed) return@withLock result
        val live = (result as Outcome.Ok).value.live
        if (!credentialsCurrent()) {
            withContext(NonCancellable) { live.close() }
            return@withLock staleMonitor()
        }
        gateway = live
        live.ok()
    }

    private suspend fun <T> read(block: suspend (ServerGateway) -> Outcome<T>): Outcome<T> {
        val live =
            when (val opened = server()) {
                is Outcome.Failed -> return opened
                is Outcome.Ok -> opened.value
            }
        if (!current()) return staleMonitor()
        val result = block(live)
        return if (credentialsCurrent()) result else staleMonitor()
    }

    override suspend fun sessionPage(limit: Int): Outcome<MonitorSessionPage> = read { g ->
        g.sessionPage(limit = limit).map { page ->
            MonitorSessionPage(page.items.map { session(it) }, page.nextCursor != null)
        }
    }

    override suspend fun pendingPermissions(): Outcome<List<MonitorPending>> = read { g ->
        g.pendingPermissions().map { rows ->
            rows.map { MonitorPending(it.id.raw, it.sessionId.raw) }
        }
    }
        .also { permissionsRead = it }

    override suspend fun pendingPermissionsV2(): Outcome<List<MonitorPending>> =
        if (!current()) staleMonitor() else permissionsRead ?: pendingPermissions()

    override suspend fun listQuestions(): Outcome<List<MonitorPending>> = read { g ->
        g.pendingQuestions().map { rows ->
            rows.map { MonitorPending(it.id.raw, it.sessionId.raw) }
        }
    }
        .also { questionsRead = it }

    override suspend fun pendingQuestionsV2(): Outcome<List<MonitorPending>> =
        if (!current()) staleMonitor() else questionsRead ?: listQuestions()

    override suspend fun pendingForms(): Outcome<List<MonitorPending>> = read { g ->
        g.pendingForms().map { rows -> rows.map { MonitorPending(it.id.raw, it.sessionId.raw) } }
    }

    override suspend fun sessionStatuses(): Outcome<Map<String, String>> = read { g ->
        g.sessionStatuses().map { rows -> rows.mapKeys { it.key.raw }.mapValues { it.value.raw } }
    }

    override suspend fun session(id: String): Outcome<MonitorSession> = read { g ->
        g.session(SessionId(id)).map(::session)
    }

    override suspend fun messagePage(sessionId: String, limit: Int): Outcome<MonitorMessagePage> =
        read { g ->
            g.messagePage(SessionId(sessionId), limit = limit).map { page ->
                MonitorMessagePage(
                    page.items.map { row ->
                        MonitorMessage(
                            row.info.id,
                            if (row.info.role == Role.User) "user" else "assistant",
                            row.info.finish,
                            row.info.errorKind != null ||
                                row.info.errorText != null ||
                                row.info.finish == "error",
                        )
                    },
                    page.nextCursor != null,
                )
            }
        }

    private fun session(value: Session) =
        MonitorSession(value.id.raw, value.title, value.directory, value.workspaceId?.raw)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        owner.coroutineContext[Job]?.cancel()
        cleanup.launch(NonCancellable) {
            try {
                lock.withLock {
                    gateway?.close()
                    gateway = null
                }
            } finally {
                try {
                    owner.coroutineContext[Job]?.cancelAndJoin()
                } finally {
                    finished.complete(Unit)
                    released(this@OwnedMonitorGateway)
                }
            }
        }
    }

    suspend fun awaitClosed(): Unit = finished.await()
}

private fun staleMonitor(): Outcome.Failed =
    Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))

@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
@Inject
class MonitorProfileCloser(private val monitor: AppProfileMonitor) : ProfileCloser {
    override val owner: String = ":engine:attention (foreground monitor)"

    override fun stopAdmission(profile: ProfileId): Unit = monitor.stopAdmission(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = monitor.drain(profile)

    override fun reopen(profile: ProfileId): Unit = monitor.reopen(profile)
}
