package io.github.eslamasabry.oc.android.voice.net

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.voice.models.FetchFailure
import io.github.eslamasabry.oc.engine.voice.models.FetchRequest
import io.github.eslamasabry.oc.engine.voice.models.FetchResponse
import io.github.eslamasabry.oc.engine.voice.models.ModelFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException

/**
 * One GET per call over `HttpURLConnection` (plan 10 §7.4): redirects are NOT followed (PackInstaller follows at most 5
 * HTTPS hops itself), no caches, no cookies, no credentials, `Accept-Encoding: identity` so the length pin holds.
 * Connect timeout 15 s; read (body idle) timeout 20 s. Failures carry only a class-name shape, never the URL.
 * [connect] is a test seam (a local server); production opens the URL itself and refuses anything but HTTPS.
 */
public class HttpsModelFetcher(
    private val io: CoroutineDispatcher,
    private val connect: (String) -> HttpURLConnection = { url -> URL(url).openConnection() as HttpURLConnection },
    private val requireHttps: Boolean = true,
) : ModelFetcher {
    override suspend fun open(request: FetchRequest): Outcome<FetchResponse> {
        if (requireHttps && !request.url.startsWith("https://")) return media(MediaProblem.DownloadHttpsRequired)
        return runInterruptible(io) {
            val connection =
                try {
                    connect(request.url)
                } catch (e: IOException) {
                    return@runInterruptible media(MediaProblem.DownloadNoResponse, e)
                }
            try {
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("User-Agent", request.userAgent)
                connection.setRequestProperty("Accept-Encoding", "identity")
                request.rangeStart?.let { connection.setRequestProperty("Range", "bytes=$it-") }
                val status = connection.responseCode
                val stream: InputStream? =
                    if (status in 200..299) connection.inputStream else null
                Response(connection, status, stream).ok()
            } catch (e: IOException) {
                connection.disconnect()
                media(classify(e), e)
            }
        }
    }

    private inner class Response(
        private val connection: HttpURLConnection,
        override val status: Int,
        private val stream: InputStream?,
    ) : FetchResponse {
        override val contentLength: Long? = connection.getHeaderFieldLong("Content-Length", -1L).takeIf { it >= 0 }
        override val contentRange: String? = connection.getHeaderField("Content-Range")
        override val location: String? = connection.getHeaderField("Location")

        override suspend fun read(buffer: ByteArray): Int {
            val input = stream ?: return -1
            return try {
                runInterruptible(io) { input.read(buffer) }
            } catch (e: IOException) {
                throw FetchFailure(classify(e))
            }
        }

        override fun close() {
            try {
                stream?.close()
            } catch (_: IOException) {
                // already gone
            }
            connection.disconnect()
        }
    }

    public companion object {
        public const val CONNECT_TIMEOUT_MS: Int = 15_000
        public const val READ_TIMEOUT_MS: Int = 20_000

        internal fun classify(error: IOException): MediaProblem =
            when (error) {
                is SocketTimeoutException -> MediaProblem.DownloadTimedOut
                is SSLException, is EOFException -> MediaProblem.DownloadClosed
                else -> MediaProblem.DownloadNoResponse
            }

        private fun media(problem: MediaProblem, error: Throwable? = null): Outcome<Nothing> =
            Failure.Media(problem, error?.let { Redacted.shape(it) }).failed()
    }
}
