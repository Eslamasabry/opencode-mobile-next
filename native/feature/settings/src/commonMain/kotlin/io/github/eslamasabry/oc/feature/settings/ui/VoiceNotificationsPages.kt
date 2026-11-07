package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationChange
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.QuietHours
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SpeechModel
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceFacts
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitActionRow
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitIconSwitchRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi25
import io.github.eslamasabry.oc.ui.words.res.kitVoiceReadAloud
import io.github.eslamasabry.oc.ui.words.res.monitorQuiet
import io.github.eslamasabry.oc.ui.words.res.monitorQuietEnd
import io.github.eslamasabry.oc.ui.words.res.monitorQuietStart
import io.github.eslamasabry.oc.ui.words.res.notifyBlockedMessage
import io.github.eslamasabry.oc.ui.words.res.notifyBlockedTitle
import io.github.eslamasabry.oc.ui.words.res.notifyFinishedRuns
import io.github.eslamasabry.oc.ui.words.res.notifyFinishedRunsDetail
import io.github.eslamasabry.oc.ui.words.res.notifyQuietAllDay
import io.github.eslamasabry.oc.ui.words.res.notifyQuietDetail
import io.github.eslamasabry.oc.ui.words.res.notifyQuietEndPicker
import io.github.eslamasabry.oc.ui.words.res.notifyQuietStartPicker
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * I11: voice typing (its speech model opens the setup sheet), read replies aloud where it exists,
 * the licences.
 */
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
                        is SpeechModel.Downloading ->
                            stringResource(Res.string.voiceSetupDownloadingPack, model.name)
                        is SpeechModel.NotDownloaded ->
                            stringResource(Res.string.voiceSetupNotDownloaded, model.size)
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
 * I05: when Android blocks notifications the page leads with the fix; then what notifies me, quiet
 * hours and Wi-Fi only (defined once for every server), the test, and the background summary row
 * that opens Keep running.
 */
@Composable
internal fun ColumnScope.NotificationsBody(n: NotificationFacts, actions: SettingsActions) {
    var checkInOpen by remember { mutableStateOf(false) }
    val defaultCheckIn = 30 // F: lib/domain/profile_monitor.dart:31
    if (checkInOpen)
        KitSheet(
            visible = true,
            onDismissRequest = { checkInOpen = false },
            title = stringResource(Res.string.monitorCheckInAfter),
        ) {
            val minutes = n.checkInMinutes
            if (minutes != null)
                KitChoiceList(
                    choices =
                        (listOf(15, 30, 60, 120) + minutes) // F: lib/domain/profile_monitor.dart:30
                            .distinct()
                            .sorted()
                            .map { // F: lib/domain/profile_monitor.dart:30
                                KitChoice(
                                    it,
                                    pluralStringResource(Res.plurals.monitorMinutes, it, it),
                                )
                            }, // NV: supported minute choices retain the current saved value
                    selected = minutes,
                    semanticsLabel = stringResource(Res.string.monitorCheckInAfter),
                    onSelect = {
                        actions.onNotification(NotificationChange.CheckIn(it))
                        checkInOpen = false
                    },
                )
        }
    if (n.blocked) {
        KitRow(
            title = stringResource(Res.string.notifyBlockedTitle),
            leading = KitRowLeading.Glyph(KitIcons.notificationImportant),
            supporting = stringResource(Res.string.notifyBlockedMessage),
            supportingMaxLines = Int.MAX_VALUE,
        )
        // The gutter sits outside the button: KitButton caps its own box (control cap, 400 dp) and a padding passed
        // in counts against that cap, which left the button 12 dp short of the rows' end edge on a phone. Flutter's
        // button fills the column (lib/ui/screens/settings/notifications_settings_screen.dart:565-576).
        Box(Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitButton(
                stringResource(Res.string.notifyTurnOnInAndroid),
                { actions.onOutside(SettingsOutside.AndroidNotificationSettings) },
                Modifier.testTag("notifications-open-settings"),
                role = KitButtonRole.Primary,
                fullWidth = true,
            )
        }
        Spacer(Modifier.height(Space.S6.dp))
    }
    KitRowGroup(leadingTiles = true, label = stringResource(Res.string.notifySectionWhat)) {
        val enabled =
            if (n.blocked) KitEnabled.Disabled(stringResource(Res.string.notifyBlockedMessage))
            else KitEnabled.Enabled
        KitIconSwitchRow(
            title = stringResource(Res.string.notifyFinishedRuns),
            icon = KitIcons.checkCircle,
            checked = n.finishedWork,
            supporting = stringResource(Res.string.notifyFinishedRunsDetail),
            enabled = enabled,
            onCheckedChange = { actions.onNotification(NotificationChange.FinishedWork(it)) },
        )
        KitIconSwitchRow(
            title = stringResource(Res.string.notifyRequests),
            icon = KitIcons.question,
            checked = n.questions,
            supporting = stringResource(Res.string.notifyRequestsDetail),
            enabled = enabled,
            onCheckedChange = { actions.onNotification(NotificationChange.Questions(it)) },
        )
        KitIconSwitchRow(
            title = stringResource(Res.string.monitorCheckIn),
            icon = KitIcons.clock,
            checked = n.checkInMinutes != null,
            supporting = stringResource(Res.string.monitorCheckInDetail),
            onCheckedChange = {
                actions.onNotification(
                    NotificationChange.CheckIn(
                        if (it) defaultCheckIn else null
                    ) // F: lib/domain/profile_monitor.dart:31
                ) // F: lib/domain/profile_monitor.dart:31
            }, // NV: the existing native check-in default is 30 minutes
        )
        n.checkInMinutes?.let { minutes ->
            KitRow(
                title = stringResource(Res.string.monitorCheckInAfter),
                leading = KitRowLeading.Glyph(KitIcons.timer),
                trailing =
                    KitRowTrailing.Value(
                        pluralStringResource(Res.plurals.monitorMinutes, minutes, minutes)
                    ),
                onClick = { checkInOpen = true },
            )
        }
        if (n.usageAlertsShown)
            KitRow(
                title = stringResource(Res.string.notifyQuotaAlerts),
                leading = KitRowLeading.Glyph(KitIcons.usage),
                supporting = stringResource(Res.string.notifyQuotaAlertsDetail),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.UsageAlerts) },
            )
        KitActionRow(
            title = stringResource(Res.string.notifySendTest),
            supporting = stringResource(Res.string.notifySendTestDetail),
            icon = KitIcons.notificationImportant,
            onClick = actions::onSendTestNotification,
        )
        val quiet = n.quietHours
        KitIconSwitchRow(
            title = stringResource(Res.string.monitorQuiet),
            icon = KitIcons.darkMode,
            checked = quiet != null,
            supporting = stringResource(Res.string.notifyQuietDetail),
            onCheckedChange = actions::onQuietHours,
        )
        if (quiet != null) {
            KitRow(
                title = stringResource(Res.string.monitorQuietStart),
                leading = KitRowLeading.Glyph(KitIcons.clock),
                trailing = KitRowTrailing.Value(clock(quiet.start)),
                onClick = { actions.onOpen(SettingsPage.QuietTime(start = true)) },
            )
            KitRow(
                title = stringResource(Res.string.monitorQuietEnd),
                leading = KitRowLeading.Glyph(KitIcons.clock),
                trailing = KitRowTrailing.Value(clock(quiet.end)),
                supporting =
                    if (quiet.allDay) stringResource(Res.string.notifyQuietAllDay) else null,
                onClick = { actions.onOpen(SettingsPage.QuietTime(start = false)) },
            )
        }
    }
    KitRowGroup(leadingTiles = true, label = stringResource(Res.string.notifySectionServers)) {
        n.profiles.forEach { profile ->
            KitIconSwitchRow(
                title = profile.name,
                icon = KitIcons.server,
                checked = profile.enabled,
                supporting = stringResource(Res.string.monitorOptIn),
                enabled =
                    if (profile.supported) KitEnabled.Enabled
                    else
                        KitEnabled.Disabled(stringResource(Res.string.e7ProjectMonitorUnsupported)),
                onCheckedChange = {
                    actions.onNotification(NotificationChange.ProfileWatch(profile.id, it))
                },
            )
            if (profile.supported && profile.enabled)
                KitIconSwitchRow(
                    title = stringResource(Res.string.monitorNotifications),
                    icon = KitIcons.notificationImportant,
                    checked = profile.notifications,
                    onCheckedChange = {
                        actions.onNotification(
                            NotificationChange.ProfileNotifications(profile.id, it)
                        )
                    },
                )
        }
        if (n.profiles.isEmpty())
            KitRow(
                title = stringResource(Res.string.notifyNoServersTitle),
                leading = KitRowLeading.Glyph(KitIcons.server),
                supporting = stringResource(Res.string.notifyNoServersDetail),
            )
        KitIconSwitchRow(
            title = stringResource(Res.string.notifyWifiOnly),
            icon = KitIcons.network,
            checked = n.wifiOnly,
            supporting = stringResource(Res.string.monitorWifiDetail),
            onCheckedChange = { actions.onNotification(NotificationChange.WifiOnly(it)) },
        )
    }
    n.background?.let { bg ->
        KitRowGroup(
            leadingTiles = true,
            label = stringResource(Res.string.notifySectionBackground),
        ) {
            KitRow(
                title = stringResource(Res.string.e7SettingsUi25),
                leading = KitRowLeading.Glyph(KitIcons.sync),
                trailing = KitRowTrailing.Value(backgroundWords(bg)),
                onClick = { actions.onOutside(SettingsOutside.KeepRunning) },
            )
        }
    }
}

/**
 * Notifications › Quiet hours start or end (Flutter's time picker, as a kit check-mark list): every
 * half hour, plus the saved time when it is between them. One tap saves and returns.
 */
@Composable
internal fun ColumnScope.QuietTimeBody(
    start: Boolean,
    n: NotificationFacts?,
    actions: SettingsActions,
) {
    val quiet = n?.quietHours ?: QuietHours.Default
    val current = if (start) quiet.start else quiet.end
    KitChoiceList(
        choices = QuietHours.choices(current).map { KitChoice(it, clock(it)) },
        selected = current,
        semanticsLabel =
            stringResource(
                if (start) Res.string.notifyQuietStartPicker else Res.string.notifyQuietEndPicker
            ),
        modifier = Modifier.testTag("quiet-time-choices"),
        onSelect = { actions.onQuietTime(start, it) },
    )
}

/**
 * 24-hour "22:00–08:00": numbers, not copy (the platform's time format arrives with the quiet-hours
 * sheet).
 */
internal fun quietText(q: QuietHours): String = "${clock(q.start)}–${clock(q.end)}"

internal fun clock(minutes: Int): String =
    "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
