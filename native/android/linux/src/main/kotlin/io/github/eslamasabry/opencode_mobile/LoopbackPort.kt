package io.github.eslamasabry.opencode_mobile

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Whether a loopback port can take the phone server now.
 *
 * B-239: the check binds the way a server does (address reuse on). Without it Linux refuses the port for about a
 * minute after a server that had an open connection stopped, so every restart (an update, Restart server) moved the
 * phone server to the next port and the app kept asking the old one. A port another program is listening on is
 * still refused: address reuse never takes over a live listener.
 */
internal object LoopbackPort {
    fun free(port: Int): Boolean =
        try {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("127.0.0.1", port))
            }
            true
        } catch (_: IOException) {
            false
        }
}
