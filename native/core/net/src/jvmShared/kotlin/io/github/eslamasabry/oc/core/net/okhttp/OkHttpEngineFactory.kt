package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.net.EngineFactory
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The OkHttp engine for one live connection (plan 04 §3.2–3.3, N4-D1, N4-D2). Compiled into both androidMain and
 * desktopMain from `src/jvmShared`.
 *
 * [base] builds the hardened `OkHttpClient`. TODO(S1-SECNET): pass `HardenedOkHttp::build` from `:core:net-okhttp`
 * (pin trust manager, trust gate interceptor) once that module lands; [hardenedBaseClient] is the stand-in with the
 * same non-trust settings. Whatever [base] returns, [create] re-asserts the no-retry/no-redirect rules inside Ktor's
 * `config {}` (Ktor applies its own `retryOnConnectionFailure(true)` on top of a preconfigured client) and installs
 * the [MutationGuard] (audit codex-04 BLOCKER 1).
 */
public class OkHttpEngineFactory(
    private val base: (TransportSpec) -> OkHttpClient = ::hardenedBaseClient,
) : EngineFactory {
    override fun create(spec: TransportSpec): HttpClientEngine {
        val client = MutationGuard.install(base(spec))
        return OkHttp.create {
            preconfigured = client
            config {
                retryOnConnectionFailure(false)
                followRedirects(false)
                followSslRedirects(false)
            }
        }
    }

    override fun classify(error: Throwable): TransportError =
        when (error) {
            is ResendRefusedException -> TransportError.ResendRefused
            is ConnectTimeoutException, is ConnectException, is UnknownHostException, is NoRouteToHostException ->
                TransportError.ConnectFailed
            is HttpRequestTimeoutException, is SocketTimeoutException -> TransportError.Timeout
            else -> error.cause?.takeIf { it !== error }?.let(::classify) ?: TransportError.Other
        }
}

/** Until `:core:net-okhttp` exists: the plan's §3.2 settings without the trust parts (pin, trust gate). */
public fun hardenedBaseClient(spec: TransportSpec): OkHttpClient =
    OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 15, TimeUnit.SECONDS)) // Flutter: 15 s idle keep-alive
        .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 10 })
        .proxy(Proxy.NO_PROXY) // Flutter: findProxy = DIRECT
        .retryOnConnectionFailure(false) // never resend a POST
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(spec.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(spec.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(spec.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

/** A mutation was about to go out a second time inside OkHttp; it was stopped. The first one may have landed. */
public class ResendRefusedException : IOException("a mutation was not sent a second time")

/**
 * Audit codex-04 BLOCKER 1: `retryOnConnectionFailure(false)` does not stop status-driven follow-ups (`503` with
 * `Retry-After: 0`, `421` on a coalesced connection) in `RetryAndFollowUpInterceptor`. Two layers make every mutation
 * (anything but GET/HEAD/OPTIONS) non-repeatable:
 * 1. an application interceptor makes the body one-shot (bodyless mutations get an empty one-shot body), which turns
 *    off OkHttp's follow-ups and connection-failure recovery for that call, and tags the request with an attempt
 *    counter;
 * 2. a network interceptor counts real network attempts of that tagged request and throws [ResendRefusedException] on
 *    the second, whatever path inside OkHttp tried it.
 */
public object MutationGuard {
    private class Attempts {
        val count = AtomicInteger()
    }

    private val readMethods = setOf("GET", "HEAD", "OPTIONS")

    public fun isMutation(method: String): Boolean = method.uppercase() !in readMethods

    private val tagAndSeal = Interceptor { chain ->
        val request = chain.request()
        if (!isMutation(request.method)) return@Interceptor chain.proceed(request)
        val sealed = request.newBuilder()
            .method(request.method, OneShotBody(request.body))
            .tag(Attempts::class.java, Attempts())
            .build()
        chain.proceed(sealed)
    }

    private val countAttempts = Interceptor { chain ->
        val attempts = chain.request().tag(Attempts::class.java)
        if (attempts != null && attempts.count.incrementAndGet() > 1) throw ResendRefusedException()
        chain.proceed(chain.request())
    }

    /** Puts the guard first among application interceptors and last among network interceptors. */
    public fun install(client: OkHttpClient): OkHttpClient {
        if (client.interceptors.firstOrNull() === tagAndSeal) return client
        val builder = client.newBuilder()
        builder.interceptors().add(0, tagAndSeal)
        builder.networkInterceptors().add(countAttempts)
        return builder.build()
    }

    private class OneShotBody(private val delegate: RequestBody?) : RequestBody() {
        override fun contentType(): MediaType? = delegate?.contentType()

        override fun contentLength(): Long = delegate?.contentLength() ?: 0L

        override fun isOneShot(): Boolean = true

        override fun isDuplex(): Boolean = delegate?.isDuplex() ?: false

        override fun writeTo(sink: BufferedSink) {
            delegate?.writeTo(sink)
        }
    }
}
