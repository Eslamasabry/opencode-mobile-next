package io.github.eslamasabry.oc.engine.linuxsetup

import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** What the tools inventory knows: the installed optional ids, or that the phone could not be asked. */
public sealed interface SetupInventory {
    public data class Known(val ids: Set<String>) : SetupInventory

    /** A busy, failed or incomplete probe: never an empty inventory, never a removal of prior tools. */
    public data object Unknown : SetupInventory
}

/** Why [SetupEngine.run] refused before anything ran. */
public data class SetupRunRefusal(val code: String)

/**
 * The phone setup engine's portable core (port of ChannelSetupEngine in lib/builtin/setup/setup_engine.dart): starting
 * a job (check first, hand the rest to the native runner), turning setup.json into [progress], timing finished steps,
 * and the shared tools inventory. Polling and the app-side voice step stay with the Android wiring.
 */
public class SetupEngine(
    private val host: SetupLinuxHost,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val toolsChanged: SetupToolsChanged,
    public val kind: SetupHostKind = SetupHostKind.Builtin,
    private val components: (Map<String, Map<String, String>>) -> List<SetupComponent> = { setupComponentRegistry(params = it) },
    private val trace: SetupTrace = SetupTrace.None,
    private val log: (() -> String) -> Unit = {},
    private val redactor: Redactor = Redactor(),
    private val wrapper: SetupScriptWrapper = SetupScriptWrapper { it },
    private val titles: SetupComponentTitles = SetupComponentTitles(),
    private val texts: SetupNotificationTexts = SetupNotificationTexts(),
) {
    private val state = MutableStateFlow(SetupProgress.Idle)
    public val progress: StateFlow<SetupProgress> = state.asStateFlow()

    public val registry: List<SetupComponent> get() = components(emptyMap())

    private var jobComponents: List<SetupComponent> = emptyList()
    private val floors = mutableMapOf<String, Double>()
    private var floorsJob: String? = null
    private val traced = mutableSetOf<String>()

    private fun publish(next: SetupProgress) {
        val previous = state.value
        state.value = next
        if (next.ended && (!previous.ended || previous.jobId != next.jobId)) toolsChanged.bump()
    }

    // ---- start ---------------------------------------------------------------------------------------------------

    /**
     * Starts (or continues) a job for [ids] plus what they depend on. A running job is watched, never restarted.
     * Unsafe Termux params refuse before any install script is generated. Unexpected failures publish a failed job
     * whose Details are redacted; they are never thrown at the caller.
     */
    public suspend fun run(ids: Set<String>, params: Map<String, Map<String, String>> = emptyMap()): SetupRunRefusal? {
        val read = host.setupStatus()
        if (read is HostReply.Refused) {
            failStart(read.code ?: read.message, "setup_status_invalid")
            return null
        }
        val text = (read as HostReply.Ok).value
        val last = SetupJobRecord.parse(text)
        if (text != null && last == null) {
            failStart(null, "setup_status_invalid")
            return null
        }
        if (kind == SetupHostKind.Builtin && last == null) {
            val status = host.status()
            if (status is HostReply.Refused) {
                failStart(status.code ?: status.message, "setup_status_invalid")
                return null
            }
        }
        if (last != null && last.state == "running") {
            show(last)
            return null
        }
        val effective = effectiveJobParams(params, last)
        if (kind == SetupHostKind.Termux && !TermuxSetupParams.isValid(effective, redactor)) {
            return SetupRunRefusal(TermuxSetupParams.INVALID_CODE)
        }
        val job =
            try {
                expandSelection(components(effective), ids)
            } catch (error: RuntimeException) {
                val details = redactor.redact("${error::class.simpleName}: ${error.message}")
                log { "setup: start failed $details" }
                failStart(null, details)
                return null
            }
        jobComponents = job
        val jobId = "setup-${clock.now().toEpochMilliseconds()}"
        resetFloors(jobId)
        emitLocal(jobId, job.associate { it.id to ComponentProgress(it.id, ComponentState.Checking) })
        val checks = check(job, requireComplete = false) ?: emptyMap()
        val after =
            job.associate { component ->
                val check = checks[component.id]
                component.id to
                    if (check?.ok == true) {
                        ComponentProgress(component.id, ComponentState.Skipped, version = check.version)
                    } else {
                        ComponentProgress(component.id, ComponentState.Pending)
                    }
            }
        emitLocal(jobId, after)
        val specs = planSetupJob(components(effective), ids, checks, effective, wrapper, titles).specs
        val handed = host.startSetup(jobId, specs, effective, texts)
        if (handed is HostReply.Refused) failStart(handed.code, redactor.redact(handed.message))
        return null
    }

    private fun failStart(code: String?, details: String) {
        val problem =
            if (isRuntimeOwnershipFailure(code)) SetupProblem.RuntimeOwnershipUnknown else SetupProblem.StartFailed(redactor.redact(details))
        val shown = state.value.components.associateBy { it.id }
        val rows = jobComponents.map { shown[it.id] ?: ComponentProgress(it.id, ComponentState.Pending) }
        publish(
            SetupProgress(
                jobId = state.value.jobId ?: "setup-${clock.now().toEpochMilliseconds()}",
                state = SetupState.Failed,
                components = rows,
                overall = overallFraction(jobComponents, rows, floors),
                error = problem,
            ),
        )
    }

    private fun emitLocal(jobId: String, rows: Map<String, ComponentProgress>) {
        val list = jobComponents.map { rows[it.id] ?: ComponentProgress(it.id, ComponentState.Pending) }
        publish(
            SetupProgress(
                jobId = jobId, state = SetupState.Running, components = list,
                overall = overallFraction(jobComponents, list, floors),
            ),
        )
    }

    /**
     * Checks [job]: the Linux base by asking Android whether it is installed, the rest in one run. Null when the phone
     * could not be asked completely and [requireComplete] (an incomplete probe is not evidence of absence).
     */
    private suspend fun check(job: List<SetupComponent>, requireComplete: Boolean): Map<String, SetupCheckResult>? {
        val started = clock.now()
        val result = checkUntraced(job, requireComplete)
        trace.recordDuration(
            "setup.check", (clock.now() - started).inWholeMilliseconds,
            mapOf("components" to "${job.size}"), result == null,
        )
        return result
    }

    private suspend fun checkUntraced(job: List<SetupComponent>, requireComplete: Boolean): Map<String, SetupCheckResult>? {
        val status = host.status()
        if (status is HostReply.Refused) return if (requireComplete) null else emptyMap()
        if (!(status as HostReply.Ok).value.installed) return emptyMap()
        val scripts =
            job.filter { !it.jobStep && !it.app && it.checkScript.isNotBlank() }.associate { it.id to it.checkScript }
        val results = linkedMapOf<String, SetupCheckResult>()
        if (scripts.isNotEmpty()) {
            when (val run = host.run(combinedCheckScript(scripts))) {
                is HostReply.Refused -> if (requireComplete) return null
                is HostReply.Ok -> {
                    val checks = parseCombinedChecks(run.value.output)
                    if (requireComplete && (!run.value.ok || scripts.keys.any { it !in checks })) return null
                    results.putAll(checks)
                }
            }
        }
        // Android's own marker is the truth for the base; its script only supplies the version.
        for (component in job.filter { it.native }) {
            results[component.id] = SetupCheckResult(true, results[component.id]?.version)
        }
        return results
    }

    // ---- restore ----------------------------------------------------------------------------------------------------

    /** Reads the persisted job (after an app restart) into [progress]. A job gone from disk is no longer shown. */
    public suspend fun restore() {
        val read = host.setupStatus() as? HostReply.Ok ?: return
        val record = SetupJobRecord.parse(read.value)
        if (record == null) {
            val held = state.value.state
            if (held != SetupState.Idle && held != SetupState.Running) publish(SetupProgress.Idle)
            return
        }
        show(record)
    }

    private fun show(record: SetupJobRecord) {
        if (jobComponents.isEmpty() || state.value.jobId != record.jobId || jobComponents.size != record.components.size) {
            val all = components(record.params).associateBy { it.id }
            jobComponents = record.components.map { all[it.id] ?: SetupComponent(id = it.id) }
        }
        resetFloors(record.jobId)
        traceFinished(record)
        publish(progressFromRecord(record, jobComponents, clock.now(), redactor, floors))
    }

    /** Each finished component, and the finished job, as spans timed by the native runner's own clock, once. */
    private fun traceFinished(record: SetupJobRecord) {
        for (component in record.components) {
            val started = component.startedAt ?: continue
            val ended = component.endedAt ?: continue
            if (component.state != "done" && component.state != "failed") continue
            if (!traced.add("${record.jobId}/${component.id}")) continue
            trace.recordDuration("setup.component", ended - started, mapOf("id" to component.id), component.state == "failed")
        }
        if (record.state != "running" && record.startedAt > 0 && record.updatedAt >= record.startedAt &&
            traced.add("${record.jobId}/")
        ) {
            trace.recordDuration(
                "setup.job", record.updatedAt - record.startedAt, mapOf("state" to record.state), record.state != "done",
            )
        }
    }

    private fun resetFloors(jobId: String) {
        if (floorsJob == jobId) return
        floorsJob = jobId
        floors.clear()
    }

    // ---- inventory --------------------------------------------------------------------------------------------------

    private class PendingRead(val revision: Int, val read: Deferred<SetupInventory>)

    private val inventoryLock = Mutex()
    private var pending: PendingRead? = null
    private var lastRead: Pair<Int, SetupInventory>? = null

    /**
     * Ids of optional components installed now, from the host's checks. Concurrent consumers share one live probe; a
     * completed read is not cached; a tools change discards an older in-flight read. A busy, failed or incomplete
     * probe is [SetupInventory.Unknown]; a missing Linux is an authoritative empty inventory.
     */
    public suspend fun installedOptional(): SetupInventory {
        var retry = false
        while (true) {
            val revision = toolsChanged.revision.value
            val read =
                inventoryLock.withLock {
                    val done = lastRead
                    val current = pending
                    when {
                        // A read that went stale takes a newer read of the current revision, never a third probe.
                        retry && done != null && done.first == revision -> return done.second
                        current != null && current.revision == revision -> current.read
                        else -> scope.async { readInstalledOptional() }.also { pending = PendingRead(revision, it) }
                    }
                }
            val result = read.await()
            inventoryLock.withLock {
                if (pending?.read === read) {
                    pending = null
                    lastRead = revision to result
                }
            }
            if (revision == toolsChanged.revision.value) return result
            retry = true
        }
    }

    private suspend fun readInstalledOptional(): SetupInventory {
        val optional = registry.filter { !it.required && !it.jobStep && !it.app }
        if (optional.isEmpty()) return SetupInventory.Known(emptySet())
        val checks = check(optional, requireComplete = true) ?: return SetupInventory.Unknown
        return SetupInventory.Known(optional.filter { checks[it.id]?.ok == true }.map { it.id }.toSet())
    }
}
