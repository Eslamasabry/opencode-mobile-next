package io.github.eslamasabry.oc.engine.linuxsetup

import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * What stopped a setup step, as a typed reason the screen turns into plain words (port of the
 * `phoneSetupError*` copy in lib/builtin/setup/setup_engine/progress_helpers.dart). [name] is the component's short
 * title. No raw error text is ever part of a problem except [StartFailed.details], which is redacted.
 */
public sealed interface SetupProblem {
    public data class NoSpace(val name: String) : SetupProblem
    public data object Interrupted : SetupProblem
    public data class Offline(val name: String) : SetupProblem
    public data object NoInternet : SetupProblem
    public data class Checksum(val name: String) : SetupProblem
    public data class Install(val name: String) : SetupProblem
    public data object PackageRepair : SetupProblem
    public data object CannotStart : SetupProblem
    public data object OpenCodeNoProgram : SetupProblem
    public data object OpenCodeWontRun : SetupProblem
    public data object OpenCodeNoStart : SetupProblem

    /** The stable recovery code `runtime_ownership_unknown`: the page offers the recovery, not a retry. */
    public data object RuntimeOwnershipUnknown : SetupProblem

    /** The job could not even be handed over; [details] is redacted technical text for Details only. */
    public data class StartFailed(val details: String) : SetupProblem
}

/** The stable code a native ownership failure carries through the job and the finish. */
public const val RUNTIME_OWNERSHIP_UNKNOWN: String = "runtime_ownership_unknown"

private val legacyOwnershipUnknown = Regex("""\bownershipUnknown\b""")

/** Whether [error] is the native runtime-ownership failure, by its code or the legacy text marker. */
public fun isRuntimeOwnershipFailure(error: String?): Boolean =
    error != null && (error == RUNTIME_OWNERSHIP_UNKNOWN || legacyOwnershipUnknown.containsMatchIn(error))

/** The English words of each problem (lib/l10n/app_en.arb keys named in each branch). */
public object SetupProblemWordsEn {
    public fun of(problem: SetupProblem): String =
        when (problem) {
            is SetupProblem.NoSpace -> // phoneSetupErrorNoSpace
                "Not enough free space to install ${problem.name}. Free some storage, then continue setup."
            SetupProblem.Interrupted -> // phoneSetupErrorInterrupted
                "Setup stopped before it finished. Continue setup to try again. Completed steps will be kept."
            is SetupProblem.Offline -> "Could not download ${problem.name}: no internet connection" // phoneSetupErrorOffline
            SetupProblem.NoInternet -> "No internet connection. Continue when you're back online." // phoneSetupErrorNoInternet
            is SetupProblem.Checksum -> // phoneSetupErrorChecksum
                "The download of ${problem.name} was damaged. Continue to fetch it again."
            is SetupProblem.Install -> "Could not install ${problem.name}" // phoneSetupErrorInstall
            SetupProblem.PackageRepair -> // phoneSetupErrorPackageRepair
                "Some Linux packages need repair. Continue setup to try again. Details show what stopped it."
            SetupProblem.CannotStart -> // phoneSetupErrorCannotStart
                "OpenCode is installed but is not answering. Continue setup to try again. Details show what stopped it."
            SetupProblem.OpenCodeNoProgram -> // phoneSetupErrorOpenCodeNoProgram
                "OpenCode was downloaded, but its program was not in the download. Continue to fetch it again."
            SetupProblem.OpenCodeWontRun -> // phoneSetupErrorOpenCodeWontRun
                "OpenCode was downloaded, but its program does not run on this phone. Details show what it said."
            SetupProblem.OpenCodeNoStart -> // phoneSetupErrorOpenCodeNoStart
                "OpenCode was installed, but it did not start. Continue to try again; Details show what it said."
            SetupProblem.RuntimeOwnershipUnknown -> RUNTIME_OWNERSHIP_UNKNOWN
            is SetupProblem.StartFailed -> // phoneSetupErrorInstall('OpenCode'); the details stay under Details
                "Could not install OpenCode"
        }
}

/** A failed component in plain words, for its row ([component]) and for the whole job ([job]). */
public data class SetupFailureText(val component: SetupProblem, val job: SetupProblem)

private val offline =
    Regex(
        "Could not resolve|Temporary failure resolving|Unable to resolve host|" +
            "No address associated|Network is unreachable|Could not connect to|" +
            "Failed to connect|Connection timed out|timed out after|" +
            "curl: \\((6|7|28|35|52|56)\\)|curl exit (6|7|28|35|52|56)\\)|" +
            "UnknownHostException|SocketTimeoutException|ConnectException|" +
            "ECONNRESET|ETIMEDOUT|EAI_AGAIN|ENOTFOUND|Failed to fetch",
        RegexOption.IGNORE_CASE,
    )
private val noSpace = Regex("No space left on device|ENOSPC|not enough free space", RegexOption.IGNORE_CASE)
private val interrupted = Regex("""terminated with signal (?:9|15)\b|\bSIG(?:KILL|TERM)\b""", RegexOption.IGNORE_CASE)
private val checksum = Regex("checksum|did not match its checksum", RegexOption.IGNORE_CASE)

private val packageRepairMarkers =
    listOf(
        "[oc] Some packages are still unfinished.",
        "[oc] Package repair could not finish.",
        "[oc] A required package is not fully installed.",
    )

/**
 * Port of `describeSetupFailure`: only the end of the log counts (15 lines), so an old warning further up is not the
 * reason. A signal proves interruption, not its cause. The technical reason stays in the log tail (Details).
 */
public fun describeSetupFailure(
    failed: SetupJobComponent,
    logTail: String,
    title: String,
    step: Boolean = false,
): SetupFailureText {
    val reason = failed.error ?: ""
    fun both(problem: SetupProblem) = SetupFailureText(problem, problem)
    if (isRuntimeOwnershipFailure(reason)) return both(SetupProblem.RuntimeOwnershipUnknown)
    if (packageRepairMarkers.any { reason.startsWith(it) }) return both(SetupProblem.PackageRepair)
    if (step) return both(SetupProblem.CannotStart)
    when {
        reason.startsWith(OpenCodeInstallFailure.NO_PROGRAM) -> return both(SetupProblem.OpenCodeNoProgram)
        reason.startsWith(OpenCodeInstallFailure.WONT_RUN) -> return both(SetupProblem.OpenCodeWontRun)
        reason.startsWith(OpenCodeInstallFailure.NO_START) -> return both(SetupProblem.OpenCodeNoStart)
    }
    val lines = setupLogForPeople(logTail).trimEnd().split('\n')
    val recent = (lines.drop(if (lines.size > 15) lines.size - 15 else 0) + reason).joinToString("\n")
    if (noSpace.containsMatchIn(recent)) return both(SetupProblem.NoSpace(title))
    if (interrupted.containsMatchIn(recent)) return both(SetupProblem.Interrupted)
    if (offline.containsMatchIn(recent)) return SetupFailureText(SetupProblem.Offline(title), SetupProblem.NoInternet)
    if (checksum.containsMatchIn(reason)) return both(SetupProblem.Checksum(title))
    return both(SetupProblem.Install(title))
}

private fun jobState(state: String): SetupState =
    when (state) {
        "running" -> SetupState.Running
        "done" -> SetupState.Done
        "failed" -> SetupState.Failed
        "cancelled" -> SetupState.Cancelled
        else -> SetupState.Interrupted
    }

/**
 * [record] as the screens see it: states, plain-word problems, the weighted bar and (once honest) the ETA.
 * [components] are the job's, in its order. Details ([SetupProgress.logTail]) are redacted, keep a failure's original
 * reason even before the script printed anything, and never carry OCTRACE timing lines.
 */
public fun progressFromRecord(
    record: SetupJobRecord,
    components: List<SetupComponent>,
    now: Instant,
    redactor: Redactor = Redactor(),
    floors: MutableMap<String, Double>? = null,
): SetupProgress {
    val titles = components.associate { it.id to it.shortTitle }
    var jobError: SetupProblem? = null
    val list =
        record.components.map { component ->
            val state = component.componentState
            var error: SetupProblem? = null
            if (state == ComponentState.Failed) {
                val failure =
                    describeSetupFailure(
                        component, record.logTail, titles[component.id] ?: component.id,
                        step = component.id == SetupComponentIds.START,
                    )
                error = failure.component
                if (jobError == null) jobError = failure.job
            }
            ComponentProgress(
                id = component.id, state = state, stage = component.stage, bytesDone = component.done,
                bytesTotal = component.total, percent = component.percent, version = component.version, error = error,
            )
        }
    val state = jobState(record.state)
    val overall = overallFraction(components, list, floors)
    val elapsed = now - Instant.fromEpochMilliseconds(record.startedAt)
    val error =
        if (state == SetupState.Failed) {
            when {
                isRuntimeOwnershipFailure(record.error) -> SetupProblem.RuntimeOwnershipUnknown
                record.failureKind == SetupFailureKind.Persistence -> SetupProblem.Install("OpenCode")
                else -> jobError
            } ?: SetupProblem.Install(titles[record.current] ?: record.current ?: "OpenCode")
        } else {
            null
        }
    val extra =
        if (state == SetupState.Failed) {
            (record.components.filter { it.componentState == ComponentState.Failed }.map { it.error } + record.error)
                .filterNotNull()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !record.logTail.contains(it) }
                .distinct()
        } else {
            emptyList()
        }
    val details = (listOf(record.logTail.trimEnd()) + extra).filter { it.isNotEmpty() }.joinToString("\n")
    return SetupProgress(
        jobId = record.jobId,
        state = state,
        components = list,
        overall = if (state == SetupState.Done) 1.0 else overall,
        current = record.current,
        etaSeconds =
            if (state == SetupState.Running) {
                estimateEta(components, list, overall, elapsed.coerceAtLeast(0.milliseconds))
            } else {
                null
            },
        error = error,
        logTail = redactor.redact(setupLogForPeople(details)),
        firstSetup = SetupJobParams.isFirstSetup(record.params),
        adding = SetupJobParams.addingIds(record.params),
    )
}
