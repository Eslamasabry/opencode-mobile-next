package io.github.eslamasabry.oc.tools.strings

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The validators every translated string passes (the translation pipeline, native/tools/strings/README.md): placeholder
 * parity, plural completeness, glossary use, no English left, XML escaping, and a length budget against the pseudo-locale.
 * They read the real resource folders of every language. Copy that predates the pipeline and fails a rule is listed in
 * `translation-baseline.tsv` (`gate<TAB>locale<TAB>key`); the list only shrinks, and the pipeline's own output must pass
 * with no entry. Regenerate it after a deliberate fix with `TRANSLATION_BASELINE_UPDATE=1`.
 */
class TranslationQualityTest {
    private val nativeRoot = File(requireNotNull(System.getProperty("strings.nativeRoot")) { "run through Gradle" })
    private val toolDir = File(requireNotNull(System.getProperty("strings.toolDir")))
    private val catalog = TranslationCatalog(nativeRoot)
    private val locales = catalog.locales()
    private val placeholder = Regex("""%(?:\d+\$)?[sd]""")

    private val baselineFile = File(toolDir, "src/test/resources/translation-baseline.tsv")
    private val baseline: Set<String> by lazy {
        baselineFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.toSet()
    }
    private val found = linkedMapOf<String, MutableList<String>>() // gate → "locale\tkey: detail"

    /** Every (root, locale, English entry, translated entry) pair. */
    private fun pairs(): Sequence<Triple<String, Entry, Entry>> = sequence {
        for (root in catalog.roots) {
            if (root.generated) continue
            val en = catalog.read(root, "en")
            for (tag in locales) {
                val tr = catalog.read(root, tag)
                for ((k, e) in tr) en[k]?.let { yield(Triple(tag, it, e)) }
            }
        }
    }

    private fun gate(name: String, offend: (locale: String, en: Entry, tr: Entry) -> String?) {
        val offenders = pairs().mapNotNull { (l, en, tr) -> offend(l, en, tr)?.let { "$l\t${en.key}: $it" } }.toList()
        ratchet(name, offenders)
    }

    private fun ratchet(gate: String, offenders: List<String>) {
        val ids = offenders.map { "$gate\t" + it.substringBefore(':') }.toSet()
        if (System.getenv("TRANSLATION_BASELINE_UPDATE") == "1") {
            val keep = baseline.filterNot { it.startsWith("$gate\t") }.toSet() + ids
            baselineFile.writeText("# gate<TAB>locale<TAB>key — shrinks only; see TranslationQualityTest\n" + keep.sorted().joinToString("\n") + "\n")
            return
        }
        val fresh = offenders.filter { "$gate\t" + it.substringBefore(':') !in baseline }
        assertTrue(fresh.isEmpty(), "$gate: ${fresh.size} new offenders\n" + fresh.take(5000).joinToString("\n"))
        val stale = baseline.filter { it.startsWith("$gate\t") } - ids
        assertTrue(stale.isEmpty(), "$gate: baseline entries no longer needed (delete them): ${stale.take(20)}")
    }

    private fun tokens(e: Entry) = e.texts().flatMap { placeholder.findAll(it).map { m -> m.value }.toList() }.toSet()

    /**
     * Arabic count variants (`zero`, `one`, `two`, written as `key__eq0/1/2` strings) may omit the count: the CLDR
     * singular and dual forms carry the number in the word ("ملفين", "ساعتين"). At most one numeric argument may go,
     * and nothing else; every other locale and quantity keeps the full rule.
     */
    private fun arabicCountMayDrop(locale: String, key: String, want: Set<String>, got: Set<String>): Boolean =
        isArabicCountVariant(locale, key) && (want - got).let { it.size == 1 && it.single().endsWith("d") }

    @Test
    fun `placeholder parity - a translation uses the same arguments as the English`() =
        gate("placeholders") { l, en, tr ->
            val want = tokens(en)
            when {
                tr.isPlural != en.isPlural -> "string in one, plurals in the other"
                tr.texts().any { t -> (placeholder.findAll(t).map { it.value }.toSet() - want).isNotEmpty() } ->
                    "uses an argument English lacks (${tokens(tr)} vs $want)"
                tokens(tr) != want && !arabicCountMayDrop(l, en.key, want, tokens(tr)) -> "drops an argument (${tokens(tr)} vs $want)"
                else -> null
            }
        }

    @Test
    fun `plural completeness - exactly the language's CLDR categories, none empty`() {
        val offenders = mutableListOf<String>()
        for ((l, en, tr) in pairs()) {
            if (!en.isPlural || !tr.isPlural) continue
            val need = PluralRules.categories(l).toSet()
            if (tr.forms.keys != need) offenders += "$l\t${en.key}: forms ${tr.forms.keys}, need $need"
            else if (tr.texts().any { it.isBlank() }) offenders += "$l\t${en.key}: an empty form"
        }
        ratchet("plurals", offenders)
    }

    @Test
    fun `escaping - apostrophes, ampersands and invisible characters survive both readers`() {
        val offenders = mutableListOf<String>()
        for (root in catalog.roots) {
            val en = catalog.read(root, "en")
            for (tag in locales) for ((k, e) in catalog.read(root, tag)) {
                if (k !in en) continue
                for (raw in e.raw.values) {
                    val problem = when {
                        Regex("""\\['"@?]""").containsMatchIn(raw) && root.kind == TranslationCatalog.Kind.CMP ->
                            "a backslash escape CMP shows literally (\\' \\\" \\@ \\?): write the character"
                        root.kind == TranslationCatalog.Kind.ANDROID && Regex("""(?<!\\)['"]""").containsMatchIn(raw) ->
                            "an unescaped quote or apostrophe breaks aapt2: write \\u0027 / \\u0022"
                        root.kind == TranslationCatalog.Kind.ANDROID && Regex("""^[@?]""").containsMatchIn(raw) -> "leading @ or ? is a resource reference"
                        raw.any { XmlText.isInvisible(it) } -> "a raw invisible character: write \\uXXXX"
                        Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#)""").containsMatchIn(raw) -> "a bare &"
                        else -> null
                    }
                    if (problem != null) { offenders += "$tag\t$k: $problem"; break }
                }
            }
        }
        ratchet("escaping", offenders)
    }

    // ---- glossary ----

    private class Glossary(val keep: List<String>, val terms: List<Pair<Regex, Regex>>, val forbid: List<Pair<Regex, String>>, val allow: Set<String>)

    private fun glossary(locale: String): Glossary? {
        val f = File(toolDir, "glossary-$locale.tsv").takeIf { it.isFile } ?: return null
        val rows = f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        return Glossary(
            keep = rows.filter { it[0] == "keep" }.map { it[1] },
            terms = rows.filter { it[0] == "term" }.map { Regex(it[1]) to Regex(it[2], RegexOption.IGNORE_CASE) },
            forbid = rows.filter { it[0] == "forbid" }.map { Regex(it[1], RegexOption.IGNORE_CASE) to it.getOrElse(2) { "" } },
            allow = rows.filter { it[0] == "allow" }.map { it[1] }.toSet(),
        )
    }

    private class Allowlist(private val global: Set<String>, private val byLocale: Map<String, Set<String>>) {
        fun allows(locale: String, key: String): Boolean = key in global || key in byLocale[locale].orEmpty()
    }

    private fun untranslatedAllowlist(): Allowlist =
        parseAllowlist(File(toolDir, "src/test/resources/untranslated-allowlist.tsv").takeIf { it.isFile }?.readLines().orEmpty())

    private fun parseAllowlist(lines: List<String>): Allowlist {
        val kinds = setOf("name", "unit", "cognate", "token")
        val global = linkedSetOf<String>()
        val byLocale = linkedMapOf<String, MutableSet<String>>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val cols = line.split('\t').map { it.trim() }
            if (cols.size == 1) {
                global += cols[0]
            } else if (cols.size == 2) {
                assertTrue(cols[1] in kinds, "allowlist reason '${cols[1]}' for ${cols[0]} must be one of $kinds")
                global += cols[0]
            } else {
                assertTrue(cols.size == 3, "invalid untranslated-allowlist.tsv row: $line")
                assertTrue(cols[2] in kinds, "allowlist reason '${cols[2]}' for ${cols[1]} must be one of $kinds")
                for (loc in cols[0].split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
                    if (loc == "*") global += cols[1] else byLocale.getOrPut(loc) { linkedSetOf() } += cols[1]
                }
            }
        }
        return Allowlist(global, byLocale)
    }

    @Test
    fun `glossary use - product terms are kept, glossary words are used, banned nouns are absent`() {
        val g = locales.associateWith(::glossary)
        val allowed = untranslatedAllowlist()
        gate("glossary") { l, en, tr ->
            val gl = g[l] ?: return@gate null
            val e = en.texts().joinToString(" ")
            val t = tr.texts().joinToString(" ")
            gl.keep.firstOrNull { it in e && it !in t }?.let { "product term \"$it\" must stay as written" }
                ?: gl.terms.takeIf { !allowed.allows(l, en.key) }?.firstOrNull { (en, tgt) -> en.containsMatchIn(e) && !tgt.containsMatchIn(t) }
                    ?.let { (en, tgt) -> "English /$en/ should read /$tgt/" }
                ?: gl.forbid.firstOrNull { (re, _) -> re.containsMatchIn(t) }?.let { (re, why) -> "/${re.pattern}/ is banned: $why" }
        }
    }

    private fun japaneseSpacingOffenders(dir: File): List<String> {
        val jaChar = """[\u3040-\u309f\u30a0-\u30ff\u4e00-\u9fff々〆ヵヶー、。（）「」『』【】：]"""
        // Allowed exceptions:
        // - Middle-dot separator `" · "` (U+00B7 is outside both character classes).
        // - Spaces strictly inside ASCII/unit/code/placeholder runs (`Android 13`, `512 MB`, `%1$d / %2$d`).
        // - Search `*Aliases` keys, which hold whitespace-delimited search tokens.
        // - `tools/strings/src/test/resources/golden/` fixtures (verified byte-for-byte against frozen ARBs).
        // - Generated Gradle `build/` output directories.
        val forbidden = listOf(
            Regex("""$jaChar [A-Za-z0-9%]"""),
            Regex("""[A-Za-z0-9%] $jaChar"""),
        )
        val offenders = mutableListOf<String>()
        for ((key, entry) in TranslationCatalog.readDir(dir)) {
            if (key.endsWith("Aliases")) continue
            val hit = entry.texts().firstNotNullOfOrNull { t ->
                forbidden.firstNotNullOfOrNull { it.find(t)?.value }
            }
            if (hit != null) {
                val relFile = File(dir, entry.file).relativeTo(nativeRoot).invariantSeparatorsPath
                offenders += "$relFile\t$key: \"$hit\""
            }
        }
        return offenders
    }

    @Test
    fun `japanese spacing - no space next to Latin, digits, or placeholders across every values-ja file`() {
        val jaDirs = nativeRoot.walkTopDown()
            .filter { it.isDirectory && it.name == "values-ja" }
            .filter { dir ->
                val rel = dir.relativeTo(nativeRoot).invariantSeparatorsPath
                "/build/" !in "/$rel/" && !rel.startsWith("tools/strings/src/test/resources/golden")
            }
            .sortedBy { it.path }
            .toList()
        val offenders = jaDirs.flatMap(::japaneseSpacingOffenders)
        assertTrue(
            offenders.isEmpty(),
            "ja spacing: ${offenders.size} spaced strings\n" + offenders.take(150).joinToString("\n"),
        )
    }

    @Test
    fun `japanese spacing - plural items and placeholder-before-Japanese are caught on the frozen ARB fixture`() {
        val goldenJa = File(nativeRoot, "tools/strings/src/test/resources/golden/composeResources/values-ja")
        val hits = japaneseSpacingOffenders(goldenJa)
        assertTrue(hits.any { "chatUiDraftsQueued" in it }, "must catch plural <item> %1\$s 件: $hits")
        assertTrue(hits.any { "chatUiRateLimitCountdown" in it }, "must catch %2\$s 後: $hits")
    }

    // ---- no English left ----

    private val stopwords = setOf("the", "and", "your", "with", "this", "that", "could", "from", "have", "please", "again", "you", "will", "can't", "isn't", "are")
    private val latinWord = Regex("""[A-Za-z][A-Za-z'’-]{3,}""")

    @Test
    fun `no English left - a translation is not the English, and does not carry English sentences`() {
        val g = locales.associateWith(::glossary)
        val allowed = untranslatedAllowlist()
        val nonLatin = setOf("ru", "ar", "ja", "zh")
        val cjkWordRuns = Regex("""[\u3040-\u30ff\u4e00-\u9fff\u3400-\u4dbf]{1,2}|[A-Za-z0-9]+|\p{L}+""")
        val latinWordRuns = Regex("""\p{L}+""")
        gate("english") { l, en, tr ->
            if (allowed.allows(l, en.key)) return@gate null
            val allow = g[l]?.allow.orEmpty() + (g[l]?.keep.orEmpty()).flatMap { it.split(' ') }
            val strip = { s: String -> s.replace(placeholder, " ").trim() }
            for ((q, t) in tr.forms) {
                val source = en.forms[q] ?: en.forms["other"] ?: continue
                val s = strip(source)
                val letters = Regex("""[A-Za-z]{3,}""").findAll(s).map { it.value }.toList()
                if (letters.isEmpty()) continue
                if (strip(t).equals(s, ignoreCase = true) && letters.any { it !in allow }) return@gate "is the English text"
                if (en.key.endsWith("Aliases")) continue
                val words = latinWord.findAll(strip(t)).map { it.value }.filter { it !in allow }.toList()
                val runs = if (l == "ja" || l == "zh") cjkWordRuns else latinWordRuns
                if (l in nonLatin) {
                    if (words.size >= 3 && words.size * 2 > runs.findAll(strip(t)).count()) return@gate "mostly Latin words: ${words.take(4)}"
                } else if (words.map { it.lowercase() }.filter { it in stopwords }.toSet().size >= 2) {
                    return@gate "English words left: ${words.take(5)}"
                }
            }
            null
        }
    }

    // ---- length against the pseudo-locale ----

    @Test
    fun `pseudo-locale - 40 percent longer English keeps its placeholders, and labels keep a length budget`() {
        val sample = PseudoLocale.expand("Try %1\$s again\\n")
        assertTrue("%1\$s" in sample && sample.contains("\\n"), sample)
        assertTrue(PseudoLocale.expand("Try again").length >= "Try again".length * 1.4, "grows by about 40 %")
        // Budget: a short English label (a button, a tab, a chip) may grow to 2x + 8 characters (Spanish, Portuguese and French run about 1.5x) in translation, which the
        // kit's pseudo-locale render (ui/kit PseudoLocaleOverflowTest, 40 % + wrapping) is known to hold.
        val cjk = setOf("ja", "zh")
        gate("length") { l, en, tr ->
            if (l in cjk || l == "ar" || en.isPlural) return@gate null
            val e = en.forms[""]!!
            val t = tr.forms[""]!!
            if (e.length > 28 || e.contains('\n') || Regex("""[.!?:;,]""").containsMatchIn(e)) return@gate null
            val budget = e.length * 2.0 + 8
            if (t.length > budget) "label of ${t.length} characters for English ${e.length} (\"$e\" → \"$t\")" else null
        }
    }

    // ---- the language gate ----

    private fun languageCoverage(cat: TranslationCatalog): Map<String, Double> =
        cat.coverageByLocale(cat.locales(), cat.roots)

    @Test
    fun `language gate - a language is offered only at 95 percent of English, in the picker and in locales_config`() {
        val coverage = languageCoverage(catalog)
        val offered = coverage.filterValues { it >= 95.0 }.keys
        val gradle = File(nativeRoot, "ui/words/build.gradle.kts").readText()
        val app = File(nativeRoot, "androidApp/build.gradle.kts").readText()
        assertTrue(">= 95 * source.size" in gradle && ">= 95 * source.size" in app, "the 95 % rule is the same in both build files")
        assertTrue(!File(nativeRoot, "androidApp/src/main/res/xml/locales_config.xml").exists(), "locales_config.xml is generated, not hand-kept")
        assertTrue(offered == locales.toSet(), "every offered language must reach 95 % across all roots: $coverage")
        println("language coverage: " + coverage.entries.joinToString { "${it.key} ${"%.1f".format(it.value)}%" } + "; offered: en $offered")
    }

    @Test
    fun `language gate - an incomplete non-words root pulls total coverage below 95 percent`() {
        val tmp = Files.createTempDirectory("lang-gate").toFile()
        try {
            fun writeXml(dir: File, keys: List<String>) {
                dir.mkdirs()
                val body = keys.joinToString("\n") { """    <string name="$it">$it</string>""" }
                File(dir, "strings.xml").writeText("<resources>\n$body\n</resources>\n")
            }
            val wordsKeys = (1..100).map { "w_$it" }
            val widgetKeys = (1..20).map { "wg_$it" }
            writeXml(File(tmp, "ui/words/src/commonMain/composeResources/values"), wordsKeys)
            writeXml(File(tmp, "ui/words/src/commonMain/composeResources/values-es"), wordsKeys)
            writeXml(File(tmp, "android/widgets/src/main/res/values"), widgetKeys)
            writeXml(File(tmp, "android/widgets/src/main/res/values-es"), emptyList())
            val cov = languageCoverage(TranslationCatalog(tmp)).getValue("es")
            assertFalse(cov >= 95.0, "expected < 95% when non-words root is untranslated, got $cov")
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `translation state - every shipped key in every language has an English hash recorded`() {
        val stateDir = File(toolDir, "translation-state")
        val missing = mutableListOf<String>()
        for (tag in locales) {
            val stateFile = File(stateDir, "$tag.tsv")
            val recorded = if (stateFile.isFile) {
                stateFile.readLines().filter { '\t' in it }.map { it.substringBefore('\t') }.toSet()
            } else {
                emptySet()
            }
            for (root in catalog.roots) {
                if (root.generated) continue
                val en = catalog.read(root, "en").keys
                val tr = catalog.read(root, tag).keys
                for (key in en.intersect(tr)) {
                    val stateKey = if (root.name == "words") key else "${root.name}:$key"
                    if (stateKey !in recorded) missing += "$tag\t$stateKey"
                }
            }
        }
        assertTrue(missing.isEmpty(), "translation-state missing ${missing.size} shipped keys:\n" + missing.take(80).joinToString("\n"))
    }

    @Test
    fun `allowlist - locale-scoped cognates allow only listed locales and reject non-values folders`() {
        val parsed = parseAllowlist(listOf("globalKey\tname", "fr,es\tcognateKey\tcognate"))
        assertTrue(parsed.allows("ar", "globalKey"))
        assertTrue(parsed.allows("fr", "cognateKey"))
        assertTrue(parsed.allows("es", "cognateKey"))
        assertFalse(parsed.allows("ar", "cognateKey"))
        assertTrue(TranslationCatalog.tagOf("values-in") == "id")
        assertTrue(TranslationCatalog.tagOf("values") == null)
        assertTrue(TranslationCatalog.tagOf("xml") == null)
    }

    @Test
    fun `arabic zero, one and two variants may omit the count, nothing else may`() {
        val count = setOf("%1\$d")
        assertTrue(arabicCountMayDrop("ar", "chatRequestAskEditFiles__eq2", count, emptySet()))
        assertTrue(arabicCountMayDrop("ar", "gitAhead__eq0", count, emptySet()))
        assertTrue(arabicCountMayDrop("ar", "chatTurnAllFilesMore__eq1", count, emptySet()))
        // keeps the other arguments: only the count goes
        assertTrue(arabicCountMayDrop("ar", "fbRuleWeekOffers__eq2", setOf("%1\$d", "%2\$d", "%3\$d"), setOf("%1\$d", "%2\$d")))
        assertTrue(!arabicCountMayDrop("ar", "fbRuleWeekOffers__eq2", setOf("%1\$d", "%2\$d", "%3\$d"), setOf("%1\$d")))
        // a text argument is not a count
        assertTrue(!arabicCountMayDrop("ar", "fbSearchMatches__eq2", setOf("%2\$s"), emptySet()))
        // few, many and other (and plain strings) keep the count
        assertTrue(!arabicCountMayDrop("ar", "chatRequestAskEditFiles__eq3", count, emptySet()))
        assertTrue(!arabicCountMayDrop("ar", "chatRequestAskEditFiles", count, emptySet()))
        // other locales keep the rule for every quantity
        for (l in listOf("es", "fr", "ru", "pt", "id", "vi")) {
            assertTrue(!arabicCountMayDrop(l, "chatRequestAskEditFiles__eq2", count, emptySet()), l)
        }
    }

    private fun isArabicCountVariant(locale: String, key: String) =
        locale == "ar" && Regex("""__eq[012]$""").containsMatchIn(key)
}
