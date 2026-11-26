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

    private val prootSignal = Regex("^proot info: vpid [0-9]+: terminated with signal [0-9]+$")

    /**
     * The one kind of line of an agent step's output that may be kept: the scripts' own fixed `[oc] ` marker lines and
     * proot's report that its guest was signalled. Everything else a program wrote stays unread, so it cannot reach a
     * message, a log or a notice; these lines are what the retry rule above and the failure kind need.
     */
    fun agentLine(line: String): String? {
        val text = line.trim()
        return text.takeIf {
            it.length <= MAX_FAILURE_CHARS &&
                it.none { c -> c.code < 0x20 || c.code == 0x7f } &&
                (it.startsWith("[oc] ") || prootSignal.matches(it))
        }
    }

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

    /** Effective proot compatibility mode: explicit request or persisted rootfs marker (issue #151). */
    fun effectiveCompat(requestedCompat: Boolean, markerPresent: Boolean): Boolean =
        requestedCompat || markerPresent
}

/**
 * One bounded liveness probe through proot (issue #151): runs a trivial command in normal mode; if it hangs or fails,
 * kills that exact PID, persists the `proot-compat` marker, and probes once in compatibility mode. An existing marker
 * skips the normal-mode probe.
 */
internal object LinuxProotProbe {
    const val TIMEOUT_MS: Long = 20_000L
    const val TOKEN: String = "OC-PROOT-OK"
    const val COMMAND: String = "/bin/sh -c \"printf '%s\\n' '$TOKEN'\""
    const val STAGE_FALLBACK: String = "Checking Linux on this phone…"
    const val FAILURE_DETAIL: String = "Linux did not answer in normal or compatibility mode"

    enum class Mode(val label: String) {
        NORMAL("normal"),
        COMPAT("compat"),
    }

    sealed interface Attempt {
        data object Answered : Attempt

        data class Exited(val exitCode: Int) : Attempt

        data class Hung(val pid: Int?) : Attempt

        data object Cancelled : Attempt
    }

    sealed interface Result {
        data class Ready(
            val mode: Mode,
            val switchedToCompat: Boolean,
            val skippedNormal: Boolean,
            val detailLine: String,
        ) : Result

        data object Cancelled : Result

        data class Failed(
            val detailLine: String,
            val failure: String,
        ) : Result
    }

    interface Runner {
        fun hasCompatMarker(): Boolean

        fun writeCompatMarker()

        fun clearCompatMarker()

        fun runOnce(mode: Mode, timeoutMs: Long, killPid: (Int) -> Unit): Attempt
    }

    fun matchedOutput(exitCode: Int, output: String): Boolean =
        exitCode == 0 && output.lineSequence().any { it.trim() == TOKEN }

    /**
     * Signals only the verified exact [pid] ([sendTerm], brief wait, then [sendKill] if still the same process).
     * Never signals a reused PID.
     */
    fun stopExactProcess(
        pid: Int,
        isSameProcess: (Int) -> Boolean,
        waitExited: () -> Boolean,
        sendTerm: (Int) -> Unit,
        sendKill: (Int) -> Unit,
    ): Boolean =
        if (!isSameProcess(pid)) {
            false
        } else {
            sendTerm(pid)
            if (!waitExited() && isSameProcess(pid)) sendKill(pid)
            true
        }

    /** Whether the phone's Linux is currently running in compatible (slower) fallback mode. */
    fun isCompatibleFallback(compatMarker: Boolean, liveProotMode: String?): Boolean =
        compatMarker || liveProotMode == "ptrace"

    /**
     * B-486: User-triggered one-tap retry of fast Linux mode from Settings > Help. Clears any
     * saved `proot-compat` marker, runs one bounded probe in normal (fast) mode, and keeps fast
     * mode if it answers. If normal mode hangs or fails, falls back to compat mode and restores
     * the marker (and also restores it on cancel/failure when it was present before) so a working
     * compatible install is never left broken.
     */
    fun retryFast(
        runner: Runner,
        timeoutMs: Long = TIMEOUT_MS,
        onStage: (compatRetry: Boolean) -> Unit = {},
        killPid: (Int) -> Unit = {},
    ): Result {
        val hadCompat = runner.hasCompatMarker()
        runner.clearCompatMarker()
        val result = runFromNormal(runner, timeoutMs, onStage, killPid)
        if (hadCompat && (result is Result.Cancelled || result is Result.Failed)) {
            runner.writeCompatMarker()
        }
        return result
    }

    fun run(
        runner: Runner,
        timeoutMs: Long = TIMEOUT_MS,
        onStage: (compatRetry: Boolean) -> Unit = {},
        killPid: (Int) -> Unit = {},
    ): Result =
        if (runner.hasCompatMarker()) runWithExistingMarker(runner, timeoutMs, onStage, killPid)
        else runFromNormal(runner, timeoutMs, onStage, killPid)

    private fun runWithExistingMarker(
        runner: Runner,
        timeoutMs: Long,
        onStage: (compatRetry: Boolean) -> Unit,
        killPid: (Int) -> Unit,
    ): Result {
        onStage(false)
        return when (val attempt = runner.runOnce(Mode.COMPAT, timeoutMs, killPid)) {
            Attempt.Answered ->
                Result.Ready(
                    mode = Mode.COMPAT,
                    switchedToCompat = false,
                    skippedNormal = true,
                    detailLine = "[oc] Linux probe ok (mode=compat, marker present)",
                )
            Attempt.Cancelled -> Result.Cancelled
            is Attempt.Hung ->
                Result.Failed(
                    detailLine = "[oc] Linux probe failed (mode=compat, marker present, compat=timeout)",
                    failure = FAILURE_DETAIL,
                )
            is Attempt.Exited ->
                Result.Failed(
                    detailLine =
                        "[oc] Linux probe failed (mode=compat, marker present, compat=exit ${attempt.exitCode})",
                    failure = FAILURE_DETAIL,
                )
        }
    }

    private fun runFromNormal(
        runner: Runner,
        timeoutMs: Long,
        onStage: (compatRetry: Boolean) -> Unit,
        killPid: (Int) -> Unit,
    ): Result {
        onStage(false)
        val first = runner.runOnce(Mode.NORMAL, timeoutMs, killPid)
        return when (first) {
            Attempt.Answered ->
                Result.Ready(
                    mode = Mode.NORMAL,
                    switchedToCompat = false,
                    skippedNormal = false,
                    detailLine = "[oc] Linux probe ok (mode=normal)",
                )
            Attempt.Cancelled -> Result.Cancelled
            is Attempt.Hung -> retryInCompat(runner, timeoutMs, "timeout", onStage, killPid)
            is Attempt.Exited -> retryInCompat(runner, timeoutMs, "exit ${first.exitCode}", onStage, killPid)
        }
    }

    private fun retryInCompat(
        runner: Runner,
        timeoutMs: Long,
        firstReason: String,
        onStage: (compatRetry: Boolean) -> Unit,
        killPid: (Int) -> Unit,
    ): Result {
        runner.writeCompatMarker()
        onStage(true)
        return when (val second = runner.runOnce(Mode.COMPAT, timeoutMs, killPid)) {
            Attempt.Answered ->
                Result.Ready(
                    mode = Mode.COMPAT,
                    switchedToCompat = true,
                    skippedNormal = false,
                    detailLine = "[oc] Linux probe ok (mode=compat, after normal $firstReason)",
                )
            Attempt.Cancelled -> {
                runner.clearCompatMarker()
                Result.Cancelled
            }
            is Attempt.Hung -> {
                runner.clearCompatMarker()
                Result.Failed(
                    detailLine = "[oc] Linux probe failed (normal=$firstReason, compat=timeout)",
                    failure = FAILURE_DETAIL,
                )
            }
            is Attempt.Exited -> {
                runner.clearCompatMarker()
                Result.Failed(
                    detailLine = "[oc] Linux probe failed (normal=$firstReason, compat=exit ${second.exitCode})",
                    failure = FAILURE_DETAIL,
                )
            }
        }
    }
}
