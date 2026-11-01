package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.CertificateWhy
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.net.TrustRefusal
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Plan 04 §4.4–4.6 on every client [OkHttpEngineFactory] builds (review w1-core-net MAJOR 4): the trust gate is the
 * first application interceptor, and a pinned spec swaps in [PinnedLeafTrustManager], so normal PKI can never stand
 * in for a pin. Ported from the P-N2 spike (`spikes/netp/TrustGate.kt`) with core's failures.
 *
 * Installed by [OkHttpEngineFactory.create] whatever its `base` returns, so no caller can opt out of it.
 */
internal object TrustEnforcement {
    fun install(client: OkHttpClient, spec: TransportSpec): OkHttpClient {
        val pin = spec.pinnedLeafSha256?.let { PinnedLeafTrustManager(it.copyOf()) }
        val builder = client.newBuilder()
        builder.interceptors().removeAll { it is TrustGateInterceptor }
        builder.interceptors().add(0, TrustGateInterceptor(originString(spec), spec.cleartextPermitted, pin))
        if (pin != null) {
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pin), null) }
            builder.sslSocketFactory(tls.socketFactory, pin).hostnameVerifier(pin.hostnameVerifier())
        }
        return builder.build()
    }

    /** `scheme://host:port`, lower-case, IPv6 bracketed, explicit port: the same form as [originOf]. */
    fun originString(spec: TransportSpec): String {
        val o = spec.origin
        val host = o.host.lowercase().removePrefix("[").removeSuffix("]")
        return "${o.scheme.lowercase()}://${if (':' in host) "[$host]" else host}:${o.port}"
    }

    fun originOf(url: HttpUrl): String {
        val host = url.host.lowercase()
        return "${url.scheme}://${if (':' in host) "[$host]" else host}:${url.port}"
    }
}

/**
 * Plan 04 §4.6, in this order; each refusal is thrown before `chain.proceed`, so nothing is sent:
 * user info → `CredentialsInUrl`; another origin (also any redirect target) → `OriginChanged`; `http` without consent →
 * `CleartextNotConfirmed`; `http` with a pin → `PinNeedsHttps`; a pin that already mismatched → `Certificate(Changed)`.
 * A handshake that fails on the pin, or on certificate validity, comes back as `Certificate(...)` too.
 */
internal class TrustGateInterceptor(
    private val origin: String,
    private val cleartextPermitted: Boolean,
    private val pin: PinnedLeafTrustManager?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw refused(RefusalReason.CredentialsInUrl)
        if (TrustEnforcement.originOf(url) != origin) throw refused(RefusalReason.OriginChanged)
        if (!url.isHttps && !cleartextPermitted) throw refused(RefusalReason.CleartextNotConfirmed)
        if (pin != null && !url.isHttps) throw refused(RefusalReason.PinNeedsHttps)
        if (pin?.latchedMismatch == true) throw certificate(CertificateWhy.Changed)
        try {
            return chain.proceed(chain.request())
        } catch (e: IOException) {
            if (e is TrustRefusal) throw e
            if (pin?.latchedMismatch == true) throw certificate(CertificateWhy.Changed)
            if (e is SSLHandshakeException) certificateWhy(e)?.let { throw certificate(it) }
            throw e
        }
    }

    private fun certificateWhy(e: Throwable): CertificateWhy? {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 8) {
            when (current) {
                is PinMismatchException -> return CertificateWhy.Changed
                is CertificateExpiredException, is CertificateNotYetValidException -> return CertificateWhy.Expired
                is CertPathValidatorException -> return CertificateWhy.Untrusted
            }
            current = current.cause?.takeIf { it !== current }
            depth += 1
        }
        return null
    }

    private fun refused(reason: RefusalReason) = TrustRefusal(Failure.Refused(reason))

    private fun certificate(why: CertificateWhy) = TrustRefusal(Failure.Certificate(why))
}

internal class PinMismatchException : CertificateException("server certificate does not match the pin")

/**
 * Plan 04 §4.4: the SHA-256 of the leaf's DER is the identity; hostname and CA checks do not apply; validity is its
 * own error (expired ≠ changed, B-SEC-007). A mismatch latches: every later call on this client is refused.
 */
internal class PinnedLeafTrustManager(
    private val pin: ByteArray,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : X509ExtendedTrustManager() {
    @Volatile var latchedMismatch: Boolean = false
        private set

    private fun check(chain: Array<out X509Certificate>?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no server certificate")
        val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        if (!MessageDigest.isEqual(digest, pin)) {
            latchedMismatch = true
            throw PinMismatchException()
        }
        leaf.checkValidity(Date(nowMs()))
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = check(chain)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = check(chain)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = check(chain)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?): Unit = throw CertificateException()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?): Unit =
        throw CertificateException()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?): Unit =
        throw CertificateException()

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    fun hostnameVerifier(): HostnameVerifier = HostnameVerifier { _, session ->
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return@HostnameVerifier false
        MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(leaf.encoded), pin)
    }
}
