package io.github.eslamasabry.opencode_mobile

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class LoopbackPortTest {
    private fun listener(port: Int = 0) =
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress("127.0.0.1", port))
        }

    @Test
    fun `B-239 the port of a server that just stopped is free, even though a connection to it was open`() {
        val server = listener()
        val port = server.localPort
        val client = Socket("127.0.0.1", port)
        val served = server.accept()
        // The server goes first, as when the app stops it: its side of the connection lingers in the system.
        served.close()
        server.close()
        client.close()
        assertTrue("the phone server would move to another port on every restart", LoopbackPort.free(port))
    }

    @Test
    fun `B-239 a port another program is listening on is never taken`() {
        listener().use { other -> assertFalse(LoopbackPort.free(other.localPort)) }
    }
}
