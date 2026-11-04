package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.AboutFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateState
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.aboutAllLicences
import io.github.eslamasabry.oc.ui.words.res.aboutAllLicencesDetail
import io.github.eslamasabry.oc.ui.words.res.aboutBuildVersion
import io.github.eslamasabry.oc.ui.words.res.appUpdateAvailableLine
import io.github.eslamasabry.oc.ui.words.res.appUpdateBuildLabel
import io.github.eslamasabry.oc.ui.words.res.appUpdateCancel
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckNow
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckSwitch
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckSwitchBody
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckedJustNow
import io.github.eslamasabry.oc.ui.words.res.appUpdateChecking
import io.github.eslamasabry.oc.ui.words.res.appUpdateCheckingOff
import io.github.eslamasabry.oc.ui.words.res.appUpdateCurrentVersion
import io.github.eslamasabry.oc.ui.words.res.appUpdateDownloadingTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstall
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstallReadyTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstalling
import io.github.eslamasabry.oc.ui.words.res.appUpdateNeverChecked
import io.github.eslamasabry.oc.ui.words.res.appUpdatePercent
import io.github.eslamasabry.oc.ui.words.res.appUpdateSizeMb
import io.github.eslamasabry.oc.ui.words.res.appUpdateTryAgain
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpToDate
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpdate
import io.github.eslamasabry.oc.ui.words.res.appUpdateWhatsNew
import io.github.eslamasabry.oc.ui.words.res.privacyPolicyTitle
import io.github.eslamasabry.oc.ui.words.res.shareRowDetail
import io.github.eslamasabry.oc.ui.words.res.shareRowTitle
import io.github.eslamasabry.oc.ui.words.res.starRowDetail
import io.github.eslamasabry.oc.ui.words.res.starRowTitle
import io.github.eslamasabry.oc.ui.words.res.starShareGroup
import io.github.eslamasabry.oc.ui.words.res.whatsNewRowTitle
import org.jetbrains.compose.resources.stringResource

/**
 * I20: the version and its state in one row, the state's one main action, Check now, the switch
 * that turns checks off, What's new and the build number.
 */
@Composable
internal fun ColumnScope.UpdatesBody(u: UpdateFacts, actions: SettingsActions) {
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.appUpdateCurrentVersion, u.current),
            modifier = Modifier.testTag("updates-state"),
            supporting = updateLine(u.state),
            supportingMaxLines = 3,
        )
    }
    Column(
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        when (u.state) {
            is UpdateState.Available ->
                KitButton(stringResource(Res.string.appUpdateUpdate), actions::onUpdateAction)
            is UpdateState.Downloading ->
                KitButton(
                    stringResource(Res.string.appUpdateCancel),
                    actions::onUpdateAction,
                    role = KitButtonRole.Secondary,
                )
            is UpdateState.Downloaded ->
                KitButton(stringResource(Res.string.appUpdateInstall), actions::onUpdateAction)
            is UpdateState.Failed ->
                KitButton(stringResource(Res.string.appUpdateTryAgain), actions::onCheckForUpdates)
            else -> Unit
        }
        val busy = u.state is UpdateState.Downloading || u.state == UpdateState.Installing
        if (!busy && u.state !is UpdateState.Failed) {
            KitButton(
                stringResource(Res.string.appUpdateCheckNow),
                actions::onCheckForUpdates,
                modifier = Modifier.testTag("updates-check-now"),
                role =
                    if (u.state is UpdateState.Available || u.state is UpdateState.Downloaded)
                        KitButtonRole.Secondary
                    else KitButtonRole.Primary,
                working = u.state == UpdateState.Checking,
            )
        }
    }
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.appUpdateCheckSwitch),
            checked = u.checksOn,
            supporting = stringResource(Res.string.appUpdateCheckSwitchBody),
            onCheckedChange = actions::onUpdateChecks,
        )
    }
    if (!u.checksOn) KitGroupNote(stringResource(Res.string.appUpdateCheckingOff))
    KitRowGroup {
        KitRow(
            title = stringResource(Res.string.appUpdateWhatsNew),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.onOutside(SettingsOutside.ReleaseNotes) },
        )
        KitRow(
            title = stringResource(Res.string.appUpdateBuildLabel),
            trailing = KitRowTrailing.Value(u.build, chevron = false),
        )
    }
}

@Composable
private fun updateLine(state: UpdateState): String =
    when (state) {
        UpdateState.NeverChecked -> stringResource(Res.string.appUpdateNeverChecked)
        UpdateState.Checking -> stringResource(Res.string.appUpdateChecking)
        is UpdateState.UpToDate ->
            state.checked?.let { stringResource(Res.string.appUpdateUpToDate, it) }
                ?: stringResource(Res.string.appUpdateCheckedJustNow)
        is UpdateState.Available ->
            stringResource(
                Res.string.appUpdateAvailableLine,
                state.version,
                stringResource(Res.string.appUpdateSizeMb, state.sizeMb),
            )
        is UpdateState.Downloading ->
            stringResource(Res.string.appUpdateDownloadingTitle, state.version) +
                " · " +
                stringResource(Res.string.appUpdatePercent, state.percent.toString())
        is UpdateState.Downloaded ->
            stringResource(Res.string.appUpdateInstallReadyTitle, state.version)
        UpdateState.Installing -> stringResource(Res.string.appUpdateInstalling)
        is UpdateState.Failed -> updateFailureWords(state.why)
    }

/**
 * L04: the version, What's new, the two quiet "Tell others" rows, the notices and the privacy
 * policy.
 */
@Composable
internal fun ColumnScope.AboutBody(
    about: AboutFacts,
    actions: SettingsActions,
    voiceLicenses: Boolean = false,
) {
    KitRowGroup {
        KitRow(title = stringResource(Res.string.aboutBuildVersion, about.version, about.build))
        if (about.whatsNew) {
            KitRow(
                title = stringResource(Res.string.whatsNewRowTitle),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.WhatsNew) },
            )
        }
    }
    KitRowGroup(label = stringResource(Res.string.starShareGroup)) {
        KitRow(
            title = stringResource(Res.string.starRowTitle),
            modifier = Modifier.testTag("about-star-github"),
            supporting = stringResource(Res.string.starRowDetail),
            onClick = { actions.onOutside(SettingsOutside.StarOnGitHub) },
        )
        KitRow(
            title = stringResource(Res.string.shareRowTitle),
            supporting = stringResource(Res.string.shareRowDetail),
            onClick = { actions.onOutside(SettingsOutside.ShareApp) },
        )
    }
    if (voiceLicenses) VoiceLicenseRows(actions)
    KitRowGroup {
        if (about.notices) {
            KitRow(
                title = stringResource(Res.string.aboutAllLicences),
                supporting = stringResource(Res.string.aboutAllLicencesDetail),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.Notices) },
            )
        }
        KitRow(
            title = stringResource(Res.string.privacyPolicyTitle),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.onOutside(SettingsOutside.PrivacyPolicy) },
        )
    }
}
