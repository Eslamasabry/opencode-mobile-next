package io.github.eslamasabry.oc.feature.updates.ui

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.updates.UpdateFailure
import io.github.eslamasabry.oc.feature.updates.presentation.NoteLinkAction
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesAction
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesActions
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesLine
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeDaysAgo
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeHoursAgo
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeMinutesAgo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
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
import io.github.eslamasabry.oc.ui.words.res.appUpdateFileSignature
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstall
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstallReadyTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateInstalling
import io.github.eslamasabry.oc.ui.words.res.appUpdateNeverChecked
import io.github.eslamasabry.oc.ui.words.res.appUpdateNoBrowser
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotInstalled
import io.github.eslamasabry.oc.ui.words.res.appUpdateOpenRelease
import io.github.eslamasabry.oc.ui.words.res.appUpdateOwnSignature
import io.github.eslamasabry.oc.ui.words.res.appUpdatePercent
import io.github.eslamasabry.oc.ui.words.res.appUpdatePermissionNeeded
import io.github.eslamasabry.oc.ui.words.res.appUpdateReadyTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateSizeMb
import io.github.eslamasabry.oc.ui.words.res.appUpdateTestBuildLine
import io.github.eslamasabry.oc.ui.words.res.appUpdateTestBuildNotice
import io.github.eslamasabry.oc.ui.words.res.appUpdateTryAgain
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpToDate
import io.github.eslamasabry.oc.ui.words.res.appUpdateUpdate
import io.github.eslamasabry.oc.ui.words.res.appUpdateVerifying
import io.github.eslamasabry.oc.ui.words.res.appUpdateWhatsNew
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkAction
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkBody
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkTitle
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.notifyDismiss
import io.github.eslamasabry.oc.ui.words.res.whatsNewShowUpdates
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › This app › Updates (Flutter `UpdatesScreen`): the version with its state line and the
 * state's one main action, the download's progress, a failure in plain words, Details (the build,
 * and the technical text only after a failure), the permission note, the switch that lets the app
 * look, Check now, and What's new.
 */
@Composable
public fun UpdatesScreen(
    state: UpdatesUi,
    actions: UpdatesActions,
    showArrival: Boolean = false,
    dismissArrival: () -> Unit = {},
) {
    val gutter = LocalKitTokens.current.gutter.dp
    val status =
        if (state.noBrowser) {
            KitStatus(
                kind = KitStatusKind.Info,
                message = stringResource(Res.string.appUpdateNoBrowser),
                id = "updates:no-browser",
                tone = StatusTone.Failure,
                onDismiss = { actions.onNoteLink(NoteLinkAction.DismissNoBrowser) },
                dismissLabel = stringResource(Res.string.notifyDismiss),
            )
        } else {
            null
        }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.appUpdatesTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onBack,
            )
        },
        status = status,
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        UpdatesPageBody(state, actions, gutter, showArrival, dismissArrival)
        KitConfirm(
            spec = null,
            onConfirm = { actions.onNoteLink(NoteLinkAction.Confirm) },
            onCancel = { actions.onNoteLink(NoteLinkAction.DismissPrompt) },
        )
    }
}

@Composable
private fun UpdatesPageBody(
    state: UpdatesUi,
    actions: UpdatesActions,
    gutter: Dp,
    showArrival: Boolean,
    dismissArrival: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(kitPageScrollState())
            .padding(vertical = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        val line = lineWords(state)
        KitRowGroup {
            KitRow(
                title =
                    state.version?.let {
                        stringResource(Res.string.appUpdateCurrentVersion, it)
                    } ?: stringResource(Res.string.appTitle),
                modifier = Modifier.testTag("updates-state"),
                supporting = line,
                supportingMaxLines = 3,
                trailing =
                    state.action?.let { KitRowTrailing.Action(mainAction(it, actions)) }
                        ?: KitRowTrailing.None,
            )
        }
        if (state.isTestBuild) {
            KitNotice(
                stringResource(Res.string.appUpdateTestBuildNotice),
                Modifier.padding(horizontal = gutter).testTag("updates-test-build"),
            )
        }
        if (state.showProgress) UpdatesDownloadProgress(state, gutter)
        state.failure?.let { failure ->
            KitNotice(
                failureWords(failure),
                Modifier.padding(horizontal = gutter).testTag("updates-failure"),
                tone = StatusTone.Failure,
            )
        }
        UpdatesDetailsFold(state, gutter)
        if (state.needsPermission) {
            KitNotice(
                stringResource(Res.string.appUpdatePermissionNeeded),
                Modifier.padding(horizontal = gutter),
            )
        }
        if (state.supported && !state.isTestBuild) {
            UpdatesCheckControls(state, actions, line, showArrival, dismissArrival)
        }
        state.notes?.let { notes -> UpdatesWhatsNew(notes, actions, gutter) }
    }
}

@Composable
private fun UpdatesDownloadProgress(state: UpdatesUi, gutter: Dp) {
    val percent = state.percent
    val progress = state.progress
    KitProgressView(
        progress =
            if (progress == null || percent == null) KitProgress.Waiting()
            else
                KitProgress.Known(
                    progress,
                    caption = stringResource(Res.string.appUpdatePercent, percent.toString()),
                ),
        modifier = Modifier.padding(horizontal = gutter),
    )
}

@Composable
private fun UpdatesDetailsFold(state: UpdatesUi, gutter: Dp) {
    val build = state.build
    val signer = state.signerDetails
    val detail = state.detail
    if (build == null && signer == null && detail == null) return
    val redactor = LocalKitRedactor.current
    val notes =
        listOfNotNull(
            build?.let { "${stringResource(Res.string.appUpdateBuildLabel)} · $it" },
            detail,
        )
    val values =
        signer?.let { sig ->
            listOf(
                KitTechnicalValue(
                    stringResource(Res.string.appUpdateOwnSignature),
                    redactor.details(sig.own),
                ),
                KitTechnicalValue(
                    stringResource(Res.string.appUpdateFileSignature),
                    redactor.details(sig.update),
                ),
            )
        }.orEmpty()
    KitDetailsFold(
        label = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        modifier = Modifier.padding(horizontal = gutter),
        values = values,
        notes = notes,
    )
}

@Composable
private fun UpdatesCheckControls(
    state: UpdatesUi,
    actions: UpdatesActions,
    line: String,
    showArrival: Boolean,
    dismissArrival: () -> Unit,
) {
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.appUpdateCheckSwitch),
            checked = state.checksOn,
            supporting = stringResource(Res.string.appUpdateCheckSwitchBody),
            onCheckedChange = actions::onChecks,
        )
        KitTip(
            stringResource(Res.string.whatsNewShowUpdates),
            showArrival,
            dismissArrival,
        ) {
            KitRow(
                title = stringResource(Res.string.appUpdateCheckNow),
                modifier = Modifier.testTag("updates-check-now"),
                enabled = state.checkNowEnabled,
                disabledReason = if (state.checkNowEnabled) null else line,
                onClick = actions::onCheckNow,
            )
        }
    }
}

@Composable
private fun UpdatesWhatsNew(notes: String, actions: UpdatesActions, gutter: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitSectionLabel(stringResource(Res.string.appUpdateWhatsNew))
        val blocks = remember(notes) { KitMdSource.blocks("updates-notes", notes, streaming = false) }
        val links =
            remember(actions) {
                KitMarkdownActions(onLink = {})
            }
        KitMarkdown(
            blocks,
            links,
            Modifier.padding(horizontal = gutter).testTag("updates-notes"),
        )
    }
}

@Composable
private fun mainAction(action: UpdatesAction, actions: UpdatesActions): KitLineAction =
    when (action) {
        UpdatesAction.Update ->
            KitLineAction(
                stringResource(Res.string.appUpdateUpdate),
                actions::onUpdate,
                testTag = "updates-update",
            )
        UpdatesAction.TryAgain ->
            KitLineAction(
                stringResource(Res.string.appUpdateTryAgain),
                actions::onTryAgain,
                testTag = "updates-update",
            )
        UpdatesAction.OpenRelease ->
            KitLineAction(
                stringResource(Res.string.appUpdateOpenRelease),
                actions::onOpenRelease,
                testTag = "updates-update",
            )
        UpdatesAction.Cancel ->
            KitLineAction(
                stringResource(Res.string.appUpdateCancel),
                actions::onCancel,
                testTag = "updates-cancel",
            )
        UpdatesAction.Install ->
            KitLineAction(
                stringResource(Res.string.appUpdateInstall),
                actions::onInstall,
                testTag = "updates-install",
            )
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
            stringResource(
                Res.string.appUpdateUpToDate,
                kitRelativeMinutesAgo(state.agoCount.toLong()),
            )
        UpdatesLine.CheckedHoursAgo ->
            stringResource(
                Res.string.appUpdateUpToDate,
                kitRelativeHoursAgo(state.agoCount.toLong()),
            )
        UpdatesLine.CheckedDaysAgo ->
            stringResource(
                Res.string.appUpdateUpToDate,
                kitRelativeDaysAgo(state.agoCount.toLong()),
            )
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
        UpdatesLine.TestBuild -> stringResource(Res.string.appUpdateTestBuildLine)
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
