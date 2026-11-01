package io.github.eslamasabry.oc.tools.strings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText

/** `feature-routes.tsv`: key-prefix rows and path-prefix rows (see the file's header). */
class Routes(
    val keyPrefixes: List<Pair<String, String>>,
    val pathPrefixes: List<Pair<String, String>>,
) {
    /** Feature for a key by name, for keys like `team…`/`fb…` that the plan routes by prefix. */
    fun featureForKey(key: String): String? = keyPrefixes.firstOrNull { (prefix, _) ->
        key.startsWith(prefix) && (key.length == prefix.length || key[prefix.length].let { it.isUpperCase() || it.isDigit() })
    }?.second

    /** Feature for a repo-relative Dart path (`lib/ui/…`), by the longest matching prefix. */
    fun featureForPath(path: String): String? =
        pathPrefixes.filter { path.startsWith(it.first) }.maxByOrNull { it.first.length }?.second

    companion object {
        private val featureName = Regex("[a-z][a-z0-9_]*")

        fun parse(lines: List<String>, source: String = "routes"): Routes {
            val keys = mutableListOf<Pair<String, String>>()
            val paths = mutableListOf<Pair<String, String>>()
            lines.forEachIndexed { n, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val cols = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
                require(cols.size == 2) { "$source:${n + 1}: expected <prefix>\\t<feature>, got \"$raw\"" }
                val (prefix, feature) = cols
                require(featureName.matches(feature)) { "$source:${n + 1}: bad feature name \"$feature\"" }
                if (prefix.startsWith("key:")) keys += prefix.removePrefix("key:") to feature else paths += prefix to feature
            }
            val dup = paths.groupBy { it.first }.filterValues { it.size > 1 }.keys
            require(dup.isEmpty()) { "$source: duplicate path prefixes $dup" }
            return Routes(keys, paths)
        }

        fun read(file: Path) = parse(file.readLines(), file.toString())
    }
}

/** Where each template key goes. */
data class Routing(
    /** key → feature, for keys that are converted. */
    val featureByKey: Map<String, String>,
    /** Keys read by no Dart file and matched by no key prefix: not converted, listed for the owner. */
    val unused: Set<String>,
    /** key → the Dart files that read it (repo-relative), for the report. */
    val usage: Map<String, Set<String>>,
)

class UnroutedPathsException(val paths: Map<String, Int>) :
    RuntimeException(
        "These Dart files read l10n keys but match no row in the routes file (key count in brackets):\n" +
            paths.entries.joinToString("\n") { "  ${it.key} (${it.value})" },
    )

object Router {
    private val importRegex = Regex("""import\s+['"][^'"]*app_localizations\.dart['"]""")
    private val partOfRegex = Regex("""^\s*part\s+of\s+'([^']+)'\s*;""", RegexOption.MULTILINE)
    private val memberRegex = Regex("""\.([A-Za-z_][A-Za-z0-9_]*)\b""")

    /**
     * Plan 06 §13.3 step 2. Dart files that read keys are the files that import `app_localizations.dart` plus the
     * `part` files of those libraries (a part file inherits its library's imports). A key is "read" by a file when
     * `.<key>` appears in it as a whole identifier. [libDir] is the Flutter `lib/` folder; generated `lib/l10n/`
     * is skipped.
     */
    fun route(keys: Collection<String>, libDir: Path, routes: Routes): Routing {
        val usage = scanUsage(keys.toSet(), libDir)
        val unrouted = sortedMapOf<String, Int>()
        val featureByKey = LinkedHashMap<String, String>()
        val unused = LinkedHashSet<String>()
        val filesPerKeyCount = HashMap<String, Int>()
        for ((_, files) in usage) for (f in files) filesPerKeyCount.merge(f, 1, Int::plus)
        for (f in filesPerKeyCount.keys) if (routes.featureForPath(f) == null) unrouted[f] = filesPerKeyCount.getValue(f)
        if (unrouted.isNotEmpty()) throw UnroutedPathsException(unrouted)
        for (key in keys) {
            val byPrefix = routes.featureForKey(key)
            if (byPrefix != null) { featureByKey[key] = byPrefix; continue }
            val features = usage[key].orEmpty().map { routes.featureForPath(it)!! }.toSortedSet()
            when (features.size) {
                0 -> unused += key
                1 -> featureByKey[key] = features.first()
                else -> featureByKey[key] = "common"
            }
        }
        return Routing(featureByKey, unused, usage)
    }

    fun scanUsage(keys: Set<String>, libDir: Path): Map<String, Set<String>> {
        val root = libDir.toAbsolutePath().normalize()
        val base = root.parent
        val dart = Files.walk(root).use { s ->
            s.filter { it.isRegularFile() && it.extension == "dart" }
                .filter { !it.startsWith(root.resolve("l10n")) }
                .toList()
        }
        val text = dart.associateWith { it.readText() }
        val importers = text.filterValues { importRegex.containsMatchIn(it) }.keys
        val readers = importers.toMutableSet()
        for ((file, src) in text) {
            val parent = partOfRegex.find(src)?.groupValues?.get(1) ?: continue
            if (file.parent.resolve(parent).normalize() in importers) readers.add(file)
        }
        val usage = HashMap<String, MutableSet<String>>()
        for (file in readers) {
            val rel = base.relativize(file).invariantSeparatorsPathString
            for (m in memberRegex.findAll(text.getValue(file))) {
                val id = m.groupValues[1]
                if (id in keys) usage.getOrPut(id) { sortedSetOf() }.add(rel)
            }
        }
        return usage
    }
}
