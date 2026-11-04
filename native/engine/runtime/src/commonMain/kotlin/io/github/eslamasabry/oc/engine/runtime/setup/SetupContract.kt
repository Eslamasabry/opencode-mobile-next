package io.github.eslamasabry.oc.engine.runtime.setup

/**
 * Port of lib/builtin/setup/setup_contract.dart and lib/state/download_size.dart (the shared types
 * of the phone setup engine). Screens depend on these values only; nothing here knows any
 * particular component. Every sentence the Flutter engine wrote into its progress is a [SetupError]
 * value here, mapped to one string key by `:ui:words`.
 */

/**
 * Where a setup job installs OpenCode. Only the app's own Linux is ported (D7: Termux migrates
 * out).
 */
public enum class SetupHostKind(public val wireName: String) {
    Builtin("builtin"),
    Termux("termux"),
}

/** Evidence about a payload, never a transport/header guess (lib/state/download_size.dart). */
public enum class DownloadSizeKind {
    Unknown,
    Exact,
    LowerBound,
    Estimate,
}

public data class DownloadSize(val kind: DownloadSizeKind, val bytes: Long?) {
    public companion object {
        public val Unknown: DownloadSize = DownloadSize(DownloadSizeKind.Unknown, null)

        public fun exact(value: Long): DownloadSize =
            DownloadSize(DownloadSizeKind.Exact, value.also { require(it >= 0) })

        public fun lowerBound(value: Long): DownloadSize =
            DownloadSize(DownloadSizeKind.LowerBound, value.also { require(it >= 0) })

        public fun estimate(value: Long): DownloadSize =
            DownloadSize(DownloadSizeKind.Estimate, value.also { require(it >= 0) })

        /** Unknown/estimated parts leave only the sum of trusted parts as a lower bound. */
        public fun sum(sizes: Iterable<DownloadSize>): DownloadSize {
            var total = 0L
            var exact = true
            for (size in sizes) {
                val bytes = size.bytes
                if (
                    (size.kind == DownloadSizeKind.Exact ||
                        size.kind == DownloadSizeKind.LowerBound) && bytes != null && bytes >= 0
                ) {
                    total += bytes
                    exact = exact && size.kind == DownloadSizeKind.Exact
                } else {
                    exact = false
                }
            }
            return if (exact) exact(total) else lowerBound(total)
        }
    }
}

/** What an app-side component offers this phone (`SetupAppOffer`). */
public data class SetupAppOffer(
    val downloadBytes: Long,
    val installed: Boolean = false,
    val downloadSize: DownloadSize = DownloadSize.Unknown,
)

/**
 * One installable piece (`SetupComponent`). [title]/[shortTitle] are display data from the registry
 * port (the binding fills them from `:ui:words`); the engine itself only uses them as names.
 */
public data class SetupComponent(
    val id: String,
    val title: String,
    val shortTitle: String,
    val checkScript: String,
    val installScript: String,
    val dependsOn: List<String> = emptyList(),
    val required: Boolean = false,
    val defaultOn: Boolean = false,
    val estimatedSeconds: Int = 60,
    val downloadBytes: Long? = null,
    val installedBytes: Long? = null,
    val downloadSize: DownloadSize = DownloadSize.Unknown,
    val removeScript: String? = null,
    val presenceScript: String? = null,
    val sizeScript: String? = null,
    val why: String? = null,
    val summary: String? = null,
    val native: Boolean = false,
    val agentUser: Boolean = false,
    val jobStep: Boolean = false,
    /**
     * Runs in the app, never in the Linux shell: downloaded into app storage by
     * [SetupAppComponents], represented durably as a native waiting step.
     */
    val app: Boolean = false,
) {
    /** The same component with [bytes] as its download size. */
    public fun withDownloadBytes(bytes: Long?, evidence: DownloadSize? = null): SetupComponent =
        copy(downloadBytes = bytes, downloadSize = evidence ?: downloadSize)

    /** Adds known extracted payload evidence while retaining other metadata. */
    public fun withInstalledBytes(bytes: Long?): SetupComponent = copy(installedBytes = bytes)

    /** Keep the selected app component's payload evidence with its display size. */
    public fun withAppOffer(offer: SetupAppOffer): SetupComponent =
        withDownloadBytes(offer.downloadBytes, evidence = offer.downloadSize)
}

public enum class SetupState {
    Idle,
    Running,
    Done,
    Failed,
    Interrupted,
    Cancelled,
}

public enum class ComponentState {
    Pending,
    Checking,
    Running,
    Done,
    Failed,
    Skipped,
}

/**
 * Why a job or a row failed, as a value `:ui:words` turns into one sentence (Flutter keys named per
 * case). The technical reason stays in [SetupProgress.logTail] (Details).
 */
public sealed interface SetupError {
    /**
     * `runtime_ownership_unknown`: `inAppServerOwnershipRecoveryBody` + the Force stop recovery.
     */
    public data object OwnershipUnknown : SetupError

    /** `setup_status_invalid`: "The saved setup could not be read. Try Continue setup." */
    public data object StatusUnreadable : SetupError

    /** `setup_host_mismatch`: the saved setup belongs to a different host. */
    public data object HostMismatch : SetupError

    /** "Installed tools could not be checked. Try again." */
    public data object ToolsUncheckable : SetupError

    /** The native runner could not save the setup journal; never display its filesystem error. */
    public data object PersistenceFailure : SetupError

    /** `phoneSetupErrorPackageRepair`. */
    public data object PackageRepair : SetupError

    /** `phoneSetupErrorCannotStart`. */
    public data object CannotStart : SetupError

    /** `phoneSetupErrorOpenCodeNoProgram`. */
    public data object OpenCodeNoProgram : SetupError

    /** `phoneSetupErrorOpenCodeWontRun`. */
    public data object OpenCodeWontRun : SetupError

    /** `phoneSetupErrorOpenCodeNoStart`. */
    public data object OpenCodeNoStart : SetupError

    /** `phoneSetupErrorInterrupted`. */
    public data object Interrupted : SetupError

    /** `phoneSetupErrorNoInternet` (the job line for an offline row). */
    public data object NoInternet : SetupError

    /** `phoneSetupErrorNoSpace(title)`. */
    public data class NoSpace(val title: String) : SetupError

    /** `phoneSetupErrorOffline(title)` (the row line). */
    public data class Offline(val title: String) : SetupError

    /** `phoneSetupErrorChecksum(title)`. */
    public data class Checksum(val title: String) : SetupError

    /** `phoneSetupErrorInstall(title)`. */
    public data class Install(val title: String) : SetupError

    /**
     * The phone refused a call (a host failure that is none of the above); its details stay under
     * Details.
     */
    public data object HostRefused : SetupError

    /** A start that broke before the runner had the job, for an unexpected reason. */
    public data object Unexpected : SetupError
}

public data class ComponentProgress(
    val id: String,
    val state: ComponentState,
    val stage: String? = null,
    val bytesDone: Long? = null,
    val bytesTotal: Long? = null,
    val percent: Double? = null,
    val version: String? = null,
    val error: SetupError? = null,
) {
    /** This component's own fraction from its real signal, or null when it only reports stages. */
    val fraction: Double?
        get() {
            if (state == ComponentState.Done || state == ComponentState.Skipped) return 1.0
            val total = bytesTotal
            val done = bytesDone
            if (total != null && total > 0 && done != null)
                return (done.toDouble() / total).coerceIn(0.0, 1.0)
            percent?.let {
                return (it / 100).coerceIn(0.0, 1.0)
            }
            return null
        }
}

public data class SetupProgress(
    val state: SetupState,
    val components: List<ComponentProgress>,
    val overall: Double,
    val current: String? = null,
    val etaSeconds: Int? = null,
    val error: SetupError? = null,
    val logTail: String = "",
    val jobId: String? = null,
    val firstSetup: Boolean = false,
    val adding: List<String> = emptyList(),
) {
    val canContinue: Boolean
        get() =
            state == SetupState.Failed ||
                state == SetupState.Interrupted ||
                state == SetupState.Cancelled

    val ended: Boolean
        get() = canContinue || state == SetupState.Done

    /** Whether this is the moment a job ended (a first end of this job). */
    public fun endedBefore(previous: SetupProgress): Boolean =
        ended && (!previous.ended || previous.jobId != jobId)

    public companion object {
        public val Idle: SetupProgress = SetupProgress(SetupState.Idle, emptyList(), 0.0)
    }
}

/** Job facts kept in the job's params under [KEY] (`SetupJobParams`). */
public object SetupJobParams {
    public const val KEY: String = "_job"

    public val firstSetup: Map<String, Map<String, String>> = mapOf(KEY to mapOf("first" to "1"))

    public fun isFirstSetup(params: Map<String, Map<String, String>>): Boolean =
        params[KEY]?.get("first") == "1"

    public fun adding(ids: Iterable<String>): Map<String, Map<String, String>> =
        mapOf(KEY to mapOf("adding" to ids.joinToString(",")))

    public fun addingIds(params: Map<String, Map<String, String>>): List<String> =
        (params[KEY]?.get("adding") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** The fixed component ids (`SetupComponentIds`). */
public object SetupComponentIds {
    public const val LINUX: String = "linux"
    public const val ESSENTIALS: String = "essentials"
    public const val PYTHON: String = "python"
    public const val NODE: String = "node"
    public const val OPENCODE: String = "opencode"
    public const val AITEAM: String = "aiteam"
    public const val VOICE: String = "voice"
    public const val START: String = "start"
}

/**
 * The reasons the OpenCode install ends a failed run with, as its last log line
 * (`OpenCodeInstallFailure`).
 */
public object OpenCodeInstallFailure {
    public const val NO_PROGRAM: String = "[oc] OpenCode's download did not contain its program"
    public const val WONT_RUN: String = "[oc] OpenCode's program does not run on this phone"
    public const val WRONG_VERSION: String = "[oc] OpenCode's program reports another version"
    public const val NO_START: String = "[oc] OpenCode was installed but did not start"
}

/** The job's log as people see it: never the app's `OCTRACE` timing lines (`setupLogForPeople`). */
public fun setupLogForPeople(log: String): String =
    if (!log.contains("OCTRACE")) log
    else log.split('\n').filterNot { it.contains("OCTRACE") }.joinToString("\n")

/**
 * One app-owned component's own handle (install, cancel, remove), e.g. for component removal. A
 * restart-safe app-owned setup step; downloads retain partial files on cancellation. The setup
 * engine drives app steps through [SetupAppComponents] (progress: [SetupAppProgress]).
 */
public interface SetupAppComponent {
    public suspend fun installed(): io.github.eslamasabry.oc.core.common.Outcome<Boolean>

    public suspend fun install(
        progress: (SetupAppProgress) -> Unit
    ): io.github.eslamasabry.oc.core.common.Outcome<Unit>

    public suspend fun cancel()

    public suspend fun remove(): io.github.eslamasabry.oc.core.common.Outcome<Unit>
}
