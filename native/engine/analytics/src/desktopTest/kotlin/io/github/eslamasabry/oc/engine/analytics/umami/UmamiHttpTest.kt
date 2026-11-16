package io.github.eslamasabry.oc.engine.analytics.umami

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UmamiHttpTest {
    @Test
    fun `B-180 a server that accepts but never answers is cut off by the total timeout`() = runTest {
        val release = CountDownLatch(1)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val accepted = CountDownLatch(1)
            val thread = Thread {
                server.accept().use { socket ->
                    accepted.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }.apply { isDaemon = true; start() }
            try {
                val event = umamiEvent("app_open", mapOf("kind" to "cold"), umamiPages.getValue("conversations"),
                    "00000000-0000-4000-8000-000000000001", UmamiBuild("3.0.1", "10081"), UmamiDevice("en", "1080x2340", "browser"), 1000)!!
                val transport = UmamiHttp("http://localhost:${server.localPort}") { "Mozilla/5.0" }
                val start = System.nanoTime()
                val failed = transport.prepare(listOf(event)).execute() is Outcome.Failed
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                assertTrue(accepted.await(1, TimeUnit.SECONDS), "the test reached the unresponsive server")
                assertTrue(failed && elapsedMs >= 1000, "the unresponsive request must return a typed failure")
                assertTrue(elapsedMs < 6000, "a dead endpoint cannot hold the worker indefinitely: $elapsedMs")
                assertTrue(UmamiHttp.CALL_TIMEOUT_MS <= 3000 && UmamiHttp.CONNECT_TIMEOUT_MS <= 1500)
            } finally {
                release.countDown()
                thread.join(1000)
            }
        }
    }
}
