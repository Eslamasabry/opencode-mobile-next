package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationChange
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SpeechModel
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceFacts
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi25
import io.github.eslamasabry.oc.ui.words.res.kitVoiceReadAloud
import io.github.eslamasabry.oc.ui.words.res.monitorQuiet
import io.github.eslamasabry.oc.ui.words.res.monitorQuietEnd
import io.github.eslamasabry.oc.ui.words.res.monitorQuietStart
import io.github.eslamasabry.oc.ui.words.res.notifyQuietAllDay
import io.github.eslamasabry.oc.ui.words.res.notifyQuietEndPicker
import io.github.eslamasabry.oc.ui.words.res.notifyQuietStartPicker
import io.github.eslamasabry.oc.ui.words.res.notifyBlockedMessage
import io.github.eslamasabry.oc.ui.words.res.notifyBlockedTitle
import io.github.eslamasabry.oc.ui.words.res.notifyFinishedRuns
import io.github.eslamasabry.oc.ui.words.res.notifyFinishedRunsDetail
import io.github.eslamasabry.oc.ui.words.res.notifyQuietDetail
import io.github.eslamasabry.oc.ui.words.res.notifyQuotaAlerts
import io.github.eslamasabry.oc.ui.words.res.notifyQuotaAlertsDetail
import io.github.eslamasabry.oc.ui.words.res.notifyRequests
import io.github.eslamasabry.oc.ui.words.res.notifyRequestsDetail
import io.github.eslamasabry.oc.ui.words.res.notifySectionBackground
import io.github.eslamasabry.oc.ui.words.res.notifySectionWhat
import io.github.eslamasabry.oc.ui.words.res.notifySendTest
import io.github.eslamasabry.oc.ui.words.res.notifySendTestDetail
import io.github.eslamasabry.oc.ui.words.res.notifyTurnOnInAndroid
import io.github.eslamasabry.oc.ui.words.res.notifyWifiOnly
import io.github.eslamasabry.oc.ui.words.res.readAloudConsentHeard
import io.github.eslamasabry.oc.ui.words.res.settingsV3VoiceTyping
import io.github.eslamasabry.oc.ui.words.res.voiceNoticesTitle
import io.github.eslamasabry.oc.ui.words.res.voiceSetupDownloadingPack
import io.github.eslamasabry.oc.ui.words.res.voiceSetupNotDownloaded
import io.github.eslamasabry.oc.ui.words.res.voiceSetupSubtitle
import org.jetbrains.compose.resources.stringResource

/** I11: voice typing (its speech model opens the setup sheet), read replies aloud where it exists, the licences. */
@Composable
internal fun ColumnScope.VoiceBody(voice: VoiceFacts, actions: SettingsActions) {
    KitRowGroup {
        val model = voice.speechModel
        if (model != SpeechModel.Unsupported) {
            KitRow(
                title = stringResource(Res.string.settingsV3VoiceTyping),
                supporting =
                    when (model) {
                        is SpeechModel.Ready -> model.name
                        is SpeechModel.Downloading -> stringResource(Res.string.voiceSetupDownloadingPack, model.name)
                        is SpeechModel.NotDownloaded -> stringResource(Res.string.voiceSetupNotDownloaded, model.size)
                        SpeechModel.Unsupported -> null
                    },
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.VoiceSetup) },
            )
        }
        if (voice.readAloudSupported) {
            KitSwitchRow(
                title = stringResource(Res.string.kitVoiceReadAloud),
                checked = voice.readAloudOn,
                supporting = stringResource(Res.string.readAloudConsentHeard),
                onCheckedChange = actions::onReadAloud,
            )
        }
        KitRow(
            title = stringResource(Res.string.voiceNoticesTitle),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.onOutside(SettingsOutside.VoiceNotices) },
        )
    }
    if (voice.speechModel is SpeechModel.NotDownloaded) {
        KitGroupNote(stringResource(Res.string.voiceSetupSubtitle))
    }
}

/**
 * I05: when Android blocks notifications the page leads with the fix; then what notifies me, quiet hours and Wi-Fi
 * only (defined once for every server), the test, and the background summary row that opens Keep running.
 */
@Composable
internal fun ColumnScope.NotificationsBody(n: NotificationFacts, actions: SettingsActions) {
    if (n.blocked) {
        KitNotice(
            stringResource(Res.string.notifyBlockedMessage),
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            title = stringResource(Res.string.notifyBlockedTitle),
            tone = StatusTone.Neutral,
            actions =
                listOf(
                    KitLineAction(stringResource(Res.string.notifyTurnOnInAndroid), { actions.onOutside(SettingsOutside.AndroidNotificationSettings) }),
                ),
        )
    }
    KitRowGroup(label = stringResource(Res.string.notifySectionWhat)) {
        KitSwitchRow(
            title = stringResource(Res.string.notifyFinishedRuns),
            checked = n.finishedWork,
            supporting = stringResource(Res.string.notifyFinishedRunsDetail),
        ) { actions.onNotification(NotificationChange.FinishedWork(it)) }
        KitSwitchRow(
            title = stringResource(Res.string.notifyRequests),
            checked = n.questions,
            supporting = stringResource(Res.string.notifyRequestsDetail),
        ) { actions.onNotification(NotificationChange.Questions(it)) }
        if (n.usageAlertsShown) {
            KitRow(
                title = stringResource(Res.string.notifyQuotaAlerts),
                supporting = stringResource(Res.string.notifyQuotaAlertsDetail),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.UsageAlerts) },
            )
        }
    }
    // Flutter `_quietHours`: one switch that names itself; when on, its start and end (each opens its time page).
    KitRowGroup {
        val quiet = n.quietHours
        KitSwitchRow(
            title = stringResource(Res.string.monitorQuiet),
            checked = quiet != null,
            supporting = stringResource(Res.string.notifyQuietDetail),
        ) { actions.onQuietHours(it) }
        if (quiet != null) {
            KitRow(
                title = stringResource(Res.string.monitorQuietStart),
                trailing = KitRowTrailing.Value(clock(quiet.start)),
                onClick = { actions.onOpen(SettingsPage.QuietTime(start = true)) },
            )
            KitRow(
                title = stringResource(Res.string.monitorQuietEnd),
                supporting = if (quiet.allDay) stringResource(Res.string.notifyQuietAllDay) else null,
                supportingMaxLines = 2,
                trailing = KitRowTrailing.Value(clock(quiet.end)),
                onClick = { actions.onOpen(SettingsPage.QuietTime(start = false)) },
            )
        }
        KitSwitchRow(title = stringResource(Res.string.notifyWifiOnly), checked = n.wifiOnly) {
            actions.onNotification(NotificationChange.WifiOnly(it))
        }
        KitRow(
            title = stringResource(Res.string.notifySendTest),
            supporting = stringResource(Res.string.notifySendTestDetail),
            onClick = actions::onSendTestNotification,
        )
    }
    n.background?.let { bg ->
        KitRowGroup(label = stringResource(Res.string.notifySectionBackground)) {
            KitRow(
                title = stringResource(Res.string.e7SettingsUi25),
                trailing = KitRowTrailing.Value(backgroundWords(bg)),
                supportingMaxLines = 2,
                onClick = { actions.onOutside(SettingsOutside.KeepRunning) },
            )
        }
    }
}

/**
 * Notifications › Quiet hours start or end (Flutter's time picker, as a kit check-mark list): every half hour, plus
 * the saved time when it is between them. One tap saves and returns.
 */
@Composable
internal fun ColumnScope.QuietTimeBody(start: Boolean, n: NotificationFacts?, actions: SettingsActions) {
    val quiet = n?.quietHours ?: QuietHours.Default
    val current = if (start) quiet.start else quiet.end
    KitChoiceList(
        choices = QuietHours.choices(current).map { KitChoice(it, clock(it)) },
        selected = current,
        semanticsLabel = stringResource(if (start) Res.string.notifyQuietStartPicker else Res.string.notifyQuietEndPicker),
        modifier = Modifier.testTag("quiet-time-choices"),
        onSelect = { actions.onQuietTime(start, it) },
    )
}

/** 24-hour "22:00–08:00": numbers, not copy (the platform's time format arrives with the quiet-hours sheet). */
internal fun quietText(q: QuietHours): String = "${clock(q.start)}–${clock(q.end)}"

internal fun clock(minutes: Int): String =
    "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
