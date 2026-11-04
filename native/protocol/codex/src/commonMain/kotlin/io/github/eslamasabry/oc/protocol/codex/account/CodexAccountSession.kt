package io.github.eslamasabry.oc.protocol.codex.account

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.protocol.codex.CodexException
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.boolOrNull
import io.github.eslamasabry.oc.protocol.codex.intOrNull
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.stringOrNull
import io.github.eslamasabry.oc.protocol.codex.transport.CodexRpcEvent
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransportListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * The stable account API of the verified 0.153.4 schema (port of lib/codex/account.dart). Bound to one location and
 * one transport epoch: it never reconnects, and a mutation never crosses a scope change (B-AGENT-051…054).
 *
 * - Reads never refresh provider tokens (`refreshToken: false`).
 * - Only one device login may be outstanding; the session owns only the login id the server returned.
 * - Cancellation is fenced: an old epoch, a lost start receipt, scope loss or an account change cannot cancel or
 *   replay a different login; an unconfirmed cancel keeps ownership for an explicit retry.
 */
public class CodexAccountSession(
    private val transport: CodexTransport,
    private val scopeValid: () -> Boolean,
) {
    private val epoch = transport.epoch
    private val listeners = mutableListOf<(AccountEvent) -> Unit>()
    private var closed = false
    private var starting = false
    private var cancelRequested = false
    private var loginId: String? = null
    private var untrackedStart = false
    private val earlyCompletions = LinkedHashMap<String, Boolean>()
    private val removeListener = transport.addListener(
        object : CodexTransportListener {
            override fun onEvent(event: CodexRpcEvent) = onRpc(event)

            override fun onDisconnect(epoch: Int) {
                loginId = null
                earlyCompletions.clear()
                if (!closed) emit(AccountEvent(AccountEventKind.Disconnected))
            }
        },
    )

    private val sameScope: Boolean get() = scopeValid() && transport.connected && transport.epoch == epoch

    public val active: Boolean get() = !closed && sameScope

    public val supported: Boolean get() = transport.accountApiSupported

    /** Synchronous observer, called on the transport lane; returns the remover. */
    public fun addListener(listener: (AccountEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    private fun emit(event: AccountEvent) {
        for (l in listeners.toList()) l(event)
    }

    private fun check() {
        if (!active) throw AccountException(AccountFailureKind.Disconnected)
        if (!supported) throw AccountException(AccountFailureKind.Unavailable)
    }

    private suspend fun call(method: String, params: JsonObject, mutation: Boolean = false, finishLogin: Boolean = false): JsonObject {
        check()
        try {
            val result = transport.requestInEpoch(method, params, epoch = epoch, mutation = mutation)
            if (!finishLogin || !sameScope) check()
            return result
        } catch (e: CodexException) {
            throw AccountException(
                when (e.kind) {
                    CodexFailureKind.DeliveryUnknown -> AccountFailureKind.Uncertain
                    CodexFailureKind.InvalidResponse -> AccountFailureKind.InvalidResponse
                    CodexFailureKind.Disconnected, CodexFailureKind.StaleRequest, CodexFailureKind.ScopeMismatch ->
                        AccountFailureKind.Disconnected
                    else -> AccountFailureKind.Unavailable
                },
            )
        }
    }

    private suspend inline fun <T> guarded(crossinline block: suspend () -> T): Outcome<T> =
        attempt(
            classify = { e -> (e as? AccountException)?.toFailure() ?: Failure.Unexpected(Redacted.shape(e)) },
            block = { withContext(transport.lane) { block() } },
        )

    public suspend fun read(): Outcome<AgentAccount> = guarded { readAccount() }

    private suspend fun readAccount(): AgentAccount {
        val result = call("account/read", jsonOf("refreshToken" to false))
        val required = result["requiresOpenaiAuth"].boolOrNull()
        if (required == null || !result.containsKey("account")) throw invalid()
        val account = result["account"]
        if (account == null || account is JsonNull) return AgentAccount(requiresSignIn = required)
        if (account !is JsonObject || account["type"].stringOrNull() == null) throw invalid()
        return AgentAccount(
            type = text(account["type"], 80),
            email = optionalText(account["email"], 320),
            plan = optionalText(account["planType"], 100),
            requiresSignIn = required,
        )
    }

    public suspend fun rateLimits(): Outcome<List<AccountRateBucket>> =
        guarded {
            val result = call("account/rateLimits/read", JsonObject(emptyMap()))
            val multi = result["rateLimitsByLimitId"]
            if (multi != null && multi !is JsonNull && multi !is JsonObject) throw invalid()
            val values: List<JsonElement?> =
                if (multi is JsonObject && multi.isNotEmpty()) {
                    if (multi.size > 32) throw invalid()
                    multi.values.toList()
                } else {
                    listOf(result["rateLimits"])
                }
            values.map { value ->
                if (value !is JsonObject) throw invalid()
                val name = value["limitName"]?.takeUnless { it is JsonNull } ?: value["limitId"]
                AccountRateBucket(optionalText(name, 100), window(value["primary"]), window(value["secondary"]))
            }
        }

    public suspend fun usage(): Outcome<AccountTokenUsage> =
        guarded {
            val summary = call("account/usage/read", JsonObject(emptyMap()))["summary"] as? JsonObject ?: throw invalid()
            AccountTokenUsage(number(summary["lifetimeTokens"]), number(summary["peakDailyTokens"]))
        }

    public suspend fun startDeviceLogin(): Outcome<AccountDeviceCode> = guarded { start() }

    private suspend fun start(): AccountDeviceCode {
        check()
        if (starting || loginId != null || untrackedStart) throw AccountException(AccountFailureKind.Unavailable)
        starting = true
        cancelRequested = false
        earlyCompletions.clear()
        var requested = false
        var receiptKnown = false
        try {
            // Do not knowingly replace an account authenticated by another client.
            val current = readAccount()
            if (current.signedIn || !current.requiresSignIn || cancelRequested) throw AccountException(AccountFailureKind.Unavailable)
            requested = true
            val result = call("account/login/start", jsonOf("type" to "chatgptDeviceCode"), mutation = true, finishLogin = true)
            val owned = text(result["loginId"], 256)
            loginId = owned
            receiptKnown = true
            val completed = earlyCompletions.remove(owned)
            if (completed != null) {
                loginId = null
                if (active) emit(AccountEvent(AccountEventKind.LoginCompleted, success = completed))
                // The matching notification already settled the login; a late code would reopen a finished flow.
                throw AccountException(AccountFailureKind.Unavailable)
            }
            if (cancelRequested) {
                val confirmed = cancelOwned()
                if (active) emit(AccountEvent(AccountEventKind.LoginCancelled, success = confirmed))
                throw AccountException(AccountFailureKind.Unavailable)
            }
            try {
                if (result["type"].stringOrNull() != "chatgptDeviceCode") throw invalid()
                val url = text(result["verificationUrl"], 2048)
                // A device code is never handed to a lookalike login host or put in a query string.
                if (!officialDeviceUrl(url)) throw invalid()
                return AccountDeviceCode(url, text(result["userCode"], 64))
            } catch (e: AccountException) {
                if (!cancelOwned()) throw AccountException(AccountFailureKind.Uncertain)
                throw e
            }
        } catch (e: AccountException) {
            if (requested && !receiptKnown && (e.kind == AccountFailureKind.Uncertain || e.kind == AccountFailureKind.InvalidResponse)) {
                untrackedStart = true
                throw AccountException(AccountFailureKind.Uncertain)
            }
            throw e
        } finally {
            starting = false
            earlyCompletions.clear()
            if (cancelRequested && !requested && active) emit(AccountEvent(AccountEventKind.LoginCancelled, success = true))
        }
    }

    private suspend fun cancelOwned(): Boolean {
        if (!sameScope) return false
        val id = loginId ?: return !starting && !untrackedStart
        return try {
            val result = transport.requestInEpoch("account/login/cancel", jsonOf("loginId" to id), epoch = epoch, mutation = true)
            val confirmed = result["status"].stringOrNull() == "canceled"
            if (confirmed && loginId == id) loginId = null
            confirmed
        } catch (e: CancellationException) {
            throw e
        } catch (_: CodexException) {
            false
        }
    }

    /** Cancels only this session's outstanding attempt. False = not confirmed; it is never a sign-out. */
    public suspend fun cancelLogin(): Boolean =
        withContext(transport.lane) {
            cancelRequested = true
            if (starting && loginId == null) false else cancelOwned()
        }

    private fun onRpc(event: CodexRpcEvent) {
        if (!active || event.epoch != epoch) return
        when (event.method) {
            "account/updated" -> {
                if (starting || loginId != null) cancelRequested = true
                emit(AccountEvent(AccountEventKind.Changed))
            }
            // Re-read instead of merging uncorrelated snapshots.
            "account/rateLimits/updated" -> emit(AccountEvent(AccountEventKind.LimitsChanged))
            "account/login/completed" -> {
                val id = event.params["loginId"].stringOrNull() ?: return
                val success = event.params["success"].boolOrNull() ?: return
                if (id == loginId) {
                    loginId = null
                    emit(AccountEvent(AccountEventKind.LoginCompleted, success = success))
                } else if (starting && id.length <= 256 && earlyCompletions.size < 8) {
                    earlyCompletions[id] = success
                }
            }
        }
    }

    /** Idempotent. Cancels an owned login on the original scope only; never against a replacement connection. */
    public suspend fun close() {
        withContext(transport.lane) {
            if (closed) return@withContext
            cancelRequested = true
            closed = true
            removeListener()
            cancelOwned()
            listeners.clear()
        }
    }

    private companion object {
        fun invalid() = AccountException(AccountFailureKind.InvalidResponse)

        val CONTROL = Regex("[\\x00-\\x1f\\x7f]")

        fun text(value: JsonElement?, max: Int): String {
            val s = value.stringOrNull() ?: throw invalid()
            if (s.isEmpty() || s.length > max || CONTROL.containsMatchIn(s)) throw invalid()
            return s
        }

        fun optionalText(value: JsonElement?, max: Int): String? = if (value == null || value is JsonNull) null else text(value, max)

        fun number(value: JsonElement?): Long? {
            if (value == null || value is JsonNull) return null
            val n = value.intOrNull() ?: throw invalid()
            if (n < 0) throw invalid()
            return n
        }

        fun window(value: JsonElement?): AccountRateWindow? {
            if (value == null || value is JsonNull) return null
            if (value !is JsonObject) throw invalid()
            val used = number(value["usedPercent"]) ?: throw invalid()
            val reset = number(value["resetsAt"])
            if (reset != null && reset > 8_640_000_000_000) throw invalid()
            return AccountRateWindow(used, number(value["windowDurationMins"]), reset?.let { it * 1000 })
        }

        /** Exactly `https://auth.openai.com/codex/device`: no user info, other port, query or fragment. */
        fun officialDeviceUrl(url: String): Boolean {
            val prefix = "https" + "://"
            if (!url.startsWith(prefix)) return false
            val rest = url.substring(prefix.length)
            val slash = rest.indexOf('/')
            if (slash < 0) return false
            val authority = rest.substring(0, slash)
            val path = rest.substring(slash)
            val host = authority.removeSuffix(":443")
            return host == OFFICIAL_HOST && path == "/codex/device"
        }

        const val OFFICIAL_HOST = "auth.openai.com"
    }
}
