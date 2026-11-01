package io.github.eslamasabry.opencode_mobile

/**
 * /proc changes while the installer's ownership check reads it. apt and dpkg
 * start and end hundreds of short processes a second; one that exits between
 * listing /proc and reading its `stat` fails the read with ESRCH (the kernel
 * already released it), and an orphan briefly shows as a zombie with an
 * empty environment before init reaps it. Treating either as an unknown
 * writer stopped the install mid-way (build 2370: "Setup stopped before it
 * finished" and "couldn't restart safely", docs/qa/fresh-setup-2026-10-16).
 *
 * Neither rule weakens the check: a process that really stays is seen again
 * on the next read and still fails it.
 */
internal object NativeProcRace {
    /** Consecutive failed checks before the installer is stopped: ~0.5 s at the 100 ms poll. */
    const val MONITOR_ATTEMPTS = 5

    /** Reads of a drain plan before its failure is final. */
    const val PLAN_ATTEMPTS = 4

    private const val MAX_CAUSE_DEPTH = 8

    /**
     * Whether a failed /proc read only means the process is gone: ESRCH (its
     * task was released after the open) or ENOENT (its directory went away).
     * Any other failure (EACCES, EIO, a parse error) is not proof and stays a
     * failure.
     */
    fun processGone(error: Throwable, errno: (Throwable) -> Int?, noSuchProcess: Int, noEntry: Int): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            errno(current)?.let { return it == noSuchProcess || it == noEntry }
            current = current.cause?.takeIf { it !== current }
            depth++
        }
        return false
    }

    /**
     * Runs [read] until it succeeds, at most [attempts] times, with [pause]
     * between tries; the last failure is thrown unchanged.
     */
    fun <T> settle(attempts: Int, pause: () -> Unit, read: () -> T): T {
        require(attempts >= 1)
        var last: Throwable? = null
        repeat(attempts) { attempt ->
            if (attempt > 0) pause()
            val result = runCatching(read)
            if (result.isSuccess) return result.getOrThrow()
            last = result.exceptionOrNull()
        }
        throw last!!
    }

    /** The monitor's count of consecutive failed checks: true once it should stop the installer. */
    class Strikes(private val limit: Int = MONITOR_ATTEMPTS) {
        init { require(limit >= 1) }
        var count = 0
            private set
        fun failed(): Boolean = ++count >= limit
        fun passed() { count = 0 }
    }
}

/**
 * The installer's own safety check stopped it (see BuiltinLinux's ownership
 * monitor): the install was interrupted by the app, not refused by its
 * script, so the setup runner may run the step again. The cause chain is
 * kept, so a real ownership failure is still recognised as one.
 */
internal class InstallerInterruptedException(message: String, cause: Throwable) : IllegalStateException(message, cause)
