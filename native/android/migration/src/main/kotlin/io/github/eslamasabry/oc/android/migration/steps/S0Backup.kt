package io.github.eslamasabry.oc.android.migration.steps

import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.StepInterrupter
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import java.io.File

internal data class BackupFile(
    val name: String,
    val bytes: Long,
    val sha256: String,
    val authoritative: Boolean,
    /**
     * Set only while a server's deletion rewrites this copy (E00-S08, `FlutterEraCopies`): the size and hash the file
     * has once the rewrite lands. Verification accepts either pair, so a kill between the file's rename and the
     * manifest's never makes the backup look damaged.
     */
    val pendingBytes: Long? = null,
    val pendingSha256: String? = null,
)

/**
 * S0 (plan 05 §7.4, ADR §9.7 step 5b): a byte-for-byte copy of the Flutter stores, published as one directory and
 * resumable at every filesystem operation (audit 04+05 #5). Ported from P5's `BackupPublisher`.
 *
 * - Copies into `files/migration/flutter-v57.partial/`, writes `manifest.json`, renames to `flutter-v57/`.
 * - A published directory that verifies is adopted (the crash came after the rename); one that does not verify blocks
 *   (`BackupInvalid`) and is NEVER copied onto, deleted or overwritten: it may be the only pre-update copy.
 * - A `.bak` twin is copied too and marked authoritative, because Android loads it instead of the `.xml`.
 * - Runs off the main thread (StartupFence), before the first `getSharedPreferences` of any of these files.
 *
 * Journal writes are the runner's: this class only touches the filesystem and returns what happened.
 */
internal class S0Backup(
    private val paths: MigrationPaths,
    private val interrupter: StepInterrupter,
    /** `StatFs(filesDir).availableBytes` on Android. */
    private val freeBytes: () -> Long,
    private val nativeVersionCode: Long,
    private val now: () -> Long,
    private val marginBytes: Long = 16L * 1024 * 1024,
) {
    sealed interface Result {
        data class Published(val files: List<BackupFile>, val adopted: Boolean) : Result
        /** No Flutter store exists: a fresh install, or a device restore with no data. Not a failure. */
        data object NoSource : Result
        data class LowSpace(val neededBytes: Long) : Result
        data object BackupInvalid : Result
    }

    private val partialDir: File get() = File(paths.backupRoot, MigrationPaths.BACKUP_NAME + ".partial")

    fun run(): Result {
        if (paths.backup.exists()) {
            val files = verifyPublished() ?: return Result.BackupInvalid
            return Result.Published(files, adopted = true)
        }
        val present = sourceFiles()
        if (present.isEmpty()) return Result.NoSource
        val needed = present.sumOf { it.length() } + marginBytes
        val free = interrupter.freeBytesOverride() ?: freeBytes()
        if (free < needed) return Result.LowSpace(needed)

        Fs.deleteRecursively(partialDir) // ours and incomplete: the sources were only read
        partialDir.mkdirs()
        interrupter.reached("S0:partialCreated")
        val copied = ArrayList<BackupFile>()
        for (src in present) {
            val sha = Fs.copyDurably(src, File(partialDir, src.name))
            interrupter.reached("S0:copied:${src.name}")
            val logical = src.name.removeSuffix(".bak").removeSuffix(".xml")
            val bakExists = File(paths.sharedPrefs, "$logical.xml.bak").exists()
            copied += BackupFile(src.name, src.length(), sha, authoritative = src.name.endsWith(".bak") || !bakExists)
        }
        Fs.writeAtomically(File(partialDir, MANIFEST), manifestJson(copied).toByteArray(Charsets.UTF_8), interrupter, "S0:manifest")
        Fs.fsyncDir(partialDir)
        interrupter.reached("S0:manifestWritten")
        Fs.move(partialDir, paths.backup)
        Fs.fsyncDir(paths.backupRoot)
        interrupter.reached("S0:renamed")
        return Result.Published(copied, adopted = false)
    }

    /** Every candidate that exists, `.xml` then `.xml.bak`, in [CANDIDATES] order. */
    fun sourceFiles(): List<File> =
        CANDIDATES.flatMap { n -> listOf("$n.xml", "$n.xml.bak") }.map { File(paths.sharedPrefs, it) }.filter { it.isFile }

    /** Re-hash every file named in the published manifest; null when anything is missing or different. */
    fun verifyPublished(): List<BackupFile>? {
        val manifest = File(paths.backup, MANIFEST)
        if (!manifest.isFile) return null
        val files = parseManifest(manifest.readText(Charsets.UTF_8)) ?: return null
        for (f in files) {
            val copy = File(paths.backup, f.name)
            if (!copy.isFile) return null
            val sha = Fs.sha256Hex(copy)
            val committed = copy.length() == f.bytes && sha == f.sha256
            val pending = copy.length() == f.pendingBytes && sha == f.pendingSha256
            if (!committed && !pending) return null
        }
        return files
    }

    /** The backup copy Android would load for [name] (`.bak` when it was authoritative), or null. */
    fun authoritativeCopy(name: String, files: List<BackupFile>): File? =
        files.firstOrNull { it.authoritative && it.name.removeSuffix(".bak").removeSuffix(".xml") == name }
            ?.let { File(paths.backup, it.name) }

    private fun manifestJson(files: List<BackupFile>): String = MiniJson.encode(
        linkedMapOf(
            "schema" to 1L,
            "copiedAt" to now(),
            "nativeVersionCode" to nativeVersionCode,
            "files" to files.map { it.toMap() },
        ),
    )

    companion object {
        const val MANIFEST: String = "manifest.json"

        /** Plan 05 S0 step 2 plus the picker cache (audit 04+05 #11). */
        val CANDIDATES: List<String> = listOf(
            "FlutterSharedPreferences",
            "FlutterSecureStorage",
            "FlutterSecureKeyStorage",
            "FlutterSecureStorageConfiguration",
            "FlutterSecureStorageConfiguration:FlutterSecureStorage",
            "flutter_image_picker_shared_preference",
        )

        fun BackupFile.toMap(): Map<String, Any?> =
            linkedMapOf<String, Any?>("name" to name, "bytes" to bytes, "sha256" to sha256, "authoritative" to authoritative)
                .also { m ->
                    if (pendingBytes != null && pendingSha256 != null) {
                        m["pendingBytes"] = pendingBytes
                        m["pendingSha256"] = pendingSha256
                    }
                }

        /**
         * Records that [name] in the published backup is about to become [bytes]/[sha256] (E00-S08). Returns false when
         * the manifest is unreadable; true when it was written, or when it does not list [name] (nothing to keep true).
         */
        fun markPending(backupDir: File, name: String, bytes: Long, sha256: String): Boolean =
            rewriteManifest(backupDir) { f -> if (f.name == name) f.copy(pendingBytes = bytes, pendingSha256 = sha256) else f }

        /** The rewrite of [name] landed: its pending size and hash become the recorded ones. */
        fun commitPending(backupDir: File, name: String): Boolean =
            rewriteManifest(backupDir) { f ->
                if (f.name == name && f.pendingBytes != null && f.pendingSha256 != null) {
                    f.copy(bytes = f.pendingBytes, sha256 = f.pendingSha256, pendingBytes = null, pendingSha256 = null)
                } else {
                    f
                }
            }

        private fun rewriteManifest(backupDir: File, change: (BackupFile) -> BackupFile): Boolean {
            val file = File(backupDir, MANIFEST)
            if (!file.isFile) return true
            val manifest = MiniJson.parseOrNull(file.readText(Charsets.UTF_8)) as? Map<*, *> ?: return false
            val files = parseManifest(file.readText(Charsets.UTF_8)) ?: return false
            val next = files.map(change)
            if (next == files) return true
            val out = LinkedHashMap<String, Any?>()
            manifest.forEach { (k, v) -> out[k as String] = v }
            out["files"] = next.map { it.toMap() }
            Fs.writeAtomically(file, MiniJson.encode(out).toByteArray(Charsets.UTF_8))
            return true
        }

        fun parseManifest(text: String): List<BackupFile>? {
            val m = MiniJson.parseOrNull(text) as? Map<*, *> ?: return null
            val list = m["files"] as? List<*> ?: return null
            return list.map { e ->
                val f = e as? Map<*, *> ?: return null
                BackupFile(
                    name = f["name"] as? String ?: return null,
                    bytes = f["bytes"] as? Long ?: return null,
                    sha256 = f["sha256"] as? String ?: return null,
                    authoritative = f["authoritative"] == true,
                    pendingBytes = f["pendingBytes"] as? Long,
                    pendingSha256 = f["pendingSha256"] as? String,
                )
            }
        }
    }
}
