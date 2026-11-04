package io.github.eslamasabry.oc.feature.linux.manager

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Why the manager could not do something, in a fixed vocabulary the screens turn into words
 * (`LinuxProblem`).
 */
public enum class LinuxProblem {
    /** Another phone task holds the installer slot. */
    Busy,

    /** Phone setup (or repair) is running. */
    SetupRunning,

    /** A package job is running. */
    JobRunning,

    /** Linux is not installed or not available on this device. */
    Unavailable,

    /** The act ran and did not finish. */
    Failed,
}

/** A manager answer: the value, or a problem with optional technical text for Details only. */
public sealed interface LinuxAnswer<out T> {
    public data class Ok<out T>(val value: T) : LinuxAnswer<T>

    public data class Problem(val problem: LinuxProblem, val details: Redacted? = null) :
        LinuxAnswer<Nothing>
}

public inline fun <T, R> LinuxAnswer<T>.then(next: (T) -> LinuxAnswer<R>): LinuxAnswer<R> =
    when (this) {
        is LinuxAnswer.Ok -> next(value)
        is LinuxAnswer.Problem -> this
    }

/**
 * The pause before trying a passing failure again; bound by the app graph (a feature may not import
 * delay, K1).
 */
public fun interface LinuxRetryWait {
    public suspend fun pause()
}

/**
 * The manager of the app's own Linux (port of lib/builtin/linux_manager/linux_manager.dart). Short
 * work goes through [LinuxHost.run], which takes the installer slot phone setup uses; long work
 * runs as the service [JOB_SERVICE], which survives leaving the page, logs its progress and stops
 * itself after [LinuxPackages.JOB_BUDGET_SECONDS].
 */
public class LinuxManager(
    private val host: LinuxHost,
    private val retryWait: LinuxRetryWait,
    private val details: io.github.eslamasabry.oc.feature.linux.host.LinuxLogDetails? = null,
) {
    public suspend fun presence(): LinuxPresence = host.presence()

    /**
     * Runs [script]; a passing failure (another phone task, or a moment when ownership of every
     * program could not be proved) is tried twice more before the page says "busy" (B-LINUX-092). A
     * non-zero exit is [LinuxProblem.Failed].
     */
    internal suspend fun run(script: String, timeoutSeconds: Long = 60): LinuxAnswer<String> {
        var attempt = 1
        while (true) {
            when (val result = host.run(script, timeoutSeconds)) {
                is Outcome.Ok ->
                    return if (result.value.exitCode == 0) {
                        LinuxAnswer.Ok(result.value.output)
                    } else {
                        LinuxAnswer.Problem(LinuxProblem.Failed)
                    }
                is Outcome.Failed -> {
                    val passing = passing(result.failure)
                    if (passing && attempt < ATTEMPTS) {
                        attempt++
                        retryWait.pause()
                        continue
                    }
                    return LinuxAnswer.Problem(
                        if (passing) LinuxProblem.Busy else LinuxProblem.Unavailable,
                        result.failure.details,
                    )
                }
            }
        }
    }

    /**
     * [LinuxProblem.SetupRunning] while setup runs, [LinuxProblem.JobRunning] while a package job
     * runs.
     */
    public suspend fun requireQuiet(): LinuxAnswer<Unit> =
        when {
            host.setupRunning() -> LinuxAnswer.Problem(LinuxProblem.SetupRunning)
            host.serviceRunning(JOB_SERVICE) -> LinuxAnswer.Problem(LinuxProblem.JobRunning)
            else -> LinuxAnswer.Ok(Unit)
        }

    // --- Ports

    /**
     * Every process's command first, then the probe with the ports they name (Flutter
     * `scanPorts(scan)`): a port only a launcher's command names is still tried, and its owner can
     * be that launcher (B-LINUX-085).
     */
    public suspend fun scanPorts(): LinuxAnswer<List<LinuxPort>> =
        run(LinuxPortScan.PROCESS_SCRIPT).then { procs ->
            val cmds = LinuxPortScan.parseCommands(procs)
            run(LinuxPortScan.script(LinuxPortScan.candidates(cmds))).then {
                LinuxAnswer.Ok(LinuxPortScan.parse(it, cmds))
            }
        }

    /** Full identities are captured before a port-owner stop is offered. */
    public suspend fun scanProcesses():
        LinuxAnswer<io.github.eslamasabry.oc.engine.runtime.procs.LinuxProcessScan> =
        run(io.github.eslamasabry.oc.engine.runtime.procs.LINUX_PROCESS_SCAN_SCRIPT).then {
            LinuxAnswer.Ok(io.github.eslamasabry.oc.engine.runtime.procs.LinuxProcessScan.parse(it))
        }

    public suspend fun stopIdentities(
        identities: List<String>
    ): LinuxAnswer<io.github.eslamasabry.oc.engine.runtime.procs.LinuxStopResult> =
        when (
            val script = io.github.eslamasabry.oc.engine.runtime.procs.linuxStopScript(identities)
        ) {
            is Outcome.Failed -> LinuxAnswer.Problem(LinuxProblem.Failed)
            is Outcome.Ok ->
                run(script.value).then { output ->
                    val result =
                        io.github.eslamasabry.oc.engine.runtime.procs.LinuxStopResult.parse(output)
                    if (result.outcomes.keys.containsAll(identities)) LinuxAnswer.Ok(result)
                    else LinuxAnswer.Problem(LinuxProblem.Failed)
                }
        }

    // --- Storage

    public suspend fun measureStorage(): LinuxAnswer<LinuxStorageReport> =
        run(LinuxStorage.script, timeoutSeconds = 180).then {
            LinuxAnswer.Ok(LinuxStorage.report(it))
        }

    /** Bytes [action] would give back now. */
    public suspend fun cleanableBytes(action: LinuxCleanupAction): LinuxAnswer<Long> =
        run(action.measureScript, timeoutSeconds = 120).then { out ->
            LinuxAnswer.Ok(LinuxStorage.parseDu(out).values.sum() * 1024L)
        }

    /**
     * Runs [action] and returns the bytes it freed (two readings). Refuses an action that names an
     * unsafe path.
     */
    public suspend fun cleanup(action: LinuxCleanupAction): LinuxAnswer<Long> {
        if (!action.paths.all(LinuxStorage::cleanupPathIsSafe))
            return LinuxAnswer.Problem(LinuxProblem.Failed)
        return requireQuiet()
            .then { run(action.measureScript, 120) }
            .then { before ->
                run(action.script, 300)
                    .then { run(action.measureScript, 120) }
                    .then { after -> LinuxAnswer.Ok(before to after) }
            }
            .then { (before, after) ->
                LinuxAnswer.Ok(
                    LinuxStorage.freedBytes(
                        LinuxStorage.parseDu(before),
                        LinuxStorage.parseDu(after),
                    )
                )
            }
    }

    // --- Packages

    public suspend fun installedPackages(): LinuxAnswer<List<LinuxPackage>> =
        run(LinuxPackages.LIST_SCRIPT).then { LinuxAnswer.Ok(LinuxPackages.parseInstalled(it)) }

    public suspend fun checkPackages(
        names: List<String>
    ): LinuxAnswer<List<LinuxPackageCandidate>> =
        run(LinuxPackages.checkScript(names)).then {
            LinuxAnswer.Ok(LinuxPackages.parseCandidates(it))
        }

    /** The packages apt would remove along with [names]. */
    public suspend fun planRemoval(names: List<String>): LinuxAnswer<List<String>> =
        run(LinuxPackages.removePlanScript(names)).then {
            LinuxAnswer.Ok(LinuxPackages.parseRemovePlan(it))
        }

    // --- The long job

    public suspend fun startJob(
        kind: LinuxJobKind,
        names: List<String>,
        notice: String,
    ): LinuxAnswer<Unit> = startScriptJob(LinuxPackages.jobScript(kind, names), notice)

    /** Starts [script] (a [LinuxPackages.jobFrame]) as the one long job. */
    public suspend fun startScriptJob(script: String, notice: String): LinuxAnswer<Unit> =
        requireQuiet()
            // Taking the slot once proves nothing else holds it right now.
            .then { run("exit 0", timeoutSeconds = 12) }
            .then {
                when (val started = host.startService(JOB_SERVICE, script, notice)) {
                    is Outcome.Ok -> LinuxAnswer.Ok(Unit)
                    is Outcome.Failed ->
                        LinuxAnswer.Problem(LinuxProblem.Unavailable, started.failure.details)
                }
            }

    public suspend fun stopJob(): LinuxAnswer<Unit> =
        when (val stopped = host.stopService(JOB_SERVICE)) {
            is Outcome.Ok -> LinuxAnswer.Ok(Unit)
            is Outcome.Failed -> LinuxAnswer.Problem(LinuxProblem.Failed, stopped.failure.details)
        }

    /**
     * Status first, then the log (as Flutter reads them): a job that writes its exit marker and
     * stops between the two reads is then read as still running, so the next poll reads its result
     * instead of "interrupted" (B-LINUX-091).
     */
    public suspend fun readJob(): LinuxJobState {
        val running = host.serviceRunning(JOB_SERVICE)
        val state =
            LinuxJobState.fromLog(host.serviceLog(JOB_SERVICE, JOB_LOG_TAIL), running = running)
        return state.copy(
            details = state.tail.takeIf { it.isNotBlank() }?.let { details?.redact(it) }
        )
    }

    public companion object {
        /** The Android service name of the long job (frozen: the Flutter app's `linux-job`). */
        public const val JOB_SERVICE: String = "linux-job"
        private const val JOB_LOG_TAIL = 24_576
        private const val ATTEMPTS = 3

        /** How long [LinuxRetryWait] pauses between tries (Flutter linux_manager retry: 700 ms). */
        public val RETRY_DELAY: Duration = 700.milliseconds

        internal fun passing(failure: Failure): Boolean =
            failure is Failure.Runtime &&
                (failure.problem == RuntimeProblem.OwnershipUnknown ||
                    failure.problem == RuntimeProblem.SlotBusy)
    }
}
