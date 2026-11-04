package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLive
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.shell.ShellStatusSource
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The background controller's one condition, inherited by every screen's existing status slot. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ShellStatusSource>())
class BackgroundShellStatus(
    private val live: BackgroundLive,
    private val hub: BackgroundHub,
    private val navigator: AppNavigator,
    private val activities: ResumedActivities,
) : ShellStatusSource {
    @Composable
    override fun status(): KitStatus? {
        val state by live.facts.collectAsStateWithLifecycle()
        val pause = state.pause ?: return null
        if (state.resuming)
            return KitStatus(
                KitStatusKind.AppStopped,
                stringResource(Res.string.backgroundPauseResuming),
                id = "background-paused:resuming",
                tone = StatusTone.Progress,
            )
        val at =
            pause.day?.let { stringResource(Res.string.appExitOnDay, it, pause.clock) }
                ?: stringResource(Res.string.appExitAtTime, pause.clock)
        val reason =
            when (pause.reason) {
                PauseReason.TimeLimit -> stringResource(Res.string.backgroundPauseTimeLimit, at)
                PauseReason.BatteryRestricted ->
                    stringResource(Res.string.backgroundPauseRestricted)
                PauseReason.UserStopped -> stringResource(Res.string.backgroundPauseUserStopped, at)
                PauseReason.DeviceRestarted ->
                    stringResource(Res.string.backgroundPauseDeviceRestarted)
                PauseReason.AppUpdated -> stringResource(Res.string.backgroundPauseAppUpdated, at)
                PauseReason.Interrupted -> stringResource(Res.string.backgroundPauseInterrupted)
            }
        val resume: () -> Unit = {
            hub.launchOnMain {
                if ((live.resume() as? Outcome.Ok)?.value == true)
                    activities.current
                        ?.window
                        ?.decorView
                        ?.announceForAccessibility(getString(Res.string.backgroundPauseResumed))
            }
        }
        val resumeShort =
            KitLineAction(
                stringResource(Res.string.backgroundPauseResumeShort),
                resume,
                testTag = "background-pause-resume",
                semanticsLabel = stringResource(Res.string.backgroundPauseResume),
            )
        val resumeAgain =
            KitLineAction(
                stringResource(Res.string.backgroundPauseResume),
                resume,
                testTag = "background-pause-resume",
            )
        val keep =
            KitLineAction(
                stringResource(Res.string.backgroundPauseOpenKeepRunning),
                { navigator.open(PhoneRoute.KeepRunning) },
            )
        return KitStatus(
            kind = KitStatusKind.AppStopped,
            message =
                if (pause.resumeFailed) stringResource(Res.string.backgroundPauseResumeFailed)
                else reason,
            id = if (pause.resumeFailed) "background-paused:failed" else "background-paused",
            tone = if (pause.resumeFailed) StatusTone.Failure else StatusTone.Neutral,
            action = if (pause.resumeFailed || !pause.canResume) keep else resumeShort,
            more =
                if (!pause.canResume) emptyList()
                else if (pause.resumeFailed) listOf(resumeAgain) else listOf(keep),
            onDismiss = { hub.launchOnMain { live.dismissPause() } },
            dismissLabel = stringResource(Res.string.notifyDismiss),
        )
    }
}
