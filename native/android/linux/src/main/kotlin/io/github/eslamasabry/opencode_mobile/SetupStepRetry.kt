package io.github.eslamasabry.opencode_mobile

/**
 * When the setup runner runs an install step again on its own. Only an
 * interruption qualifies: the app's own safety check stopped the step
 * ([InstallerInterruptedException]), or its programs ended by SIGTERM or
 * SIGKILL. A script that failed by itself (no network, no space, a broken
 * package) is reported at once; running it again would only hide why.
 * Install steps are safe to repeat: each begins by repairing an interrupted
 * apt or dpkg run (setup_scripts.dart, the repair prelude).
 */
internal object SetupStepRetry {
    /** Runs of one step, the first included. */
    const val ATTEMPTS = 3

    private val signalled = Regex("terminated with signal (?:9|15)\\b")

    fun interrupted(failure: String?): Boolean = failure != null && signalled.containsMatchIn(failure)

    /**
     * The failure of a step whose script never reported its result. PRoot
     * exits zero when its guest was signalled, so its own last line
     * ("proot info: vpid 1: terminated with signal 15") is what tells an
     * interruption from a script that ended early.
     */
    fun missingResult(last: String?): String =
        if (interrupted(last)) last!!.take(MAX_FAILURE_CHARS) else "The setup step could not finish. Try again."

    private const val MAX_FAILURE_CHARS = 300

    fun again(attempt: Int, cancelled: Boolean, interrupted: Boolean): Boolean =
        !cancelled && interrupted && attempt < ATTEMPTS

    /** A step's script ended with a segmentation fault (128 + SIGSEGV). */
    const val SEGFAULT_EXIT = 139

    /** The shell's own report of a program killed by SIGSEGV, as it reaches the step's output. */
    fun reportsSegfault(line: String): Boolean = line.contains("Segmentation fault")

    /**
     * A step that failed after its shell reported a segmentation fault counts
     * as one, even when the script turned the crash into its own exit status
     * (`if ! npm ci`, `[ "$(tool --version)" = ... ]`).
     */
    fun crashExit(exitCode: Int, sawSegfaultReport: Boolean): Int =
        if (exitCode != 0 && sawSegfaultReport) SEGFAULT_EXIT else exitCode

    /**
     * Run a step once more in compatibility mode (proot without seccomp
     * acceleration, glibc without rseq) when it died of a segmentation
     * fault and has not been run that way yet. Issue #117.
     */
    fun compatibility(exitCode: Int?, alreadyCompatible: Boolean, cancelled: Boolean): Boolean =
        !cancelled && !alreadyCompatible && exitCode == SEGFAULT_EXIT
}
