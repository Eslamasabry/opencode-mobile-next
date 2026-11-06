package io.github.eslamasabry.oc.quality

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * E00-S13 / ADR §9.9: the component freeze. The names Android stores or binds to (component class names, widget and
 * tile services, shortcut ids, the deep-link scheme, channel ids, WorkManager worker classes and unique-work names, the
 * FileProvider authority) are compared with a list frozen from the released Flutter app. Pure text and XML reading, so
 * the rule itself can be planted with fake trees (ComponentFreezePlantedTest).
 */
internal object ComponentFreeze {
    const val APP_ID = "io.github.eslamasabry.opencode_mobile"
    private const val ANDROID = "http://schemas.android.com/apk/res/android"
    private const val TOOLS = "http://schemas.android.com/tools"
    private val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")

    data class Entry(val kind: String, val name: String, val detail: String) {
        override fun toString(): String = "$kind $name"
    }

    data class Component(
        val tag: String,
        val name: String,
        val permission: String,
        val authorities: List<String>,
        val actions: Set<String>,
        val meta: Map<String, String>,
        val data: Set<String>,
    )

    /** What the shipped app declares: its manifest components, app meta-data, an XML resource lookup and Kotlin text. */
    class Shipped(
        val components: List<Component>,
        val appMeta: Set<String>,
        val xmlResource: (String) -> File?,
        val kotlin: List<Pair<String, String>>, // path to text, main (non-test) sources of the shipped modules
    )

    fun readList(file: File): List<Entry> =
        file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val cols = line.split('\t')
            require(cols.size >= 2) { "${file.name}: bad line '$line'" }
            Entry(cols[0], cols[1], cols.getOrElse(2) { "" })
        }

    // ---- reading -----------------------------------------------------------------------------------------------

    private fun parse(file: File): Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement

    private fun Element.kids(tag: String? = null): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()
            .filter { tag == null || it.tagName == tag }

    private fun Element.a(name: String): String = getAttributeNS(ANDROID, name)

    /**
     * Components of one manifest. [namespace] resolves relative names; null means "a library manifest", whose relative
     * names stay marked with `?` so a freeze line can never be met by a guess.
     */
    fun components(manifest: File, namespace: String?): Pair<List<Component>, Set<String>> {
        val app = parse(manifest).kids("application").singleOrNull() ?: return emptyList<Component>() to emptySet()
        val list =
            app.kids().filter { it.tagName in componentTags }.map { c ->
                val raw = c.a("name")
                val name = if (raw.startsWith(".")) (namespace ?: "?") + raw else raw
                val filters = c.kids("intent-filter")
                Component(
                    tag = c.tagName,
                    name = if (c.getAttributeNS(TOOLS, "node") == "remove") "-$name" else name,
                    permission = c.a("permission"),
                    authorities = c.a("authorities").split(';').filter { it.isNotBlank() },
                    actions = filters.flatMap { f -> f.kids("action").map { it.a("name") } }.toSet(),
                    meta = c.kids("meta-data").associate { it.a("name") to it.a("resource").ifEmpty { it.a("value") } },
                    data =
                        filters.flatMap { f ->
                            f.kids("data").map { d -> d.a("scheme") + "://" + d.a("host") }
                        }.toSet(),
                )
            }
        return list to app.kids("meta-data").map { it.a("name") }.toSet()
    }

    /** Applies `tools:node="remove"` markers: a removed name is dropped wherever it was declared. */
    fun merge(parts: List<List<Component>>): List<Component> {
        val all = parts.flatten()
        val removed = all.filter { it.name.startsWith("-") }.map { it.name.drop(1) }.toSet()
        return all.filter { !it.name.startsWith("-") && it.name !in removed }
    }

    // ---- checking ----------------------------------------------------------------------------------------------

    private fun authority(a: String): String =
        a.replace("\${applicationId}", "{applicationId}").let {
            if (it.startsWith("$APP_ID.") || it == APP_ID) "{applicationId}" + it.removePrefix(APP_ID) else it
        }

    /** A `{placeholder}` pattern as a regex over Kotlin string-template text (`$x`, `${a.b}`). */
    fun templateRegex(pattern: String): Regex {
        val out = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val open = pattern.indexOf('{', i)
            if (open < 0) {
                out.append(Regex.escape(pattern.substring(i)))
                break
            }
            if (open > i) out.append(Regex.escape(pattern.substring(i, open)))
            out.append("""\$(\{[^}]+\}|[A-Za-z_][A-Za-z0-9_]*)""")
            i = pattern.indexOf('}', open) + 1
        }
        return Regex("\"$out\"")
    }

    /** One line per frozen name the shipped app no longer honours, naming the name; empty when everything matches. */
    fun problems(frozen: List<Entry>, shipped: Shipped): List<String> =
        frozen.mapNotNull { e -> check(e, shipped)?.let { "${e.kind} ${e.name}: $it" } }

    fun check(e: Entry, s: Shipped): String? {
        fun component(tag: String?, name: String) = s.components.filter { (tag == null || it.tag == tag) && it.name == name }
        return when (e.kind) {
            "component" -> {
                val tag = e.name.substringBefore(':')
                val name = e.name.substringAfter(':')
                val c = component(tag, name)
                when {
                    c.isEmpty() ->
                        component(null, name).firstOrNull()?.let { "declared as <${it.tag}>, frozen as <$tag>" }
                            ?: "no <$tag> with this class name in the shipped manifest"
                    else -> {
                        val want = e.detail.split(',').filter { it.isNotBlank() }.toSet()
                        val missing = want - c.flatMap { it.actions }.toSet()
                        if (missing.isEmpty()) null else "no longer receives ${missing.sorted().joinToString()}"
                    }
                }
            }
            "widget" -> {
                val c = component("receiver", e.name).firstOrNull()
                when {
                    c == null -> "no app-widget receiver with this class name (placed widgets would vanish)"
                    "android.appwidget.action.APPWIDGET_UPDATE" !in c.actions -> "does not receive APPWIDGET_UPDATE"
                    "android.appwidget.provider" !in c.meta -> "has no android.appwidget.provider meta-data"
                    else -> null
                }
            }
            "tile" -> {
                val c = component("service", e.name).firstOrNull()
                when {
                    c == null -> "no tile service with this class name (added tiles would vanish)"
                    "android.service.quicksettings.action.QS_TILE" !in c.actions -> "does not handle QS_TILE"
                    c.permission != "android.permission.BIND_QUICK_SETTINGS_TILE" -> "is not guarded by BIND_QUICK_SETTINGS_TILE"
                    else -> null
                }
            }
            "meta" -> if (e.name in s.appMeta) null else "application meta-data missing"
            "shortcut" -> {
                val main = component("activity", "$APP_ID.MainActivity").firstOrNull()
                val ref = main?.meta?.get("android.app.shortcuts")
                val file = ref?.takeIf { it.startsWith("@xml/") }?.let { s.xmlResource(it.removePrefix("@xml/")) }
                if (file == null) return "MainActivity has no android.app.shortcuts resource"
                val shortcut = parse(file).kids("shortcut").firstOrNull { it.a("shortcutId") == e.name }
                    ?: return "no static shortcut with this id in ${file.name}"
                val intents = shortcut.kids("intent")
                when {
                    intents.isEmpty() -> "has no intent"
                    intents.any { it.a("targetClass") != "$APP_ID.MainActivity" } -> "does not open $APP_ID.MainActivity"
                    intents.any { it.a("targetPackage") != APP_ID } -> "does not target package $APP_ID"
                    else -> null
                }
            }
            "scheme" -> {
                val main = component("activity", "$APP_ID.MainActivity").firstOrNull()
                if (main != null && e.name in main.data && "android.intent.action.VIEW" in main.actions) null
                else "MainActivity's VIEW filter no longer accepts it"
            }
            "channel" ->
                if (s.kotlin.any { (_, text) -> "\"${e.name}\"" in text }) null
                else "no Kotlin source of the shipped app names this channel id"
            "worker" -> {
                val pkg = e.name.substringBeforeLast('.')
                val simple = e.name.substringAfterLast('.')
                val declares =
                    Regex("""\bclass\s+$simple\s*\([^)]*\)\s*:\s*[\w.]*Worker\s*\(""")
                if (s.kotlin.any { (_, text) -> Regex("""(?m)^package\s+${Regex.escape(pkg)}\s*$""").containsMatchIn(text) && declares.containsMatchIn(text) }) null
                else "no WorkManager worker class with this name in the shipped sources (enqueued work would fail)"
            }
            "work-name", "work-tag" -> {
                val rx = templateRegex(e.name)
                if (s.kotlin.any { (_, text) -> rx.containsMatchIn(text) }) null
                else "no Kotlin source of the shipped app names this ${if (e.kind == "work-tag") "work tag" else "unique-work name"}"
            }
            "authority" ->
                if (s.components.any { c -> c.tag == "provider" && c.authorities.any { authority(it) == e.name } }) null
                else "no provider declares this authority"
            else -> "unknown kind '${e.kind}' in the freeze list"
        }
    }

    // ---- the real tree -------------------------------------------------------------------------------------------

    private val moduleRef = Regex(""""(:[a-z][A-Za-z0-9_-]*(?::[A-Za-z0-9_-]+)*)"""")
    private val testDep = Regex("""\b(test|androidTest|testFixtures|kapt|ksp)\w*\s*\(\s*project\s*\(\s*"[^"]+"\s*\)\s*\)""")
    private val excludedSet = Regex("""(?i)test|desktop|ios|jvm|benchmark|debug|preview|qa""")

    /** `:androidApp` and every project module its build file reaches (test-only dependencies left out). */
    fun shippedModules(root: File): List<String> {
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque(listOf(":androidApp"))
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            if (!seen.add(path)) continue
            val build = File(root, path.drop(1).replace(':', '/') + "/build.gradle.kts")
            if (!build.isFile) continue
            moduleRef.findAll(testDep.replace(build.readText(), "")).map { it.groupValues[1] }
                .filter { File(root, it.drop(1).replace(':', '/')).isDirectory }
                .forEach(queue::addLast)
        }
        return seen.toList()
    }

    private fun sourceSets(root: File, module: String): List<File> {
        val src = File(root, module.drop(1).replace(':', '/') + "/src")
        val sets = src.listFiles().orEmpty().filter { it.isDirectory && !excludedSet.containsMatchIn(it.name) }
        // The same-package release is the `switch` flavor; `preview` is the side-by-side id and is not frozen.
        return sets.sortedBy { if (it.name == "main" || it.name == "commonMain") 0 else 1 }
    }

    /**
     * The shipped app read from the tree. [mergedManifest], when given, is the merged manifest AGP wrote for the
     * same-package release (authoritative); otherwise the source manifests of the shipped modules are merged here.
     */
    fun shipped(root: File, mergedManifest: File?): Shipped {
        val modules = shippedModules(root)
        val (components, meta) =
            if (mergedManifest != null) components(mergedManifest, APP_ID)
            else {
                val parts =
                    modules.flatMap { m ->
                        sourceSets(root, m).map { File(it, "AndroidManifest.xml") }.filter { it.isFile }
                            .map { components(it, if (m == ":androidApp") APP_ID else null) }
                    }
                merge(parts.map { it.first }) to parts.flatMap { it.second }.toSet()
            }
        val resDirs = modules.flatMap { m -> sourceSets(root, m).map { File(it, "res/xml") } }
            .sortedBy { if (it.path.contains("androidApp/src/switch")) 0 else if (it.path.contains("androidApp/")) 1 else 2 }
        val kotlin =
            modules.flatMap { m -> sourceSets(root, m).map { File(it, "kotlin") } }
                .filter { it.isDirectory }
                .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.map { it.relativeTo(root).path to it.readText() } }
        return Shipped(components, meta, { name -> resDirs.map { File(it, "$name.xml") }.firstOrNull { it.isFile } }, kotlin)
    }

    /** The merged manifest AGP writes for [variant] (`processSwitchReleaseMainManifest`), or null if not built. */
    fun findMergedManifest(root: File, variant: String = "switchRelease"): File? =
        File(root, "androidApp/build/intermediates").takeIf { it.isDirectory }?.walkTopDown()
            ?.filter { f ->
                f.name == "AndroidManifest.xml" && f.path.contains("merged_manifest") && f.path.contains("/$variant/")
            }
            ?.maxByOrNull { it.lastModified() }
}
