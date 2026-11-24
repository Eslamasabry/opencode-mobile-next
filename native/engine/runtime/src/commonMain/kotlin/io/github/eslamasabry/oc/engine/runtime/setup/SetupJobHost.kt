package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.server.StartProblem

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
    /** phoneSetupStageCompat */
    val stageCompat: String,
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
    /** Told the kind of a start that failed ([failed]); the engine keeps that kind with the stored text. */
    val failedAs: (StartStepKind) -> Unit = {},
) {
    /** Names the [kind] of a start that ended in [failed], which the caller returns as it is. */
    public fun failed(kind: StartStepKind, failed: Outcome.Failed): Outcome.Failed {
        failedAs(kind)
        return failed
    }
}

/** Starts the server and connects; Ok only when OpenCode answers (`SetupFinisher`). */
public fun interface SetupFinisher {
    public suspend fun finish(request: SetupFinishRequest): Outcome<Unit>
}

/**
 * What stopped a start of the phone's own OpenCode, the setup job's `start` step. setup.json keeps [code] in front
 * of the redacted text ([mark], [read]), so a job read again after a restart still knows the kind.
 */
public enum class StartStepKind(public val code: String) {
    TimedOut("start_timed_out"),
    Ownership("start_ownership"),
    Exited("start_exited"),
    Refused("start_refused"),
    Password("start_password"),
    Withdrawn("start_withdrawn"),
    Profile("start_profile"),
    Recovery("start_recovery"),
    Store("start_store"),
    Prepare("start_prepare"),
    Confirm("start_confirm"),
    Save("start_save"),
    Connect("start_connect"),
    ;

    public companion object {
        /** The start kind for what the one start path says stopped it; every problem is Refused for now. */
        public fun of(problem: StartProblem): StartStepKind = StartStepKind.Refused

        /** The text setup.json keeps for a failed start: the kind's code in front of [text]. */
        public fun mark(kind: StartStepKind, text: String): String = "[${kind.code}] $text"

        /**
         * The kind a stored start error names and its text without the code; `(null, [stored])`, unchanged, when
         * the text does not start with a known kind's code.
         */
        public fun read(stored: String): Pair<StartStepKind?, String> {
            val kind =
                StartStepKind.entries.firstOrNull { stored.startsWith("[${it.code}] ") } ?: return null to stored
            return kind to stored.substringAfter("] ")
        }
    }
}
