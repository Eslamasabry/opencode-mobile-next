package io.github.eslamasabry.oc.tools.strings

import io.github.eslamasabry.oc.tools.strings.IcuNode.Arg
import io.github.eslamasabry.oc.tools.strings.IcuNode.Plural
import io.github.eslamasabry.oc.tools.strings.IcuNode.Select
import io.github.eslamasabry.oc.tools.strings.IcuNode.Text
import java.util.SortedSet

/** A positional CMP argument: ARB `{name}` becomes `%index$spec`. */
data class Placeholder(val name: String, val index: Int, val spec: Char, val type: String?, val format: String?) {
    val token get() = "%$index\$$spec"
}

sealed interface Resource {
    /** Resource name in the XML (the ARB key, or `<key>__eq<N>` for an exact plural selector). */
    val name: String
    /** The ARB key it came from. */
    val key: String

    data class Str(override val name: String, override val key: String, val text: String) : Resource
    data class Plurals(override val name: String, override val key: String, val items: LinkedHashMap<String, String>) : Resource
}

enum class ProblemKind(val label: String) {
    SELECT("ICU select (becomes code: one string per branch, chosen by a `when`)"),
    NESTED_PLURAL("plural or select inside a plural/select branch"),
    MULTIPLE_PLURALS("several plurals in one message (split into separate resources by hand)"),
    PARSE_ERROR("not parseable as ICU"),
    UNKNOWN_PLACEHOLDER("translation uses a placeholder the template does not have"),
    PLURAL_ARG_MISMATCH("translation pluralises a different argument than the other languages"),
    BAD_SELECTOR("plural selector that is neither a CLDR category nor =N"),
    PLURAL_WITHOUT_OTHER("plural without `other` and a category left uncovered"),
    LITERAL_FORMAT_TOKEN("text already contains a %N\$s / %N\$d token that CMP would substitute"),
    OS_PERCENT_CONFLICT("strings_os value mixes a literal % with placeholders (Android would need %%, CMP would show it)"),
}

/** A message the converter cannot turn into CMP XML. Listed loudly; the run fails unless told to skip them. */
data class Problem(val locale: String, val key: String, val kind: ProblemKind, val detail: String)

/** Per-key facts shared by every language. */
data class KeyPlan(
    val key: String,
    val feature: String?,
    val placeholders: List<Placeholder>,
    val pluralArg: String?,
    val exact: SortedSet<Int>,
)

data class LocaleResult(
    val locale: String,
    val keysIn: Int,
    /** feature → resources, in template key order. Unused keys are under [UNUSED]. */
    val files: Map<String, List<Resource>>,
    /** Template keys this language does not have (CMP falls back to the template at runtime). */
    val missing: List<String>,
    /** Keys this language has and the template does not. Dropped. */
    val orphans: List<String>,
    /** Keys this language has that were skipped because of a [Problem]. */
    val skipped: List<String>,
    /** Keys where this language's plain text was promoted to `<plurals>` because another language pluralises. */
    val promoted: List<String>,
    /** "key: category" for branches this language's plural rules can never select (dropped). */
    val unreachableBranches: List<String>,
) {
    fun convertedKeys(includeUnused: Boolean = true): Set<String> =
        files.filterKeys { includeUnused || it != UNUSED }.values.flatten().mapTo(LinkedHashSet()) { it.key }

    companion object { const val UNUSED = "unused" }
}

data class Conversion(
    val template: String,
    val locales: List<LocaleResult>,
    val plans: Map<String, KeyPlan>,
    val problems: List<Problem>,
) {
    operator fun get(locale: String) = locales.first { it.locale == locale }
}

/**
 * ARB → CMP resources (plan 06 §13.3 steps 1, 3–6).
 *
 * [featureOf] gives the feature file for a template key, or null for an unused key (those are still converted,
 * into the [LocaleResult.UNUSED] group, so every input key can be accounted for).
 */
class Converter(
    private val featureOf: (String) -> String?,
    private val template: String = "en",
) {
    private val exactSelector = Regex("=(\\d+)")
    private val formatToken = Regex("""%\d+\$[ds]""")

    fun convert(arbs: List<ArbFile>): Conversion {
        val en = arbs.firstOrNull { it.locale == template } ?: error("template $template missing")
        val all = listOf(en) + arbs.filter { it !== en }
        all.forEach { require(PluralRules.isKnown(it.locale)) { "No plural rules for ${it.locale}" } }

        val problems = mutableListOf<Problem>()
        val skipped = all.associate { it.locale to LinkedHashSet<String>() }
        val promoted = all.associate { it.locale to mutableListOf<String>() }
        val unreachable = all.associate { it.locale to mutableListOf<String>() }
        val files = all.associate { it.locale to LinkedHashMap<String, MutableList<Resource>>() }
        val plans = LinkedHashMap<String, KeyPlan>()

        fun problem(locale: String, key: String, kind: ProblemKind, detail: String) {
            problems += Problem(locale, key, kind, detail)
            skipped.getValue(locale) += key
        }

        for ((key, enText) in en.messages) {
            // 1. Parse and classify every language's message.
            val parsed = LinkedHashMap<String, List<IcuNode>>()
            for (arb in all) {
                val text = arb.messages[key] ?: continue
                val nodes = try {
                    Icu.parse(text)
                } catch (e: IcuParseException) {
                    problem(arb.locale, key, ProblemKind.PARSE_ERROR, e.message ?: "")
                    continue
                }
                val bad = classify(nodes)
                if (bad != null) { problem(arb.locale, key, bad.first, bad.second); continue }
                val literal = texts(nodes).firstOrNull { formatToken.containsMatchIn(it) }
                if (literal != null) { problem(arb.locale, key, ProblemKind.LITERAL_FORMAT_TOKEN, literal); continue }
                parsed[arb.locale] = nodes
            }
            // When the template itself cannot be converted, every language loses the key.
            fun templateLost(): Boolean {
                if (template in parsed) return false
                val kind = problems.last { it.key == key && it.locale == template }.kind
                for (arb in all) if (key in arb.messages && key !in skipped.getValue(arb.locale)) {
                    problem(arb.locale, key, kind, "template message is not convertible")
                }
                return true
            }
            if (templateLost()) continue
            val enNodes = parsed.getValue(template)

            // 2. Placeholder index map, from first appearance in the template (then any metadata-only names).
            val meta = en.placeholders[key].orEmpty()
            val names = LinkedHashSet(Icu.argOrder(enNodes)).apply { addAll(meta.keys) }
            val pluralArgs = parsed.mapValues { (_, n) -> n.filterIsInstance<Plural>().singleOrNull()?.arg }
            val placeholders = names.mapIndexed { i, name ->
                val m = meta[name]
                val isCount = pluralArgs.values.any { it == name }
                val numeric = m?.type == "int" || (m?.type == null && isCount)
                val spec = if (numeric && m?.format == null) 'd' else 's'
                Placeholder(name, i + 1, spec, m?.type, m?.format)
            }
            val byName = placeholders.associateBy { it.name }
            for ((locale, nodes) in parsed.entries.toList()) {
                val unknown = Icu.argOrder(nodes).filter { it !in byName }
                if (unknown.isNotEmpty()) {
                    problem(locale, key, ProblemKind.UNKNOWN_PLACEHOLDER, unknown.joinToString())
                    parsed.remove(locale)
                }
            }
            if (templateLost()) continue

            // 3. Shape: plural when any language pluralises; all languages must pluralise the same argument.
            val pluralArg = pluralArgs[template] ?: parsed.keys.firstNotNullOfOrNull { pluralArgs[it] }
            if (pluralArg != null) {
                for (locale in parsed.keys.toList()) {
                    val a = pluralArgs[locale]
                    if (a != null && a != pluralArg) {
                        problem(locale, key, ProblemKind.PLURAL_ARG_MISMATCH, "pluralises {$a}, others {$pluralArg}")
                        parsed.remove(locale)
                    }
                }
            }
            if (templateLost()) continue

            // 4. Exact selectors (=N) used by any language become <key>__eqN everywhere the key exists.
            val exact = sortedSetOf<Int>()
            for ((locale, nodes) in parsed.entries.toList()) {
                val p = nodes.filterIsInstance<Plural>().singleOrNull() ?: continue
                val badSel = p.branches.keys.filter { it !in PluralRules.ALL && !exactSelector.matches(it) }
                if (badSel.isNotEmpty()) {
                    problem(locale, key, ProblemKind.BAD_SELECTOR, badSel.joinToString())
                    parsed.remove(locale); continue
                }
                p.branches.keys.mapNotNullTo(exact) { exactSelector.matchEntire(it)?.groupValues?.get(1)?.toInt() }
            }
            if (templateLost()) continue

            val feature = featureOf(key)
            plans[key] = KeyPlan(key, feature, placeholders, pluralArg, exact)

            // 5. Render each language.
            for ((locale, nodes) in parsed) {
                val out = render(locale, key, nodes, byName, pluralArg, exact, promoted, unreachable) { kind, detail ->
                    problem(locale, key, kind, detail)
                } ?: continue
                if (feature == "os" && out.any { r -> values(r).any { hasLiteralPercentAndArgs(it) } }) {
                    problem(locale, key, ProblemKind.OS_PERCENT_CONFLICT, enText)
                    if (locale == template) { parsed.remove(template); plans.remove(key); templateLost(); break }
                    continue
                }
                files.getValue(locale).getOrPut(feature ?: LocaleResult.UNUSED) { mutableListOf() } += out
            }
        }

        val results = all.map { arb ->
            LocaleResult(
                locale = arb.locale,
                keysIn = arb.messages.size,
                files = files.getValue(arb.locale).toSortedMap(),
                missing = if (arb === en) emptyList() else en.messages.keys.filter { it !in arb.messages },
                orphans = if (arb === en) emptyList() else arb.messages.keys.filter { it !in en.messages },
                skipped = skipped.getValue(arb.locale).toList(),
                promoted = promoted.getValue(arb.locale),
                unreachableBranches = unreachable.getValue(arb.locale),
            )
        }
        return Conversion(template, results, plans, problems)
    }

    /** Null when the message is convertible; otherwise why not. */
    private fun classify(nodes: List<IcuNode>): Pair<ProblemKind, String>? {
        fun nestedIn(ns: List<IcuNode>): Boolean = ns.any { it is Plural || it is Select }
        val selects = nodes.filterIsInstance<Select>()
        if (selects.isNotEmpty()) return ProblemKind.SELECT to selects.joinToString { "{${it.arg}, select: ${it.branches.keys.joinToString(" ")}}" }
        val plurals = nodes.filterIsInstance<Plural>()
        for (p in plurals) {
            if (p.branches.values.any(::nestedIn)) {
                val inner = p.branches.values.flatten().filter { it is Plural || it is Select }
                    .joinToString { if (it is Plural) "plural {${it.arg}}" else "select {${(it as Select).arg}}" }
                return ProblemKind.NESTED_PLURAL to "inside {${p.arg}, plural}: $inner"
            }
        }
        if (plurals.size > 1) return ProblemKind.MULTIPLE_PLURALS to plurals.joinToString { "{${it.arg}, plural}" }
        return null
    }

    private fun texts(nodes: List<IcuNode>): List<String> = nodes.flatMap {
        when (it) {
            is Text -> listOf(it.text)
            is Arg -> emptyList()
            is Plural -> it.branches.values.flatMap(::texts)
            is Select -> it.branches.values.flatMap(::texts)
        }
    }

    private fun flat(nodes: List<IcuNode>, byName: Map<String, Placeholder>): String = buildString {
        for (n in nodes) when (n) {
            is Text -> append(n.text)
            is Arg -> append(byName.getValue(n.name).token)
            else -> error("unexpected $n")
        }
    }

    private fun render(
        locale: String,
        key: String,
        nodes: List<IcuNode>,
        byName: Map<String, Placeholder>,
        pluralArg: String?,
        exact: Set<Int>,
        promoted: Map<String, MutableList<String>>,
        unreachable: Map<String, MutableList<String>>,
        fail: (ProblemKind, String) -> Unit,
    ): List<Resource>? {
        if (pluralArg == null) return listOf(Resource.Str(key, key, flat(nodes, byName)))

        val cats = PluralRules.categories(locale)
        val idx = nodes.indexOfFirst { it is Plural }
        if (idx < 0) {
            // Plain text in a language where others pluralise: same text for every quantity.
            promoted.getValue(locale) += key
            val text = flat(nodes, byName)
            return listOf(Resource.Plurals(key, key, cats.associateWithTo(LinkedHashMap()) { text })) +
                exact.map { Resource.Str("${key}__eq$it", key, text) }
        }
        val p = nodes[idx] as Plural
        val prefix = flat(nodes.subList(0, idx), byName)
        val suffix = flat(nodes.subList(idx + 1, nodes.size), byName)
        fun branch(sel: String) = p.branches[sel]?.let { prefix + flat(it, byName) + suffix }

        for (sel in p.branches.keys) if (sel in PluralRules.ALL && sel !in cats) unreachable.getValue(locale) += "$key: $sel"

        val items = LinkedHashMap<String, String>()
        for (c in cats) {
            val text = branch(c)
                ?: PluralRules.soleMember(locale, c)?.let { branch("=$it") }
                ?: branch(PluralRules.OTHER)
            if (text == null) { fail(ProblemKind.PLURAL_WITHOUT_OTHER, "no text for `$c`"); return null }
            items[c] = text
        }
        val eq = exact.map { n ->
            // What ICU shows for exactly n in this language: =n, else n's category, else other.
            val text = branch("=$n") ?: branch(PluralRules.category(locale, n)) ?: branch(PluralRules.OTHER)
                ?: items.getValue(PluralRules.category(locale, n))
            Resource.Str("${key}__eq$n", key, text)
        }
        return listOf(Resource.Plurals(key, key, items)) + eq
    }

    private fun values(r: Resource) = when (r) {
        is Resource.Str -> listOf(r.text)
        is Resource.Plurals -> r.items.values.toList()
    }

    private fun hasLiteralPercentAndArgs(s: String): Boolean {
        val stripped = formatToken.replace(s, "")
        return stripped.length != s.length && '%' in stripped
    }
}
