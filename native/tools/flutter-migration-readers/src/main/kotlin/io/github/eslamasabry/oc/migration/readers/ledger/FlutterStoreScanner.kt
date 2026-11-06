package io.github.eslamasabry.oc.migration.readers.ledger

import java.io.File

/** Where in the Flutter tree a stored name was found. */
public enum class ScanSource(public val wire: String) {
    DartLiteral("dart-literal"),
    DartTypedKey("dart-typed-key"),
    DartKeyConst("dart-key-const"),
    DartSupportDir("dart-support-dir"),
    KotlinFlutterPref("kotlin-flutter-pref"),
    KotlinLiteral("kotlin-literal"),
    KotlinFilesDir("kotlin-files-dir"),
    KotlinChannel("kotlin-channel"),
    KotlinPrefsFile("kotlin-prefs-file"),
    KotlinWork("kotlin-work"),
    ManifestPermission("manifest-permission"),
    ManifestComponent("manifest-component"),
    Shortcut("shortcut"),
    ;

    public companion object {
        public fun fromWire(wire: String): ScanSource = entries.first { it.wire == wire }
    }
}

/** One name the Flutter app stores data under (or a name that looks like one), with the first file that writes it. */
public data class ScannedName(val name: String, val source: ScanSource, val writer: String)

/**
 * Finds every name the Flutter tree (`lib/` and `android/app/src/main/`) keeps data under: preference keys and key
 * prefixes, other SharedPreferences files, files under the app's files and cache dirs, notification channels and
 * groups, WorkManager names, manifest components and permissions, and static shortcut ids.
 *
 * The rules are textual on purpose: they over-match (a metadata field or a log tag that looks like a key) rather than
 * miss a key, and each over-match gets a NOT_STORED row in the store ledger with the reason. Names built only at run
 * time from non-literal parts are out of reach; the plan 05 appendices cover those rows by hand.
 */
public object FlutterStoreScanner {

    private val dartRules: List<Pair<ScanSource, Regex>> = listOf(
        ScanSource.DartLiteral to Regex("""['"]((?:oc|voice)\.[A-Za-z0-9_.:-]*)"""),
        ScanSource.DartTypedKey to Regex("""\.(?:get|set)(?:String|Bool|Int|Double|StringList)\(\s*['"]([A-Za-z0-9_.-]+)['"]"""),
        ScanSource.DartKeyConst to Regex("""\b(?:const|final)\s+\w*[kK]ey\s*=\s*['"]([A-Za-z0-9_.-]+)['"]"""),
        ScanSource.DartSupportDir to Regex("""getApplicationSupportDirectory\(\)\)?\.path\}?/([A-Za-z0-9_.-]+)"""),
        ScanSource.DartSupportDir to Regex("""getApplicationSupportDirectory\(\)\)\.path,\s*['"]([A-Za-z0-9_.-]+)['"]"""),
        ScanSource.DartSupportDir to Regex("""\bsupport\.path\}/([A-Za-z0-9_.-]+)"""),
        ScanSource.DartSupportDir to Regex("""\b\w*(?:DirectoryName|FileName)\s*=\s*['"]([A-Za-z0-9_.-]+)['"]"""),
    )

    private val kotlinRules: List<Pair<ScanSource, Regex>> = listOf(
        ScanSource.KotlinFlutterPref to Regex(""""flutter\.([A-Za-z0-9_.:-]+)"""),
        ScanSource.KotlinLiteral to Regex(""""(oc\.[A-Za-z0-9_.:-]*)"""),
        ScanSource.KotlinFilesDir to Regex("""File\(\s*[\w.]*(?:filesDir|cacheDir|noBackupFilesDir)[\w.]*\s*,\s*"([^"/$]+)"""),
        ScanSource.KotlinChannel to Regex("""\bconst\s+val\s+(?!EXTRA_)(?:\w*CHANNEL\w*|GROUP_\w+)\s*=\s*"([a-z0-9_]+)""""),
        ScanSource.KotlinChannel to Regex("""\bChannel\(\s*"([a-z0-9_]+)""""),
    )
    private val filesDirTemplate = Regex("""File\(\s*[\w.]*(?:filesDir|cacheDir|noBackupFilesDir)[\w.]*\s*,\s*"([^"/]+?)\$""")
    private val constVal = Regex("""\bconst\s+val\s+(\w+)\s*=\s*"([^"]*)"""")
    private val prefsFile = Regex("""getSharedPreferences\(\s*(?:"([^"]+)"|([\w.]+))""")
    private val workName = Regex("""fun\s+\w+\([^)]*\)\s*=\s*"([^"$]+)\$""")
    private val permission = Regex("""<uses-permission\b[^>]*?android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
    private val component = Regex("""<(?:service|receiver|provider|activity|activity-alias)\b[^>]*?android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
    private val shortcutId = Regex("""android:shortcutId="([^"]+)"""")

    public const val MANIFEST: String = "android/app/src/main/AndroidManifest.xml"
    public const val SHORTCUTS: String = "android/app/src/main/res/xml/shortcuts.xml"

    /** Scans [root] (a Flutter checkout). The result is sorted by name; each name keeps the first writer in path order. */
    public fun scan(root: File): List<ScannedName> {
        val out = LinkedHashMap<String, ScannedName>()
        fun add(source: ScanSource, name: String, path: String) {
            if (name.isNotEmpty() && name !in out) out[name] = ScannedName(name, source, path)
        }

        for ((path, text) in sources(root, "lib", ".dart")) {
            for ((source, rule) in dartRules) for (m in rule.findAll(text)) add(source, m.groupValues[1], path)
        }

        val kotlin = sources(root, "android/app/src/main", ".kt")
        val consts = HashMap<String, MutableSet<String>>()
        for ((_, text) in kotlin) for (m in constVal.findAll(text)) consts.getOrPut(m.groupValues[1]) { sortedSetOf() } += m.groupValues[2]
        for ((path, text) in kotlin) {
            for ((source, rule) in kotlinRules) for (m in rule.findAll(text)) add(source, m.groupValues[1], path)
            for (m in filesDirTemplate.findAll(text)) add(ScanSource.KotlinFilesDir, m.groupValues[1], path)
            for (m in prefsFile.findAll(text)) {
                val literal = m.groupValues[1]
                if (literal.isNotEmpty()) {
                    add(ScanSource.KotlinPrefsFile, literal, path)
                    continue
                }
                val ident = m.groupValues[2].substringAfterLast('.')
                val local = Regex("""\bconst\s+val\s+${Regex.escape(ident)}\s*=\s*"([^"]*)"""").find(text)
                val values = if (local != null) listOf(local.groupValues[1]) else consts[ident].orEmpty().toList()
                for (v in values) add(ScanSource.KotlinPrefsFile, v, path)
            }
            if ("WorkManager" in text) for (m in workName.findAll(text)) add(ScanSource.KotlinWork, m.groupValues[1], path)
        }

        File(root, MANIFEST).takeIf { it.isFile }?.readText()?.let { manifest ->
            for (m in permission.findAll(manifest)) add(ScanSource.ManifestPermission, m.groupValues[1], MANIFEST)
            for (m in component.findAll(manifest)) add(ScanSource.ManifestComponent, m.groupValues[1], MANIFEST)
        }
        File(root, SHORTCUTS).takeIf { it.isFile }?.readText()?.let { xml ->
            for (m in shortcutId.findAll(xml)) add(ScanSource.Shortcut, m.groupValues[1], SHORTCUTS)
        }
        return out.values.sortedBy { it.name }
    }

    /** Files under [root]/[sub] ending in [ext], as (path relative to root with '/' separators, text), in path order. */
    private fun sources(root: File, sub: String, ext: String): List<Pair<String, String>> {
        val base = File(root, sub)
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown().filter { it.isFile && it.name.endsWith(ext) }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .sortedBy { it.first }
            .toList()
    }
}
