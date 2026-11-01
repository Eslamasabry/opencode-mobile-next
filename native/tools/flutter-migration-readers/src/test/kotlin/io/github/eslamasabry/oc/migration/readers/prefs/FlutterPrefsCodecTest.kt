package io.github.eslamasabry.oc.migration.readers.prefs

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlutterPrefsCodecTest {

    private fun d(raw: RawPref) = FlutterPrefsCodec.decode(raw)

    @Test
    fun `prefix constants are the Base64 sentences the plugin uses`() {
        fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray()).trimEnd('=')
        assertEquals(b64("This is the prefix for a list."), FlutterPrefsCodec.LIST_PREFIX)
        assertEquals(b64("This is the prefix for Double."), FlutterPrefsCodec.DOUBLE_PREFIX)
        assertEquals(b64("This is the prefix for BigInteger"), FlutterPrefsCodec.BIG_INTEGER_PREFIX)
        assertEquals("flutter.oc.profiles", FlutterPrefsCodec.physicalKey("oc.profiles"))
        assertEquals("oc.profiles", FlutterPrefsCodec.logicalKey("flutter.oc.profiles"))
        assertNull(FlutterPrefsCodec.logicalKey("oc.profiles"))
    }

    @Test
    fun `native types map to what Dart sees`() {
        assertEquals(PrefValue.Bool(true), d(RawPref.Bool(true)).value)
        assertEquals(PrefValue.Int64(5), d(RawPref.Int64(5)).value)
        assertEquals(StoredKind.Int, d(RawPref.Int32(5)).kind)
        assertEquals(PrefValue.Int64(5), d(RawPref.Int32(5)).value)
        assertEquals(PrefValue.Dbl(1.5), d(RawPref.F32(1.5f)).value)
        assertEquals(PrefValue.Dbl(2.5), d(RawPref.F64(2.5)).value)
        assertEquals(PrefValue.StrList(listOf("a")), d(RawPref.StrSet(setOf("a"))).value)
        assertNull(d(RawPref.Null).value)
        assertTrue(d(RawPref.Null).invisibleToDart)
        assertEquals(PrefValue.Undecodable(UndecodableKind.UnknownType), d(RawPref.Other("int-array", "<int-array/>")).value)
    }

    @Test
    fun `JSON lists as written since shared_preferences_android 2_4_3`() {
        val enc = FlutterPrefsCodec.encodeStringList(listOf("a", "ب", "\"q\"", "line\nbreak", ""))
        assertEquals(FlutterPrefsCodec.JSON_LIST_PREFIX + """["a","ب","\"q\"","line\nbreak",""]""", enc.value)
        val dec = d(enc)
        assertEquals(StoredKind.JsonList, dec.kind)
        assertEquals(PrefValue.StrList(listOf("a", "ب", "\"q\"", "line\nbreak", "")), dec.value)
        assertEquals(PrefValue.StrList(emptyList()), d(RawPref.Str(FlutterPrefsCodec.JSON_LIST_PREFIX + "[]")).value)
    }

    @Test
    fun `broken JSON lists are undecodable and never carry the value`() {
        for (bad in listOf("[1,2]", "{\"a\":1}", "[\"a\"", "not json", "[\"a\",null]")) {
            val v = d(RawPref.Str(FlutterPrefsCodec.JSON_LIST_PREFIX + bad)).value
            assertEquals(PrefValue.Undecodable(UndecodableKind.BadJsonList), v, bad)
            assertFalse(v.toString().contains(bad))
        }
    }

    @Test
    fun `legacy Java-serialized lists decode for ArrayList and Arrays_asList`() {
        val a = d(RawPref.Str(FixtureGenerator.javaSerializedList(listOf("x", "ي", ""))))
        assertEquals(StoredKind.JavaList, a.kind)
        assertEquals(PrefValue.StrList(listOf("x", "ي", "")), a.value)
        val b = d(RawPref.Str(FixtureGenerator.javaSerializedList(listOf("p", "q"), arraysAsList = true)))
        assertEquals(PrefValue.StrList(listOf("p", "q")), b.value)
    }

    @Test
    fun `Java lists with classes outside the plugin allowlist are refused without instantiating them`() {
        fun serialized(o: Any): String {
            val bos = ByteArrayOutputStream()
            ObjectOutputStream(bos).use { it.writeObject(o) }
            return FlutterPrefsCodec.LIST_PREFIX + FlutterPrefsCodec.encodeAndroidBase64Default(bos.toByteArray())
        }
        val bad = UndecodableKind.BadJavaList
        val inputs = listOf(
            serialized(java.util.LinkedList(listOf("a"))),
            serialized(java.util.Date(0)),
            serialized(arrayListOf<Any>(1, 2)),
            FlutterPrefsCodec.LIST_PREFIX + "%%%not-base64",
            FlutterPrefsCodec.LIST_PREFIX + "AAAA",
        )
        for (s in inputs) assertEquals(PrefValue.Undecodable(bad), d(RawPref.Str(s)).value)
    }

    @Test
    fun `doubles use the double prefix and Java toString`() {
        for (v in listOf(0.5, -0.0, 1.0E-7, 1.0E21, 123456789.125, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY)) {
            val enc = FlutterPrefsCodec.encodeDouble(v)
            assertEquals(FlutterPrefsCodec.DOUBLE_PREFIX + v.toString(), enc.value)
            val back = d(enc)
            assertEquals(StoredKind.PrefixedDouble, back.kind)
            assertEquals(PrefValue.Dbl(v), back.value)
        }
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadDouble), d(RawPref.Str(FlutterPrefsCodec.DOUBLE_PREFIX + "x1")).value)
    }

    @Test
    fun `BigInteger prefix decodes base 36 to a Dart int, overflow is undecodable`() {
        assertEquals(PrefValue.Int64(1295), d(RawPref.Str(FlutterPrefsCodec.BIG_INTEGER_PREFIX + "zz")).value)
        assertEquals(StoredKind.BigInteger, d(RawPref.Str(FlutterPrefsCodec.BIG_INTEGER_PREFIX + "zz")).kind)
        val huge = java.math.BigInteger.ONE.shiftLeft(80).toString(36)
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadBigInteger), d(RawPref.Str(FlutterPrefsCodec.BIG_INTEGER_PREFIX + huge)).value)
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadBigInteger), d(RawPref.Str(FlutterPrefsCodec.BIG_INTEGER_PREFIX + "!")).value)
    }

    @Test
    fun `plain strings stay strings, and writing a string that clashes with a prefix is refused`() {
        assertEquals(PrefValue.Str("VGhpcy"), d(RawPref.Str("VGhpcy")).value)
        for (p in listOf(FlutterPrefsCodec.LIST_PREFIX, FlutterPrefsCodec.DOUBLE_PREFIX, FlutterPrefsCodec.BIG_INTEGER_PREFIX)) {
            assertFailsWith<FlutterPrefsCodec.ClashException> { FlutterPrefsCodec.encodeString(p + "x") }
        }
    }

    @Test
    fun `round trip of every writable value type`() {
        val values = listOf(
            PrefValue.Bool(false), PrefValue.Int64(Long.MAX_VALUE), PrefValue.Dbl(3.25),
            PrefValue.Str("text"), PrefValue.StrList(listOf("a", "b")),
        )
        for (v in values) assertEquals(v, d(FlutterPrefsCodec.encode(v)).value)
        assertFailsWith<IllegalArgumentException> { FlutterPrefsCodec.encode(PrefValue.Undecodable(UndecodableKind.BadDouble)) }
    }

    @Test
    fun `Android Base64 DEFAULT wraps at 76 characters and ends with a newline`() {
        val bytes = ByteArray(100) { it.toByte() }
        val enc = FlutterPrefsCodec.encodeAndroidBase64Default(bytes)
        val lines = enc.split("\n")
        assertEquals(76, lines[0].length)
        assertEquals("", lines.last())
        assertTrue(FlutterPrefsCodec.decodeAndroidBase64(enc).contentEquals(bytes))
        assertTrue(FlutterPrefsCodec.decodeAndroidBase64(enc.replace("\n", "\r\n")).contentEquals(bytes))
    }

    @Test
    fun `Android Base64 decode semantics - junk skipped, padding optional, misplaced padding refused`() {
        fun dec(s: String) = String(FlutterPrefsCodec.decodeAndroidBase64(s), Charsets.ISO_8859_1)
        assertEquals("hi", dec("aGk="))
        assertEquals("hi", dec("aGk"), "missing padding is accepted")
        assertEquals("hi", dec("a G\n*k=@"), "every non-alphabet byte is skipped")
        assertEquals("", dec(""))
        for (bad in listOf("a", "aG=k", "=", "aGk=x", "aG=")) {
            assertFailsWith<IllegalArgumentException>(bad) { FlutterPrefsCodec.decodeAndroidBase64(bad) }
        }
        // Agrees with java.util.Base64 on every well-formed input length.
        for (n in 0..40) {
            val b = ByteArray(n) { (it * 37).toByte() }
            assertTrue(FlutterPrefsCodec.decodeAndroidBase64(Base64.getEncoder().encodeToString(b)).contentEquals(b), "n=$n")
        }
    }
}
