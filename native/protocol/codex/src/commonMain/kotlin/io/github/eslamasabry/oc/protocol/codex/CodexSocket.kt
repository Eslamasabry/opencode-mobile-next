package io.github.eslamasabry.oc.protocol.codex

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Text-only JSON-RPC socket. Production upgrades disable redirects before attaching the bearer
 * token.
 */
public interface CodexSocket {
    public val incoming: ReceiveChannel<String>

    public suspend fun send(text: String)

    public suspend fun close()
}

/** Endpoints are validated by the protocol before tokens reach this boundary. */
public fun interface CodexSocketFactory {
    public suspend fun open(endpoint: String, token: String): CodexSocket
}
