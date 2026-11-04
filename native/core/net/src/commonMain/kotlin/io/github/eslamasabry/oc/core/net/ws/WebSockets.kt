package io.github.eslamasabry.oc.core.net.ws

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.net.TransportSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

/** A connection-owned socket. Header values never appear in diagnostic representations. */
public class WsSpec(
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
    override fun toString(): String = "WsSpec(maxMessageBytes=$maxMessageBytes)"
}

public sealed interface WsMessage {
    public class Text(public val text: String) : WsMessage

    public class Binary(public val bytes: ByteArray) : WsMessage
}

public data class WsClose(val code: Int? = null, val failure: Failure? = null)

public interface WsSession {
    public val incoming: Flow<WsMessage>
    public val closeReason: Deferred<WsClose>

    public suspend fun sendText(text: String): Outcome<Unit>

    public suspend fun sendBinary(bytes: ByteArray): Outcome<Unit>

    public suspend fun close(code: Short = 1000)
}

public fun interface WsConnections {
    public suspend fun open(spec: WsSpec): Outcome<WsSession>
}
