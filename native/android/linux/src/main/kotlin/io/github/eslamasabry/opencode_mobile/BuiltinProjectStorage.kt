package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * App-private projects survive removal of the disposable Linux runtime.
 *
 * The caller must stop runtime processes and serialize every lifecycle operation.
 * [inspect] must lstat without following links, return null only for an absent
 * entry, and throw on every other failure. App-provided filesDir/cache anchors
 * are trusted; managed ancestors below them must be actual directories.
 * No manifest, project names, contents or raw filesystem errors are persisted.
 */
class BuiltinProjectStorage(
    val filesDir: File,
    val archive: File,
    private val inspect: (File) -> Entry?,
) {
    enum class Kind { DIRECTORY, FILE, LINK, OTHER }

    data class Entry(val kind: Kind, val bytes: Long)

    /** Logical regular-file bytes; links and special files contribute zero. */
    data class Measurement(val runtimeBytes: Long, val projectsBytes: Long)

    val projects = File(filesDir, "projects")
    val rootfs = File(filesDir, "linux/ubuntu")
    private val linux = File(filesDir, "linux")
    private val root = File(rootfs, "root")
    private val legacy = File(root, "projects")

    /** Atomic whole-directory migration; repeated calls recover by location. */
    fun prepare(): Unit = guarded { prepareInternal() }

    /** Fresh, strict measurement. An unsafe/unknown location never means zero. */
    fun measure(): Measurement = guarded {
        validateLocations()
        rejectCollision()
        val legacyBytes = bytes(legacy)
        val allRuntimeBytes = add(bytes(linux), bytes(archive))
        check(allRuntimeBytes >= legacyBytes)
        Measurement(
            runtimeBytes = allRuntimeBytes - legacyBytes,
            projectsBytes = add(bytes(projects), legacyBytes),
        )
    }

    /** Preserve projects before an installer replaces even a partial rootfs. */
    fun resetRootfs(): Unit = guarded {
        prepareInternal()
        deleteTree(rootfs)
    }

    /** Default removal keeps all projects, including previously unmigrated ones. */
    fun removeRuntime(alsoDeleteProjects: Boolean = false): Unit = guarded {
        prepareInternal()
        requireDirectory(archive.parentFile ?: error("Invalid anchor"))
        // A partial deletion must not leave a damaged runtime marked ready.
        deleteTree(File(linux, "ubuntu.ready"))
        deleteTree(linux)
        deleteTree(archive)
        if (alsoDeleteProjects) deleteTree(projects)
    }

    private fun prepareInternal() {
        validateLocations()
        rejectCollision()
        if (inspect(legacy) != null && children(legacy).isNotEmpty()) {
            // A missing or empty destination is the only safe rename target.
            // Links may spell the folder either way (/data/user/0 or /data/data).
            val before = listOf(legacy.absolutePath, legacy.canonicalPath)
            if (inspect(projects) != null) check(projects.delete())
            check(legacy.renameTo(projects))
            val after = listOf(projects.absolutePath, projects.canonicalPath)
            repointMovedLinks(before.zip(after).distinct())
        }
        makeDirectory(projects)
        if (inspect(rootfs) != null) {
            makeDirectory(root)
            makeDirectory(legacy)
        }
    }

    /**
     * proot's link2symlink made every hard link (each Git object, for one)
     * a link holding the absolute path it was made at. After the move above
     * those still name the old folder, and Git says `bad object HEAD`; each
     * link into the old folder is pointed at the same place in the new one.
     * Best effort: an entry that can't be read or changed keeps its link,
     * and nothing here can stop the move from being used.
     */
    private fun repointMovedLinks(moved: List<Pair<String, String>>) {
        try {
            Files.walkFileTree(projects.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isSymbolicLink) repoint(file, moved)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                    FileVisitResult.CONTINUE
            })
        } catch (_: Exception) {
        }
    }

    private fun repoint(link: Path, moved: List<Pair<String, String>>) {
        try {
            val target = Files.readSymbolicLink(link).toString()
            val (old, new) = moved.firstOrNull { (old, _) ->
                target == old || target.startsWith("$old/")
            } ?: return
            Files.delete(link)
            Files.createSymbolicLink(link, Paths.get(new + target.substring(old.length)))
        } catch (_: Exception) {
        }
    }

    private fun validateLocations() {
        requireDirectory(filesDir)
        requireDirectory(archive.parentFile ?: error("Invalid anchor"))
        optionalDirectory(projects)
        // Do not resolve children of an absent ancestor: inspect may correctly
        // return ENOENT, but traversing a symlink must never be permitted.
        if (optionalDirectory(linux)) {
            if (optionalDirectory(rootfs)) {
                if (optionalDirectory(root)) optionalDirectory(legacy)
            }
        }
    }

    private fun rejectCollision() {
        if (inspect(legacy) != null && inspect(projects) != null) {
            check(children(legacy).isEmpty() || children(projects).isEmpty())
        }
    }

    private fun optionalDirectory(file: File): Boolean {
        val entry = inspect(file) ?: return false
        check(entry.kind == Kind.DIRECTORY)
        return true
    }

    private fun requireDirectory(file: File) {
        check(optionalDirectory(file))
    }

    private fun makeDirectory(file: File) {
        if (inspect(file) == null) check(file.mkdir())
        requireDirectory(file)
    }

    private fun children(file: File): Array<File> {
        requireDirectory(file)
        return file.listFiles() ?: error("Cannot enumerate directory")
    }

    private fun bytes(file: File): Long {
        val entry = inspect(file) ?: return 0
        return when (entry.kind) {
            Kind.DIRECTORY -> children(file).fold(0L) { total, child ->
                add(total, bytes(child))
            }
            Kind.FILE -> entry.bytes.also { check(it >= 0) }
            Kind.LINK, Kind.OTHER -> 0
        }
    }

    private fun add(left: Long, right: Long): Long {
        check(left >= 0 && right >= 0 && left <= Long.MAX_VALUE - right)
        return left + right
    }

    private fun deleteTree(file: File) {
        val entry = inspect(file) ?: return
        if (entry.kind == Kind.DIRECTORY) {
            // Tools inside Ubuntu leave owner-read-only directories (Go's module
            // cache is 0555). The app owns them; restore owner access so one
            // such directory cannot make every removal fail. lstat above
            // established a real directory, so no link target is changed.
            file.setReadable(true, true)
            file.setWritable(true, true)
            file.setExecutable(true, true)
            children(file).forEach(::deleteTree)
        }
        // File.delete removes the link itself, never the linked target.
        check(file.delete())
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (_: Exception) {
        // No cause: native exception messages may contain private paths/data.
        throw IllegalStateException("Project storage could not be safely accessed.")
    }
}
