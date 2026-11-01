package io.github.eslamasabry.oc.tools.strings

import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The whole real catalogue: lib/l10n/app_*.arb read by the real Dart lib/ and native/tools/strings/feature-routes.tsv. */
class RealArbTest {
    private val repo: Path = assertNotNull(Fixture.repoRoot, "run through Gradle: the tests need the repository's lib/l10n")
    private val arbs = Arb.readDir(repo.resolve("lib/l10n"))
    private val routing = Router.route(
        arbs.first().messages.keys,
        repo.resolve("lib"),
        Routes.read(repo.resolve("native/tools/strings/feature-routes.tsv")),
    )
    private val conv = Converter({ routing.featureByKey[it] }).convert(arbs)

    private data class ReadBack(val keys: Set<String>, val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    /** Reads XML back the way CMP 1.12.2 does (DOM + handleSpecialCharacters). */
    private fun readBack(dir: Path): ReadBack {
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        if (dir.exists()) for (f in dir.listDirectoryEntries("strings_*.xml")) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f.toFile())
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val name = el.getAttribute("name")
                when (el.tagName) {
                    "string" -> check(strings.put(name, XmlText.cmpUnescape(el.textContent)) == null) { "duplicate $name in $dir" }
                    "plurals" -> {
                        val items = LinkedHashMap<String, String>()
                        val kids = el.getElementsByTagName("item")
                        for (j in 0 until kids.length) {
                            val item = kids.item(j) as Element
                            items[item.getAttribute("quantity")] = XmlText.cmpUnescape(item.textContent)
                        }
                        check(plurals.put(name, items) == null) { "duplicate $name in $dir" }
                    }
                    else -> error("unexpected <${el.tagName}> in $f")
                }
            }
        }
        val keys = (strings.keys.map { it.replace(Regex("__eq\\d+$"), "") } + plurals.keys).toSet()
        return ReadBack(keys, strings, plurals)
    }

    @Test
    fun `round trip - keys in equals keys out, per language, read back from the written XML`() {
        val out = Files.createTempDirectory("arb2xml-real")
        XmlWriter.write(conv, out.resolve("composeResources"), out.resolve("unused"))
        val summary = StringBuilder()
        for (arb in arbs) {
            val loc = conv[arb.locale]
            val folder = XmlWriter.folder(arb.locale, "en")
            val shipped = readBack(out.resolve("composeResources").resolve(folder))
            val unused = readBack(out.resolve("unused").resolve(folder))
            assertTrue(shipped.keys.intersect(unused.keys).isEmpty(), "${arb.locale}: a key is both shipped and unused")
            val accounted = shipped.keys + unused.keys + loc.skipped + loc.orphans
            assertEquals(arb.messages.keys, accounted, "${arb.locale}: every ARB key is written, skipped (listed) or an orphan (listed)")
            assertEquals(arb.messages.size, shipped.keys.size + unused.keys.size + loc.skipped.size + loc.orphans.size, arb.locale)
            summary.append("${arb.locale}: ${arb.messages.size} = ${shipped.keys.size} + ${unused.keys.size} + ${loc.skipped.size} + ${loc.orphans.size}\n")

            // Every value reads back exactly as converted.
            for (r in loc.files.values.flatten()) {
                val back = if (r.key in shipped.keys) shipped else unused
                when (r) {
                    is Resource.Str -> assertEquals(r.text, back.strings[r.name], "${arb.locale}/${r.name}")
                    is Resource.Plurals -> assertEquals(r.items, back.plurals[r.name], "${arb.locale}/${r.name}")
                }
            }
            // Every plural has exactly the language's CLDR categories (Arabic: all six).
            for ((name, items) in shipped.plurals + unused.plurals) {
                assertEquals(PluralRules.categories(arb.locale), items.keys.toList(), "${arb.locale}/$name")
            }
        }
        println(summary)
    }

    @Test
    fun `nothing is converted in a language that lacks the template key, and orphans are only Arabic`() {
        for (l in conv.locales.drop(1)) assertTrue(l.convertedKeys().all { it in arbs.first().messages }, l.locale)
        assertEquals(mapOf("ar" to 1018), conv.locales.filter { it.orphans.isNotEmpty() }.associate { it.locale to it.orphans.size })
    }

    @Test
    fun `placeholders in a translation use only indices the template defines`() {
        val token = Regex("""%(\d+)\$[ds]""")
        for (l in conv.locales) for (r in l.files.values.flatten()) {
            val max = conv.plans.getValue(r.key).placeholders.size
            val values = if (r is Resource.Str) listOf(r.text) else (r as Resource.Plurals).items.values.toList()
            for (v in values) for (m in token.findAll(v)) assertTrue(m.groupValues[1].toInt() in 1..max, "${l.locale}/${r.name}: $v")
        }
    }

    @Test
    fun `the not-convertible list is exactly the known ICU it cannot express`() {
        val keys = conv.problems.filter { it.locale == "en" }.associate { it.key to it.kind }
        assertEquals(
            mapOf(
                "e7SettingsStorageSummary" to ProblemKind.MULTIPLE_PLURALS,
                "e7SetupDeleteDisclosure" to ProblemKind.MULTIPLE_PLURALS,
                "chatAttachmentSheetAttached" to ProblemKind.MULTIPLE_PLURALS,
                "teamProjectGoalStatus" to ProblemKind.MULTIPLE_PLURALS,
                "teamProjectGoalStatusDraft" to ProblemKind.MULTIPLE_PLURALS,
                "teamProjectBoardSummary" to ProblemKind.MULTIPLE_PLURALS,
                "teamProjectPlanSummary" to ProblemKind.MULTIPLE_PLURALS,
                "kitAgentLabel" to ProblemKind.SELECT,
                "activeContextMessageTitle" to ProblemKind.SELECT,
                "runningPhoneGroup" to ProblemKind.SELECT,
                "runningPhoneName" to ProblemKind.SELECT,
                "runningPhoneCpu" to ProblemKind.SELECT,
                "runningPhoneAbout" to ProblemKind.SELECT,
            ),
            keys,
        )
        assertEquals(keys.keys, conv.problems.map { it.key }.toSet(), "no translation-only problems")
    }

    @Test
    fun `CLI fails loudly and writes nothing without --skip-unsupported`() {
        val tmp = Files.createTempDirectory("arb2xml-cli")
        val err = ByteArrayOutputStream()
        val code = run(
            listOf(
                "convert", "--arb", "${repo.resolve("lib/l10n")}", "--dart-lib", "${repo.resolve("lib")}",
                "--routes", "${repo.resolve("native/tools/strings/feature-routes.tsv")}",
                "--out", "$tmp/out", "--report", "$tmp/report",
            ),
            PrintStream(ByteArrayOutputStream()), PrintStream(err, true, Charsets.UTF_8),
        )
        val text = err.toString(Charsets.UTF_8)
        assertEquals(1, code, text)
        assertTrue("38 messages cannot be converted" in text, text)
        assertTrue("runningPhoneName [en,ar]" in text, text)
        assertFalse(tmp.resolve("out").exists(), "no XML written")
        assertTrue(tmp.resolve("report/unsupported.tsv").exists())
    }

    @Test
    fun `CLI exits 2 on a Dart file no route covers`() {
        val tmp = Files.createTempDirectory("arb2xml-routes")
        val routes = tmp.resolve("routes.tsv").also { Files.writeString(it, "lib/ui/\tcommon\n") }
        val err = ByteArrayOutputStream()
        val code = run(
            listOf(
                "convert", "--arb", "${repo.resolve("lib/l10n")}", "--dart-lib", "${repo.resolve("lib")}",
                "--routes", "$routes", "--out", "$tmp/out", "--report", "$tmp/report",
            ),
            PrintStream(ByteArrayOutputStream()), PrintStream(err, true, Charsets.UTF_8),
        )
        val text = err.toString(Charsets.UTF_8)
        assertEquals(2, code, text)
        assertTrue("lib/main.dart" in text, text)
    }

    @Test
    fun `the golden fixture is still verbatim from the real ARB files`() {
        for (f in Fixture.arbs()) {
            val real = arbs.first { it.locale == f.locale }
            for ((k, v) in f.messages) assertEquals(real.messages[k], v, "${f.locale}/$k drifted; rerun fixture-tools/cut_fixture.py")
            for ((k, v) in f.placeholders) assertEquals(real.placeholders[k], v, "@$k drifted")
        }
        assertTrue(Fixture.arbDir.listDirectoryEntries().map { it.name }.containsAll(arbs.map { "app_${it.locale}.arb" }))
    }
}
