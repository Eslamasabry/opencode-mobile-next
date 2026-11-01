package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec.Companion.BIG_INTEGER_PREFIX
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec.Companion.DOUBLE_PREFIX
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec.Companion.JSON_LIST_PREFIX
import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec.Companion.LIST_PREFIX
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.data.kv.PrefixClash
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.kv.StoredKind
import io.github.eslamasabry.oc.core.data.kv.UndecodableKind
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlutterPrefsCodecTest {
    private val codec = testCodec

    private fun value(raw: RawPref) = codec.decode(raw).value

    @Test
    fun `B-MIGR-005 every stored type decodes to what Dart sees`() {
        assertEquals(PrefValue.Bool(true), value(RawPref.Bool(true)))
        assertEquals(PrefValue.Int64(1L shl 40), value(RawPref.Int64(1L shl 40)), "Dart ints are Longs")
        assertEquals(PrefValue.Int64(7), value(RawPref.Int32(7)), "a Kotlin putInt reads as a Dart int")
        assertEquals(PrefValue.Dbl(1.5), value(RawPref.F32(1.5f)))
        assertEquals(PrefValue.Dbl(2.5), value(RawPref.F64(2.5)))
        assertEquals(PrefValue.StrList(listOf("a", "b")), value(RawPref.StrSet(linkedSetOf("a", "b"))))
        assertEquals(PrefValue.Str("plain"), value(RawPref.Str("plain")))
        assertNull(value(RawPref.Null), "<null> is invisible to Dart")
        assertEquals(PrefValue.Undecodable(UndecodableKind.UnknownType), value(RawPref.Other("int-array", "<int-array/>")))
    }

    @Test
    fun `B-MIGR-005 the JSON list encoding decodes, and a bad one is unreadable without its value`() {
        assertEquals(PrefValue.StrList(listOf("x", "مرحبا", "🚀")), value(RawPref.Str("$JSON_LIST_PREFIX[\"x\",\"مرحبا\",\"\\ud83d\\ude80\"]")))
        assertEquals(PrefValue.StrList(emptyList()), value(RawPref.Str("$JSON_LIST_PREFIX[]")))
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadJsonList), value(RawPref.Str("$JSON_LIST_PREFIX[1,\"a\"]")))
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadJsonList), value(RawPref.Str("${JSON_LIST_PREFIX}not json")))
        assertEquals(StoredKind.JsonList, codec.decode(RawPref.Str("$JSON_LIST_PREFIX[]")).kind)
    }

    @Test
    fun `B-MIGR-005 the legacy Java list goes through Android Base64 to the injected decoder`() {
        var seen: ByteArray? = null
        val c = FlutterPrefsCodec(JavaListDecoder { bytes -> seen = bytes; listOf("ok") })
        // Android's DEFAULT Base64 has a line break every 76 characters; the decoder skips it.
        assertEquals(PrefValue.StrList(listOf("ok")), c.decode(RawPref.Str(LIST_PREFIX + "rO0A\nBXQ=")).value)
        assertContentEquals(byteArrayOf(0xAC.toByte(), 0xED.toByte(), 0x00, 0x05, 0x74), seen)
        assertEquals(StoredKind.JavaList, c.decode(RawPref.Str(LIST_PREFIX + "rO0A")).kind)
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadJavaList), codec.decode(RawPref.Str(LIST_PREFIX + "rO0A")).value)
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadJavaList), c.decode(RawPref.Str(LIST_PREFIX + "A")).value, "a lone sextet")
    }

    @Test
    fun `B-MIGR-005 the double and BigInteger prefixes decode as the plugin does`() {
        assertEquals(PrefValue.Dbl(1.0E-7), value(RawPref.Str(DOUBLE_PREFIX + "1.0E-7")))
        assertEquals(PrefValue.Dbl(1.25), value(RawPref.Str(DOUBLE_PREFIX + "1.25")))
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadDouble), value(RawPref.Str(DOUBLE_PREFIX + "abc")))
        assertEquals(PrefValue.Int64(1295), value(RawPref.Str(BIG_INTEGER_PREFIX + "zz")))
        assertEquals(PrefValue.Int64(-35), value(RawPref.Str(BIG_INTEGER_PREFIX + "-z")))
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadBigInteger), value(RawPref.Str(BIG_INTEGER_PREFIX + "zzzzzzzzzzzzzzzz")))
        assertEquals(PrefValue.Undecodable(UndecodableKind.BadBigInteger), value(RawPref.Str(BIG_INTEGER_PREFIX + "!")))
    }

    @Test
    fun `B-MIGR-011 decoded values never print their content`() {
        val secretish = "server-password-123"
        assertFalse(value(RawPref.Str(secretish)).toString().contains(secretish))
        assertFalse(value(RawPref.Str("$JSON_LIST_PREFIX[\"$secretish\"]")).toString().contains(secretish))
        assertFalse(codec.decode(RawPref.Str(secretish)).toString().contains(secretish))
        assertFalse(RawPref.Str(secretish).toString().contains(secretish))
        assertFalse(value(RawPref.Str("${JSON_LIST_PREFIX}$secretish")).toString().contains(secretish))
    }

    @Test
    fun `B-MIGR-005 encoders write what Flutter writes and refuse clashing strings`() {
        assertEquals(RawPref.Int64(42), codec.encodeLong(42))
        assertEquals(RawPref.Str("$JSON_LIST_PREFIX[\"a\",\"b\\nc\"]"), codec.encodeStringList(listOf("a", "b\nc")))
        assertEquals(RawPref.Str(DOUBLE_PREFIX + "0.1"), codec.encodeDouble(0.1))
        for (p in listOf(LIST_PREFIX, JSON_LIST_PREFIX, DOUBLE_PREFIX, BIG_INTEGER_PREFIX)) {
            assertFailsWith<PrefixClash> { codec.encodeString(p + "x") }
            assertTrue(FlutterPrefsCodec.clashes(p))
        }
        assertEquals(RawPref.Str("VGhpcyBp"), codec.encodeString("VGhpcyBp"), "a partial prefix is an ordinary string")
    }

    @Test
    fun `Android Base64 skips junk, accepts missing padding and refuses data after padding`() {
        assertContentEquals("hi".encodeToByteArray(), FlutterPrefsCodec.decodeAndroidBase64("aGk"))
        assertContentEquals("hi".encodeToByteArray(), FlutterPrefsCodec.decodeAndroidBase64("aG k=\n"))
        assertNull(FlutterPrefsCodec.decodeAndroidBase64("aGk=aGk="))
        assertNull(FlutterPrefsCodec.decodeAndroidBase64("=aGk"))
    }

    @Test
    fun `physical and logical keys map through the flutter prefix only`() {
        assertEquals("flutter.oc.model.1", FlutterPrefsCodec.physicalKey("oc.model.1"))
        assertEquals("oc.model.1", FlutterPrefsCodec.logicalKey("flutter.oc.model.1"))
        assertNull(FlutterPrefsCodec.logicalKey("unprefixed.kotlin.key"))
    }
}
