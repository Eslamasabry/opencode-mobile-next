package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.TerminalChannel
import io.github.eslamasabry.oc.core.net.EngineFactory
import io.github.eslamasabry.oc.core.net.TerminalSocketSpec
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.net.TrustRefusal
import io.github.eslamasabry.oc.core.net.ws.WsSession
import io.github.eslamasabry.oc.core.net.ws.WsSpec
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import okhttp3.Call
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * The OkHttp engine for one live connection (plan 04 §3.2–3.3, N4-D1, N4-D2). Compiled into both
 * androidMain and desktopMain from `src/jvmShared`.
 *
 * [base] builds the `OkHttpClient` settings ([hardenedBaseClient]: pool, dispatcher, no proxy,
 * timeouts). Whatever [base] returns, [create]:
 * - installs the trust gate and, for a pinned spec, the pinned leaf trust manager
 *   ([TrustEnforcement], plan 04 §4.4–4.6), so a spec's origin, cleartext consent and pin are
 *   always enforced before a byte is sent (review w1-core-net MAJOR 4; until `:core:net-okhttp`
 *   exists this is its stand-in);
 * - re-asserts the no-retry/no-redirect rules inside Ktor's `config {}` (Ktor applies its own
 *   `retryOnConnectionFailure(true)` on top of a preconfigured client) and installs the
 *   [MutationGuard] (audit codex-04 BLOCKER 1).
 *
 * [release] cancels running calls and shuts the pool and dispatcher down after the engine is closed
 * (plan 04 §3.1).
 */
public class OkHttpEngineFactory(
    private val base: (TransportSpec) -> OkHttpClient = ::hardenedBaseClient
) : EngineFactory {
    override fun create(spec: TransportSpec): HttpClientEngine {
        val client =
            LiveCalls.install(
                TrustEnforcement.install(
                    StaleConnectionRetry.install(MutationGuard.install(base(spec))),
                    spec,
                )
            )
        return OkHttp.create {
            preconfigured = client
            config {
                retryOnConnectionFailure(false)
                followRedirects(false)
                followSslRedirects(false)
            }
        }
    }

    override suspend fun connectSocket(
        engine: HttpClientEngine,
        spec: WsSpec,
        scope: CoroutineScope,
        dispatchers: AppDispatchers,
    ): Outcome<WsSession> {
        val client =
            (engine.config as? OkHttpConfig)?.preconfigured
                ?: return Outcome.Failed(Failure.UnreadableReply())
        return OkHttpWebSockets.connect(client, spec, scope, dispatchers, ::classify)
    }

    override fun release(engine: HttpClientEngine) {
        // Ktor builds its client from `preconfigured.newBuilder()`, which shares this pool and
        // dispatcher.
        val client = (engine.config as? OkHttpConfig)?.preconfigured ?: return
        // An open event stream ends now, not when the server closes it. OkHttp's dispatcher forgets
        // an async call once
        // its headers arrive, so the calls are cancelled through their own handles too.
        client.interceptors.filterIsInstance<LiveCalls>().forEach { it.cancelAll() }
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    override suspend fun openTerminal(
        engine: HttpClientEngine,
        spec: TerminalSocketSpec,
        scope: CoroutineScope,
        dispatchers: AppDispatchers,
    ): Outcome<TerminalChannel> {
        val client =
            (engine.config as? OkHttpConfig)?.preconfigured
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("terminal")))
        return openTerminalSocket(client, spec, scope, dispatchers, ::classify)
    }

    override fun classify(error: Throwable): TransportError =
        when (error) {
            is TrustRefusal ->
                TransportError
                    .ConnectFailed // nothing was sent; TransportFailures uses its own failure
            is ResendRefusedException -> TransportError.ResendRefused
            is UnknownHostException -> TransportError.DnsFailed
            is ConnectTimeoutException,
            is ConnectException,
            is NoRouteToHostException -> TransportError.ConnectFailed
            is HttpRequestTimeoutException,
            is SocketTimeoutException -> TransportError.Timeout
            else -> error.cause?.takeIf { it !== error }?.let(::classify) ?: TransportError.Other
        }
}

/**
 * Plan 04 §3.2's settings; the trust parts (gate, pin) are added by [OkHttpEngineFactory.create] on
 * top of any base.
 */
public fun hardenedBaseClient(spec: TransportSpec): OkHttpClient =
    OkHttpClient.Builder()
        // QA wave 4 B2: the OpenCode 2 server closes an idle keep-alive socket after 6 s, so a
        // pooled socket must be
        // dropped before that (Flutter's 15 s reused sockets the server had already closed).
        .connectionPool(ConnectionPool(5, IDLE_KEEP_ALIVE_MS, TimeUnit.MILLISECONDS))
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 10
            }
        )
        .proxy(Proxy.NO_PROXY) // Flutter: findProxy = DIRECT
        .retryOnConnectionFailure(false) // never resend a POST
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(spec.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS) // per read: inactivity, not a total
        .writeTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

/**
 * Reuses a socket after a 4 s reading pause, but drops it before the server's 6 s idle close
 * (B-PROTO-072).
 */
public const val IDLE_KEEP_ALIVE_MS: Long = 5_000

/**
 * QA wave 4 B2: the OpenCode 2 server closes an idle socket after 6 s, and OkHttp only
 * health-checks a pooled socket idle for 10 s or more, so a request 6–10 s after the last one was
 * written into a dead socket. Two rules, each retrying at most once:
 * 1. Before anything is written on a **reused** HTTP/1.1 connection, the socket is probed (1 ms
 *    read): if the server already closed it, the attempt stops with [StaleSocketException] —
 *    provably nothing was sent — and the call is tried once more on a fresh connection. This holds
 *    for a mutation too: the probe runs before the [MutationGuard]'s attempt counter, so the retry
 *    is still the mutation's first network attempt.
 * 2. A read (GET/HEAD/OPTIONS) that fails with an I/O error on a reused connection is tried once
 *    more (a read is safe to repeat). A mutation that failed after its bytes may have left is never
 *    retried. A fresh connection's failure, a timeout or a cancelled call is not retried.
 */
internal object StaleConnectionRetry {
    private class Trace {
        @Volatile var reusedConnection = false
    }

    /** The pooled socket was closed by the server before this request wrote a byte. */
    class StaleSocketException :
        IOException("the pooled connection was already closed; nothing was sent")

    private val served: MutableSet<Connection> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    private val observe = Interceptor { chain ->
        val trace = chain.request().tag(Trace::class.java)
        val connection = chain.connection()
        if (trace != null && connection != null) {
            val reused = !served.add(connection)
            trace.reusedConnection = reused
            if (
                reused &&
                    connection.protocol() == Protocol.HTTP_1_1 &&
                    closedByPeer(connection.socket())
            ) {
                throw StaleSocketException()
            }
        }
        chain.proceed(chain.request())
    }

    /**
     * An idle HTTP/1.1 socket has nothing to read: end of stream (or stray bytes) means it is
     * unusable.
     */
    private fun closedByPeer(socket: Socket): Boolean {
        if (socket.isClosed || socket.isInputShutdown) return true
        val before = socket.soTimeout
        return try {
            socket.soTimeout = 1
            socket
                .getInputStream()
                .read() // returns: -1 = closed by the server; a byte = a broken exchange
            true
        } catch (_: SocketTimeoutException) {
            false // nothing to read: alive
        } catch (_: IOException) {
            true
        } finally {
            try {
                socket.soTimeout = before
            } catch (_: IOException) {}
        }
    }

    private class Retry(private val pool: ConnectionPool) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val first = Trace()
            try {
                return chain.proceed(request.newBuilder().tag(Trace::class.java, first).build())
            } catch (error: IOException) {
                val neverSent = error is StaleSocketException
                val staleRead =
                    !MutationGuard.isMutation(request.method) &&
                        first.reusedConnection &&
                        error !is InterruptedIOException &&
                        error !is ResendRefusedException &&
                        error !is TrustRefusal
                if (!(neverSent || staleRead) || chain.call().isCanceled()) throw error
                // The other idle sockets are as old as the dead one; a retry must not pick one of
                // them.
                pool.evictAll()
                return chain.proceed(request.newBuilder().tag(Trace::class.java, Trace()).build())
            }
        }
    }

    /**
     * A one-use PTY ticket is never replayed after a failed upgrade (the unit's terminal channel
     * name for [withoutRetries]).
     */
    fun forUpgrade(client: OkHttpClient): OkHttpClient = withoutRetries(client)

    /** A single-use-ticket upgrade must never inherit the read retry path. */
    fun withoutRetries(client: OkHttpClient): OkHttpClient =
        client
            .newBuilder()
            .apply {
                interceptors().removeAll { it is Retry }
            }
            .build()

    fun install(client: OkHttpClient): OkHttpClient {
        if (client.interceptors.any { it is Retry }) return client
        val builder = client.newBuilder()
        // After the MutationGuard's sealing interceptor, so a mutation keeps its one attempt
        // counter.
        val at = builder.interceptors().indexOfFirst { it === MutationGuard.sealer } + 1
        builder.interceptors().add(at, Retry(client.connectionPool))
        builder.networkInterceptors().add(0, observe)
        return builder.build()
    }
}

/**
 * Remembers every call of one client (weakly) so [OkHttpEngineFactory.release] can cancel a call
 * whose body is still being read: `Call.cancel()` closes its socket, so a blocked read ends at once
 * (plan 04 §3.1, B-PROTO-148).
 */
internal class LiveCalls private constructor() : Interceptor {
    private val calls: MutableSet<Call> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    override fun intercept(chain: Interceptor.Chain): Response {
        calls += chain.call()
        return chain.proceed(chain.request())
    }

    fun cancelAll() {
        val live = synchronized(calls) { calls.toList() }
        live.forEach { it.cancel() }
    }

    companion object {
        fun install(client: OkHttpClient): OkHttpClient {
            if (client.interceptors.any { it is LiveCalls }) return client
            return client.newBuilder().addInterceptor(LiveCalls()).build()
        }
    }
}

/**
 * A mutation was about to go out a second time inside OkHttp; it was stopped. The first one may
 * have landed.
 */
public class ResendRefusedException : IOException("a mutation was not sent a second time")

/**
 * Audit codex-04 BLOCKER 1: `retryOnConnectionFailure(false)` does not stop status-driven
 * follow-ups (`503` with `Retry-After: 0`, `421` on a coalesced connection) in
 * `RetryAndFollowUpInterceptor`. Two layers make every mutation (anything but GET/HEAD/OPTIONS)
 * non-repeatable:
 * 1. an application interceptor makes the body one-shot (bodyless mutations get an empty one-shot
 *    body), which turns off OkHttp's follow-ups and connection-failure recovery for that call, and
 *    tags the request with an attempt counter;
 * 2. a network interceptor counts real network attempts of that tagged request and throws
 *    [ResendRefusedException] on the second, whatever path inside OkHttp tried it.
 */
public object MutationGuard {
    private class Attempts {
        val count = AtomicInteger()
    }

    private val readMethods = setOf("GET", "HEAD", "OPTIONS")

    public fun isMutation(method: String): Boolean = method.uppercase() !in readMethods

    internal val sealer: Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (!isMutation(request.method)) return@Interceptor chain.proceed(request)
        val sealed =
            request
                .newBuilder()
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
        if (client.interceptors.firstOrNull() === sealer) return client
        val builder = client.newBuilder()
        builder.interceptors().add(0, sealer)
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
