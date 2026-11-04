package io.github.eslamasabry.oc.engine.runtime.procs

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Port of the leftover-process notice logic: `runawayProjectName` and `TermuxRunawayWatcher` of
 * lib/ui/widgets/termux_phone_tools.dart, and `WorkRunawayNotice` of
 * lib/ui/widgets/work_status_line.dart (a helper left running with nothing to do: its line names a
 * project by its folder and never shows a path, names OpenCode only when OpenCode itself is busy,
 * and offers "See what's running" only when the list can be opened).
 */

/**
 * The project a leftover process belongs to, by its working folder: the first folder under a
 * `projects` folder (`/root/projects/FinanceHub/src` is FinanceHub). Null for the projects folder
 * itself, a home folder, a hidden folder or anything else, which the line then leaves out rather
 * than show a path.
 */
public fun runawayProjectName(cwd: String): String? {
    val parts = cwd.split('/').filter { it.isNotEmpty() }
    val index = parts.lastIndexOf("projects")
    if (index < 0 || index + 1 >= parts.size) return null
    val name = parts[index + 1]
    return if (name.startsWith(".")) null else name
}

/**
 * A duration in the words the page uses (Flutter `formatTermuxDuration`): seconds, minutes, or
 * hours and minutes.
 */
public sealed interface DurationWords {
    /** l10n `termuxProcsDurationSeconds(seconds)`. */
    public data class Seconds(val seconds: Long) : DurationWords

    /** l10n `termuxProcsDurationMinutes(minutes)`: "10 min". */
    public data class Minutes(val minutes: Long) : DurationWords

    /** l10n `termuxProcsDurationHours(hours, minutes)`: "1 h 12 min". */
    public data class Hours(val hours: Long, val minutes: Long) : DurationWords

    public companion object {
        public fun of(seconds: Long): DurationWords {
            if (seconds < 60) return Seconds(seconds)
            val minutes = seconds / 60
            if (minutes < 60) return Minutes(minutes)
            return Hours(minutes / 60, minutes % 60)
        }
    }
}

/** Which sentence the leftover line says (each maps 1:1 to a Flutter l10n key). */
public sealed interface RunawayMessage {
    /** l10n `workRunaway(busyFor)`: "OpenCode has been busy for 10 min with nothing to do". */
    public data class OpenCode(val busyFor: DurationWords) : RunawayMessage

    /**
     * l10n `workRunawayInProject(project, busyFor)`: "OpenCode has been busy in FinanceHub for 10
     * min …".
     */
    public data class OpenCodeInProject(val project: String, val busyFor: DurationWords) :
        RunawayMessage

    /**
     * l10n `workRunawayHelper(helper, busyFor)`: "A leftover java process has been busy for 10 min
     * …".
     */
    public data class Helper(val helper: String, val busyFor: DurationWords) : RunawayMessage

    /**
     * l10n `workRunawayHelperInProject(helper, project, busyFor)`: "A leftover node process in
     * FinanceHub has …".
     */
    public data class HelperInProject(
        val helper: String,
        val project: String,
        val busyFor: DurationWords,
    ) : RunawayMessage

    /**
     * l10n `workRunawayStopFailed(helper)`: "Couldn't stop node. Try again, or stop it from
     * Termux.".
     */
    public data class StopFailed(val helper: String) : RunawayMessage
}

/** The line's extra actions under More. */
public enum class RunawayMoreAction {
    /** l10n `workRunawaySeeRunning`: "See what's running", opens Running on this phone. */
    SeeRunning
}

/** A leftover process, for the line: what the process list reports about it. */
public data class LeftoverProcess(
    val pid: Int,
    /** The process's own name ("java", "node", or a path), as the phone reports it. */
    val name: String,
    val cwd: String,
    val cpuSeconds: Long,
    /** Left running with nothing waiting on it (an orphan). */
    val orphan: Boolean,
    val startTicks: Long? = null,
)

/** The leftover-process line's facts (Flutter `WorkRunawayNotice`). */
public data class RunawayNotice(
    /** Changes when the process does; a dismissal holds only for one identity. */
    val identity: RunawayIdentity,
    /** The process's own name ("java", "node"), as the phone reports it. */
    val helper: String,
    /** The project folder's name when it runs inside one; null otherwise. */
    val project: String?,
    val busyFor: DurationWords,
    /** The last stop did not end the process. */
    val stopFailed: Boolean = false,
) {
    /**
     * The process's name as the line says it: its last path part, never a folder ("/usr/bin/node"
     * is "node").
     */
    val helperName: String
        get() {
            val parts = helper.split('/').filter { it.isNotEmpty() }
            return if (parts.isEmpty()) helper.trim() else parts.last()
        }

    /**
     * OpenCode itself is the one busy, not a helper it left behind: only then does the line name
     * OpenCode.
     */
    val isOpenCode: Boolean
        get() = helperName.lowercase().startsWith("opencode")

    /** Which sentence the line says. */
    val message: RunawayMessage
        get() =
            when {
                stopFailed -> RunawayMessage.StopFailed(helperName)
                isOpenCode && project == null -> RunawayMessage.OpenCode(busyFor)
                isOpenCode -> RunawayMessage.OpenCodeInProject(project.orEmpty(), busyFor)
                project == null -> RunawayMessage.Helper(helperName, busyFor)
                else -> RunawayMessage.HelperInProject(helperName, project, busyFor)
            }

    /**
     * The line's Stop is labelled with this name (l10n `termuxProcsStopSemantics(helper)`: "Stop
     * node").
     */
    val stopLabelName: String
        get() = helperName

    /**
     * More holds "See what's running" only when the caller [canSeeRunning] (it can open the list).
     */
    public fun more(canSeeRunning: Boolean): List<RunawayMoreAction> =
        if (canSeeRunning) listOf(RunawayMoreAction.SeeRunning) else emptyList()
}

/** A process as a dismissal remembers it: its pid and name. */
public data class RunawayIdentity(val pid: Int, val name: String, val startTicks: Long? = null)

/**
 * Chooses the worst leftover process for the line and remembers dismissals for the life of the app
 * (Flutter `TermuxRunawayWatcher`): the line comes back only for a different process. The caller
 * polls (every 60 s) and stops.
 */
public class RunawayWatch(private val threshold: Duration = 10.minutes) {
    private val dismissed = mutableSetOf<RunawayIdentity>()
    private var stopFailed: RunawayIdentity? = null

    /**
     * The line for [processes], or null when nothing is over [threshold] or the worst one was
     * dismissed.
     */
    public fun notice(processes: List<LeftoverProcess>): RunawayNotice? {
        val worst =
            processes
                .filter { it.orphan && it.cpuSeconds > threshold.inWholeSeconds }
                .maxByOrNull { it.cpuSeconds } ?: return null
        val identity = RunawayIdentity(worst.pid, worst.name, worst.startTicks)
        if (identity in dismissed) return null
        return RunawayNotice(
            identity = identity,
            helper = worst.name,
            project = runawayProjectName(worst.cwd),
            busyFor = DurationWords.of(worst.cpuSeconds),
            stopFailed = stopFailed == identity,
        )
    }

    public fun dismiss(identity: RunawayIdentity) {
        dismissed.add(identity)
    }

    /**
     * After a stop: an ended process is dismissed; one that is still running keeps the line in its
     * failure words.
     */
    public fun stopped(identity: RunawayIdentity, ended: Boolean) {
        if (ended) {
            dismissed.add(identity)
            stopFailed = null
        } else {
            stopFailed = identity
        }
    }
}
