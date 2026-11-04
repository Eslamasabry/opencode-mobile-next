package io.github.eslamasabry.oc.engine.integrations.quota

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

/** Optional deployment extension, not an upstream OpenCode API route (Flutter `providerQuotaPath`). */
public const val PROVIDER_QUOTA_PATH: String = "/ocmn/quota/v1"

/** The wire names are the Dart enum names. */
public enum class QuotaProvider(public val wire: String, internal val source: String) {
    Codex("codex", "codex.wham"),
    Claude("claude", "claude.oauth"),
    Minimax("minimax", "minimax.tokenPlan"),
    Glm("glm", "glm.codingPlan"),
}

/** A parser or historical credential format is not permission to collect data: Claude stays off. */
public fun quotaCollectionAvailable(provider: QuotaProvider): Boolean = provider != QuotaProvider.Claude

public fun quotaPathFor(provider: QuotaProvider): String =
    when (provider) {
        QuotaProvider.Codex -> PROVIDER_QUOTA_PATH
        QuotaProvider.Claude -> "$PROVIDER_QUOTA_PATH/claude"
        QuotaProvider.Minimax -> "$PROVIDER_QUOTA_PATH/minimax"
        QuotaProvider.Glm -> "$PROVIDER_QUOTA_PATH/glm"
    }

public enum class ProviderQuotaStatus(public val wire: String) {
    Ok("ok"), Unconfigured("unconfigured"), Unsupported("unsupported"), AuthRequired("authRequired"),
    RateLimited("rateLimited"), Unavailable("unavailable"), InvalidResponse("invalidResponse"),
}

public enum class QuotaAccountStatus(public val wire: String) {
    Matched("matched"), Unverified("unverified"), Mismatch("mismatch"), SourceBound("sourceBound"),
}

public enum class QuotaFreshness(public val wire: String) { Fresh("fresh"), Stale("stale"), None("none") }

public enum class QuotaWindowStatus(public val wire: String) { Reported("reported"), Missing("missing") }

public enum class QuotaFailureKind(public val wire: String) {
    CollectorAuth("collectorAuth"), Unsupported("unsupported"), Unavailable("unavailable"), InvalidResponse("invalidResponse"),
}

/** Contains no raw HTTP error, credential, URL, or provider response. */
public data class ProviderQuotaFailure(val kind: QuotaFailureKind) {
    override fun toString(): String = "ProviderQuotaFailure(${kind.wire})"
}

/** [ref] is an opaque collector reference for identity comparisons only, never an email or token, never shown. */
public class ProviderQuotaAccount internal constructor(
    public val ref: String?,
    public val status: QuotaAccountStatus,
    public val plan: String?,
) {
    override fun toString(): String = "ProviderQuotaAccount(${status.wire})"
}

public class ProviderQuotaWindow internal constructor(
    public val id: String,
    public val status: QuotaWindowStatus,
    public val usedPercent: Double?,
    public val durationSeconds: Long?,
    public val resetsAt: Instant?,
) {
    public val remainingPercent: Double? get() = usedPercent?.let { 100 - it }
}

/**
 * Provider windows, separate from OpenCode project spend. Source-bound snapshots identify the collector's configured
 * credential, not an independently returned account. Immutable: built from a parsed JSON tree, windows are an
 * [ImmutableList].
 */
public class ProviderQuotaSnapshot internal constructor(
    public val provider: QuotaProvider,
    public val status: ProviderQuotaStatus,
    public val freshness: QuotaFreshness,
    public val fetchedAt: Instant,
    public val expiresAt: Instant,
    public val account: ProviderQuotaAccount,
    public val ordinaryUsageAllowed: Boolean?,
    public val windows: ImmutableList<ProviderQuotaWindow>,
) {
    /** Expiry is inclusive; a passed reset never replenishes a window. */
    public fun isStale(now: Instant): Boolean = freshness == QuotaFreshness.Stale || now >= expiresAt

    public val canShowWindows: Boolean
        get() =
            status == ProviderQuotaStatus.Ok &&
                (account.status == QuotaAccountStatus.Matched ||
                    (provider != QuotaProvider.Codex && account.status == QuotaAccountStatus.SourceBound))

    override fun toString(): String = "ProviderQuotaSnapshot(${status.wire}, ${windows.size} windows)"

    public companion object {
        /** Dart's DateTime range in milliseconds. */
        public const val MAX_TIMESTAMP_MS: Long = 8_640_000_000_000_000L
        private const val MAX_WINDOWS = 64
        private const val MAX_DURATION_SECONDS = 315_360_000L
        private val PLANS = setOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")
        private val REF = Regex("^[a-f0-9]{64}$")
        private val WINDOW_ID = Regex("^[a-zA-Z0-9._/-]{1,64}$")

        /** Null for anything invalid. The parser keeps no input text: there is nothing to echo. */
        public fun fromJson(value: JsonElement?): ProviderQuotaSnapshot? =
            try {
                parse(value)
            } catch (_: InvalidSnapshot) {
                null
            }

        private fun parse(value: JsonElement?): ProviderQuotaSnapshot {
            val json = obj(value)
            val provider = enumOf(QuotaProvider.entries, json["provider"]) { it.wire }
            if (int(json["schemaVersion"]) != 1L || string(json["source"]) != provider.source) invalid()
            val status = enumOf(ProviderQuotaStatus.entries, json["status"]) { it.wire }
            val freshness = enumOf(QuotaFreshness.entries, json["freshness"]) { it.wire }
            val fetchedAt = timestamp(json["fetchedAtMs"])
            val expiresAt = timestamp(json["expiresAtMs"])
            if (expiresAt < fetchedAt) invalid()

            val rawAccount = obj(json["account"])
            val accountStatus = enumOf(QuotaAccountStatus.entries, rawAccount["status"]) { it.wire }
            val rawRef = rawAccount["ref"]
            val ref: String? =
                if (rawRef == null || rawRef is JsonNull) null else string(rawRef)?.takeIf(REF::matches) ?: invalid()
            if ((accountStatus == QuotaAccountStatus.Matched || accountStatus == QuotaAccountStatus.SourceBound) && ref == null) {
                invalid()
            }
            if (accountStatus == QuotaAccountStatus.SourceBound && provider == QuotaProvider.Codex) invalid()
            // Unknown plan names must not become arbitrary provider-controlled copy.
            val plan = string(rawAccount["plan"])?.takeIf { it in PLANS }
            val rawAllowed = json["ordinaryUsageAllowed"]
            val allowed: Boolean? =
                if (rawAllowed == null || rawAllowed is JsonNull) null else bool(rawAllowed) ?: invalid()
            if (provider != QuotaProvider.Codex && allowed != null) invalid()

            val rawWindows = json["windows"] as? JsonArray ?: invalid()
            if (rawWindows.size > MAX_WINDOWS) invalid()
            val ids = HashSet<String>()
            val windows = ArrayList<ProviderQuotaWindow>(rawWindows.size)
            for (element in rawWindows) {
                val window = obj(element)
                val id = string(window["id"])
                if (id == null || !WINDOW_ID.matches(id) || !ids.add(id)) invalid()
                val windowStatus = enumOf(QuotaWindowStatus.entries, window["status"]) { it.wire }
                val rawPercent = window["usedPercent"]
                val percent: Double? =
                    if (rawPercent == null || rawPercent is JsonNull) {
                        null
                    } else {
                        val n = number(rawPercent) ?: invalid()
                        if (!n.isFinite() || n < 0 || n > 100) invalid()
                        n
                    }
                if ((windowStatus == QuotaWindowStatus.Reported) != (percent != null)) invalid()
                val rawDuration = window["durationSeconds"]
                val duration: Long? =
                    if (rawDuration == null || rawDuration is JsonNull) {
                        null
                    } else {
                        val d = int(rawDuration) ?: invalid()
                        if (d <= 0 || d > MAX_DURATION_SECONDS) invalid()
                        d
                    }
                val rawReset = window["resetsAtMs"]
                val reset = if (rawReset == null || rawReset is JsonNull) null else timestamp(rawReset)
                windows += ProviderQuotaWindow(id, windowStatus, percent, duration, reset)
            }
            // Never display another account's quotas, even if a buggy collector sends measurements with an error status
            // or a mismatched identity.
            val attributed =
                accountStatus == QuotaAccountStatus.Matched ||
                    (provider != QuotaProvider.Codex && accountStatus == QuotaAccountStatus.SourceBound)
            if ((status != ProviderQuotaStatus.Ok || !attributed) && (windows.isNotEmpty() || allowed != null)) invalid()
            return ProviderQuotaSnapshot(
                provider = provider,
                status = status,
                freshness = freshness,
                fetchedAt = fetchedAt,
                expiresAt = expiresAt,
                account = ProviderQuotaAccount(ref, accountStatus, plan),
                ordinaryUsageAllowed = allowed,
                windows = windows.toImmutableList(),
            )
        }

        private fun obj(value: JsonElement?): JsonObject = value as? JsonObject ?: invalid()

        private fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun bool(value: JsonElement?): Boolean? =
            (value as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.content?.let {
                when (it) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }

        /** A JSON integer (Dart `int`): no fraction, no exponent. */
        private fun int(value: JsonElement?): Long? {
            val p = (value as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull } ?: return null
            val text = p.content
            if (text.isEmpty() || text.any { it == '.' || it == 'e' || it == 'E' }) return null
            return text.toLongOrNull()
        }

        /** A JSON number (Dart `num`); booleans and strings are not numbers. NaN/Infinity come back non-finite. */
        private fun number(value: JsonElement?): Double? {
            val p = (value as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull } ?: return null
            if (p.content == "true" || p.content == "false") return null
            return p.content.toDoubleOrNull()
        }

        private fun timestamp(value: JsonElement?): Instant {
            val ms = int(value) ?: invalid()
            if (ms < 0 || ms > MAX_TIMESTAMP_MS) invalid()
            return Instant.fromEpochMilliseconds(ms)
        }

        private inline fun <T> enumOf(values: List<T>, raw: JsonElement?, wire: (T) -> String): T {
            val name = string(raw) ?: invalid()
            return values.firstOrNull { wire(it) == name } ?: invalid()
        }

        private fun invalid(): Nothing = throw InvalidSnapshot()
    }
}

/** Internal control flow of the parser; never leaves [ProviderQuotaSnapshot.fromJson] and carries no text. */
private class InvalidSnapshot : Exception()
