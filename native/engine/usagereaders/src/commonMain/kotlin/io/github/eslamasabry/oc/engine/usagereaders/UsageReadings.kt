package io.github.eslamasabry.oc.engine.usagereaders

/*
 * What the usage readers hand to the Usage model (Flutter `lib/domain/usage_subscriptions.dart`, source-side types).
 * Display numbers and plain states only: no server text, no credential, no email. The plan-card model (merge, alerts,
 * widget) lives in another module and maps these values; nothing here depends on it.
 */

/** How a provider is signed in. [Plan] is an account sign-in; [ApiKey] is a pay-as-you-go key with no plan limits. */
public enum class SignInMethod { Plan, ApiKey, Unknown }

public enum class SignInState { SignedIn, Expired, NotSignedIn }

public enum class UsageReadingStatus { Ok, NoLimits, AuthRequired, NotSignedIn }

/** Why the last numbers could not be refreshed. */
public enum class UsageProblem { RateLimited, Broken, Offline }

public enum class UsageWindowKind { FiveHour, Weekly, Monthly, Other }

/** One limit window. [usedPercent] is clamped to 0..100; [kind] follows the length first, then the id. */
public class UsageWindow(
    public val id: String,
    usedPercent: Double,
    public val durationSeconds: Long? = null,
    public val resetsAtMs: Long? = null,
    public val label: String = "",
) {
    public val usedPercent: Double = usedPercent.coerceIn(0.0, 100.0)
    public val kind: UsageWindowKind = kindOf(id, durationSeconds)
    public val leftPercent: Double get() = 100 - usedPercent

    override fun toString(): String = "UsageWindow($id, $kind, $usedPercent)"

    private companion object {
        fun kindOf(id: String, seconds: Long?): UsageWindowKind {
            if (seconds == 18_000L) return UsageWindowKind.FiveHour
            if (seconds == 604_800L) return UsageWindowKind.Weekly
            if (seconds != null && seconds >= 28L * 86_400 && seconds <= 31L * 86_400) return UsageWindowKind.Monthly
            return when (id) {
                "5h", "five_hour" -> UsageWindowKind.FiveHour
                "weekly", "seven_day", "week" -> UsageWindowKind.Weekly
                "monthly", "month" -> UsageWindowKind.Monthly
                else -> UsageWindowKind.Other
            }
        }
    }
}

/** One provider as one connection reports it. [accountRef] is an opaque hash, never an email or token. */
public data class UsageReading(
    val providerId: String,
    val displayName: String,
    val connectionId: String,
    val status: UsageReadingStatus,
    val fetchedAtMs: Long,
    val windows: List<UsageWindow> = emptyList(),
    val accountRef: String? = null,
    val plan: String? = null,
    val method: SignInMethod = SignInMethod.Unknown,
)

/** What a connection says about one provider's sign-in. Says nothing about numbers. */
public data class UsageSignIn(
    val providerId: String,
    val connectionId: String,
    val state: SignInState = SignInState.SignedIn,
    val method: SignInMethod = SignInMethod.Unknown,
    val displayName: String = "",
)

/** A reader tried and failed; no text from the failure is kept. */
public data class UsageFailure(val connectionId: String, val providerId: String, val problem: UsageProblem)

/** One source's answer for one refresh. */
public data class UsageSourceResult(
    val readings: List<UsageReading> = emptyList(),
    val signIns: List<UsageSignIn> = emptyList(),
    val failures: List<UsageFailure> = emptyList(),
    /** Connections that did not answer: their state is unknown (offline). */
    val unreached: Set<String> = emptySet(),
    /** Connections whose [signIns] answer was complete. */
    val authoritativeSignIns: Set<String> = emptySet(),
)

/** What one refresh asks of a source. */
public class UsageReadContext(
    public val nowMs: Long,
    public val force: Boolean = false,
    public val background: Boolean = false,
    /** The cached reading for (connectionId, providerId), if any. */
    public val lastReading: ((String, String) -> UsageReading?)? = null,
    /** Provider ids signed in on a connection as other sources reported them. */
    public val signedIn: ((String) -> Set<String>)? = null,
)

/** One reader of plan usage. */
public interface UsageSource {
    public suspend fun read(context: UsageReadContext): UsageSourceResult
}
