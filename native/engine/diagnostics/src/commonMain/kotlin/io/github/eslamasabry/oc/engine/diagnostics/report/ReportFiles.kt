package io.github.eslamasabry.oc.engine.diagnostics.report

/** The two files of the persisted problem report (Flutter: `report_problem.json` and `report_problem.pending`). */
public enum class ReportFile(public val fileName: String) {
    Snapshot("report_problem.json"),
    Pending("report_problem.pending"),
}

/**
 * The app-private diagnostics folder the persisted problem report lives in (port of `ReportProblem`'s two files).
 *
 * Synchronous on purpose, like Flutter's bounded synchronous IO: a fatal error handler cannot rely on a queue draining
 * before the process dies, so a returned record call is already on disk. Every file is at most the report's byte cap.
 * Any problem is thrown; [ReportProblem] turns it into a fixed `Failure.StorageRefused` and never shows its text or a path.
 *
 * Android: `filesDir/diagnostics/`, owner-only files; [writePending] writes and `fd.sync()`s the pending file,
 * [commitPending] renames it over the snapshot (atomic on one file system); symbolic links are not followed.
 */
public interface ReportFiles {
    /** The file's size in bytes, or null when it does not exist. */
    public fun size(file: ReportFile): Long?

    /** The whole file. Only called after [size] answered a size within the cap. */
    public fun read(file: ReportFile): ByteArray

    /** Writes [bytes] to [ReportFile.Pending] and flushes them to the device. */
    public fun writePending(bytes: ByteArray)

    /** Atomically replaces [ReportFile.Snapshot] with [ReportFile.Pending]. */
    public fun commitPending()

    /** Removes the file; an absent file is fine. */
    public fun delete(file: ReportFile)
}

/** A tiny non-reentrant lock for the synchronous report paths (common code has no `synchronized`). */
internal class SpinLock {
    @PublishedApi internal val held: kotlin.concurrent.atomics.AtomicBoolean = kotlin.concurrent.atomics.AtomicBoolean(false)

    inline fun <T> withLock(block: () -> T): T {
        while (!held.compareAndSet(expectedValue = false, newValue = true)) Unit
        try {
            return block()
        } finally {
            held.store(false)
        }
    }
}
