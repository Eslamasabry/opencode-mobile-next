package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plan 06 §13.2 and §13.3 step 7: the Flutter app's native Android strings merge into `strings_os.xml` with their
 * names kept, and `strings_os` is generated into Android `res` for notifications, widgets and tiles.
 */
class AndroidStringsTest {
    @Test
    fun `aapt escapes decode to the text Android shows`() {
        assertEquals("Can't read yet", AndroidStrings.decode("Can\\'t read yet", formatted = true))
        assertEquals("say \"hi\"", AndroidStrings.decode("say \\\"hi\\\"", formatted = true))
        assertEquals("line\nnext\ttab", AndroidStrings.decode("line\\nnext\\ttab", formatted = true))
        assertEquals("@home ?x", AndroidStrings.decode("\\@home \\?x", formatted = true))
        assertEquals("é", AndroidStrings.decode("\\u00E9", formatted = true))
        // aapt2 collapses whitespace runs and trims the ends, except inside double quotes.
        assertEquals("a b", AndroidStrings.decode("  a \n   b ", formatted = true))
        assertEquals("  kept  ", AndroidStrings.decode("\"  kept  \"", formatted = true))
    }

    @Test
    fun `format strings become CMP positional tokens with the same meaning`() {
        assertEquals("%1\$d% left", AndroidStrings.decode("%1\$d%% left", formatted = true))
        assertEquals("%1\$d need you", AndroidStrings.decode("%d need you", formatted = true))
        assertEquals("%1\$s has %2\$d", AndroidStrings.decode("%s has %d", formatted = true))
        // A string with no arguments: a % is literal.
        assertEquals("80% done", AndroidStrings.decode("80% done", formatted = true))
        assertEquals("100%% sure", AndroidStrings.decode("100%% sure", formatted = false))
    }

    @Test
    fun `the Android res copy re-escapes for aapt2`() {
        val res = listOf(
            Resource.Str("left", "left", "%1\$d% left"),
            Resource.Str("cant", "cant", "Can't \"x\""),
            Resource.Str("pct", "pct", "80% done"),
            Resource.Plurals("need", "need", linkedMapOf("one" to "%1\$d needs you", "other" to "%1\$d need you")),
        )
        val xml = XmlWriter.renderAndroidRes(res, "strings_os.xml")
        assertTrue("<string name=\"left\">%1\$d%% left</string>" in xml, xml)
        assertTrue("<string name=\"cant\">Can\\u0027t \\u0022x\\u0022</string>" in xml, xml)
        assertTrue("<string name=\"pct\" formatted=\"false\">80% done</string>" in xml, xml)
        assertTrue("<item quantity=\"one\">%1\$d needs you</item>" in xml, xml)
        // And it decodes back to the same text through the aapt2 rules.
        val back = AndroidStrings.parse(xml, "test")
        assertEquals(res, back)
    }

    private fun resDir(): Path {
        val root = Files.createTempDirectory("strings-android-res")
        fun file(rel: String, text: String) = root.resolve(rel).also { it.parent.createDirectories() }.writeText(text)
        file(
            "values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <!-- a comment -->
                <string name="tile_label">Background mode</string>
                <string name="usage_left">%1${'$'}d%% left</string>
                <plurals name="tile_need_you">
                    <item quantity="one">%d needs you</item>
                    <item quantity="other">%d need you</item>
                </plurals>
            </resources>
            """.trimIndent(),
        )
        file("values/colors.xml", "<resources><color name=\"x\">#fff</color></resources>")
        file("values/widget_strings.xml", "<resources><string name=\"widget_label\">Conversation</string></resources>")
        file(
            "values-ar/strings.xml",
            """
            <resources>
                <string name="tile_label">وضع الخلفية</string>
                <plurals name="tile_need_you">
                    <item quantity="one">%d يحتاجك</item>
                    <item quantity="few">%d يحتاجونك</item>
                    <item quantity="other">%d يحتاجونك</item>
                </plurals>
            </resources>
            """.trimIndent(),
        )
        file("values-night/strings.xml", "<resources><string name=\"tile_label\">night</string></resources>")
        return root
    }

    @Test
    fun `B-L10N-013 the native strings are read per language, names kept, other qualifiers ignored`() {
        val native = AndroidStrings.readDir(resDir(), template = "en")
        assertEquals(setOf("en", "ar"), native.keys)
        assertEquals(listOf("tile_label", "usage_left", "tile_need_you", "widget_label"), native.getValue("en").map { it.name })
        val ar = native.getValue("ar").filterIsInstance<Resource.Plurals>().single()
        // Arabic plurals get every category of the language; a missing one reads `other`, as Android does.
        assertEquals(listOf("zero", "one", "two", "few", "many", "other"), ar.items.keys.toList())
        assertEquals("%1\$d يحتاجونك", ar.items["many"])
    }

    @Test
    fun `B-L10N-013 native strings are merged into strings_os, and a name clash fails`() {
        val base = Fixture.conversion().first
        val native = AndroidStrings.readDir(resDir(), template = "en")
        val merged = base.withNativeOs(native)
        val os = merged.osResources("en").map { it.name }
        assertTrue("tile_label" in os && "tile_need_you" in os && "widget_label" in os, os.toString())
        assertEquals(4, merged.nativeOs.getValue("en").size)
        val arbName = base["en"].files.getValue("os").first().name
        val clash = mapOf("en" to listOf(Resource.Str(arbName, arbName, "x")))
        assertFailsWith<IllegalArgumentException> { base.withNativeOs(clash) }
    }

    @Test
    fun `strings_os is written to composeResources and generated into Android res`() {
        val conv = Fixture.conversion().first.withNativeOs(AndroidStrings.readDir(resDir(), template = "en"))
        val out = Files.createTempDirectory("strings-os")
        XmlWriter.write(conv, out.resolve("composeResources"), out.resolve("unused"), androidResOut = out.resolve("res"))
        val cmp = out.resolve("composeResources/values/strings_os.xml").readText()
        val android = out.resolve("res/values/strings_os.xml").readText()
        assertTrue("<string name=\"usage_left\">%1\$d% left</string>" in cmp, cmp)
        assertTrue("<string name=\"usage_left\">%1\$d%% left</string>" in android, android)
        assertNotNull(Files.readString(out.resolve("res/values-ar/strings_os.xml")))
        // Only strings_os goes to Android res.
        assertEquals(listOf("strings_os.xml"), Files.list(out.resolve("res/values")).map { it.fileName.toString() }.toList())
    }
}
