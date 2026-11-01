package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Golden tests on real ARB samples (fixture cut verbatim from lib/l10n). The XML files under
 * src/test/resources/golden are the reviewed expectation; `-PupdateGoldens=true` rewrites them.
 */
class GoldenTest {
    private val conv = Fixture.conversion().first

    private fun res(locale: String, name: String): Resource =
        conv[locale].files.values.flatten().firstOrNull { it.name == name } ?: fail("$locale has no $name")

    private fun str(locale: String, name: String) = (res(locale, name) as Resource.Str).text
    private fun plural(locale: String, name: String) = (res(locale, name) as Resource.Plurals).items

    @Test
    fun `fixture XML matches the goldens byte for byte`() {
        val out = Files.createTempDirectory("arb2xml-golden")
        XmlWriter.write(conv, out.resolve("composeResources"), out.resolve("unused"))
        val actual = files(out)
        if (System.getProperty("strings.updateGoldens") == "true") {
            Fixture.goldenDir.toFile().deleteRecursively()
            for (rel in actual.keys) out.resolve(rel).copyTo(Fixture.goldenDir.resolve(rel).also { it.parent.createDirectories() })
        }
        assertTrue(Fixture.goldenDir.exists(), "no goldens; run ./gradlew test -PupdateGoldens=true and review them")
        val expected = files(Fixture.goldenDir)
        assertEquals(expected.keys, actual.keys, "golden file set")
        for ((rel, text) in expected) assertEquals(text, actual[rel], "golden $rel")
    }

    private fun files(root: Path): Map<String, String> = Files.walk(root).use { s ->
        s.filter { it.isRegularFile() }.toList().associate { root.relativize(it).invariantSeparatorsPathString to it.readText() }
    }

    @Test
    fun `Arabic plural gets all six categories with the ARB text`() {
        val items = plural("ar", "chatUiDraftsQueued")
        assertEquals(PluralRules.ALL, items.keys.toList())
        assertEquals("لا مسودات في قائمة الانتظار.", items["zero"])
        assertEquals("مسودة واحدة في قائمة الانتظار للإرسال عند إعادة الاتصال.", items["one"])
        assertTrue(items.getValue("few").startsWith("%1\$s "), items["few"]) // `count` is ARB type num → %s
    }

    @Test
    fun `every language gets exactly its own CLDR categories`() {
        for (loc in listOf("en", "ar", "es", "ja", "pt", "ru", "zh")) {
            assertEquals(PluralRules.categories(loc), plural(loc, "chatUiDraftsQueued").keys.toList(), loc)
        }
    }

    @Test
    fun `exact selectors become __eqN strings and fill a category only where it is exact`() {
        // en: =1 is exactly `one`; =0 has no category, so only the helper string carries it.
        assertEquals("waiting 1 minute", plural("en", "kitNeedsYouWaitingSpoken")["one"])
        assertEquals("waiting less than a minute", str("en", "kitNeedsYouWaitingSpoken__eq0"))
        assertEquals("waiting 1 minute", str("en", "kitNeedsYouWaitingSpoken__eq1"))
        // ar: =0 → zero and =1 → one; `two` has no branch, so it takes `other`.
        val ar = plural("ar", "kitNeedsYouWaitingSpoken")
        assertEquals("ينتظر منذ أقل من دقيقة", ar["zero"])
        assertEquals("ينتظر منذ دقيقة واحدة", ar["one"])
        assertEquals("ينتظر منذ %1\$s من الدقائق", ar["two"])
        // pt: `one` is {0, 1}, so =1 must NOT stand in for it; the helper string carries =1.
        assertEquals("aguardando há %1\$s minutos", plural("pt", "kitNeedsYouWaitingSpoken")["one"])
        assertEquals("aguardando há 1 minuto", str("pt", "kitNeedsYouWaitingSpoken__eq1"))
        // ru has no =1 branch: __eq1 is what ICU shows for 1, the `one` text.
        assertEquals("ждёт %1\$s минуту", str("ru", "kitNeedsYouWaitingSpoken__eq1"))
        assertEquals("ждёт меньше минуты", str("ru", "kitNeedsYouWaitingSpoken__eq0"))
    }

    @Test
    fun `placeholders are numbered by English order in every language`() {
        assertEquals("%1\$s has the team's latest work (%2\$s).", str("en", "aiteamBringInDone"))
        assertEquals("أصبح أحدث عمل للفريق (%2\$s) في %1\$s.", str("ar", "aiteamBringInDone"))
        assertEquals("已触发速率限制。%2\$s 后重试%1\$s", str("zh", "chatUiRateLimitCountdown"))
        assertEquals("%1\$s · %2\$s\n%3\$s: %4\$s", str("en", "monitorRequestSummary"))
    }

    @Test
    fun `int placeholders are d, num and decimalPattern ints are s`() {
        val plans = conv.plans
        assertEquals('d', plans.getValue("agentAccountPercentUsed").placeholders.single().spec)
        assertEquals('s', plans.getValue("chatUiDraftsQueued").placeholders.single().spec)
        val shown = plans.getValue("terminalShowEarlier").placeholders.single()
        assertEquals('s', shown.spec)
        assertEquals("decimalPattern", shown.format)
    }

    @Test
    fun `an embedded plural copies the surrounding text into every item`() {
        val en = plural("en", "chatUiQuestionsSummary")
        assertEquals("%1\$s · 1 question", en["one"])
        assertEquals("%1\$s · %2\$s questions", en["other"])
        assertEquals("%1\$s · سؤالان", plural("ar", "chatUiQuestionsSummary")["two"])
    }

    @Test
    fun `plain text is promoted to plurals when another language pluralises`() {
        val en = plural("en", "chatTurnAllFilesMore")
        assertEquals(setOf("All files · %1\$d more changed"), en.values.toSet())
        assertEquals(listOf("chatTurnAllFilesMore"), conv["en"].promoted)
        assertEquals("كل الملفات · ملفان آخران تغيّرا", plural("ar", "chatTurnAllFilesMore")["two"])
        assertEquals("كل الملفات · ملف آخر تغيّر", str("ar", "chatTurnAllFilesMore__eq1"))
    }

    @Test
    fun `routing follows the plan rule`() {
        val (c, routing) = Fixture.conversion()
        val f = routing.featureByKey
        assertEquals("chat", f["chatUiDraftsQueued"])
        assertEquals("chat", f["kitNeedsYouWaitingSpoken"], "read only from a part file of a chat library")
        assertEquals("kit", f["agentAccountPercentUsed"])
        assertEquals("common", f["servicesIntro"], "read by common and project")
        assertEquals("project", f["libraryModelsAgentsTitle"])
        assertEquals("phone", f["terminalShowEarlier"])
        assertEquals("os", f["appExitServerStopped"])
        assertEquals("team", f["teamUiGateAnswerRunRetry"], "key prefix, no reader")
        assertEquals(setOf("servicesTitle"), routing.unused, "non-importing files and lib/l10n are ignored")
        assertEquals(listOf("servicesTitle"), c["en"].files.getValue(LocaleResult.UNUSED).map { it.key })
    }

    @Test
    fun `strings_os uses the Android-safe escaping, feature files stay readable`() {
        val os = XmlWriter.render(conv["en"].files.getValue("os"), "os", "app_en.arb")
        assertTrue("Your phone\\u0027s OpenCode stopped with it; it\\u0027s starting again." in os, os)
        val common = XmlWriter.render(conv["en"].files.getValue("common"), "common", "app_en.arb")
        assertTrue("Keep your project's development commands" in common, common)
        assertTrue("Set one with \"paseo daemon set-password\". Stored in this device's secure storage." in common, common)
        val kit = XmlWriter.render(conv["en"].files.getValue("kit"), "kit", "app_en.arb")
        assertTrue(">%1\$d% used<" in kit, kit)
        val ar = XmlWriter.render(conv["ar"].files.getValue("chat"), "chat", "app_ar.arb")
        assertTrue(">\\u200E@%1\$s موجود بالفعل في الطلب<" in ar, ar)
        val project = XmlWriter.render(conv["en"].files.getValue("project"), "project", "app_en.arb")
        assertTrue("&amp;" in project, project)
    }

    @Test
    fun `select and multi-plural messages are refused and listed, orphans dropped`() {
        val kinds = conv.problems.groupBy({ it.kind }, { "${it.locale}:${it.key}" })
        assertEquals(listOf("en:runningPhoneCpu", "ar:runningPhoneCpu"), kinds[ProblemKind.SELECT])
        assertEquals(
            listOf("en:e7SettingsStorageSummary", "ar:e7SettingsStorageSummary", "es:e7SettingsStorageSummary", "zh:e7SettingsStorageSummary"),
            kinds[ProblemKind.MULTIPLE_PLURALS],
        )
        assertEquals(listOf("setupCancelConnection"), conv["ar"].orphans)
        assertNull(conv["ar"].files.values.flatten().firstOrNull { it.key == "setupCancelConnection" })
        for (loc in conv.locales) {
            assertNull(loc.files.values.flatten().firstOrNull { it.key == "runningPhoneCpu" || it.key == "e7SettingsStorageSummary" })
        }
    }

    @Test
    fun `missing keys are reported per language`() {
        assertEquals(emptyList(), conv["en"].missing)
        assertEquals(14, conv["ja"].missing.size)
        assertTrue("servicesIntro" in conv["ja"].missing)
        assertTrue("chatUiDraftsQueued" !in conv["ja"].missing)
    }
}
