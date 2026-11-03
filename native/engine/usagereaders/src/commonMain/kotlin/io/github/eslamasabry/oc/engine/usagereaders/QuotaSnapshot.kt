package io.github.eslamasabry.oc.engine.usagereaders

/*
 * The quota collector's snapshot (Flutter `lib/domain/provider_quota.dart`). Strict: anything the app cannot trust
 * is refused as a whole, so a buggy collector never shows another account's numbers.
 */

/** Declaration order is the Flutter order (`QuotaProvider.values`); lists built from it keep that order. */
public enum class QuotaProvider(public val wire: String) {
    Codex("codex"), Claude("claude"), Minimax("minimax"), Glm("glm");

    /** A parser or credential format is not permission to collect: Claude stays off. */
    public val collectionAvailable: Boolean get() = this == Codex || this == Minimax || this == Glm

    /** The proper name the readers give the provider. */
    public val displayName: String
        get() = when (this) {
            Codex -> "Codex"
            Claude -> "Claude"
            Minimax -> "MiniMax"
            Glm -> "GLM"
        }

    internal val source: String
        get() = when (this) {
            Codex -> "codex.wham"
            Claude -> "claude.oauth"
            Minimax -> "minimax.tokenPlan"
            Glm -> "glm.codingPlan"
        }

    public companion object {
        public fun fromWire(raw: Any?): QuotaProvider? = entries.firstOrNull { it.wire == raw }
    }
}

public enum class ProviderQuotaStatus(internal val wire: String) {
    Ok("ok"), Unconfigured("unconfigured"), Unsupported("unsupported"), AuthRequired("authRequired"),
    RateLimited("rateLimited"), Unavailable("unavailable"), InvalidResponse("invalidResponse"),
}

public enum class QuotaAccountStatus(internal val wire: String) {
    Matched("matched"), Unverified("unverified"), Mismatch("mismatch"), SourceBound("sourceBound"),
}

public enum class QuotaFreshness(internal val wire: String) { Fresh("fresh"), Stale("stale"), None("none") }

public enum class QuotaWindowStatus(internal val wire: String) { Reported("reported"), Missing("missing") }

/** [ref] is an opaque collector reference (64 hex), for identity comparison only. */
public class ProviderQuotaAccount internal constructor(
    public val ref: String?,
    public val status: QuotaAccountStatus,
    public val plan: String?,
) {
    override fun toString(): String = "ProviderQuotaAccount($status)"
}

public class ProviderQuotaWindow internal constructor(
    public val id: String,
    public val status: QuotaWindowStatus,
    public val usedPercent: Double?,
    public val durationSeconds: Long?,
    public val resetsAtMs: Long?,
)

public class ProviderQuotaSnapshot internal constructor(
    public val provider: QuotaProvider,
    public val status: ProviderQuotaStatus,
    public val freshness: QuotaFreshness,
    public val fetchedAtMs: Long,
    public val expiresAtMs: Long,
    public val account: ProviderQuotaAccount,
    public val ordinaryUsageAllowed: Boolean?,
    public val windows: List<ProviderQuotaWindow>,
) {
    public val canShowWindows: Boolean
        get() = status == ProviderQuotaStatus.Ok &&
            (
                account.status == QuotaAccountStatus.Matched ||
                    (provider != QuotaProvider.Codex && account.status == QuotaAccountStatus.SourceBound)
                )

    override fun toString(): String = "ProviderQuotaSnapshot($status, ${windows.size} windows)"

    public companion object {
        private val REF = Regex("^[a-f0-9]{64}$")
        private val WINDOW_ID = Regex("^[a-zA-Z0-9._/-]{1,64}$")
        private val PLANS = setOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")
        private const val MAX_MS = 8_640_000_000_000_000L

        /** Null when the value is not a snapshot the app can trust (Flutter throws FormatException). */
        public fun parse(value: Any?): ProviderQuotaSnapshot? = try {
            parseOrThrow(value)
        } catch (e: InvalidSnapshot) {
            null
        }

        private class InvalidSnapshot : Exception()

        private fun invalid(): Nothing = throw InvalidSnapshot()

        @Suppress("UNCHECKED_CAST")
        private fun obj(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: invalid()

        private fun <T> enumOf(values: List<T>, raw: Any?, wire: (T) -> String): T =
            values.firstOrNull { wire(it) == raw } ?: invalid()

        private fun timestamp(value: Any?): Long {
            if (value !is Long || value < 0 || value > MAX_MS) invalid()
            return value
        }

        @Suppress("CyclomaticComplexMethod", "LongMethod")
        private fun parseOrThrow(value: Any?): ProviderQuotaSnapshot {
            val json = obj(value)
            val provider = QuotaProvider.fromWire(json["provider"]) ?: invalid()
            if (json["schemaVersion"] != 1L || json["source"] != provider.source) invalid()
            val status = enumOf(ProviderQuotaStatus.entries, json["status"]) { it.wire }
            val freshness = enumOf(QuotaFreshness.entries, json["freshness"]) { it.wire }
            val fetchedAt = timestamp(json["fetchedAtMs"])
            val expiresAt = timestamp(json["expiresAtMs"])
            if (expiresAt < fetchedAt) invalid()

            val rawAccount = obj(json["account"])
            val accountStatus = enumOf(QuotaAccountStatus.entries, rawAccount["status"]) { it.wire }
            val ref = rawAccount["ref"]
            if (ref != null && (ref !is String || !REF.matches(ref))) invalid()
            val bound = accountStatus == QuotaAccountStatus.Matched || accountStatus == QuotaAccountStatus.SourceBound
            if (bound && ref == null) invalid()
            if (accountStatus == QuotaAccountStatus.SourceBound && provider == QuotaProvider.Codex) invalid()
            val plan = rawAccount["plan"]
            // Unknown plan names must not become provider-controlled copy.
            val safePlan = if (plan is String && plan in PLANS) plan else null
            val allowed = json["ordinaryUsageAllowed"]
            if (allowed != null && allowed !is Boolean) invalid()
            if (provider != QuotaProvider.Codex && allowed != null) invalid()

            val rawWindows = json["windows"]
            if (rawWindows !is List<*> || rawWindows.size > 64) invalid()
            val ids = HashSet<String>()
            val windows = rawWindows.map { item ->
                val window = obj(item)
                val id = window["id"]
                if (id !is String || !WINDOW_ID.matches(id) || !ids.add(id)) invalid()
                val windowStatus = enumOf(QuotaWindowStatus.entries, window["status"]) { it.wire }
                val percent = when (val raw = window["usedPercent"]) {
                    null -> null
                    is Long -> raw.toDouble()
                    is Double -> raw
                    else -> invalid()
                }
                if (percent != null && (!percent.isFinite() || percent < 0 || percent > 100)) invalid()
                if ((windowStatus == QuotaWindowStatus.Reported) != (percent != null)) invalid()
                val duration = window["durationSeconds"]
                if (duration != null && (duration !is Long || duration <= 0 || duration > 315_360_000L)) invalid()
                val reset = window["resetsAtMs"]
                ProviderQuotaWindow(
                    id = id,
                    status = windowStatus,
                    usedPercent = percent,
                    durationSeconds = duration as Long?,
                    resetsAtMs = if (reset == null) null else timestamp(reset),
                )
            }
            // Never display another account's quotas, even if a collector sends numbers with an error status.
            val attributed = accountStatus == QuotaAccountStatus.Matched ||
                (provider != QuotaProvider.Codex && accountStatus == QuotaAccountStatus.SourceBound)
            if ((status != ProviderQuotaStatus.Ok || !attributed) && (windows.isNotEmpty() || allowed != null)) invalid()
            return ProviderQuotaSnapshot(
                provider = provider,
                status = status,
                freshness = freshness,
                fetchedAtMs = fetchedAt,
                expiresAtMs = expiresAt,
                account = ProviderQuotaAccount(ref as String?, accountStatus, safePlan),
                ordinaryUsageAllowed = allowed as Boolean?,
                windows = windows,
            )
        }
    }
}

/** A reading that says only how the sign-in stands, with no numbers. */
public fun usageBareReading(
    connectionId: String,
    provider: QuotaProvider,
    status: UsageReadingStatus,
    nowMs: Long,
): UsageReading = UsageReading(
    providerId = provider.wire,
    displayName = provider.displayName,
    connectionId = connectionId,
    status = status,
    fetchedAtMs = nowMs,
    method = SignInMethod.Plan,
)

/** The display numbers of a quota snapshot as one connection's reading. */
public fun usageReadingFromSnapshot(
    connectionId: String,
    provider: QuotaProvider,
    snapshot: ProviderQuotaSnapshot,
): UsageReading {
    val windows = snapshot.windows.mapNotNull { w ->
        w.usedPercent?.let { UsageWindow(w.id, it, w.durationSeconds, w.resetsAtMs) }
    }
    return UsageReading(
        providerId = provider.wire,
        displayName = provider.displayName,
        connectionId = connectionId,
        status = if (windows.isEmpty()) UsageReadingStatus.NoLimits else UsageReadingStatus.Ok,
        fetchedAtMs = snapshot.fetchedAtMs,
        windows = windows,
        accountRef = snapshot.account.ref,
        plan = snapshot.account.plan,
        method = SignInMethod.Plan,
    )
}
