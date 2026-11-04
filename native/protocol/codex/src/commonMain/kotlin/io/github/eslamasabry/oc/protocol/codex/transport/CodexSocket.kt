package io.github.eslamasabry.oc.protocol.codex.transport

import io.github.eslamasabry.oc.protocol.codex.CodexEndpoint
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The upgraded app-server socket (Dart `CodexSocket`). Production sockets are written by the app (OkHttp): the upgrade
 * carries `Authorization: Bearer <secret>`, follows no redirect, offers no extension or sub-protocol, caps a message at
 * [CodexTransport.MAX_FRAME_BYTES] and pings every 20 s. Tests use an in-memory fake app-server.
 */
public interface CodexSocket {
    /** Text frames in arrival order; closed when the server or the network closes the socket. */
    public val incoming: ReceiveChannel<String>

    /** Throws when the socket cannot take the frame. */
    public suspend fun send(text: String)

    /** Idempotent. */
    public suspend fun close()
}

/**
 * Opens the upgraded socket for [endpoint] with the connection [secret] (Dart `connectCodexSocket`). A 401/403 throws
 * `CodexException(Authentication)`; a bad 101 `InvalidResponse`; anything else `Disconnected` (any other throwable is
 * read as `Disconnected`).
 */
public fun interface CodexSocketFactory {
    public suspend fun open(endpoint: CodexEndpoint, secret: String): CodexSocket
}

/** A server-initiated notification or request (Dart `CodexRpcEvent`). [requestId] is set for a request. */
public class CodexRpcEvent(
    public val epoch: Int,
    public val method: String,
    public val params: JsonObject,
    public val requestId: JsonElement?,
)

/** Synchronous observers (Dart's `sync: true` broadcast streams): called on the transport's lane, in arrival order. */
public interface CodexTransportListener {
    public fun onEvent(event: CodexRpcEvent) {}

    /** The socket of [epoch] was lost (never called after `close()`). */
    public fun onDisconnect(epoch: Int) {}
}
