package io.github.eslamasabry.oc.tools.strings

import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * `strings check` (plan 06 §13.8 items 1–4 and 6): the drift check between the frozen sources and `:ui:words`.
 *
 * - **ARB → XML:** the ARB files (with the hand recipes) and the Flutter app's native Android strings are converted
 *   again in memory. Every resource they produce must be in the XML with the same text, and every XML name that
 *   belongs to them must still be produced (no stale `__eqN` or branch). Which `strings_<feature>.xml` holds a
 *   name does not matter, so a feature owner may move a key between files.
 * - **The XML on its own:** no name twice in a folder; every name in a translation exists in `values/`; a
 *   translation uses only argument indices (and the same `d`/`s`) its key has; every plural has exactly its
 *   language's categories (Arabic all six); every `__eqN` exists wherever its key does; no raw invisible
 *   characters; copy added after the conversion has an Arabic value.
 * - **Android res:** the generated `strings_os.xml` copies say exactly what the CMP `strings_os.xml` says.
 *
 * Copy added in `:ui:words` after the conversion (names no source produces) is allowed.
 */
object Drift {
    enum class Kind(val label: String) {
        UNCONVERTIBLE("a source message has no conversion (add a hand recipe)"),
        MISSING("a source resource is not in the XML"),
        CHANGED("the XML text differs from the source"),
        SHAPE("string in one, plurals in the other"),
        STALE("the XML has a source-derived name the sources no longer produce"),
        DUPLICATE("a name is defined twice in one values folder"),
        MALFORMED("not well-formed XML, or an unexpected element"),
        ORPHAN("a translation has a name values/ does not have"),
        PLACEHOLDER("a translation uses an argument its key does not have"),
        CATEGORIES("a plural does not have exactly its language's categories"),
        EXACT("an __eqN string is missing where its key exists"),
        INVISIBLE("a raw invisible or bidi character (write it as \\uXXXX)"),
        AR_MISSING("copy added after the conversion has no Arabic value"),
        ANDROID_RES("the Android res strings_os differs from the CMP strings_os"),
    }

    data class Finding(val kind: Kind, val locale: String, val name: String, val detail: String)

    data class Inputs(
        val arbDir: Path,
        val recipes: List<Recipe>,
        /** The Flutter app's `android/app/src/main/res`, or null. */
        val androidRes: Path?,
        /** `:ui:words` composeResources. */
        val words: Path,
        /** The unused keys' XML (outside composeResources), or null. */
        val unused: Path?,
        /** `:ui:words` `src/androidMain/res` (the generated strings_os copies), or null. */
        val androidResOut: Path?,
        val template: String = "en",
    ) {
        companion object {
            fun forRepo(repo: Path) = Inputs(
                arbDir = repo.resolve("lib/l10n"),
                recipes = HandRecipes.read(repo.resolve(Paths.HAND_RECIPES)),
                androidRes = repo.resolve(Paths.ANDROID_RES).takeIf { it.isDirectory() },
                words = repo.resolve(Paths.WORDS),
                unused = repo.resolve(Paths.UNUSED),
                androidResOut = repo.resolve(Paths.WORDS_ANDROID_RES),
            )
        }
    }

    private sealed interface Value {
        val file: String
        data class S(val text: String, override val file: String) : Value
        data class P(val items: Map<String, String>, override val file: String) : Value
    }

    private val token = Regex("""%(\d+)\$([ds])""")
    private val eq = Regex("__eq(\\d+)$")

    fun check(inputs: Inputs): List<Finding> {
        val findings = mutableListOf<Finding>()
        fun f(kind: Kind, locale: String, name: String, detail: String = "") { findings += Finding(kind, locale, name, detail) }

        // 1. The sources, converted again.
        val arbs = Arb.readDir(inputs.arbDir, inputs.template)
        val expansion = Hand.expand(arbs, inputs.recipes, inputs.template)
        var conv = Converter({ "all" }, inputs.template).convert(expansion.arbs)
        val native = inputs.androidRes?.let { AndroidStrings.readDir(it, inputs.template) }.orEmpty()
        conv = conv.withNativeOs(native)
        for (p in conv.problems) f(Kind.UNCONVERTIBLE, p.locale, p.key, "${p.kind}: ${p.detail.take(100)}")

        val templateKeys = arbs.first().messages.keys
        val nativeNames = native.values.flatten().mapTo(HashSet()) { it.name }
        fun claimed(name: String): Boolean {
            val s = name.replace(eq, "")
            return s in templateKeys || s in expansion.baseOf || name in nativeNames
        }

        // 2. The XML, per locale (composeResources and the unused folder together).
        val xml = LinkedHashMap<String, LinkedHashMap<String, Value>>()
        for (root in listOfNotNull(inputs.words, inputs.unused)) {
            if (!root.isDirectory()) continue
            for (dir in root.listDirectoryEntries("values*").filter { it.isDirectory() }.sortedBy { it.name }) {
                val locale = localeOf(dir.name, inputs.template) ?: continue
                val map = xml.getOrPut(locale) { LinkedHashMap() }
                for (file in dir.listDirectoryEntries("strings_*.xml").sortedBy { it.name }) {
                    val rel = "${dir.name}/${file.name}"
                    val text = file.readText()
                    text.forEachIndexed { i, c ->
                        if (XmlText.isInvisible(c)) f(Kind.INVISIBLE, locale, rel, "U+%04X at offset $i".format(c.code))
                    }
                    val values = try {
                        read(text, rel)
                    } catch (e: Exception) {
                        f(Kind.MALFORMED, locale, rel, e.message ?: ""); continue
                    }
                    for ((name, v) in values) {
                        val before = map.put(name, v)
                        if (before != null) f(Kind.DUPLICATE, locale, name, "${before.file} and $rel")
                    }
                }
            }
        }

        // 3. Sources → XML.
        val expected = HashMap<String, Map<String, Resource>>()
        for (loc in conv.locales) {
            val exp = (loc.files.values.flatten() + conv.nativeOs[loc.locale].orEmpty()).associateBy { it.name }
            expected[loc.locale] = exp
            val have = xml[loc.locale].orEmpty()
            for ((name, r) in exp) {
                val v = have[name]
                when {
                    v == null -> f(Kind.MISSING, loc.locale, name)
                    r is Resource.Str && v is Value.S -> if (r.text != v.text) f(Kind.CHANGED, loc.locale, name, "${v.file}: \"${v.text.take(60)}\" ≠ \"${r.text.take(60)}\"")
                    r is Resource.Plurals && v is Value.P -> if (r.items != v.items) f(Kind.CHANGED, loc.locale, name, "${v.file}: ${v.items} ≠ ${r.items}")
                    else -> f(Kind.SHAPE, loc.locale, name, v.file)
                }
            }
            for (name in have.keys) if (name !in exp && claimed(name)) f(Kind.STALE, loc.locale, name, have.getValue(name).file)
        }
        for (locale in xml.keys) if (locale !in expected) {
            for (name in xml.getValue(locale).keys) if (claimed(name)) f(Kind.STALE, locale, name, "no app_$locale.arb")
        }

        // 4. The XML on its own.
        val en = xml[inputs.template].orEmpty()
        val plans = conv.plans
        fun allowed(name: String): Set<Pair<String, String>> {
            val key = expected[inputs.template]?.get(name)?.key
            val plan = key?.let { plans[it] }
            if (plan != null) return plan.placeholders.mapTo(HashSet()) { it.index.toString() to it.spec.toString() }
            return tokens(en[name])
        }
        for ((locale, values) in xml) {
            val cats = if (PluralRules.isKnown(locale)) PluralRules.categories(locale) else null
            for ((name, v) in values) {
                if (locale != inputs.template && name !in en) { f(Kind.ORPHAN, locale, name, v.file); continue }
                if (locale != inputs.template) {
                    val extra = tokens(v) - allowed(name)
                    if (extra.isNotEmpty()) f(Kind.PLACEHOLDER, locale, name, "${v.file}: ${extra.joinToString { "%${it.first}\$${it.second}" }}")
                }
                if (v is Value.P && cats != null && v.items.keys.toList() != cats) {
                    f(Kind.CATEGORIES, locale, name, "${v.file}: ${v.items.keys} (want $cats)")
                }
            }
            for (name in en.keys) {
                val m = eq.find(name) ?: continue
                val base = name.removeRange(m.range)
                if (base in values && name !in values) f(Kind.EXACT, locale, name, "has $base")
            }
        }
        val ar = xml["ar"]
        if (ar != null) for (name in en.keys) if (!claimed(name) && name !in ar) f(Kind.AR_MISSING, "ar", name, en.getValue(name).file)

        // 5. Android res copies of strings_os.
        val out = inputs.androidResOut
        if (out != null && inputs.words.isDirectory()) {
            val cmpFolders = inputs.words.listDirectoryEntries("values*").filter { it.resolve("strings_os.xml").exists() }.map { it.name }.toSet()
            val resFolders = if (out.isDirectory()) out.listDirectoryEntries("values*").filter { it.resolve("strings_os.xml").exists() }.map { it.name }.toSet() else emptySet()
            for (folder in (cmpFolders + resFolders).sorted()) {
                val locale = localeOf(folder, inputs.template) ?: folder
                if (folder !in resFolders) { f(Kind.ANDROID_RES, locale, "$folder/strings_os.xml", "missing in Android res"); continue }
                if (folder !in cmpFolders) { f(Kind.ANDROID_RES, locale, "$folder/strings_os.xml", "no CMP strings_os.xml"); continue }
                val cmp = read(inputs.words.resolve(folder).resolve("strings_os.xml").readText(), folder)
                val android = AndroidStrings.parse(out.resolve(folder).resolve("strings_os.xml").readText(), folder).associateBy { it.name }
                for ((name, v) in cmp) {
                    val a = android[name]
                    val same = when {
                        a is Resource.Str && v is Value.S -> a.text == v.text
                        a is Resource.Plurals && v is Value.P -> a.items == v.items
                        else -> false
                    }
                    if (!same) f(Kind.ANDROID_RES, locale, name, if (a == null) "missing" else "differs")
                }
                for (name in android.keys - cmp.keys) f(Kind.ANDROID_RES, locale, name, "only in Android res")
            }
        }
        return findings
    }

    private fun tokens(v: Value?): Set<Pair<String, String>> = when (v) {
        null -> emptySet()
        is Value.S -> token.findAll(v.text).mapTo(HashSet()) { it.groupValues[1] to it.groupValues[2] }
        is Value.P -> v.items.values.flatMapTo(HashSet()) { t -> token.findAll(t).map { it.groupValues[1] to it.groupValues[2] } }
    }

    fun localeOf(folder: String, template: String): String? {
        if (folder == "values") return template
        val m = Regex("values-([a-z]{2,3})(?:-r([A-Z]{2}))?").matchEntire(folder) ?: return null
        return if (m.groupValues[2].isEmpty()) m.groupValues[1] else "${m.groupValues[1]}_${m.groupValues[2]}"
    }

    /** One CMP resource file read the way CMP reads it (DOM, then `handleSpecialCharacters`). */
    private fun read(text: String, file: String): LinkedHashMap<String, Value> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(text)))
        val out = LinkedHashMap<String, Value>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.getAttribute("name")
            val v: Value = when (el.tagName) {
                "string" -> Value.S(XmlText.cmpUnescape(el.textContent), file)
                "plurals" -> {
                    val items = LinkedHashMap<String, String>()
                    val kids = el.getElementsByTagName("item")
                    for (j in 0 until kids.length) {
                        val item = kids.item(j) as Element
                        items[item.getAttribute("quantity")] = XmlText.cmpUnescape(item.textContent)
                    }
                    Value.P(items, file)
                }
                else -> throw IllegalArgumentException("unexpected <${el.tagName}>")
            }
            if (out.put(name, v) != null) throw IllegalArgumentException("$name twice")
        }
        return out
    }
}

/** Repository-relative locations (from the repository root). */
object Paths {
    const val ARB = "lib/l10n"
    const val DART_LIB = "lib"
    const val ANDROID_RES = "android/app/src/main/res"
    const val TOOL = "native/tools/strings"
    const val ROUTES = "$TOOL/feature-routes.tsv"
    const val HAND_RECIPES = "$TOOL/hand-recipes.tsv"
    const val UNUSED = "$TOOL/unused"
    const val REPORT = "$TOOL/report"
    const val WORDS = "native/ui/words/src/commonMain/composeResources"
    const val WORDS_ANDROID_RES = "native/ui/words/src/androidMain/res"
    const val HAND_WORDS_KT = "native/ui/words/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/words/HandWords.kt"
}
