package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.feature.settings.presentation.ExitCause
import io.github.eslamasabry.oc.feature.settings.presentation.ExitNotice
import io.github.eslamasabry.oc.feature.settings.presentation.ExitNoticeRules
import io.github.eslamasabry.oc.feature.settings.presentation.ExitRestartLine
import io.github.eslamasabry.oc.feature.settings.presentation.ExitServerLine
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appExitCrashed
import io.github.eslamasabry.oc.ui.words.res.appExitForceStopped
import io.github.eslamasabry.oc.ui.words.res.appExitKilled
import io.github.eslamasabry.oc.ui.words.res.appExitLowMemory
import io.github.eslamasabry.oc.ui.words.res.appExitQuietRestarted
import io.github.eslamasabry.oc.ui.words.res.appExitQuietRestarting
import io.github.eslamasabry.oc.ui.words.res.appExitServerAndTeamStopped
import io.github.eslamasabry.oc.ui.words.res.appExitServerAndTeamStoppedManual
import io.github.eslamasabry.oc.ui.words.res.appExitServerBack
import io.github.eslamasabry.oc.ui.words.res.appExitServerBackTeam
import io.github.eslamasabry.oc.ui.words.res.appExitServerStopped
import io.github.eslamasabry.oc.ui.words.res.appExitServerStoppedManual
import io.github.eslamasabry.oc.ui.words.res.notifyDismiss
import org.jetbrains.compose.resources.stringResource

/**
 * The exit notice's one sentence (Flutter `appExitMessage`). [at] is the time already in words ("at 9:21 PM", "on
 * Monday at 9:21 PM", from `appExitAtTime` / `appExitOnDay`).
 */
@Composable
public fun exitNoticeMessage(notice: ExitNotice, at: String, serverBack: Boolean = false): String {
    val what =
        stringResource(
            when (ExitNoticeRules.cause(notice.kind)) {
                ExitCause.LowMemory -> Res.string.appExitLowMemory
                ExitCause.Crashed -> Res.string.appExitCrashed
                ExitCause.Killed -> Res.string.appExitKilled
                ExitCause.Closed -> Res.string.appExitForceStopped
            },
            at,
        )
    return stringResource(
        when (ExitNoticeRules.line(notice, serverBack)) {
            ExitServerLine.StartingAgain -> Res.string.appExitServerStopped
            ExitServerLine.StartingAgainTeam -> Res.string.appExitServerAndTeamStopped
            ExitServerLine.StartYourself -> Res.string.appExitServerStoppedManual
            ExitServerLine.StartYourselfTeam -> Res.string.appExitServerAndTeamStoppedManual
            ExitServerLine.RunningAgain -> Res.string.appExitServerBack
            ExitServerLine.RunningAgainTeam -> Res.string.appExitServerBackTeam
        },
        what,
    )
}

/** The phone server's quiet status line after the app closed; null when the app does not restart it itself. */
@Composable
public fun exitRestartWords(notice: ExitNotice, serverBack: Boolean): String? =
    when (ExitNoticeRules.restartLine(notice, serverBack)) {
        ExitRestartLine.Restarting -> stringResource(Res.string.appExitQuietRestarting)
        ExitRestartLine.Restarted -> stringResource(Res.string.appExitQuietRestarted)
        null -> null
    }

/**
 * The notice itself, for the shell to place once after such an exit (neutral: amber is needs-you only). It folds away
 * when dismissed; the host owns whether it is shown ([ExitNoticeRules.shows]).
 */
@Composable
public fun AppExitNoticeLine(
    notice: ExitNotice,
    at: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    serverBack: Boolean = false,
) {
    KitNotice(
        exitNoticeMessage(notice, at, serverBack),
        modifier.testTag("app-exit-notice"),
        tone = StatusTone.Neutral,
        dismiss = KitLineAction(stringResource(Res.string.notifyDismiss), onDismiss),
    )
}
