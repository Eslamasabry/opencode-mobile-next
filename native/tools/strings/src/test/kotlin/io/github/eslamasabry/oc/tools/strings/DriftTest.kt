package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drift check (plan 06 §13.8, `strings check`): the ARB files and the Android native strings, converted again
 * in memory, must equal what `:ui:words` holds; and the XML itself must be consistent across languages.
 * Each planted defect must be found; clean output must give no finding.
 */
class DriftTest {
    private val recipes = HandRecipes.parse(
        listOf("runningPhoneCpu\tselect\tlevel words", "e7SettingsStorageSummary\tsplit-plurals\ttwo counts"),
    )

    private fun written(): Path {
        val (conv0, _) = Fixture.conversion(recipes)
        val out = Files.createTempDirectory("strings-drift")
        XmlWriter.write(conv0, out.resolve("composeResources"), out.resolve("unused"), androidResOut = out.resolve("res"))
        return out
    }

    private fun check(out: Path) = Drift.check(
        Drift.Inputs(
            arbDir = Fixture.arbDir,
            recipes = recipes,
            androidRes = null,
            words = out.resolve("composeResources"),
            unused = out.resolve("unused"),
            androidResOut = out.resolve("res"),
        ),
    )

    private fun edit(file: Path, from: String, to: String) {
        val text = file.readText()
        check(from in text) { "$from not in $file" }
        file.writeText(text.replaceFirst(from, to))
    }

    @Test
    fun `B-L10N-010 freshly converted output has no drift`() {
        assertEquals(emptyList(), check(written()))
    }

    @Test
    fun `a changed value, a lost key and a stale key are found`() {
        val out = written()
        val en = out.resolve("composeResources/values")
        edit(en.resolve("strings_kit.xml"), "%1\$d%", "%1\$d %")
        val chat = en.resolve("strings_chat.xml")
        edit(chat, "<string name=\"chatUiReferenceAlreadyAdded\">", "<string name=\"chatUiReferenceAlreadyAddedX\">")
        val f = check(out)
        val kinds = f.map { it.kind to it.name }.toSet()
        assertTrue(Drift.Kind.CHANGED to "agentAccountPercentUsed" in kinds, f.joinToString("\n"))
        assertTrue(Drift.Kind.MISSING to "chatUiReferenceAlreadyAdded" in kinds, f.joinToString("\n"))
        // An XML name that starts with an ARB key but that the ARB does not produce: stale.
        edit(chat, "<string name=\"chatUiReferenceAlreadyAddedX\">", "<string name=\"chatUiReferenceAlreadyAdded__eq7\">")
        assertTrue(check(out).any { it.kind == Drift.Kind.STALE && it.name == "chatUiReferenceAlreadyAdded__eq7" })
    }

    @Test
    fun `native-only copy is allowed, but must still be consistent`() {
        val out = written()
        val en = out.resolve("composeResources/values/strings_chat.xml")
        val ar = out.resolve("composeResources/values-ar/strings_chat.xml")
        edit(en, "</resources>", "    <string name=\"chats_title\">Conversations</string>\n</resources>")
        edit(ar, "</resources>", "    <string name=\"chats_title\">المحادثات</string>\n</resources>")
        assertEquals(emptyList(), check(out))
        edit(ar, "name=\"chats_title\"", "name=\"chats_titel\"")
        val f = check(out)
        assertTrue(f.any { it.kind == Drift.Kind.ORPHAN && it.locale == "ar" && it.name == "chats_titel" }, f.toString())
        assertTrue(f.any { it.kind == Drift.Kind.AR_MISSING && it.name == "chats_title" }, f.toString())
    }

    @Test
    fun `placeholder, plural category, exact selector and invisible character defects are found`() {
        val out = written()
        val ar = out.resolve("composeResources/values-ar")
        // A translation that uses an index English does not have.
        val arChat = ar.resolve("strings_chat.xml")
        val arText = arChat.readText()
        val name = "chatUiReferenceAlreadyAdded"
        val line = arText.lines().first { "name=\"$name\"" in it }
        edit(arChat, line, line.replace("%1\$s", "%2\$s"))
        // Arabic plurals need all six categories.
        edit(arChat, "<item quantity=\"few\">", "<item quantity=\"fwe\">")
        // A raw bidi mark instead of its \u escape.
        edit(arChat, "\\u200E", "‎")
        // An exact selector string removed from one language.
        val eq = arText.lines().first { "__eq1\"" in it }
        edit(arChat, eq, "")
        val f = check(out)
        val kinds = f.map { it.kind }.toSet()
        assertTrue(Drift.Kind.PLACEHOLDER in kinds, f.joinToString("\n"))
        assertTrue(Drift.Kind.CATEGORIES in kinds, f.joinToString("\n"))
        assertTrue(Drift.Kind.INVISIBLE in kinds, f.joinToString("\n"))
        assertTrue(Drift.Kind.EXACT in kinds, f.joinToString("\n"))
    }

    @Test
    fun `B-L10N-013 the Android res copy of strings_os must match the CMP copy`() {
        val out = written()
        val res = out.resolve("res/values/strings_os.xml")
        val first = res.readText().lines().first { "<string name=" in it }
        edit(res, first, first.replace("</string>", " changed</string>"))
        val f = check(out)
        assertTrue(f.any { it.kind == Drift.Kind.ANDROID_RES && it.locale == "en" }, f.toString())
    }

    @Test
    fun `B-L10N-010 the committed ui-words XML matches the ARB files and the Android strings`() {
        val repo = Fixture.repoRoot ?: error("run through Gradle: the test needs the repository")
        val findings = Drift.check(Drift.Inputs.forRepo(repo))
        assertEquals("", findings.joinToString("\n") { "${it.kind} ${it.locale} ${it.name}: ${it.detail}" })
    }
}

/** Keeping copy that `:ui:words` gained after the conversion (new copy goes into the XML only, plan 06 §13.3). */
class KeepNativeTest {
    @Test
    fun `a rerun keeps strings that do not come from the ARB files`() {
        val (conv, _) = Fixture.conversion()
        val out = Files.createTempDirectory("strings-keep")
        val dir = out.resolve("values").createDirectories()
        dir.resolve("strings_chat.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="chats_title">Conversations</string>
                <plurals name="chats_count">
                    <item quantity="one">%1${'$'}d conversation</item>
                    <item quantity="other">%1${'$'}d conversations</item>
                </plurals>
                <string name="chatUiReferenceAlreadyAdded">an old ARB value, replaced</string>
            </resources>
            """.trimIndent(),
        )
        XmlWriter.write(conv, out, null)
        val text = dir.resolve("strings_chat.xml").readText()
        assertTrue("<string name=\"chats_title\">Conversations</string>" in text, text)
        assertTrue("<plurals name=\"chats_count\">" in text, text)
        assertTrue("an old ARB value" !in text, text)
        // A native-only file the converter does not produce is left alone.
        val other = out.resolve("values/strings_extra.xml")
        other.writeText("<resources><string name=\"x\">y</string></resources>")
        XmlWriter.write(conv, out, null)
        assertTrue(Files.exists(other))
    }
}
