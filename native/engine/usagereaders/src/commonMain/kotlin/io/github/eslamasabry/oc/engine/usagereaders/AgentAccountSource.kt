package io.github.eslamasabry.oc.engine.usagereaders

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson

/** The account at a connected agent runtime (Flutter `AgentAccount`). Never carries a provider token. */
public data class AgentAccount(
    val type: String? = null,
    val email: String? = null,
    val plan: String? = null,
    val requiresSignIn: Boolean,
) {
    val signedIn: Boolean get() = type != null

    override fun toString(): String = "AgentAccount(type=$type, signedIn=$signedIn)"
}

public data class AccountRateWindow(val usedPercent: Int, val durationMinutes: Long?, val resetsAtMs: Long?)

public data class AccountRateBucket(val name: String?, val primary: AccountRateWindow?, val secondary: AccountRateWindow?)

/**
 * PORT. One account session at the open Codex connection (Flutter `AgentAccountSession`, read part only). A dropped
 * link is `Failure.Network` (Flutter `AgentAccountFailure.disconnected`); any other failure means the reader is broken.
 */
public interface AgentAccountSession {
    public suspend fun read(): Outcome<AgentAccount>

    public suspend fun rateLimits(): Outcome<List<AccountRateBucket>>

    public suspend fun close()
}

/** PORT. Opens an account session on a connection that is already open; it never connects or switches anything. */
public fun interface AgentAccountGateway {
    public fun openAccountSession(): AgentAccountSession
}

/** The open connection that supports an agent account. */
public data class OpenAgentAccount(val connectionId: String, val gateway: AgentAccountGateway)

/**
 * The Codex account at the connection that is open right now (Flutter `AgentAccountSource`): its 5-hour and weekly
 * limits, the plan and a hash of the email (never the email itself). The session is always closed.
 */
public class AgentAccountSource(private val current: () -> OpenAgentAccount?) : UsageSource {
    override suspend fun read(context: UsageReadContext): UsageSourceResult {
        val open = current() ?: return UsageSourceResult()
        val id = open.connectionId
        val session = open.gateway.openAccountSession()
        val outcome = attempt({ Failure.Unexpected() }) { readWith(session, id, context) }
        attempt({ Failure.Unexpected() }) { session.close() }
        return when (outcome) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> broken(id)
        }
    }

    private suspend fun readWith(session: AgentAccountSession, id: String, context: UsageReadContext): UsageSourceResult {
        val account = when (val read = session.read()) {
            is Outcome.Ok -> read.value
            is Outcome.Failed -> return failed(id, read.failure)
        }
        if (!account.signedIn) {
            val before = context.lastReading?.invoke(id, CODEX)
            return UsageSourceResult(
                readings = if (before != null || account.requiresSignIn) {
                    listOf(
                        UsageReading(
                            providerId = CODEX,
                            displayName = "Codex",
                            connectionId = id,
                            status = UsageReadingStatus.NotSignedIn,
                            fetchedAtMs = context.nowMs,
                            method = SignInMethod.Plan,
                        ),
                    )
                } else {
                    emptyList()
                },
            )
        }
        if ("key" in account.type.orEmpty().lowercase()) {
            return UsageSourceResult(
                signIns = listOf(UsageSignIn(CODEX, id, method = SignInMethod.ApiKey, displayName = "OpenAI")),
            )
        }
        val buckets = when (val limits = session.rateLimits()) {
            is Outcome.Ok -> limits.value
            is Outcome.Failed -> return failed(id, limits.failure)
        }
        val windows = buckets.firstOrNull()?.let { bucket ->
            listOf(bucket.primary, bucket.secondary).mapIndexedNotNull { index, w ->
                w?.let {
                    UsageWindow(
                        id = if (index == 0) "primary" else "secondary",
                        usedPercent = it.usedPercent.toDouble(),
                        durationSeconds = it.durationMinutes?.let { minutes -> minutes * 60 },
                        resetsAtMs = it.resetsAtMs,
                    )
                }
            }
        }.orEmpty()
        val email = account.email
        val plan = account.plan?.lowercase()
        return UsageSourceResult(
            readings = listOf(
                UsageReading(
                    providerId = CODEX,
                    displayName = "Codex",
                    connectionId = id,
                    status = if (windows.isEmpty()) UsageReadingStatus.NoLimits else UsageReadingStatus.Ok,
                    fetchedAtMs = context.nowMs,
                    windows = windows,
                    accountRef = if (email.isNullOrEmpty()) null else codexAccountRef(email),
                    plan = plan?.takeIf { it in PLANS },
                    method = SignInMethod.Plan,
                ),
            ),
        )
    }

    private fun failed(id: String, failure: Failure): UsageSourceResult =
        if (failure is Failure.Network) UsageSourceResult(unreached = setOf(id)) else broken(id)

    private fun broken(id: String) = UsageSourceResult(failures = listOf(UsageFailure(id, CODEX, UsageProblem.Broken)))

    public companion object {
        private const val CODEX = "codex"
        private val PLANS = setOf("free", "go", "plus", "pro", "team", "business", "enterprise", "edu")

        /** The opaque account reference: SHA-256 hex of `codex-account:<email>` (same as Flutter). */
        public fun codexAccountRef(email: String): String = DartJson.sha256Hex("codex-account:$email")
    }
}
