package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What a [UsageSource] is asked to read. */
public class UsageReadContext(
    public val connections: List<UsageConnection>,
    public val now: Instant,
    public val force: Boolean = false,
    /**
     * The app is in the background: readers that cost a request on a computer keep to their slower
     * cadence.
     */
    public val background: Boolean = false,
    /**
     * The last reading kept for a connection and provider, to tell "used before" from "never used".
     */
    public val lastReading: (connectionId: String, providerId: String) -> UsageReading? = { _, _ ->
        null
    },
    /** The provider ids the last refresh saw signed in on a connection. */
    public val signedIn: (connectionId: String) -> Set<String> = { emptySet() },
)

/** What one source learned. */
public data class UsageSourceResult(
    val readings: List<UsageReading> = emptyList(),
    val signIns: List<UsageSignIn> = emptyList(),
    val failures: List<UsageFailure> = emptyList(),
    /** Connections the source tried and could not reach at all. */
    val unreached: Set<String> = emptySet(),
    /**
     * Connections whose [signIns] list is complete: a provider missing from it is signed out there.
     */
    val authoritativeSignIns: Set<String> = emptySet(),
)

/**
 * One reader of usage (the collector, OpenCode sign-ins, Paseo, this phone). Sources never switch
 * the active server.
 */
public fun interface UsageSource {
    public suspend fun read(context: UsageReadContext): Outcome<UsageSourceResult>
}

/** A rule the old per-source quota monitor already holds. */
public data class LegacyQuotaRule(
    val connectionId: String,
    val providerId: String,
    val accountRef: String,
    val threshold: Double,
)

/** One collector reading that should be watched at [threshold]. */
public data class QuotaWatch(
    val connectionId: String,
    val providerId: String,
    val threshold: Double,
)

/** The bridge to the quota monitor, which keeps its own timer, delivery and dedupe markers. */
public interface UsageAlertBridge {
    public fun legacyRules(): List<LegacyQuotaRule>

    public suspend fun reconcile(wanted: List<QuotaWatch>, alertsOn: Boolean)
}

/**
 * Subscriptions across every connection, with the alert state that goes with them (Flutter
 * `UsageSubscriptionsController`; contract docs/design/usage-subscriptions-contract-2026-10-13.md).
 * Cached readings live in `oc.usageLast.<connectionId>` (swept with the profile); alert levels in
 * the app-wide `oc.usageAlerts.v1`. Timers belong to the caller: [refresh] reads every source once.
 */
public class UsageSubscriptionsEngine(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    public val connections: () -> List<UsageConnection>,
    private val sources: List<UsageSource>,
    private val alertsOnRule: () -> Boolean,
    private val setQuotaAlerts: suspend (Boolean) -> Unit,
    private val alertBridge: UsageAlertBridge? = null,
    private val widget: UsageWidgetFeed? = null,
    private val clock: () -> Instant,
    private val sourceTimeout: Duration = 25.seconds,
) {
    public companion object {
        public const val ALERTS_KEY: String = "oc.usageAlerts.v1"

        /** The default level every readable subscription is watched at (D4). */
        public const val DEFAULT_ALERT_THRESHOLD: Double = 80.0

        public fun lastKey(connectionId: String): String = "oc.usageLast.$connectionId"

        private fun validLevel(percent: Double): Boolean =
            percent.isFinite() && percent > 0 && percent <= 100
    }

    private val cache = HashMap<String, MutableMap<String, UsageReading>>()
    private val signIns = HashMap<String, MutableList<UsageSignIn>>()
    private val authoritative = HashSet<String>()
    private val offline = HashSet<String>()
    private val refreshLock = Mutex()
    private val alertWrites = Mutex()
    private var refreshedAt: Instant? = null
    private var loaded = false
    private var disposed = false
    private var foreground = true
    private var epoch = 0

    private var default = DEFAULT_ALERT_THRESHOLD
    private var resetDefault = false
    private val overrides = LinkedHashMap<String, UsageAlertOverride>()
    private var migrated = false

    public var widgetSettings: UsageWidgetSettings = UsageWidgetSettings.load(store)
        private set

    private val _snapshot = MutableStateFlow(UsageSnapshot())
    public val snapshot: StateFlow<UsageSnapshot> = _snapshot.asStateFlow()

    init {
        loadAlerts()
        for (c in connections()) loadCache(c.id)
        rebuild(loaded = false)
    }

    public fun subscription(key: String): UsageSubscription? =
        _snapshot.value.subscriptions.firstOrNull { it.key == key }

    // ------------------------------------------------------------------------------------------------------- refresh

    public fun setForeground(value: Boolean) {
        foreground = value
    }

    /** Reads every source once; a concurrent call waits for the running one. */
    public suspend fun refresh(force: Boolean = false) {
        if (disposed) return
        if (!refreshLock.tryLock()) {
            refreshLock.withLock {}
            return
        }
        try {
            read(force)
        } finally {
            refreshLock.unlock()
        }
    }

    private suspend fun read(force: Boolean) {
        val start = epoch
        val list = connections()
        for (c in list) loadCache(c.id)
        val context =
            UsageReadContext(
                connections = list,
                now = clock(),
                force = force,
                background = !foreground,
                lastReading = { connection, provider -> cache[connection]?.get(provider) },
                signedIn = { connection ->
                    signIns[connection].orEmpty().mapTo(HashSet()) { it.providerId }
                },
            )
        // A source that fails, throws or runs out of time is ignored; the others still show.
        val results = coroutineScope {
            sources
                .map { source ->
                    async {
                        withTimeoutOrNull(sourceTimeout) {
                            when (val r = attempt({ Failure.Device() }) { source.read(context) }) {
                                is Outcome.Ok -> (r.value as? Outcome.Ok)?.value
                                is Outcome.Failed -> null
                            }
                        } ?: UsageSourceResult()
                    }
                }
                .map { it.await() }
        }
        if (disposed || start != epoch) return
        val now = clock()
        val known = list.mapTo(HashSet()) { it.id }
        val touched = HashSet<String>()

        val unreached = results.flatMapTo(HashSet()) { it.unreached }
        offline.clear()
        offline += unreached.intersect(known)
        for (id in unreached) {
            val slot = cache[id] ?: continue
            for (reading in slot.values.toList()) {
                if (!reading.hasNumbers) continue
                slot[reading.providerId] =
                    reading.copy(problem = UsageProblem.Offline, checkedAt = now)
                touched += id
            }
        }
        for (result in results) {
            for (reading in result.readings) {
                if (reading.connectionId !in known) continue
                val slot = cache.getOrPut(reading.connectionId) { LinkedHashMap() }
                val old = slot[reading.providerId]
                // The older of two numbers never replaces the newer.
                if (
                    old != null &&
                        old.hasNumbers &&
                        old.problem == null &&
                        reading.hasNumbers &&
                        old.fetchedAt > reading.fetchedAt
                )
                    continue
                slot[reading.providerId] =
                    reading.copy(
                        problem = null,
                        checkedAt = now,
                        accountRef = reading.accountRef ?: old?.accountRef,
                    )
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
            for (id in result.unreached) authoritative -= id
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
        // Sign-ins seen without a complete list (one provider at a time): kept per connection.
        for (result in results) {
            for (s in result.signIns) {
                if (s.connectionId in result.authoritativeSignIns) continue
                val l = signIns.getOrPut(s.connectionId) { ArrayList() }
                l.removeAll { it.providerId == s.providerId && it.connectionId == s.connectionId }
                l += s
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

    // ------------------------------------------------------------------------------------------------------- caching

    private fun loadCache(id: String) {
        if (cache.containsKey(id)) return
        val slot = LinkedHashMap<String, UsageReading>()
        val raw = store.string(lastKey(id))
        // An unreadable cache is just an empty one.
        if (raw != null && raw.length <= 262_144) {
            (DartJson.decodeOrNull(raw) as? List<*>)?.take(32)?.forEach { item ->
                UsageReading.fromJson(item, id)?.let { slot[it.providerId] = it }
            }
        }
        cache[id] = slot
    }

    private suspend fun saveCache(id: String) {
        val slot = cache[id] ?: return
        if (slot.isEmpty()) {
            store.commit(lane) { remove(lastKey(id)) }
        } else {
            val payload = DartJson.encode(slot.values.map { it.toJson() })
            store.commit(lane) { putString(lastKey(id), payload) }
        }
    }

    private fun rebuild(loaded: Boolean) {
        val list = connections()
        _snapshot.value =
            mergeUsageSubscriptions(
                now = clock(),
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

    // -------------------------------------------------------------------------------------------------------- alerts

    public val alertsOn: Boolean
        get() = alertsOnRule()

    public suspend fun setAlertsOn(value: Boolean) {
        setQuotaAlerts(value)
        if (disposed) return
        syncAlerts()
    }

    public val defaultThreshold: Double
        get() = default

    public val notifyOnResetDefault: Boolean
        get() = resetDefault

    public val alertOverrides: List<UsageSubscription>
        get() = _snapshot.value.subscriptions.filter { it.hasAlertOverride }

    public suspend fun setDefaultThreshold(percent: Double): Boolean {
        if (!validLevel(percent)) return false
        return updateAlerts { default = percent }
    }

    public suspend fun setNotifyOnResetDefault(value: Boolean): Boolean {
        return updateAlerts { resetDefault = value }
    }

    /**
     * A per-subscription level; null removes the override. Bad levels are refused and change
     * nothing.
     */
    public suspend fun setThresholdFor(key: String, percent: Double?): Boolean {
        if (percent != null && !validLevel(percent)) return false
        return updateAlerts {
            putOverride(
                key,
                UsageAlertOverride(
                    threshold = percent,
                    notifyOnReset = overrides[key]?.notifyOnReset,
                ),
            )
        }
    }

    public suspend fun setNotifyOnReset(key: String, value: Boolean): Boolean {
        return updateAlerts {
            putOverride(
                key,
                UsageAlertOverride(threshold = overrides[key]?.threshold, notifyOnReset = value),
            )
        }
    }

    private fun putOverride(key: String, value: UsageAlertOverride) {
        if (value.isEmpty) overrides.remove(key) else overrides[key] = value
    }

    private suspend fun updateAlerts(change: () -> Unit): Boolean = alertWrites.withLock {
        val oldDefault = default
        val oldReset = resetDefault
        val oldOverrides = overrides.toMap()
        change()
        val saved = saveAlerts()
        if (!saved) {
            default = oldDefault
            resetDefault = oldReset
            overrides.clear()
            overrides.putAll(oldOverrides)
        }
        rebuild(loaded)
        if (saved) syncAlerts()
        saved
    }

    private fun loadAlerts() {
        val raw = store.string(ALERTS_KEY) ?: return
        // Start from the defaults rather than keep unknown content.
        val data = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return
        (data["default"] as? Number)?.toDouble()?.takeIf { validLevel(it) }?.let { default = it }
        resetDefault = data["resetDefault"] == true
        migrated = data["migrated"] == true
        (data["overrides"] as? Map<*, *>)?.entries?.take(256)?.forEach { (k, v) ->
            if (k !is String || v !is Map<*, *>) return@forEach
            overrides[k] =
                UsageAlertOverride(
                    threshold = (v["t"] as? Number)?.toDouble()?.takeIf { validLevel(it) },
                    notifyOnReset = v["r"] as? Boolean,
                )
        }
    }

    private suspend fun saveAlerts(): Boolean {
        val payload =
            DartJson.encode(
                linkedMapOf(
                    "v" to 1,
                    "default" to default,
                    "resetDefault" to resetDefault,
                    "migrated" to migrated,
                    "overrides" to
                        overrides.entries.associateTo(LinkedHashMap<String, Any?>()) { (k, v) ->
                            val entry = LinkedHashMap<String, Any?>()
                            v.threshold?.let { entry["t"] = it }
                            v.notifyOnReset?.let { entry["r"] = it }
                            k to entry
                        },
                )
            )
        return store.commit(lane) { putString(ALERTS_KEY, payload) } is Outcome.Ok
    }

    /** Keeps any threshold the person chose under the old per-source rules, once. */
    private suspend fun migrateOnce() {
        if (migrated) return
        val bridge = alertBridge ?: return
        for (rule in bridge.legacyRules()) {
            if (abs(rule.threshold - DEFAULT_ALERT_THRESHOLD) < 0.001) continue
            val key = usageSubscriptionKey(rule.providerId, rule.accountRef, rule.connectionId)
            val old = overrides[key]
            overrides[key] =
                UsageAlertOverride(
                    threshold = old?.threshold ?: rule.threshold,
                    notifyOnReset = old?.notifyOnReset,
                )
        }
        migrated = true
        saveAlerts()
    }

    /** Watches every readable subscription at its effective level. */
    private suspend fun syncAlerts() {
        val bridge = alertBridge ?: return
        if (disposed) return
        val wanted =
            _snapshot.value.subscriptions.mapNotNull { s ->
                val from = s.readFrom
                if (
                    from == null ||
                        s.windows.isEmpty() ||
                        s.state == SubscriptionState.OfflineStale ||
                        s.state == SubscriptionState.ReaderBroken
                ) {
                    null
                } else {
                    QuotaWatch(from.id, s.providerId, s.alertThreshold)
                }
            }
        // Alerts are best effort; the next refresh tries again.
        attempt({ Failure.Device() }) { bridge.reconcile(wanted, alertsOn) }
    }

    // -------------------------------------------------------------------------------------------------------- widget

    public suspend fun setWidgetSettings(value: UsageWidgetSettings): Boolean {
        if (
            store.commit(lane) { putString(UsageWidgetSettings.KEY, value.toJson()) } !is Outcome.Ok
        )
            return false
        widgetSettings = value
        publishWidget()
        return true
    }

    private suspend fun publishWidget() {
        widget?.publishSubscriptions(_snapshot.value, widgetSettings)
    }

    // ------------------------------------------------------------------------------------------------------ deletion

    /**
     * A saved server was removed: its cached numbers, sign-ins and overrides of vanished
     * subscriptions go with it.
     */
    public suspend fun removeProfile(profileId: String) {
        epoch++
        cache.remove(profileId)
        signIns.remove(profileId)
        authoritative -= profileId
        offline -= profileId
        store.commit(lane) { remove(lastKey(profileId)) }
        rebuild(loaded)
        val alive = _snapshot.value.subscriptions.mapTo(HashSet()) { it.key }
        val before = overrides.size
        overrides.keys.retainAll(alive)
        if (overrides.size != before) saveAlerts()
        publishWidget()
    }

    public fun dispose() {
        disposed = true
        epoch++
    }
}
