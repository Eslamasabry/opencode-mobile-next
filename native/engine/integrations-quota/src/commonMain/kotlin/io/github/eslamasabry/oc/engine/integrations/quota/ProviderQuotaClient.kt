package io.github.eslamasabry.oc.engine.integrations.quota

import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.SignInWhy
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.io.encoding.Base64

/** Where the collector the app runs inside In-app Ubuntu listens: loopback only, on this phone. */
public const val IN_APP_QUOTA_PORT: Int = 4195
public const val IN_APP_QUOTA_ORIGIN: String = "http://127.0.0.1:$IN_APP_QUOTA_PORT"

/** The answer of one quota read. */
public sealed interface QuotaRead {
    public data class Read(val snapshot: ProviderQuotaSnapshot) : QuotaRead

    public data class Failed(val failure: ProviderQuotaFailure) : QuotaRead
}

/** The plain core failure for a quota failure kind (Usage words it; no text from the server is kept). */
public fun ProviderQuotaFailure.toFailure(): Failure =
    when (kind) {
        QuotaFailureKind.CollectorAuth -> Failure.SignIn(SignInWhy.PasswordRejected)
        QuotaFailureKind.Unsupported -> Failure.Unavailable(FeatureId("providerQuota"))
        QuotaFailureKind.Unavailable -> Failure.Network()
        QuotaFailureKind.InvalidResponse -> Failure.UnreadableReply()
    }

/**
 * A separately installed, explicitly trusted deployment extension (Flutter `HttpProviderQuotaGateway`). Not an
 * OpenCode API client. The constructor COPIES the profile's origin and credentials: later profile edits never change
 * this gateway's target. [inAppToken] is set only for the app's own In-app Ubuntu server: it then reads loopback
 * [IN_APP_QUOTA_ORIGIN] with a Bearer token instead of the server's origin with Basic; no token means the collector was
 * never set up ([QuotaFailureKind.Unsupported], nothing sent).
 */
public class HttpProviderQuotaGateway(
    baseUrl: String,
    username: String,
    password: String,
    requiresPasswordReentry: Boolean = false,
    private val transport: QuotaTransport,
    public val provider: QuotaProvider = QuotaProvider.Codex,
    private val inAppToken: (suspend () -> String?)? = null,
) {
    private val baseUrl: String = if (inAppToken == null) baseUrl else IN_APP_QUOTA_ORIGIN
    private val username: String = username
    private val password: String = password
    private val requiresPasswordReentry: Boolean = requiresPasswordReentry
    private val requests = LinkedHashSet<Deferred<*>>()
    private var closed = false

    public suspend fun readSnapshot(): QuotaRead {
        if (!quotaCollectionAvailable(provider)) return failed(QuotaFailureKind.Unsupported)
        if (closed || (inAppToken == null && !validProfile(baseUrl, username, password, requiresPasswordReentry))) {
            return failed(QuotaFailureKind.Unavailable)
        }
        var token: String? = null
        if (inAppToken != null) {
            token = inAppToken.invoke()
            // No token: the collector was never set up here (or was turned off).
            if (token.isNullOrEmpty()) return failed(QuotaFailureKind.Unsupported)
        }
        val authorization =
            if (token != null) "Bearer $token" else "Basic " + Base64.encode("$username:$password".encodeToByteArray())
        return coroutineScope {
            // A total deadline also bounds a peer that keeps dripping body bytes; close() settles the caller even if a
            // transport stalls.
            val call =
                async {
                    try {
                        withTimeout(REQUEST_TIMEOUT_MS) { read(authorization) }
                    } catch (e: TimeoutCancellationException) {
                        failed(QuotaFailureKind.Unavailable)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Never keep the error: it may hold the URL, Basic credentials or the raw provider response.
                        failed(QuotaFailureKind.Unavailable)
                    }
                }
            requests += call
            try {
                call.await()
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                failed(QuotaFailureKind.Unavailable) // closed under the read
            } finally {
                requests -= call
                call.cancel()
            }
        }
    }

    private suspend fun read(authorization: String): QuotaRead {
        val request =
            QuotaRequest(
                method = "GET",
                url = originOf(baseUrl) + quotaPathFor(provider),
                headers = persistentMapOf("Accept" to "application/json", "Authorization" to authorization),
                followRedirects = false,
                maxRedirects = 0,
                connectTimeoutMs = REQUEST_TIMEOUT_MS,
                sendTimeoutMs = REQUEST_TIMEOUT_MS,
                receiveTimeoutMs = REQUEST_TIMEOUT_MS,
            )
        val response = transport.fetch(request)
        statusFailure(response.status)?.let { return failed(it) }
        if (response.isRedirect) return failed(QuotaFailureKind.InvalidResponse)
        val bytes = ByteCollector()
        try {
            // The cap applies while collecting: an oversized source is stopped before anything more is buffered.
            response.body.collect { chunk ->
                if (bytes.size + chunk.size > MAX_RESPONSE_BYTES) throw BodyTooLarge()
                bytes.add(chunk)
            }
        } catch (e: BodyTooLarge) {
            return failed(QuotaFailureKind.InvalidResponse)
        }
        val snapshot = decode(bytes.toByteArray()) ?: return failed(QuotaFailureKind.InvalidResponse)
        if (snapshot.provider != provider) return failed(QuotaFailureKind.InvalidResponse)
        return QuotaRead.Read(snapshot)
    }

    /** Idempotent. Cancels reads in flight (headers or a partial body) and closes the transport once, forced. */
    public fun close() {
        if (closed) return
        closed = true
        for (call in requests.toList()) call.cancel()
        requests.clear()
        try {
            transport.close(force = true)
        } catch (_: Exception) {
            // A faulty transport must not leak a raw exception during scope retirement.
        }
    }

    override fun toString(): String = "HttpProviderQuotaGateway"

    private class BodyTooLarge : Exception()

    private class ByteCollector {
        private val chunks = ArrayList<ByteArray>()
        var size: Int = 0
            private set

        fun add(chunk: ByteArray) {
            chunks += chunk.copyOf()
            size += chunk.size
        }

        fun toByteArray(): ByteArray {
            val out = ByteArray(size)
            var at = 0
            for (c in chunks) {
                c.copyInto(out, at)
                at += c.size
            }
            return out
        }
    }

    public companion object {
        public const val MAX_RESPONSE_BYTES: Int = 64 * 1024
        public const val REQUEST_TIMEOUT_MS: Long = 10_000

        private val json = Json

        private fun failed(kind: QuotaFailureKind): QuotaRead = QuotaRead.Failed(ProviderQuotaFailure(kind))

        /** Status only: the body of a non-200 answer is never read. */
        internal fun statusFailure(status: Int): QuotaFailureKind? =
            when {
                status == 200 -> null
                status == 401 || status == 403 -> QuotaFailureKind.CollectorAuth
                status == 404 || status == 405 -> QuotaFailureKind.Unsupported
                status == 408 || status == 429 || status >= 500 -> QuotaFailureKind.Unavailable
                else -> QuotaFailureKind.InvalidResponse
            }

        private fun decode(bytes: ByteArray): ProviderQuotaSnapshot? {
            val element: JsonElement =
                try {
                    json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true))
                } catch (_: Exception) {
                    return null
                }
            return ProviderQuotaSnapshot.fromJson(element)
        }

        /**
         * Setup eligibility only, NOT permission to send. Even loopback needs a non-empty password; unreadable secure
         * storage never means anonymous.
         */
        public fun canReadProfile(baseUrl: String, username: String, password: String, requiresPasswordReentry: Boolean): Boolean =
            validProfile(baseUrl, username, password, requiresPasswordReentry)

        private val CONTROL = Regex("[\\x00-\\x1f\\x7f]")
        private val RAW_AUTHORITY = Regex("^https?://([^/?#]*)", RegexOption.IGNORE_CASE)

        internal fun validProfile(baseUrl: String, username: String, password: String, requiresPasswordReentry: Boolean): Boolean {
            if (requiresPasswordReentry || password.isEmpty() || baseUrl.length > 2048) return false
            // RFC 7617 cannot represent a colon in the user-id: never authenticate as a different principal.
            if (':' in username || CONTROL.containsMatchIn(username) || CONTROL.containsMatchIn(password)) return false
            // Reject credentials in the raw authority, including an empty `https://@host`.
            val rawAuthority = RAW_AUTHORITY.find(baseUrl.trim())?.groupValues?.get(1) ?: return false
            if ('@' in rawAuthority) return false
            val url = QuotaUrl.parse(baseUrl) ?: return false
            // Plain HTTP to a non-loopback address needs a per-profile confirmation this scope does not carry.
            return !(url.scheme == "http" && !QuotaUrl.isLoopbackHost(url.host))
        }

        private fun originOf(baseUrl: String): String = QuotaUrl.parse(baseUrl)?.origin ?: baseUrl.trim().trimEnd('/')
    }
}

/**
 * The Flutter `validateServerProfileUrl` rules, for an origin only: http(s), a host, port 1..65535, no user info, no
 * query or fragment (not even empty ones), no path but `/`, no whitespace, `%`, backslash or control characters.
 */
internal class QuotaUrl private constructor(val scheme: String, val host: String, val authority: String) {
    val origin: String get() = "$scheme://$authority"

    companion object {
        private val LOOPBACK = setOf("localhost", "127.0.0.1", "::1")

        fun isLoopbackHost(host: String): Boolean = host.lowercase() in LOOPBACK

        fun parse(value: String): QuotaUrl? {
            val raw = value.trim()
            if (raw.isEmpty()) return null
            val sep = raw.indexOf("://")
            if (sep <= 0) return null
            val scheme = raw.substring(0, sep).lowercase()
            if (scheme != "http" && scheme != "https") return null
            if ('\\' in raw || raw.any { it.code <= 0x1f || it.code in 0x7f..0x9f }) return null
            val rest = raw.substring(sep + 3)
            val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, end)
            val tail = rest.substring(end)
            if ('?' in tail || '#' in tail) return null
            if (tail.isNotEmpty() && tail != "/") return null
            if ('@' in authority) return null
            val host: String
            val portText: String?
            if (authority.startsWith("[")) {
                val close = authority.indexOf(']')
                if (close < 2) return null
                host = authority.substring(1, close)
                val after = authority.substring(close + 1)
                portText = if (after.isEmpty()) null else if (after.startsWith(":")) after.substring(1) else return null
            } else {
                val colon = authority.lastIndexOf(':')
                host = if (colon >= 0) authority.substring(0, colon) else authority
                portText = if (colon >= 0) authority.substring(colon + 1) else null
                if (':' in host) return null
            }
            if (host.isEmpty() || host.any { it.isWhitespace() } || '%' in host) return null
            val port =
                when {
                    portText == null -> if (scheme == "https") 443 else 80
                    portText.isEmpty() || !portText.all { it in '0'..'9' } || portText.length > 5 -> return null
                    else -> portText.toInt()
                }
            if (port !in 1..65535) return null
            return QuotaUrl(scheme, host.lowercase(), authority.lowercase())
        }
    }
}
