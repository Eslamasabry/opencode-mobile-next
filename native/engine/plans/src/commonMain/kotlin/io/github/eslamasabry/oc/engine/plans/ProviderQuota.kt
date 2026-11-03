package io.github.eslamasabry.oc.engine.plans

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome

// The collector's quota snapshot as the plans engine needs it (port of lib/domain/provider_quota.dart, minus the wire
// parser: the readers module validates the `/ocmn/quota/v1` JSON and hands over these values). Times are epoch ms.

public enum class QuotaProvider(public val id: String) {
    Codex("codex"),
    Claude("claude"),
    MiniMax("minimax"),
    Glm("glm"),
    ;

    public companion object {
        public fun byId(id: String): QuotaProvider? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A parser or historical credential format is not permission to collect data. Claude subscription collection stays off
 * pending a supported integration.
 */
public fun quotaCollectionAvailable(provider: QuotaProvider): Boolean = provider != QuotaProvider.Claude

public enum class ProviderQuotaStatus {
    Ok, Unconfigured, Unsupported, AuthRequired, RateLimited, Unavailable, InvalidResponse,
}

public enum class QuotaAccountStatus { Matched, Unverified, Mismatch, SourceBound }

public enum class QuotaFreshness { Fresh, Stale, None }

public enum class QuotaWindowStatus { Reported, Missing }

public enum class QuotaFailureKind { CollectorAuth, Unsupported, Unavailable, InvalidResponse }

/** An opaque collector-generated [ref] (64 hex), never an email or access token; for identity comparisons only. */
public data class ProviderQuotaAccount(
    val ref: String?,
    val status: QuotaAccountStatus,
    val plan: String? = null,
) {
    override fun toString(): String = "ProviderQuotaAccount($status)"
}

public data class ProviderQuotaWindow(
    val id: String,
    val status: QuotaWindowStatus,
    val usedPercent: Double? = null,
    val durationSeconds: Int? = null,
    val resetsAt: Long? = null,
) {
    val remainingPercent: Double? get() = usedPercent?.let { 100 - it }
}

public data class ProviderQuotaSnapshot(
    val provider: QuotaProvider,
    val status: ProviderQuotaStatus,
    val freshness: QuotaFreshness,
    val fetchedAt: Long,
    val expiresAt: Long,
    val account: ProviderQuotaAccount,
    val ordinaryUsageAllowed: Boolean? = null,
    val windows: List<ProviderQuotaWindow> = emptyList(),
) {
    public fun isStale(now: Long): Boolean = freshness == QuotaFreshness.Stale || now >= expiresAt

    val canShowWindows: Boolean
        get() = status == ProviderQuotaStatus.Ok &&
            (
                account.status == QuotaAccountStatus.Matched ||
                    (provider != QuotaProvider.Codex && account.status == QuotaAccountStatus.SourceBound)
                )

    override fun toString(): String = "ProviderQuotaSnapshot($status, ${windows.size} windows)"
}

/**
 * One short-lived read of a collector (port of ProviderQuotaGateway). Expected failures come back as [Outcome.Failed];
 * [quotaFailureKind] says how each [Failure] is read. [close] tears the connection down and may be called while a read
 * is pending (the late answer is then ignored).
 */
public interface ProviderQuotaGateway {
    public suspend fun readSnapshot(): Outcome<ProviderQuotaSnapshot>

    public fun close()
}

/**
 * How a gateway failure maps to the collector's failure kinds: a sign-in problem is [QuotaFailureKind.CollectorAuth];
 * an unavailable feature or incompatible server is [QuotaFailureKind.Unsupported]; an unreadable reply is
 * [QuotaFailureKind.InvalidResponse]; everything else (network, time-out, server error) is
 * [QuotaFailureKind.Unavailable].
 */
public fun quotaFailureKind(failure: Failure): QuotaFailureKind =
    when (failure) {
        is Failure.SignIn -> QuotaFailureKind.CollectorAuth
        is Failure.Unavailable, is Failure.Incompatible -> QuotaFailureKind.Unsupported
        is Failure.UnreadableReply -> QuotaFailureKind.InvalidResponse
        else -> QuotaFailureKind.Unavailable
    }

/**
 * A saved server as the quota monitor sees it. [credentialStamp] is an opaque, process-local value that changes when
 * the password or its re-entry state changes (the integrator derives it, e.g. an HMAC with a per-process salt); the
 * engine never sees the password itself.
 */
public data class QuotaProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val credentialStamp: String,
    val isOpenCode: Boolean = true,
) {
    override fun toString(): String = "QuotaProfile($id)"
}
