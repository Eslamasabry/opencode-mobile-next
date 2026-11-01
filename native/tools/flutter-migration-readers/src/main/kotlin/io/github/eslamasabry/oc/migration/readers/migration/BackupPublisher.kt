package io.github.eslamasabry.oc.migration.readers.migration

import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import java.io.File

/**
 * Step S0: a byte-for-byte copy of the Flutter stores, published as one directory, resumable at every
 * filesystem operation (audit 04+05 finding 5).
 *
 * Layout: `<backupRoot>/<name>.partial/` while copying, renamed to `<backupRoot>/<name>/` when complete. The
 * published directory holds `manifest.json` (names, sizes, SHA-256, which copy Android would load) and the
 * copied files with their original names, `.bak` twins included.
 *
 * Restart rules:
 * - published dir exists and verifies against its manifest → it is THE backup: journal it, never copy again
 *   (this is the "died after rename, before journaling" case);
 * - published dir exists and does not verify → block (`BackupInvalid`); never overwrite or delete it;
 * - only a partial dir exists → it is ours and incomplete: delete it and copy again (sources were only read).
 */
public class BackupPublisher(
    private val sharedPrefsDir: File,
    private val backupRoot: File,
    private val journal: JournalStore,
    private val faults: FaultPoints = FaultPoints.None,
    private val backupName: String = DEFAULT_NAME,
    private val fileNames: List<String> = DEFAULT_FILES,
    /** Free bytes on the data partition; `StatFs(filesDir).availableBytes` on Android. */
    private val freeBytes: () -> Long = { backupRoot.absoluteFile.let { f -> generateSequence(f) { it.parentFile }.first { it.exists() }.usableSpace } },
    private val marginBytes: Long = 16L * 1024 * 1024,
) {
    public sealed interface Outcome {
        public data class Published(val files: List<BackupFile>) : Outcome
        public data object AlreadyPublished : Outcome
        /** No Flutter store exists: a fresh install (or a restore with no data). Not a failure. */
        public data object NoSource : Outcome
        public data class LowSpace(val neededBytes: Long) : Outcome
        public data object BackupInvalid : Outcome
    }

    public val publishedDir: File get() = File(backupRoot, backupName)
    private val partialDir: File get() = File(backupRoot, "$backupName.partial")

    public fun runIfNeeded(): Outcome {
        val j = journal.load()
        if (j.backupState == BackupState.Published && verifyPublished() != null) return Outcome.AlreadyPublished
        if (j.sourceKind != null && j.sourceKind != "flutter") return Outcome.NoSource

        // Resume: a published directory is complete by construction (atomic rename); verify and adopt it.
        if (publishedDir.exists()) {
            val files = verifyPublished() ?: return blockInvalid()
            journal.update {
                it.sourceKind = "flutter"
                it.backupState = BackupState.Published
                it.backupFiles = files
            }
            faults.reached("S0:journaled")
            return Outcome.Published(files)
        }

        val present = fileNames.flatMap { n -> listOf("$n.xml", "$n.xml.bak") }.map { File(sharedPrefsDir, it) }.filter { it.exists() }
        if (present.isEmpty()) {
            journal.update { it.sourceKind = "none" }
            return Outcome.NoSource
        }
        val needed = present.sumOf { it.length() } + marginBytes
        if (freeBytes() < needed) return Outcome.LowSpace(needed)

        journal.update {
            it.sourceKind = "flutter"
            it.backupState = BackupState.Copying
        }
        faults.reached("S0:copying")
        Fs.deleteRecursively(partialDir)
        partialDir.mkdirs()
        faults.reached("S0:partialCreated")
        val copied = ArrayList<BackupFile>()
        for (src in present) {
            val sha = Fs.copyDurably(src, File(partialDir, src.name))
            faults.reached("S0:copied:${src.name}")
            val logical = src.name.removeSuffix(".bak").removeSuffix(".xml")
            // AOSP loadFromDisk: when <name>.xml.bak exists it replaces <name>.xml, so the .bak is what loads.
            val bakExists = File(sharedPrefsDir, "$logical.xml.bak").exists()
            val authoritative = if (src.name.endsWith(".bak")) true else !bakExists
            copied += BackupFile(src.name, src.length(), sha, authoritative)
        }
        Fs.writeAtomically(File(partialDir, MANIFEST), manifestJson(copied).toByteArray(Charsets.UTF_8), faults, "S0:manifest")
        Fs.fsyncDir(partialDir)
        faults.reached("S0:manifestWritten")
        Fs.move(partialDir, publishedDir)
        Fs.fsyncDir(backupRoot)
        faults.reached("S0:renamed")
        journal.update {
            it.backupState = BackupState.Published
            it.backupFiles = copied
        }
        faults.reached("S0:journaled")
        return Outcome.Published(copied)
    }

    /** Re-hash every file named in the published manifest; null when anything is missing or different. */
    public fun verifyPublished(): List<BackupFile>? {
        val manifest = File(publishedDir, MANIFEST)
        if (!manifest.exists()) return null
        val files = parseManifest(manifest.readText()) ?: return null
        for (f in files) {
            val copy = File(publishedDir, f.name)
            if (!copy.exists() || copy.length() != f.bytes || Fs.sha256Hex(copy) != f.sha256) return null
        }
        return files
    }

    private fun blockInvalid(): Outcome {
        journal.update { it.blocked = Blocked("BackupInvalid", "S0") }
        return Outcome.BackupInvalid
    }

    public companion object {
        public const val DEFAULT_NAME: String = "flutter-v57"
        public const val MANIFEST: String = "manifest.json"

        /** plan/05 S0 step 2: the Flutter prefs file and the four secure-storage files. */
        public val DEFAULT_FILES: List<String> = listOf(
            "FlutterSharedPreferences",
            "FlutterSecureStorage",
            "FlutterSecureKeyStorage",
            "FlutterSecureStorageConfiguration",
            "FlutterSecureStorageConfiguration:FlutterSecureStorage",
            // Audit finding 11: the picker's lost result is the only link to a photo taken before the update.
            "flutter_image_picker_shared_preference",
        )

        public fun manifestJson(files: List<BackupFile>): String = MiniJson.encode(
            linkedMapOf(
                "schema" to 1L,
                "files" to files.map { linkedMapOf("name" to it.name, "bytes" to it.bytes, "sha256" to it.sha256, "authoritative" to it.authoritative) },
            ),
        )

        public fun parseManifest(text: String): List<BackupFile>? {
            val m = MiniJson.parseOrNull(text) as? Map<*, *> ?: return null
            val list = m["files"] as? List<*> ?: return null
            return list.map { e ->
                val f = e as? Map<*, *> ?: return null
                BackupFile(
                    name = f["name"] as? String ?: return null,
                    bytes = f["bytes"] as? Long ?: return null,
                    sha256 = f["sha256"] as? String ?: return null,
                    authoritative = f["authoritative"] == true,
                )
            }
        }
    }
}
