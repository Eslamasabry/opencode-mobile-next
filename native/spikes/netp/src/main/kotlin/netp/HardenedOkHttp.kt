package netp

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.net.Proxy
import java.security.Provider
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/** Plan 04 §3.2 `JvmTransportSpec`, plus two spike-only switches. Plain values only. */
class JvmTransportSpec(
    val origin: String,
    val cleartextPermitted: Boolean,
    val pinnedLeafSha256: ByteArray?,
    val connectTimeoutMs: Long = 8_000,
    val readTimeoutMs: Long = 120_000,
    /** TLS provider for the pinned context. Android: Conscrypt is the platform default; the JVM spike passes it. */
    val sslProvider: Provider? = null,
    /** false = the plan's §3.2 builder verbatim (kept only to reproduce the codex-04 BLOCKER as evidence). */
    val auditFixes: Boolean = true,
)

object HardenedOkHttp {
    /** Where [NoStatusFollowUps] moves a stripped `Retry-After`, so callers can still read the server's hint. */
    const val ORIGINAL_RETRY_AFTER = "X-Oc-Retry-After"

    fun build(spec: JvmTransportSpec): OkHttpClient {
        val pin = spec.pinnedLeafSha256?.let { PinnedLeafTrustManager(it) }
        val builder = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 15, TimeUnit.SECONDS))
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 10 })
            .proxy(Proxy.NO_PROXY)
            .retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(spec.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(spec.readTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor(TrustGateInterceptor(spec.origin, spec.cleartextPermitted, pin))
        if (spec.auditFixes) {
            // codex-04 BLOCKER #1: retryOnConnectionFailure(false) is not the whole invariant.
            builder.addInterceptor(OneShotMutationBodies)      // no body replay: connection-failure retry, 408, 421
            builder.addNetworkInterceptor(NoStatusFollowUps)   // no status-driven follow-up: 503 Retry-After: 0
        }
        if (pin != null) {
            val tls = (spec.sslProvider?.let { SSLContext.getInstance("TLS", it) } ?: SSLContext.getInstance("TLS"))
                .apply { init(null, arrayOf(pin), null) }
            builder.sslSocketFactory(tls.socketFactory, pin).hostnameVerifier(pin.hostnameVerifier())
        }
        return builder.build()
    }

    /**
     * Ktor engine over [client]. Ktor's OkHttpConfig default block sets retryOnConnectionFailure(true) and runs on
     * `preconfigured.newBuilder()`, so the plan re-asserts ours. [reassert] = false only for evidence tests.
     */
    fun ktorEngine(client: OkHttpClient, reassert: Boolean = true): HttpClientEngine = OkHttp.create {
        preconfigured = client
        if (reassert) {
            config {
                retryOnConnectionFailure(false)
                followRedirects(false)
                followSslRedirects(false)
            }
        }
    }

    /** Plan 04 §3.3 HttpClientFactory, minus trace/UA (not under test). */
    fun ktorClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    fun shutdown(client: OkHttpClient) {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}

private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS")

/** Application interceptor: every mutation body becomes one-shot, so OkHttp can never replay it. */
object OneShotMutationBodies : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        if (request.method in SAFE_METHODS || body == null || body.isOneShot()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().method(request.method, OneShot(body)).build())
    }

    private class OneShot(private val delegate: RequestBody) : RequestBody() {
        override fun contentType(): MediaType? = delegate.contentType()
        override fun contentLength(): Long = delegate.contentLength()
        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
        override fun isDuplex(): Boolean = delegate.isDuplex()
        override fun isOneShot(): Boolean = true
    }
}

/**
 * Network interceptor: OkHttp's RetryAndFollowUpInterceptor re-sends on `503` + `Retry-After: 0` regardless of
 * retryOnConnectionFailure, and bodyless mutations (POST /interrupt, DELETE) have no body to make one-shot. Moving the
 * header out of the way removes the follow-up for every method; the value stays readable under [HardenedOkHttp.ORIGINAL_RETRY_AFTER].
 * (408 is already gated by retryOnConnectionFailure(false); 421 only follows up on a coalesced HTTP/2 connection and
 * is stopped for bodied requests by the one-shot body.)
 */
object NoStatusFollowUps : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val retryAfter = response.header("Retry-After") ?: return response
        if (response.code != 503 && response.code != 408) return response
        return response.newBuilder()
            .removeHeader("Retry-After")
            .header(HardenedOkHttp.ORIGINAL_RETRY_AFTER, retryAfter)
            .build()
    }
}
