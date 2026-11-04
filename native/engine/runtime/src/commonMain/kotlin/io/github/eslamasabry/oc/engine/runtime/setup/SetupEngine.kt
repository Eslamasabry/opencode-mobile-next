package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Port of lib/builtin/setup/setup_engine.dart and its parts start.dart, poll.dart (the phone setup engine, built-in
 * host). [run] checks every component first and skips what is installed, so each run is also a resume; the rest goes
 * to the native job runner over [SetupJobHost], which keeps going in the background and writes setup.json. The engine
 * polls that into [progress]. Starting OpenCode is the job's last step (`start`): the native job waits while
 * [finisher] starts the server and connects.
 *
 * Confined to one serial lane: every call and [scope] must run on the same serial dispatcher. A second [run] while one
 * is starting returns at once (Flutter's `_starting`), and [cancel] never waits for a start that is still checking.
 *
 * Not ported here: app-side components (the voice model), the Termux host's uncertain hand-over, and the process
 * budget wait (`:engine:runtime` budget owns it; the binding admits before calling [run]).
 */
public class SetupEngine(
    private val host: SetupJobHost,
    private val registryPort: SetupRegistry,
    private val words: SetupWords,
    private val scope: CoroutineScope,
    private val clock: Clock,
    /** Put in front of every install script (`withSetupPrelude`'s prelude). */
    private val scriptPrelude: String,
    /** The Redactor the Details text goes through before it is published. */
    private val redact: (String) -> String,
    public var finisher: SetupFinisher? = null,
    private val pollInterval: Duration = 500.milliseconds,
    private val idlePollInterval: Duration = 60.seconds,
    private val readTimeout: Duration = 10.seconds,
    private val foreground: () -> Boolean = { true },
) {
    public val hostKind: SetupHostKind = SetupHostKind.Builtin

    private val _progress = MutableStateFlow(SetupProgress.Idle)

    /** The live progress of the current or last job. Collecting it keeps an idle engine reading (idle interval). */
    public val progress: StateFlow<SetupProgress> = _progress.asStateFlow()

    private val _toolsChanged = MutableStateFlow(0)

    /** Bumped when a job ends: what is on the phone changed for good (`setupToolsChanged`). */
    public val toolsChanged: StateFlow<Int> = _toolsChanged.asStateFlow()

    private var record: SetupJobRecord? = null
    private var jobComponents: List<SetupComponent> = emptyList()
    private val floors = mutableMapOf<String, Double>()
    private var floorsJob: String? = null
    private var starting = false
    private var startingJobId: String? = null
    private var cancelRequested = false
    private var localFirstSetup = false
    private var localAdding: List<String> = emptyList()
    private var handedOver: String? = null
    private var finishing: String? = null
    private var poll: Job? = null
    private var pollIdle = false
    private var disposed = false
    private val watcher: Job

    init {
        watcher = scope.launch {
            _progress.subscriptionCount.map { it > 0 }.distinctUntilChanged().collect { watched ->
                if (watched) {
                    ensurePolling()
                } else if (pollIdle) {
                    // The last page stopped watching: an idle wait has nothing left to show.
                    poll?.cancel()
                    poll = null
                    pollIdle = false
                }
            }
        }
    }

    /** Every component the app knows, in dependency order. */
    public val registry: List<SetupComponent> get() = registryPort.components(emptyMap())

    private val jobRunning: Boolean get() = record?.state == "running"

    private fun publish(next: SetupProgress) {
        val ended = next.endedBefore(_progress.value)
        _progress.value = next
        if (ended) _toolsChanged.value++
    }

    /** Starts (or continues) a job for [ids] plus what they depend on. Failures are published, never thrown. */
    public suspend fun run(ids: Set<String>, params: Map<String, Map<String, String>> = emptyMap()) {
        if (starting) return
        starting = true
        cancelRequested = false
        try {
            val last = when (val read = read()) {
                is Outcome.Failed -> {
                    failStart(readError(read.failure))
                    return
                }
                is Outcome.Ok -> read.value
            }
            if (last == null) {
                // An unreadable host must never be mistaken for a fresh setup.
                val status = withTimeoutOrNull(readTimeout) { guarded { host.linuxInstalled() } }
                    ?: Failure.TimedOut().failed()
                if (status is Outcome.Failed) {
                    failStart(readError(status.failure))
                    return
                }
            }
            if (last != null && last.state == "running") {
                // Already running (the app came back to it): watch, do not restart.
                show(last)
                ensurePolling()
                return
            }
            start(ids, SetupPlanning.effectiveJobParams(params, last))
        } finally {
            starting = false
            startingJobId = null
        }
    }

    /** Stops the running job; finished components stay installed. */
    public suspend fun cancel() {
        cancelRequested = true
        // Still checking: nothing native runs yet, and start stops on the flag.
        if (starting && handedOver != _progress.value.jobId) return
        if (_progress.value.state != SetupState.Running) return
        guarded { host.cancelSetup() }
        refresh()
    }

    /** Reads the persisted job (after an app restart) into [progress]. */
    public suspend fun restore(): Outcome<Unit> {
        val saved = when (val read = read()) {
            is Outcome.Failed -> return read
            is Outcome.Ok -> read.value
        }
        if (saved == null) {
            // No job on disk: none yet, or Linux was removed with it. A finished or stopped job still held from
            // before is no longer true, so the screens must not say "ready" from it (N1-5).
            val held = _progress.value.state
            if (!starting && held != SetupState.Idle && held != SetupState.Running) {
                record = null
                _progress.value = SetupProgress.Idle
            }
            return Unit.ok()
        }
        show(saved)
        if (saved.state == "running") ensurePolling()
        return Unit.ok()
    }

    /** Continues the saved selection and params after a cold restart; a running job is observed, never duplicated. */
    public suspend fun resume() {
        val saved = (read() as? Outcome.Ok)?.value ?: return
        show(saved)
        if (saved.state == "running") {
            ensurePolling()
        } else if (saved.canContinue) {
            run(saved.components.map { it.id }.toSet(), saved.params)
        }
    }

    /** Ids of optional components installed now; an incomplete probe is a failure, never absence. */
    public suspend fun installedOptional(): Outcome<Set<String>> {
        val optional = registry.filter { !it.required && !it.jobStep }
        if (optional.isEmpty()) return emptySet<String>().ok()
        return when (val checks = check(optional, requireComplete = true)) {
            is Checks.Failed -> Failure.Runtime(RuntimeProblem.Generic).failed()
            is Checks.Ok -> optional.filter { checks.results[it.id]?.ok == true }.map { it.id }.toSet().ok()
        }
    }

    /** Stops polling for good. */
    public fun dispose() {
        disposed = true
        poll?.cancel()
        watcher.cancel()
    }

    // ---- start ---------------------------------------------------------------------------------------------------

    private suspend fun start(ids: Set<String>, params: Map<String, Map<String, String>>) {
        val all = registryPort.components(params)
        val job = SetupPlanning.expandSelection(all, ids)
        val jobId = "setup-${nowMicros()}"
        startingJobId = jobId
        jobComponents = job
        localFirstSetup = SetupJobParams.isFirstSetup(params)
        localAdding = SetupJobParams.addingIds(params)
        resetFloors(jobId)
        emitLocal(
            jobId,
            job.associate { it.id to ComponentProgress(it.id, if (it.jobStep) ComponentState.Pending else ComponentState.Checking) },
            job,
        )
        // The resume rule: whatever its check finds installed is not redone.
        val checks = when (val result = check(job, requireComplete = false)) {
            is Checks.Failed -> {
                emitLocal(jobId, emptyMap(), job, SetupState.Failed, result.error)
                return
            }
            is Checks.Ok -> result.results
        }
        if (cancelRequested) {
            emitLocal(jobId, afterChecks(job, checks), job, SetupState.Cancelled)
            return
        }
        emitLocal(jobId, afterChecks(job, checks), job)
        val runtime = runtimeWireName(params[SetupComponentIds.OPENCODE]?.get("runtime"))
        val openCodeChanged = job.any { it.id == SetupComponentIds.OPENCODE } &&
            checks[SetupComponentIds.OPENCODE]?.ok != true
        val specs = job.map { spec(it, checks[it.id], runtime, openCodeChanged) }
        val texts = SetupNotificationTexts(
            channel = words.notificationChannel,
            title = if (localAdding.isEmpty()) {
                words.notificationTitle
            } else {
                words.addingTitle(localAdding.mapNotNull { id -> all.firstOrNull { it.id == id }?.shortTitle })
            },
            progress = words.notificationProgress,
            done = words.notificationDone,
            stopped = words.notificationStopped,
        )
        val handed = guarded { host.startSetup(SetupJobStart(jobId, specs, params, texts)) }
        if (handed is Outcome.Failed) {
            val error = when {
                SetupPlanning.isOwnershipFailure(handed.failure) -> SetupError.OwnershipUnknown
                (handed.failure as? Failure.Runtime)?.problem == RuntimeProblem.SetupPersistence ->
                    SetupError.Install("OpenCode")
                else -> SetupError.HostRefused
            }
            emitLocal(jobId, afterChecks(job, checks), job, SetupState.Failed, error)
            return
        }
        handedOver = jobId
        // A cancel that came while the job was being handed over.
        if (cancelRequested) guarded { host.cancelSetup() }
        try {
            refresh()
        } finally {
            ensurePolling()
        }
    }

    /** Ends a start that broke before the runner had the job as a failed job. */
    private fun failStart(error: SetupError) {
        val jobId = startingJobId ?: "setup-${nowMicros()}"
        val shown = _progress.value.components.associateBy { it.id }
        emitLocal(jobId, jobComponents.mapNotNull { c -> shown[c.id]?.let { c.id to it } }.toMap(), jobComponents, SetupState.Failed, error)
    }

    private fun readError(failure: Failure): SetupError =
        if (SetupPlanning.isOwnershipFailure(failure)) SetupError.OwnershipUnknown else SetupError.StatusUnreadable

    private fun spec(
        component: SetupComponent,
        check: SetupCheckResult?,
        runtime: String,
        openCodeChanged: Boolean,
    ): SetupStepSpec {
        val data = mapOf(
            "requiredFreeBytes" to
                SetupPreflight.requiredFreeBytes(component.downloadBytes ?: 0, component.installedBytes).toString(),
        )
        val base = SetupStepSpec(id = component.id, weight = component.estimatedSeconds, data = data)
        return when {
            check?.ok == true -> base.copy(skipped = true, version = check.version)
            component.jobStep -> base.copy(
                step = true,
                stage = words.stageStarting,
                data = data + mapOf("runtime" to runtime, "openCodeChanged" to openCodeChanged.toString()),
            )
            component.native -> base.copy(
                native = true,
                labels = mapOf("download" to words.stageDownloadingLinux, "unpack" to words.stageUnpackingLinux),
            )
            // An interrupted step runs again by itself (SetupStepRetry.kt); its row says so.
            else -> base.copy(
                script = scriptPrelude + component.installScript,
                labels = mapOf("retry" to words.stageRetrying),
                agentUser = component.agentUser,
            )
        }
    }

    private sealed interface Checks {
        data class Ok(val results: Map<String, SetupCheckResult>) : Checks

        data class Failed(val error: SetupError) : Checks
    }

    /** The Linux base by asking Android whether it is installed, the rest in one proot run per user. */
    private suspend fun check(job: List<SetupComponent>, requireComplete: Boolean): Checks {
        val installed = when (val status = guarded { host.linuxInstalled() }) {
            is Outcome.Failed -> return Checks.Failed(hostError(status.failure))
            is Outcome.Ok -> status.value
        }
        // Nothing inside Ubuntu can pass before Ubuntu is there.
        if (!installed) return Checks.Ok(emptyMap())
        val results = LinkedHashMap<String, SetupCheckResult>()
        for (agentUser in listOf(false, true)) {
            val scripts = LinkedHashMap<String, String>()
            for (component in job) {
                if (!component.jobStep && component.agentUser == agentUser && component.checkScript.isNotBlank()) {
                    scripts[component.id] = component.checkScript
                }
            }
            if (scripts.isEmpty()) continue
            when (val run = guarded { host.runCheck(SetupPlanning.combinedCheckScript(scripts), agentUser) }) {
                is Outcome.Failed -> {
                    if (requireComplete || !agentUser || SetupPlanning.isOwnershipFailure(run.failure)) {
                        return Checks.Failed(hostError(run.failure))
                    }
                    // The first agent install has not created oc yet: its checks stay pending.
                }
                is Outcome.Ok -> {
                    val parsed = SetupPlanning.parseCombinedChecks(run.value.output)
                    if (requireComplete && (!run.value.ok || scripts.keys.any { it !in parsed })) {
                        // A timed-out/truncated probe is not evidence of missing tools.
                        return Checks.Failed(SetupError.ToolsUncheckable)
                    }
                    results.putAll(parsed)
                }
            }
        }
        // Android's own marker is the truth for the base; its script only supplies the version.
        for (component in job.filter { it.native }) {
            results[component.id] = SetupCheckResult(true, results[component.id]?.version)
        }
        return Checks.Ok(results)
    }

    private fun hostError(failure: Failure): SetupError =
        if (SetupPlanning.isOwnershipFailure(failure)) SetupError.OwnershipUnknown else SetupError.HostRefused

    private fun afterChecks(job: List<SetupComponent>, checks: Map<String, SetupCheckResult>): Map<String, ComponentProgress> =
        job.associate { component ->
            val check = checks[component.id]
            component.id to if (check?.ok == true) {
                ComponentProgress(component.id, ComponentState.Skipped, version = check.version)
            } else {
                ComponentProgress(component.id, ComponentState.Pending)
            }
        }

    private fun emitLocal(
        jobId: String,
        components: Map<String, ComponentProgress>,
        rows: List<SetupComponent>,
        state: SetupState = SetupState.Running,
        error: SetupError? = null,
    ) {
        val list = rows.map { components[it.id] ?: ComponentProgress(it.id, ComponentState.Pending) }
        publish(
            SetupProgress(
                jobId = jobId,
                state = state,
                components = list,
                overall = SetupPlanning.overallFraction(rows, list, floors),
                error = error,
                firstSetup = localFirstSetup,
                adding = localAdding,
            ),
        )
    }

    // ---- polling -------------------------------------------------------------------------------------------------

    /** Reads within [pollInterval]: a job started or a page began watching. Brings an idle wait forward. */
    private fun ensurePolling() {
        if (disposed) return
        val pending = poll
        if (pending != null && pending.isActive) {
            if (!pollIdle) return
            pending.cancel()
        }
        pollIdle = false
        poll = scope.launch {
            delay(pollInterval)
            while (true) {
                tick()
                if (disposed) break
                if (jobRunning) {
                    delay(pollInterval)
                } else if (_progress.subscriptionCount.value > 0) {
                    // Nothing runs: a page merely shows the last job.
                    pollIdle = true
                    delay(idlePollInterval)
                    pollIdle = false
                } else {
                    break
                }
            }
        }
    }

    private suspend fun tick() {
        // A watched engine with no job reads only while the app is in front. One bad read never ends polling.
        if (jobRunning || foreground()) refresh()
    }

    private suspend fun refresh() {
        val current = (read() as? Outcome.Ok)?.value ?: return
        if (startingJobId != null && current.jobId != startingJobId) return
        show(current)
        if (current.state == "running" && current.current == SetupComponentIds.START && finishing != current.jobId) {
            val step = current.component(SetupComponentIds.START)
            if (step != null && step.state == "running") {
                finishing = current.jobId
                scope.launch { finish(current, step) }
            }
        }
    }

    private suspend fun finish(job: SetupJobRecord, step: SetupJobComponent) {
        val version = job.component(SetupComponentIds.OPENCODE)?.version
        val start = finisher
        val error: String? = if (start == null) {
            START_UNAVAILABLE
        } else {
            val request = SetupFinishRequest(
                host = hostKind,
                runtime = runtimeWireName(step.data["runtime"]),
                openCodeChanged = step.data["openCodeChanged"] == "true",
                version = version,
            )
            when (val result = guarded { start.finish(request) }) {
                is Outcome.Ok -> null
                is Outcome.Failed -> when {
                    SetupPlanning.isOwnershipFailure(result.failure) -> SetupPlanning.OWNERSHIP_UNKNOWN_CODE
                    else -> redact(result.failure.details?.text ?: START_FAILED)
                }
            }
        }
        val completed = guarded {
            host.completeStep(job.jobId, SetupComponentIds.START, error == null, error, version?.let(redact))
        }
        if (completed is Outcome.Failed) {
            // The acknowledgement may have been lost: the next poll retries the idempotent finish on this same job.
            finishing = null
            return
        }
        refresh()
    }

    /** setup.json, read within [readTimeout]. An unreadable job is not evidence of a fresh install. */
    private suspend fun read(): Outcome<SetupJobRecord?> {
        val answer = withTimeoutOrNull(readTimeout) { guarded { host.setupStatus() } }
            ?: return Failure.TimedOut().failed()
        val text = when (answer) {
            is Outcome.Failed -> return answer
            is Outcome.Ok -> answer.value
        }
        val parsed = SetupJobRecord.parse(text)
        if (text != null && text.isNotBlank() && parsed == null) return Failure.Runtime(RuntimeProblem.Generic).failed()
        if (parsed != null) {
            // Hostless records predate host selection and belong only to built-in.
            if ((parsed.host ?: SetupHostKind.Builtin.wireName) != hostKind.wireName) {
                return Failure.Runtime(RuntimeProblem.InvalidProfile).failed()
            }
            record = parsed
        }
        return parsed.ok()
    }

    private fun show(saved: SetupJobRecord) {
        val starting = startingJobId
        if (starting != null && saved.jobId != starting) return
        if (jobComponents.isEmpty() || _progress.value.jobId != saved.jobId ||
            jobComponents.size != saved.components.size
        ) {
            val all = registryPort.components(saved.params)
            jobComponents = saved.components.map { row ->
                all.firstOrNull { it.id == row.id } ?: SetupComponent(row.id, row.id, row.id, "", "")
            }
        }
        resetFloors(saved.jobId)
        publish(
            SetupPlanning.progressFromRecord(saved, jobComponents, clock.now().toEpochMilliseconds(), redact, floors),
        )
    }

    private fun resetFloors(jobId: String) {
        if (floorsJob == jobId) return
        floorsJob = jobId
        floors.clear()
    }

    private fun nowMicros(): Long {
        val now = clock.now()
        return now.epochSeconds * 1_000_000 + now.nanosecondsOfSecond / 1_000
    }

    /** A port that throws instead of answering is a failure value here, never an escaped exception. */
    private suspend inline fun <T> guarded(block: () -> Outcome<T>): Outcome<T> =
        when (val result = attempt({ Failure.Unexpected(Redacted.shape(it)) }, block)) {
            is Outcome.Ok -> result.value
            is Outcome.Failed -> result
        }

    public companion object {
        /** Persisted as the start step's error when no finisher is set (shown as [SetupError.CannotStart]). */
        public const val START_UNAVAILABLE: String = "start_unavailable"

        /** Persisted when a finisher failed without details. */
        public const val START_FAILED: String = "start_failed"

        /** `TermuxRuntime.parse(...).wireName` for the values a job can hold. */
        internal fun runtimeWireName(value: String?): String =
            if (value?.trim() == SetupPlanning.RUNTIME_OPENCODE2) SetupPlanning.RUNTIME_OPENCODE2 else "opencode1"
    }
}
