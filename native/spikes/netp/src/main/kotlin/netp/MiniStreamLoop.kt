package netp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import java.io.IOException
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SseEvent {
    data class Data(val data: String) : SseEvent
    data class Comment(val text: String) : SseEvent
}

/** Line-level SSE reader (a stand-in for the plan's ported Sse2Parser; enough for the proofs). */
suspend fun ByteReadChannel.readSse(maxEventChars: Int = 16 * 1024 * 1024, onEvent: suspend (SseEvent) -> Unit) {
    val data = StringBuilder()
    while (true) {
        val line = readUTF8Line() ?: break
        when {
            line.isEmpty() -> if (data.isNotEmpty()) { onEvent(SseEvent.Data(data.removeSuffix("\n").toString())); data.clear() }
            line.startsWith(":") -> onEvent(SseEvent.Comment(line.substring(1).trim()))
            line.substringBefore(':') == "data" -> {
                data.append(line.substringAfter(':').removePrefix(" ")).append('\n')
                if (data.length > maxEventChars) throw IOException("event over cap")
            }
            else -> Unit
        }
    }
}

/**
 * A minimal EventStreamLoop (plan 04 §6.2) for the proofs: OpenCode 2 backoff, a socket-read stall timeout, and
 * `retryNow()` that cancels the wait AND the current attempt (finding of P-N6: evicting the pool does not touch a
 * connection that is in use, so a half-open stream otherwise waits for the stall timeout).
 */
class MiniStreamLoop(
    private val http: HttpClient,
    private val url: String,
    private val requestHeaders: Map<String, String>,
    private val stallMs: Long,
    private val scope: CoroutineScope,
    private val backoffMs: (attempt: Int) -> Long = { a -> min(16_000L, 500L shl min(a, 5)) },
) {
    sealed interface Signal {
        val generation: Int
        val atNanos: Long
        data class Connected(override val generation: Int, override val atNanos: Long) : Signal
        data class Event(override val generation: Int, override val atNanos: Long, val event: SseEvent) : Signal
        data class Dropped(override val generation: Int, override val atNanos: Long, val cause: String) : Signal
    }

    private val _signals = MutableSharedFlow<Signal>(extraBufferCapacity = 100_000)
    val signals: SharedFlow<Signal> get() = _signals
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var current: Job? = null
    @Volatile private var forced = false
    private var loop: Job? = null

    fun start() {
        loop = scope.launch {
            var attempt = 0
            var generation = 0
            while (isActive) {
                val gen = ++generation
                var cause = "ended"
                val job = launch {
                    try {
                        http.prepareGet(url) {
                            requestHeaders.forEach { (k, v) -> header(k, v) }
                            header(HttpHeaders.Accept, "text/event-stream")
                            timeout {
                                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                                socketTimeoutMillis = stallMs
                            }
                        }.execute { response ->
                            val type = response.headers[HttpHeaders.ContentType].orEmpty()
                            if (response.status.value != 200 || !type.startsWith("text/event-stream")) {
                                throw IOException("status ${response.status.value} $type")
                            }
                            _signals.emit(Signal.Connected(gen, System.nanoTime()))
                            attempt = 0
                            response.bodyAsChannel().readSse { _signals.emit(Signal.Event(gen, System.nanoTime(), it)) }
                        }
                    } catch (e: CancellationException) {
                        cause = "cancelled"
                        throw e
                    } catch (e: Throwable) {
                        cause = generateSequence(e) { it.cause }.joinToString(">") { it.javaClass.simpleName }
                    }
                }
                current = job
                job.join()
                _signals.emit(Signal.Dropped(gen, System.nanoTime(), cause))
                if (forced) { forced = false; attempt = 0; continue }
                attempt++
                withTimeoutOrNull(backoffMs(attempt)) { wake.receive() }
                if (forced) { forced = false; attempt = 0 }
            }
        }
    }

    /** Network changed / Try again: drop the current attempt (if any) and connect now with attempt = 0. */
    fun retryNow() {
        forced = true
        current?.cancel()
        wake.trySend(Unit)
    }

    fun stop() { loop?.cancel() }
}
