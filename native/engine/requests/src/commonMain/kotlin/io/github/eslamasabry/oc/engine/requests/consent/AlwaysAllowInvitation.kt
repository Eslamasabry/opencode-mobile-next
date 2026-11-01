package io.github.eslamasabry.oc.engine.requests.consent

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The exact scope an "Always allow" grant would cover (port of Dart `PermissionConsentScope`): the permission, its
 * exact patterns and its `always` patterns. Pattern order does not matter; the conversation does not either (the
 * third identical ask counts across conversations), but the same request seen twice counts once.
 */
public data class ConsentScope(
    val sessionId: String,
    val permission: String,
    val patterns: List<String>,
    val alwaysPatterns: List<String> = emptyList(),
) {
    internal val key: String get() = digest(listOf(permission, patterns.sorted(), alwaysPatterns.sorted()))
}

public enum class OfferDecision { NotOffered, Offered, Accepted, Declined }

public data class ConsentStatus(
    /** True exactly once: on the third distinct ask of this scope. Show the one-line invitation under the card. */
    val offerAlwaysAllow: Boolean = false,
    val distinctAsks: Int = 0,
    val decision: OfferDecision = OfferDecision.NotOffered,
    /** False when the record is corrupt, full or unwritable: offers are suppressed, never guessed. */
    val storageAvailable: Boolean = true,
) {
    /** A declined invitation is explained once in Settings › Always allowed actions. */
    val explainDeclinedInSettings: Boolean get() = decision == OfferDecision.Declined
}

/** The `oc.permissionConsent.<profileId>` string (05 Appendix A; format unchanged from Flutter). */
public interface ConsentStorage {
    public suspend fun read(): Outcome<String?>

    public suspend fun write(json: String): Outcome<Unit>
}

/**
 * The "Always allow" invitation (plan 09 §5.3, map CHAT-GENUI-46, B-SEC-147; port of Dart
 * `repeated_permission_consent.dart`): the third distinct identical ask offers "Always allow" once per server and scope,
 * only where the server keeps grants, never while the conversation auto-approves. The claim is written before the
 * offer is returned, so a replay or a restart can never offer it again; it is recorded as accepted only after the
 * server took the `always` reply ([recordDecision] after the reply). Only digests are stored — never a command, path or
 * pattern. 256 scopes / 128 KiB; a corrupt, oversized or unwritable record suppresses every offer until restart.
 *
 * One instance per profile, owned by the requests engine; every call is serialized.
 */
public class AlwaysAllowInvitation(private val storage: ConsentStorage) {
    private val lane = Mutex()
    private var blocked = false

    public suspend fun observe(
        scope: ConsentScope,
        requestId: String,
        supportsPersistentGrants: Boolean,
        autoApproving: Boolean,
    ): ConsentStatus =
        serialized {
            if (!supportsPersistentGrants || autoApproving || requestId.isEmpty() || scope.sessionId.isEmpty() ||
                scope.permission.isEmpty()
            ) {
                return@serialized ConsentStatus()
            }
            val entries = read() ?: return@serialized UNAVAILABLE
            val key = scope.key
            val entry = entries[key] ?: Entry(mutableListOf(), OfferDecision.NotOffered)
            if (entry.decision != OfferDecision.NotOffered) return@serialized entry.status()
            val request = digest(listOf(scope.sessionId, requestId))
            if (request in entry.requests) return@serialized entry.status()
            if (key !in entries && entries.size >= LIMIT) return@serialized UNAVAILABLE
            entry.requests += request
            val offer = entry.requests.size == OFFER_AT
            if (offer) entry.decision = OfferDecision.Offered
            entries[key] = entry
            if (!write(entries)) return@serialized UNAVAILABLE
            entry.status(offer)
        }

    public suspend fun status(scope: ConsentScope): ConsentStatus =
        serialized { read()?.get(scope.key)?.status() ?: if (blocked) UNAVAILABLE else ConsentStatus() }

    /** After the server took "always" ([accepted]) or the person chose "Keep asking". Only an open offer changes. */
    public suspend fun recordDecision(scope: ConsentScope, accepted: Boolean): ConsentStatus =
        serialized {
            val entries = read() ?: return@serialized UNAVAILABLE
            val entry = entries[scope.key] ?: return@serialized ConsentStatus()
            if (entry.decision != OfferDecision.Offered) return@serialized entry.status()
            entry.decision = if (accepted) OfferDecision.Accepted else OfferDecision.Declined
            if (!write(entries)) return@serialized UNAVAILABLE
            entry.status()
        }

    /** Scopes offered or declined but never accepted (Settings counts them); null when unreadable. */
    public suspend fun declinedCount(): Int? =
        lane.withLock { read()?.values?.count { it.notAccepted } }

    public suspend fun forgetDeclined(): Boolean =
        lane.withLock {
            val entries = read() ?: return@withLock false
            entries.values.removeAll { it.notAccepted }
            write(entries)
        }

    private suspend fun serialized(block: suspend () -> ConsentStatus): ConsentStatus =
        lane.withLock { if (blocked) UNAVAILABLE else block() }

    private suspend fun read(): LinkedHashMap<String, Entry>? {
        if (blocked) return null
        val raw =
            when (val o = storage.read()) {
                is Outcome.Ok -> o.value ?: return LinkedHashMap()
                is Outcome.Failed -> return block()
            }
        if (raw.length > MAX_CHARS) return block()
        val parsed = parse(raw) ?: return block()
        return parsed
    }

    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: String): LinkedHashMap<String, Entry>? {
        val json = DartJson.decodeOrNull(raw) as? Map<String, Any?> ?: return null
        if ((json["version"] as? Number)?.toDouble() != 1.0) return null
        val rows = json["entries"] as? Map<String, Any?> ?: return null
        if (rows.size > LIMIT) return null
        val out = LinkedHashMap<String, Entry>()
        for ((key, value) in rows) {
            if (!isDigest(key)) return null
            val row = value as? Map<String, Any?> ?: return null
            val requests = (row["requests"] as? List<Any?>)?.map { it as? String ?: return null } ?: return null
            val decision = OfferDecision.entries.firstOrNull { it.wire == row["decision"] } ?: return null
            if (requests.isEmpty() || requests.size > OFFER_AT || requests.any { !isDigest(it) } ||
                requests.toSet().size != requests.size ||
                (requests.size == OFFER_AT) != (decision != OfferDecision.NotOffered)
            ) {
                return null
            }
            out[key] = Entry(requests.toMutableList(), decision)
        }
        return out
    }

    private suspend fun write(entries: Map<String, Entry>): Boolean {
        val encoded =
            DartJson.encode(
                linkedMapOf(
                    "version" to 1,
                    "entries" to entries.mapValues { (_, e) -> linkedMapOf("requests" to e.requests, "decision" to e.decision.wire) },
                ),
            )
        if (storage.write(encoded) is Outcome.Ok) return true
        blocked = true
        return false
    }

    private fun block(): LinkedHashMap<String, Entry>? {
        blocked = true
        return null
    }

    private class Entry(val requests: MutableList<String>, var decision: OfferDecision) {
        val notAccepted: Boolean get() = decision == OfferDecision.Declined || decision == OfferDecision.Offered

        fun status(offer: Boolean = false) = ConsentStatus(offerAlwaysAllow = offer, distinctAsks = requests.size, decision = decision)
    }

    public companion object {
        private const val LIMIT = 256
        private const val MAX_CHARS = 128 * 1024
        private const val OFFER_AT = 3
        private val UNAVAILABLE = ConsentStatus(storageAvailable = false)
        private val PROFILE_ID = Regex("^[A-Za-z0-9_-]{1,128}$")

        /** `oc.permissionConsent.<profileId>`; register it with `ProfileScope` (rule 9). */
        public fun storageKey(profileId: String): String {
            require(PROFILE_ID.matches(profileId)) { "Invalid profile identifier" }
            return "oc.permissionConsent.$profileId"
        }
    }
}

/** Dart enum `.name`. */
private val OfferDecision.wire: String get() = name.replaceFirstChar { it.lowercase() }

private fun digest(value: Any): String = DartJson.sha256Hex(DartJson.encode(value))

private val DIGEST = Regex("^[0-9a-f]{64}$")

private fun isDigest(value: String): Boolean = DIGEST.matches(value)
