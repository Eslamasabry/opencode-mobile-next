package io.github.eslamasabry.opencode_mobile

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * proot's scratch folder lives in `noBackupFilesDir/proot-tmp` (so Android's cache sweep cannot take it from a running
 * step), which also means nothing ever empties it. This empties it, but only when none of our proot processes runs:
 * their files are in use while one does. The rule that protects a live process does not depend on that check alone:
 * a `proot-<pid>-*` folder whose process still exists is never entered, so a proot started after the check (or a short
 * probe the check does not see) keeps its files even while this pass runs.
 */
internal object ProotTmpCleanup {
    /** Entries touched in the last minute are left alone: a start that raced the decision may already own them. */
    const val GRACE_MS: Long = 60_000L

    /** At most this many files and folders go in one pass; the rest wait for the next start. */
    const val MAX_DELETIONS: Int = 5_000

    /**
     * The rule: clean only when no proot of ours can be using the folder. Any one of these running keeps it
     * (the phone server and its services, an agent helper, the installer, a terminal session).
     */
    fun safeToClean(
        serviceRunning: Boolean,
        processRunning: Boolean,
        agentRunning: Boolean,
        installerRunning: Boolean,
        terminalLive: Boolean,
    ): Boolean = !(serviceRunning || processRunning || agentRunning || installerRunning || terminalLive)

    /**
     * proot makes one folder per process, `proot-<pid>-XXXXXX` (mkdtemp with its own pid), and keeps its loader and
     * bind stand-ins inside. Returns that pid for a top-level entry name, or null for any other name.
     */
    fun ownerPid(name: String): Long? = OWNER.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()

    private val OWNER = Regex("""proot-(\d+)-.*""")

    /** Whether process [pid] still exists (its /proc entry is there). Unknown counts as alive: the folder stays. */
    fun processAlive(pid: Long): Boolean = pid <= 0 || File("/proc/$pid").exists()

    /** Whether an entry last touched at [modifiedAt] may go at [now]. */
    fun old(modifiedAt: Long, now: Long): Boolean = modifiedAt < now - GRACE_MS

    /**
     * Deletes what is old inside [dir] (never [dir] itself, never anything a link points to), at most
     * [MAX_DELETIONS] entries. Returns how many went. Failures on single entries are skipped; a failure to walk the
     * folder at all is thrown for the caller to log.
     */
    fun clean(dir: File, now: Long = System.currentTimeMillis(), pidAlive: (Long) -> Boolean = ::processAlive): Int {
        val root: Path = dir.toPath()
        // A link in the folder's place is never walked: walkFileTree would visit the link itself as a file and delete it.
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return 0
        var deleted = 0
        // A folder's age is its age before its children went: deleting them touches it.
        val ages = ArrayDeque<Long>()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(directory: Path, attrs: BasicFileAttributes): FileVisitResult {
                    ages.addLast(attrs.lastModifiedTime().toMillis())
                    // A folder a live proot process made is its own, whatever its age: it stays whole, even if the
                    // check that started this pass ran before that process did.
                    if (directory.parent == root) {
                        val pid = ownerPid(directory.fileName.toString())
                        if (pid != null && pidAlive(pid)) {
                            ages.removeLast()
                            return FileVisitResult.SKIP_SUBTREE
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (deleted >= MAX_DELETIONS) return FileVisitResult.TERMINATE
                    if (old(attrs.lastModifiedTime().toMillis(), now) && tryDelete(file)) deleted++
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    val age = ages.removeLastOrNull()
                    if (deleted >= MAX_DELETIONS) return FileVisitResult.TERMINATE
                    // Only an emptied, old folder goes; delete refuses a non-empty one.
                    if (directory != root && age != null && old(age, now) &&
                        tryDelete(directory)
                    ) {
                        deleted++
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        return deleted
    }

    private fun tryDelete(path: Path): Boolean =
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            false
        }
}
