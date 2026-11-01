package io.github.eslamasabry.oc.android.camera.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FakeClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now(): Instant = now

    fun advance(by: Duration) {
        now += by
    }
}

/** Stands in for the pairing parser owned by plan 04 (B-PROTO-064…066): only JSON objects with "urls" are codes. */
private class Pairing(val urls: String) {
    override fun toString() = "Pairing(<redacted>)"
}

private fun parsePairing(text: String): Pairing? =
    if (text.trimStart().startsWith("{") && "\"urls\"" in text) Pairing(text.substringAfter("urls")) else null

class ScanSessionTest {
    private val clock = FakeClock()
    private val events = mutableListOf<String>()
    private val decoder = ZxingCoreDecoder()
    private val latched = mutableListOf<Pairing>()
    private var rejections = 0

    private fun session(parse: (String) -> Pairing? = ::parsePairing) =
        ScanSession(
            decoder = decoder,
            parse = parse,
            clock = clock,
            releaseCamera = { events += "release" },
            onLatched = { events += "latched"; latched += it },
            onRejected = { rejections++ },
        )

    private fun window(name: String): LumaFrame {
        val f = QrFixtures.frame(name)
        return f.withCrop(ScanWindow.cropFor(f.width, f.height, 1f))
    }

    @Test
    fun `B-PROTO-069 a pairing QR latches once, the camera is released before the host hears of it`() {
        val s = session()
        val attempt = s.begin()
        assertEquals(FrameVerdict.Accepted, s.analyze(attempt, window("pairing.png")))
        assertEquals(listOf("release", "latched"), events)
        assertEquals(1, latched.size)
        assertTrue(s.latched)
    }

    @Test
    fun `queued frames after the latch are ignored and never decoded`() {
        val s = session()
        val attempt = s.begin()
        s.analyze(attempt, window("pairing.png"))
        val decodes = decoder.calls
        assertEquals(FrameVerdict.Ignored, s.analyze(attempt, window("pairing-two-addresses.png")))
        assertEquals(FrameVerdict.Ignored, s.analyze(attempt, window("pairing.png")))
        assertEquals(decodes, decoder.calls)
        assertEquals(1, latched.size)
        assertEquals(listOf("release", "latched"), events)
    }

    @Test
    fun `B-PROTO-069 a QR that is not a pairing code is rejected and scanning goes on`() {
        val s = session()
        val attempt = s.begin()
        assertEquals(FrameVerdict.Rejected, s.analyze(attempt, window("url.png")))
        assertEquals(1, rejections)
        assertFalse(s.latched)
        assertEquals(FrameVerdict.Accepted, s.analyze(attempt, window("pairing.png")))
    }

    @Test
    fun `a rejected code is reported once per sighting, again only after 2 s out of view`() {
        val s = session()
        val attempt = s.begin()
        assertEquals(FrameVerdict.Rejected, s.analyze(attempt, window("wifi.png")))
        repeat(10) {
            clock.advance(300.milliseconds) // still in view: each frame refreshes the sighting
            assertEquals(FrameVerdict.RejectedQuietly, s.analyze(attempt, window("wifi.png")))
        }
        // A different wrong code is its own sighting.
        assertEquals(FrameVerdict.Rejected, s.analyze(attempt, window("url.png")))
        clock.advance(1999.milliseconds)
        assertEquals(FrameVerdict.RejectedQuietly, s.analyze(attempt, window("wifi.png")))
        clock.advance(2.seconds)
        assertEquals(FrameVerdict.Rejected, s.analyze(attempt, window("wifi.png")))
        assertEquals(3, rejections)
    }

    @Test
    fun `a frame without a code is just no code`() {
        val s = session()
        assertEquals(FrameVerdict.NoCode, s.analyze(s.begin(), window("blank-640x480.png")))
        assertEquals(0, rejections)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a late frame from a paused or stopped start is ignored without decoding`() {
        val s = session()
        val first = s.begin()
        s.end() // ON_PAUSE
        val decodes = decoder.calls
        assertEquals(FrameVerdict.Ignored, s.analyze(first, window("pairing.png")))
        assertEquals(decodes, decoder.calls)
        assertFalse(s.isCurrent(first))
        val second = s.begin() // ON_RESUME
        assertTrue(s.isCurrent(second))
        assertEquals(FrameVerdict.Accepted, s.analyze(second, window("pairing.png")))
        assertEquals(FrameVerdict.Ignored, s.analyze(first, window("pairing.png")))
    }

    @Test
    fun `after the latch a new start stays latched so nothing scans twice`() {
        val s = session()
        s.analyze(s.begin(), window("pairing.png"))
        val again = s.begin()
        assertEquals(FrameVerdict.Ignored, s.analyze(again, window("pairing.png")))
        assertEquals(1, latched.size)
    }

    @Test
    fun `a parser that throws counts as a rejection, never a crash on the camera thread`() {
        val s = session(parse = { error("boom") })
        assertEquals(FrameVerdict.Rejected, s.analyze(s.begin(), window("pairing.png")))
        assertFalse(s.latched)
    }

    @Test
    fun `the host is told even if releasing the camera fails`() {
        val s =
            ScanSession(
                decoder = decoder, parse = ::parsePairing, clock = clock,
                releaseCamera = { error("unbind failed") }, onLatched = { latched += it },
            )
        assertEquals(FrameVerdict.Accepted, s.analyze(s.begin(), window("pairing.png")))
        assertEquals(1, latched.size)
    }

    @Test
    fun `B-SEC-107 the session never keeps or prints the decoded text`() {
        val s = session()
        val attempt = s.begin()
        s.analyze(attempt, window("wifi.png"))
        s.analyze(attempt, window("url.png"))
        s.analyze(attempt, window("pairing.png"))
        val secrets = listOf("hunter2", "example.com", "fixture-not-a-live")
        val strings = reachableStrings(s)
        secrets.forEach { secret ->
            assertTrue(strings.none { secret in it }, "the session holds decoded text")
            assertFalse(secret in s.toString())
        }
        FrameVerdict.entries.forEach { v -> secrets.forEach { assertFalse(it in v.toString()) } }
    }
}

/** Every String reachable from [root] through fields, arrays, collections and maps (production objects only). */
internal fun reachableStrings(root: Any): List<String> {
    val seen = java.util.IdentityHashMap<Any, Unit>()
    val out = mutableListOf<String>()
    val mainCode = ScanSession::class.java.protectionDomain?.codeSource?.location
    fun walk(o: Any?, depth: Int) {
        if (o == null || depth > 6 || seen.put(o, Unit) != null) return
        when (o) {
            is String -> out += o
            is CharSequence -> out += o.toString()
            is ByteArray -> out += String(o, Charsets.UTF_8)
            is Array<*> -> o.forEach { walk(it, depth + 1) }
            is Iterable<*> -> o.forEach { walk(it, depth + 1) }
            is Map<*, *> -> o.forEach { (k, v) -> walk(k, depth + 1); walk(v, depth + 1) }
            else -> {
                // Walk only production classes (same code source as ScanSession), not test lambdas and fixtures.
                if (o.javaClass.protectionDomain?.codeSource?.location != mainCode) return
                var c: Class<*>? = o.javaClass
                while (c != null && c != Any::class.java) {
                    c.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach { f ->
                        f.isAccessible = true
                        walk(f.get(o), depth + 1)
                    }
                    c = c.superclass
                }
            }
        }
    }
    walk(root, 0)
    return out
}
