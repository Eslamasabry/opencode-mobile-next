package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

object XmlWriter {
    private val formatToken = Regex("""%\d+\$[ds]""")

    /** `en` → `values`, `ar` → `values-ar`, `pt_BR` → `values-pt-rBR` (CMP/Android qualifier syntax). */
    fun folder(locale: String, template: String): String {
        if (locale == template) return "values"
        val parts = locale.split('_', '-')
        return when (parts.size) {
            1 -> "values-${parts[0]}"
            2 -> if (parts[1].length == 2) "values-${parts[0]}-r${parts[1].uppercase()}" else error("Unsupported locale $locale")
            else -> error("Unsupported locale $locale")
        }
    }

    fun profileFor(feature: String) = if (feature == "os") XmlText.Profile.ANDROID else XmlText.Profile.CMP

    fun render(resources: List<Resource>, feature: String, sourceName: String): String {
        val profile = profileFor(feature)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<!-- Converted once from lib/l10n/$sourceName by native/tools/strings (plan 06 §13.3).\n")
        sb.append("     The ARB files are frozen with Flutter: edit this file, not the ARB. -->\n")
        sb.append("<resources>\n")
        for (r in resources) when (r) {
            is Resource.Str -> {
                val attrs = if (profile == XmlText.Profile.ANDROID && '%' in r.text && !formatToken.containsMatchIn(r.text)) {
                    " formatted=\"false\""
                } else ""
                sb.append("    <string name=\"${r.name}\"$attrs>${XmlText.escape(r.text, profile)}</string>\n")
            }
            is Resource.Plurals -> {
                sb.append("    <plurals name=\"${r.name}\">\n")
                for ((q, text) in r.items) sb.append("        <item quantity=\"$q\">${XmlText.escape(text, profile)}</item>\n")
                sb.append("    </plurals>\n")
            }
        }
        sb.append("</resources>\n")
        return sb.toString()
    }

    /**
     * Writes `values[-xx]/strings_<feature>.xml` under [out] and the unused group as `values[-xx]/strings_unused.xml`
     * under [unusedOut] (outside composeResources, so it never ships). Old `strings_*.xml` there are removed first.
     */
    fun write(conversion: Conversion, out: Path, unusedOut: Path?) {
        clean(out); unusedOut?.let(::clean)
        for (loc in conversion.locales) {
            val folder = folder(loc.locale, conversion.template)
            for ((feature, resources) in loc.files) {
                if (resources.isEmpty()) continue
                val root = if (feature == LocaleResult.UNUSED) unusedOut ?: continue else out
                val dir = root.resolve(folder).createDirectories()
                dir.resolve("strings_$feature.xml").writeText(render(resources, feature, "app_${loc.locale}.arb"))
            }
        }
    }

    private fun clean(root: Path) {
        if (!root.isDirectory()) return
        for (dir in root.listDirectoryEntries("values*").filter { it.isDirectory() }) {
            dir.listDirectoryEntries("strings_*.xml").forEach { it.deleteIfExists() }
            if (dir.listDirectoryEntries().isEmpty()) Files.delete(dir)
        }
    }
}

object ReportWriter {
    fun write(conversion: Conversion, routing: Routing?, dir: Path, skippedProblems: Boolean) {
        dir.createDirectories()
        dir.listDirectoryEntries().filter { !it.isDirectory() }.forEach { it.deleteIfExists() }
        dir.resolve("convert-report.md").writeText(markdown(conversion, routing, skippedProblems))
        for (loc in conversion.locales) {
            if (loc.missing.isNotEmpty()) dir.resolve("missing-${loc.locale}.txt").writeText(loc.missing.joinToString("\n", postfix = "\n"))
            if (loc.unreachableBranches.isNotEmpty()) {
                dir.resolve("unreachable-${loc.locale}.txt").writeText(loc.unreachableBranches.joinToString("\n", postfix = "\n"))
            }
            if (loc.orphans.isNotEmpty()) dir.resolve("orphans-${loc.locale}.txt").writeText(loc.orphans.joinToString("\n", postfix = "\n"))
        }
        dir.resolve("unsupported.tsv").writeText(
            "locale\tkey\tkind\tdetail\n" +
                conversion.problems.joinToString("") { "${it.locale}\t${it.key}\t${it.kind}\t${it.detail.replace('\t', ' ').replace('\n', ' ')}\n" },
        )
        dir.resolve("placeholders.tsv").writeText(
            "key\tplural_arg\targs (index:name:type:format)\n" +
                conversion.plans.values.filter { it.placeholders.isNotEmpty() }.joinToString("") { p ->
                    "${p.key}\t${p.pluralArg ?: ""}\t" +
                        p.placeholders.joinToString(" ") { "${it.index}:${it.name}:${it.type ?: "-"}:${it.format ?: "-"}" } + "\n"
                },
        )
        dir.resolve("exact-selectors.tsv").writeText(
            "key\tN (each has <key>__eqN in every language that has the key)\n" +
                conversion.plans.values.filter { it.exact.isNotEmpty() }.joinToString("") { "${it.key}\t${it.exact.joinToString(",")}\n" },
        )
        if (routing != null) {
            dir.resolve("key-routes.tsv").writeText(
                "key\tfeature\treaders\tfirst reader\n" +
                    conversion.plans.values.joinToString("") { p ->
                        val readers = routing.usage[p.key].orEmpty()
                        "${p.key}\t${p.feature ?: LocaleResult.UNUSED}\t${readers.size}\t${readers.firstOrNull() ?: ""}\n"
                    },
            )
        }
    }

    fun markdown(c: Conversion, routing: Routing?, skippedProblems: Boolean): String = buildString {
        val en = c[c.template]
        appendLine("# ARB → CMP conversion report")
        appendLine()
        appendLine("Generated by `native/tools/strings`. Template: `${c.template}`.")
        if (skippedProblems) appendLine("Run with `--skip-unsupported`: the ${c.problems.size} messages below were NOT converted.")
        appendLine()
        appendLine("## Keys per language")
        appendLine()
        appendLine("`in = converted + unused + not convertible + orphans` must hold for every row.")
        appendLine()
        appendLine("| lang | in (ARB) | converted | unused | not convertible | orphans dropped | in = out | missing vs ${c.template} | `<string>` | `<plurals>` | `__eqN` | promoted to plurals |")
        appendLine("|---|---:|---:|---:|---:|---:|:---:|---:|---:|---:|---:|---:|")
        for (l in c.locales) {
            val conv = l.convertedKeys(includeUnused = false).size
            val unused = l.files[LocaleResult.UNUSED].orEmpty().map { it.key }.toSet().size
            val sum = conv + unused + l.skipped.size + l.orphans.size
            val all = l.files.values.flatten()
            val eq = all.count { it is Resource.Str && it.name != it.key }
            val plurals = all.count { it is Resource.Plurals }
            val strings = all.count { it is Resource.Str } - eq
            appendLine(
                "| ${l.locale} | ${l.keysIn} | $conv | $unused | ${l.skipped.size} | ${l.orphans.size} | " +
                    "${if (sum == l.keysIn) "yes" else "**NO ($sum)**"} | ${l.missing.size} | $strings | $plurals | $eq | ${l.promoted.size} |",
            )
        }
        appendLine()
        appendLine("Missing keys fall back to `values/` at runtime. Lists: `missing-<lang>.txt`; dropped orphans: `orphans-<lang>.txt`.")
        appendLine()

        val features = c.locales.flatMap { it.files.keys }.toSortedSet()
        appendLine("## Keys per feature file")
        appendLine()
        appendLine("| file | " + c.locales.joinToString(" | ") { it.locale } + " |")
        appendLine("|---|" + c.locales.joinToString("") { "---:|" })
        for (f in features) {
            val name = if (f == LocaleResult.UNUSED) "strings_unused.xml (not shipped)" else "strings_$f.xml"
            appendLine("| $name | " + c.locales.joinToString(" | ") { l -> l.files[f].orEmpty().map { it.key }.toSet().size.toString() } + " |")
        }
        appendLine()

        if (routing != null) {
            appendLine("## Routing")
            appendLine()
            val byPrefix = c.plans.values.count { p -> routing.usage[p.key].isNullOrEmpty() && p.feature != null }
            appendLine("- Template keys: ${en.keysIn}. Read by Dart code: ${routing.usage.size}. Routed by key prefix only: $byPrefix.")
            appendLine("- Unused (read by no Dart file): ${routing.unused.size}, written to `strings_unused.xml` outside composeResources for the owner to confirm.")
            appendLine("- Per-key routes and readers: `key-routes.tsv`.")
            appendLine()
        }

        appendLine("## Not convertible (${c.problems.size} messages)")
        appendLine()
        if (c.problems.isEmpty()) appendLine("None.")
        for ((kind, ps) in c.problems.groupBy { it.kind }) {
            appendLine("### ${kind.name}: ${kind.label} (${ps.size})")
            appendLine()
            for ((key, kps) in ps.groupBy { it.key }) {
                val own = kps.firstOrNull { it.detail != "template message is not convertible" } ?: kps.first()
                appendLine("- `$key` [${kps.joinToString(",") { it.locale }}]: ${own.detail.take(160)}")
            }
            appendLine()
        }

        val decimal = c.plans.values.filter { p -> p.placeholders.any { it.format != null } }
        appendLine("## Formatted numbers (${decimal.size} keys)")
        appendLine()
        appendLine("These placeholders had an ARB `format` (e.g. `decimalPattern`) and became `%N\$s`: call sites must pass a")
        appendLine("number already formatted for the locale. Keys: " + decimal.joinToString(", ") { "`${it.key}`" })
        appendLine()
        appendLine("## Exact plural selectors")
        appendLine()
        appendLine("${c.plans.values.count { it.exact.isNotEmpty() }} keys use `=N`; each gets `<key>__eqN` in every language (`exact-selectors.tsv`).")
        appendLine()
        val unreachable = c.locales.filter { it.unreachableBranches.isNotEmpty() }
        if (unreachable.isNotEmpty()) {
            appendLine("## Plural branches no rule can select (dropped)")
            appendLine()
            appendLine("ICU branches for a category the language does not have (for example `one` in zh). ICU never picks them")
            appendLine("either, so nothing changes on screen. Lists: `unreachable-<lang>.txt`.")
            appendLine()
            for (l in unreachable) appendLine("- ${l.locale}: ${l.unreachableBranches.size}")
            appendLine()
        }
    }
}
