package io.github.eslamasabry.opencode_mobile.graph

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
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * The voice model fetcher the app uses (plan 10 §7.4, the `ModelFetcher` contract): one GET, no redirects followed
 * (PackInstaller follows at most 5 HTTPS hops itself), no cache, no cookies, no credentials, `Accept-Encoding:
 * identity` so the length pin holds; connect 15 s, read idle 20 s.
 *
 * Why OkHttp and not `HttpURLConnection` (owner's phone, 2026-11-07): with the Tailscale VPN as the default network
 * the model host's IPv6 addresses did not answer, and the platform connection tries a host's addresses one after
 * another, 15 s each, without letting a cancel through. The page sat at "0 MB of 148 MB" for minutes before
 * "timed out". OkHttp races IPv6 and IPv4 (fast fallback, as Dart's HttpClient did for the Flutter app) and a cancel
 * closes the socket at once.
 */
internal class OkHttpModelFetcher(
    private val io: CoroutineDispatcher,
    private val client: OkHttpClient = defaultClient(),
) : ModelFetcher {
    override suspend fun open(request: FetchRequest): Outcome<FetchResponse> {
        if (!request.url.startsWith("https://")) return media(MediaProblem.DownloadHttpsRequired)
        val http =
            try {
                Request.Builder()
                    .url(request.url)
                    .header("User-Agent", request.userAgent)
                    .header("Accept-Encoding", "identity")
                    .apply { request.rangeStart?.let { header("Range", "bytes=$it-") } }
                    .get()
                    .build()
            } catch (_: IllegalArgumentException) {
                return media(MediaProblem.DownloadHttpsRequired)
            }
        val call = client.newCall(http)
        return try {
            Body(call, call.await()).ok()
        } catch (e: IOException) {
            media(classify(e), e)
        }
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) else response.close()
                    }
                }
            )
        }

    private inner class Body(private val call: Call, private val response: Response) : FetchResponse {
        override val status: Int = response.code
        override val contentLength: Long? = response.header("Content-Length")?.toLongOrNull()?.takeIf { it >= 0 }
        override val contentRange: String? = response.header("Content-Range")
        override val location: String? = response.header("Location")
        private val stream: InputStream? = if (status in 200..299) response.body.byteStream() else null

        override suspend fun read(buffer: ByteArray): Int {
            val input = stream ?: return -1
            return try {
                // A cancel (Cancel, a stall, the page's network watch) closes the call, which ends a blocked read.
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }
                    io.dispatch(EmptyCoroutineContext) {
                        try {
                            continuation.resume(input.read(buffer))
                        } catch (e: IOException) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            } catch (e: IOException) {
                throw FetchFailure(classify(e))
            }
        }

        override fun close() {
            response.close()
            call.cancel()
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS: Long = 15_000
        const val READ_TIMEOUT_MS: Long = 20_000

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .fastFallback(true)
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()

        private fun IOException.saysOffline(): Boolean =
            message.orEmpty().let { it.contains("Network is unreachable", true) || it.contains("Unable to resolve host", true) }

        fun classify(error: IOException): MediaProblem =
            when (error) {
                is SocketTimeoutException -> MediaProblem.DownloadTimedOut
                // No route / no DNS means the phone is offline: say so, not "no response".
                is java.net.UnknownHostException, is java.net.NoRouteToHostException -> MediaProblem.DownloadOffline
                is java.net.ConnectException ->
                    if (error.saysOffline()) {
                        MediaProblem.DownloadOffline
                    } else {
                        MediaProblem.DownloadNoResponse
                    }
                is SSLException, is EOFException -> MediaProblem.DownloadClosed
                // Other socket errors that still say the network is gone ("Network is unreachable").
                else ->
                    if (error.saysOffline()) MediaProblem.DownloadOffline else MediaProblem.DownloadNoResponse
            }

        private fun media(problem: MediaProblem, error: Throwable? = null): Outcome<Nothing> =
            Failure.Media(problem, error?.let { Redacted.shape(it) }).failed()
    }
}
