package io.github.eslamasabry.oc.feature.background.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appExitAtTime
import io.github.eslamasabry.oc.ui.words.res.appExitOnDay
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseAppUpdated
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseDeviceRestarted
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseInterrupted
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseOpenKeepRunning
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseRestricted
import io.github.eslamasabry.oc.ui.words.res.backgroundPauseResume
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
        status = state.pause?.let { pauseStatus(it, state.resuming, actions) },
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = Space.S2.dp, bottom = Space.S6.dp)
        ) {
            if (state.supported && state.timeoutNotice) {
                Box(
                    Modifier.fillMaxWidth()
                        .padding(
                            horizontal = LocalKitTokens.current.gutter.dp,
                            vertical = Space.S3.dp,
                        )
                ) {
                    KitNotice(
                        title = stringResource(Res.string.e7SettingsUi23),
                        message = stringResource(Res.string.e7SettingsUi24),
                        icon = KitIcons.timer,
                        modifier = Modifier.testTag("background-timeout-notice"),
                    )
                }
            }
            if (state.supported)
                KitRowGroup {
                    KitSwitchRow(
                        title = stringResource(Res.string.e7SettingsUi25),
                        checked = state.switchOn,
                        supporting = stringResource(Res.string.e7SettingsUi26),
                        leading = KitRowLeading.Glyph(KitIcons.sync),
                        supportingMaxLines =
                            4, // F: lib/ui/screens/settings/notifications_settings_screen.dart:376
                        modifier = Modifier.testTag("background-live-switch"),
                        enabled =
                            if (state.switchEnabled) KitEnabled.Enabled
                            else KitEnabled.Disabled(stringResource(Res.string.commandRunning)),
                        onCheckedChange = actions::onSwitch,
                    )
                    state.failure?.let { failure ->
                        Box(
                            Modifier.fillMaxWidth()
                                .padding(horizontal = LocalKitTokens.current.gutter.dp)
                        ) {
                            KitNotice(
                                message = stringResource(failureWords(failure)),
                                tone = StatusTone.Failure,
                                modifier = Modifier.testTag("background-live-error"),
                            )
                        }
                    }
                    if (state.batteryRow) {
                        KitRow(
                            leading =
                                KitRowLeading.Glyph(
                                    if (state.batteryUnrestricted) KitIcons.batteryCharging
                                    else KitIcons.batteryWarning
                                ),
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
                            supportingMaxLines =
                                2, // F:
                                   // lib/ui/screens/settings/notifications_settings_screen.dart:405
                            trailing =
                                if (state.batteryUnrestricted) KitRowTrailing.None
                                else KitRowTrailing.Chevron,
                            onClick =
                                if (state.batteryUnrestricted) null else actions::onAllowBattery,
                        )
                    }
                    val restart = stringResource(Res.string.notifyRestartBackground)
                    KitRow(
                        title = stringResource(statusTitle(state.status)),
                        leading =
                            KitRowLeading.Glyph(
                                when (state.status) {
                                    LiveStatusLine.StoppedByAndroid -> KitIcons.timer
                                    LiveStatusLine.Running -> KitIcons.sync
                                    LiveStatusLine.Starting -> KitIcons.cloud
                                    LiveStatusLine.Off -> KitIcons.cloudOff
                                }
                            ),
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
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.keepRunningTitle),
                    leading = KitRowLeading.Glyph(KitIcons.batteryWarning),
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
private fun pauseStatus(
    pause: PauseFacts,
    resuming: Boolean,
    actions: BackgroundActions,
): KitStatus {
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
        KitLineAction(
            stringResource(Res.string.backgroundPauseResumeShort),
            actions::onResume,
            semanticsLabel = stringResource(Res.string.backgroundPauseResume),
        )
    val resumeAgain =
        KitLineAction(stringResource(Res.string.backgroundPauseResume), actions::onResume)
    val keepRunning =
        KitLineAction(
            stringResource(Res.string.backgroundPauseOpenKeepRunning),
            actions::onOpenKeepRunning,
        )
    return KitStatus(
        kind = KitStatusKind.AppStopped,
        id =
            if (resuming) "background-paused:resuming"
            else if (pause.resumeFailed) "background-paused:failed" else "background-paused",
        tone =
            if (resuming) StatusTone.Progress
            else if (pause.resumeFailed) StatusTone.Failure else StatusTone.Neutral,
        message =
            if (resuming) stringResource(Res.string.backgroundPauseResuming)
            else if (pause.resumeFailed) stringResource(Res.string.backgroundPauseResumeFailed)
            else message,
        icon =
            if (
                pause.reason == PauseReason.TimeLimit ||
                    pause.reason == PauseReason.BatteryRestricted
            )
                KitIcons.batteryWarning
            else KitIcons.pause,
        action =
            when {
                resuming -> null
                pause.resumeFailed || !pause.canResume -> keepRunning
                else -> resume
            },
        more =
            when {
                resuming || !pause.canResume -> emptyList()
                pause.resumeFailed -> listOf(resumeAgain)
                else -> listOf(keepRunning)
            },
        onDismiss = if (resuming) null else actions::onDismissPause,
        dismissLabel = stringResource(Res.string.notifyDismiss),
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
