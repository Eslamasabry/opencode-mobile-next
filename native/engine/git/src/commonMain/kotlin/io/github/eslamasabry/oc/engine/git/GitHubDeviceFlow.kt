package io.github.eslamasabry.oc.engine.git

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

// GitHub sign-in by device flow (RFC 8628 as GitHub runs it; port of lib/domain/git/github_device_flow.dart). The app
// asks GitHub for a short code, the person opens github.com/login/device and types it, and the app polls until
// GitHub hands over a token. Only the public client id is needed; there is no client secret in the app.

/** The app's GitHub OAuth App (device flow enabled). Public by design (the Flutter app ships the same id). */
public const val GITHUB_CLIENT_ID: String = "Ov23liG1iMtZQSsgpXz4"

/** Push and pull to private repositories need `repo`. */
public const val GITHUB_SCOPE: String = "repo"

public const val GITHUB_DEVICE_CODE_URL: String = "https://github.com/login/device/code"
public const val GITHUB_ACCESS_TOKEN_URL: String = "https://github.com/login/oauth/access_token"
public const val GITHUB_USER_URL: String = "https://api.github.com/user"

/** Where people review or revoke the app's access. */
public const val GITHUB_AUTHORIZED_APPS_URL: String = "https://github.com/settings/connections/applications/$GITHUB_CLIENT_ID"

/** What GitHub answered to "give me a code". */
public class GitHubDeviceCode(
    /** Secret to the app: only ever sent back to GitHub. */
    public val deviceCode: String,
    /** What the person types (`WDJB-MJHT`). */
    public val userCode: String,
    /** Where they type it (`https://github.com/login/device`). */
    public val verificationUri: String,
    public val expiresAtMs: Long,
    public val intervalMs: Long,
) {
    override fun toString(): String = "GitHubDeviceCode($userCode)"
}

/** A token GitHub issued, with the account it belongs to once known. Never prints a token. */
public class GitHubCredential(
    public val accessToken: String,
    public val login: String = "",
    public val scope: String = "",
    public val refreshToken: String? = null,
    public val expiresAtMs: Long? = null,
    public val refreshExpiresAtMs: Long? = null,
) {
    public fun expiredAt(nowMs: Long): Boolean = expiresAtMs != null && nowMs >= expiresAtMs - SKEW_MS

    public fun canRefreshAt(nowMs: Long): Boolean = refreshToken != null && (refreshExpiresAtMs == null || nowMs < refreshExpiresAtMs)

    public fun withLogin(value: String): GitHubCredential =
        GitHubCredential(accessToken, value, scope, refreshToken, expiresAtMs, refreshExpiresAtMs)

    override fun equals(other: Any?): Boolean =
        other is GitHubCredential && other.accessToken == accessToken && other.login == login && other.scope == scope &&
            other.refreshToken == refreshToken && other.expiresAtMs == expiresAtMs && other.refreshExpiresAtMs == refreshExpiresAtMs

    override fun hashCode(): Int = accessToken.hashCode() * 31 + login.hashCode()

    override fun toString(): String = "GitHubCredential(login: $login)"

    private companion object {
        const val SKEW_MS = 120_000L
    }
}

public enum class GitHubSignInProblem {
    /** The code ran out before the person entered it. */
    Expired,

    /** The person pressed Cancel on GitHub's page. */
    Denied,

    /** The OAuth App does not have device flow switched on. */
    DeviceFlowDisabled,

    /** GitHub could not be reached. */
    Network,

    /** GitHub refused the stored token (signed out on GitHub, revoked). */
    TokenRejected,

    /** GitHub answered something the app does not understand. */
    Unexpected,

    /** The app stopped waiting (the person cancelled in the app). */
    Cancelled,
}

/** A finished device-flow step: a value, or a typed problem with GitHub's `error` words for Details (never a token). */
public sealed interface GitHubResult<out T> {
    public data class Ok<out T>(val value: T) : GitHubResult<T>

    public data class Problem(val problem: GitHubSignInProblem, val details: String = "") : GitHubResult<Nothing>
}

/** The HTTP the flow needs; [NetGitHubHttp] in production, a fake in tests. */
public interface GitHubHttp {
    /** A form POST with `Accept: application/json`; [GitHubReply.Answer] carries any parsed JSON object. */
    public suspend fun postForm(address: String, fields: Map<String, String>): GitHubReply

    /** `GET /user` with the bearer [token]. */
    public suspend fun getUser(token: String): GitHubReply
}

public sealed interface GitHubReply {
    public data class Answer(val status: Int, val body: JsonObject?) : GitHubReply {
        override fun toString(): String = "Answer($status)"
    }

    /** Nothing usable came back (DNS, refused, reset, timeout). */
    public data object Unreachable : GitHubReply
}

/** Who a GitHub sign-in belongs to. */
public data class GitHubProfile(val login: String, val id: Long? = null, val name: String? = null)

/**
 * The HTTP side of device flow. Every network call goes through [http] and every wait through [sleep], so tests run
 * instantly. Every code and token is registered with the [redactor] before anything else sees it.
 */
public class GitHubDeviceFlow(
    private val http: GitHubHttp,
    private val redactor: Redactor,
    private val nowMs: () -> Long,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val clientId: String = GITHUB_CLIENT_ID,
) {
    public suspend fun requestCode(scope: String = GITHUB_SCOPE): GitHubResult<GitHubDeviceCode> {
        val body =
            when (val reply = post(GITHUB_DEVICE_CODE_URL, mapOf("client_id" to clientId, "scope" to scope))) {
                is GitHubResult.Problem -> return reply
                is GitHubResult.Ok -> reply.value
            }
        val error = body.str("error")
        if (error != null) {
            return GitHubResult.Problem(
                if (error == "device_flow_disabled") GitHubSignInProblem.DeviceFlowDisabled else GitHubSignInProblem.Unexpected,
                errorDetails(body),
            )
        }
        val deviceCode = body.str("device_code")
        val userCode = body.str("user_code")
        val uri = body.str("verification_uri")
        if (deviceCode == null || userCode == null || uri == null || !uri.startsWith("https://")) {
            return GitHubResult.Problem(GitHubSignInProblem.Unexpected)
        }
        redactor.registerSecret(deviceCode)
        val expiresIn = body.long("expires_in") ?: 900L
        val interval = (body.long("interval") ?: 5L).coerceIn(1L, 60L)
        return GitHubResult.Ok(GitHubDeviceCode(deviceCode, userCode, uri, nowMs() + expiresIn * 1000, interval * 1000))
    }

    /**
     * Polls until the person finishes on GitHub. Honors `slow_down` (+5 s, or the interval GitHub names), ends on
     * `expired_token`/`access_denied`, and gives up at the code's expiry. [cancelled] is checked before each poll and
     * after each answer, so a late token after Cancel is ignored. A brief network failure is retried; three in a row end
     * the wait.
     */
    public suspend fun pollForToken(code: GitHubDeviceCode, cancelled: () -> Boolean = { false }): GitHubResult<GitHubCredential> {
        var interval = code.intervalMs
        var failures = 0
        while (true) {
            sleep(interval)
            if (cancelled()) return GitHubResult.Problem(GitHubSignInProblem.Cancelled)
            if (nowMs() >= code.expiresAtMs) return GitHubResult.Problem(GitHubSignInProblem.Expired)
            val body =
                when (
                    val reply =
                        post(
                            GITHUB_ACCESS_TOKEN_URL,
                            mapOf(
                                "client_id" to clientId,
                                "device_code" to code.deviceCode,
                                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                            ),
                        )
                ) {
                    is GitHubResult.Problem -> {
                        if (reply.problem != GitHubSignInProblem.Network) return reply
                        if (++failures >= MAX_POLL_FAILURES) return reply
                        continue
                    }
                    is GitHubResult.Ok -> {
                        failures = 0
                        reply.value
                    }
                }
            if (cancelled()) return GitHubResult.Problem(GitHubSignInProblem.Cancelled)
            when (body.str("error")) {
                null -> return credential(body)
                "authorization_pending" -> continue
                "slow_down" -> {
                    val named = body.long("interval")
                    interval = if (named != null) named * 1000 else interval + SLOW_DOWN_MS
                }
                "expired_token" -> return GitHubResult.Problem(GitHubSignInProblem.Expired, errorDetails(body))
                "access_denied" -> return GitHubResult.Problem(GitHubSignInProblem.Denied, errorDetails(body))
                "device_flow_disabled" -> return GitHubResult.Problem(GitHubSignInProblem.DeviceFlowDisabled, errorDetails(body))
                else -> return GitHubResult.Problem(GitHubSignInProblem.Unexpected, errorDetails(body))
            }
        }
    }

    /** Exchanges a refresh token for a new pair (expiring-token apps only), keeping the login. */
    public suspend fun refresh(current: GitHubCredential): GitHubResult<GitHubCredential> {
        val refreshToken = current.refreshToken ?: return GitHubResult.Problem(GitHubSignInProblem.TokenRejected)
        val body =
            when (
                val reply =
                    post(
                        GITHUB_ACCESS_TOKEN_URL,
                        mapOf("client_id" to clientId, "grant_type" to "refresh_token", "refresh_token" to refreshToken),
                    )
            ) {
                is GitHubResult.Problem -> return reply
                is GitHubResult.Ok -> reply.value
            }
        if (body["error"] != null) return GitHubResult.Problem(GitHubSignInProblem.TokenRejected, errorDetails(body))
        return when (val made = credential(body)) {
            is GitHubResult.Problem -> made
            is GitHubResult.Ok -> GitHubResult.Ok(made.value.withLogin(current.login))
        }
    }

    /** The account's login, numeric id and display name (`GET /user`). */
    public suspend fun fetchProfile(token: String): GitHubResult<GitHubProfile> =
        when (val reply = http.getUser(token)) {
            GitHubReply.Unreachable -> GitHubResult.Problem(GitHubSignInProblem.Network)
            is GitHubReply.Answer -> {
                val login = reply.body?.str("login")
                when {
                    reply.status == 401 -> GitHubResult.Problem(GitHubSignInProblem.TokenRejected)
                    reply.status != 200 || login.isNullOrEmpty() ->
                        GitHubResult.Problem(GitHubSignInProblem.Unexpected, "GET /user answered ${reply.status}")
                    else ->
                        GitHubResult.Ok(
                            GitHubProfile(login, reply.body?.long("id"), reply.body?.str("name")?.trim()?.takeIf { it.isNotEmpty() }),
                        )
                }
            }
        }

    private fun credential(body: JsonObject): GitHubResult<GitHubCredential> {
        val token = body.str("access_token")
        if (token.isNullOrEmpty()) return GitHubResult.Problem(GitHubSignInProblem.Unexpected)
        redactor.registerSecret(token)
        val refresh = body.str("refresh_token")?.takeIf { it.isNotEmpty() }
        refresh?.let(redactor::registerSecret)
        val now = nowMs()
        return GitHubResult.Ok(
            GitHubCredential(
                accessToken = token,
                scope = body.str("scope").orEmpty(),
                refreshToken = refresh,
                expiresAtMs = body.long("expires_in")?.let { now + it * 1000 },
                refreshExpiresAtMs = body.long("refresh_token_expires_in")?.let { now + it * 1000 },
            ),
        )
    }

    private suspend fun post(address: String, fields: Map<String, String>): GitHubResult<JsonObject> =
        when (val reply = http.postForm(address, fields)) {
            GitHubReply.Unreachable -> GitHubResult.Problem(GitHubSignInProblem.Network)
            is GitHubReply.Answer -> {
                val body = reply.body
                if (body != null) {
                    // Register every credential-shaped value before the body is read anywhere else.
                    redactor.registerCredentialValues(body)
                    GitHubResult.Ok(body)
                } else {
                    GitHubResult.Problem(
                        if (reply.status >= 500 || reply.status == 0) GitHubSignInProblem.Network else GitHubSignInProblem.Unexpected,
                        "GitHub answered ${reply.status}",
                    )
                }
            }
        }

    private fun errorDetails(body: JsonObject): String = listOfNotNull(body.str("error"), body.str("error_description")).joinToString(": ")

    override fun toString(): String = "GitHubDeviceFlow"

    public companion object {
        /** How many network failures in a row end the wait. */
        public const val MAX_POLL_FAILURES: Int = 3
        private const val SLOW_DOWN_MS = 5_000L
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.longOrNull ?: value.content.toDoubleOrNull()?.toLong()
}

/**
 * [GitHubHttp] over two trust-gated core:net clients: [github] for `https://github.com` and [api] for
 * `https://api.github.com` (one origin per client, plan 04 §4.6). Status codes come back as values; a failed
 * exchange is [GitHubReply.Unreachable] except a 401 on `/user`, which is the rejected token.
 */
public class NetGitHubHttp(private val github: NetClient, private val api: NetClient) : GitHubHttp {
    override suspend fun postForm(address: String, fields: Map<String, String>): GitHubReply {
        val reply =
            github.exchange({
                method = HttpMethod.Post
                url(address)
                header(HttpHeaders.Accept, "application/json")
                setBody(FormDataContent(Parameters.build { fields.forEach { (k, v) -> append(k, v) } }))
            }) { response -> response.status.value to response.bodyAsText() }
        return when (reply) {
            is Outcome.Ok -> GitHubReply.Answer(reply.value.first, ServerGitShell.parseJson(reply.value.second) as? JsonObject)
            is Outcome.Failed -> statusOf(reply.failure)?.let { GitHubReply.Answer(it, null) } ?: GitHubReply.Unreachable
        }
    }

    override suspend fun getUser(token: String): GitHubReply {
        val reply =
            api.exchange({
                method = HttpMethod.Get
                url(GITHUB_USER_URL)
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.Authorization, "Bearer $token")
                header("X-GitHub-Api-Version", "2022-11-28")
            }) { response -> response.status.value to response.bodyAsText() }
        return when (reply) {
            is Outcome.Ok -> GitHubReply.Answer(reply.value.first, ServerGitShell.parseJson(reply.value.second) as? JsonObject)
            is Outcome.Failed -> statusOf(reply.failure)?.let { GitHubReply.Answer(it, null) } ?: GitHubReply.Unreachable
        }
    }

    /** The status a failed exchange stands for, when it was an answer and not a transport failure. */
    private fun statusOf(failure: Failure): Int? =
        when (failure) {
            is Failure.SignIn -> 401
            is Failure.NotFound -> 404
            is Failure.Rejected -> failure.status
            is Failure.Server -> failure.status
            else -> null
        }

    override fun toString(): String = "NetGitHubHttp"
}
