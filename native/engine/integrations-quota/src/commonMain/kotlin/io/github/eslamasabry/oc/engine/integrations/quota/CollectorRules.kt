package io.github.eslamasabry.oc.engine.integrations.quota

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.PrefType
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import io.github.eslamasabry.oc.core.data.scope.KeyScope
import io.github.eslamasabry.oc.core.data.scope.KeyShape
import io.github.eslamasabry.oc.core.data.scope.ProfileScope
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Explicit, source-bound collector consent. Only opaque hashes and policy are stored. */
public data class CollectorRule(
    val source: String,
    val account: String,
    val token: String,
    val notifications: Boolean,
    val threshold: Double,
    val wifiOnly: Boolean = false,
    val quietStart: Int? = null,
    val quietEnd: Int? = null,
    val alerted: Map<String, String> = emptyMap(),
) {
    public fun toJson(): Map<String, Any?> =
        mapOf(
            "source" to source,
            "account" to account,
            "token" to token,
            "notifications" to notifications,
            "threshold" to threshold,
            "wifiOnly" to wifiOnly,
            "quietStart" to quietStart,
            "quietEnd" to quietEnd,
            "alerted" to alerted,
        )

    public companion object {
        private val hash = Regex("^[a-f0-9]{64}$")

        public fun parse(value: Any?): CollectorRule? {
            val m = value as? Map<*, *> ?: return null
            val source = m["source"] as? String ?: return null
            val account = m["account"] as? String ?: return null
            val token = m["token"] as? String ?: return null
            val notifications = m["notifications"] as? Boolean ?: return null
            val wifiOnly = m["wifiOnly"] as? Boolean ?: return null
            val thresholdValue = m["threshold"]
            if (thresholdValue != null && thresholdValue !is Number) return null
            val threshold = (thresholdValue as? Number)?.toDouble() ?: 100.0
            if (
                !hash.matches(source) ||
                    !hash.matches(account) ||
                    !hash.matches(token) ||
                    !threshold.isFinite() ||
                    threshold <= 0 ||
                    threshold > 100
            )
                return null
            fun minute(key: String): Int? =
                (m[key] as? Number)
                    ?.takeIf { it.toDouble() % 1.0 == 0.0 && it.toDouble() in 0.0..1439.0 }
                    ?.toInt()
            val start = minute("quietStart")
            val end = minute("quietEnd")
            if (
                (m["quietStart"] != null && (start == null || start !in 0..1439)) ||
                    (m["quietEnd"] != null && (end == null || end !in 0..1439))
            )
                return null
            val raw = m["alerted"] as? Map<*, *> ?: return null
            if (
                raw.size > 64 ||
                    raw.any { (k, v) ->
                        k !is String || v !is String || !hash.matches(k) || !hash.matches(v)
                    }
            )
                return null
            return CollectorRule(
                source,
                account,
                token,
                notifications,
                threshold,
                wifiOnly,
                start,
                end,
                raw.entries.associate { it.key as String to it.value as String },
            )
        }
    }
}

/**
 * The migrated `oc.quotaMonitor.<profile>` store. A refused or unreadable store never grants
 * consent.
 */
public class CollectorRules(
    private val prefs: KeyValueStore,
    private val lane: (ProfileId) -> WriterLane,
    private val readable: (ProfileId) -> Boolean,
    private val randomToken: () -> String,
) {
    private val lock = Mutex()

    public fun rule(id: ProfileId, provider: QuotaProvider): CollectorRule? {
        if (!readable(id) || !quotaCollectionAvailable(provider)) return null
        return rules(id)?.get(provider.wire)?.let(CollectorRule::parse)
    }

    public suspend fun enroll(
        id: ProfileId,
        source: String,
        snapshot: ProviderQuotaSnapshot,
        now: Instant,
        notifications: Boolean,
        threshold: Double,
    ): Outcome<Unit> {
        val account = snapshot.account.ref
        if (
            !snapshot.canShowWindows ||
                snapshot.freshness != QuotaFreshness.Fresh ||
                snapshot.isStale(now) ||
                snapshot.fetchedAt > now ||
                snapshot.windows.any { it.resetsAt != null && it.resetsAt!! <= now } ||
                account == null ||
                !quotaCollectionAvailable(snapshot.provider)
        )
            return Outcome.Failed(Failure.UnreadableReply())
        return save(
            id,
            snapshot.provider,
            CollectorRule(source, account, randomToken(), notifications, threshold),
        )
    }

    public suspend fun save(
        id: ProfileId,
        provider: QuotaProvider,
        value: CollectorRule?,
    ): Outcome<Unit> = lock.withLock {
        if (!readable(id) || (value != null && CollectorRule.parse(value.toJson()) == null))
            return@withLock Outcome.Failed(Failure.StorageRefused())
        val existing = rules(id) ?: return@withLock Outcome.Failed(Failure.StorageRefused())
        val next = LinkedHashMap(existing)
        if (value == null) next.remove(provider.wire) else next[provider.wire] = value.toJson()
        prefs.commit(lane(id)) {
            if (next.isEmpty()) remove(key(id))
            else putString(key(id), DartJson.encode(mapOf("version" to 1, "rules" to next)))
        }
    }

    private fun rules(id: ProfileId): Map<String, Any?>? {
        val raw = prefs.string(key(id)) ?: return emptyMap()
        if (raw.length > 65536) return null
        val m = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        if ((m["version"] as? Number)?.toDouble() != 1.0) return null
        val rules = m["rules"] as? Map<*, *> ?: return null
        if (
            rules.keys.any { it !is String } || rules.values.any { CollectorRule.parse(it) == null }
        )
            return null
        return rules.entries.associate { it.key as String to it.value }
    }

    public companion object {
        public const val PREFIX: String = "oc.quotaMonitor."

        public fun key(id: ProfileId): String = PREFIX + id.raw

        public fun register(scope: ProfileScope) {
            scope.register(
                KeyShape(
                    PREFIX,
                    KeyScope.Profile,
                    PrefType.Str,
                    ":engine:integrations-quota",
                    "explicit collector consent and alert rules",
                )
            )
        }
    }
}
