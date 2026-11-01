package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.net.StatusMapping
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportFailures
import io.github.eslamasabry.oc.core.net.CallKind
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

/** An open event-stream body. [read] returns -1 at a clean end and throws on an IO error. */
public interface SseBody {
    public suspend fun read(buffer: ByteArray): Int
}

public sealed interface SseConnectResult {
    /** A 200 event stream was opened and its session ran to the end (clean, error or cancelled by the loop). */
    public data object Ended : SseConnectResult

    /**
     * No stream. [stop] = do not retry on a schedule (Paseo/Codex-style terminal sign-in, incompatible server). An
     * OpenCode 2 sign-in failure is NOT a stop: its stream keeps the normal backoff (plan 04 §4.8, §6.2).
     */
    public data class Failed(val failure: Failure, val stop: Boolean) : SseConnectResult
}

/** Opens one stream per call; the body is valid only inside [session]. */
public fun interface SseConnector {
    public suspend fun connect(lastEventId: String?, session: suspend (SseBody) -> Unit): SseConnectResult
}

/**
 * The Ktor connector: `Accept: text/event-stream`, no request timeout, the stall rule lives in the loop (N4-D7).
 * [request] is rebuilt on every try (auth, `after=` cursor, `Last-Event-ID`).
 *
 * [stopOnAuthFailure]: false for OpenCode 2 (default): a 401/403 is a failed try like any other, the loop keeps its
 * backoff and publishes the failure in `lastFailure` (plan 04 §4.8, §6.2; Flutter `sse2.dart:189-222`; review
 * w1-core-net MAJOR 8). True only for a backend whose plan row makes sign-in failures terminal.
 */
public class KtorSseConnector(
    private val client: HttpClient,
    private val classify: (Throwable) -> TransportError = { TransportError.Other },
    private val stopOnAuthFailure: Boolean = false,
    private val request: HttpRequestBuilder.(lastEventId: String?) -> Unit,
) : SseConnector {
    override suspend fun connect(lastEventId: String?, session: suspend (SseBody) -> Unit): SseConnectResult {
        val outcome = attempt({ TransportFailures.of(CallKind.Read, classify(it), it) }) {
            client.prepareGet {
                request(lastEventId)
                header(HttpHeaders.Accept, EVENT_STREAM)
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
            }.execute { response ->
                val status = response.status.value
                when {
                    status == 401 || status == 403 ->
                        SseConnectResult.Failed(StatusMapping.failureFor(status), stop = stopOnAuthFailure)
                    status !in 200..299 -> SseConnectResult.Failed(StatusMapping.failureFor(status), stop = false)
                    response.contentType()?.match(ContentType.Text.EventStream) != true ->
                        SseConnectResult.Failed(Failure.UnreadableReply(Redacted.shape(null, status)), stop = false)
                    else -> {
                        session(ChannelBody(response.bodyAsChannel()))
                        SseConnectResult.Ended
                    }
                }
            }
        }
        return when (outcome) {
            is io.github.eslamasabry.oc.core.common.Outcome.Ok -> outcome.value
            is io.github.eslamasabry.oc.core.common.Outcome.Failed -> SseConnectResult.Failed(outcome.failure, stop = false)
        }
    }

    private class ChannelBody(private val channel: ByteReadChannel) : SseBody {
        override suspend fun read(buffer: ByteArray): Int = channel.readAvailable(buffer, 0, buffer.size)
    }

    private companion object {
        const val EVENT_STREAM = "text/event-stream"

        /** Plan 04 §3.4: 45 s socket read (3 × heartbeat), the same as the loop's stall rule. */
        const val SOCKET_TIMEOUT_MS = 45_000L
    }
}
