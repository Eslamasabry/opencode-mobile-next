package io.github.eslamasabry.oc.core.net.ws

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.net.TransportSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

/**
 * AGENT-2 socket API (connection-owned, byte-bounded), named apart from PROTO's Ws* types in WebSockets.kt.
 * A connection-owned socket. Header values never appear in diagnostic representations. */
public class SocketSpec(
    public val transport: TransportSpec,
    public val url: String,
    public val headers: List<Pair<String, String>>,
    public val owner: CoroutineScope,
    public val subprotocol: String? = null,
    public val maxMessageBytes: Int = 4 * 1024 * 1024,
    public val handshakeTimeoutMs: Long = 8_000,
    public val pingIntervalMs: Long = 20_000,
    public val pongTimeoutMs: Long = 15_000,
) {
    override fun toString(): String = "SocketSpec(maxMessageBytes=$maxMessageBytes)"
}

public sealed interface SocketMessage {
    public class Text(public val text: String) : SocketMessage

    public class Binary(public val bytes: ByteArray) : SocketMessage
}

public data class SocketClose(val code: Int? = null, val failure: Failure? = null)

public interface SocketSession {
    public val incoming: Flow<SocketMessage>
    public val closeReason: Deferred<SocketClose>

    public suspend fun sendText(text: String): Outcome<Unit>

    public suspend fun sendBinary(bytes: ByteArray): Outcome<Unit>

    public suspend fun close(code: Short = 1000)
}

public fun interface SocketConnections {
    public suspend fun open(spec: SocketSpec): Outcome<SocketSession>
}
