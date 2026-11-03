package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** What a [UsageSource] is asked to read. */
public data class UsageReadContext(
    val connections: List<UsageConnection>,
    val now: Long,
    val force: Boolean = false,
    /** The app is in the background: readers that cost a request keep to their slower cadence. */
    val background: Boolean = false,
    /** The last reading kept for a connection and provider, if any ("used before" vs "never used"). */
    val lastReading: ((connectionId: String, providerId: String) -> UsageReading?)? = null,
    /** The provider ids the last refresh saw signed in on a connection. */
    val signedIn: ((connectionId: String) -> Set<String>)? = null,
)

/** What one source learned. */
public data class UsageSourceResult(
    val readings: List<UsageReading> = emptyList(),
    val signIns: List<UsageSignIn> = emptyList(),
    val failures: List<UsageFailure> = emptyList(),
    /** Connections the source tried and could not reach at all. */
    val unreached: Set<String> = emptySet(),
    /** Connections whose [signIns] list is complete: a provider missing from it is signed out there. */
    val authoritativeSignIns: Set<String> = emptySet(),
)

/**
 * The readings port: one reader (collector, Paseo, Codex account, computer, OpenCode sign-ins). Readers live in another
 * module; they never connect to or switch the active server. A reader that fails returns an empty result.
 */
public fun interface UsageSource {
    public suspend fun read(context: UsageReadContext): UsageSourceResult
}

/** A rule the old per-source quota monitor already holds. */
public data class LegacyQuotaRule(
    val connectionId: String,
    val providerId: String,
    val accountRef: String,
    val threshold: Double,
)

/** One collector reading that should be watched at [threshold]. */
public data class QuotaWatch(val connectionId: String, val providerId: String, val threshold: Double)

/** The bridge to [ProviderQuotaMonitor]: the controller decides what is watched and at what level. */
public interface UsageAlertBridge {
    public fun legacyRules(): List<LegacyQuotaRule>

    public suspend fun reconcile(wanted: List<QuotaWatch>, alertsOn: Boolean)

    /** Whether the monitor already alerts for this reading, so the second delivery path does not repeat it. */
    public fun covers(connectionId: String, providerId: String): Boolean = false
}

/**
 * Subscriptions across every connection, with the alert state that goes with them (port of
 * UsageSubscriptionsController). StateFlow out, suspend commands in.
 */
public class UsageSubscriptionsController(
    private val prefs: PlansPrefs,
    private val connections: () -> List<UsageConnection>,
    private val sources: List<UsageSource>,
    private val sharedRules: () -> SharedNotifyRules,
    private val setQuotaAlerts: suspend (Boolean) -> Unit,
    private val clock: PlansClock,
    private val scope: CoroutineScope,
    private val alertBridge: UsageAlertBridge? = null,
    private val subscriptionAlerts: SubscriptionAlertDelivery? = null,
    private val widget: UsageWidgetSnapshot? = null,
    private val foregroundIntervalMs: Long = 5 * 60_000L,
    private val backgroundIntervalMs: Long = 15 * 60_000L,
    private val sourceTimeoutMs: Long = 25_000L,
) {
    public companion object {
        public const val ALERTS_KEY: String = "oc.usageAlerts.v1"

        public fun lastKey(connectionId: String): String = "oc.usageLast.$connectionId"

        /** The default level every readable subscription is watched at (D4). */
        public const val DEFAULT_ALERT_THRESHOLD: Double = 80.0
    }

    private val cache = HashMap<String, MutableMap<String, UsageReading>>()
    private val signIns = HashMap<String, MutableList<UsageSignIn>>()
    private val authoritative = LinkedHashSet<String>()
    private val offline = LinkedHashSet<String>()
    private val _snapshot = MutableStateFlow(UsageSnapshot())
    public val snapshotFlow: StateFlow<UsageSnapshot> = _snapshot.asStateFlow()
    public val snapshot: UsageSnapshot get() = _snapshot.value
    private var running: Deferred<Unit>? = null
    private var timer: Job? = null
    private var foreground = true
    private var backgroundAllowed = false
    private var disposed = false
    private var refreshedAt: Long? = null
    private var loaded = false
    private var epoch = 0
    private var default = DEFAULT_ALERT_THRESHOLD
    private var resetDefault = false
    private val overrides = LinkedHashMap<String, UsageAlertOverride>()
    private var migrated = false

    public var widgetSettings: UsageWidgetSettings = UsageWidgetSettings.load(prefs)
        private set

    init {
        loadAlerts()
        for (c in connections()) loadCache(c.id)
        rebuild(loaded = false)
    }

    public val refreshing: Boolean get() = running != null
    public val runningAllowed: Boolean get() = !disposed && (foreground || backgroundAllowed)

    public fun subscription(key: String): UsageSubscription? = snapshot.subscriptions.firstOrNull { it.key == key }

    // ------------------------------------------------------------------------------------------------ refresh

    public fun start() {
        if (disposed) return
        schedule()
        startRefresh()
    }

    public fun setRuntime(foreground: Boolean, backgroundAllowed: Boolean) {
        if (disposed) return
        if (this.foreground == foreground && this.backgroundAllowed == backgroundAllowed) return
        this.foreground = foreground
        this.backgroundAllowed = backgroundAllowed
        timer?.cancel()
        timer = null
        if (runningAllowed) {
            schedule()
            startRefresh()
        }
    }

    private fun schedule() {
        timer?.cancel()
        timer = null
        if (!runningAllowed) return
        val wait = if (foreground) foregroundIntervalMs else backgroundIntervalMs
        timer =
            scope.launch {
                delay(wait)
                timer = null
                refresh()
            }
    }

    /** Reads every source once, or joins the read already running. */
    public suspend fun refresh(force: Boolean = false) {
        startRefresh(force)?.await()
    }

    /** Starts a read now (or returns the one running) without waiting for it; null when nothing may run. */
    public fun startRefresh(force: Boolean = false): Deferred<Unit>? {
        if (!runningAllowed) return null
        running?.let { return it }
        val operation = scope.async { read(force) }
        running = operation
        operation.invokeOnCompletion {
            if (running === operation) running = null
            if (!disposed) schedule()
        }
        return operation
    }

    private suspend fun read(force: Boolean) {
        val readEpoch = epoch
        val list = connections()
        for (c in list) loadCache(c.id)
        val context =
            UsageReadContext(
                connections = list,
                now = clock.nowMs(),
                force = force,
                background = !foreground,
                lastReading = { connection, provider -> cache[connection]?.get(provider) },
                signedIn = { connection -> signIns[connection].orEmpty().map { it.providerId }.toSet() },
            )
        val results =
            sources.map { source ->
                scope.async {
                    withTimeoutOrNull(sourceTimeoutMs) { guarded { source.read(context) } } ?: UsageSourceResult()
                }
            }.awaitAll()
        if (disposed || readEpoch != epoch) return
        val now = clock.nowMs()
        val known = list.map { it.id }.toSet()
        val touched = LinkedHashSet<String>()

        val unreached = results.flatMap { it.unreached }.toSet()
        offline.clear()
        offline += unreached.intersect(known)
        for (id in unreached) {
            val slot = cache[id] ?: continue
            for (reading in slot.values.toList()) {
                if (!reading.hasNumbers) continue
                slot[reading.providerId] = reading.copy(problem = UsageProblem.Offline, checkedAt = now)
                touched += id
            }
        }
        for (result in results) {
            for (reading in result.readings) {
                if (reading.connectionId !in known) continue
                val slot = cache.getOrPut(reading.connectionId) { LinkedHashMap() }
                val old = slot[reading.providerId]
                // The older of two numbers never replaces the newer.
                if (old != null && old.hasNumbers && old.problem == null && reading.hasNumbers &&
                    old.fetchedAt > reading.fetchedAt
                ) {
                    continue
                }
                slot[reading.providerId] =
                    reading.copy(problem = null, checkedAt = now, accountRef = reading.accountRef ?: old?.accountRef)
                touched += reading.connectionId
            }
            for (failure in result.failures) {
                if (failure.connectionId !in known) continue
                val slot = cache.getOrPut(failure.connectionId) { LinkedHashMap() }
                val old = slot[failure.providerId]
                if (old != null) {
                    slot[failure.providerId] = old.copy(problem = failure.problem, checkedAt = now)
                } else if (failure.problem == UsageProblem.Broken) {
                    slot[failure.providerId] =
                        UsageReading(
                            providerId = failure.providerId,
                            displayName = failure.providerId,
                            connectionId = failure.connectionId,
                            status = UsageReadingStatus.NoLimits,
                            fetchedAt = now,
                            checkedAt = now,
                            problem = UsageProblem.Broken,
                        )
                } else {
                    continue
                }
                touched += failure.connectionId
            }
            for (id in result.authoritativeSignIns) {
                signIns[id] = result.signIns.filter { it.connectionId == id }.toMutableList()
                authoritative += id
            }
            authoritative -= result.unreached
        }
        // A reached connection is no longer offline.
        for (id in known - unreached) {
            val slot = cache[id] ?: continue
            for (reading in slot.values.toList()) {
                if (reading.problem == UsageProblem.Offline) {
                    slot[reading.providerId] = reading.copy(problem = null)
                    touched += id
                }
            }
        }
        // Sign-ins seen without a reader (OpenCode servers): kept per connection.
        for (result in results) {
            for (s in result.signIns) {
                if (s.connectionId in result.authoritativeSignIns) continue
                val slot = signIns.getOrPut(s.connectionId) { mutableListOf() }
                slot.removeAll { it.providerId == s.providerId && it.connectionId == s.connectionId }
                slot += s
            }
        }
        refreshedAt = now
        loaded = true
        for (id in touched) saveCache(id)
        migrateOnce()
        rebuild(loaded = true)
        syncAlerts()
        publishWidget()
    }

    // ------------------------------------------------------------------------------------------------ caching

    private fun loadCache(id: String) {
        if (cache.containsKey(id)) return
        val slot = LinkedHashMap<String, UsageReading>()
        val raw = prefs.getString(lastKey(id))
        if (raw != null && raw.length <= 262144) {
            (DartJson.decodeOrNull(raw) as? List<*>)?.take(32)?.forEach { item ->
                UsageReadingCodec.decode(item, id)?.let { slot[it.providerId] = it }
            }
        }
        cache[id] = slot
    }

    private suspend fun saveCache(id: String) {
        val slot = cache[id] ?: return
        if (slot.isEmpty()) {
            prefs.remove(lastKey(id))
        } else {
            prefs.setString(lastKey(id), DartJson.encode(slot.values.map { UsageReadingCodec.encode(it) }))
        }
    }

    private fun rebuild(loaded: Boolean) {
        val list = connections()
        _snapshot.value =
            mergeUsageSubscriptions(
                now = clock.nowMs(),
                connections = list,
                readings = list.flatMap { cache[it.id]?.values.orEmpty() },
                signIns = list.flatMap { signIns[it.id].orEmpty() },
                authoritativeSignIns = authoritative.toSet(),
                offlineConnections = offline.toSet(),
                defaultThreshold = default,
                overrides = overrides.toMap(),
                notifyOnResetDefault = resetDefault,
                refreshedAt = refreshedAt,
                loaded = loaded || this.loaded,
            )
    }

    // ------------------------------------------------------------------------------------------------ alerts

    public val alertsOn: Boolean get() = sharedRules().quotaAlerts

    public suspend fun setAlertsOn(value: Boolean) {
        setQuotaAlerts(value)
        if (disposed) return
        syncAlerts()
    }

    public val defaultThreshold: Double get() = default
    public val notifyOnResetDefault: Boolean get() = resetDefault
    public val alertOverrides: List<UsageSubscription> get() = snapshot.subscriptions.filter { it.hasAlertOverride }

    public suspend fun setDefaultThreshold(percent: Double): Boolean {
        if (!validPercent(percent)) return false
        default = percent
        return commitAlerts()
    }

    public suspend fun setNotifyOnResetDefault(value: Boolean): Boolean {
        resetDefault = value
        return commitAlerts()
    }

    public suspend fun setThresholdFor(key: String, percent: Double?): Boolean {
        if (percent != null && !validPercent(percent)) return false
        putOverride(key, UsageAlertOverride(percent, overrides[key]?.notifyOnReset))
        return commitAlerts()
    }

    public suspend fun setNotifyOnReset(key: String, value: Boolean): Boolean {
        putOverride(key, UsageAlertOverride(overrides[key]?.threshold, value))
        return commitAlerts()
    }

    private fun validPercent(p: Double) = p.isFinite() && p > 0 && p <= 100

    private fun putOverride(key: String, value: UsageAlertOverride) {
        if (value.isEmpty) overrides.remove(key) else overrides[key] = value
    }

    private suspend fun commitAlerts(): Boolean {
        val saved = saveAlerts()
        rebuild(loaded)
        syncAlerts()
        return saved
    }

    private fun loadAlerts() {
        val data = prefs.getString(ALERTS_KEY)?.let { DartJson.decodeOrNull(it) } as? Map<*, *> ?: return
        (data["default"] as? Number)?.toDouble()?.takeIf { validPercent(it) }?.let { default = it }
        resetDefault = data["resetDefault"] == true
        migrated = data["migrated"] == true
        (data["overrides"] as? Map<*, *>)?.entries?.take(256)?.forEach { (key, v) ->
            if (key !is String || v !is Map<*, *>) return@forEach
            overrides[key] =
                UsageAlertOverride(
                    threshold = (v["t"] as? Number)?.toDouble()?.takeIf { validPercent(it) },
                    notifyOnReset = v["r"] as? Boolean,
                )
        }
    }

    private suspend fun saveAlerts(): Boolean =
        prefs.setString(
            ALERTS_KEY,
            DartJson.encode(
                linkedMapOf(
                    "v" to 1,
                    "default" to default,
                    "resetDefault" to resetDefault,
                    "migrated" to migrated,
                    "overrides" to
                        overrides.mapValues { (_, o) ->
                            linkedMapOf<String, Any?>().apply {
                                o.threshold?.let { put("t", it) }
                                o.notifyOnReset?.let { put("r", it) }
                            }
                        },
                ),
            ),
        )

    /** Keeps any threshold the person chose under the old per-source rules. */
    private suspend fun migrateOnce() {
        if (migrated) return
        val bridge = alertBridge ?: return
        for (rule in bridge.legacyRules()) {
            if (abs(rule.threshold - DEFAULT_ALERT_THRESHOLD) < 0.001) continue
            val key = usageSubscriptionKey(rule.providerId, rule.accountRef, rule.connectionId)
            val old = overrides[key]
            overrides[key] = UsageAlertOverride(old?.threshold ?: rule.threshold, old?.notifyOnReset)
        }
        migrated = true
        saveAlerts()
    }

    /** Watches every readable subscription at its effective level. */
    private suspend fun syncAlerts() {
        if (disposed) return
        deliverSubscriptionAlerts()
        val bridge = alertBridge ?: return
        val wanted =
            snapshot.subscriptions.mapNotNull { s ->
                val from = s.readFrom
                if (from == null || s.windows.isEmpty() || s.state == SubscriptionState.OfflineStale ||
                    s.state == SubscriptionState.ReaderBroken
                ) {
                    null
                } else {
                    QuotaWatch(from.id, s.providerId, s.alertThreshold)
                }
            }
        // Alerts are best effort; the next refresh tries again.
        guarded { bridge.reconcile(wanted, alertsOn) }
    }

    /** Whether a notification for [token] names a subscription read from [profileId]. */
    public fun ownsAlertToken(profileId: String, token: String): Boolean =
        subscriptionAlerts?.owns(snapshot, profileId, token) ?: false

    private suspend fun deliverSubscriptionAlerts() {
        val alerts = subscriptionAlerts ?: return
        val bridge = alertBridge
        guarded {
            alerts.evaluate(snapshot, alertsOn, foreground) { s ->
                val from = s.readFrom
                from != null && bridge != null && bridge.covers(from.id, s.providerId)
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ widget

    public suspend fun setWidgetSettings(value: UsageWidgetSettings) {
        widgetSettings = value
        prefs.setString(UsageWidgetSettings.KEY, value.toJson())
        publishWidget()
    }

    private suspend fun publishWidget() {
        guarded { widget?.publishSubscriptions(snapshot, widgetSettings) }
    }

    // ------------------------------------------------------------------------------------------------ deletion

    /** A saved server was removed: its cached numbers, sign-ins and orphaned overrides and alert records go too. */
    public fun removeProfile(profileId: String) {
        epoch++
        cache.remove(profileId)
        signIns.remove(profileId)
        authoritative.remove(profileId)
        offline.remove(profileId)
        rebuild(loaded)
        val alive = snapshot.subscriptions.map { it.key }.toSet()
        val before = overrides.size
        overrides.keys.retainAll(alive)
        val overridesChanged = overrides.size != before
        scope.launch {
            prefs.remove(lastKey(profileId))
            if (overridesChanged) saveAlerts()
            subscriptionAlerts?.prune(alive)
            publishWidget()
        }
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        epoch++
        timer?.cancel()
    }
}

/** The `oc.usageLast.<connectionId>` record of one reading (same JSON as Flutter's UsageReading.toJson). */
public object UsageReadingCodec {
    private val providerPattern = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

    public fun encode(r: UsageReading): Map<String, Any?> =
        linkedMapOf(
            "provider" to r.providerId,
            "name" to r.displayName,
            "status" to statusWire(r.status),
            "fetchedAt" to r.fetchedAt,
            "account" to r.accountRef,
            "plan" to r.plan,
            "method" to methodWire(r.method),
            "windows" to
                r.windows.map {
                    linkedMapOf(
                        "id" to it.id,
                        "used" to it.usedPercent,
                        "seconds" to it.durationSeconds,
                        "resetsAt" to it.resetsAt,
                        "label" to it.label,
                    )
                },
        )

    public fun decode(value: Any?, connectionId: String): UsageReading? {
        if (value !is Map<*, *>) return null
        val provider = value["provider"] as? String ?: return null
        val name = value["name"] as? String ?: return null
        val fetched = value["fetchedAt"] as? Long ?: return null
        if (!providerPattern.matches(provider)) return null
        val status = UsageReadingStatus.entries.firstOrNull { statusWire(it) == value["status"] } ?: return null
        val account = value["account"] as? String
        return UsageReading(
            providerId = provider,
            displayName = name.take(40),
            connectionId = connectionId,
            status = status,
            fetchedAt = fetched,
            windows = (value["windows"] as? List<*>).orEmpty().take(16).mapNotNull { window(it) },
            accountRef = account?.takeIf { it.length <= 128 },
            plan = value["plan"] as? String,
            method = SignInMethod.entries.firstOrNull { methodWire(it) == value["method"] } ?: SignInMethod.Unknown,
        )
    }

    private fun window(value: Any?): UsageWindow? {
        if (value !is Map<*, *>) return null
        val id = value["id"] as? String ?: return null
        val used = (value["used"] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: return null
        return UsageWindow(
            id = id,
            usedPercent = used,
            durationSeconds = (value["seconds"] as? Long)?.toInt(),
            resetsAt = value["resetsAt"] as? Long,
            label = value["label"] as? String ?: "",
        )
    }

    private fun statusWire(s: UsageReadingStatus) =
        when (s) {
            UsageReadingStatus.Ok -> "ok"
            UsageReadingStatus.NoLimits -> "noLimits"
            UsageReadingStatus.AuthRequired -> "authRequired"
            UsageReadingStatus.NotSignedIn -> "notSignedIn"
        }

    private fun methodWire(m: SignInMethod) =
        when (m) {
            SignInMethod.Plan -> "plan"
            SignInMethod.ApiKey -> "apiKey"
            SignInMethod.Unknown -> "unknown"
        }
}
