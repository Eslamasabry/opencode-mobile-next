package io.github.eslamasabry.oc.engine.runtime.setup.jobs

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.flatMap
import io.github.eslamasabry.oc.engine.runtime.setup.ToolsChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/** One registry entry as the engine needs it: its id, what it depends on, and whether it is the closing job step. */
public data class SetupPart(
    val id: String,
    val dependsOn: List<String> = emptyList(),
    val required: Boolean = false,
    /** Not installed: the step the engine runs at the end of every job (starting OpenCode, [SetupIds.START]). */
    val jobStep: Boolean = false,
)

/** What one component's check found: installed and healthy, and its version. */
public data class SetupCheck(val ok: Boolean, val version: String? = null)

/**
 * What one component asks of the native runner (the engine's half of `_spec`). The binding adds the install script,
 * the prelude and the labels from its registry and words; the engine decides only what is skipped and what is a step.
 */
public data class SetupSpec(
    val id: String,
    val skipped: Boolean,
    val version: String? = null,
    val step: Boolean = false,
    val openCodeChanged: Boolean = false,
)

/**
 * The native job runner (SetupRunner.kt) and the combined check run, as the engine sees them. Method names follow the
 * channel (`startSetup`, `setupStatus`, `completeSetupStep`). [setupStatus] is setup.json's text, or null when there
 * has never been a job.
 */
public interface SetupRunnerPort {
    /** The resume rule's checks: whatever passes is not redone. */
    public suspend fun check(job: List<SetupPart>): Outcome<Map<String, SetupCheck>>

    public suspend fun startSetup(jobId: String, components: List<SetupSpec>, params: JobParams): Outcome<Unit>

    public suspend fun setupStatus(): Outcome<String?>

    public suspend fun completeSetupStep(jobId: String, id: String, ok: Boolean, error: String?, version: String?): Outcome<Unit>
}

/** What the engine asks of the app once OpenCode is installed: start the server and connect to it. */
public data class SetupFinishRequest(
    val host: SetupHost = SetupHost.Builtin,
    /** OpenCode was installed or updated by this job, so a server already running is the old one. */
    val openCodeChanged: Boolean,
    val version: String? = null,
)

public enum class SetupHost { Builtin, Termux }

/** Starts and connects; Ok when OpenCode answers. */
public fun interface SetupFinishStep {
    public suspend fun finish(request: SetupFinishRequest): Outcome<Unit>
}

/**
 * [ids] plus every required part, expanded by `dependsOn` and put in dependency order, registry order among equals
 * (port of progress_helpers.dart `expandSelection`). A dependency cycle is a registry bug and throws.
 */
public fun expandSelection(registry: List<SetupPart>, ids: Set<String>): List<SetupPart> {
    val byId = registry.associateBy { it.id }
    val wanted = mutableSetOf<String>()
    fun want(id: String) {
        val part = byId[id] ?: return
        if (!wanted.add(id)) return
        part.dependsOn.forEach(::want)
    }
    registry.forEach { if (it.required || it.id in ids) want(it.id) }
    val ordered = mutableListOf<SetupPart>()
    val placed = mutableSetOf<String>()
    val visiting = mutableSetOf<String>()
    fun place(part: SetupPart) {
        if (part.id in placed) return
        check(visiting.add(part.id)) { "Setup components depend on each other" }
        part.dependsOn.forEach { dependency -> byId[dependency]?.takeIf { dependency in wanted }?.let(::place) }
        visiting.remove(part.id)
        placed.add(part.id)
        ordered.add(part)
    }
    registry.forEach { if (it.id in wanted) place(it) }
    return ordered
}

/**
 * The phone setup engine (port of setup_engine.dart `ChannelSetupEngine`, the run / restore / poll / finish path;
 * B-LINUX-014). [run] checks every component first and skips what is installed, so each run is also a resume; the rest
 * goes to the native runner, which writes setup.json. [poll] reads it once (the shell's lane calls it every poll
 * interval while a job runs) and runs the closing start step through [finisher] when the job waits on it.
 *
 * Nothing a start does is thrown at a caller: a start that breaks, a check that fails or an unreadable job becomes a
 * failed job with Continue, its technical shape kept for Details only. While a job is being started, a record of any
 * other job is old news and never replaces this job's rows. Every job end bumps [toolsChanged] exactly once.
 *
 * Not ported here: app-side components (voice model), the Termux host's uncertain hand-over, cancel, and the process
 * budget wait; each is its own row.
 */
public class SetupJobEngine(
    private val runner: SetupRunnerPort,
    private val registry: (JobParams) -> List<SetupPart>,
    private val clock: Clock,
    private val finisher: SetupFinishStep?,
    public val toolsChanged: ToolsChanged = ToolsChanged(),
) {
    private val state = MutableStateFlow(SetupProgress.Idle)

    public val progress: StateFlow<SetupProgress> = state.asStateFlow()

    private var starting = false
    private var startingJobId: String? = null
    private var jobParts: List<SetupPart> = emptyList()
    private var localFirstSetup = false
    private var localAdding: List<String> = emptyList()
    private var finishing: String? = null
    private var lastMicros = 0L

    /** Starts (or continues) a job with [ids]; a running job is watched, never restarted. */
    public suspend fun run(ids: Set<String>, params: JobParams = emptyMap()) {
        if (starting) return
        starting = true
        try {
            val last = when (val read = read()) {
                is Outcome.Failed -> return failStart(read.failure)
                is Outcome.Ok -> read.value
            }
            if (last != null && last.state == "running") {
                show(last)
                return
            }
            val effective = effectiveJobParams(params, last)
            val started = attempt(::broken) { start(ids, effective) }
            if (started is Outcome.Failed) failStart(started.failure)
        } finally {
            starting = false
            startingJobId = null
        }
    }

    /** Shows the job on disk; none held any more (Reset, Remove) clears an ended job shown from before. */
    public suspend fun restore() {
        val record = when (val read = read()) {
            is Outcome.Failed -> return
            is Outcome.Ok -> read.value
        }
        if (record == null) {
            val held = state.value.state
            if (!starting && held != SetupState.Idle && held != SetupState.Running) publish(SetupProgress.Idle)
            return
        }
        show(record)
    }

    /** One read of a running job; runs the start step once per job when the job waits on it. */
    public suspend fun poll() {
        val record = (read() as? Outcome.Ok)?.value ?: return
        if (startingJobId != null && record.jobId != startingJobId) return
        show(record)
        if (record.state != "running" || record.current != SetupIds.START || finishing == record.jobId) return
        val step = record.component(SetupIds.START) ?: return
        if (step.state != "running") return
        finishing = record.jobId
        finish(record, step)
    }

    private suspend fun start(ids: Set<String>, params: JobParams) {
        val job = expandSelection(registry(params), ids)
        val jobId = newJobId()
        startingJobId = jobId
        jobParts = job
        localFirstSetup = SetupJobParams.isFirstSetup(params)
        localAdding = SetupJobParams.addingIds(params)
        emitLocal(
            jobId,
            job.associate { it.id to ComponentProgress(it.id, if (it.jobStep) ComponentState.Pending else ComponentState.Checking) },
        )

        val checks = when (val checked = runner.check(job)) {
            is Outcome.Failed -> {
                emitLocal(jobId, pending(job), SetupState.Failed, checked.failure)
                return
            }
            is Outcome.Ok -> checked.value
        }
        val afterChecks = job.associate { part ->
            val check = checks[part.id]
            part.id to
                if (check?.ok == true) ComponentProgress(part.id, ComponentState.Skipped, check.version)
                else ComponentProgress(part.id, ComponentState.Pending)
        }
        emitLocal(jobId, afterChecks)
        val openCodeChanged = job.any { it.id == SetupIds.OPEN_CODE } && checks[SetupIds.OPEN_CODE]?.ok != true
        val specs = job.map { part ->
            val check = checks[part.id]
            when {
                check?.ok == true -> SetupSpec(part.id, skipped = true, version = check.version)
                part.jobStep -> SetupSpec(part.id, skipped = false, step = true, openCodeChanged = openCodeChanged)
                else -> SetupSpec(part.id, skipped = false)
            }
        }
        when (val handed = runner.startSetup(jobId, specs, params)) {
            is Outcome.Failed -> emitLocal(jobId, afterChecks, SetupState.Failed, handed.failure)
            is Outcome.Ok -> {
                val record = (read() as? Outcome.Ok)?.value
                if (record != null && record.jobId == jobId) show(record)
            }
        }
    }

    private suspend fun finish(record: SetupJobRecord, step: SetupJobComponent) {
        val version = record.component(SetupIds.OPEN_CODE)?.version
        val finish = finisher
        val result: Outcome<Unit> =
            if (finish == null) {
                Failure.Runtime(RuntimeProblem.ServerStartUnavailable).failed()
            } else {
                attempt(::broken) {
                    finish.finish(SetupFinishRequest(openCodeChanged = step.data["openCodeChanged"] == "true", version = version))
                }.flatMap { it }
            }
        val failure = (result as? Outcome.Failed)?.failure
        val acknowledged = runner.completeSetupStep(
            jobId = record.jobId,
            id = SetupIds.START,
            ok = failure == null,
            error = failure?.let { it.details?.text ?: it::class.simpleName },
            version = version,
        )
        if (acknowledged is Outcome.Failed) {
            // The acknowledgement may have been lost: the next poll observes completion or retries this same job.
            finishing = null
            return
        }
        val after = (read() as? Outcome.Ok)?.value ?: return
        show(after)
    }

    private fun failStart(failure: Failure) {
        val jobId = startingJobId ?: newJobId()
        val shown = state.value.components.associateBy { it.id }
        val rows = jobParts.associate { it.id to (shown[it.id] ?: ComponentProgress(it.id, ComponentState.Pending)) }
        emitLocal(jobId, rows, SetupState.Failed, failure)
    }

    /** An unreadable job is never a fresh install. */
    private suspend fun read(): Outcome<SetupJobRecord?> =
        runner.setupStatus().flatMap { text ->
            val record = SetupJobRecord.parse(text)
            if (!text.isNullOrBlank() && record == null) Failure.Runtime(RuntimeProblem.Generic).failed() else Outcome.Ok(record)
        }

    private fun show(record: SetupJobRecord) {
        val starting = startingJobId
        if (starting != null && record.jobId != starting) return
        if (jobParts.isEmpty() || state.value.jobId != record.jobId || jobParts.size != record.components.size) {
            val all = registry(record.params).associateBy { it.id }
            jobParts = record.components.map { all[it.id] ?: SetupPart(it.id) }
        }
        publish(progressFromRecord(record))
    }

    private fun emitLocal(
        jobId: String,
        rows: Map<String, ComponentProgress>,
        status: SetupState = SetupState.Running,
        failure: Failure? = null,
    ) {
        publish(
            SetupProgress(
                jobId = jobId,
                state = status,
                components = jobParts.map { rows[it.id] ?: ComponentProgress(it.id, ComponentState.Pending) },
                failure = failure,
                firstSetup = localFirstSetup,
                adding = localAdding,
            ),
        )
    }

    private fun publish(next: SetupProgress) {
        val ended = next.endedBefore(state.value)
        state.value = next
        if (ended) toolsChanged.bump()
    }

    private fun pending(job: List<SetupPart>) = job.associate { it.id to ComponentProgress(it.id, ComponentState.Pending) }

    /** `setup-<microseconds>`, strictly increasing even when the clock does not move. */
    private fun newJobId(): String {
        val micros = maxOf(clock.now().toEpochMilliseconds() * 1000, lastMicros + 1)
        lastMicros = micros
        return "setup-$micros"
    }

    private companion object {
        /** A start that broke: its class name only, for Details (a message can carry a path or a secret). */
        fun broken(error: Throwable): Failure = Failure.Unexpected(Redacted.shape(error))
    }
}
