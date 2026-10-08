package io.github.eslamasabry.opencode_mobile

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.Closeable
import java.io.File
import java.io.FileInputStream

/** Fixed installed-program journals only. Admission/installer lifetime belongs to the caller. */
internal class NativeComponentUpdateRecovery(
    private val filesDir: File,
    private val home: File,
    private val rootfs: File,
    private val fs: Fs = AndroidFs,
) {
    enum class Kind { DIRECTORY, REGULAR, SYMLINK, OTHER }
    data class Stat(val kind: Kind, val device: Long, val inode: Long, val size: Long) {
        fun sameObject(other: Stat?) = other != null && kind == other.kind &&
            device == other.device && inode == other.inode
    }
    interface ReceiptHandle : Closeable {
        fun stat(): Stat
        fun read(maximum: Int): ByteArray
    }
    interface Fs {
        fun lstat(file: File): Stat?
        fun openReceiptNoFollow(file: File): ReceiptHandle
        fun children(directory: File): List<File>
        fun rename(source: File, destination: File)
        fun unlink(file: File)
        fun rmdir(directory: File)
        fun syncDirectory(directory: File)
    }
    private data class Target(val guest: String, val kind: Kind, val link: Boolean = false)
    private val code = listOf(
        Target("/opt/opencode", Kind.DIRECTORY),
        Target("/opt/opencode2", Kind.DIRECTORY),
        Target("/opt/oc-claude/claude", Kind.REGULAR),
        Target("/home/oc/.local/share/oc-paseo/0.9.2-82d16f9c432d", Kind.DIRECTORY),
        Target("/home/oc/.local/share/oc-agents/claude/2.1.283", Kind.DIRECTORY),
    )
    private val links = listOf("/usr/local/bin/opencode", "/usr/local/bin/opencode2",
        "/usr/local/bin/claude", "/home/oc/.local/bin/paseo", "/home/oc/.local/bin/claude")
        .map { Target(it, Kind.REGULAR, link = true) }
    private val paths = Paths(filesDir, home, rootfs, fs)
    private val legacyLock = "/home/oc/.local/share/oc-agents/.lock-claude"

    /** Refuses unsafe ancestry/code roots even when there is no reachable journal. */
    fun hasPending(): Boolean = safe {
        validateCode()
        val pending = (code + links).any { target ->
            paths.resolve(target.guest)?.let { fs.lstat(paths.sibling(it, ".oc-pending")) != null } == true
        }
        val lock = paths.resolve(legacyLock)?.let { fs.lstat(it) }
        check(lock == null || lock.kind == Kind.DIRECTORY)
        pending || lock != null
    }

    /** Re-check the caller's exact quiescence proof before every filesystem write. */
    fun recoverAfterQuiescence(isQuiescent: () -> Boolean): Unit = safe {
        validateCode()
        if (!hasPending()) return@safe
        check(isQuiescent())
        val lock = paths.resolve(legacyLock)
        val legacy = lock?.let { fs.lstat(it) }
        if (lock != null && legacy != null) {
            check(legacy.kind == Kind.DIRECTORY && paths.checkedChildren(lock, legacy).isEmpty())
        }
        // Recover installed code before exposing/restoring any command link.
        for (target in code + links) recover(target, isQuiescent)
        if (lock != null) {
            val before = fs.lstat(lock)
            if (before != null) {
                check(before.kind == Kind.DIRECTORY && paths.checkedChildren(lock, before).isEmpty())
                mutate(lock, isQuiescent) { check(before.sameObject(fs.lstat(lock))); fs.rmdir(lock) }
                sync(lock.parentFile ?: error(FAILURE), isQuiescent)
            }
        }
    }

    private fun validateCode() {
        for (target in code) paths.resolve(target.guest)?.let { active ->
            fs.lstat(active)?.let { check(matches(it, target)) }
        }
    }

    private fun receipt(file: File): String {
        check(paths.validateAncestors(file.parentFile ?: error(FAILURE)))
        val before = fs.lstat(file) ?: error(FAILURE)
        check(before.kind == Kind.REGULAR && before.size in 1L..MAX_RECEIPT_BYTES.toLong())
        val bytes = fs.openReceiptNoFollow(file).use { descriptor ->
            val opened = descriptor.stat()
            check(opened.kind == Kind.REGULAR && opened.size in 1L..MAX_RECEIPT_BYTES.toLong() &&
                before.sameObject(opened))
            descriptor.read(RECEIPT_READ_LIMIT)
        }
        check(bytes.size in 1..MAX_RECEIPT_BYTES && before.sameObject(fs.lstat(file)))
        return when (bytes.toString(Charsets.US_ASCII)) {
            "existing", "existing\n" -> "existing"
            "new", "new\n" -> "new"
            else -> error(FAILURE)
        }
    }

    private fun recover(target: Target, quiescent: () -> Boolean) {
        val active = paths.resolve(target.guest) ?: return
        val pending = paths.sibling(active, ".oc-pending")
        val pendingStat = fs.lstat(pending) ?: return
        fs.lstat(active)?.let { check(matches(it, target)) }
        val intent = receipt(pending)
        val good = paths.sibling(active, ".oc-good")
        if (intent == "existing") {
            val backup = fs.lstat(good)
            if (backup != null) {
                check(matches(backup, target))
                remove(active, quiescent)
                mutate(good, quiescent) {
                    check(backup.sameObject(fs.lstat(good)) && fs.lstat(active) == null)
                    fs.rename(good, active)
                }
            } else check(fs.lstat(active)?.let { matches(it, target) } == true)
        } else {
            fs.lstat(active)?.let { check(matches(it, target)) }
            remove(active, quiescent)
        }
        val parent = active.parentFile ?: error(FAILURE)
        // A failed pre-clear sync keeps the journal available for idempotent retry.
        sync(parent, quiescent)
        mutate(pending, quiescent) {
            check(pendingStat.sameObject(fs.lstat(pending)))
            fs.unlink(pending)
        }
        sync(parent, quiescent)
    }

    private fun matches(stat: Stat, target: Target) =
        if (target.link) stat.kind in setOf(Kind.SYMLINK, Kind.REGULAR) else stat.kind == target.kind

    private fun remove(
        file: File, quiescent: () -> Boolean, depth: Int = 0, budget: IntArray = intArrayOf(MAX_REMOVE_ENTRIES),
    ) {
        check(paths.validateAncestors(file.parentFile ?: error(FAILURE)))
        val before = fs.lstat(file) ?: return
        check(depth <= MAX_REMOVE_DEPTH && --budget[0] >= 0)
        if (before.kind == Kind.DIRECTORY) {
            for (child in paths.checkedChildren(file, before)) remove(child, quiescent, depth + 1, budget)
            mutate(file, quiescent) { check(before.sameObject(fs.lstat(file))); fs.rmdir(file) }
        } else {
            check(before.kind in setOf(Kind.REGULAR, Kind.SYMLINK))
            // Unlink the symlink object, including a dangling guest command link.
            mutate(file, quiescent) { check(before.sameObject(fs.lstat(file))); fs.unlink(file) }
        }
    }

    private inline fun mutate(file: File, quiescent: () -> Boolean, action: () -> Unit) {
        check(paths.validateAncestors(file.parentFile ?: error(FAILURE)))
        check(quiescent())
        action()
    }
    private fun sync(directory: File, quiescent: () -> Boolean) {
        check(paths.validateAncestors(directory) && quiescent())
        fs.syncDirectory(directory)
    }
    private inline fun <T> safe(action: () -> T): T = try { action() }
        catch (_: Exception) { throw IllegalStateException(FAILURE) }

    companion object {
        private const val MAX_RECEIPT_BYTES = 16
        private const val RECEIPT_READ_LIMIT = MAX_RECEIPT_BYTES + 1
        private const val MAX_REMOVE_ENTRIES = 100_000
        private const val MAX_REMOVE_DEPTH = 64
        const val FAILURE = "A component update could not be restored. Run setup again."
    }

    private class Paths(
        private val filesDir: File, private val home: File,
        private val rootfs: File, private val fs: Fs,
    ) {
        fun resolve(guest: String): File? {
            check(guest.startsWith('/') && guest.split('/').drop(1).all { it.isNotEmpty() && it !in setOf(".", "..") })
            return if (!validateAncestors(rootfs)) {
                null
            } else {
                val segments = guest.removePrefix("/").split('/')
                var parent: File? = rootfs
                for (segment in segments.dropLast(1)) {
                    val next = File(checkNotNull(parent), segment)
                    val stat = fs.lstat(next)
                    if (stat == null) {
                        parent = null
                        break
                    }
                    check(stat.kind == Kind.DIRECTORY)
                    parent = next
                }
                parent?.let { File(it, segments.last()) }
            }
        }

        /** Anchor at app-authored filesDir; walk every private ancestor without following links. */
        fun validateAncestors(directory: File): Boolean {
            val base = filesDir.absoluteFile
            val homePath = home.absoluteFile
            val root = rootfs.absoluteFile
            val destination = directory.absoluteFile
            fun normalized(file: File) = file.toPath().normalize().toFile() == file
            check(listOf(base, homePath, root, destination).all(::normalized))
            check(homePath.path.startsWith(base.path + File.separator) &&
                root.path.startsWith(homePath.path + File.separator))
            check(destination == root || destination.path.startsWith(root.path + File.separator))
            val baseStat = fs.lstat(base)
            return if (baseStat == null) {
                false
            } else {
                check(baseStat.kind == Kind.DIRECTORY)
                var current = base
                var complete = true
                val segments = destination.path.removePrefix(base.path + File.separator).split(File.separatorChar)
                for (segment in segments) {
                    current = File(current, segment)
                    val stat = fs.lstat(current)
                    if (stat == null) {
                        complete = false
                        break
                    }
                    check(stat.kind == Kind.DIRECTORY)
                }
                complete
            }
        }

        fun sibling(file: File, suffix: String) = File(file.path + suffix)

        fun checkedChildren(directory: File, before: Stat): List<File> {
            check(validateAncestors(directory) && before.kind == Kind.DIRECTORY &&
                before.sameObject(fs.lstat(directory)))
            val children = fs.children(directory)
            check(before.sameObject(fs.lstat(directory)))
            check(children.all { it.parentFile == directory && it.name.isNotEmpty() && it.name !in setOf(".", "..") })
            return children
        }
    }

    internal object AndroidFs : Fs {
        private fun stat(value: android.system.StructStat): Stat = Stat(when {
            OsConstants.S_ISDIR(value.st_mode) -> Kind.DIRECTORY
            OsConstants.S_ISREG(value.st_mode) -> Kind.REGULAR
            OsConstants.S_ISLNK(value.st_mode) -> Kind.SYMLINK
            else -> Kind.OTHER
        }, value.st_dev, value.st_ino, value.st_size)
        override fun lstat(file: File): Stat? = try { stat(Os.lstat(file.absolutePath)) }
            catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) null else throw error }
        override fun openReceiptNoFollow(file: File): ReceiptHandle {
            val descriptor = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            val stream = FileInputStream(descriptor)
            return object : ReceiptHandle {
                override fun stat() = stat(Os.fstat(descriptor))
                override fun read(maximum: Int): ByteArray {
                    check(maximum in 1..RECEIPT_READ_LIMIT)
                    val bytes = ByteArray(maximum)
                    var total = 0
                    while (total < maximum) {
                        val count = stream.read(bytes, total, maximum - total)
                        if (count < 0) break
                        check(count > 0)
                        total += count
                    }
                    return bytes.copyOf(total)
                }
                override fun close() = stream.close()
            }
        }
        override fun children(directory: File): List<File> {
            val before = lstat(directory) ?: error(FAILURE)
            check(before.kind == Kind.DIRECTORY)
            // The public Android SDK omits O_DIRECTORY. Never block on a raced
            // FIFO; require the opened no-follow descriptor to be this directory.
            val descriptor = Os.open(directory.absolutePath,
                OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
            try {
                check(before.kind == Kind.DIRECTORY && before.sameObject(stat(Os.fstat(descriptor))))
                val children = directory.listFiles()?.toList() ?: error(FAILURE)
                check(before.sameObject(lstat(directory)))
                return children
            } finally { Os.close(descriptor) }
        }
        override fun rename(source: File, destination: File) = Os.rename(source.absolutePath, destination.absolutePath)
        override fun unlink(file: File) {
            check(lstat(file)?.kind in setOf(Kind.REGULAR, Kind.SYMLINK))
            Os.remove(file.absolutePath)
        }
        override fun rmdir(directory: File) {
            check(lstat(directory)?.kind == Kind.DIRECTORY)
            Os.remove(directory.absolutePath)
        }
        override fun syncDirectory(directory: File) {
            val before = lstat(directory) ?: error(FAILURE)
            check(before.kind == Kind.DIRECTORY)
            val descriptor = Os.open(directory.absolutePath,
                OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
            try {
                check(before.kind == Kind.DIRECTORY && before.sameObject(stat(Os.fstat(descriptor))))
                Os.fsync(descriptor)
            } finally { Os.close(descriptor) }
        }
    }
}
