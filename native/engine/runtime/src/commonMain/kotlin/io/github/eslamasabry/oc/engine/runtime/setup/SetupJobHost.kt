package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * The ports of the setup engine (port of the BuiltinLinux channel calls lib/builtin/setup/setup_engine.dart makes).
 * The Android binding maps [SetupJobHost] onto :android:linux `SetupRunner` (start/status/cancel/completeStep, which
 * write setup.json) and `BuiltinLinux` (installed status, the root check run and the agent-user check run).
 */
public interface SetupJobHost {
    /** Whether Ubuntu is installed in the app (`BuiltinLinux.status().installed`). A failure is never "absent". */
    public suspend fun linuxInstalled(): Outcome<Boolean>

    /** setup.json's text, or null when there is no job (`SetupRunner.status()`). */
    public suspend fun setupStatus(): Outcome<String?>

    /** Runs a combined check script in Ubuntu, as root or in the fixed agent-user view (2 minute timeout). */
    public suspend fun runCheck(script: String, agentUser: Boolean): Outcome<SetupCheckRun>

    /** Hands a job to the native runner (`SetupRunner.start`), which persists before it runs. */
    public suspend fun startSetup(job: SetupJobStart): Outcome<Unit>

    /** Stops the running native job; finished components stay (`SetupRunner.cancel`). */
    public suspend fun cancelSetup(): Outcome<Unit>

    /** Tells the native job how an app-run step went (`SetupRunner.completeStep`). [error] is a code or redacted text. */
    public suspend fun completeStep(jobId: String, id: String, ok: Boolean, error: String?, version: String?): Outcome<Unit>
}

/** One check run's exit code and output. */
public data class SetupCheckRun(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/** What `SetupRunner.start` receives. */
public data class SetupJobStart(
    val jobId: String,
    val components: List<SetupStepSpec>,
    val params: Map<String, Map<String, String>>,
    val texts: SetupNotificationTexts,
)

/** One component of a native job (`SetupRunner.Spec`). */
public data class SetupStepSpec(
    val id: String,
    val weight: Int,
    val data: Map<String, String>,
    val skipped: Boolean = false,
    val version: String? = null,
    val script: String? = null,
    val native: Boolean = false,
    val step: Boolean = false,
    val stage: String? = null,
    val labels: Map<String, String> = emptyMap(),
    val agentUser: Boolean = false,
)

/** The ongoing notification's words (`SetupRunner.Texts`); [progress] holds `{percent}`. */
public data class SetupNotificationTexts(
    val channel: String,
    val title: String,
    val progress: String,
    val done: String,
    val stopped: String,
)

/**
 * The words the engine hands to the native runner, from `:ui:words` (Flutter l10n keys in brackets). The engine
 * never authors a sentence.
 */
public data class SetupWords(
    /** phoneSetupNotificationChannel */
    val notificationChannel: String,
    /** phoneSetupNotificationTitle */
    val notificationTitle: String,
    /** phoneSetupNotificationProgress('{percent}') */
    val notificationProgress: String,
    /** phoneSetupNotificationDone */
    val notificationDone: String,
    /** phoneSetupNotificationStopped */
    val notificationStopped: String,
    /** phoneSetupStageStarting */
    val stageStarting: String,
    /** phoneSetupStageDownloadingLinux */
    val stageDownloadingLinux: String,
    /** phoneSetupStageUnpackingLinux */
    val stageUnpackingLinux: String,
    /** phoneSetupStageRetrying */
    val stageRetrying: String,
    /** aiteamComponentAddingTitle(names joined by phoneSetupStartListPair/Separator) */
    val addingTitle: (List<String>) -> String,
)

/** Every component the app knows for these params, in dependency order (`setupComponents`). */
public fun interface SetupRegistry {
    public fun components(params: Map<String, Map<String, String>>): List<SetupComponent>
}

/** What the engine asks of the app once OpenCode is installed (`SetupFinishRequest`). */
public data class SetupFinishRequest(
    val host: SetupHostKind,
    /** `opencode2` (wire name of the runtime). */
    val runtime: String,
    /** OpenCode was installed or updated by this job: a running server is the old one and must restart. */
    val openCodeChanged: Boolean,
    val version: String?,
)

/** Starts the server and connects; Ok only when OpenCode answers (`SetupFinisher`). */
public fun interface SetupFinisher {
    public suspend fun finish(request: SetupFinishRequest): Outcome<Unit>
}
