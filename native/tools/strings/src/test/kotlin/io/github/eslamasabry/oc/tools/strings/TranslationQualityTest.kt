package io.github.eslamasabry.oc.tools.strings

import java.io.File
import kotlin.test.Test
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

    @Test
    fun `placeholder parity - a translation uses the same arguments as the English`() =
        gate("placeholders") { _, en, tr ->
            val want = tokens(en)
            when {
                tr.isPlural != en.isPlural -> "string in one, plurals in the other"
                tr.texts().any { t -> (placeholder.findAll(t).map { it.value }.toSet() - want).isNotEmpty() } ->
                    "uses an argument English lacks (${tokens(tr)} vs $want)"
                tokens(tr) != want -> "drops an argument (${tokens(tr)} vs $want)"
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

    @Test
    fun `glossary use - product terms are kept, glossary words are used, banned nouns are absent`() {
        val g = locales.associateWith(::glossary)
        gate("glossary") { l, en, tr ->
            val gl = g[l] ?: return@gate null
            val e = en.texts().joinToString(" ")
            val t = tr.texts().joinToString(" ")
            gl.keep.firstOrNull { it in e && it !in t }?.let { "product term \"$it\" must stay as written" }
                ?: gl.terms.firstOrNull { (en, tgt) -> en.containsMatchIn(e) && !tgt.containsMatchIn(t) }?.let { (en, tgt) -> "English /$en/ should read /$tgt/" }
                ?: gl.forbid.firstOrNull { (re, _) -> re.containsMatchIn(t) }?.let { (re, why) -> "/${re.pattern}/ is banned: $why" }
        }
    }

    // ---- no English left ----

    private val stopwords = setOf("the", "and", "your", "with", "this", "that", "could", "from", "have", "please", "again", "you", "will", "can't", "isn't", "are")
    private val latinWord = Regex("""[A-Za-z][A-Za-z'’-]{3,}""")

    @Test
    fun `no English left - a translation is not the English, and does not carry English sentences`() {
        val g = locales.associateWith(::glossary)
        val allowed = File(toolDir, "src/test/resources/untranslated-allowlist.tsv").takeIf { it.isFile }?.readLines().orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }.toSet()
        val nonLatin = setOf("ru", "ar", "ja", "zh")
        gate("english") { l, en, tr ->
            if (en.key in allowed) return@gate null
            val allow = g[l]?.allow.orEmpty() + (g[l]?.keep.orEmpty()).flatMap { it.split(' ') }
            val strip = { s: String -> s.replace(placeholder, " ").trim() }
            for ((q, t) in tr.forms) {
                val source = en.forms[q] ?: en.forms["other"] ?: continue
                val s = strip(source)
                val letters = Regex("""[A-Za-z]{3,}""").findAll(s).map { it.value }.toList()
                if (letters.isEmpty()) continue
                if (strip(t).equals(s, ignoreCase = true) && letters.any { it !in allow }) return@gate "is the English text"
                val words = latinWord.findAll(strip(t)).map { it.value }.filter { it !in allow }.toList()
                if (l in nonLatin) {
                    if (words.size >= 3 && words.size * 2 > Regex("""\p{L}+""").findAll(strip(t)).count()) return@gate "mostly Latin words: ${words.take(4)}"
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
        // Budget: a short English label (a button, a tab, a chip) may grow by 60 % + 6 characters in translation, which the
        // kit's pseudo-locale render (ui/kit PseudoLocaleOverflowTest, 40 % + wrapping) is known to hold.
        val cjk = setOf("ja", "zh")
        gate("length") { l, en, tr ->
            if (l in cjk || en.isPlural) return@gate null
            val e = en.forms[""]!!
            val t = tr.forms[""]!!
            if (e.length > 28 || e.contains('\n') || Regex("""[.!?:;,]""").containsMatchIn(e)) return@gate null
            val budget = e.length * 1.6 + 6
            if (t.length > budget) "label of ${t.length} characters for English ${e.length} (\"$e\" → \"$t\")" else null
        }
    }

    // ---- the language gate ----

    @Test
    fun `language gate - a language is offered only at 95 percent of English, in the picker and in locales_config`() {
        val words = catalog.roots.first()
        val en = catalog.read(words, "en").keys
        val coverage = locales.associateWith { l -> catalog.read(words, l).keys.intersect(en).size * 100.0 / en.size }
        val offered = coverage.filterValues { it >= 95.0 }.keys
        val gradle = File(nativeRoot, "ui/words/build.gradle.kts").readText()
        val app = File(nativeRoot, "androidApp/build.gradle.kts").readText()
        assertTrue(">= 95 * source.size" in gradle && ">= 95 * source.size" in app, "the 95 % rule is the same in both build files")
        assertTrue(!File(nativeRoot, "androidApp/src/main/res/xml/locales_config.xml").exists(), "locales_config.xml is generated, not hand-kept")
        println("language coverage: " + coverage.entries.joinToString { "${it.key} ${"%.1f".format(it.value)}%" } + "; offered: en $offered")
    }
}
