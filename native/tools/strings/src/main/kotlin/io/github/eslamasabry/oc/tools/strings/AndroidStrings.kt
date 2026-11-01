package io.github.eslamasabry.oc.tools.strings

import org.w3c.dom.Element
import java.io.StringReader
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import org.xml.sax.InputSource

/**
 * The Flutter app's native Android strings (`android/app/src/main/res/values{,-ar}/ files`: `strings.xml`,
 * `widget_v2_strings.xml`, `widget_v2_bridge_strings.xml`, `scheduled_prompts.xml`, `bb_idle_strings.xml`), read
 * the way aapt2 reads them, into the converter's plain-text form so they can join `strings_os.xml`
 * (plan 06 §13.3 step 7). Names are kept.
 *
 * The plain-text form is what CMP shows: `%N$s`/`%N$d` are the only tokens, and any other `%` is literal. So a
 * formatted Android string's `%%` becomes `%`, and a non-positional `%d`/`%s` becomes `%1$d`, `%2$s`… in order —
 * the same output from `String.format` with the same arguments. The Android res copy turns it back
 * ([XmlWriter.renderAndroidRes]).
 */
object AndroidStrings {
    private val langFolder = Regex("values-([a-z]{2,3})")
    private val spec = Regex("""%(?:(\d+)\$)?([ds%])""")

    /** locale → resources, for `values` (the template) and every `values-<lang>` folder under [resDir]. */
    fun readDir(resDir: Path, template: String = "en"): Map<String, List<Resource>> {
        val out = LinkedHashMap<String, List<Resource>>()
        val folders = resDir.listDirectoryEntries("values*").filter { it.isDirectory() }.sortedBy { it.name }
        for (dir in folders) {
            val locale = if (dir.name == "values") template else langFolder.matchEntire(dir.name)?.groupValues?.get(1) ?: continue
            val res = dir.listDirectoryEntries("*.xml").sortedBy { it.name }.flatMap { parse(it.readText(), it.toString()) }
            if (res.isEmpty()) continue
            out[locale] = res.map { r ->
                if (r !is Resource.Plurals || !PluralRules.isKnown(locale)) return@map r
                // Every category of the language; one the file leaves out reads `other`, as Android does.
                val other = r.items[PluralRules.OTHER] ?: throw IllegalArgumentException("$dir: <plurals ${r.name}> has no `other`")
                Resource.Plurals(r.name, r.key, PluralRules.categories(locale).associateWithTo(LinkedHashMap()) { r.items[it] ?: other })
            }
        }
        return out
    }

    /** `<string>` and `<plurals>` of one resource file, in document order; other elements are skipped. */
    fun parse(xml: String, source: String): List<Resource> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val nodes = doc.documentElement.childNodes
        val out = mutableListOf<Resource>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.getAttribute("name")
            val formatted = el.getAttribute("formatted") != "false"
            when (el.tagName) {
                "string" -> out += Resource.Str(name, name, decode(el.textContent, formatted))
                "plurals" -> {
                    val raw = LinkedHashMap<String, String>()
                    val kids = el.getElementsByTagName("item")
                    for (j in 0 until kids.length) {
                        val item = kids.item(j) as Element
                        raw[item.getAttribute("quantity")] = item.textContent
                    }
                    // getQuantityString formats every item when given arguments: one item's tokens decide.
                    val args = formatted && raw.values.any { v -> spec.findAll(v).any { it.groupValues[2] != "%" } }
                    val items = raw.mapValuesTo(LinkedHashMap()) { (_, v) -> decode(v, formatted, force = args) }
                    out += Resource.Plurals(name, name, items)
                }
                "string-array" -> throw IllegalArgumentException("$source: <string-array $name> is not supported by CMP")
            }
        }
        return out
    }

    /** aapt2's reading of one value ([raw] is the DOM text), then the format tokens in CMP form. */
    fun decode(raw: String, formatted: Boolean, force: Boolean = false): String {
        // Characters from escapes or quotes are "kept": never collapsed or trimmed.
        val text = StringBuilder()
        val kept = mutableListOf<Boolean>()
        fun add(c: Char, keep: Boolean) { text.append(c); kept += keep }
        var quoted = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> {
                    val n = raw[i + 1]
                    when (n) {
                        'n' -> add('\n', true)
                        't' -> add('\t', true)
                        'u' -> {
                            val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                            require(hex.length == 4 && hex.all { it.isLetterOrDigit() }) { "bad \\u escape in: $raw" }
                            add(hex.toInt(16).toChar(), true)
                            i += 4
                        }
                        else -> add(n, true)
                    }
                    i += 2
                    continue
                }
                c == '"' -> quoted = !quoted
                quoted -> add(c, true)
                c.isWhitespace() -> if (text.isEmpty() || kept.last() || !text.last().isWhitespace()) add(' ', false)
                else -> add(c, false)
            }
            i++
        }
        var start = 0
        var end = text.length
        while (start < end && !kept[start] && text[start] == ' ') start++
        while (end > start && !kept[end - 1] && text[end - 1] == ' ') end--
        val s = text.substring(start, end)
        if (!formatted || (!force && spec.findAll(s).none { it.groupValues[2] != "%" })) return s
        var next = 0
        return spec.replace(s) { m ->
            val (index, kind) = m.destructured
            when {
                kind == "%" -> "%"
                index.isNotEmpty() -> m.value
                else -> "%${++next}\$$kind"
            }
        }
    }

    /** The plain-text form back to an Android value (before XML escaping): a literal `%` doubles when [formatted]. */
    fun encodePercent(text: String, formatted: Boolean): String {
        val tokens = Regex("""%\d+\$[ds]""")
        if (!formatted) return text
        val sb = StringBuilder()
        var last = 0
        for (m in tokens.findAll(text)) {
            sb.append(text.substring(last, m.range.first).replace("%", "%%")).append(m.value)
            last = m.range.last + 1
        }
        return sb.append(text.substring(last).replace("%", "%%")).toString()
    }
}
