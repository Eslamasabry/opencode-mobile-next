package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem

/**
 * Port of the pure parts of lib/builtin/setup/setup_engine/progress_helpers.dart and the check
 * helpers of lib/builtin/setup/setup_scripts.dart: the params a run gets, the selection, the
 * weighted bar and ETA, the combined check script and its parser, and a setup.json record turned
 * into [SetupProgress] with [SetupError] values.
 */
public object SetupPlanning {
    /** The stable recovery code native writes into setup.json for an unproven runtime owner. */
    public const val OWNERSHIP_UNKNOWN_CODE: String = "runtime_ownership_unknown"

    private val legacyOwnershipUnknown = Regex("""\bownershipUnknown\b""")

    /** Whether [text] (a persisted error, a details line) names the native ownership failure. */
    public fun isOwnershipText(text: String?): Boolean =
        text != null &&
            (text == OWNERSHIP_UNKNOWN_CODE || legacyOwnershipUnknown.containsMatchIn(text))

    /**
     * Whether a port [failure] is the native ownership failure, by its problem or a legacy cause in
     * its details.
     */
    public fun isOwnershipFailure(failure: Failure?): Boolean =
        failure != null &&
            ((failure is Failure.Runtime && failure.problem == RuntimeProblem.OwnershipUnknown) ||
                isOwnershipText(failure.details?.text))

    /**
     * The params a new run gets (`effectiveJobParams`). Continue keeps what the stopped job was for
     * unless the caller asks for something else; job facts are laid over the stopped job's params
     * rather than replacing them. OpenCode 1 selections and 1.x version pins resume on OpenCode 2,
     * the only supported runtime.
     */
    public fun effectiveJobParams(
        requested: Map<String, Map<String, String>>,
        last: SetupJobRecord?,
    ): Map<String, Map<String, String>> {
        val facts = requested[SetupJobParams.KEY]
        val components = requested.filterKeys { it != SetupJobParams.KEY }
        val base =
            if (components.isEmpty() && last != null && last.canContinue) last.params
            else components
        val openCode = (base[SetupComponentIds.OPENCODE] ?: emptyMap()).toMutableMap()
        if (openCode["runtime"] == "opencode1" || openCode["version"]?.startsWith("1.") == true) {
            openCode.remove("version")
        }
        openCode["runtime"] = RUNTIME_OPENCODE2
        val result = LinkedHashMap(base)
        result[SetupComponentIds.OPENCODE] = openCode
        if (facts != null)
            result[SetupJobParams.KEY] = (base[SetupJobParams.KEY] ?: emptyMap()) + facts
        return result
    }

    /**
     * Installation metadata only: unknown keys, credentials and shell text never reach a script
     * builder.
     */
    public fun validParams(
        params: Map<String, Map<String, String>>,
        redact: (String) -> String,
        allowLegacy: Boolean = false,
    ): Boolean = params.all { (group, values) ->
        val allowed =
            when (group) {
                SetupComponentIds.OPENCODE -> setOf("runtime", "version")
                SetupJobParams.KEY -> setOf("first", "adding")
                else -> return@all false
            }
        values.all { (key, value) ->
            key in allowed &&
                redact(value) == value &&
                when (key) {
                    "runtime" -> value == RUNTIME_OPENCODE2 || (allowLegacy && value == "opencode1")
                    "version" ->
                        Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$").matches(value)
                    "first" -> value == "0" || value == "1"
                    "adding" ->
                        value.isEmpty() ||
                            Regex("^[a-z][a-z0-9_-]*(?:,[a-z][a-z0-9_-]*)*$").matches(value)
                    else -> false
                }
        }
    }

    public const val RUNTIME_OPENCODE2: String = "opencode2"

    /**
     * [ids] plus every required component, expanded by `dependsOn`, in dependency order (registry
     * order among equals).
     */
    public fun expandSelection(
        registry: List<SetupComponent>,
        ids: Set<String>,
    ): List<SetupComponent> {
        val byId = registry.associateBy { it.id }
        val wanted = mutableSetOf<String>()
        fun want(id: String) {
            val component = byId[id] ?: return
            if (!wanted.add(id)) return
            component.dependsOn.forEach(::want)
        }
        for (component in registry) if (component.required || component.id in ids)
            want(component.id)
        val ordered = mutableListOf<SetupComponent>()
        val placed = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        fun place(component: SetupComponent) {
            if (component.id in placed) return
            check(visiting.add(component.id)) { "setup components depend on each other" }
            for (dependency in component.dependsOn) {
                val other = byId[dependency]
                if (other != null && dependency in wanted) place(other)
            }
            visiting.remove(component.id)
            placed.add(component.id)
            ordered.add(component)
        }
        for (component in registry) if (component.id in wanted) place(component)
        return ordered
    }

    /**
     * 0..1 over [components], weighted by estimate; never below what a component already reached
     * ([floors]).
     */
    public fun overallFraction(
        components: List<SetupComponent>,
        progress: List<ComponentProgress>,
        floors: MutableMap<String, Double>? = null,
    ): Double {
        var total = 0.0
        var done = 0.0
        for (i in 0 until minOf(components.size, progress.size)) {
            val weight = components[i].estimatedSeconds.toDouble()
            total += weight
            done += weight * fraction(progress[i], floors)
        }
        if (total <= 0) return 0.0
        return (done / total).coerceIn(0.0, 1.0)
    }

    private fun fraction(progress: ComponentProgress, floors: MutableMap<String, Double>?): Double {
        val raw =
            when (progress.state) {
                ComponentState.Done,
                ComponentState.Skipped -> 1.0
                ComponentState.Running -> progress.fraction ?: 0.5
                else -> 0.0
            }
        if (floors == null) return raw
        if (
            progress.state == ComponentState.Running ||
                progress.state == ComponentState.Done ||
                progress.state == ComponentState.Skipped
        ) {
            val capped =
                if (progress.state == ComponentState.Running) raw.coerceIn(0.0, 0.98) else raw
            val floor = floors[progress.id] ?: 0.0
            val best = if (capped > floor) capped else floor
            floors[progress.id] = best
            return best
        }
        return floors[progress.id] ?: raw
    }

    /** Seconds left, once the job has made real progress for at least [minimumMillis]. */
    public fun estimateEta(
        components: List<SetupComponent>,
        progress: List<ComponentProgress>,
        overall: Double,
        elapsedMillis: Long,
        minimumMillis: Long = 10_000,
    ): Int? {
        if (elapsedMillis < minimumMillis) return null
        var total = 0.0
        var before = 0.0
        for (i in 0 until minOf(components.size, progress.size)) {
            val weight = components[i].estimatedSeconds.toDouble()
            total += weight
            if (progress[i].state == ComponentState.Skipped) before += weight
        }
        val doneNow = overall * total - before
        if (doneNow <= 0) return null
        val remaining = total * (1 - overall)
        if (remaining <= 0) return 0
        val pace = elapsedMillis / 1000.0 / doneNow
        return kotlin.math.ceil(remaining * pace).toInt()
    }

    /**
     * One script that runs every check (id to check script) in its own subshell, with begin/end
     * markers.
     */
    public fun combinedCheckScript(checks: Map<String, String>): String = buildString {
        for ((id, script) in checks) {
            append("printf '\\n::oc-check-begin %s\\n' '").append(id).append("'\n")
            append("(\n")
            append(script.trimEnd()).append('\n')
            append(") 2>/dev/null </dev/null\n")
            append("printf '\\n::oc-check-end %s %s\\n' '").append(id).append("' \"\$?\"\n")
        }
    }

    /**
     * Which checks passed and the version each printed last; a check with no end marker did not
     * pass.
     */
    public fun parseCombinedChecks(output: String): Map<String, SetupCheckResult> {
        val results = LinkedHashMap<String, SetupCheckResult>()
        var current: String? = null
        var lines = mutableListOf<String>()
        for (raw in output.split('\n')) {
            val line = raw.trimEnd()
            if (line.startsWith("::oc-check-begin ")) {
                current = line.removePrefix("::oc-check-begin ").trim()
                lines = mutableListOf()
                continue
            }
            if (line.startsWith("::oc-check-end ")) {
                val parts = line.removePrefix("::oc-check-end ").trim().split(' ')
                if (parts.size == 2 && parts[0] == current) {
                    val version = lines.lastOrNull { it.isNotBlank() }?.trim()
                    results[current] = SetupCheckResult(parts[1] == "0", version?.ifEmpty { null })
                }
                current = null
                continue
            }
            if (current != null) lines.add(line)
        }
        return results
    }

    private val offline =
        Regex(
            "Could not resolve|Temporary failure resolving|Unable to resolve host|" +
                "No address associated|Network is unreachable|Could not connect to|" +
                "Failed to connect|Connection timed out|timed out after|" +
                """curl: \((6|7|28|35|52|56)\)|curl exit (6|7|28|35|52|56)\)|""" +
                "UnknownHostException|SocketTimeoutException|ConnectException|" +
                "ECONNRESET|ETIMEDOUT|EAI_AGAIN|ENOTFOUND|Failed to fetch",
            RegexOption.IGNORE_CASE,
        )
    private val noSpace =
        Regex("No space left on device|ENOSPC|not enough free space", RegexOption.IGNORE_CASE)
    private val interrupted =
        Regex("""terminated with signal (?:9|15)\b|\bSIG(?:KILL|TERM)\b""", RegexOption.IGNORE_CASE)
    private val checksum = Regex("checksum|did not match its checksum", RegexOption.IGNORE_CASE)

    /** A failed component as values for its row and for the whole job (`describeSetupFailure`). */
    public fun describeFailure(
        failed: SetupJobComponent,
        logTail: String,
        title: String,
        step: Boolean = false,
    ): Pair<SetupError, SetupError> {
        val reason = failed.error ?: ""
        fun both(error: SetupError) = error to error
        if (isOwnershipText(reason)) return both(SetupError.OwnershipUnknown)
        if (
            reason.startsWith("[oc] Some packages are still unfinished.") ||
                reason.startsWith("[oc] Package repair could not finish.") ||
                reason.startsWith("[oc] A required package is not fully installed.")
        ) {
            return both(SetupError.PackageRepair)
        }
        if (step) return both(SetupError.CannotStart)
        if (reason.startsWith(OpenCodeInstallFailure.NO_PROGRAM))
            return both(SetupError.OpenCodeNoProgram)
        if (reason.startsWith(OpenCodeInstallFailure.WONT_RUN))
            return both(SetupError.OpenCodeWontRun)
        if (reason.startsWith(OpenCodeInstallFailure.NO_START))
            return both(SetupError.OpenCodeNoStart)
        val lines = setupLogForPeople(logTail).trimEnd().split('\n')
        val recent =
            (lines.drop(if (lines.size > 15) lines.size - 15 else 0) + reason).joinToString("\n")
        if (noSpace.containsMatchIn(recent)) return both(SetupError.NoSpace(title))
        if (interrupted.containsMatchIn(recent)) return both(SetupError.Interrupted)
        if (offline.containsMatchIn(recent))
            return SetupError.Offline(title) to SetupError.NoInternet
        if (checksum.containsMatchIn(reason)) return both(SetupError.Checksum(title))
        return both(SetupError.Install(title))
    }

    internal fun jobState(state: String): SetupState =
        when (state) {
            "running" -> SetupState.Running
            "done" -> SetupState.Done
            "failed" -> SetupState.Failed
            "cancelled" -> SetupState.Cancelled
            else -> SetupState.Interrupted
        }

    /**
     * [record] as the screens see it (`progressFromRecord`): states, error values, the weighted bar
     * and (once honest) the ETA. [redact] is the Redactor the Details text goes through.
     */
    public fun progressFromRecord(
        record: SetupJobRecord,
        components: List<SetupComponent>,
        nowMillis: Long,
        redact: (String) -> String,
        floors: MutableMap<String, Double>? = null,
    ): SetupProgress {
        val titles = components.associate { it.id to it.shortTitle }
        val list = mutableListOf<ComponentProgress>()
        var jobError: SetupError? = null
        for (component in record.components) {
            val state = component.componentState
            var error: SetupError? = null
            if (state == ComponentState.Failed) {
                val (row, job) =
                    describeFailure(
                        component,
                        record.logTail,
                        titles[component.id] ?: component.id,
                        step = component.id == SetupComponentIds.START,
                    )
                error = row
                if (jobError == null) jobError = job
            }
            list +=
                ComponentProgress(
                    id = component.id,
                    state = state,
                    stage = component.stage,
                    bytesDone = component.done,
                    bytesTotal = component.total,
                    percent = component.percent,
                    version = component.version,
                    error = error,
                )
        }
        val state = jobState(record.state)
        val overall = overallFraction(components, list, floors)
        val failedText =
            if (state == SetupState.Failed) {
                (record.components
                        .filter { it.componentState == ComponentState.Failed }
                        .map { it.error } + record.error)
                    .mapNotNull { raw ->
                        raw?.trim()?.takeIf { it.isNotEmpty() && !record.logTail.contains(it) }
                    }
                    .distinct()
            } else {
                emptyList()
            }
        val log =
            (listOf(record.logTail.trimEnd()) + failedText)
                .filter { it.isNotEmpty() }
                .joinToString("\n")
        return SetupProgress(
            jobId = record.jobId,
            state = state,
            components = list,
            overall = if (state == SetupState.Done) 1.0 else overall,
            current = record.current,
            etaSeconds =
                if (state == SetupState.Running) {
                    estimateEta(components, list, overall, nowMillis - record.startedAt)
                } else {
                    null
                },
            error =
                if (state == SetupState.Failed) {
                    when {
                        isOwnershipText(record.error) -> SetupError.OwnershipUnknown
                        record.persistenceFailure -> SetupError.PersistenceFailure
                        else -> jobError
                    } ?: SetupError.Install(titles[record.current] ?: record.current ?: "OpenCode")
                } else {
                    null
                },
            logTail = redact(setupLogForPeople(log)),
            firstSetup = SetupJobParams.isFirstSetup(record.params),
            adding = SetupJobParams.addingIds(record.params),
        )
    }
}

/** One check's answer: passed, and the version it printed on its last line. */
public data class SetupCheckResult(val ok: Boolean, val version: String?)
