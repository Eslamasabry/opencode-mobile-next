package io.github.eslamasabry.oc.android.platform

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.lifecycle.AppStorageFacts
import io.github.eslamasabry.oc.engine.lifecycle.ManageSpaceStorage
import io.github.eslamasabry.oc.engine.lifecycle.ProjectStorageScanner
import io.github.eslamasabry.oc.engine.lifecycle.StorageEntryKind
import io.github.eslamasabry.oc.engine.lifecycle.StorageExport
import io.github.eslamasabry.oc.engine.lifecycle.StorageTree
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Storage-only window service. It never opens a graph, secure preferences, or a runtime. */
public class AndroidManageSpaceStorage(
    context: Context,
    private val dispatchers: AppDispatchers,
    private val savedServers: () -> Int,
) : ManageSpaceStorage {
    private val context = context.applicationContext
    private var activity = WeakReference<ComponentActivity>(null)
    private var launcher: ActivityResultLauncher<String>? = null
    private var destination: CompletableDeferred<Uri?>? = null
    private val cancelled = AtomicBoolean(false)
    private val scanner = ProjectStorageScanner(AndroidStorageTree)

    /** Fixed result key survives recreation while the same ViewModel owns the waiting export. */
    public fun attach(host: ComponentActivity) {
        activity = WeakReference(host)
        launcher =
            host.activityResultRegistry.register(
                PICKER_KEY,
                host,
                ActivityResultContracts.CreateDocument("application/zip"),
            ) { uri ->
                destination?.complete(uri)
            }
    }

    public fun detach(host: ComponentActivity) {
        if (activity.get() === host) {
            activity.clear()
            launcher = null
        }
        if (host.isFinishing) {
            destination?.complete(null)
            cancelExport()
        }
    }

    public fun finish() {
        activity.get()?.finish()
    }

    override suspend fun measure(): Outcome<Pair<AppStorageFacts, Long>> = io {
        scanner.scan(context.filesDir.path).withSavedServers(savedServers()) to
            cacheRoots().sumOf(::measureCache)
    }

    override suspend fun clearCache(): Outcome<Long> = io {
        cacheRoots().sumOf { root ->
            children(root).sumOf { file ->
                if (kept(root, file)) 0 else size(file).also { delete(file) }
            }
        }
    }

    override suspend fun deleteAll(): Outcome<Unit> =
        withContext(dispatchers.main) {
            try {
                val manager = context.getSystemService(ActivityManager::class.java)
                if (manager?.clearApplicationUserData() == true) Outcome.Ok(Unit)
                else Outcome.Failed(Failure.StorageRefused())
            } catch (_: Exception) {
                Outcome.Failed(Failure.StorageRefused())
            }
        }

    override fun cancelExport() {
        cancelled.set(true)
        destination?.complete(null)
    }

    override suspend fun export(
        includePrivate: Boolean,
        progress: (Long) -> Unit,
    ): Outcome<StorageExport?> {
        cancelled.set(false)
        val entries =
            when (val planned = io { scanner.plan(context.filesDir.path, includePrivate) }) {
                is Outcome.Failed -> return planned
                is Outcome.Ok ->
                    when (val plan = planned.value) {
                        is Outcome.Ok -> plan.value
                        is Outcome.Failed -> return plan
                    }
            }
        val target =
            withContext(dispatchers.main) {
                val pick = launcher ?: return@withContext null
                val answer = CompletableDeferred<Uri?>()
                destination = answer
                try {
                    pick.launch(
                        if (includePrivate) "opencode-projects-private.zip"
                        else "opencode-projects.zip"
                    )
                    answer.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } finally {
                    if (destination === answer) destination = null
                }
            } ?: return Outcome.Ok(null)
        return withContext(dispatchers.io) {
            var done = 0L
            try {
                val out =
                    context.contentResolver.openOutputStream(target, "wt") ?: throw IOException()
                ZipOutputStream(out.buffered(BUFFER)).use { zip ->
                    val buffer = ByteArray(BUFFER)
                    for (entry in entries) {
                        currentCoroutineContext().ensureActive()
                        if (cancelled.get()) throw ExportCancelled()
                        require(safeName(entry.name))
                        openRegularFileInside(File(entry.source)).use { input ->
                            zip.putNextEntry(ZipEntry(entry.name))
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                if (cancelled.get()) throw ExportCancelled()
                                val count = input.read(buffer)
                                if (count < 0) break
                                zip.write(buffer, 0, count)
                                done += count
                                progress(done)
                            }
                            zip.closeEntry()
                        }
                    }
                }
                Outcome.Ok(StorageExport(done, entries.size, includePrivate))
            } catch (e: CancellationException) {
                discard(target)
                throw e
            } catch (_: ExportCancelled) {
                discard(target)
                Outcome.Ok(null)
            } catch (_: Exception) {
                discard(target)
                Outcome.Failed(Failure.StorageRefused())
            }
        }
    }

    /** Held no-follow directory descriptors prevent a checked parent being swapped for a link. */
    private fun openRegularFileInside(file: File): FileInputStream {
        val path = file.absolutePath
        val root =
            listOf(context.filesDir.absolutePath, context.filesDir.canonicalPath).firstOrNull {
                path.startsWith("$it/")
            } ?: throw IOException()
        val relative = path.removePrefix("$root/")
        require(safeName(relative))
        val parts = relative.split('/')
        fun open(path: String, directory: Boolean): ParcelFileDescriptor {
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
                if (directory) check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
                return ParcelFileDescriptor.dup(fd)
            } finally {
                Os.close(fd)
            }
        }
        var parent = open(root, true)
        try {
            for (part in parts.dropLast(1)) {
                val next = open("/proc/self/fd/${parent.fd}/$part", true)
                parent.close()
                parent = next
            }
            val leaf = open("/proc/self/fd/${parent.fd}/${parts.last()}", false)
            try {
                val stat = Os.fstat(leaf.fileDescriptor)
                check(OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L)
                return ParcelFileDescriptor.AutoCloseInputStream(leaf)
            } catch (e: Exception) {
                leaf.close()
                throw e
            }
        } finally {
            parent.close()
        }
    }

    private fun discard(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {}
    }

    private fun safeName(name: String): Boolean =
        !name.startsWith('/') &&
            '\\' !in name &&
            name.none { it.code < 32 || it.code == 127 } &&
            name.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun cacheRoots(): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir)

    private fun kept(root: File, file: File): Boolean =
        root == context.cacheDir && file.name == "proot-tmp"

    private fun children(folder: File): List<File> =
        folder.listFiles()?.toList() ?: if (folder.exists()) throw IOException() else emptyList()

    private fun measureCache(root: File): Long =
        children(root).sumOf { if (kept(root, it)) 0 else size(it) }

    private fun size(file: File): Long =
        when {
            Files.isSymbolicLink(file.toPath()) -> 0
            Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) -> file.length()
            Files.isDirectory(file.toPath(), NOFOLLOW_LINKS) -> children(file).sumOf(::size)
            else -> 0
        }

    private fun delete(file: File) {
        if (Files.isDirectory(file.toPath(), NOFOLLOW_LINKS)) children(file).forEach(::delete)
        check(file.delete() || !Files.exists(file.toPath(), NOFOLLOW_LINKS))
    }

    private suspend fun <T> io(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            try {
                Outcome.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Outcome.Failed(Failure.StorageRefused())
            }
        }

    private class ExportCancelled : Exception()

    private companion object {
        const val PICKER_KEY = "oc.manageSpace.export"
        const val BUFFER = 64 * 1024
    }
}

private object AndroidStorageTree : StorageTree {
    override fun kind(path: String): StorageEntryKind =
        try {
            val mode = Os.lstat(path).st_mode
            when {
                OsConstants.S_ISREG(mode) -> StorageEntryKind.File
                OsConstants.S_ISDIR(mode) -> StorageEntryKind.Directory
                OsConstants.S_ISLNK(mode) -> StorageEntryKind.Link
                else -> StorageEntryKind.Other
            }
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.ENOENT || e.errno == OsConstants.ENOTDIR)
                StorageEntryKind.Missing
            else throw e
        }

    override fun list(path: String): List<String>? = File(path).list()?.toList()

    override fun size(path: String): Long? =
        try {
            Os.lstat(path).st_size
        } catch (_: ErrnoException) {
            null
        }

    override fun readLink(path: String): String? =
        try {
            Os.readlink(path)
        } catch (_: ErrnoException) {
            null
        }

    override fun realPath(path: String): String? =
        try {
            File(path).canonicalPath
        } catch (_: IOException) {
            null
        }
}
