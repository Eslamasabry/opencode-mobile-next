package io.github.eslamasabry.oc.core.net

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.security.HostClass
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess

/**
 * What one live connection's transport is allowed to do (plan 04 §3.3). The trust decision itself (cleartext consent,
 * pin) is made by `:core:security` before this exists; this only carries it to the engine.
 */
public class TransportSpec(
    public val origin: ServerOrigin,
    public val hostClass: HostClass,
    public val cleartextPermitted: Boolean,
    /** SHA-256 of the pinned leaf certificate (32 bytes), or null. Enforced by the engine (`:core:net-okhttp`). */
    public val pinnedLeafSha256: ByteArray? = null,
    public val connectTimeoutMs: Long = 8_000,
    public val requestTimeoutMs: Long = 120_000,
)

/** How a transport error happened, as far as the engine can tell. Decides "may have landed" for mutations. */
public enum class TransportError {
    /** Nothing reached the server: DNS, refused, no route, connect timeout, a refused trust gate. */
    ConnectFailed,

    /** The request may have been written, then the reply timed out. */
    Timeout,

    /** The stack refused to send a mutation a second time (the resend guard). */
    ResendRefused,

    /** Anything else after the connection was up: reset, EOF, cut body. */
    Other,
}

/** Builds the engine for a spec; android/desktop bind the hardened OkHttp engine (`OkHttpEngineFactory`). */
public interface EngineFactory {
    public fun create(spec: TransportSpec): HttpClientEngine

    /** Engine-specific exceptions (java.net.*) are classified where they are known. */
    public fun classify(error: Throwable): TransportError
}

/** Read = safe to repeat; Mutation = the stack never repeats it (N4-D2, audit codex-04 BLOCKER 1). */
public enum class CallKind {
    Read,
    Mutation,
    ;

    public companion object {
        public fun of(method: HttpMethod): CallKind =
            if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) Read else Mutation
    }
}

/** Status codes → plain failures (plan 04 §3.8). Server text never goes into details: status only. */
public object StatusMapping {
    public fun failureFor(status: Int): Failure {
        val details = Redacted.shape(null, status)
        return when {
            status == 401 || status == 403 -> Failure.SignIn(details)
            status == 404 || status == 410 -> Failure.NotFound(details)
            status == 408 -> Failure.TimedOut(details)
            status == 409 -> Failure.Conflict(details)
            status == 429 || status == 503 -> Failure.Busy(details)
            status >= 500 -> Failure.Server(status, details)
            else -> Failure.Rejected(status, details) // 3xx too: redirects are never followed (B-PROTO-129)
        }
    }
}

/** Transport errors → plain failures. A mutation that may have reached the server is DispatchUncertain, never retried. */
public object TransportFailures {
    public fun of(kind: CallKind, error: TransportError, cause: Throwable?): Failure {
        val details = Redacted.shape(cause)
        return when {
            error == TransportError.ConnectFailed -> Failure.Network(details)
            kind == CallKind.Mutation -> Failure.Refused(RefusalReason.DispatchUncertain, details)
            error == TransportError.Timeout -> Failure.TimedOut(details)
            else -> Failure.Network(details)
        }
    }
}

/**
 * One connection's Ktor client (plan 04 §3.3): no redirects, no retry plugin, no content negotiation, no logging
 * plugin, status codes as values. Closing it closes the engine.
 */
public class HttpClientFactory(private val engines: EngineFactory) {
    public fun create(spec: TransportSpec): NetClient {
        val client = HttpClient(engines.create(spec)) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = spec.connectTimeoutMs
                requestTimeoutMillis = spec.requestTimeoutMs
                socketTimeoutMillis = spec.requestTimeoutMs
            }
            install(UserAgent) { agent = USER_AGENT }
        }
        return NetClient(client, engines::classify)
    }

    public companion object {
        /** No version, no device facts. */
        public const val USER_AGENT: String = "OpenCodeMobile"
    }
}

public class NetClient(public val http: HttpClient, private val classify: (Throwable) -> TransportError) {
    /**
     * One request, one answer. A non-2xx status becomes its [StatusMapping] failure; any error becomes a
     * [TransportFailures] failure for the method's [CallKind]. Cancellation is rethrown. Nothing here retries.
     */
    public suspend fun <T> exchange(build: HttpRequestBuilder.() -> Unit, read: suspend (HttpResponse) -> T): Outcome<T> {
        val builder = HttpRequestBuilder().apply(build)
        val kind = CallKind.of(builder.method)
        val outcome = attempt({ TransportFailures.of(kind, classify(it), it) }) {
            http.prepareRequest(builder).execute { response ->
                if (!response.status.isSuccess()) {
                    Outcome.Failed(StatusMapping.failureFor(response.status.value))
                } else {
                    Outcome.Ok(read(response))
                }
            }
        }
        return when (outcome) {
            is Outcome.Ok -> outcome.value
            is Outcome.Failed -> outcome
        }
    }

    public fun close() {
        http.close()
    }
}
