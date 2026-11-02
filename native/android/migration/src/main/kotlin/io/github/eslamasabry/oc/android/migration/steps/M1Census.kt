package io.github.eslamasabry.oc.android.migration.steps

import io.github.eslamasabry.oc.android.migration.DeferredFeature
import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.prefs.ImagePickerCache
import io.github.eslamasabry.oc.android.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.readers.xml.SharedPrefsFile
import java.io.File

/**
 * M1, census and integrity (plan 05 §7.4 M1). Read-only on the prefs file; its only writes are the baseline and the
 * inventory under `files/native/`. Everything it records is a count, a presence flag or a size: never a value.
 */
internal class M1Census(
    private val paths: MigrationPaths,
    private val packageName: String,
) {
    sealed interface Result {
        /** Android loaded a non-empty file as an empty map: the first commit would overwrite it. No write may happen. */
        data object PrefsUnreadable : Result
        data class Done(val counts: Map<String, Any?>, val deferred: Set<DeferredFeature>) : Result
    }

    /**
     * @param live the map Android loaded for `FlutterSharedPreferences` (physical keys).
     * @param backupCopy the S0 copy Android would load (`.bak` if present), or null when S0 found no prefs file.
     */
    fun run(live: Map<String, RawPref>, backupCopy: File?): Result {
        // 1. Prefs integrity: our own parse of the backed-up bytes vs what Android handed the app.
        val ownParse = backupCopy?.takeIf { it.isFile }?.let { AndroidPrefsXml.parse(it) }
        val ownKeys = ownParse?.entries?.keys?.count { it.startsWith(FlutterPrefsCodec.KEY_PREFIX) } ?: 0
        val liveKeys = live.keys.count { it.startsWith(FlutterPrefsCodec.KEY_PREFIX) }
        if (backupCopy != null && backupCopy.length() > 0 && ownKeys > 0 && liveKeys == 0) return Result.PrefsUnreadable
        val counts = LinkedHashMap<String, Any?>()
        counts["prefsBytes"] = backupCopy?.length() ?: 0L
        counts["prefsKeys"] = liveKeys.toLong()
        counts["backupParseMismatch"] = ownParse != null && ownKeys != liveKeys

        // Baseline before any write of this process; kept if a crash already left it (nothing was written since).
        if (!paths.baseline.isFile) PrefsBaseline.of(live).save(paths.baseline)

        // 2. Codec census: stored kinds, legacy Java lists, undecodable values.
        val kinds = sortedMapOf<String, Long>()
        var undecodable = 0L
        for ((k, v) in live) {
            if (!k.startsWith(FlutterPrefsCodec.KEY_PREFIX)) continue
            val decoded = FlutterPrefsCodec.decode(v)
            kinds.merge(decoded.kind.name, 1L, Long::plus)
            if (decoded.value is io.github.eslamasabry.oc.android.migration.readers.prefs.PrefValue.Undecodable) undecodable++
        }
        counts["kinds"] = kinds
        counts["undecodable"] = undecodable

        // 3. Strays (presence and key counts only).
        val datastore = File(paths.files, "datastore/FlutterSharedPreferences.preferences_pb")
        counts["strayDataStoreBytes"] = if (datastore.isFile) datastore.length() else null
        counts["strayDefaultPrefsKeys"] = SharedPrefsFile(paths.sharedPrefs, "${packageName}_preferences").read()?.androidMap?.size?.toLong()
        counts["unprefixedKeys"] = live.keys.count { !it.startsWith(FlutterPrefsCodec.KEY_PREFIX) }.toLong()
        val picker = SharedPrefsFile(paths.sharedPrefs, ImagePickerCache.FILE_NAME).read()?.androidMap?.let(ImagePickerCache::read)
        counts["pickerLostResult"] = picker?.hasResult == true

        // 4. Profiles: count, invalid ids, reserved-segment collisions, OC1-era flags (L4 read rule, zero writes).
        val prefs = LogicalPrefs(live)
        val profiles = prefs.profiles()
        counts["profiles"] = profiles.size.toLong()
        counts["invalidIds"] = profiles.count { !ID_SHAPE.matches(it["id"] as String) }.toLong()
        counts["reservedCollisions"] = profiles.count { (it["id"] as String) in RESERVED_SEGMENTS }.toLong()
        counts["oc1MigrationRequired"] = profiles.count(::requiresOpenCode2Migration).toLong()
        counts["oc2HistoryPending"] = profiles.count { it["openCode2HistoryPending"] == true }.toLong()

        // 5. Directory inventory (names and sizes, written to its own file for Report a problem and the v3 cleanup).
        val inventory = inventory()
        Fs.writeAtomically(paths.inventory, MiniJson.encode(linkedMapOf("v" to 1L, "dirs" to inventory)).toByteArray(Charsets.UTF_8))
        counts["inventoryEntries"] = inventory.size.toLong()

        // 6. Interrupted atomic writes: presence and size, never contents (handled by their owners, plan 05 §7.8).
        counts["pendingWrites"] = PENDING_WRITES.associateWith { p -> File(paths.files, p).takeIf { it.isFile }?.length() }

        // Deferred and dropped features (plan 02 §3.7): does any of their data exist? (S2-GATEUI's one-time notice.)
        val deferred = deferredFeatures(prefs)
        counts["deferred"] = deferred.map { it.name }.sorted()
        counts["a2aPrefsKeys"] = prefs.map.keys.count { it.startsWith("oc.a2aTasks.") || it == "oc.externalAgents" }.toLong()
        return Result.Done(counts, deferred)
    }

    private fun deferredFeatures(prefs: LogicalPrefs): Set<DeferredFeature> {
        val out = LinkedHashSet<DeferredFeature>()
        val teamFiles = paths.files.listFiles()?.any { it.name.startsWith("oc.teamEngine") } == true
        if (teamFiles || prefs.map.keys.any { it.startsWith("oc.orchestration.") || it.startsWith("oc.team") }) out += DeferredFeature.AiTeam
        val factsbookDir = paths.externalFilesDir?.let { File(it, "factsbook") }?.exists() == true
        if (factsbookDir || prefs.map.keys.any { it.startsWith("oc.factsbook") }) out += DeferredFeature.Memory
        if (File(paths.files, "local_llm_models").list()?.isNotEmpty() == true) out += DeferredFeature.Gemma
        return out
    }

    /** Depth 1 of the data dirs, depth 2 of `files/` (`files/linux` and `files/projects` as top-level counts only). */
    private fun inventory(): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        fun entry(dir: String, f: File, deep: Boolean) {
            val (count, bytes, truncated) = if (deep) measure(f) else Triple(f.list()?.size?.toLong() ?: 1L, if (f.isFile) f.length() else 0L, false)
            out += linkedMapOf("dir" to dir, "name" to f.name, "isDir" to f.isDirectory, "files" to count, "bytes" to bytes, "truncated" to truncated)
        }
        for (top in INVENTORY_DIRS) {
            val dir = File(paths.dataDir, top)
            for (child in dir.listFiles().orEmpty().sortedBy { it.name }) {
                val shallow = top == "files" && child.name in SHALLOW
                entry(top, child, deep = !shallow)
                if (top == "files" && child.isDirectory && !shallow) {
                    for (grand in child.listFiles().orEmpty().sortedBy { it.name }) entry("files/${child.name}", grand, deep = true)
                }
            }
        }
        paths.externalFilesDir?.listFiles().orEmpty().sortedBy { it.name }.forEach { entry("external/files", it, deep = true) }
        paths.externalCacheDir?.listFiles().orEmpty().sortedBy { it.name }.forEach { entry("external/cache", it, deep = true) }
        return out
    }

    /** File count and bytes under [f], bounded so a large tree cannot stall the first launch. */
    private fun measure(f: File): Triple<Long, Long, Boolean> {
        if (f.isFile) return Triple(1L, f.length(), false)
        var count = 0L
        var bytes = 0L
        for (x in f.walkTopDown()) {
            if (x.isFile) { count++; bytes += x.length() }
            if (count >= WALK_LIMIT) return Triple(count, bytes, true)
        }
        return Triple(count, bytes, false)
    }

    companion object {
        /** Plan 05 §3.2 `ProfileId` shape. */
        val ID_SHAPE: Regex = Regex("^[A-Za-z0-9_-]{1,128}$")

        /** Dotted segments of app-wide keys a generated id must never equal (plan 05 §3.2; ledger-derived set pending). */
        val RESERVED_SEGMENTS: Set<String> = setOf("app", "native", "notify", "nudges", "firstRun", "transcript", "phone", "agents", "v1")

        val INVENTORY_DIRS: List<String> = listOf("files", "app_flutter", "cache", "code_cache", "no_backup", "databases", "app_webview")
        val SHALLOW: Set<String> = setOf("linux", "projects")
        val PENDING_WRITES: List<String> = listOf(
            "diagnostics/report_problem.pending", "crash-diagnostics.pending", "native-last-crash.properties.tmp",
        )
        const val WALK_LIMIT: Long = 20_000

        /** Dart `ServerProfile.fromJson` (`lib/state/profiles/models.dart:327`): L4, applied for counting only. */
        fun requiresOpenCode2Migration(p: Map<String, Any?>): Boolean =
            p["requiresOpenCode2Migration"] == true || (p["backend"] != "codex" && p["backend"] != "paseo" && p["flavor"] != "v2")
    }
}
