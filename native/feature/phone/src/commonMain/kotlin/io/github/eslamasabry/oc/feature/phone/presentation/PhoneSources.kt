package io.github.eslamasabry.oc.feature.phone.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.budget.ServiceExitReason
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.engine.runtime.server.StartProblem
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * The seams these pages read. They are the narrow faces of :engine:runtime's RuntimeHost facets (plan 11 §5.4), which
 * S3-RTENG has not written yet (its report §7 item 5). Each is shaped so the runtime engine can implement it 1:1 when
 * those facets land; until then the binding lives with S3-RTWIRE / :androidApp. Feature code never sees Android types.
 */

/**
 * What the app knows about the phone server right now. Every field is a reading; the page derives
 * the words.
 */
public data class PhoneServerFacts(
    /** Null until the first status read answered (the page says "Checking the server"). */
    val status: RuntimeStatus? = null,
    /**
     * The last authenticated loopback probe while [RuntimeStatus.serverRunning]; a pid alone is
     * never evidence.
     */
    val probe: ProbeAnswer? = null,
    /**
     * `oc.phoneServerStopped.<p>` or the native `lastStopRequested`: the person stopped it (plan 11
     * §6.7).
     */
    val stoppedByPerson: Boolean = false,
    /**
     * How the server last ended, when it is not running ([ServiceExitReason.MemoryOrPhantomKill] =
     * Android closed it).
     */
    val lastExit: ServiceExitReason? = null,
    /** The one start path is running (plan 11 §6.2), whoever asked for it. */
    val starting: Boolean = false,
    /** The last start that failed, until the next start answered. */
    val startProblem: StartProblem? = null,
    /** The native crash recovery for this phone's profile (plan 11 §6.3). */
    val recovery: RecoveryFacts = RecoveryFacts.None,
    /** OpenCode's version inside Ubuntu, or null when unknown. A leading `v` is allowed. */
    val installedVersion: String? = null,
    /** The version this app pins (the "Update OpenCode" row installs it). */
    val pinnedVersion: String? = null,
    /** The setup engine's "Update OpenCode" job runs, or failed last time. */
    val updateRunning: Boolean = false,
    val updateFailed: Boolean = false,
    /** A reply is being written on this phone's server (restart asks first; update refuses). */
    val replyRunning: Boolean = false,
    /** The app's child-process count (`ProcessHost.appProcessCount`), null when unreadable. */
    val processCount: Int? = null,
    /** The person turned Android's child-process limit off (the ceiling is then 48). */
    val limitLifted: Boolean = false,
)

/** Where the native crash recovery stands (plan 11 §6.3: attempt N of 3, 1 s doubling to 60 s). */
public sealed interface RecoveryFacts {
    public data object None : RecoveryFacts

    /** Waiting for attempt [attempt] of 3 at [nextAt]. */
    public data class Waiting(val attempt: Int, val nextAt: Instant?) : RecoveryFacts

    /** An automatic attempt is launching now. */
    public data object Restarting : RecoveryFacts

    /**
     * Three attempts failed: it will not restart by itself until a manual start or a healthy open.
     */
    public data object Exhausted : RecoveryFacts
}

/**
 * The phone server's commands (plan 11 §6.2 Manual start, §6.7 Stop). Each runs on the runtime's
 * one lane.
 */
public interface PhoneServerSource {
    public val facts: StateFlow<PhoneServerFacts>

    /** Reads the status again (page shown, back from Android's settings). */
    public suspend fun refresh()

    /** The one start path with `LaunchMode.Manual`; clears the deliberate-stop mark on success. */
    public suspend fun start(): Outcome<Unit>

    /** `server.requestStop(Person)`, then marks `oc.phoneServerStopped.<p>`. */
    public suspend fun stop(): Outcome<Unit>

    /**
     * Stop then the one start path; a person's restart is not a crash, so it spends no recovery
     * attempt.
     */
    public suspend fun restart(): Outcome<Unit>

    /**
     * The setup engine's "Update OpenCode" job (B-LINUX-079 manual row); the server restarts itself
     * after (R7).
     */
    public suspend fun updateOpenCode(): Outcome<Unit>
}

/** Real Android exit metadata, never the OS description or exception text. */
public enum class PhoneExitKind {
    ForceStop,
    LowMemory,
    MemoryLimit,
    Crash,
    Killed,
}

public data class PhoneExitNotice(
    val kind: PhoneExitKind,
    val at: Instant,
    val recoveryAllowed: Boolean,
    /**
     * True only after a new authenticated restart completed; a pre-existing process never sets
     * this.
     */
    val restored: Boolean = false,
)

/** One process-wide launch notice. Desktop has no Android exit record. */
public interface PhoneExitNotices {
    public val notice: StateFlow<PhoneExitNotice?>

    public fun dismiss()

    public companion object {
        public val Absent: PhoneExitNotices =
            object : PhoneExitNotices {
                override val notice: StateFlow<PhoneExitNotice?> = MutableStateFlow(null)

                override fun dismiss() = Unit
            }
    }
}

/** One Android settings screen the app may open (plan 11 §8.4 `KeepAliveSettings.open`). */
public enum class KeepAliveSetting {
    Battery,
    Autostart,
    AppDetails,
}

/** The phone facts Keep running reads (plan 11 §8.4 `KeepAliveSettings.info`). */
public data class KeepAliveInfo(
    val manufacturer: String,
    val brand: String,
    val sdkInt: Int,
    val batteryOptimizationIgnored: Boolean,
)

/**
 * Plan 11 §8.4's `KeepAliveSettings` (CR11-9, :core:domain when it lands). [open] answers false if
 * nothing opened.
 */
public interface KeepAliveSettings {
    public suspend fun info(): KeepAliveInfo?

    public suspend fun open(setting: KeepAliveSetting): Boolean
}

/** Why a process row may not be stopped from Running on this phone (B-LINUX-143: never offered). */
public enum class ProcessRole {
    Normal,
    ServerRoot,
    App,
}

/**
 * What a process is, so the row can be named in plain words (B-LINUX-180; the reading names it, not
 * the path).
 */
public enum class ProcessKind {
    OpenCode,
    ClaudeCode,
    AiTeam,
    DevService,
    Terminal,
    Helper,
    Other,
}

/**
 * One process from the reading. [name] is already the plain name the runtime chose (B-LINUX-146,
 * 180).
 */
public data class PhoneProcess(
    val pid: Int,
    val parentPid: Int?,
    val name: String,
    val kind: ProcessKind = ProcessKind.Other,
    val role: ProcessRole = ProcessRole.Normal,
    val cpuPercent: Double? = null,
    val leftBehind: Boolean = false,
    val command: io.github.eslamasabry.oc.core.common.Redacted? = null,
    val memoryBytes: Long? = null,
    val elapsedSeconds: Long? = null,
    val stopIdentities: Map<Int, Long> = emptyMap(),
)

/**
 * One reading of what runs (B-LINUX-147). [count] is the app's child-process count the limit is
 * about.
 */
public data class ProcessReading(
    val processes: List<PhoneProcess>,
    val count: Int?,
    val limitLifted: Boolean,
)

/**
 * Reads and stops the app's own processes (`ProcessHost`, plan 11 §5.4). Off Android nothing is
 * asked.
 */
public interface PhoneProcessSource {
    public suspend fun read(): Outcome<ProcessReading>

    /** A polite stop, then a forced one; protected processes are refused by the runtime as well. */
    public suspend fun stop(pid: Int): Outcome<Unit>

    /** The exact displayed tree, including kernel start ticks, frozen before the stop question. */
    public suspend fun stop(pid: Int, identities: Map<Int, Long>): Outcome<Unit> = stop(pid)
}

/** Android 14+ "Disable child process restrictions" (plan 11 §7.2 guided card). */
public enum class ChildLimitState {
    Enforced,
    Lifted,
    Unknown,
}

public data class ChildLimitStatus(
    val sdkInt: Int? = null,
    val state: ChildLimitState = ChildLimitState.Unknown,
    val developerOptionsEnabled: Boolean? = null,
    /** This build can open Settings for the person (`OsSettingsPages`). */
    val canOpenSettings: Boolean = false,
)

/**
 * Which Settings screen opened, if any (`openDeveloperOptions`: "developer", "about" or "none").
 */
public enum class DeveloperOptionsOpened {
    Developer,
    About,
    None,
}

/**
 * The card's inputs: the limit's state, Android's kills and the budget guard's queueing. Never
 * changes the setting.
 */
public interface ChildLimitSource {
    public suspend fun status(): ChildLimitStatus

    public suspend fun openDeveloperOptions(): DeveloperOptionsOpened

    /**
     * When Android last stopped one of the app's Linux programs by itself in 24 h
     * (`lastAndroidKill`), else null.
     */
    public suspend fun lastAndroidKill(): Instant?

    /** `ProcessBudgetGuard.queuesOften()`. */
    public fun queuesOften(): Boolean

    /** The device-wide Hide memory `oc.childLimitCardHidden` (not per profile). */
    public suspend fun hiddenAt(): Instant?

    public suspend fun hide(at: Instant)
}

/**
 * The pages these screens open. The entry installer maps them to `PhoneRoute`s once :ui:routes has
 * them.
 */
/** [Linux] is the Linux manager (:feature:linux), reached from This phone's "Linux" row. */
public enum class PhonePage {
    Running,
    KeepRunning,
    SetupStart,
    SetupProgress,
    Linux,
    ExportProjects,
    SharedFolders,
}

public interface PhoneNavigator {
    public fun open(page: PhonePage)

    public fun back()
}
