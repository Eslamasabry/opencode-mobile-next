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
}
