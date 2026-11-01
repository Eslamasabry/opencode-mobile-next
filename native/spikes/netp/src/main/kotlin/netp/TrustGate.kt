package netp

import java.io.IOException
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Plan 04 §4.6: refusals thrown before any socket is opened. */
class TrustRefusal(val kind: Kind) : IOException(kind.name) {
    enum class Kind { CredentialsInUrl, OriginChanged, CleartextNotConfirmed, PinNeedsHttps, CertificateChanged }
}

class PinMismatchException : CertificateException("server certificate does not match the pin")

fun originOf(url: HttpUrl): String {
    val host = if (url.host.contains(':')) "[${url.host}]" else url.host
    return "${url.scheme}://${host.lowercase()}:${url.port}"
}

/** Plan 04 §4.6, first application interceptor. Also runs for WebSocket upgrades on the same client. */
class TrustGateInterceptor(
    private val origin: String,
    private val cleartextPermitted: Boolean,
    private val pin: PinnedLeafTrustManager?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw TrustRefusal(TrustRefusal.Kind.CredentialsInUrl)
        if (originOf(url) != origin) throw TrustRefusal(TrustRefusal.Kind.OriginChanged)
        if (!url.isHttps && !cleartextPermitted) throw TrustRefusal(TrustRefusal.Kind.CleartextNotConfirmed)
        if (pin != null && !url.isHttps) throw TrustRefusal(TrustRefusal.Kind.PinNeedsHttps)
        if (pin?.latchedMismatch == true) throw TrustRefusal(TrustRefusal.Kind.CertificateChanged)
        return chain.proceed(chain.request())
    }
}

/** Plan 04 §4.4 verbatim (leaf-DER SHA-256; the pin is the identity; expiry is its own error). */
class PinnedLeafTrustManager(
    private val pin: ByteArray,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : X509ExtendedTrustManager() {
    @Volatile var latchedMismatch: Boolean = false
        private set

    /** How many times a TLS stack asked us; proves the provider actually routed through this manager. */
    @Volatile var checks: Int = 0
        private set

    private fun check(chain: Array<out X509Certificate>?) {
        checks++
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
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?): Unit = throw CertificateException()
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?): Unit = throw CertificateException()
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    fun hostnameVerifier(): HostnameVerifier = HostnameVerifier { _, session ->
        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return@HostnameVerifier false
        MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(leaf.encoded), pin)
    }
}
