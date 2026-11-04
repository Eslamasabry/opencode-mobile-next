package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

public data class CodexAccountState(
    public val type: String?,
    public val email: String?,
    public val plan: String?,
    public val requiresSignIn: Boolean,
) {
    public val signedIn: Boolean
        get() = type != null

    override fun toString(): String = "CodexAccountState(signedIn=$signedIn)"
}

public data class CodexRateWindow(
    public val usedPercent: Int,
    public val durationMinutes: Int?,
    public val resetsAtEpochMillis: Long?,
)

public data class CodexRateBucket(
    public val name: String?,
    public val primary: CodexRateWindow?,
    public val secondary: CodexRateWindow?,
)

public data class CodexUsage(public val lifetimeTokens: Long?, public val peakDailyTokens: Long?)

public class CodexDeviceLogin(public val verificationUrl: String, public val userCode: String) {
    override fun toString(): String = "CodexDeviceLogin(<private>)"
}

public enum class CodexAccountEventKind {
    Changed,
    LimitsChanged,
    LoginCompleted,
    LoginCancelled,
    Disconnected,
}

public data class CodexAccountEvent(
    public val kind: CodexAccountEventKind,
    public val success: Boolean = false,
)

/**
 * Account reads never refresh provider tokens. A panel can cancel only the handle it received on
 * its own epoch.
 */
public class CodexAccountSession
internal constructor(
    private val transport: CodexTransport,
    private val lane: CoroutineDispatcher,
    private val scopeValid: () -> Boolean,
    private val onClosed: () -> Unit,
) {
    private val epoch = transport.epoch
    private var closed = false
    private var invalid = false
    private var starting = false
    private var cancelRequested = false
    private var loginId: String? = null
    private var untrackedStart = false
    private val earlyCompletions = mutableMapOf<String, Boolean>()
    private val mutableEvents = MutableSharedFlow<CodexAccountEvent>(extraBufferCapacity = 16)
    public val events: Flow<CodexAccountEvent> = mutableEvents.asSharedFlow()
    private val sameScope: Boolean
        get() = !invalid && scopeValid() && transport.connected && transport.epoch == epoch

    public val active: Boolean
        get() = !closed && sameScope

    public val supported: Boolean
        get() = transport.accountSupported

    private fun check() {
        if (!active) stale()
        if (!supported) unavailable("agentAccount")
    }

    private suspend fun rpc(
        method: String,
        params: JsonObject,
        mutation: Boolean = false,
        finishLogin: Boolean = false,
    ): JsonObject {
        check()
        val result = transport.request(method, params, mutation, epoch)
        if (!finishLogin || !sameScope) check()
        return result
    }

    private suspend fun <T> call(block: suspend () -> T): Outcome<T> =
        withContext(lane) { codexWireAttempt(block) }

    private suspend fun readValue(): CodexAccountState {
        val result = rpc("account/read", json("refreshToken" to JsonPrimitive(false)))
        val required =
            (result["requiresOpenaiAuth"] as? JsonPrimitive)?.booleanOrNull ?: malformed()
        if (!result.containsKey("account")) malformed()
        val value = result["account"]
        if (value == JsonNull) return CodexAccountState(null, null, null, required)
        val account = value.obj()
        return CodexAccountState(
            text(account["type"], 80),
            optionalText(account["email"], 320),
            optionalText(account["planType"], 100),
            required,
        )
    }

    public suspend fun read(): Outcome<CodexAccountState> = call { readValue() }

    public suspend fun rateLimits(): Outcome<ImmutableList<CodexRateBucket>> = call {
        val result = rpc("account/rateLimits/read", json())
        val multi = result["rateLimitsByLimitId"]?.takeUnless { it == JsonNull }
        if (multi != null && multi !is JsonObject) malformed()
        val values =
            if (multi is JsonObject && multi.isNotEmpty()) {
                if (multi.size > 32) malformed()
                multi.values.toList()
            } else listOf(result["rateLimits"] ?: malformed())
        values
            .map { raw ->
                val value = raw.obj()
                CodexRateBucket(
                    optionalText(value["limitName"] ?: value["limitId"], 100),
                    window(value["primary"]),
                    window(value["secondary"]),
                )
            }
            .toImmutableList()
    }

    public suspend fun usage(): Outcome<CodexUsage> = call {
        val summary = rpc("account/usage/read", json())["summary"].obj()
        CodexUsage(number(summary["lifetimeTokens"]), number(summary["peakDailyTokens"]))
    }

    public suspend fun startDeviceLogin(): Outcome<CodexDeviceLogin> = call {
        check()
        if (starting || loginId != null || untrackedStart) unavailable("agentAccount")
        starting = true
        cancelRequested = false
        earlyCompletions.clear()
        var requested = false
        var receiptKnown = false
        try {
            val current = readValue()
            if (current.signedIn || !current.requiresSignIn || cancelRequested)
                unavailable("agentAccount")
            requested = true
            val result =
                rpc(
                    "account/login/start",
                    json("type" to s("chatgptDeviceCode")),
                    mutation = true,
                    finishLogin = true,
                )
            loginId = text(result["loginId"], 256)
            receiptKnown = true
            val completed = earlyCompletions.remove(loginId)
            if (completed != null) {
                loginId = null
                if (active)
                    mutableEvents.emit(
                        CodexAccountEvent(CodexAccountEventKind.LoginCompleted, completed)
                    )
                unavailable("agentAccount")
            }
            if (cancelRequested) {
                val confirmed = cancelOwned()
                if (active)
                    mutableEvents.emit(
                        CodexAccountEvent(CodexAccountEventKind.LoginCancelled, confirmed)
                    )
                unavailable("agentAccount")
            }
            try {
                if (result["type"].str(80) != "chatgptDeviceCode") malformed()
                val url = text(result["verificationUrl"], 2048)
                if (
                    url != "https://auth.openai.com/codex/device" &&
                        url != "https://auth.openai.com:443/codex/device"
                )
                    malformed()
                CodexDeviceLogin(url, text(result["userCode"], 64))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!cancelOwned()) unknown()
                throw e
            }
        } catch (e: CodexWireException) {
            if (
                requested &&
                    !receiptKnown &&
                    (e.failure is Failure.UnreadableReply ||
                        (e.failure is Failure.Refused &&
                            e.failure.reason == RefusalReason.DispatchUncertain))
            ) {
                untrackedStart = true
                unknown()
            }
            throw e
        } finally {
            starting = false
            earlyCompletions.clear()
            if (cancelRequested && !requested && active)
                mutableEvents.emit(CodexAccountEvent(CodexAccountEventKind.LoginCancelled, true))
        }
    }

    private suspend fun cancelOwned(): Boolean {
        if (!sameScope) return false
        val id = loginId ?: return !starting && !untrackedStart
        return try {
            val result =
                transport.request("account/login/cancel", json("loginId" to s(id)), true, epoch)
            val confirmed = result.text("status") == "canceled"
            if (confirmed && loginId == id) loginId = null
            confirmed
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    public suspend fun cancelLogin(): Outcome<Boolean> = call {
        cancelRequested = true
        if (starting && loginId == null) false else cancelOwned()
    }

    internal suspend fun onPush(push: CodexPush) {
        if (!active || push.epoch != epoch) return
        when (push.method) {
            "account/updated" -> {
                if (starting || loginId != null) cancelRequested = true
                mutableEvents.emit(CodexAccountEvent(CodexAccountEventKind.Changed))
            }
            "account/rateLimits/updated" ->
                mutableEvents.emit(CodexAccountEvent(CodexAccountEventKind.LimitsChanged))
            "account/login/completed" -> {
                val id = push.params.text("loginId") ?: return
                val success = (push.params["success"] as? JsonPrimitive)?.booleanOrNull ?: return
                if (id == loginId) {
                    loginId = null
                    mutableEvents.emit(
                        CodexAccountEvent(CodexAccountEventKind.LoginCompleted, success)
                    )
                } else if (starting && id.length <= 256 && earlyCompletions.size < 8)
                    earlyCompletions[id] = success
            }
        }
    }

    internal suspend fun disconnected() {
        loginId = null
        earlyCompletions.clear()
        if (!closed) mutableEvents.emit(CodexAccountEvent(CodexAccountEventKind.Disconnected))
    }

    internal fun invalidate() {
        invalid = true
        cancelRequested = true
    }

    public suspend fun close(): Unit =
        withContext(lane) {
            if (closed) return@withContext
            cancelRequested = true
            // The in-flight start may still return its owned receipt and cancel it after this panel
            // closes.
            closed = true
            try {
                cancelOwned()
            } finally {
                onClosed()
            }
        }

    private fun text(value: JsonElement?, max: Int): String =
        value.str(max).also { if (it.any { ch -> ch.code < 32 || ch.code == 127 }) malformed() }

    private fun optionalText(value: JsonElement?, max: Int): String? =
        if (value == null || value == JsonNull) null else text(value, max)

    private fun number(value: JsonElement?): Long? {
        if (value == null || value == JsonNull) return null
        return (value as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.takeIf { it >= 0 }
            ?: malformed()
    }

    private fun window(value: JsonElement?): CodexRateWindow? {
        if (value == null || value == JsonNull) return null
        val obj = value.obj()
        val used = number(obj["usedPercent"]) ?: malformed()
        val duration = number(obj["windowDurationMins"])
        val reset = number(obj["resetsAt"])
        if (
            used > Int.MAX_VALUE ||
                (duration != null && duration > Int.MAX_VALUE) ||
                (reset != null && reset > 8_640_000_000_000)
        )
            malformed()
        return CodexRateWindow(used.toInt(), duration?.toInt(), reset?.times(1000))
    }
}
