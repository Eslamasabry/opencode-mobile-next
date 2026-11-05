package io.github.eslamasabry.opencode_mobile

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SetupAppFetchTest {
    private lateinit var rootfs: File
    private lateinit var server: HttpServer
    private val payload = ByteArray(300_000) { ((it * 31 + 7) % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private val ranges = mutableListOf<String?>()

    @Before fun setUp() {
        rootfs = Files.createTempDirectory("oc-rootfs").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/blob") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            ranges.add(range)
            val start = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
            if (range != null) {
                exchange.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                exchange.sendResponseHeaders(206, (payload.size - start).toLong())
            } else exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload, start, payload.size - start) }
        }
        server.start()
    }

    @After fun tearDown() { server.stop(0); rootfs.deleteRecursively() }

    private fun request(sha: String = this.sha, dest: String = "/var/cache/oc-setup/node.tgz") =
        SetupProtocol.Event.Fetch("http://127.0.0.1:${server.address.port}/blob", dest, sha)

    private fun fetch() = SetupAppFetch(rootfs, allowInsecure = true)
    private fun doneFile(dest: String = "/var/cache/oc-setup/node.tgz") = File(rootfs, dest.removePrefix("/") + ".oc-fetch-done")

    @Test fun parsesTheFetchLine() {
        assertEquals(
            SetupProtocol.Event.Fetch("https://x.test/a", "/var/cache/a", sha),
            SetupProtocol.parse("::oc fetch https://x.test/a /var/cache/a $sha"),
        )
        assertNull(SetupProtocol.parse("::oc fetch https://x.test/a"))
    }

    @Test fun downloadsIntoTheGuestPathAndAnswersOk() {
        val seen = mutableListOf<Long>()
        fetch().run(request(), { false }) { d, _ -> seen.add(d) }
        val target = File(rootfs, "var/cache/oc-setup/node.tgz")
        assertTrue(target.readBytes().contentEquals(payload))
        assertEquals("ok", doneFile().readText())
        assertTrue(File(target.path + ".oc-fetch-ack").exists())
        assertEquals(payload.size.toLong(), seen.last())
    }

    @Test fun resumesAPartialFileWithARangeRequest() {
        val target = File(rootfs, "var/cache/oc-setup/node.tgz")
        target.parentFile!!.mkdirs()
        target.writeBytes(payload.copyOf(100_000))
        fetch().run(request(), { false }) { _, _ -> }
        assertEquals(listOf<String?>("bytes=100000-"), ranges)
        assertEquals("ok", doneFile().readText())
        assertTrue(target.readBytes().contentEquals(payload))
    }

    @Test fun aWrongChecksumFailsAndDeletesTheFile() {
        fetch().run(request(sha = "0".repeat(64)), { false }) { _, _ -> }
        assertTrue(doneFile().readText().startsWith("fail "))
        assertFalse(File(rootfs, "var/cache/oc-setup/node.tgz").exists())
    }

    @Test fun refusesInsecureAddressesAndPathsOutsideTheRootfs() {
        val plain = SetupProtocol.Event.Fetch("http://127.0.0.1:${server.address.port}/blob", "/tmp/x", sha)
        SetupAppFetch(rootfs).run(plain, { false }) { _, _ -> }
        assertTrue(File(rootfs, "tmp/x.oc-fetch-done").readText().startsWith("fail "))
        assertNull(fetch().resolve("/var/../../etc/passwd"))
        assertNull(fetch().resolve("relative/path"))
    }

    @Test fun cancellingStopsWithAFailureAnswer() {
        fetch().run(request(), { true }) { _, _ -> }
        assertEquals("fail cancelled", doneFile().readText())
    }
}
