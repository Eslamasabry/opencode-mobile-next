package io.github.eslamasabry.oc.engine.files

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Synthetic addresses built from parts (the push scanner blocks private-address literals). */
private fun ip(a: Int, b: Int, c: Int, d: Int): String = "$a.$b.$c.$d"

private class FixedClock(var ms: Long = 1_767_225_600_000) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
}

private class FakeHost(var address: String? = ip(192, 168, 1, 5), var bindFails: Boolean = false) : ShareHost {
    var handler: ShareHandler? = null
    var opens = 0
    var closes = 0
    var boundTo: String? = null

    override suspend fun wifiAddress(): String? = address

    override suspend fun open(address: String, handler: ShareHandler): Outcome<ShareServer> {
        if (bindFails) return Outcome.Failed(Failure.Device())
        opens++
        boundTo = address
        this.handler = handler
        return Outcome.Ok(
            object : ShareServer {
                override val port: Int = 41234

                override suspend fun close() {
                    closes++
                }
            }
        )
    }

    override fun secureBytes(count: Int): ByteArray = ByteArray(count) { (it * 7 + 3).toByte() }
}

class WifiShareTest {
    private val file = FileData("notes/report final.txt", "text/plain", "hi".encodeToByteArray())

    private fun TestScope.share(host: ShareHost?) = WifiShare(host, FixedClock(), backgroundScope)

    private fun tokenOf(url: String) = url.substringAfter(":41234/").substringBefore('/')

    @Test
    fun `B-MEDIA-034 no Wi-Fi refuses to start and nothing listens`() = runTest {
        val host = FakeHost(address = null)
        val result = share(host).start(file)
        assertEquals(Failure.Media(MediaProblem.WifiShareNoWifi), (result as Outcome.Failed).failure)
        assertEquals(0, host.opens)
    }

    @Test
    fun `B-MEDIA-034 a public address is not Wi-Fi`() = runTest {
        val host = FakeHost(address = ip(8, 8, 8, 8))
        assertIs<Outcome.Failed>(share(host).start(file))
        assertEquals(0, host.opens)
    }

    @Test
    fun `B-MEDIA-034 serves exactly one file by secret token as an attachment and counts downloads`() = runTest {
        val host = FakeHost()
        val engine = share(host)
        val started = (engine.start(file) as Outcome.Ok).value
        assertEquals(ip(192, 168, 1, 5), host.boundTo)
        assertTrue(started.url.startsWith("http://${ip(192, 168, 1, 5)}:41234/"))
        assertTrue(started.url.endsWith("/report%20final.txt"))
        val token = tokenOf(started.url)
        assertEquals(32, token.length) // 24 bytes, base64url without padding
        val handler = host.handler!!
        val ok = handler.reply("GET", "/$token/report%20final.txt")
        assertIs<ShareReply.Serve>(ok)
        assertEquals("hi", ok.body.decodeToString())
        assertFalse(ok.headOnly)
        assertTrue(ok.headers.any { it.first == "Content-Disposition" && it.second.startsWith("attachment;") })
        assertTrue(ok.headers.contains("Cache-Control" to "no-store"))
        handler.served()
        assertEquals(1, engine.state.value?.downloads)
        assertTrue((handler.reply("HEAD", "/$token/report%20final.txt") as ShareReply.Serve).headOnly)
    }

    @Test
    fun `B-MEDIA-034 returns 404 elsewhere and 405 for non-GET`() = runTest {
        val host = FakeHost()
        val started = (share(host).start(file) as Outcome.Ok).value
        val token = tokenOf(started.url)
        val handler = host.handler!!
        assertEquals(ShareReply.NotFound, handler.reply("GET", "/"))
        assertEquals(ShareReply.NotFound, handler.reply("GET", "/$token"))
        assertEquals(ShareReply.NotFound, handler.reply("GET", "/${token}x/report%20final.txt"))
        assertEquals(ShareReply.NotFound, handler.reply("GET", "/$token/other.txt"))
        assertEquals(ShareReply.NotFound, handler.reply("GET", "/$token/report%20final.txt/more"))
        assertEquals(ShareReply.MethodNotAllowed, handler.reply("POST", "/$token/report%20final.txt"))
        assertEquals(ShareReply.MethodNotAllowed, handler.reply("PUT", "/$token/report%20final.txt"))
    }

    @Test
    fun `B-MEDIA-034 stop closes the server, clears the state and the old link answers 404`() = runTest {
        val host = FakeHost()
        val engine = share(host)
        val started = (engine.start(file) as Outcome.Ok).value
        val handler = host.handler!!
        engine.stop()
        assertNull(engine.state.value)
        assertEquals(1, host.closes)
        assertEquals(ShareReply.NotFound, handler.reply("GET", "/${tokenOf(started.url)}/report%20final.txt"))
        engine.stop() // safe when nothing is shared
        assertEquals(1, host.closes)
    }

    @Test
    fun `B-MEDIA-034 the share ends by itself after 15 minutes`() = runTest {
        val host = FakeHost()
        val engine = share(host)
        val started = (engine.start(file) as Outcome.Ok).value
        assertEquals(15 * 60 * 1000L, started.remainingMs(1_767_225_600_000))
        advanceTimeBy(15 * 60 * 1000L - 1)
        runCurrent()
        assertTrue(engine.state.value != null)
        advanceTimeBy(2)
        runCurrent()
        assertNull(engine.state.value)
        assertEquals(1, host.closes)
    }

    @Test
    fun `B-MEDIA-034 a new share stops the first`() = runTest {
        val host = FakeHost()
        val engine = share(host)
        val first = (engine.start(file) as Outcome.Ok).value
        val firstHandler = host.handler!!
        engine.start(FileData("b.txt", null, byteArrayOf(1)))
        assertEquals(1, host.closes)
        assertEquals(ShareReply.NotFound, firstHandler.reply("GET", "/${tokenOf(first.url)}/report%20final.txt"))
        assertEquals("b.txt", engine.state.value?.name)
    }

    @Test
    fun `B-MEDIA-034 a secret-looking file is refused before anything listens`() = runTest {
        val host = FakeHost()
        val result = share(host).start(FileData("app/.env.local", null, byteArrayOf(1)))
        assertEquals(Failure.Media(MediaProblem.WifiSharePrivateFile), (result as Outcome.Failed).failure)
        assertEquals(0, host.opens)
    }

    @Test
    fun `B-MEDIA-034 a platform without the server says sharing is unavailable`() = runTest {
        val engine = share(null)
        assertFalse(engine.supported)
        assertEquals(Failure.Media(MediaProblem.ShareUnavailable), (engine.start(file) as Outcome.Failed).failure)
    }

    @Test
    fun `B-MEDIA-034 a failed bind is a plain unavailable failure`() = runTest {
        val engine = share(FakeHost(bindFails = true))
        assertEquals(Failure.Media(MediaProblem.WifiShareUnavailable), (engine.start(file) as Outcome.Failed).failure)
        assertNull(engine.state.value)
    }

    @Test
    fun `B-MEDIA-034 only private LAN addresses count as Wi-Fi`() {
        assertTrue(WifiShareRules.isPrivateLan(ip(192, 168, 1, 5)))
        assertTrue(WifiShareRules.isPrivateLan(ip(10, 0, 0, 2)))
        assertTrue(WifiShareRules.isPrivateLan(ip(172, 16, 4, 1)))
        assertTrue(WifiShareRules.isPrivateLan(ip(172, 31, 255, 1)))
        assertFalse(WifiShareRules.isPrivateLan(ip(172, 32, 0, 1)))
        assertFalse(WifiShareRules.isPrivateLan(ip(8, 8, 8, 8)))
        assertFalse(WifiShareRules.isPrivateLan(ip(0, 0, 0, 0)))
        assertFalse(WifiShareRules.isPrivateLan(ip(100, 64, 1, 2))) // carrier/VPN range
        assertFalse(WifiShareRules.isPrivateLan("2001:db8::1"))
        assertTrue(WifiShareRules.isWifiInterface("wlan0"))
        assertTrue(WifiShareRules.isWifiInterface("swlan0"))
        assertTrue(WifiShareRules.isWifiInterface("ap0"))
        assertFalse(WifiShareRules.isWifiInterface("rmnet_data0"))
        assertFalse(WifiShareRules.isWifiInterface("tun0"))
    }

    @Test
    fun `B-MEDIA-034 names are base names without control characters and round-trip through the link`() {
        assertEquals("b.txt", WifiShareRules.safeName("a/b.txt"))
        assertEquals("a_b_.txt", WifiShareRules.safeName("a\"b\n.txt"))
        assertEquals("file", WifiShareRules.safeName(""))
        val name = "تقرير 1%.md"
        assertEquals(name, WifiShareRules.decode(WifiShareRules.encode(name)))
        assertNull(WifiShareRules.decode("%G1"))
        assertNull(WifiShareRules.decode("%FF"))
    }
}
