package io.github.eslamasabry.oc.core.net

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.ws.WsSession
import io.github.eslamasabry.oc.core.net.ws.WsSpec
import io.github.eslamasabry.oc.core.security.HostClass
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.IOException

/**
 * What one live connection's transport is allowed to do (plan 04 §3.3). The trust decision itself
 * (cleartext consent, pin) is made by `:core:security` before this exists; this only carries it to
 * the engine, and the engine enforces every field before a byte is written (plan 04 §4.6; review
 * w1-core-net MAJOR 4): a request to another origin, an `http` request without
 * [cleartextPermitted], an `http` request with a pin, a URL with user info, and a TLS leaf that
 * does not match [pinnedLeafSha256] are all refused with a [TrustRefusal].
 */
public class TransportSpec(
    public val origin: ServerOrigin,
    public val hostClass: HostClass,
    public val cleartextPermitted: Boolean,
    /**
     * SHA-256 of the pinned leaf certificate (32 bytes), or null. Enforced by the engine
     * (`OkHttpEngineFactory`).
     */
    public val pinnedLeafSha256: ByteArray? = null,
    public val connectTimeoutMs: Long = 8_000,
    /**
     * Inactivity between reads (Flutter Dio `receiveTimeout`), not a total (plan 04 §3.3–3.4;
     * review w1-core-net MAJOR 10). There is no default total deadline: a call that needs one sets
     * it in its own `timeout {}` block.
     */
    public val readTimeoutMs: Long = 120_000,
)

/**
 * The trust gate refused a request before anything was sent (plan 04 §4.6), or the pinned leaf did
 * not match. [failure] is the plain failure the caller gets (`Refused(OriginChanged |
 * CleartextNotConfirmed | PinNeedsHttps | CredentialsInUrl)` or `Certificate(Changed | Expired |
 * Untrusted)`). The message carries no URL.
 */
public class TrustRefusal(public val failure: Failure) : IOException("refused by the trust gate")

/**
 * How a transport error happened, as far as the engine can tell. Decides "may have landed" for
 * mutations.
 */
public enum class TransportError {
    /**
     * Nothing reached the server: DNS, refused, no route, connect timeout, a refused trust gate.
     */
    ConnectFailed,

    /** The request may have been written, then the reply timed out. */
    Timeout,

    /** The stack refused to send a mutation a second time (the resend guard). */
    ResendRefused,

    /** Anything else after the connection was up: reset, EOF, cut body. */
    Other,
}

/**
 * Builds the engine for a spec; android/desktop bind the hardened OkHttp engine
 * (`OkHttpEngineFactory`).
 */
public interface EngineFactory {
    public fun create(spec: TransportSpec): HttpClientEngine

    /** Engine-specific exceptions (java.net.*) are classified where they are known. */
    public fun classify(error: Throwable): TransportError

    /** The connection's own hardened client, sharing its pin, consent and lifetime. */
    public suspend fun connectSocket(
        engine: HttpClientEngine,
        spec: WsSpec,
        scope: CoroutineScope,
        dispatchers: AppDispatchers,
    ): Outcome<WsSession> = Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))

    /**
     * Runs after `engine.close()`: cancels calls still running on [engine] (an open event stream
     * included) and frees what [create] built under it (OkHttp pool and dispatcher threads; plan 04
     * §3.1). Ktor's `HttpClient(engine)` does not manage an engine it was given, so without this
     * nothing frees them (review w1-core-net MAJOR 6).
     */
    public fun release(engine: HttpClientEngine) {}
}

/**
 * Read = safe to repeat; Mutation = the stack never repeats it (N4-D2, audit codex-04 BLOCKER 1).
 */
public enum class CallKind {
    Read,
    Mutation;

    public companion object {
        public fun of(method: HttpMethod): CallKind =
            if (
                method == HttpMethod.Get ||
                    method == HttpMethod.Head ||
                    method == HttpMethod.Options
            )
                Read
            else Mutation
    }
}

/**
 * Status codes → plain failures (plan 04 §3.8, onto core's flat Failure). Server text never goes
 * into details: status only. The server's symbolic tag (`_tag`/`code`) refines Busy, NotFound,
 * Conflict and Rejected in the protocol module, which reads the body; this mapping sees the status
 * alone.
 */
public object StatusMapping {
    /**
     * [credentialsSent]: a 401 without a credential means "password missing", with one "password
     * rejected".
     */
    public fun failureFor(status: Int, credentialsSent: Boolean = true): Failure {
        val details = Redacted.shape(null, status)
        return when {
            status == 401 ->
                Failure.SignIn(
                    if (credentialsSent) SignInWhy.PasswordRejected else SignInWhy.PasswordMissing,
                    details,
                )
            // 403 is words only: it never sets passwordRejected (plan 04 §3.8).
            status == 403 -> Failure.SignIn(SignInWhy.Forbidden, details)
            status == 404 || status == 410 -> Failure.NotFound(details = details)
            status == 408 -> Failure.TimedOut(details)
            status == 409 -> Failure.Conflict(details = details)
            status == 429 -> Failure.Busy(BusyKind.RateLimited, details = details)
            // 503 without a tag; the protocol refines Starting/Stopping/Failed from `service_*`
            // tags.
            status == 503 -> Failure.Busy(BusyKind.Overloaded, details = details)
            status >= 500 -> Failure.Server(status, details = details)
            else ->
                Failure.Rejected(
                    status,
                    details = details,
                ) // 3xx too: redirects are never followed (B-PROTO-129)
        }
    }
}

/**
 * Transport errors → plain failures. A mutation that may have reached the server is
 * DispatchUncertain, never retried.
 */
public object TransportFailures {
    public fun of(kind: CallKind, error: TransportError, cause: Throwable?): Failure {
        // The trust gate refused before any byte left (plan 04 §4.6): its own failure, for
        // mutations too.
        trustRefusalIn(cause)?.let {
            return it.failure
        }
        val details = Redacted.shape(cause)
        return when {
            // Nothing reached the server, so even a mutation is safe to call failed (DNS, refused,
            // no route, gate).
            error == TransportError.ConnectFailed -> Failure.Network(NetworkWhy.Refused, details)
            kind == CallKind.Mutation -> Failure.Refused(RefusalReason.DispatchUncertain, details)
            error == TransportError.Timeout -> Failure.TimedOut(details)
            else -> Failure.Network(NetworkWhy.Reset, details)
        }
    }

    private fun trustRefusalIn(error: Throwable?): TrustRefusal? {
        var current = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is TrustRefusal) return current
            current = current.cause?.takeIf { it !== current }
            depth += 1
        }
        return null
    }

    private const val MAX_CAUSE_DEPTH = 8
}

/**
 * One connection's Ktor client (plan 04 §3.3): no redirects, no retry plugin, no content
 * negotiation, no logging plugin, status codes as values. Closing it closes the engine and releases
 * it (plan 04 §3.1).
 */
public class HttpClientFactory(private val engines: EngineFactory) {
    public fun create(spec: TransportSpec): NetClient {
        val engine = engines.create(spec)
        val client =
            HttpClient(engine) {
                expectSuccess = false
                followRedirects = false
                install(HttpTimeout) {
                    connectTimeoutMillis = spec.connectTimeoutMs
                    // Inactivity only. requestTimeoutMillis stays unset: a long download or a slow
                    // reply that keeps sending
                    // must not be cut off at two minutes (plan 04 §3.3–3.4; review w1-core-net
                    // MAJOR 10).
                    socketTimeoutMillis = spec.readTimeoutMs
                }
                install(UserAgent) { agent = USER_AGENT }
            }
        return NetClient(
            client,
            engines::classify,
            socketConnector = { socket, scope, dispatchers ->
                engines.connectSocket(engine, socket, scope, dispatchers)
            },
        ) {
            engine.close()
            engines.release(engine)
        }
    }

    public companion object {
        /** No version, no device facts. */
        public const val USER_AGENT: String = "OpenCodeMobile"
    }
}

public class NetClient(
    public val http: HttpClient,
    private val classify: (Throwable) -> TransportError,
    private val socketConnector:
        suspend (WsSpec, CoroutineScope, AppDispatchers) -> Outcome<WsSession> =
        { _, _, _ ->
            Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))
        },
    /**
     * Closes and releases the engine this client was built on; [HttpClient.close] alone leaves it
     * running.
     */
    private val closeEngine: () -> Unit = {},
) {
    /** A single upgrade; every caller obtains its own one-use ticket first. */
    public suspend fun connectSocket(
        spec: WsSpec,
        scope: CoroutineScope,
        dispatchers: AppDispatchers,
    ): Outcome<WsSession> = socketConnector(spec, scope, dispatchers)

    /**
     * One request, one answer. A non-2xx status becomes its [StatusMapping] failure; any error
     * becomes a [TransportFailures] failure for the method's [CallKind]. Cancellation is rethrown.
     * Nothing here retries.
     */
    public suspend fun <T> exchange(
        build: HttpRequestBuilder.() -> Unit,
        readFailure: suspend (HttpResponse) -> Failure? = { null },
        read: suspend (HttpResponse) -> T,
    ): Outcome<T> {
        val builder = HttpRequestBuilder().apply(build)
        val kind = CallKind.of(builder.method)
        val watch: List<HttpCallEnd> =
            observers.load().mapNotNull { observer ->
                attempt<HttpCallEnd>({ Failure.Unexpected() }) {
                        observer.begin(builder.method.value, builder.url.encodedPath)
                    }
                    .getOrNull()
            }
        var status: Int? = null
        var bytes: Long? = null
        val outcome =
            attempt({ TransportFailures.of(kind, classify(it), it) }) {
                http.prepareRequest(builder).execute { response ->
                    status = response.status.value
                    bytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (!response.status.isSuccess()) {
                        Outcome.Failed(
                            readFailure(response) ?: StatusMapping.failureFor(response.status.value)
                        )
                    } else {
                        Outcome.Ok(read(response))
                    }
                }
            }
        val result =
            when (outcome) {
                is Outcome.Ok -> outcome.value
                is Outcome.Failed -> outcome
            }
        for (end in watch) {
            attempt<Unit>({ Failure.Unexpected() }) {
                end.end(status, bytes, result is Outcome.Failed)
            }
        }
        return result
    }

    private val observers = AtomicReference<List<HttpCallObserver>>(emptyList())

    /**
     * Adds [observer] once (B-BUILD-026: attaching twice adds one). False when it was already
     * attached.
     */
    public fun attach(observer: HttpCallObserver): Boolean {
        while (true) {
            val current = observers.load()
            if (observer in current) return false
            if (observers.compareAndSet(current, current + observer)) return true
        }
    }

    /** The observers attached to this client (diagnostics and tests). */
    public val observerCount: Int
        get() = observers.load().size

    /**
     * Stops new calls, then closes the engine and cancels calls still running on it (plan 04 §3.1
     * order).
     */
    public fun close() {
        http.close()
        closeEngine()
    }
}
