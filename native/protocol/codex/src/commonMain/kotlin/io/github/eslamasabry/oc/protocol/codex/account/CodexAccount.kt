package io.github.eslamasabry.oc.protocol.codex.account

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.RefusalReason

/*
 * Account values of the connected official agent runtime (port of lib/domain/agent_account.dart). Credentials stay at
 * that runtime: none of these types carries a provider token.
 */

/** Dart `AgentAccountFailure`. */
public enum class AccountFailureKind { Unavailable, InvalidResponse, Disconnected, Uncertain }

/** Internal throwable of the account session; its message is fixed. */
public class AccountException(public val kind: AccountFailureKind) : Exception("Agent account operation failed (${kind.name}).") {
    public fun toFailure(): Failure =
        when (kind) {
            AccountFailureKind.Unavailable -> Failure.Unavailable(FeatureId("agentAccount"))
            AccountFailureKind.InvalidResponse -> Failure.UnreadableReply()
            AccountFailureKind.Disconnected -> Failure.Network(NetworkWhy.Reset)
            AccountFailureKind.Uncertain -> Failure.Refused(RefusalReason.DispatchUncertain)
        }
}

/** Reads the account failure kind back from [AccountException.toFailure]'s value. */
public fun Failure.accountKind(): AccountFailureKind? =
    when (this) {
        is Failure.Unavailable -> AccountFailureKind.Unavailable.takeIf { feature.name == "agentAccount" }
        is Failure.UnreadableReply -> AccountFailureKind.InvalidResponse
        is Failure.Network -> AccountFailureKind.Disconnected
        is Failure.Refused -> AccountFailureKind.Uncertain.takeIf { reason == RefusalReason.DispatchUncertain }
        else -> null
    }

public data class AgentAccount(
    val type: String? = null,
    val email: String? = null,
    val plan: String? = null,
    val requiresSignIn: Boolean,
) {
    val signedIn: Boolean get() = type != null
}

/** [resetsAtMs] epoch ms. A reported null stays null; a reported zero stays zero (B-AGENT-054). */
public data class AccountRateWindow(val usedPercent: Long, val durationMinutes: Long?, val resetsAtMs: Long?)

public data class AccountRateBucket(val name: String?, val primary: AccountRateWindow?, val secondary: AccountRateWindow?)

public data class AccountTokenUsage(val lifetimeTokens: Long? = null, val peakDailyTokens: Long? = null)

/** One session owns this handle. Never persist or log it or its user code; `toString` is redacted. */
public class AccountDeviceCode(public val verificationUrl: String, public val userCode: String) {
    override fun toString(): String = "AccountDeviceCode(redacted)"
}

public enum class AccountEventKind { Changed, LimitsChanged, LoginCompleted, LoginCancelled, Disconnected }

public data class AccountEvent(val kind: AccountEventKind, val success: Boolean = false)
