package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageFacts
import io.github.eslamasabry.oc.feature.phone.storage.StoredProject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import kotlinx.collections.immutable.toImmutableList

/** A planned archive member; no member name or source is ever logged. */
internal data class NativeExportEntry(val name: String, val source: File, val bytes: Long)

/**
 * Port of the Flutter project-export planner: regular files only, private exports opt in, proot Git
 * objects retained.
 */
internal class NativeProjectFiles(private val app: Application) {
    private val files = app.filesDir
    private val linux = File(files, "linux")
    private val guest = File(linux, "ubuntu/root")
    private val projectRoots
        get() = listOf(File(files, "projects"), File(guest, "projects"))

    fun measure(savedServers: Int): ProjectStorageFacts {
        val projects =
            tops(strict = false)
                .map { top ->
                    var bytes = 0L
                    var count = 0
                    var private = 0
                    walk(top, top.name, gitObjects = true) { name, _, size ->
                        bytes += size
                        count++
                        if (isPrivate(name)) private++
                    }
                    StoredProject(top.name, bytes, count, private)
                }
                .sortedBy { it.name.lowercase() }
        var serverBytes = 0L
        walk(linux, "linux") { _, _, size -> serverBytes += size }
        var legacyBytes = 0L
        walk(File(guest, "projects"), "projects") { _, _, size -> legacyBytes += size }
        return ProjectStorageFacts(
            projects = projects.toImmutableList(),
            serverInstalled = regular(File(linux, "ubuntu.ready")),
            serverBytes = (serverBytes - legacyBytes).coerceAtLeast(0),
            privateDataBytes = privateEntries(false).sumOf { it.bytes },
            savedServers = savedServers,
        )
    }

    fun plan(includePrivate: Boolean): List<NativeExportEntry> = buildList {
        tops(strict = true).forEach { top ->
            walk(top, "projects/${top.name}", strict = true, gitObjects = true) { name, source, size
                ->
                if (includePrivate || !isPrivate(name.removePrefix("projects/")))
                    add(NativeExportEntry(name, source, size))
            }
        }
        if (includePrivate) addAll(privateEntries(true))
    }

    private fun privateEntries(strict: Boolean): List<NativeExportEntry> = buildList {
        privateRoots.forEach { root ->
            walk(File(guest, root), "opencode/$root", strict, skip = setOf("bin", "log")) {
                name,
                source,
                size ->
                add(NativeExportEntry(name, source, size))
            }
        }
    }

    private fun tops(strict: Boolean): List<File> = buildList {
        val seen = mutableSetOf<String>()
        projectRoots.forEach { root ->
            if (!directory(root)) return@forEach
            val children =
                root.listFiles()
                    ?: if (strict) throw IOException("unreadable projects") else emptyArray()
            children.forEach { child ->
                if (!directory(child) && !regular(child)) return@forEach
                if (!seen.add(child.name)) {
                    if (strict) throw IOException("conflicting projects")
                } else add(child)
            }
        }
    }

    private fun walk(
        file: File,
        name: String,
        strict: Boolean = false,
        gitObjects: Boolean = false,
        skip: Set<String> = emptySet(),
        visit: (String, File, Long) -> Unit,
    ) {
        val mode =
            try {
                Os.lstat(file.path).st_mode
            } catch (_: Exception) {
                return
            }
        when {
            OsConstants.S_ISREG(mode) -> visit(name, file, file.length())
            OsConstants.S_ISLNK(mode) && gitObjects ->
                linkedObject(file)?.let { visit(name, it, it.length()) }
            OsConstants.S_ISDIR(mode) -> {
                val children =
                    file.listFiles()
                        ?: if (strict) throw IOException("unreadable folder") else return
                children.forEach { child ->
                    if (child.name !in skip && !(gitObjects && child.name.startsWith(".l2s."))) {
                        walk(child, "$name/${child.name}", strict, gitObjects, skip, visit)
                    }
                }
            }
        }
    }

    /**
     * Only proot's .l2s files under the app root can replace a linked Git object; arbitrary links
     * stay out.
     */
    private fun linkedObject(link: File): File? {
        val top = files.canonicalPath + File.separator
        var current = link
        repeat(8) {
            val target =
                try {
                    Os.readlink(current.path)
                } catch (_: Exception) {
                    return null
                }
            val leaf = File(target).name
            if (!leaf.startsWith(".l2s.")) return null
            var next =
                if (File(target).isAbsolute) File(target) else File(current.parentFile, target)
            if (!next.exists()) next = File(current.parentFile, leaf)
            val mode =
                try {
                    Os.lstat(next.path).st_mode
                } catch (_: Exception) {
                    return null
                }
            when {
                OsConstants.S_ISLNK(mode) -> current = next
                OsConstants.S_ISREG(mode) ->
                    return next.canonicalFile.takeIf { it.path.startsWith(top) }
                else -> return null
            }
        }
        return null
    }

    fun regular(file: File): Boolean =
        try {
            OsConstants.S_ISREG(Os.lstat(file.path).st_mode)
        } catch (_: Exception) {
            false
        }

    private fun directory(file: File): Boolean =
        try {
            OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)
        } catch (_: Exception) {
            false
        }

    /**
     * Pin every directory; O_NOFOLLOW/O_NONBLOCK reject replacement links and FIFOs during archive
     * streaming.
     */
    fun openRegularFileInside(file: File): FileInputStream {
        val root =
            listOf(files.absolutePath, files.canonicalPath).firstOrNull {
                file.absolutePath.startsWith(it + File.separator)
            } ?: throw IOException("source outside files")
        val relative = file.absolutePath.substring(root.length + 1)
        if (!safeName(relative)) throw IOException("invalid source")
        val parts = relative.split('/')
        var parent = open(File(root), true)
        try {
            for (part in parts.dropLast(1)) {
                val next = open(File("/proc/self/fd/${parent.fd}/$part"), true)
                parent.close()
                parent = next
            }
            val leaf = open(File("/proc/self/fd/${parent.fd}/${parts.last()}"), false)
            try {
                val stat = Os.fstat(leaf.fileDescriptor)
                if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_nlink != 1L)
                    throw IOException("unsupported source")
                return ParcelFileDescriptor.AutoCloseInputStream(leaf)
            } catch (error: Exception) {
                leaf.close()
                throw error
            }
        } finally {
            parent.close()
        }
    }

    private fun open(file: File, directory: Boolean): ParcelFileDescriptor {
        val flags =
            OsConstants.O_RDONLY or
                OsConstants.O_NOFOLLOW or
                OsConstants.O_CLOEXEC or
                OsConstants.O_NONBLOCK
        val fd = Os.open(file.path, flags, 0)
        try {
            if (directory && !OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
                throw IOException("unsupported directory")
            return ParcelFileDescriptor.dup(fd)
        } finally {
            Os.close(fd)
        }
    }

    /**
     * Cache is traversed through pinned descriptors; the live server's proot-tmp is always kept.
     */
    fun cache(clear: Boolean): Long =
        listOfNotNull(app.cacheDir, app.externalCacheDir).sumOf { root ->
            if (!directory(root)) 0L
            else
                open(root, true).use { parent ->
                    (File("/proc/self/fd/${parent.fd}").list()
                            ?: throw IOException("unreadable cache"))
                        .sumOf { name ->
                            if (root == app.cacheDir && name == "proot-tmp") 0L
                            else cacheChild(parent.fd, name, clear)
                        }
                }
        }

    private fun cacheChild(parent: Int, name: String, clear: Boolean): Long {
        val path = "/proc/self/fd/$parent/$name"
        val mode =
            try {
                Os.lstat(path).st_mode
            } catch (_: Exception) {
                return 0
            }
        return when {
            OsConstants.S_ISDIR(mode) -> {
                val bytes =
                    open(File(path), true).use { dir ->
                        (File("/proc/self/fd/${dir.fd}").list()
                                ?: throw IOException("unreadable cache"))
                            .sumOf { cacheChild(dir.fd, it, clear) }
                    }
                if (clear) Files.delete(File(path).toPath())
                bytes
            }
            else -> {
                val bytes = if (OsConstants.S_ISREG(mode)) Os.lstat(path).st_size else 0
                if (clear) Files.delete(File(path).toPath())
                bytes
            }
        }
    }

    companion object {
        private val privateNames =
            setOf(
                "auth.json",
                ".gitconfig",
                ".git-credentials",
                ".netrc",
                "_netrc",
                ".npmrc",
                ".pypirc",
                ".pgpass",
                ".dockercfg",
                "credentials",
                "credentials.json",
                "id_rsa",
                "id_dsa",
                "id_ecdsa",
                "id_ed25519",
            )
        private val privateFolders = setOf(".ssh", ".gnupg", ".aws", ".docker")
        private val privateExtensions = setOf("pem", "key", "p12", "pfx", "jks", "keystore")
        private val envTemplates =
            setOf(".env.example", ".env.sample", ".env.template", ".env.dist")
        private val privateRoots =
            listOf(
                ".local/share/opencode",
                ".config/opencode",
                ".oc-opencode2/config",
                ".oc-opencode2/data",
                ".oc-migration-exports",
            )

        fun isPrivate(relative: String): Boolean {
            val parts = relative.lowercase().split('/')
            val name = parts.last()
            return parts.any { it in privateFolders } ||
                name in privateNames ||
                name == ".env" ||
                (name.startsWith(".env.") && name !in envTemplates) ||
                name.substringAfterLast('.', "") in privateExtensions ||
                (".git" in parts && name in setOf("config", "config.worktree"))
        }

        fun safeName(name: String): Boolean =
            !name.startsWith('/') &&
                '\\' !in name &&
                name.none { it.code < 32 || it.code == 127 } &&
                name.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
}
