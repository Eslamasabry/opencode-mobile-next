package io.github.eslamasabry.oc.engine.linuxsetup

import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.time.Instant

/**
 * One component as the native job runner receives it: the same fields as android/linux `SetupRunner.Spec`
 * (port of `_spec` in setup_engine/start.dart). Three kinds: [native] (the Linux base, downloaded by the runner),
 * [step] (done by the app: starting OpenCode, an app-side install), and a [script] run inside Ubuntu.
 */
public data class SetupRunnerSpec(
    val id: String,
    val script: String?,
    val native: Boolean,
    val step: Boolean,
    val weight: Double,
    val skipped: Boolean,
    val version: String?,
    val stage: String?,
    val labels: Map<String, String>,
    val data: Map<String, String>,
    val agentUser: Boolean = false,
)

/** What the ongoing notification says (SetupRunner.Texts); [progress] holds `{percent}`. */
public data class SetupNotificationTexts(
    val channel: String = "Phone setup", // phoneSetupNotificationChannel
    val title: String = "Setting up OpenCode on this phone", // phoneSetupNotificationTitle
    val progress: String = "{percent}% done", // phoneSetupNotificationProgress
    val done: String = "OpenCode is ready on this phone", // phoneSetupNotificationDone
    val stopped: String = "Setup stopped. Open the app to continue.", // phoneSetupNotificationStopped
)

/** A planned job: its components in install order and what the runner gets for each. */
public data class SetupJobPlan(
    val components: List<SetupComponent>,
    val specs: List<SetupRunnerSpec>,
    val params: Map<String, Map<String, String>>,
    /** OpenCode is (re)installed by this job, so the start step restarts a running server. */
    val openCodeChanged: Boolean,
)

/** How long the native job waits for an app-side component (a slow download takes longer than a start). */
public const val APP_STEP_WAIT_MINUTES: Int = 120

/**
 * Plans a job (the pure part of `_start`): [ids] plus required components and dependencies in order, each turned into
 * a [SetupRunnerSpec]; whatever [checks] found installed is skipped (the resume rule). Every install body goes through
 * [wrapper] (the setup prelude). Throws [IllegalStateException] only for a registry with a dependency cycle.
 */
public fun planSetupJob(
    registry: List<SetupComponent>,
    ids: Set<String>,
    checks: Map<String, SetupCheckResult>,
    params: Map<String, Map<String, String>>,
    wrapper: SetupScriptWrapper,
    titles: SetupComponentTitles = SetupComponentTitles(),
): SetupJobPlan {
    val job = expandSelection(registry, ids)
    val openCodeChanged = job.any { it.id == SetupComponentIds.OPENCODE } && checks[SetupComponentIds.OPENCODE]?.ok != true
    val runtime = params[SetupComponentIds.OPENCODE]?.get("runtime") ?: OpenCodePins.RUNTIME
    val specs =
        job.map { component ->
            val check = checks[component.id]
            val data =
                mapOf(
                    "requiredFreeBytes" to
                        "${SetupPreflight.requiredFreeBytes(component.downloadBytes ?: 0, component.installedBytes)}",
                )
            val base =
                SetupRunnerSpec(
                    id = component.id, script = null, native = false, step = false,
                    weight = component.estimatedSeconds.toDouble(), skipped = false, version = null, stage = null,
                    labels = emptyMap(), data = data,
                )
            when {
                check?.ok == true -> base.copy(skipped = true, version = check.version)
                component.jobStep ->
                    base.copy(
                        step = true, stage = titles.stageStarting,
                        data = data + mapOf("runtime" to runtime, "openCodeChanged" to "$openCodeChanged"),
                    )
                component.app ->
                    base.copy(
                        step = true, stage = titles.stageAppDownloading,
                        data = data + ("waitMinutes" to "$APP_STEP_WAIT_MINUTES"),
                    )
                component.native ->
                    base.copy(
                        native = true,
                        labels = mapOf("download" to titles.stageDownloadingLinux, "unpack" to titles.stageUnpackingLinux),
                    )
                else ->
                    base.copy(
                        script = wrapper.wrap(component.installScript),
                        labels = mapOf("retry" to titles.stageRetrying),
                        agentUser = component.agentUser,
                    )
            }
        }
    return SetupJobPlan(job, specs, params, openCodeChanged)
}

/**
 * Turns successive setup.json reads into progress for one screen: the bar never moves backwards within a job (floors
 * reset when the job changes) and the ETA waits for 10 s of real progress.
 */
public class SetupProgressTracker(private val redactor: Redactor = Redactor()) {
    private val floors = mutableMapOf<String, Double>()
    private var job: String? = null

    /** [components] are the job's, in its order (unknown ids are shown by id). */
    public fun progress(record: SetupJobRecord, registry: List<SetupComponent>, now: Instant): SetupProgress {
        if (job != record.jobId) {
            job = record.jobId
            floors.clear()
        }
        val byId = registry.associateBy { it.id }
        val components = record.components.map { byId[it.id] ?: SetupComponent(id = it.id) }
        return progressFromRecord(record, components, now, redactor, floors)
    }
}

/** setup.json text → the job record; null for no job or text that is not one. */
public fun parseSetupJob(text: String?): SetupJobRecord? = SetupJobRecord.parse(text)
