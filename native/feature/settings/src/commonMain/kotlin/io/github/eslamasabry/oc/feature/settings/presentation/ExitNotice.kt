package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind

/**
 * The notice after Android ended the app while the phone's server ran in it (Flutter `AppExitNotice`, B-ANDR-008).
 * [recoveryAllowed] is whether the app restarts the server by itself: when it does not, the notice never says it is
 * starting again. [teamStopped]: the AI Team stopped with it too.
 */
public data class ExitNotice(
    val kind: ExitKind,
    val teamStopped: Boolean = false,
    val recoveryAllowed: Boolean = true,
)

/** How the app ended, in words that blame no one wrongly (a force stop is not Android's doing). */
public enum class ExitCause { Closed, LowMemory, Crashed, Killed }

/** What happened to the phone's server, said once (Flutter `appExitMessage`). */
public enum class ExitServerLine { StartingAgain, StartingAgainTeam, StartYourself, StartYourselfTeam, RunningAgain, RunningAgainTeam }

/** The quiet line on the phone's server status after the app closed (Flutter `appExitQuietRestarted` / `…Restarting`). */
public enum class ExitRestartLine { Restarting, Restarted }

/** Pure rules of the exit notice (Flutter `app_exit_notice.dart`). */
public object ExitNoticeRules {
    /** Shown only after an exit worth telling about (not a normal exit or an update). */
    public fun shows(kind: ExitKind): Boolean = kind.notable

    public fun cause(kind: ExitKind): ExitCause =
        when (kind) {
            ExitKind.LowMemory, ExitKind.MemoryLimit -> ExitCause.LowMemory
            ExitKind.Crash -> ExitCause.Crashed
            ExitKind.Killed -> ExitCause.Killed
            // A force stop is the person's or the maker's battery manager: "was closed", never "Android stopped".
            ExitKind.ForceStop, ExitKind.Normal, ExitKind.Update -> ExitCause.Closed
        }

    /** Once the server is back the notice says so instead of "starting again"; with restart off it asks the person. */
    public fun line(notice: ExitNotice, serverBack: Boolean = false): ExitServerLine =
        when {
            serverBack -> if (notice.teamStopped) ExitServerLine.RunningAgainTeam else ExitServerLine.RunningAgain
            !notice.recoveryAllowed -> if (notice.teamStopped) ExitServerLine.StartYourselfTeam else ExitServerLine.StartYourself
            else -> if (notice.teamStopped) ExitServerLine.StartingAgainTeam else ExitServerLine.StartingAgain
        }

    /** The status line reflects the real state: nothing when the app does not restart the server itself. */
    public fun restartLine(notice: ExitNotice, serverBack: Boolean): ExitRestartLine? =
        when {
            !notice.recoveryAllowed -> null
            serverBack -> ExitRestartLine.Restarted
            else -> ExitRestartLine.Restarting
        }
}
