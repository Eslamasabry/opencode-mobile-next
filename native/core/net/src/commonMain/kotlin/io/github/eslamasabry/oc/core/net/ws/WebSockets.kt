package io.github.eslamasabry.oc.core.net.ws

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

/** One same-origin socket request. Its ticket, URL and headers are never printable. */
public class WsSpec(
    public val url: String,
    public val headers: Map<String, String>,
    public val subprotocol: String? = null,
    public val maxMessageBytes: Long = 16L * 1024 * 1024,
    public val incomingByteBudget: Long = 2 * maxMessageBytes,
    public val pingIntervalMs: Long = 20_000,
    public val pongTimeoutMs: Long = 15_000,
    public val handshakeTimeoutMs: Long = 8_000,
) {
    override fun toString(): String = "WsSpec(<redacted>)"
}

public sealed interface WsMessage {
    public class Text(public val text: String) : WsMessage {
        override fun toString(): String = "WsMessage.Text(<redacted>)"
    }

    public class Binary(public val bytes: ByteArray) : WsMessage {
        override fun toString(): String = "WsMessage.Binary(<redacted>)"
    }
}

/** Peer reason text is deliberately discarded. */
public data class WsClose(val code: Short?, val failure: Failure? = null)

/** One connection, no reconnection or input replay. The owning connection scope closes it. */
public interface WsSession {
    public val incoming: Flow<WsMessage>
    public val closeReason: Deferred<WsClose?>

    public suspend fun sendText(text: String): Outcome<Unit>

    public suspend fun close(code: Short = 1000)
}
