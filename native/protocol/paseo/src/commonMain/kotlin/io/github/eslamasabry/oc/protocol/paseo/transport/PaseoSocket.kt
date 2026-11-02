package io.github.eslamasabry.oc.protocol.paseo.transport

import io.github.eslamasabry.oc.protocol.paseo.PaseoEndpoint
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.json.JsonObject

/** One WebSocket message as the daemon sent it. */
public sealed interface PaseoFrame {
    public class Text(public val text: String) : PaseoFrame

    /** Terminal data: `[opcode, slot, …payload]`. */
    public class Binary(public val bytes: ByteArray) : PaseoFrame
}

/**
 * The upgraded daemon socket (Dart `PaseoSocket`). Production sockets come from `:core:net`'s `WsSession` (plan 04 §5,
 * P-N1): `ws` upgrade with redirects off, no `Sec-WebSocket-Extensions`, 8 MiB message cap (1 MiB on the bounded
 * history connection), WS ping every 20 s. Tests use a scripted daemon; the live proof uses the JDK client.
 */
public interface PaseoSocket {
    /** Frames in arrival order; closed when the daemon or the network closes the socket. */
    public val incoming: ReceiveChannel<PaseoFrame>

    /** The WebSocket close code once the daemon has closed (4401 wrong password, 4003 protocol mismatch), else null. */
    public val closeCode: Int?

    /** Throws when the socket cannot take the frame. */
    public suspend fun send(text: String)

    /** Idempotent. */
    public suspend fun close()
}

/**
 * Opens the upgraded socket. [password] (may be empty) travels as `Sec-WebSocket-Protocol: paseo.bearer.<pw>` plus
 * `Authorization: Bearer <pw>`, only when non-empty (plan 04 §5.3). A 401 throws `Authentication`; a 403 throws
 * `Authentication` when a password was sent and `HostRefused` when none was; a bad 101 throws `InvalidResponse`;
 * anything else `Disconnected` — all as `PaseoException`.
 */
public fun interface PaseoSocketFactory {
    public suspend fun open(endpoint: PaseoEndpoint, password: String): PaseoSocket
}

/** A message the daemon pushed without being asked (Dart `PaseoEvent`). */
public class PaseoPush(public val epoch: Int, public val type: String, public val payload: JsonObject)

/** One binary terminal frame, tagged with its connection. */
public class PaseoBinaryFrame(public val epoch: Int, public val opcode: Int, public val slot: Int, public val payload: ByteArray)

/** Synchronous observers (Dart's `sync: true` broadcast streams): called on the transport's lane, in arrival order. */
public interface PaseoTransportListener {
    public fun onPush(push: PaseoPush) {}

    /** The socket of [epoch] was lost (never called after `close()`). */
    public fun onDisconnect(epoch: Int) {}

    public fun onBinary(frame: PaseoBinaryFrame) {}
}
