package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.truncate

public enum class UsageBudgetUnit(public val wire: String) { Usd("usd"), Tokens("tokens") }

/** The exact Usage view a budget belongs to: project, calendar range, window start and time zone (Flutter UsageQuery). */
public data class UsageBudgetScope(val project: String?, val range: String, val from: Long?, val timezone: String)

/** One write queue per preference key, shared by every [UsageBudgets] (a replacement screen writes through the same queue). */
public class UsageBudgetLocks {
    private val guard = Mutex()
    private val locks = HashMap<String, Mutex>()

    internal suspend fun <T> withKey(key: String, block: suspend () -> T): T {
        val lock = guard.withLock { locks.getOrPut(key) { Mutex() } }
        return lock.withLock { block() }
    }
}

public data class UsageBudgetsState(val saving: Boolean = false, val failed: Boolean = false)

/**
 * Personal budgets for measured consumption; never subscription allowances (Flutter `UsageBudgets`). Each rule belongs to
 * the exact server, project, time zone and window start; a new start is a different budget. Stored in
 * `oc.consumptionBudgets.<profileId>` (swept with the profile) as `{version: 1, rules: {sha256(scope): rule}}`.
 */
public class UsageBudgets(
    private val store: KeyValueStore,
    private val lane: WriterLane,
    private val locks: UsageBudgetLocks,
    public val profileId: String,
    serverOrigin: String,
    private val isCurrent: () -> Boolean,
    private val isProfilePresent: () -> Boolean,
) {
    public companion object {
        public const val MAX_RULES: Int = 64
        private val hex64 = Regex("^[a-f0-9]{64}$")
        private val ranges = setOf("today", "thirtyDays", "year", "allTime")
        private val fields = setOf("source", "project", "range", "from", "timezone", "unit", "limit")
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0

        public fun key(profileId: String): String = "oc.consumptionBudgets.$profileId"

        public fun validLimit(value: Double, unit: UsageBudgetUnit): Boolean =
            value.isFinite() && value > 0 && value <= MAX_SAFE_INTEGER && (unit != UsageBudgetUnit.Tokens || value == truncate(value))

        private fun validStoredRule(id: String, value: Any?): Boolean {
            val rule = value as? Map<*, *> ?: return false
            val unit = UsageBudgetUnit.entries.firstOrNull { it.wire == rule["unit"] } ?: return false
            val limit = (rule["limit"] as? Number)?.toDouble() ?: return false
            val source = rule["source"] as? String ?: return false
            val timezone = rule["timezone"] as? String ?: return false
            if (!hex64.matches(id) || rule.keys != fields || !hex64.matches(source) ||
                (rule["project"] != null && rule["project"] !is String) ||
                rule["range"] !in ranges ||
                (rule["from"] != null && rule["from"] !is Long && rule["from"] !is Int) ||
                timezone.isBlank() || !validLimit(limit, unit)
            ) {
                return false
            }
            val scope =
                linkedMapOf(
                    "source" to source,
                    "project" to rule["project"],
                    "range" to rule["range"],
                    "from" to (rule["from"] as? Number)?.toLong(),
                    "timezone" to timezone,
                    "unit" to unit.wire,
                )
            return DartJson.sha256Hex(DartJson.encode(scope)) == id
        }
    }

    public val key: String = key(profileId)
    private val source = DartJson.sha256Hex(serverOrigin)
    private var rules: Map<String, Map<*, *>> = emptyMap()
    private var disposed = false
    private val _state = MutableStateFlow(UsageBudgetsState())
    public val state: StateFlow<UsageBudgetsState> = _state.asStateFlow()

    init {
        var failed = false
        val loaded = LinkedHashMap<String, Map<*, *>>()
        val raw = store.string(key)
        if (raw != null) {
            val data = if (raw.length > 65_536) null else DartJson.decodeOrNull(raw) as? Map<*, *>
            val stored = data?.takeIf { it["version"] == 1L }?.get("rules") as? Map<*, *>
            if (stored == null || stored.size > MAX_RULES) {
                failed = true
            } else {
                for ((id, rule) in stored) {
                    if (id is String && validStoredRule(id, rule)) loaded[id] = rule as Map<*, *> else failed = true
                }
            }
        }
        rules = loaded
        _state.value = UsageBudgetsState(failed = failed)
    }

    public val available: Boolean get() = !disposed && isCurrent()

    /** Whether any budget is saved for this server, for any range or project. */
    public val hasSaved: Boolean get() = available && rules.isNotEmpty()

    private fun scopeOf(scope: UsageBudgetScope, unit: UsageBudgetUnit): LinkedHashMap<String, Any?> =
        linkedMapOf(
            "source" to source,
            "project" to scope.project,
            "range" to scope.range,
            "from" to scope.from,
            "timezone" to scope.timezone,
            "unit" to unit.wire,
        )

    private fun idOf(scope: UsageBudgetScope, unit: UsageBudgetUnit): String = DartJson.sha256Hex(DartJson.encode(scopeOf(scope, unit)))

    private fun matchesScope(rule: Map<*, *>, scope: UsageBudgetScope, unit: UsageBudgetUnit): Boolean {
        val wanted = scopeOf(scope, unit)
        return rule.size == wanted.size + 1 &&
            wanted.all { (k, v) -> if (k == "from") (rule[k] as? Number)?.toLong() == v else rule[k] == v }
    }

    /** The saved limit for exactly this scope and unit, or null. */
    public fun limit(scope: UsageBudgetScope, unit: UsageBudgetUnit): Double? {
        if (!available) return null
        val rule = rules[idOf(scope, unit)] ?: return null
        return if (matchesScope(rule, scope, unit)) (rule["limit"] as? Number)?.toDouble() else null
    }

    /** Saves (or with null clears) one budget. A failed write keeps the durable previous budget. */
    public suspend fun save(scope: UsageBudgetScope, unit: UsageBudgetUnit, value: Double?): Boolean {
        if (!available || _state.value.saving || (value != null && !validLimit(value, unit))) return false
        val id = idOf(scope, unit)
        val rule: Map<String, Any?>? =
            value?.let { scopeOf(scope, unit) + ("limit" to (if (value == truncate(value)) value.toLong() else value)) }
        _state.value = UsageBudgetsState(saving = true)
        val durable = update(mapOf(id to rule), clear = false)
        var degraded = false
        if (durable != null) {
            degraded = durable.any { (k, v) -> !validStoredRule(k, v) }
            rules = durable.filter { (k, v) -> validStoredRule(k, v) }.mapValues { it.value as Map<*, *> }
        }
        _state.value = UsageBudgetsState(saving = false, failed = durable == null || degraded)
        return durable != null
    }

    /** Clears every budget of this server: full capacity again, never a silent eviction. */
    public suspend fun clearAll(): Boolean {
        if (!available || _state.value.saving) return false
        _state.value = UsageBudgetsState(saving = true)
        val result = update(emptyMap(), clear = true)
        if (result != null) rules = emptyMap()
        _state.value = UsageBudgetsState(saving = false, failed = result == null)
        return result != null
    }

    public fun dispose() {
        disposed = true
        rules = emptyMap()
    }

    /** Merges only this action's changes after reading durable state, serialized per key across instances. */
    private suspend fun update(changes: Map<String, Any?>, clear: Boolean): Map<String, Any?>? =
        locks.withKey(key) {
            if (!available) return@withKey null
            val raw = store.string(key)
            val merged = LinkedHashMap<String, Any?>()
            if (raw != null && !clear) {
                val data = if (raw.length > 65_536) null else DartJson.decodeOrNull(raw) as? Map<*, *>
                val stored = data?.takeIf { it["version"] == 1L }?.get("rules") as? Map<*, *> ?: return@withKey null
                stored.forEach { (k, v) -> if (k is String) merged[k] = v }
            }
            for ((id, rule) in changes) if (rule == null) merged.remove(id) else merged[id] = rule
            if (merged.size > MAX_RULES) return@withKey null
            val written =
                if (merged.isEmpty()) {
                    store.commit(lane) { remove(key) }
                } else {
                    val payload = DartJson.encode(linkedMapOf("version" to 1, "rules" to merged))
                    store.commit(lane) { putString(key, payload) }
                }
            if (!isProfilePresent()) {
                // The profile went away while this write was pending: it must not resurrect the budget.
                store.commit(lane) { remove(key) }
                return@withKey null
            }
            if (written is Outcome.Ok) merged else null
        }
}

/**
 * The Remaining page's personal quota budgets are retired (quota monitoring's threshold replaced them): the rules they
 * left on the device (`oc.budgets.<profileId>`) go on load, and nothing else does. Profile deletion still sweeps the
 * key (it is an ordinary per-profile key).
 */
public object RetiredQuotaBudgets {
    public const val PREFIX: String = "oc.budgets."

    /** Removes every stored quota budget; Ok(0) when there was nothing to do. */
    public suspend fun dropAll(store: KeyValueStore, lane: WriterLane): Outcome<Int> {
        val keys = store.keys(PREFIX)
        if (keys.isEmpty()) return Outcome.Ok(0)
        return when (val r = store.commit(lane) { keys.forEach { remove(it) } }) {
            is Outcome.Ok -> Outcome.Ok(keys.size)
            is Outcome.Failed -> r
        }
    }
}
