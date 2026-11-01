package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.data.dart.DartJson
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Vectors derived from dart:convert `_JsonStringifier`, dart:core `Uri._uriEncode`, `DateTime.toIso8601String` and
 * `double.toString`. TODO(move-to-core): replace with the generated `dart-vectors.json` (plan 05 §2.5) in :core:common.
 */
class DartJsonTest {
    @Test
    fun `jsonEncode escapes like dart convert`() {
        assertEquals("[\"a\\\"b\\\\c\\n\\t\\b\\f\\r\\u0001\\u001f/é😀\"]", DartJson.encode(listOf("a\"b\\c\n\t\b\u000C\r\u0001\u001f/é😀")))
        assertEquals("\"\\ud800x\\udc00\"", DartJson.encode("\uD800x\uDC00"), "lone surrogates as lowercase \\u")
        assertEquals("{\"b\":1,\"a\":[true,null,1.5]}", DartJson.encode(linkedMapOf("b" to 1L, "a" to listOf(true, null, 1.5))))
        assertEquals("[]", DartJson.encode(emptyList<String>()))
    }

    @Test
    fun `doubles print like Dart double toString`() {
        assertEquals("1.0", DartJson.dartDouble(1.0))
        assertEquals("100.0", DartJson.dartDouble(100.0))
        assertEquals("0.5", DartJson.dartDouble(0.5))
        assertEquals("-2.25", DartJson.dartDouble(-2.25))
        assertEquals("0.000001", DartJson.dartDouble(0.000001))
        assertEquals("1e-7", DartJson.dartDouble(1e-7))
        assertEquals("1.5e-7", DartJson.dartDouble(1.5e-7))
        assertEquals("100000000000000000000.0", DartJson.dartDouble(1e20))
        assertEquals("1e+21", DartJson.dartDouble(1e21))
        assertEquals("1.2345e+22", DartJson.dartDouble(1.2345e22))
        assertEquals("-0.0", DartJson.dartDouble(-0.0))
    }

    @Test
    fun `decode keeps order and number kinds and refuses junk`() {
        val v = DartJson.decode("{\"z\":1,\"a\":[1.0,\"x\",false,null]}") as Map<*, *>
        assertEquals(listOf("z", "a"), v.keys.toList())
        assertEquals(1L, v["z"])
        assertEquals(listOf(1.0, "x", false, null), v["a"])
        assertNull(DartJson.decodeOrNull("[1,]"))
        assertNull(DartJson.decodeOrNull("{} x"))
    }

    @Test
    fun `encodeComponent keeps Dart's unreserved set and uppercases hex`() {
        assertEquals("a%20b%2F%C3%BC!~*'()-_.", DartJson.encodeComponent("a b/ü!~*'()-_."))
        assertEquals("%3Cdefault%3E%0A%3Cdefault%3E", DartJson.encodeComponent("<default>\n<default>"))
        assertEquals("%EF%BF%BD", DartJson.encodeComponent("\uD800"), "a lone surrogate encodes as U+FFFD")
    }

    @Test
    fun `iso8601 matches DateTime toIso8601String in UTC`() {
        assertEquals("1970-01-01T00:00:00.000Z", DartJson.iso8601Utc(0))
        assertEquals("2024-01-02T03:04:05.006Z", DartJson.iso8601Utc(1_704_164_645_006_000))
        assertEquals("2024-01-02T03:04:05.006007Z", DartJson.iso8601Utc(1_704_164_645_006_007))
        assertEquals("1969-12-31T23:59:59.999999Z", DartJson.iso8601Utc(-1))
        assertEquals("2000-02-29T00:00:00.000Z", DartJson.iso8601Utc(951_782_400_000_000))
    }

    @Test
    fun `sha256Hex matches the FIPS vectors over UTF-8`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", DartJson.sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DartJson.sha256Hex("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            DartJson.sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        assertContentEquals(byteArrayOf(0xD9.toByte(), 0x85.toByte()), DartJson.utf8("م"))
    }
}
