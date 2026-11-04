package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.lane.WriterLanes
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupKind
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupManifest
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupPhase
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupPlatform
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupProblem
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupProgress
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupResult
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackups
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxRestoreSummary
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServices
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.manager.LinuxAnswer
import io.github.eslamasabry.oc.feature.linux.manager.LinuxBackupScripts
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobState
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobStatus
import io.github.eslamasabry.oc.feature.linux.manager.LinuxManager
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProblem
import io.github.eslamasabry.oc.feature.linux.manager.LinuxRetryWait
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxJobWatch
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@BindingContainer
@ContributesTo(AppScope::class)
object LinuxBackupWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun backups(
        app: Application,
        dispatchers: AppDispatchers,
        scope: AppCoroutineScope,
        manager: LinuxManager,
        jobs: LinuxJobWatch,
        wait: LinuxRetryWait,
        exports: AndroidProjectExport,
        store: KeyValueStore,
        lanes: WriterLanes,
        clock: Clock,
        build: AppBuildInfo,
        services: KeptServices,
    ): LinuxBackups =
        LinuxBackups(
            AndroidLinuxBackups(
                app,
                dispatchers,
                manager,
                jobs,
                wait,
                exports,
                store,
                lanes,
                clock,
                build,
                services,
            ),
            scope,
            dispatchers,
        )
}

/**
 * Fixed-name format-1 backups. No URI, guest output, or archive content is logged or used as
 * visible copy.
 */
private class AndroidLinuxBackups(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val manager: LinuxManager,
    private val jobs: LinuxJobWatch,
    private val wait: LinuxRetryWait,
    private val exports: AndroidProjectExport,
    private val store: KeyValueStore,
    private val lanes: WriterLanes,
    private val clock: Clock,
    private val build: AppBuildInfo,
    private val services: KeptServices,
) : LinuxBackupPlatform {
    private data class Chosen(val token: String, val file: File, val manifest: JSONObject)

    private var chosen: Chosen? = null
    private var activeOperation: String? = null
    private val safeFiles = NativeProjectFiles(app)

    override suspend fun backup(
        kind: LinuxBackupKind,
        progress: (LinuxBackupProgress) -> Unit,
    ): LinuxBackupResult =
        boundary(LinuxBackupProblem.Save) {
            requireLinux()
            val stage = safeDir("root/.oc-builtin/backup/stage")
            val operation = UUID.randomUUID().toString()
            try {
                runJob(
                    LinuxJobKind.Backup,
                    operation,
                    LinuxBackupScripts.backup(kind, operation),
                    progress,
                )
                activeOperation = null
                jobs.dismiss()
                val parts =
                    LinuxBackupScripts.entries
                        .filter { it != "manifest.json" }
                        .mapNotNull { name ->
                            val source = File(stage, name)
                            if (Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS))
                                NativeExportEntry(name, source, source.length())
                            else null
                        }
                if (parts.none { it.name == "home.tar.gz" }) abort(LinuxBackupProblem.Job)
                val manifest =
                    JSONObject()
                        .put("format", 1)
                        .put(
                            "kind",
                            if (kind == LinuxBackupKind.Setup) "setup" else "setupAndProjects",
                        )
                        .put("created", clock.now().toString())
                        .put("app", build.versionName)
                        .put("ubuntu", "24.04")
                        .put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                        .put("entries", JSONArray(parts.map { it.name }))
                val manifestFile = File(stage, "manifest.json")
                createPrivateFile(manifestFile).use { it.write(manifest.toString(2).toByteArray()) }
                val entries =
                    listOf(
                        NativeExportEntry("manifest.json", manifestFile, manifestFile.length())
                    ) + parts
                val name =
                    "opencode-linux-${if (kind == LinuxBackupKind.Setup) "setup" else "setup-projects"}-${clock.now().toString().take(10)}.zip"
                val target =
                    when (val picked = exports.pickDestination(name)) {
                        is Outcome.Failed ->
                            return@boundary LinuxBackupResult.Problem(
                                LinuxBackupProblem.Save,
                                picked.failure,
                            )
                        is Outcome.Ok -> picked.value ?: return@boundary LinuxBackupResult.Cancelled
                    }
                when (
                    val result =
                        exports.writeZip(target, entries) { bytes, total ->
                            progress(LinuxBackupProgress(LinuxBackupPhase.Saving, bytes, total))
                        }
                ) {
                    is Outcome.Failed ->
                        LinuxBackupResult.Problem(LinuxBackupProblem.Save, result.failure)
                    is Outcome.Ok -> {
                        // Device scope: resetting Linux keeps its backup reminder; removing Linux
                        // clears oc.linux.*.
                        store.commit(lanes.app) {
                            putLong("oc.linux.lastBackupAt", clock.now().toEpochMilliseconds())
                        }
                        LinuxBackupResult.Saved(name, result.value.bytes)
                    }
                }
            } finally {
                activeOperation = null
                deletePrivateTree(stage)
            }
        }

    override suspend fun choose(): LinuxBackupResult =
        boundary(LinuxBackupProblem.NotBackup) {
            forgetChosen()
            val uri =
                when (val picked = exports.pickArchive()) {
                    is Outcome.Failed ->
                        return@boundary LinuxBackupResult.Problem(
                            LinuxBackupProblem.NotBackup,
                            picked.failure,
                        )
                    is Outcome.Ok -> picked.value ?: return@boundary LinuxBackupResult.Cancelled
                }
            val file = File.createTempFile("oc-linux-backup-", ".zip", app.cacheDir)
            try {
                val budget = available(file.parentFile!!)
                app.contentResolver.openInputStream(Uri.parse(uri)).use { input ->
                    if (input == null) abort(LinuxBackupProblem.NotBackup)
                    file.outputStream().use { output -> copyBounded(input, output, budget) }
                }
                val manifest =
                    ZipFile(file).use { zip ->
                        val entries = zip.entries().asSequence().toList()
                        if (
                            entries.size > LinuxBackupScripts.entries.size ||
                                entries.map { it.name }.toSet().size != entries.size ||
                                entries.any {
                                    it.name !in LinuxBackupScripts.entries ||
                                        it.isDirectory ||
                                        it.size < 0
                                }
                        )
                            abort(LinuxBackupProblem.NotBackup)
                        val entry =
                            zip.getEntry("manifest.json") ?: abort(LinuxBackupProblem.NotBackup)
                        if (entry.size > 65_536) abort(LinuxBackupProblem.NotBackup)
                        val read =
                            JSONObject(
                                zip.getInputStream(entry).use { it.readBytes().decodeToString() }
                            )
                        val format = read.optInt("format", -1)
                        if (format > 1) abort(LinuxBackupProblem.Newer)
                        if (
                            format != 1 ||
                                read.optString("kind") !in setOf("setup", "setupAndProjects")
                        )
                            abort(LinuxBackupProblem.NotBackup)
                        kotlin.time.Instant.parse(read.optString("created"))
                        val listed =
                            read.optJSONArray("entries") ?: abort(LinuxBackupProblem.NotBackup)
                        val names = (0 until listed.length()).map { listed.optString(it) }
                        if (
                            names.distinct().size != names.size ||
                                names.any {
                                    it == "manifest.json" ||
                                        it !in LinuxBackupScripts.entries ||
                                        zip.getEntry(it) == null
                                } ||
                                names.toSet() !=
                                    entries.map { it.name }.filter { it != "manifest.json" }.toSet()
                        )
                            abort(LinuxBackupProblem.NotBackup)
                        read
                    }
                val token = UUID.randomUUID().toString()
                chosen = Chosen(token, file, manifest)
                LinuxBackupResult.Checked(
                    LinuxBackupManifest(
                        token,
                        manifest.getString("created").take(10),
                        manifest.getJSONArray("entries").toString().contains("projects.tar.gz"),
                    )
                )
            } finally {
                if (chosen?.file != file) file.delete()
            }
        }

    override suspend fun restore(
        token: String,
        progress: (LinuxBackupProgress) -> Unit,
    ): LinuxBackupResult =
        boundary(LinuxBackupProblem.Restore) {
            val read = chosen?.takeIf { it.token == token } ?: abort(LinuxBackupProblem.NotBackup)
            requireLinux()
            val into = safeDir("root/.oc-builtin/restore")
            // Restore sources are app-private and fixed named; clear a preceding interrupted
            // restore before staging this one.
            into.listFiles()?.forEach(::deletePrivateTree)
            try {
                ZipFile(read.file).use { zip ->
                    var remaining = available(into)
                    zip.entries().asSequence().forEach { entry ->
                        if (
                            entry.name !in LinuxBackupScripts.entries ||
                                entry.size < 0 ||
                                entry.size > remaining
                        )
                            abort(LinuxBackupProblem.NotBackup)
                        val target = File(into, entry.name)
                        zip.getInputStream(entry).use { input ->
                            createPrivateFile(target).use { output ->
                                copyBounded(input, output, remaining)
                            }
                        }
                        remaining -= target.length()
                        if (entry.name.endsWith(".tar.gz"))
                            safeFiles.openRegularFileInside(target).use {
                                LinuxBackupTar.validate(
                                    it,
                                    entry.name,
                                    BuiltinDiscovery.rootfs(app.filesDir),
                                    available(into),
                                )
                            }
                    }
                }
                val operation = UUID.randomUUID().toString()
                val done =
                    runJob(
                        LinuxJobKind.Restore,
                        operation,
                        LinuxBackupScripts.restore(operation),
                        progress,
                    )
                val counts = mutableMapOf<String, Int>()
                val skipped = mutableListOf<String>()
                done.tail.lineSequence().forEach { line ->
                    if (line.startsWith("::oc restored ")) {
                        val fields = line.removePrefix("::oc restored ").split(' ')
                        if (fields.size == 2)
                            fields[1]
                                .toIntOrNull()
                                ?.takeIf { it >= 0 }
                                ?.let { counts[fields[0]] = it }
                    } else if (line.startsWith("::oc skipped ")) {
                        line
                            .removePrefix("::oc skipped ")
                            .takeIf { Regex("[A-Za-z0-9+.:-]{1,160}").matches(it) }
                            ?.let(skipped::add)
                    }
                }
                activeOperation = null
                jobs.dismiss()
                services.ensureSupervisor()
                LinuxBackupResult.Restored(LinuxRestoreSummary(counts, skipped))
            } finally {
                activeOperation = null
                deletePrivateTree(into)
                forgetChosen()
            }
        }

    override suspend fun stop(): Outcome<Unit> {
        val operation = activeOperation ?: return Outcome.Failed(Failure.Busy())
        val fresh = manager.readJob()
        if (!fresh.running || !fresh.tail.contains("::oc backup-operation $operation"))
            return Outcome.Failed(Failure.Busy())
        return when (val result = jobs.stop()) {
            is LinuxAnswer.Ok -> Outcome.Ok(Unit)
            is LinuxAnswer.Problem -> Outcome.Failed(Failure.Busy())
        }
    }

    override suspend fun forgetChosen() {
        val old = chosen
        chosen = null
        if (old != null) withContext(dispatchers.io) { old.file.delete() }
    }

    private suspend fun requireLinux() {
        if (manager.presence() != LinuxPresence.Ready) abort(LinuxBackupProblem.Missing)
    }

    private suspend fun runJob(
        kind: LinuxJobKind,
        operation: String,
        script: String,
        progress: (LinuxBackupProgress) -> Unit,
    ): LinuxJobState {
        when (val started = jobs.startScript(kind, script)) {
            is LinuxAnswer.Problem ->
                abort(
                    if (started.problem == LinuxProblem.Unavailable) LinuxBackupProblem.Missing
                    else LinuxBackupProblem.Busy
                )
            is LinuxAnswer.Ok -> Unit
        }
        activeOperation = operation
        val deadline = clock.now() + 1.hours
        var sawThisOperation = false
        var reads = 0
        while (clock.now() < deadline && reads++ < 6_000) {
            val state = jobs.readCurrent()
            sawThisOperation =
                sawThisOperation || state.tail.contains("::oc backup-operation $operation")
            if (!state.running && state.wasBusy && state.kind == kind)
                abort(LinuxBackupProblem.Busy)
            if (sawThisOperation && state.kind == kind) {
                progress(
                    LinuxBackupProgress(
                        if (kind == LinuxJobKind.Backup) LinuxBackupPhase.Preparing
                        else LinuxBackupPhase.Restoring,
                        percent = state.percent,
                    )
                )
                if (!state.running) {
                    if (state.status != LinuxJobStatus.Done)
                        abort(
                            if (state.wasBusy) LinuxBackupProblem.Busy
                            else if (kind == LinuxJobKind.Backup) LinuxBackupProblem.Job
                            else LinuxBackupProblem.Restore
                        )
                    return state
                }
            }
            wait.pause()
        }
        abort(
            if (kind == LinuxJobKind.Backup) LinuxBackupProblem.Job else LinuxBackupProblem.Restore
        )
    }

    private fun safeDir(relative: String): File {
        var at = BuiltinDiscovery.rootfs(app.filesDir)
        if (!Files.isDirectory(at.toPath(), LinkOption.NOFOLLOW_LINKS))
            abort(LinuxBackupProblem.Missing)
        relative.split('/').forEach { part ->
            at = File(at, part)
            if (!at.exists() && !Files.isSymbolicLink(at.toPath())) check(at.mkdir())
            if (!Files.isDirectory(at.toPath(), LinkOption.NOFOLLOW_LINKS))
                abort(LinuxBackupProblem.NotBackup)
        }
        return at
    }

    private fun available(dir: File): Long =
        (StatFs(dir.path).availableBytes - 64L * 1024 * 1024).coerceAtLeast(0)

    private fun pinnedDirectory(directory: File): ParcelFileDescriptor {
        val root = app.filesDir.absolutePath
        check(directory.absolutePath == root || directory.absolutePath.startsWith("$root/"))
        val relative = directory.absolutePath.removePrefix(root).trim('/')
        fun open(path: String): ParcelFileDescriptor {
            val fd =
                Os.open(
                    path,
                    OsConstants.O_RDONLY or
                        OsConstants.O_NOFOLLOW or
                        OsConstants.O_CLOEXEC or
                        OsConstants.O_NONBLOCK,
                    0,
                )
            try {
                check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
                return ParcelFileDescriptor.dup(fd)
            } finally {
                Os.close(fd)
            }
        }
        var parent = open(root)
        try {
            relative
                .split('/')
                .filter { it.isNotEmpty() }
                .forEach { part ->
                    check(part != "." && part != "..")
                    val next = open("/proc/self/fd/${parent.fd}/$part")
                    parent.close()
                    parent = next
                }
            return parent
        } catch (error: Exception) {
            parent.close()
            throw error
        }
    }

    private fun createPrivateFile(file: File): java.io.FileOutputStream =
        pinnedDirectory(file.parentFile!!).use { parent ->
            val fd =
                Os.open(
                    "/proc/self/fd/${parent.fd}/${file.name}",
                    OsConstants.O_WRONLY or
                        OsConstants.O_CREAT or
                        OsConstants.O_EXCL or
                        OsConstants.O_NOFOLLOW or
                        OsConstants.O_CLOEXEC or
                        OsConstants.O_NONBLOCK,
                    384,
                )
            try {
                check(OsConstants.S_ISREG(Os.fstat(fd).st_mode) && Os.fstat(fd).st_nlink == 1L)
                ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(fd))
            } finally {
                Os.close(fd)
            }
        }

    private fun deletePrivateTree(file: File) {
        // A replaced parent causes cleanup to refuse; it never follows a guest-created link.
        pinnedDirectory(file.parentFile!!).use { parent -> deletePinnedChild(parent.fd, file.name) }
    }

    private fun deletePinnedChild(parent: Int, name: String) {
        val path = "/proc/self/fd/$parent/$name"
        val stat =
            try {
                Os.lstat(path)
            } catch (_: Exception) {
                return
            }
        if (OsConstants.S_ISDIR(stat.st_mode)) {
            val fd =
                Os.open(
                    path,
                    OsConstants.O_RDONLY or
                        OsConstants.O_NOFOLLOW or
                        OsConstants.O_CLOEXEC or
                        OsConstants.O_NONBLOCK,
                    0,
                )
            try {
                check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
                val held = ParcelFileDescriptor.dup(fd)
                held.use { dir ->
                    val names =
                        File("/proc/self/fd/${dir.fd}").list()
                            ?: throw java.io.IOException("unreadable staging")
                    names.forEach { deletePinnedChild(dir.fd, it) }
                }
            } finally {
                Os.close(fd)
            }
        }
        // Files.delete removes the final entry itself; it never follows a symbolic link.
        Files.delete(File(path).toPath())
    }

    private suspend fun boundary(
        default: LinuxBackupProblem,
        block: suspend () -> LinuxBackupResult,
    ): LinuxBackupResult =
        withContext(dispatchers.io) {
            var kind = default
            when (
                val result =
                    attempt({ cause ->
                        if (cause is BackupAbort) kind = cause.kind
                        Failure.StorageRefused()
                    }) {
                        block()
                    }
            ) {
                is Outcome.Ok -> result.value
                is Outcome.Failed -> LinuxBackupResult.Problem(kind, result.failure)
            }
        }

    private class BackupAbort(val kind: LinuxBackupProblem) : Exception()

    private fun abort(kind: LinuxBackupProblem): Nothing = throw BackupAbort(kind)
}

private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream, budget: Long) {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        check(total <= budget)
        output.write(buffer, 0, n)
    }
}
