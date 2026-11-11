package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.net.sse.SseFrame
import io.github.eslamasabry.oc.core.net.sse.SseParser
import io.github.eslamasabry.oc.engine.team.fleet.FleetFrame
import io.github.eslamasabry.oc.engine.team.fleet.FleetReply
import io.github.eslamasabry.oc.engine.team.fleet.FleetStreamEnd
import io.github.eslamasabry.oc.engine.team.fleet.FleetTransport
import io.github.eslamasabry.oc.engine.team.fleet.FleetTransports
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * The AI Team on a computer over OkHttp (Dart `OrchestrationHttpClient` + `OrchestrationSseClient`). Each base URL gets
 * its own client: no cookies, no redirects, no retries, and never a credential header (the host takes none; the
 * engine's address rule allowed this base before it was opened). Nothing here logs a URL or a body.
 */
internal class AppFleetTransports(private val dispatchers: AppDispatchers) : FleetTransports {
    override fun open(baseUrl: String): FleetTransport = AppFleetTransport(baseUrl.trim().trimEnd('/'), dispatchers)
}

private class AppFleetTransport(private val base: String, private val dispatchers: AppDispatchers) : FleetTransport {
    private val client =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .build()

    @Volatile private var closed = false

    /**
     * The request's address, or null when OkHttp cannot parse it (a saved or front-given address the rule allowed but
     * that is not a valid URL): the caller answers "not reachable" instead of OkHttp throwing out of the team page.
     */
    private fun url(path: String, query: List<Pair<String, String>>): HttpUrl? =
        (if (query.isEmpty()) base + path
        else base + path + "?" + query.joinToString("&") { (k, v) -> DartJson.encodeComponent(k) + "=" + DartJson.encodeComponent(v) })
            .toHttpUrlOrNull()

    private fun answered(response: Response): FleetReply.Answered {
        val text = response.body.byteStream().use { stream -> String(readBounded(stream), Charsets.UTF_8) }
        return FleetReply.Answered(
            status = response.code,
            text = text,
            hostRequestId = response.header("X-GC-Request-Id"),
            replayed = response.header("Idempotent-Replayed")?.lowercase().let { it == "true" || it == "1" },
        )
    }

    /** A body of at most [MAX_BODY] bytes: a larger answer is cut, so a broken host cannot fill the phone's memory. */
    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < MAX_BODY) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, minOf(n, MAX_BODY - out.size()))
        }
        return out.toByteArray()
    }

    private suspend fun call(request: Request, timeoutMs: Long): FleetReply =
        withContext(dispatchers.io) {
            if (closed) return@withContext FleetReply.Unreachable()
            val bounded = client.newBuilder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS).callTimeout(timeoutMs + 8_000, TimeUnit.MILLISECONDS).build()
            val call = bounded.newCall(request)
            cancelledWith(call) {
                try {
                    call.execute().use(::answered)
                } catch (e: IOException) {
                    ensureActive()
                    FleetReply.Unreachable(timedOut = e is SocketTimeoutException)
                }
            }
        }

    /**
     * Runs [block] (a blocking OkHttp [call] on this IO thread) and cancels the call the moment the caller is cancelled. A
     * completion handler would only run once the blocked read returned (up to the stall timeout, 90 s on a stream), so a
     * page that closed kept its connection open; the watcher runs on another thread and closes the socket at once.
     */
    private suspend fun <T> cancelledWith(call: Call, block: suspend () -> T): T =
        coroutineScope {
            val watcher =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        awaitCancellation()
                    } finally {
                        call.cancel()
                    }
                }
            try {
                block()
            } finally {
                watcher.cancel()
            }
        }

    override suspend fun get(path: String, query: List<Pair<String, String>>, timeoutMs: Long): FleetReply {
        val target = url(path, query) ?: return FleetReply.Unreachable()
        return call(Request.Builder().url(target).header("Accept", "application/json").get().build(), timeoutMs)
    }

    override suspend fun send(
        method: String,
        path: String,
        body: Map<String, Any?>?,
        requestId: String,
        idempotencyKey: String?,
    ): FleetReply {
        val target = url(path, emptyList()) ?: return FleetReply.Unreachable()
        val payload = body?.let { DartJson.encode(it).toRequestBody(JSON) }
        val request =
            Request.Builder()
                .url(target)
                .header("Accept", "application/json")
                .header("X-GC-Request", requestId)
                .apply { idempotencyKey?.let { header("Idempotency-Key", it) } }
                .method(method, payload ?: if (method == "POST") ByteArray(0).toRequestBody(JSON) else null)
                .build()
        return call(request, WRITE_TIMEOUT_MS)
    }

    override suspend fun stream(path: String, lastEventId: String?, stallMs: Long, onFrame: suspend (FleetFrame) -> Unit): FleetStreamEnd =
        withContext(dispatchers.io) {
            if (closed) return@withContext FleetStreamEnd.Dropped()
            val target = url(path, emptyList()) ?: return@withContext FleetStreamEnd.Dropped()
            val streaming = client.newBuilder().readTimeout(stallMs, TimeUnit.MILLISECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()
            val request =
                Request.Builder()
                    .url(target)
                    .header("Accept", "text/event-stream")
                    .apply { lastEventId?.let { header("Last-Event-ID", it) } }
                    .get()
                    .build()
            val call = streaming.newCall(request)
            cancelledWith(call) {
                val response =
                    try {
                        call.execute()
                    } catch (e: IOException) {
                        ensureActive()
                        return@cancelledWith FleetStreamEnd.Dropped(e is SocketTimeoutException)
                    }
                response.use { r ->
                    val type = r.header("Content-Type").orEmpty().lowercase()
                    if (!r.isSuccessful || !type.startsWith("text/event-stream")) return@cancelledWith FleetStreamEnd.Refused(r.code)
                    val pending = ArrayList<SseFrame>()
                    val parser = SseParser { pending += it }
                    val input = r.body.byteStream()
                    val buffer = ByteArray(8192)
                    try {
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            parser.feed(buffer, 0, n)
                            deliver(pending, onFrame)
                        }
                        parser.flush()
                        deliver(pending, onFrame)
                        FleetStreamEnd.Ended
                    } catch (e: IOException) {
                        ensureActive()
                        parser.discardPartial()
                        FleetStreamEnd.Dropped(e is SocketTimeoutException)
                    }
                }
            }
        }

    private suspend fun deliver(pending: MutableList<SseFrame>, onFrame: suspend (FleetFrame) -> Unit) {
        if (pending.isEmpty()) return
        val frames = pending.toList()
        pending.clear()
        frames.forEach { onFrame(FleetFrame(it.event, it.id, it.data)) }
    }

    override fun close() {
        if (closed) return
        closed = true
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val MAX_BODY = 8 * 1024 * 1024
        const val WRITE_TIMEOUT_MS = 30_000L
    }
}
