package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

public enum class QuotaMonitorStatus { Disabled, Waiting, Checking, Current, Paused, WifiRequired, Unavailable, SourceChanged }

public data class QuotaMonitorTarget(val profileId: String, val provider: QuotaProvider)

/** One source's consent and alert rule, stored under `oc.quotaMonitor.<profileId>` (hashes and numbers only). */
public data class QuotaMonitorRules(
    val source: String,
    val account: String,
    val token: String,
    val notifications: Boolean = false,
    val wifiOnly: Boolean = false,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
    val alerted: Map<String, String> = emptyMap(),
    val threshold: Double = 100.0,
) {
    public fun quietAt(minuteOfDay: Int): Boolean = quietAt(quietStart, quietEnd, minuteOfDay)

    public fun toJson(): Map<String, Any?> =
        linkedMapOf(
            "source" to source,
            "account" to account,
            "token" to token,
            "notifications" to notifications,
            "wifiOnly" to wifiOnly,
            "quietStart" to quietStart,
            "quietEnd" to quietEnd,
            "alerted" to alerted,
            "threshold" to threshold,
        )

    override fun toString(): String = "QuotaMonitorRules(threshold=$threshold, notifications=$notifications)"

    public companion object {
        private val hash = Regex("^[a-f0-9]{64}$")

        /** Null for anything that is not a valid stored rule. */
        public fun fromJson(value: Map<*, *>): QuotaMonitorRules? {
            val threshold = (value["threshold"] ?: 100L) as? Number ?: return null
            val t = threshold.toDouble()
            if (!t.isFinite() || t <= 0 || t > 100) return null
            fun isHash(v: Any?) = v is String && hash.matches(v)
            fun minute(v: Any?): Boolean = v == null || (v is Long && v in 0..1439)
            if (!isHash(value["source"]) || !isHash(value["account"]) || !isHash(value["token"]) ||
                value["notifications"] !is Boolean || value["wifiOnly"] !is Boolean ||
                !minute(value["quietStart"]) || !minute(value["quietEnd"])
            ) {
                return null
            }
            val alertedRaw = value["alerted"] as? Map<*, *> ?: return null
            if (alertedRaw.size > 64 || alertedRaw.any { !isHash(it.key) || !isHash(it.value) }) return null
            return QuotaMonitorRules(
                source = value["source"] as String,
                account = value["account"] as String,
                token = value["token"] as String,
                notifications = value["notifications"] as Boolean,
                wifiOnly = value["wifiOnly"] as Boolean,
                quietStart = (value["quietStart"] as Long?)?.toInt(),
                quietEnd = (value["quietEnd"] as Long?)?.toInt(),
                alerted = alertedRaw.entries.associate { it.key as String to it.value as String },
                threshold = t,
            )
        }
    }
}

public data class QuotaMonitorObservation(
    val status: QuotaMonitorStatus,
    val snapshot: ProviderQuotaSnapshot? = null,
    val checkedAt: Long? = null,
)

/** The quota-alert notification sink. A [Outcome.Failed] or `false` is not a delivery: the claim is rolled back. */
public interface QuotaAlertSink {
    public suspend fun alert(profileId: String, key: String, token: String): Outcome<Boolean>

    public suspend fun dismiss(key: String)
}

/**
 * Explicit collector consent, independent of page consent and session monitoring (port of ProviderQuotaMonitor). It
 * never starts a foreground service, connects OpenCode, or persists measured quotas. One dedicated bounded read runs at
 * a time, fairly rotated (at most three sources per cycle).
 *
 * [sharedRules] is null until the one-time notification migration has run; until then each source's own legacy
 * record answers for alerts, Wi-Fi only and quiet hours.
 */
public class ProviderQuotaMonitor(
    private val prefs: PlansPrefs,
    private val profiles: () -> List<QuotaProfile>,
    private val isReadable: (String) -> Boolean,
    private val createGateway: (QuotaProfile, QuotaProvider) -> ProviderQuotaGateway,
    private val wifi: WifiState,
    private val sink: QuotaAlertSink,
    private val sharedRules: () -> SharedNotifyRules?,
    private val clock: PlansClock,
    private val secureRandom: (Int) -> ByteArray,
    private val scope: CoroutineScope,
    private val widget: UsageWidgetSnapshot? = null,
    private val foregroundIntervalMs: Long = 5 * 60_000L,
    private val backgroundIntervalMs: Long = 15 * 60_000L,
    private val timeoutMs: Long = 10_000L,
) {
    public companion object {
        public fun key(profileId: String): String = "oc.quotaMonitor.$profileId"

        private fun id(profileId: String, provider: QuotaProvider) = "$profileId:${provider.id}"

        internal fun alertKey(profileId: String, provider: QuotaProvider) = "quota:$profileId:${provider.id}"

        private fun hash(value: Any?): String = DartJson.sha256Hex(DartJson.encode(value))

        private fun source(profile: QuotaProfile) = hash(listOf(profile.baseUrl, profile.username))
    }

    private val observations = HashMap<String, QuotaMonitorObservation>()
    private val epochs = HashMap<String, Int>()
    private val blocked = HashSet<String>()
    private val pausedSources = HashSet<String>()
    private val writes = HashMap<String, Deferred<Boolean>>()

    // Credential identity is process-local and never persisted.
    private val credentialSources = HashMap<String, String>()
    private val retiredCredentials = HashSet<String>()
    private val lane = Mutex()
    private var running: Deferred<Unit>? = null
    private var gateway: ProviderQuotaGateway? = null
    private var readingProfile: String? = null
    private var timer: Job? = null
    private var foreground = true
    private var backgroundAllowed = false
    private var disposed = false
    private var runtimeEpoch = 0
    private var cursor = 0
    private val _revision = MutableStateFlow(0L)

    /** Bumps whenever observations or rules change (the Flutter ChangeNotifier). */
    public val revision: StateFlow<Long> = _revision.asStateFlow()

    private fun notifyChanged() {
        _revision.value += 1
    }

    private fun identity(profile: QuotaProfile) = listOf(profile.baseUrl, profile.username, profile.credentialStamp)

    private fun credentialIdentity(profile: QuotaProfile): String = hash(identity(profile))

    private fun reconcileCredential(profile: QuotaProfile) {
        val id = profile.id
        val identity = credentialIdentity(profile)
        val prior = credentialSources[id]
        if (prior == identity) return
        credentialSources[id] = identity
        if (prior == null) return
        epochs[id] = (epochs[id] ?: 0) + 1
        if (readingProfile == id) closeGateway()
        for (provider in QuotaProvider.entries) {
            retiredCredentials += id(id, provider)
            observations.remove(id(id, provider))
            scope.launch { dismiss(id, provider) }
            scope.launch { widget?.removeProvider(id, provider) }
        }
    }

    public val runningAllowed: Boolean get() = !disposed && (foreground || backgroundAllowed)
    public val refreshing: Boolean get() = running != null

    private fun profile(id: String) = profiles().firstOrNull { it.id == id }

    private fun readable(id: String) = !disposed && id !in blocked && isReadable(id)

    public fun alertsFor(rules: QuotaMonitorRules): Boolean = sharedRules()?.quotaAlerts ?: rules.notifications

    public fun wifiOnlyFor(rules: QuotaMonitorRules): Boolean = sharedRules()?.wifiOnly ?: rules.wifiOnly

    public fun quietFor(rules: QuotaMonitorRules, now: Long): Boolean {
        val minute = clock.minuteOfDay(now)
        return sharedRules()?.quietAt(minute) ?: rules.quietAt(minute)
    }

    /** The shared rules were edited: sources waiting for Wi-Fi, or alerts no longer wanted, must not wait a cycle. */
    public suspend fun sharedRulesChanged() {
        if (disposed) return
        if (sharedRules()?.quotaAlerts == false) for (t in sources) dismiss(t.profileId, t.provider)
        if (disposed) return
        notifyChanged()
        refresh()
    }

    public fun rulesFor(id: String, provider: QuotaProvider): QuotaMonitorRules? {
        if (!readable(id) || !quotaCollectionAvailable(provider)) return null
        val raw = prefs.getString(key(id)) ?: return null
        if (raw.length > 65536) return null
        val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        if (data["version"] != 1L) return null
        val rule = (data["rules"] as? Map<*, *>)?.get(provider.id) as? Map<*, *> ?: return null
        return QuotaMonitorRules.fromJson(rule)
    }

    public val sources: List<QuotaMonitorTarget>
        get() {
            val result = mutableListOf<QuotaMonitorTarget>()
            for (profile in profiles()) {
                reconcileCredential(profile)
                for (provider in QuotaProvider.entries) {
                    if (rulesFor(profile.id, provider) != null) result += QuotaMonitorTarget(profile.id, provider)
                }
            }
            return result
        }

    private fun fresh(snapshot: ProviderQuotaSnapshot): Boolean {
        val now = clock.nowMs()
        return snapshot.canShowWindows && snapshot.freshness == QuotaFreshness.Fresh && !snapshot.isStale(now) &&
            now >= snapshot.fetchedAt && snapshot.windows.none { it.resetsAt != null && now >= it.resetsAt }
    }

    public fun observationFor(id: String, provider: QuotaProvider): QuotaMonitorObservation {
        val profile = profile(id)
        if (profile != null) reconcileCredential(profile)
        val rules = rulesFor(id, provider) ?: return QuotaMonitorObservation(QuotaMonitorStatus.Disabled)
        if (id(id, provider) in pausedSources) return QuotaMonitorObservation(QuotaMonitorStatus.Paused)
        if (profile == null || source(profile) != rules.source || id(id, provider) in retiredCredentials) {
            return QuotaMonitorObservation(QuotaMonitorStatus.SourceChanged)
        }
        if (!runningAllowed) return QuotaMonitorObservation(QuotaMonitorStatus.Paused)
        val value = observations[id(id, provider)]
        val snapshot = value?.snapshot
        if (snapshot != null && (!fresh(snapshot) || snapshot.account.ref != rules.account)) {
            return QuotaMonitorObservation(QuotaMonitorStatus.Waiting, checkedAt = value.checkedAt)
        }
        return value ?: QuotaMonitorObservation(QuotaMonitorStatus.Waiting)
    }

    /**
     * Only the exact source just reviewed by the person can be enrolled. Consent never silently follows an account or
     * server credential-owner change.
     */
    public suspend fun enroll(
        id: String,
        snapshot: ProviderQuotaSnapshot,
        notifications: Boolean,
        wifiOnly: Boolean = false,
        threshold: Double = 100.0,
        quietStart: Int? = null,
        quietEnd: Int? = null,
    ): Boolean {
        val profile = profile(id)
        val ref = snapshot.account.ref
        if (!readable(id) || profile == null || !quotaCollectionAvailable(snapshot.provider) || !fresh(snapshot) ||
            ref == null
        ) {
            return false
        }
        val rules =
            QuotaMonitorRules(
                source = source(profile),
                account = ref,
                token = secureRandom(32).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') },
                notifications = notifications,
                wifiOnly = wifiOnly,
                quietStart = quietStart,
                quietEnd = quietEnd,
                threshold = threshold,
            )
        reconcileCredential(profile)
        return save(id, snapshot.provider, rules, reauthorize = true)
    }

    public suspend fun setPolicy(
        id: String,
        provider: QuotaProvider,
        notifications: Boolean,
        wifiOnly: Boolean,
        threshold: Double? = null,
        quietStart: Int? = null,
        quietEnd: Int? = null,
    ): Boolean {
        val old = rulesFor(id, provider) ?: return false
        return save(
            id,
            provider,
            old.copy(
                notifications = notifications,
                wifiOnly = wifiOnly,
                quietStart = quietStart,
                quietEnd = quietEnd,
                threshold = threshold ?: old.threshold,
            ),
        )
    }

    public suspend fun disable(id: String, provider: QuotaProvider): Boolean = save(id, provider, null, reauthorize = true)

    private suspend fun save(
        id: String,
        provider: QuotaProvider,
        rules: QuotaMonitorRules?,
        reauthorize: Boolean = false,
    ): Boolean {
        val profile = profile(id)
        if (profile != null) reconcileCredential(profile)
        val targetId = id(id, provider)
        if (!readable(id) || (targetId in retiredCredentials && !reauthorize)) return false
        if (rules == null) pausedSources += targetId
        if (rules != null && QuotaMonitorRules.fromJson(DartJson.decode(DartJson.encode(rules.toJson())) as Map<*, *>) == null) {
            return false
        }
        val epoch = (epochs[id] ?: 0) + 1
        epochs[id] = epoch
        val credential = profile?.let { credentialIdentity(it) }

        fun currentCredential(): Boolean {
            val current = profile(id)
            return current != null && credential != null && credentialIdentity(current) == credential &&
                epochs[id] == epoch && readable(id)
        }

        fun currentState() = readable(id) && epochs[id] == epoch && (credential == null || currentCredential())
        if (readingProfile == id) closeGateway()
        observations.remove(targetId)
        val operation =
            scope.async(start = CoroutineStart.UNDISPATCHED) {
                val result = update(id, mapOf(provider.id to rules?.toJson()), ::currentState)
                dismiss(id, provider)
                if (result == null) return@async false
                if (rules == null) widget?.removeProvider(id, provider)
                if (reauthorize && currentCredential()) retiredCredentials -= targetId
                pausedSources -= targetId
                if (!disposed) {
                    notifyChanged()
                    schedule()
                }
                true
            }
        writes[id] = operation
        return operation.await()
    }

    /** The per-profile rule blob, merged under one lane (port of BudgetPersistence.update). */
    private suspend fun update(id: String, changes: Map<String, Any?>, isCurrent: () -> Boolean): Map<String, Any?>? =
        lane.withLock {
            if (!isCurrent()) return@withLock null
            val raw = prefs.getString(key(id))
            val rules = LinkedHashMap<String, Any?>()
            if (raw != null) {
                if (raw.length > 65536) return@withLock null
                val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return@withLock null
                if (data["version"] != 1L) return@withLock null
                val stored = data["rules"] as? Map<*, *> ?: return@withLock null
                stored.forEach { (k, v) -> rules[k.toString()] = v }
            }
            for ((k, v) in changes) if (v == null) rules.remove(k) else rules[k] = v
            if (rules.size > 64) return@withLock null
            val success =
                if (rules.isEmpty()) {
                    prefs.remove(key(id))
                } else {
                    prefs.setString(key(id), DartJson.encode(mapOf("version" to 1, "rules" to rules)))
                }
            if (!isReadable(id)) {
                prefs.remove(key(id))
                return@withLock null
            }
            if (success) rules else null
        }

    /** The source a tapped quota notification names, or null when consent, identity or the source changed. */
    public fun routeForToken(id: String, token: String): QuotaMonitorTarget? {
        if (!readable(id)) return null
        profile(id)?.let { reconcileCredential(it) }
        for (provider in QuotaProvider.entries) {
            val rules = rulesFor(id, provider)
            val profile = profile(id)
            if (rules != null && profile != null && alertsFor(rules) && id(id, provider) !in retiredCredentials &&
                id(id, provider) !in pausedSources &&
                observations[id(id, provider)]?.status != QuotaMonitorStatus.SourceChanged &&
                rules.token == token && rules.source == source(profile)
            ) {
                return QuotaMonitorTarget(id, provider)
            }
        }
        return null
    }

    /**
     * Re-reads the source a tap names. A historical alert remains reviewable during a temporary outage (the review
     * shows unavailable, never an expired measurement).
     */
    public suspend fun resolveRoute(id: String, token: String): QuotaMonitorTarget? {
        val target = routeForToken(id, token) ?: return null
        refreshSource(target)
        if (routeForToken(id, token) == null) return null
        dismiss(id, target.provider)
        return target
    }

    public suspend fun refreshSource(target: QuotaMonitorTarget) {
        while (true) {
            val r = running ?: break
            r.await()
        }
        if (!runningAllowed) return
        runExclusive { read(target.profileId, target.provider) }.await()
    }

    private fun runExclusive(block: suspend () -> Unit): Deferred<Unit> {
        val operation = scope.async { block() }
        running = operation
        operation.invokeOnCompletion {
            if (running === operation) running = null
            if (!disposed) {
                schedule()
                notifyChanged()
            }
        }
        return operation
    }

    private fun closeGateway() {
        gateway?.close()
    }

    public fun start() {
        if (!disposed && sources.isNotEmpty()) {
            schedule()
            startRefresh()
        }
    }

    public fun setRuntime(foreground: Boolean, backgroundAllowed: Boolean) {
        if (disposed) return
        if (this.foreground == foreground && this.backgroundAllowed == backgroundAllowed) return
        this.foreground = foreground
        this.backgroundAllowed = backgroundAllowed
        runtimeEpoch++
        closeGateway()
        observations.clear()
        timer?.cancel()
        timer = null
        if (runningAllowed) {
            schedule()
            startRefresh()
        } else {
            for (t in sources) scope.launch { dismiss(t.profileId, t.provider) }
        }
        notifyChanged()
    }

    private fun schedule() {
        timer?.cancel()
        timer = null
        if (!runningAllowed || sources.isEmpty()) return
        val wait = if (foreground) foregroundIntervalMs else backgroundIntervalMs
        timer =
            scope.launch {
                delay(wait)
                timer = null
                refresh()
            }
    }

    /** One cycle over at most three sources, or joins the cycle already running. */
    public suspend fun refresh() {
        startRefresh()?.await()
    }

    /** Starts a cycle now (or returns the one running) without waiting for it; null when nothing may run. */
    public fun startRefresh(): Deferred<Unit>? {
        if (!runningAllowed || sources.isEmpty()) return null
        return running ?: runExclusive { cycle() }
    }

    private suspend fun cycle() {
        val list = sources
        if (list.isEmpty()) return
        // At most three sources per cycle; never starve sources beyond this bound.
        val count = minOf(3, list.size)
        val start = cursor % list.size
        cursor = (start + count) % list.size
        var i = 0
        while (i < count && runningAllowed) {
            val target = list[(start + i) % list.size]
            read(target.profileId, target.provider)
            i++
        }
    }

    private suspend fun read(id: String, provider: QuotaProvider) {
        val profile = profile(id)
        if (profile != null) reconcileCredential(profile)
        val targetId = id(id, provider)
        val rules = rulesFor(id, provider)
        if (rules == null || profile == null || targetId in retiredCredentials || targetId in pausedSources) return
        val epoch = epochs[id] ?: 0
        val runtime = runtimeEpoch

        fun current(): Boolean {
            val p = profile(id)
            val r = rulesFor(id, provider)
            return runningAllowed && readable(id) && (epochs[id] ?: 0) == epoch && runtimeEpoch == runtime &&
                p?.baseUrl == profile.baseUrl && p.username == profile.username &&
                p.credentialStamp == profile.credentialStamp && targetId !in retiredCredentials &&
                r?.token == rules.token && r.source == source(profile)
        }

        fun publish(status: QuotaMonitorStatus, snapshot: ProviderQuotaSnapshot? = null) {
            if (!current()) return
            observations[targetId] = QuotaMonitorObservation(status, snapshot, clock.nowMs())
            notifyChanged()
        }

        if (source(profile) != rules.source) {
            dismiss(id, provider)
            widget?.removeProvider(id, provider)
            return
        }
        var opened: ProviderQuotaGateway? = null
        try {
            if (wifiOnlyFor(rules)) {
                val onWifi = guarded { withTimeoutOrNull(timeoutMs) { wifi.onWifi() } }
                if (!current()) return
                if (onWifi != true) {
                    publish(QuotaMonitorStatus.WifiRequired)
                    dismiss(id, provider)
                    return
                }
            }
            if (!current()) return
            publish(QuotaMonitorStatus.Checking)
            opened = createGateway(profile.copy(name = ""), provider)
            gateway = opened
            readingProfile = id
            val result: Outcome<ProviderQuotaSnapshot> =
                withTimeoutOrNull(timeoutMs) { guarded { opened.readSnapshot() } ?: Outcome.Failed(Failure.Unexpected()) }
                    ?: Outcome.Failed(Failure.TimedOut())
            if (!current()) return
            val snapshot =
                when (result) {
                    is Outcome.Failed -> {
                        publish(QuotaMonitorStatus.Unavailable)
                        if (quotaFailureKind(result.failure) == QuotaFailureKind.CollectorAuth) {
                            widget?.markAuthNeeded(profile.id, profile.name, provider)
                        }
                        return
                    }
                    is Outcome.Ok -> result.value
                }
            if (snapshot.provider == provider && snapshot.status == ProviderQuotaStatus.Unconfigured) {
                // Signed out where it is read: nothing to compare, say how to fix it.
                publish(QuotaMonitorStatus.Unavailable)
                widget?.markNotSignedIn(profile.id, profile.name, provider.id)
                return
            }
            if (snapshot.provider != provider || snapshot.account.ref != rules.account) {
                publish(QuotaMonitorStatus.SourceChanged)
                dismiss(id, provider)
                widget?.removeProvider(id, provider)
                return
            }
            if (!fresh(snapshot)) {
                publish(QuotaMonitorStatus.Unavailable)
                if (snapshot.status == ProviderQuotaStatus.AuthRequired) {
                    widget?.markAuthNeeded(profile.id, profile.name, provider)
                }
                return
            }
            publish(QuotaMonitorStatus.Current, snapshot)
            if (current()) widget?.record(profile.id, profile.name, snapshot)
            deliver(id, provider, rules, snapshot, ::current, ::publish)
        } finally {
            opened?.close()
            if (gateway === opened) {
                gateway = null
                readingProfile = null
            }
        }
    }

    private suspend fun deliver(
        id: String,
        provider: QuotaProvider,
        rules: QuotaMonitorRules,
        snapshot: ProviderQuotaSnapshot,
        current: () -> Boolean,
        publish: (QuotaMonitorStatus, ProviderQuotaSnapshot?) -> Unit,
    ) {
        val markers = LinkedHashMap(rules.alerted)
        var changed = false
        var reached = false
        for (window in snapshot.windows) {
            val used = window.usedPercent ?: continue
            if (used < rules.threshold) continue
            reached = true
            val windowId = hash(listOf(window.id, window.durationSeconds))
            val marker = hash(listOf(windowId, window.resetsAt, rules.threshold))
            if (markers[windowId] == marker) continue
            markers[windowId] = marker
            changed = true
        }
        if (!reached) {
            // Missing windows do not prove recovery of a previously alerted limit.
            val recovered =
                snapshot.windows
                    .filter { it.usedPercent != null && it.usedPercent < rules.threshold }
                    .map { hash(listOf(it.id, it.durationSeconds)) }
                    .toSet()
            if (rules.alerted.keys.all { it in recovered }) dismiss(id, provider)
            return
        }
        // Foreground use elsewhere and quiet hours suppress new delivery. They do not acknowledge a historical alert.
        if (foreground || !alertsFor(rules) || quietFor(rules, clock.nowMs())) return
        if (!changed || !current()) return
        val claimed = rules.copy(alerted = markers)
        val durable = update(id, mapOf(provider.id to claimed.toJson()), current)
        if (durable == null || !current()) return
        if (!fresh(snapshot)) {
            update(id, mapOf(provider.id to rules.toJson()), current)
            return
        }
        val shown = guarded { sink.alert(id, alertKey(id, provider), rules.token) } ?: Outcome.Failed(Failure.Unexpected())
        if (shown is Outcome.Failed) {
            // The marker is a claim, not proof of delivery. Roll it back so a later refresh can retry.
            if (current() && fresh(snapshot)) update(id, mapOf(provider.id to rules.toJson()), current)
            publish(QuotaMonitorStatus.Unavailable, null)
            return
        }
        if (!current() || !fresh(snapshot)) {
            dismiss(id, provider)
            return
        }
        if ((shown as Outcome.Ok).value.not()) {
            // Retry on a later scheduled check; an unavailable native sink is not a delivery.
            update(id, mapOf(provider.id to rules.toJson()), current)
        }
    }

    private suspend fun dismiss(id: String, provider: QuotaProvider) {
        guarded { sink.dismiss(alertKey(id, provider)) }
    }

    /** Removal of a saved server. [retainIdentity] keeps credential retirement for a reversible (undoable) removal. */
    public fun removeProfile(id: String, retainIdentity: Boolean = false) {
        blocked += id
        epochs[id] = (epochs[id] ?: 0) + 1
        if (readingProfile == id) closeGateway()
        for (provider in QuotaProvider.entries) {
            observations.remove(id(id, provider))
            scope.launch { dismiss(id, provider) }
        }
        widget?.let { w -> scope.launch { w.clearForProfile(id) } }
        if (!retainIdentity) {
            credentialSources.remove(id)
            for (provider in QuotaProvider.entries) retiredCredentials -= id(id, provider)
        }
        if (!disposed) notifyChanged()
    }

    /** Reopens admission after an aborted removal; old callbacks stay invalid. */
    public fun cancelDeletion(id: String) {
        if (disposed || !blocked.remove(id)) return
        schedule()
    }

    public suspend fun drain(id: String) {
        writes[id]?.await()
        running?.await()
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        runtimeEpoch++
        timer?.cancel()
        closeGateway()
        observations.clear()
        credentialSources.clear()
        retiredCredentials.clear()
    }
}

/** Runs a port call whose failure must not escape (a throwing adapter); cancellation still does. */
internal suspend inline fun <T> guarded(block: () -> T): T? = attempt({ Failure.Unexpected() }, block).getOrNull()
