package io.github.eslamasabry.oc.engine.attention.monitor

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sequential, bounded attention snapshots of the person's other saved servers (port of Flutter `ProfileMonitor`;
 * B-ANDR-056..064, B-ANDR-058..060 check-in reminders). No subscriptions, no active-connection mutation, no
 * background-service ownership: monitoring is off by default, needs a stored consent record per server, reads only
 * while the app is in front or an existing live service is allowed to run, and never enables keep-live itself.
 *
 * Confinement: every member is called from one dispatcher (the app's main one); [refresh] is the engine's one serial
 * lane (a second call joins the read in flight). [scope] only runs fire-and-forget dismissals.
 */
@OptIn(ExperimentalUuidApi::class)
public class ProfileMonitor(
    private val prefs: MonitorPrefs,
    private val profiles: MonitorProfiles,
    private val consent: MonitorConsent,
    private val createGateway: (MonitorProfile) -> MonitorGateway,
    private val isReadable: (String) -> Boolean,
    /** null means the platform cannot establish Wi-Fi policy: fail closed. */
    private val networkWifi: suspend () -> Boolean?,
    private val alerts: MonitorAlerts,
    private val clock: Clock,
    private val scope: CoroutineScope,
    /** The local wall-clock minute of an instant, for quiet hours. */
    private val minuteOfDay: (Instant) -> Int,
    private val alertsAllowed: ((String) -> Boolean)? = null,
    private val readAttention: MonitorAttentionReading? = null,
    private val sharedRules: MonitorSharedRules = MonitorSharedRules.None,
    public val foregroundInterval: Duration = 1.minutes,
    public val backgroundInterval: Duration = 5.minutes,
    public val timeout: Duration = 8.seconds,
) {
    // Salted, runtime-only identity: never retain another raw credential copy.
    private val identitySalt = Uuid.random().toHexString() + Uuid.random().toHexString()
    private val sources = mutableMapOf<String, String>()
    private val snapshots = mutableMapOf<String, ProfileAttentionSnapshot>()
    private val failures = mutableMapOf<String, Int>()
    private val epochs = mutableMapOf<String, Int>()
    private val pollGenerations = mutableMapOf<String, Int>()
    private val blocked = mutableSetOf<String>()
    private val next = mutableMapOf<String, Instant>()
    private val alertKeys = mutableMapOf<String, MutableSet<String>>()
    private val writes = mutableMapOf<String, Mutex>()
    private val polls = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val busy = mutableMapOf<String, Map<String, ObservedBusyInterval>>()
    private val policyAllowed = mutableMapOf<String, Boolean>()
    private val policyRemovers = mutableMapOf<String, () -> Unit>()
    private var activeProfileId: String? = null
    private var activeGateway: MonitorGateway? = null
    private var foreground = true
    private var backgroundAllowed = false
    private var disposed = false
    private var cursor = 0
    private var runtimeGeneration = 0
    private var inFlight: CompletableDeferred<Unit>? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val mutableRevision = MutableStateFlow(0)

    /** Bumps on every visible change; the UI re-reads [snapshots] then. */
    public val revision: StateFlow<Int> = mutableRevision.asStateFlow()

    public val refreshing: Boolean get() = inFlight != null
    public val runningAllowed: Boolean get() = foreground || backgroundAllowed

    private fun notifyChanged() {
        if (!disposed) mutableRevision.value += 1
    }

    /** Readable, non-deleted profiles' current pictures. Keys are profile ids only. */
    public fun snapshots(): Map<String, ProfileAttentionSnapshot> =
        profiles.profiles()
            .filter { it.id !in blocked && isReadable(it.id) }
            .associate { it.id to snapshotFor(it.id) }

    /** Codex keeps its requests inside a live app-server session, so it is never read from here. */
    public fun supportsProfile(profile: MonitorProfile): Boolean = profile.backend != MonitorBackend.Codex

    private fun profile(id: String): MonitorProfile? = profiles.profiles().firstOrNull { it.id == id }

    private fun source(p: MonitorProfile): String =
        DartJson.sha256Hex(
            identitySalt +
                JsonArray(listOf(p.baseUrl, p.username, p.credential, p.flavor, p.backend.name, p.boundDirectory, p.teamEngineToken).map { JsonPrimitive(it) }),
        )

    private fun sameSource(id: String): Boolean {
        val p = profile(id) ?: return false
        return sources[id] == source(p)
    }

    private suspend fun reconcileSource(p: MonitorProfile) {
        val now = source(p)
        val prior = sources[p.id]
        if (prior == now) return
        sources[p.id] = now
        if (prior == null) return
        epochs[p.id] = (epochs[p.id] ?: 0) + 1
        invalidatePoll(p.id)
        snapshots.remove(p.id)
        next.remove(p.id)
        failures.remove(p.id)
        dismissProfile(p.id)
        prefs.remove(routesKey(p.id))
        forgetBusy(p.id)
    }

    private fun beginPoll(id: String): Int = ((pollGenerations[id] ?: 0) + 1).also { pollGenerations[id] = it }

    private fun invalidatePoll(id: String) {
        pollGenerations[id] = (pollGenerations[id] ?: 0) + 1
    }

    // ---- busy intervals (check-in reminders) ----

    private fun busyIntervals(id: String): Map<String, ObservedBusyInterval> =
        busy.getOrPut(id) {
            val raw = prefs.getString(busyIntervalsKey(id)) ?: return@getOrPut emptyMap()
            val list = parseArray(raw) ?: return@getOrPut emptyMap()
            list.mapNotNull { ObservedBusyInterval.fromJson(it) }.associateBy { it.sessionId }
        }

    /**
     * Folds one complete, successful status read into the busy intervals. A failed or partial poll never gets here: it
     * must neither end an interval nor extend it. Idle or absent ends it; a gap beyond [BUSY_OBSERVATION_GAP] restarts it.
     */
    private suspend fun observeBusy(
        id: String,
        statuses: Map<String, String>,
        sessions: Map<String, MonitorSession>,
        now: Instant,
        directory: String?,
        workspace: String?,
    ): List<ObservedBusyInterval> {
        val previous = busyIntervals(id)
        val nextIntervals = LinkedHashMap<String, ObservedBusyInterval>()
        for ((sessionId, status) in statuses) {
            if (status == "idle") continue
            val session = sessions[sessionId]
            val prior = previous[sessionId]
            val observedDirectory = session?.directory ?: directory
            val observedWorkspace = session?.workspace ?: workspace
            val continues =
                prior != null &&
                    prior.directory == observedDirectory &&
                    prior.workspace == observedWorkspace &&
                    prior.lastObservedBusyAt <= now &&
                    now - prior.lastObservedBusyAt <= BUSY_OBSERVATION_GAP
            nextIntervals[sessionId] =
                if (continues) {
                    prior.observedAgainAt(now, session?.title, session?.directory, session?.workspace)
                } else {
                    ObservedBusyInterval(sessionId, now, now, session?.title, observedDirectory, observedWorkspace)
                }
        }
        busy[id] = nextIntervals
        // Observation persistence is best effort; a reminder separately needs its claim durably saved first.
        attempt({ Failure.StorageRefused() }) {
            if (nextIntervals.isEmpty()) prefs.remove(busyIntervalsKey(id))
            else prefs.putString(busyIntervalsKey(id), ObservedBusyInterval.listToJson(nextIntervals.values))
        }
        return nextIntervals.values.sortedBy { it.firstObservedBusyAt }
    }

    private suspend fun forgetBusy(id: String) {
        busy.remove(id)
        attempt({ Failure.StorageRefused() }) { prefs.remove(busyIntervalsKey(id)) }
    }

    private suspend fun claimCheckIn(id: String, request: MonitoredRequest, current: () -> Boolean): Boolean {
        if (!current()) return false
        val intervals = busyIntervals(id)
        val interval = intervals[request.sessionId]
        if (interval == null || interval.id != request.id || interval.reminderClaimed) return false
        val claimed = LinkedHashMap(intervals).apply { put(request.sessionId, interval.claimReminder()) }
        val saved = attempt({ Failure.StorageRefused() }) { prefs.putString(busyIntervalsKey(id), ObservedBusyInterval.listToJson(claimed.values)) }
        if (saved.getOrNull() != true) {
            attempt({ Failure.StorageRefused() }) { prefs.reload() }
            return false
        }
        if (!current()) return false
        busy[id] = claimed
        return true
    }

    // ---- consent ----

    private fun allows(id: String): Boolean {
        if (id !in policyRemovers) {
            policyAllowed[id] = consent.allows(id)
            policyRemovers[id] = consent.observe(id) { policyChanged(id) }
        }
        return consent.allows(id)
    }

    private fun policyChanged(id: String) {
        val nextAllowed = consent.allows(id)
        if (disposed || id in blocked || nextAllowed == policyAllowed[id]) return
        policyAllowed[id] = nextAllowed
        invalidatePoll(id)
        next.remove(id)
        snapshots[id]?.let { prior -> snapshots[id] = stale(id, if (nextAllowed) MonitorStatus.Waiting else MonitorStatus.Disabled, prior) }
        if (activeProfileId == id) activeGateway?.close()
        if (!nextAllowed) scope.launch { dismissProfile(id) }
        notifyChanged()
        wake.trySend(Unit)
    }

    private fun detachPolicy(id: String) {
        policyRemovers.remove(id)?.invoke()
        policyAllowed.remove(id)
    }

    private suspend fun migrateConsent(p: MonitorProfile) {
        if (p.id in blocked || !supportsProfile(p)) return
        if (consent.hasRecord(p.id)) return
        // Only a stored rule is prior consent; the old two-phone-server fallback was an implicit default.
        val raw = prefs.getString(rulesKey(p.id)) ?: return
        val rules = MonitorNotifyRules.fromJson(raw) ?: return
        if (rules.enabled) {
            allows(p.id)
            consent.set(p.id, true)
        }
    }

    private fun stale(id: String, status: MonitorStatus, prior: ProfileAttentionSnapshot?): ProfileAttentionSnapshot =
        ProfileAttentionSnapshot(
            profileId = id,
            status = status,
            checkedAt = prior?.checkedAt,
            directory = prior?.directory,
            workspace = prior?.workspace,
            requests = prior?.requests ?: emptyList<MonitoredRequest>().toImmutableList(),
            attention = (prior?.attention ?: emptyList()).map { it.copy(isFresh = false) }.toImmutableList(),
            busyIntervals = prior?.busyIntervals ?: emptyList<ObservedBusyInterval>().toImmutableList(),
            runningCount = prior?.runningCount,
            nextCheckAt = next[id],
        )

    // ---- routes ----

    private fun routes(id: String): LinkedHashMap<String, JsonObject> {
        val raw = prefs.getString(routesKey(id)) ?: return LinkedHashMap()
        val o = parseObject(raw) ?: return LinkedHashMap()
        return LinkedHashMap<String, JsonObject>().apply { o.forEach { (k, v) -> if (v is JsonObject) put(k, v) } }
    }

    /** The opaque alert token's route, or null when it is stale, from another source, future-dated or over a day old. */
    public fun routeForToken(id: String, token: String): MonitoredRoute? {
        if (!isReadable(id) || id in blocked || !rulesFor(id).enabled || !allows(id)) return null
        val p = profile(id) ?: return null
        if (!supportsProfile(p)) return null
        val route = MonitoredRoute.fromJson(id, routes(id)[token]) ?: return null
        val now = clock.now()
        if ((id in sources && !sameSource(id)) ||
            route.createdAt > now ||
            p.baseUrl != route.serverUrl ||
            route.sourceIdentity != routeSourceIdentity(p) ||
            now - route.createdAt > 1.days
        ) {
            return null
        }
        return route
    }

    private suspend fun saveRoute(id: String, request: MonitoredRequest): String? {
        if (id in blocked) return null
        val p = profile(id) ?: return null
        val token = Uuid.random().toHexString() + Uuid.random().toHexString().take(16)
        val all = routes(id)
        while (all.size >= MAX_ROUTES) all.remove(all.keys.first())
        all[token] =
            MonitoredRoute(
                profileId = id,
                requestId = request.id,
                sessionId = request.sessionId,
                kind = request.kind,
                createdAt = clock.now(),
                serverUrl = p.baseUrl,
                sourceIdentity = routeSourceIdentity(p),
                directory = request.directory,
                workspace = request.workspace,
            ).toJson()
        return if (prefs.putString(routesKey(id), JsonObject(all).toString())) token else null
    }

    // ---- rules ----

    /** The rule as it applies now: shared rules over the server's own record; Wi-Fi only never applies on-phone. */
    public fun rulesFor(id: String): MonitorNotifyRules {
        val rules = sharedRules.applyTo(storedRules(id))
        val p = profile(id)
        return if (p != null && isOnThisPhone(p) && rules.wifiOnly) rules.copy(wifiOnly = false) else rules
    }

    private fun storedRules(id: String): MonitorNotifyRules {
        prefs.getString(rulesKey(id))?.let { raw -> MonitorNotifyRules.fromJson(raw)?.let { return it } }
        // The old two-phone-server settings default, for compatibility. It is not consent: allows() still blocks reads.
        val p = profile(id)
        return if (p != null && isOnThisPhone(p) && profiles.profiles().count { isOnThisPhone(it) } > 1) {
            MonitorNotifyRules(enabled = true)
        } else {
            MonitorNotifyRules()
        }
    }

    /** The shared rules were edited: a changed check-in duration retires posted reminders. */
    public suspend fun sharedRulesChanged(checkInChanged: Boolean) {
        if (disposed) return
        next.clear()
        snapshots.entries.removeAll { it.value.status == MonitorStatus.WifiRequired }
        if (checkInChanged) for (p in profiles.profiles()) dismissCheckIns(p.id)
        if (disposed) return
        notifyChanged()
        wake.trySend(Unit)
    }

    public fun snapshotFor(id: String): ProfileAttentionSnapshot {
        val rules = rulesFor(id)
        if (!rules.enabled || id in blocked) return ProfileAttentionSnapshot(id, MonitorStatus.Disabled)
        val p = profile(id)
        if (p == null || !supportsProfile(p)) return ProfileAttentionSnapshot(id, MonitorStatus.Unavailable)
        val location = profiles.location(id)
        val stored = if (sameSource(id)) snapshots[id] else null
        val value = stored?.takeIf { it.directory == location?.directory && it.workspace == location?.workspace }
        if (!allows(id)) return stale(id, MonitorStatus.Disabled, value)
        if (!runningAllowed) return stale(id, MonitorStatus.Paused, value)
        if (value == null) return stale(id, MonitorStatus.Waiting, null)
        val now = clock.now()
        val maxAge = (if (foreground) foregroundInterval else backgroundInterval) * 2
        val checkedAt = value.checkedAt
        if (value.isCurrent && (checkedAt == null || checkedAt > now || now - checkedAt > maxAge)) {
            return stale(id, MonitorStatus.Waiting, value)
        }
        return value
    }

    public val currentPendingTotal: Int
        get() = profiles.profiles().filter { isReadable(it.id) }.sumOf { snapshotFor(it.id).pendingCount ?: 0 }

    public val unknownProfileCount: Int
        get() = profiles.profiles().count { isReadable(it.id) && !snapshotFor(it.id).isCurrent }

    /** The app says whether it is in front and whether an existing live service may run reads in the background. */
    public fun setRuntime(foreground: Boolean, backgroundAllowed: Boolean) {
        if (disposed) return
        val changed = this.foreground != foreground || this.backgroundAllowed != backgroundAllowed
        this.foreground = foreground
        this.backgroundAllowed = backgroundAllowed
        if (!runningAllowed) {
            runtimeGeneration++
            for (id in pollGenerations.keys.toList()) invalidatePoll(id)
            activeGateway?.close()
        } else if (changed) {
            if (foreground) next.clear()
            wake.trySend(Unit)
        }
        if (changed) notifyChanged()
    }

    /** An explicit opt-in (or opt-out); opting in is consent and can restore monitoring after a revoked policy. */
    public suspend fun setEnabled(id: String, enabled: Boolean): Outcome<Unit> =
        writeRules(id, rulesFor(id).copy(enabled = enabled), explicitConsent = enabled)

    public suspend fun setRules(id: String, value: MonitorNotifyRules): Outcome<Unit> = writeRules(id, value, explicitConsent = false)

    private suspend fun writeRules(id: String, value: MonitorNotifyRules, explicitConsent: Boolean): Outcome<Unit> {
        val p = profile(id)
        if (disposed || id in blocked || !isReadable(id) || p == null || !supportsProfile(p)) {
            return Failure.Unavailable(FeatureId("profileMonitor")).failed()
        }
        return writes.getOrPut(id) { Mutex() }.withLock {
            if (disposed || id in blocked) return@withLock Unit.ok()
            val before = rulesFor(id)
            if (!prefs.putString(rulesKey(id), value.toJson())) return@withLock Failure.StorageRefused().failed()
            val grantsConsent = value.enabled && (explicitConsent || !before.enabled)
            if (!value.enabled || grantsConsent || !consent.hasRecord(id)) {
                // An unrelated edit cannot revive a revoked policy; a new record fences legacy migration.
                allows(id)
                consent.set(id, grantsConsent)
            }
            epochs[id] = (epochs[id] ?: 0) + 1
            invalidatePoll(id)
            if (activeProfileId == id) activeGateway?.close()
            next.remove(id)
            snapshots.remove(id)
            if (!value.enabled || !value.notifications) {
                dismissProfile(id)
            } else if (value.checkInAfterMinutes != before.checkInAfterMinutes) {
                dismissCheckIns(id)
            }
            if (!value.enabled) forgetBusy(id)
            notifyChanged()
            if (value.enabled) wake.trySend(Unit)
            Unit.ok()
        }
    }

    /** Synchronously closes admission for a profile being deleted; [drain] then waits for its writes and reads. */
    public fun removeProfile(id: String, retainIdentity: Boolean = false) {
        if (activeProfileId == id) activeGateway?.close()
        blocked += id
        detachPolicy(id)
        epochs[id] = (epochs[id] ?: 0) + 1
        invalidatePoll(id)
        snapshots.remove(id)
        next.remove(id)
        failures.remove(id)
        if (!retainIdentity) sources.remove(id)
        busy.remove(id)
        scope.launch { dismissProfile(id) }
        notifyChanged()
    }

    /** Reopens admission after an aborted removal; old reads stay invalid. */
    public fun cancelDeletion(id: String) {
        if (disposed || !blocked.remove(id)) return
        wake.trySend(Unit)
    }

    public suspend fun drain(id: String) {
        writes[id]?.withLock { }
        polls[id]?.await()
        dismissProfile(id)
    }

    private fun storedAlertKeys(id: String): MutableSet<String> = prefs.getStringList(alertsKey(id)).orEmpty().toMutableSet()

    private suspend fun dismissKey(key: String): Boolean =
        attempt({ Failure.Unexpected() }) { withTimeoutOrNull(timeout) { alerts.dismiss(key) } ?: false }.getOrNull() ?: false

    private suspend fun dismissProfile(id: String) {
        val keys = (alertKeys.remove(id).orEmpty() + storedAlertKeys(id))
        for (key in keys) dismissKey(key) // No raw platform error enters state or logs.
        attempt({ Failure.StorageRefused() }) { prefs.remove(alertsKey(id)) }
    }

    /** Retires only [id]'s check-in reminders; request alerts stay. */
    private suspend fun dismissCheckIns(id: String) {
        val keys = alertKeys.getOrPut(id) { storedAlertKeys(id) }
        for (key in keys.filter { isCheckInAlertKey(it) }) {
            if (dismissKey(key)) keys.remove(key)
        }
        attempt({ Failure.StorageRefused() }) { prefs.putStringList(alertsKey(id), keys.take(MAX_ALERT_KEYS)) }
    }

    /** Runs one bounded read of every monitored server due now; a second call joins the read in flight. */
    public suspend fun refresh() {
        if (disposed || !runningAllowed) return
        inFlight?.let {
            it.await()
            return
        }
        val done = CompletableDeferred<Unit>()
        inFlight = done
        try {
            attempt({ Failure.Unexpected() }) { run() }.onFailedLog()
        } finally {
            inFlight = null
            done.complete(Unit)
            notifyChanged()
        }
    }

    private fun Outcome<Unit>.onFailedLog() {
        if (this is Outcome.Failed) log.w { "monitor refresh ended early: ${failure::class.simpleName}" }
    }

    /**
     * The app's polling loop: refreshes, then waits the interval or until a setting or the runtime changes. Launch it
     * once in the app's scope; it ends when the scope is cancelled.
     */
    public suspend fun runLoop() {
        while (!disposed) {
            refresh()
            withTimeoutOrNull(if (foreground) foregroundInterval else backgroundInterval) { wake.receive() }
        }
    }

    private suspend fun run() {
        val runtime = runtimeGeneration
        fun admitted(): Boolean = !disposed && runningAllowed && runtime == runtimeGeneration
        for (p in profiles.profiles().toList()) {
            if (!admitted()) return
            migrateConsent(p)
        }
        if (!admitted()) return
        val due =
            profiles.profiles().filter {
                rulesFor(it.id).enabled && allows(it.id) && isReadable(it.id) && supportsProfile(it) && it.id !in blocked
            }
        if (due.isEmpty()) return
        var wifi: Boolean? = null
        if (due.any { rulesFor(it.id).wifiOnly }) {
            wifi = attempt({ Failure.Unexpected() }) { withTimeoutOrNull(timeout) { networkWifi() } }.getOrNull()
        }
        var n = 0
        while (n < due.size && n < BATCH) {
            if (!admitted()) return
            val p = due[(cursor + n) % due.size]
            n++
            reconcileSource(p)
            if (!admitted()) return
            if (disposed || !isReadable(p.id) || !allows(p.id) || p.id in blocked) continue
            if (next[p.id]?.let { it > clock.now() } == true) continue
            if (rulesFor(p.id).wifiOnly && wifi != true) {
                snapshots[p.id] = stale(p.id, MonitorStatus.WifiRequired, snapshotFor(p.id))
                continue
            }
            val generation = beginPoll(p.id)
            val finished = CompletableDeferred<Unit>()
            polls[p.id] = finished
            try {
                poll(p, generation)
            } finally {
                polls.remove(p.id)
                finished.complete(Unit)
            }
        }
        cursor = (cursor + BATCH) % due.size
    }

    private suspend fun <T> timed(block: suspend () -> Outcome<T>): Outcome<T> =
        when (val r = attempt({ Failure.Unexpected() }) { withTimeoutOrNull(timeout) { block() } ?: Failure.TimedOut().failed() }) {
            is Outcome.Ok -> r.value
            is Outcome.Failed -> r
        }

    private suspend fun poll(p: MonitorProfile, generation: Int) {
        if (!supportsProfile(p)) return
        val id = p.id
        val epoch = epochs[id] ?: 0
        val location = profiles.location(id)
        val src = source(p)
        fun current(): Boolean =
            !disposed &&
                runningAllowed &&
                id !in blocked &&
                isReadable(id) &&
                rulesFor(id).enabled &&
                allows(id) &&
                (pollGenerations[id] ?: 0) == generation &&
                (epochs[id] ?: 0) == epoch &&
                profiles.profiles().any { it.id == id && source(it) == src } &&
                profiles.location(id)?.directory == location?.directory &&
                profiles.location(id)?.workspace == location?.workspace
        if (!current()) return
        val prior = snapshotFor(id)
        snapshots[id] = stale(id, MonitorStatus.Checking, prior)
        notifyChanged()
        var gateway: MonitorGateway? = null

        fun failed() {
            if (!current()) return
            val attempts = (failures[id] ?: 0) + 1
            failures[id] = attempts
            val base = (if (foreground) foregroundInterval else backgroundInterval).inWholeSeconds
            val seconds = (base * (1L shl attempts.coerceIn(0, 6))).coerceIn(60L, 900L)
            next[id] = clock.now() + seconds.seconds
            snapshots[id] = stale(id, MonitorStatus.Unavailable, prior)
        }

        try {
            if (!p.usable) return failed()
            if (!current()) return
            val gw = createGateway(p)
            gateway = gw
            activeGateway = gw
            activeProfileId = id
            // A Paseo server is bound to its project folder; with no project chosen yet, that folder is read.
            val directory = location?.directory ?: if (p.backend == MonitorBackend.Paseo) p.boundDirectory else null
            gw.setLocation(directory, location?.workspace)
            val page = timed { gw.sessionPage(100) }.getOrNull() ?: return failed()
            if (!current()) return
            val permissions = readPermissions(gw, ::current) ?: return failed()
            if (!current()) return
            val questions = readQuestions(gw, ::current) ?: return failed()
            if (!current()) return
            val forms = if (gw.formsSupported) timed { gw.pendingForms() }.getOrNull() ?: return failed() else emptyList()
            if (!current()) return
            val statuses = timed { gw.sessionStatuses() }.getOrNull() ?: return failed()
            if (!current()) return
            var details = MonitorAttentionDetails(prior.attention.map { it.copy(isFresh = false) }, complete = false)
            val reader = readAttention
            if (reader != null) {
                val observed =
                    attempt({ Failure.Unexpected() }) {
                        withTimeoutOrNull(timeout) { reader.read(p, gw, page.items, statuses, ::current) }
                    }.getOrNull()
                // A failed enrichment does not make its retained observations fresh.
                if (observed != null) details = merge(observed, prior, page.hasMore)
            }
            if (!current()) return
            val sessions = page.items.associateBy { it.id }
            fun row(requestId: String, sessionId: String, kind: MonitoredRequestKind): MonitoredRequest {
                val s = sessions[sessionId]
                return MonitoredRequest(requestId, sessionId, kind, s?.title, s?.directory ?: location?.directory, s?.workspace ?: location?.workspace)
            }
            val requests =
                permissions.map { row(it.id, it.sessionId, MonitoredRequestKind.Permission) } +
                    questions.map { row(it.id, it.sessionId, MonitoredRequestKind.Question) } +
                    forms.map { row(it.id, it.sessionId, MonitoredRequestKind.Form) }
            val unique = LinkedHashMap<String, MonitoredRequest>().apply { requests.forEach { put(it.identity, it) } }.values.take(MAX_REQUESTS)
            val now = clock.now()
            // Every read above succeeded: a complete observation may end or extend a busy interval.
            val observedBusy = observeBusy(id, statuses, sessions, now, location?.directory, location?.workspace)
            if (!current()) return
            failures.remove(id)
            next[id] = now + if (foreground) foregroundInterval else backgroundInterval
            snapshots[id] =
                ProfileAttentionSnapshot(
                    profileId = id,
                    status = if (requests.size <= MAX_REQUESTS) MonitorStatus.Current else MonitorStatus.Unavailable,
                    checkedAt = now,
                    directory = location?.directory,
                    workspace = location?.workspace,
                    requests = unique.toImmutableList(),
                    attention = details.items.take(MAX_REQUESTS).toImmutableList(),
                    attentionComplete = details.complete && details.items.size <= MAX_REQUESTS,
                    busyIntervals = observedBusy.toImmutableList(),
                    complete = requests.size <= MAX_REQUESTS,
                    runningCount = statuses.values.count { it != "idle" },
                    nextCheckAt = next[id],
                )
            if (!foreground && backgroundAllowed && current()) {
                val requestsAllowed = notificationPolicyCurrent(id)
                val checkInsAllowed = checkInPolicyCurrent(id)
                if (requestsAllowed || checkInsAllowed) {
                    val due = buildList {
                        if (requestsAllowed) addAll(unique)
                        if (checkInsAllowed) snapshots[id]!!.dueCheckIns(rulesFor(id)).forEach { add(it.toRequest()) }
                    }
                    publishAlerts(id, due, ::current) { notificationPolicyCurrent(id) || checkInPolicyCurrent(id) }
                }
            }
        } finally {
            gateway?.close()
            if (activeGateway === gateway) {
                activeGateway = null
                activeProfileId = null
            }
        }
    }

    private fun merge(observed: MonitorAttentionDetails, prior: ProfileAttentionSnapshot, hasMore: Boolean): MonitorAttentionDetails {
        val complete = observed.complete && !hasMore
        if (complete) return MonitorAttentionDetails(observed.items, complete = true)
        val kept =
            prior.attention.filter { old ->
                observed.items.none { it.identity == old.identity } &&
                    !(old.kind == MonitorAttentionKind.FailedRun && old.taskId == null && old.runId == null && old.sessionId in observed.checkedSessionIds) &&
                    !((old.kind == MonitorAttentionKind.TeamGate || old.taskId != null || old.runId != null) && observed.teamComplete)
            }.map { it.copy(isFresh = false) }
        return MonitorAttentionDetails(observed.items + kept, complete = false)
    }

    private suspend fun readPermissions(gw: MonitorGateway, current: () -> Boolean): List<MonitorPending>? =
        mergeReads(current, { gw.pendingPermissions() }, { gw.pendingPermissionsV2() })

    private suspend fun readQuestions(gw: MonitorGateway, current: () -> Boolean): List<MonitorPending>? =
        mergeReads(current, { gw.listQuestions() }, { gw.pendingQuestionsV2() })

    /** Legacy then modern read; either may fail alone, both failing is "unavailable"; ids de-duplicate. */
    internal suspend fun mergeReads(
        current: () -> Boolean,
        legacy: suspend () -> Outcome<List<MonitorPending>>,
        modern: suspend () -> Outcome<List<MonitorPending>>,
    ): List<MonitorPending>? {
        if (!current()) return null
        val a = timed(legacy).getOrNull()
        if (!current()) return null
        val b = timed(modern).getOrNull()
        if (a == null && b == null) return null
        return LinkedHashMap<String, MonitorPending>().apply { (a.orEmpty() + b.orEmpty()).forEach { put(it.id, it) } }.values.toList()
    }

    /**
     * Reconciles posted alerts with [requests]: anything posted that is no longer listed is dismissed, anything new is
     * posted once. A check-in key names its busy interval, so an ended interval retires its reminder.
     */
    private suspend fun publishAlerts(id: String, requests: List<MonitoredRequest>, current: () -> Boolean, policy: () -> Boolean) {
        if (!current() || !policy()) return dismissProfile(id)
        val keys = alertKeys.getOrPut(id) { storedAlertKeys(id) }
        val valid = requests.map { alertKey(id, it) }.toSet()
        for (key in (keys - valid).toList()) {
            if (!current() || !policy()) return dismissProfile(id)
            if (!dismissKey(key)) continue
            if (!current() || !policy()) return dismissProfile(id)
            keys.remove(key)
        }
        fun mayDispatch(): Boolean = current() && policy() && !foreground && backgroundAllowed
        var dispatched = 0
        for (request in requests) {
            if (!mayDispatch()) return
            val key = alertKey(id, request)
            if (key in keys) continue
            if (request.kind == MonitoredRequestKind.CheckIn && busyIntervals(id)[request.sessionId]?.reminderClaimed == true) continue
            if (dispatched >= BATCH) break
            dispatched++
            val token = attempt({ Failure.StorageRefused() }) { saveRoute(id, request) }
            if (token !is Outcome.Ok) return
            val saved = token.value ?: return
            if (!mayDispatch()) return
            if (request.kind == MonitoredRequestKind.CheckIn && !claimCheckIn(id, request, current)) continue
            // Runtime admission can change while either write is held: keep a recorded claim, never dispatch then.
            if (!mayDispatch()) return
            val delivered =
                attempt({ Failure.Unexpected() }) { withTimeoutOrNull(timeout) { alerts.alert(id, request, key, saved) } ?: false }
            if (delivered !is Outcome.Ok) return
            if (!mayDispatch()) {
                if (delivered.value) dismissKey(key)
                return
            }
            if (delivered.value) keys += key
        }
        if (current() && policy()) prefs.putStringList(alertsKey(id), keys.take(MAX_ALERT_KEYS))
    }

    private fun quietNow(rules: MonitorNotifyRules): Boolean = rules.quietAt(minuteOfDay(clock.now()))

    private fun notificationPolicyCurrent(id: String): Boolean {
        if (disposed || !runningAllowed || id in blocked || !allows(id)) return false
        val rules = rulesFor(id)
        return rules.enabled && rules.notifications && sharedRules.requestsAllowed && !quietNow(rules) && (alertsAllowed?.invoke(id) ?: true)
    }

    /** Check-ins follow every request-alert policy except [alertsAllowed]; the rule's duration must be set. */
    private fun checkInPolicyCurrent(id: String): Boolean {
        if (disposed || !runningAllowed || id in blocked || !allows(id)) return false
        val rules = rulesFor(id)
        return rules.enabled && rules.notifications && rules.checkInAfterMinutes != null && !quietNow(rules)
    }

    public fun dispose() {
        disposed = true
        for (id in pollGenerations.keys.toList()) invalidatePoll(id)
        activeGateway?.close()
        for (id in policyRemovers.keys.toList()) detachPolicy(id)
        wake.close()
    }

    public companion object {
        public val BUSY_OBSERVATION_GAP: Duration = 20.minutes
        internal const val BATCH = 8
        internal const val MAX_REQUESTS = 256
        internal const val MAX_ALERT_KEYS = 256
        internal const val MAX_ROUTES = 32
        private val log = AppLog.tagged("ProfileMonitor")

        public fun rulesKey(id: String): String = "oc.notifyRules.$id"

        public fun alertsKey(id: String): String = "oc.monitorAlerts.$id"

        public fun routesKey(id: String): String = "oc.monitorRoutes.$id"

        public fun busyIntervalsKey(id: String): String = "oc.monitorBusy.$id"

        /** Every per-profile key the monitor writes; the profile deletion sweep removes them (ProfileScope). */
        public fun profileScopedKeys(id: String): List<String> = listOf(rulesKey(id), alertsKey(id), routesKey(id), busyIntervalsKey(id))

        /** Loopback servers use no network, so Wi-Fi rules do not apply; they still need consent. */
        public fun isOnThisPhone(p: MonitorProfile): Boolean = hostOf(p.baseUrl) in setOf("127.0.0.1", "localhost", "::1", "[::1]")

        /** Durable route scope deliberately excludes credentials. */
        public fun routeSourceIdentity(p: MonitorProfile): String =
            DartJson.sha256Hex(JsonArray(listOf(p.baseUrl, p.username, p.flavor).map { JsonPrimitive(it) }).toString())

        public fun alertKey(id: String, r: MonitoredRequest): String = "monitor:${encodeComponent(id)}:${encodeComponent(r.identity)}"

        public fun isCheckInAlertKey(key: String): Boolean = key.contains(":${MonitoredRequestKind.CheckIn.wire}%3A")

        internal fun hostOf(url: String): String? {
            val rest = url.substringAfter("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() } ?: return null
            val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
            if (authority.startsWith("[")) return authority.substringBefore(']').plus("]").lowercase().let { if (it == "[::1]") "::1" else it }
            return authority.substringBefore(':').lowercase()
        }

        /** Dart `Uri.encodeComponent`: everything but unreserved marks is percent-encoded as UTF-8. */
        internal fun encodeComponent(value: String): String =
            buildString {
                for (b in value.encodeToByteArray()) {
                    val c = b.toInt() and 0xff
                    val ch = c.toChar()
                    if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-_.!~*'()") append(ch)
                    else append('%').append(HEX[c shr 4]).append(HEX[c and 0xf])
                }
            }

        private const val HEX = "0123456789ABCDEF"
    }
}
