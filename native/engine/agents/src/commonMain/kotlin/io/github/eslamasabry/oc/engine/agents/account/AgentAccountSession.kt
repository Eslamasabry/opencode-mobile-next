package io.github.eslamasabry.oc.engine.agents.account

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

// Port of lib/domain/agent_account.dart: optional account management at the connected official agent runtime.
// Credentials stay at that runtime; these types never carry provider tokens.

/** The runtime's account. [type] null means signed out (`chatgpt`, `apiKey`, …). */
public data class AgentRuntimeAccount(
    val type: String? = null,
    val email: String? = null,
    val plan: String? = null,
    val requiresSignIn: Boolean,
) {
    val signedIn: Boolean get() = type != null

    override fun toString(): String = "AgentRuntimeAccount(type=$type, signedIn=$signedIn)" // no email in logs
}

public data class AccountRateWindow(val usedPercent: Int, val durationMinutes: Int?, val resetsAtEpochMillis: Long?)

public data class AccountRateBucket(val name: String?, val primary: AccountRateWindow?, val secondary: AccountRateWindow?)

/** A reported zero is a real zero, distinct from null (not reported). */
public data class AccountTokenUsage(val lifetimeTokens: Long? = null, val peakDailyTokens: Long? = null)

/** One session owns this handle. Never persist or log it or its user code. */
public class AccountDeviceCode(public val verificationUrl: String, public val userCode: String) {
    override fun toString(): String = "AccountDeviceCode(redacted)"
}

public enum class AccountEventKind { Changed, LimitsChanged, LoginCompleted, LoginCancelled, Disconnected }

public data class AccountEvent(val kind: AccountEventKind, val success: Boolean = false)

/**
 * Bound to one location and transport epoch; mutations never reconnect (Dart `AgentAccountSession`). Expected failures
 * are [Outcome.Failed]: `Failure.Unavailable` = the runtime has no account API, `Failure.Network` /
 * `Failure.ComputerDidNotAnswer` = disconnected, `Failure.Refused(DispatchUncertain)` = the runtime may have acted.
 */
public interface AgentAccountSession {
    public val active: Boolean
    public val supported: Boolean
    public val events: Flow<AccountEvent>

    public suspend fun read(): Outcome<AgentRuntimeAccount>

    public suspend fun rateLimits(): Outcome<ImmutableList<AccountRateBucket>>

    public suspend fun usage(): Outcome<AccountTokenUsage>

    public suspend fun startDeviceLogin(): Outcome<AccountDeviceCode>

    /** Cancels only this session's outstanding attempt. False means not confirmed; never a sign-out. */
    public suspend fun cancelLogin(): Outcome<Boolean>

    public suspend fun close()
}
