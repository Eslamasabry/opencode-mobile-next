package io.github.eslamasabry.oc.feature.updates.ui

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
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesAction
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesLine
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appTitle
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
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailChecksum
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailInstaller
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailNetwork
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailNoFile
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailPackage
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailSigner
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailStorage
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailVersion
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstall
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstallReadyTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstalling
import io.github.eslamasabry.oc.ui.words.res.appUpdateNeverChecked
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotInstalled
import io.github.eslamasabry.oc.ui.words.res.appUpdateOpenRelease
import io.github.eslamasabry.oc.ui.words.res.appUpdatePercent
import io.github.eslamasabry.oc.ui.words.res.appUpdatePermissionNeeded
import io.github.eslamasabry.oc.ui.words.res.appUpdateReadyTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateSizeMb
import io.github.eslamasabry.oc.ui.words.res.appUpdateTryAgain
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpToDate
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpdate
import io.github.eslamasabry.oc.ui.words.res.appUpdateVerifying
import io.github.eslamasabry.oc.ui.words.res.appUpdateWhatsNew
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceDaysAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceHoursAgo
import io.github.eslamasabry.oc.ui.words.res.e7WorkspaceMinutesAgo
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › This app › Updates (Flutter `UpdatesScreen`): the version with its state line and the state's one main
 * action, the download's progress, a failure in plain words, Details (the build, and the technical text only after a
 * failure), the permission note, the switch that lets the app look, Check now, and What's new.
 */
@Composable
public fun UpdatesScreen(state: UpdatesUi, actions: UpdatesActions) {
    val gutter = LocalKitTokens.current.gutter.dp
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.appUpdatesTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            val line = lineWords(state)
            KitRowGroup {
                KitRow(
                    title =
                        state.version?.let { stringResource(Res.string.appUpdateCurrentVersion, it) }
                            ?: stringResource(Res.string.appTitle),
                    modifier = Modifier.testTag("updates-state"),
                    supporting = line,
                    supportingMaxLines = 3,
                    trailing = state.action?.let { KitRowTrailing.Action(mainAction(it, actions)) } ?: KitRowTrailing.None,
                )
            }
            if (state.showProgress) {
                val percent = state.percent
                val progress = state.progress
                KitProgressView(
                    progress =
                        if (progress == null || percent == null) KitProgress.Waiting()
                        else KitProgress.Known(progress, caption = stringResource(Res.string.appUpdatePercent, percent.toString())),
                    modifier = Modifier.padding(horizontal = gutter),
                )
            }
            state.failure?.let { failure ->
                KitNotice(
                    failureWords(failure),
                    Modifier.padding(horizontal = gutter).testTag("updates-failure"),
                    tone = StatusTone.Failure,
                )
            }
            state.build?.let { build ->
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    modifier = Modifier.padding(horizontal = gutter),
                    notes = listOfNotNull("${stringResource(Res.string.appUpdateBuildLabel)} · $build", state.detail),
                )
            }
            if (state.needsPermission) {
                KitNotice(stringResource(Res.string.appUpdatePermissionNeeded), Modifier.padding(horizontal = gutter))
            }
            if (state.supported) {
                KitRowGroup {
                    KitSwitchRow(
                        title = stringResource(Res.string.appUpdateCheckSwitch),
                        checked = state.checksOn,
                        supporting = stringResource(Res.string.appUpdateCheckSwitchBody),
                        onCheckedChange = actions::onChecks,
                    )
                    KitRow(
                        title = stringResource(Res.string.appUpdateCheckNow),
                        modifier = Modifier.testTag("updates-check-now"),
                        enabled = state.checkNowEnabled,
                        disabledReason = if (state.checkNowEnabled) null else line,
                        onClick = actions::onCheckNow,
                    )
                }
            }
            state.notes?.let { notes ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                    KitSectionLabel(stringResource(Res.string.appUpdateWhatsNew))
                    // Plain reading text: the kit's Markdown renderer takes parsed blocks that a feature cannot build
                    // (:core:markdown is out of reach), and a link in the notes must never open without the link policy.
                    KitText(notes, Modifier.padding(horizontal = gutter).testTag("updates-notes"))
                }
            }
        }
    }
}

@Composable
private fun mainAction(action: UpdatesAction, actions: UpdatesActions): KitLineAction =
    when (action) {
        UpdatesAction.Update -> KitLineAction(stringResource(Res.string.appUpdateUpdate), actions::onUpdate, testTag = "updates-update")
        UpdatesAction.TryAgain ->
            KitLineAction(stringResource(Res.string.appUpdateTryAgain), actions::onTryAgain, testTag = "updates-update")
        UpdatesAction.OpenRelease ->
            KitLineAction(stringResource(Res.string.appUpdateOpenRelease), actions::onOpenRelease, testTag = "updates-update")
        UpdatesAction.Cancel -> KitLineAction(stringResource(Res.string.appUpdateCancel), actions::onCancel, testTag = "updates-cancel")
        UpdatesAction.Install -> KitLineAction(stringResource(Res.string.appUpdateInstall), actions::onInstall, testTag = "updates-install")
    }

@Composable
private fun lineWords(state: UpdatesUi): String {
    val v = state.releaseVersion
    return when (state.line) {
        UpdatesLine.NeverChecked -> stringResource(Res.string.appUpdateNeverChecked)
        UpdatesLine.CheckingOff -> stringResource(Res.string.appUpdateCheckingOff)
        UpdatesLine.Checking -> stringResource(Res.string.appUpdateChecking)
        UpdatesLine.CheckedJustNow -> stringResource(Res.string.appUpdateCheckedJustNow)
        UpdatesLine.CheckedMinutesAgo ->
            stringResource(Res.string.appUpdateUpToDate, stringResource(Res.string.e7WorkspaceMinutesAgo, state.agoCount))
        UpdatesLine.CheckedHoursAgo ->
            stringResource(Res.string.appUpdateUpToDate, stringResource(Res.string.e7WorkspaceHoursAgo, state.agoCount))
        UpdatesLine.CheckedDaysAgo ->
            stringResource(Res.string.appUpdateUpToDate, stringResource(Res.string.e7WorkspaceDaysAgo, state.agoCount))
        UpdatesLine.Available ->
            stringResource(
                Res.string.appUpdateAvailableLine,
                v,
                stringResource(Res.string.appUpdateSizeMb, (state.sizeMb ?: 0L).toString()),
            )
        UpdatesLine.AvailableNoSize -> stringResource(Res.string.appUpdateReadyTitle, v)
        UpdatesLine.NotInstalled -> stringResource(Res.string.appUpdateNotInstalled, v)
        UpdatesLine.Downloading -> stringResource(Res.string.appUpdateDownloadingTitle, v)
        UpdatesLine.Verifying -> stringResource(Res.string.appUpdateVerifying)
        UpdatesLine.Installing -> stringResource(Res.string.appUpdateInstalling)
        UpdatesLine.ReadyToInstall -> stringResource(Res.string.appUpdateInstallReadyTitle, v)
    }
}

/** One plain sentence per failure; the technical text stays under Details. */
@Composable
internal fun failureWords(failure: UpdateFailure): String =
    stringResource(
        when (failure) {
            UpdateFailure.Network -> Res.string.appUpdateFailNetwork
            UpdateFailure.NoFile -> Res.string.appUpdateFailNoFile
            UpdateFailure.Checksum -> Res.string.appUpdateFailChecksum
            UpdateFailure.Signer -> Res.string.appUpdateFailSigner
            UpdateFailure.Package -> Res.string.appUpdateFailPackage
            UpdateFailure.Version -> Res.string.appUpdateFailVersion
            UpdateFailure.Storage -> Res.string.appUpdateFailStorage
            UpdateFailure.Installer -> Res.string.appUpdateFailInstaller
        }
    )
