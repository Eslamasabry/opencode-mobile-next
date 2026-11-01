package io.github.eslamasabry.oc.tools.strings

import io.github.eslamasabry.oc.tools.strings.IcuNode.Arg
import io.github.eslamasabry.oc.tools.strings.IcuNode.Plural
import io.github.eslamasabry.oc.tools.strings.IcuNode.Select
import java.nio.file.Path
import kotlin.io.path.readLines

/**
 * The hand conversions: messages CMP XML cannot hold as they are (an ICU `select`, several plurals in one
 * message). Each is listed in `hand-recipes.tsv` with the rewrite the l10n owner chose, and [Hand.expand] applies
 * it to every language **before** conversion, as plain ICU messages the converter already handles. So Arabic still
 * gets its six categories, every `=N` still gets its `__eqN` string, escaping and placeholder numbering stay the
 * converter's, and the drift check can redo it. A refused message without a recipe still fails the run.
 *
 * - `select` (plan 01 §7.5: "the ICU select messages become code"): `{arg, select, a{…} b{…} other{…}}` becomes
 *   one message `<key>__a`, `<key>__b`, `<key>__other` per branch of any language, with the text around the
 *   select copied in. A language without a branch gets its own `other`, which is what ICU shows. The select
 *   argument stops being a format argument; code picks the string with an exhaustive `when` (`HandWords`).
 * - `split-plurals` (plan 06 §13.3 step 4): every plural `{n, plural, …}` becomes its own message `<key>__n`
 *   (the plural alone), and `<key>` keeps the sentence with `{n__plural}` where the plural was. A language that
 *   writes a count as a plain number keeps `{n}` in its sentence and gets no piece. Code fills the sentence with
 *   the pieces (`HandWords`).
 */
enum class RecipeKind(val id: String) { SELECT("select"), SPLIT_PLURALS("split-plurals") }

data class Recipe(val key: String, val kind: RecipeKind, val note: String)

class HandRecipeException(message: String) : RuntimeException(message)

object HandRecipes {
    fun parse(lines: List<String>, source: String = "hand-recipes"): List<Recipe> =
        lines.mapIndexedNotNull { n, raw ->
            val line = raw.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) return@mapIndexedNotNull null
            val cols = line.split('\t')
            require(cols.size >= 2) { "$source:${n + 1}: expected <key>\\t<recipe>\\t<note>" }
            val kind = RecipeKind.entries.firstOrNull { it.id == cols[1].trim() }
            requireNotNull(kind) { "$source:${n + 1}: unknown recipe \"${cols[1]}\" (${RecipeKind.entries.joinToString { it.id }})" }
            Recipe(cols[0].trim(), kind, cols.drop(2).joinToString(" ").trim())
        }.also { rs ->
            val dup = rs.groupBy { it.key }.filterValues { it.size > 1 }.keys
            require(dup.isEmpty()) { "$source: duplicate keys $dup" }
        }

    fun read(file: Path) = parse(file.readLines(), file.toString())
}

/** One hand-converted key: the languages it was converted for and the messages it became. */
data class HandConverted(
    val key: String,
    val recipe: Recipe,
    val locales: List<String>,
    /** Synthetic message names, in order (`<key>__branch` or `<key>` + `<key>__arg`). */
    val names: List<String>,
    /** For a select: the argument and its branches. For a split: the plural arguments, in English order. */
    val selectArg: String?,
    val branches: List<String>,
    val pluralArgs: List<String>,
    /** The template message's arguments (name → ARB type), in English order of first appearance. */
    val params: List<Pair<String, String?>>,
    /** locale → the original ICU message. */
    val sources: Map<String, String> = emptyMap(),
)

data class Expansion(
    /** The ARB files with every recipe applied (synthetic keys at the base key's place). */
    val arbs: List<ArbFile>,
    /** synthetic message name → the ARB key it came from. */
    val baseOf: Map<String, String>,
    val converted: List<HandConverted>,
    /** locale → split pieces that language's sentence does not use (it writes the count as a number). */
    val unreferencedPieces: Map<String, Set<String>>,
) {
    /** The ARB key a resource name comes from: `x__eq1` → `x`, `x__yes` → `x`, `x__n__eq0` → `x`. */
    fun baseKey(name: String): String {
        val noEq = name.replace(EQ, "")
        return baseOf[noEq] ?: noEq
    }

    companion object {
        private val EQ = Regex("__eq\\d+$")
        fun none(arbs: List<ArbFile>) = Expansion(arbs, emptyMap(), emptyList(), emptyMap())
    }
}

object Hand {
    const val PIECE_SUFFIX = "__plural"

    fun expand(arbs: List<ArbFile>, recipes: List<Recipe>, template: String = "en"): Expansion {
        val en = arbs.firstOrNull { it.locale == template } ?: throw HandRecipeException("template $template missing")
        val byKey = recipes.associateBy { it.key }
        for (r in recipes) if (r.key !in en.messages) throw HandRecipeException("recipe for ${r.key}: no such key in app_$template.arb")

        // key → locale → replacement messages (name → ICU text), and key → template placeholder metadata per name.
        val replacement = HashMap<String, Map<String, LinkedHashMap<String, String>>>()
        val meta = HashMap<String, Map<String, LinkedHashMap<String, PlaceholderMeta>>>()
        val converted = mutableListOf<HandConverted>()
        val unreferenced = HashMap<String, MutableSet<String>>()
        val baseOf = HashMap<String, String>()
        val fixedOrder = HashMap<String, List<String>>()

        for (r in recipes) {
            val parsed = LinkedHashMap<String, List<IcuNode>>()
            for (arb in arbs) {
                val text = arb.messages[r.key] ?: continue
                parsed[arb.locale] = try {
                    Icu.parse(text)
                } catch (e: IcuParseException) {
                    throw HandRecipeException("${r.key} [${arb.locale}]: ${e.message}")
                }
            }
            val baseMeta = en.placeholders[r.key].orEmpty()
            val out = when (r.kind) {
                RecipeKind.SELECT -> select(r, parsed, template, baseMeta)
                RecipeKind.SPLIT_PLURALS -> split(r, parsed, template, baseMeta, unreferenced)
            }
            replacement[r.key] = out.messages
            meta[r.key] = out.meta
            for ((name, m) in out.meta) fixedOrder[name] = m.keys.toList()
            out.names.forEach { baseOf[it] = r.key }
            val enArgs = (Icu.argOrder(parsed.getValue(template)) + baseMeta.keys).distinct()
            converted += HandConverted(
                r.key, r, parsed.keys.toList(), out.names, out.selectArg, out.branches, out.pluralArgs,
                enArgs.map { it to baseMeta[it]?.type },
                arbs.mapNotNull { a -> a.messages[r.key]?.let { a.locale to it } }.toMap(),
            )
        }

        val expanded = arbs.map { arb ->
            val messages = LinkedHashMap<String, String>()
            for ((k, v) in arb.messages) {
                val rep = if (k in byKey) replacement.getValue(k)[arb.locale] else null
                if (rep == null) messages[k] = v else messages.putAll(rep)
            }
            val ph = HashMap(arb.placeholders)
            if (arb === en) for ((k, m) in meta) { ph.remove(k); ph.putAll(m) }
            ArbFile(arb.locale, messages, ph, if (arb === en) fixedOrder else emptyMap())
        }
        return Expansion(expanded, baseOf, converted, unreferenced)
    }

    private class Out(
        val names: List<String>,
        val messages: Map<String, LinkedHashMap<String, String>>,
        val meta: Map<String, LinkedHashMap<String, PlaceholderMeta>>,
        val selectArg: String? = null,
        val branches: List<String> = emptyList(),
        val pluralArgs: List<String> = emptyList(),
    )

    private fun hasNested(nodes: List<IcuNode>): Boolean = nodes.any { n ->
        when (n) {
            is Plural -> n.branches.values.any { b -> b.any { it is Plural || it is Select } || hasNested(b) }
            is Select -> n.branches.values.any { b -> b.any { it is Plural || it is Select } || hasNested(b) }
            else -> false
        }
    }

    private fun select(
        r: Recipe,
        parsed: Map<String, List<IcuNode>>,
        template: String,
        baseMeta: Map<String, PlaceholderMeta>,
    ): Out {
        fun fail(why: String): Nothing = throw HandRecipeException("${r.key}: recipe select: $why")
        val enNodes = parsed[template] ?: fail("no template message")
        val enSel = enNodes.filterIsInstance<Select>().singleOrNull() ?: fail("the template has no single select")
        if (enNodes.any { it is Plural }) fail("the template also has a plural")
        if ("other" !in enSel.branches) fail("the template select has no `other`")
        val branches = LinkedHashSet(enSel.branches.keys)
        for ((locale, nodes) in parsed) {
            if (hasNested(nodes)) fail("[$locale] has a plural or select inside a branch")
            val sels = nodes.filterIsInstance<Select>()
            if (sels.size > 1 || nodes.any { it is Plural }) fail("[$locale] has more than one select, or a plural")
            val s = sels.singleOrNull() ?: continue
            if (s.arg != enSel.arg) fail("[$locale] selects on {${s.arg}}, the template on {${enSel.arg}}")
            if ("other" !in s.branches) fail("[$locale] has no `other`")
            branches += s.branches.keys
        }
        val names = branches.map { "${r.key}__$it" }
        val messages = parsed.mapValues { (_, nodes) ->
            val i = nodes.indexOfFirst { it is Select }
            LinkedHashMap<String, String>().apply {
                for ((b, name) in branches.zip(names)) {
                    put(
                        name,
                        if (i < 0) Icu.print(nodes)
                        else {
                            val s = nodes[i] as Select
                            Icu.print(nodes.subList(0, i) + (s.branches[b] ?: s.branches.getValue("other")) + nodes.subList(i + 1, nodes.size))
                        },
                    )
                }
            }
        }
        // The select argument is no longer substituted, unless a branch also prints it.
        val printed = parsed.values.flatMap { nodes -> Icu.argOrder(nodes.flatMap { n -> if (n is Select) n.branches.values.flatten() else listOf(n) }) }
        val keep = { a: String -> a != enSel.arg || a in printed }
        val order = (Icu.argOrder(enNodes) + baseMeta.keys).distinct().filter(keep)
        val m = order.associateWithTo(LinkedHashMap()) { baseMeta[it] ?: PlaceholderMeta(null, null) }
        return Out(names, messages, names.associateWith { m }, enSel.arg, branches.toList())
    }

    private fun split(
        r: Recipe,
        parsed: Map<String, List<IcuNode>>,
        template: String,
        baseMeta: Map<String, PlaceholderMeta>,
        unreferenced: MutableMap<String, MutableSet<String>>,
    ): Out {
        fun fail(why: String): Nothing = throw HandRecipeException("${r.key}: recipe split-plurals: $why")
        val enNodes = parsed[template] ?: fail("no template message")
        val enPlurals = enNodes.filterIsInstance<Plural>()
        if (enPlurals.size < 2) fail("the template has ${enPlurals.size} plural(s); the recipe needs two or more")
        val args = enPlurals.map { it.arg }
        if (args.toSet().size != args.size) fail("the template pluralises one argument twice")
        val frameName = r.key
        val pieceNames = args.associateWith { "${r.key}__$it" }
        val frameMeta = LinkedHashMap<String, PlaceholderMeta>()
        val messages = LinkedHashMap<String, LinkedHashMap<String, String>>()
        val frames = LinkedHashMap<String, List<IcuNode>>()
        for ((locale, nodes) in parsed) {
            if (nodes.any { it is Select } || hasNested(nodes)) fail("[$locale] has a select or a nested plural")
            val own = nodes.filterIsInstance<Plural>()
            val unknown = own.map { it.arg }.filter { it !in args }
            if (unknown.isNotEmpty()) fail("[$locale] pluralises $unknown, which the template does not")
            val frame = nodes.map { if (it is Plural) Arg(it.arg + PIECE_SUFFIX) else it }
            frames[locale] = frame
            val msgs = LinkedHashMap<String, String>()
            msgs[frameName] = Icu.print(frame)
            for (p in own) msgs[pieceNames.getValue(p.arg)] = Icu.print(listOf(p))
            val missing = args.filter { a -> own.none { it.arg == a } }
            if (missing.isNotEmpty()) unreferenced.getOrPut(locale) { sortedSetOf() } += missing.map { pieceNames.getValue(it) }
            messages[locale] = msgs
        }
        // Frame placeholders: English order of appearance, then anything another language's frame adds.
        val frameArgs = LinkedHashSet(Icu.argOrder(frames.getValue(template)))
        frames.values.forEach { frameArgs += Icu.argOrder(it) }
        for (a in frameArgs) {
            frameMeta[a] = if (a.endsWith(PIECE_SUFFIX)) PlaceholderMeta("String", null)
            else baseMeta[a] ?: fail("{$a} has no @${r.key} placeholder metadata")
        }
        val meta = LinkedHashMap<String, LinkedHashMap<String, PlaceholderMeta>>()
        meta[frameName] = frameMeta
        for ((a, name) in pieceNames) {
            val p = enPlurals.first { it.arg == a }
            val used = LinkedHashSet<String>().apply { add(a); addAll(Icu.argOrder(listOf(p))) }
            parsed.values.forEach { nodes -> nodes.filterIsInstance<Plural>().filter { it.arg == a }.forEach { used += Icu.argOrder(listOf(it)) } }
            meta[name] = used.associateWithTo(LinkedHashMap()) { baseMeta[it] ?: fail("{$it} has no @${r.key} placeholder metadata") }
        }
        // Per-language order must be the template's: synthetic names in template order.
        val order = listOf(frameName) + pieceNames.values
        val ordered = messages.mapValues { (_, m) -> order.filter { it in m }.associateWithTo(LinkedHashMap()) { m.getValue(it) } }
        return Out(order, ordered, meta, pluralArgs = args)
    }
}
