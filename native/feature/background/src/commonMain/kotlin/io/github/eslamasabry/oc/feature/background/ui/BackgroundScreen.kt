package io.github.eslamasabry.oc.feature.background.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundActions
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundUi
import io.github.eslamasabry.oc.feature.background.presentation.LiveStatusLine
import io.github.eslamasabry.oc.feature.background.presentation.PauseFacts
import io.github.eslamasabry.oc.feature.background.presentation.PauseReason
import io.github.eslamasabry.oc.feature.background.presentation.SwitchFailure
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appExitAtTime
import io.github.eslamasabry.oc.ui.words.res.appExitOnDay
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseAppUpdated
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseDeviceRestarted
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseInterrupted
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseOpenKeepRunning
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseRestricted
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseResumeFailed
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseResumeShort
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseResuming
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseTimeLimit
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseUserStopped
import io.github.eslamasabry.oc.ui.words.res.commandRunning
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi22
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi23
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi24
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi25
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi26
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi27
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi28
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi29
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi30
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi31
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi32
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi34
import io.github.eslamasabry.oc.ui.words.res.keepRunningRowSubtitle
import io.github.eslamasabry.oc.ui.words.res.keepRunningTitle
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.notifyBlockedMessage
import io.github.eslamasabry.oc.ui.words.res.notifyDismiss
import io.github.eslamasabry.oc.ui.words.res.notifyRestartBackground
import io.github.eslamasabry.oc.ui.words.res.quotaBudgetOff
import io.github.eslamasabry.oc.ui.words.res.settingsBackgroundOffFailed
import io.github.eslamasabry.oc.ui.words.res.settingsV3Background
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Notifications › Background (the background section of Flutter's
 * `NotificationsSettingsScreen` plus the app-wide pause line of `background_pause_notice.dart`):
 * Android's time-limit notice first (it needs the person), then the pause with Resume, the switch
 * with its cost spelled out, the battery row while it is on, the status row where the choice and
 * the service disagree, and Keep running settings.
 */
@Composable
public fun BackgroundScreen(
    state: BackgroundUi,
    actions: BackgroundActions,
) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.settingsV3Background),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.timeoutNotice) {
                KitNotice(
                    title = stringResource(Res.string.e7SettingsUi23),
                    message = stringResource(Res.string.e7SettingsUi24),
                    tone = StatusTone.Attention,
                    modifier = Modifier.testTag("background-timeout-notice"),
                )
            }
            state.pause?.let { pause -> PauseNotice(pause, state.resuming, actions) }
            KitRowGroup {
                KitSwitchRow(
                    title = stringResource(Res.string.e7SettingsUi25),
                    checked = state.switchOn,
                    supporting = stringResource(Res.string.e7SettingsUi26),
                    modifier = Modifier.testTag("background-live-switch"),
                    enabled =
                        if (state.switchEnabled) KitEnabled.Enabled
                        else KitEnabled.Disabled(stringResource(Res.string.commandRunning)),
                    onCheckedChange = actions::onSwitch,
                )
                if (state.batteryRow) {
                    KitRow(
                        title =
                            stringResource(
                                if (state.batteryUnrestricted) Res.string.e7SettingsUi27
                                else Res.string.e7SettingsUi28
                            ),
                        supporting =
                            stringResource(
                                if (state.batteryUnrestricted) Res.string.e7SettingsUi29
                                else Res.string.e7SettingsUi30
                            ),
                        supportingMaxLines = 3,
                        trailing =
                            if (state.batteryUnrestricted) KitRowTrailing.None
                            else KitRowTrailing.Chevron,
                        onClick = if (state.batteryUnrestricted) null else actions::onAllowBattery,
                    )
                }
                val restart = stringResource(Res.string.notifyRestartBackground)
                KitRow(
                    title = stringResource(statusTitle(state.status)),
                    supporting = stringResource(Res.string.e7SettingsUi34),
                    supportingMaxLines = 2,
                    modifier = Modifier.testTag("background-status-row"),
                    trailing =
                        if (state.restart)
                            KitRowTrailing.Action(KitLineAction(restart, actions::onRestart))
                        else KitRowTrailing.None,
                    onClick = if (state.restart) actions::onRestart else null,
                )
            }
            state.failure?.let { failure ->
                KitNotice(
                    message = stringResource(failureWords(failure)),
                    tone = StatusTone.Failure,
                    modifier = Modifier.testTag("background-live-error"),
                )
            }
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.keepRunningTitle),
                    supporting = stringResource(Res.string.keepRunningRowSubtitle),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::onOpenKeepRunning,
                )
            }
        }
    }
}

@Composable
private fun PauseNotice(pause: PauseFacts, resuming: Boolean, actions: BackgroundActions) {
    if (resuming) {
        KitNotice(
            message = stringResource(Res.string.backgroundPauseResuming),
            tone = StatusTone.Progress,
            modifier = Modifier.testTag("background-pause"),
        )
        return
    }
    val at = atWords(pause)
    val message =
        when (pause.reason) {
            PauseReason.TimeLimit -> stringResource(Res.string.backgroundPauseTimeLimit, at)
            PauseReason.BatteryRestricted -> stringResource(Res.string.backgroundPauseRestricted)
            PauseReason.UserStopped -> stringResource(Res.string.backgroundPauseUserStopped, at)
            PauseReason.DeviceRestarted -> stringResource(Res.string.backgroundPauseDeviceRestarted)
            PauseReason.AppUpdated -> stringResource(Res.string.backgroundPauseAppUpdated, at)
            PauseReason.Interrupted -> stringResource(Res.string.backgroundPauseInterrupted)
        }
    val resume =
        KitLineAction(stringResource(Res.string.backgroundPauseResumeShort), actions::onResume)
    KitNotice(
        message = message,
        tone = StatusTone.Attention,
        modifier = Modifier.testTag("background-pause"),
        // A resume Android did not confirm keeps the pause and points at Keep running (B-ANDR-016,
        // -018).
        notes =
            if (pause.resumeFailed) listOf(stringResource(Res.string.backgroundPauseResumeFailed))
            else emptyList(),
        actions =
            if (!pause.canResume) {
                listOf(
                    KitLineAction(
                        stringResource(Res.string.backgroundPauseOpenKeepRunning),
                        actions::onOpenKeepRunning,
                    )
                )
            } else if (pause.resumeFailed) {
                listOf(
                    KitLineAction(
                        stringResource(Res.string.backgroundPauseOpenKeepRunning),
                        actions::onOpenKeepRunning,
                    ),
                    resume,
                )
            } else {
                listOf(resume)
            },
        dismiss = KitLineAction(stringResource(Res.string.notifyDismiss), actions::onDismissPause),
    )
}

/** "at 3:10 PM" today, "on Sep 25 at 8:19 PM" otherwise (Flutter `appExitTimeText`). */
@Composable
private fun atWords(pause: PauseFacts): String =
    pause.day?.let { stringResource(Res.string.appExitOnDay, it, pause.clock) }
        ?: stringResource(Res.string.appExitAtTime, pause.clock)

private fun statusTitle(status: LiveStatusLine) =
    when (status) {
        LiveStatusLine.StoppedByAndroid -> Res.string.e7SettingsUi31
        LiveStatusLine.Running -> Res.string.e7SettingsUi32
        LiveStatusLine.Starting -> Res.string.commandRunning
        LiveStatusLine.Off -> Res.string.quotaBudgetOff
    }

private fun failureWords(failure: SwitchFailure) =
    when (failure) {
        SwitchFailure.NotEnabled,
        SwitchFailure.Unavailable -> Res.string.e7SettingsUi22
        SwitchFailure.NotDisabled -> Res.string.settingsBackgroundOffFailed
        SwitchFailure.NotificationsDenied -> Res.string.notifyBlockedMessage
    }
