package io.github.eslamasabry.oc.core.net.sse

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Vectors ported from Flutter `test/api2_sse_test.dart` (B-PROTO-033) plus plan 04 §6.1 rules 1–7. */
class SseParserTest {
    private fun parse(text: String, chunk: Int = Int.MAX_VALUE, flush: Boolean = true, max: Int = SseParser.MAX_FRAME_BYTES):
        List<SseFrame> = parseBytes(text.encodeToByteArray(), chunk, flush, max)

    private fun parseBytes(bytes: ByteArray, chunk: Int, flush: Boolean, max: Int): List<SseFrame> {
        val frames = mutableListOf<SseFrame>()
        val parser = SseParser(max) { frames += it }
        var at = 0
        while (at < bytes.size) {
            val n = minOf(chunk, bytes.size - at)
            parser.feed(bytes, at, n)
            at += n
        }
        if (flush) parser.flush()
        return frames
    }

    @Test
    fun `B-PROTO-033 bare data frames and keepalive comments`() {
        val frames = parse(
            "data: {\"type\":\"server.connected\",\"data\":{}}\n\n" +
                ": heartbeat\n\n" +
                "data: {\"type\":\"session.deleted\"}\n\n",
        )
        assertEquals(
            listOf(
                SseFrame(null, null, "{\"type\":\"server.connected\",\"data\":{}}"),
                SseFrame(null, null, "{\"type\":\"session.deleted\"}"),
            ),
            frames,
        )
    }

    @Test
    fun `B-PROTO-033 event and id fields are kept`() {
        val frames = parse("event: session.text.delta\nid: evt_42\ndata: {\"delta\":\"pong\"}\n\n")
        assertEquals(listOf(SseFrame("session.text.delta", "evt_42", "{\"delta\":\"pong\"}")), frames)
    }

    @Test
    fun `B-PROTO-033 multi-line data joins with newlines`() {
        assertEquals("first\nsecond\nthird", parse("data: first\ndata: second\ndata:third\n\n").single().data)
    }

    @Test
    fun `B-PROTO-033 CRLF and mid-line chunk boundaries`() {
        for (size in listOf(1, 2, 3, 7, 64)) {
            val frames = parse("event: ping\r\ndata: {\"a\":1}\r\n\r\ndata: {\"b\":2}\r\n\r\n", chunk = size)
            assertEquals(listOf(SseFrame("ping", null, "{\"a\":1}"), SseFrame(null, null, "{\"b\":2}")), frames, "chunk $size")
        }
    }

    @Test
    fun `B-PROTO-033 multi-byte characters split across chunks decode whole`() {
        val text = "data: مرحبا 👋 ok\n\n"
        for (size in 1..5) assertEquals("مرحبا 👋 ok", parse(text, chunk = size).single().data, "chunk $size")
    }

    @Test
    fun `B-PROTO-033 malformed UTF-8 is replaced, never an error`() {
        val bytes = "data: a".encodeToByteArray() + byteArrayOf(0xC3.toByte(), 0x28) + "b\n\n".encodeToByteArray()
        val frame = parseBytes(bytes, Int.MAX_VALUE, true, SseParser.MAX_FRAME_BYTES).single()
        assertTrue(frame.data.startsWith("a") && frame.data.endsWith("(b"), frame.data)
    }

    @Test
    fun `B-PROTO-033 a lone CR is not a line end`() {
        assertEquals("a\rb", parse("data: a\rb\n\n").single().data)
    }

    @Test
    fun `B-PROTO-033 trailing frame only on flush`() {
        assertEquals(emptyList(), parse("data: {\"tail\":true}", flush = false))
        assertEquals("{\"tail\":true}", parse("data: {\"tail\":true}").single().data)
    }

    @Test
    fun `B-PROTO-033 a frame cut mid-write is discarded`() {
        val frames = mutableListOf<SseFrame>()
        val parser = SseParser { frames += it }
        parser.feed("data: whole\n\ndata: {\"cut\":".encodeToByteArray())
        parser.discardPartial() // the body ended with an IO error: no flush
        parser.feed("data: after\n\n".encodeToByteArray())
        assertEquals(listOf("whole", "after"), frames.map { it.data })
    }

    @Test
    fun `B-PROTO-033 oversized frames are dropped without harming the next`() {
        assertEquals(listOf("ok"), parse("data: ${"x".repeat(200)}\n\ndata: ok\n\n", max = 64).map { it.data })
        // Many data lines that are each small but together exceed the cap.
        val many = (1..20).joinToString("") { "data: ${"y".repeat(10)}\n" } + "\n" + "data: next\n\n"
        assertEquals(listOf("next"), parse(many, max = 64).map { it.data })
        // An oversized comment line also drops its frame, as in Flutter.
        assertEquals(listOf("ok"), parse("data: lost\n: ${"z".repeat(100)}\n\ndata: ok\n\n", max = 64).map { it.data })
        // Flutter fix: a line exactly one byte over the cap must not leave an "empty" residue that ends discard
        // mode early; the rest of that frame stays dropped.
        val overByOne = "data: ${"q".repeat(64 - 6 + 1)}\ndata: tail\n\ndata: ok\n\n"
        assertEquals(listOf("ok"), parse(overByOne, max = 64).map { it.data })
    }

    @Test
    fun `B-PROTO-033 empty data lines count toward the cap (review w1-core-net MAJOR 5)`() {
        // Each `data:` line adds one joining newline even when its value is empty; an endless run of them must hit the
        // cap instead of growing without bound at zero counted bytes.
        val empties = "data:\n".repeat(200) + "\n" + "data: ok\n\n"
        assertEquals(listOf("ok"), parse(empties, max = 64).map { it.data })
        // Exactly at the cap with the separators counted: 13 values of 4 bytes + 12 newlines = 64.
        val atCap = "data: aaaa\n".repeat(13) + "\n"
        assertEquals(64, parse(atCap, max = 64).single().data.length)
        // One more line: 56 value bytes + 13 newlines = 69 > 64, so the frame is dropped and the next one parses.
        val over = "data: aaaa\n".repeat(14) + "\n" + "data: next\n\n"
        assertEquals(listOf("next"), parse(over, max = 64).map { it.data })
    }

    @Test
    fun `B-PROTO-033 the 16 MiB cap holds at the real size`() {
        val big = "data: " + "a".repeat(SseParser.MAX_FRAME_BYTES + 1) + "\n\ndata: small\n\n"
        assertEquals(listOf("small"), parse(big, chunk = 16 * 1024).map { it.data })
        val atCap = "data: " + "b".repeat(SseParser.MAX_FRAME_BYTES - 6) + "\n\n"
        assertEquals(SseParser.MAX_FRAME_BYTES - 6, parse(atCap, chunk = 16 * 1024).single().data.length)
    }

    @Test
    fun `B-PROTO-033 fields without data are not delivered and unknown fields are ignored`() {
        assertEquals(emptyList(), parse("event: only\nid: 1\n\n"))
        assertEquals(emptyList(), parse("data:\n\n"))
        assertEquals(listOf(SseFrame(null, null, "x")), parse("retry: 10\nfoo: bar\nnocolon\ndata: x\n\n"))
        assertEquals("\n", parse("data:\ndata:\n\n").single().data)
        assertEquals(" two", parse("data:  two\n\n").single().data) // only one leading space is removed
    }

    @Test
    fun `B-PROTO-033 fuzz 10k cases, random chunk boundaries yield the frames of the model`() {
        val random = Random(20261101)
        repeat(10_000) { case ->
            val max = if (case % 5 == 0) 48 else SseParser.MAX_FRAME_BYTES
            val stream = FuzzStream.generate(random, max)
            val whole = parseBytes(stream.bytes, Int.MAX_VALUE, true, max)
            assertEquals(stream.expected, whole, "case $case whole")
            val cuts = parseRandomCuts(stream.bytes, random, max)
            assertEquals(stream.expected, cuts, "case $case cuts")
        }
    }

    private fun parseRandomCuts(bytes: ByteArray, random: Random, max: Int): List<SseFrame> {
        val frames = mutableListOf<SseFrame>()
        val parser = SseParser(max) { frames += it }
        var at = 0
        while (at < bytes.size) {
            val n = minOf(1 + random.nextInt(if (random.nextBoolean()) 4 else 40), bytes.size - at)
            parser.feed(bytes, at, n)
            at += n
        }
        parser.flush()
        return frames
    }
}

/** An independent model of the wire: builds bytes and the frames a correct parser must produce from them. */
private class FuzzStream(val bytes: ByteArray, val expected: List<SseFrame>) {
    companion object {
        private val alphabet = listOf("a", "b", "z", "0", " ", ":", "{", "\"", "é", "م", "ح", "👋", "\t", "\\")

        private fun value(random: Random, maxLen: Int): String =
            buildString { repeat(random.nextInt(maxLen + 1)) { append(alphabet[random.nextInt(alphabet.size)]) } }
                .trimStart(' ')

        fun generate(random: Random, max: Int): FuzzStream {
            val out = StringBuilder()
            val expected = mutableListOf<SseFrame>()
            repeat(random.nextInt(0, 6)) {
                val eol = if (random.nextInt(4) == 0) "\r\n" else "\n"
                val lines = mutableListOf<String>()
                var event: String? = null
                var id: String? = null
                val data = mutableListOf<String>()
                var dataBytes = 0
                var dropped = false
                val longest = if (max < 100) 30 else 12
                repeat(random.nextInt(0, 5)) {
                    when (random.nextInt(6)) {
                        0 -> value(random, longest).let { event = it; lines += "event: $it" }
                        1 -> value(random, 6).let { id = it; lines += "id:$it" }
                        2 -> lines += ": ${value(random, longest)}"
                        else -> value(random, longest).let { v ->
                            data += v
                            dataBytes += v.encodeToByteArray().size
                            lines += if (random.nextBoolean()) "data: $v" else "data:$v"
                        }
                    }
                }
                for (line in lines) {
                    // Flutter rule: a raw line (CR included) longer than the cap starts discard mode for the frame.
                    if ((line + eol.dropLast(1)).encodeToByteArray().size > max) dropped = true
                }
                // The joined data, separators included, is what the cap counts (review w1-core-net MAJOR 5).
                if (data.isNotEmpty() && dataBytes + data.size - 1 > max) dropped = true
                lines.forEach { out.append(it).append(eol) }
                out.append(eol)
                val joined = data.joinToString("\n")
                if (!dropped && data.isNotEmpty() && joined.isNotEmpty()) expected += SseFrame(event, id, joined)
            }
            return FuzzStream(out.toString().encodeToByteArray(), expected)
        }
    }
}
