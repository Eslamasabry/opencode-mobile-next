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
 * their files are in use while one does.
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

    /** Whether an entry last touched at [modifiedAt] may go at [now]. */
    fun old(modifiedAt: Long, now: Long): Boolean = modifiedAt < now - GRACE_MS

    /**
     * Deletes what is old inside [dir] (never [dir] itself, never anything a link points to), at most
     * [MAX_DELETIONS] entries. Returns how many went. Failures on single entries are skipped; a failure to walk the
     * folder at all is thrown for the caller to log.
     */
    fun clean(dir: File, now: Long = System.currentTimeMillis()): Int {
        if (!dir.isDirectory) return 0
        val root: Path = dir.toPath()
        var deleted = 0
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (deleted >= MAX_DELETIONS) return FileVisitResult.TERMINATE
                    if (old(attrs.lastModifiedTime().toMillis(), now) && tryDelete(file)) deleted++
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (deleted >= MAX_DELETIONS) return FileVisitResult.TERMINATE
                    // Only an emptied, old folder goes; delete refuses a non-empty one.
                    if (directory != root &&
                        old(Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS).toMillis(), now) &&
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
