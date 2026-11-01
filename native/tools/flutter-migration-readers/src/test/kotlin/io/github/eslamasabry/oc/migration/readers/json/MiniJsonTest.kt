package io.github.eslamasabry.oc.migration.readers.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MiniJsonTest {

    @Test
    fun `parse keeps order and number kinds like dart jsonDecode`() {
        val v = MiniJson.parse("""{"b":1,"a":[true,null,1.5,-0,1e3,"\u00e9\n"],"c":{}}""") as Map<*, *>
        assertEquals(listOf("b", "a", "c"), v.keys.toList())
        assertEquals(1L, v["b"])
        assertEquals(listOf(true, null, 1.5, 0L, 1000.0, "é\n"), v["a"])
    }

    @Test
    fun `bad documents throw without echoing input`() {
        for (bad in listOf("", "{", "[1,]", "{\"a\" 1}", "\"\u0001\"", "01", "tru", "[1] x", "\"\\x\"")) {
            val e = assertFailsWith<MiniJson.ParseException> { MiniJson.parse(bad) }
            assertEquals(false, bad.isNotBlank() && e.message!!.contains(bad.trim()), bad)
        }
        assertNull(MiniJson.parseOrNull("{"))
    }

    @Test
    fun `encode follows dart jsonEncode escaping`() {
        assertEquals("""["a\"b","\\","\b\t\n\f\r","\u0001\u001f","/","é","😀","\ud800","\udc00x"]""",
            MiniJson.encode(listOf("a\"b", "\\", "\b\t\n\u000C\r", "\u0001\u001f", "/", "é", "😀", "\uD800", "\uDC00x")))
        assertEquals("""{"k":[1,true,null]}""", MiniJson.encode(linkedMapOf("k" to listOf(1L, true, null))))
    }

    @Test
    fun `doubles print like dart double toString`() {
        val cases = mapOf(
            1.0 to "1.0", 0.5 to "0.5", -2.0 to "-2.0", 123456.789 to "123456.789", 0.000001 to "0.000001",
            1e-7 to "1e-7", 1.5e-7 to "1.5e-7", 1e21 to "1e+21", 1.25e22 to "1.25e+22", 1e20 to "100000000000000000000.0",
            0.0 to "0.0", -0.0 to "-0.0",
        )
        for ((d, s) in cases) assertEquals(s, MiniJson.dartDouble(d), "for $d")
        assertFailsWith<IllegalArgumentException> { MiniJson.dartDouble(Double.NaN) }
    }

    @Test
    fun `round trip`() {
        val v = linkedMapOf("x" to listOf("a", 1L, 2.5, false, null, linkedMapOf("y" to "z")))
        assertEquals(v, MiniJson.parse(MiniJson.encode(v)))
    }
}
