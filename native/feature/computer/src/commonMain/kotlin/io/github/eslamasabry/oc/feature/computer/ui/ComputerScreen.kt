package io.github.eslamasabry.oc.feature.computer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.computer.ComputerActions
import io.github.eslamasabry.oc.feature.computer.ComputerUi
import io.github.eslamasabry.oc.feature.computer.ImportPhase
import io.github.eslamasabry.oc.feature.computer.UpdateLine
import io.github.eslamasabry.oc.feature.computer.UpdateStep
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.claudeImportAlready
import io.github.eslamasabry.oc.ui.words.res.claudeImportEmptyBody
import io.github.eslamasabry.oc.ui.words.res.claudeImportEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.claudeImportFailed
import io.github.eslamasabry.oc.ui.words.res.claudeImportIntro
import io.github.eslamasabry.oc.ui.words.res.claudeImportLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.claudeImportLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.claudeImportRowDetail
import io.github.eslamasabry.oc.ui.words.res.claudeImportTitle
import io.github.eslamasabry.oc.ui.words.res.claudeImportUntitled
import io.github.eslamasabry.oc.ui.words.res.claudeImportWorking
import io.github.eslamasabry.oc.ui.words.res.hostUpdateConfirmAction
import io.github.eslamasabry.oc.ui.words.res.hostUpdateConfirmBody
import io.github.eslamasabry.oc.ui.words.res.hostUpdateConfirmKept
import io.github.eslamasabry.oc.ui.words.res.hostUpdateConfirmPauses
import io.github.eslamasabry.oc.ui.words.res.hostUpdateConfirmTitle
import io.github.eslamasabry.oc.ui.words.res.hostUpdateCurrent
import io.github.eslamasabry.oc.ui.words.res.hostUpdateDone
import io.github.eslamasabry.oc.ui.words.res.hostUpdateDoneNoVersion
import io.github.eslamasabry.oc.ui.words.res.hostUpdateDownloading
import io.github.eslamasabry.oc.ui.words.res.hostUpdateDropped
import io.github.eslamasabry.oc.ui.words.res.hostUpdateFailed
import io.github.eslamasabry.oc.ui.words.res.hostUpdateFinishing
import io.github.eslamasabry.oc.ui.words.res.hostUpdateInstalling
import io.github.eslamasabry.oc.ui.words.res.hostUpdateRow
import io.github.eslamasabry.oc.ui.words.res.hostUpdateRowRunning
import io.github.eslamasabry.oc.ui.words.res.hostUpdateRowUnknown
import io.github.eslamasabry.oc.ui.words.res.hostUpdateStarting
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.serversCancel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Agents › a computer: update its agents (Flutter host-update row and sheet,
 * B-AGENT-167) and import Claude Code conversations from its project (Flutter `ClaudeImportScreen`,
 * B-AGENT-174). Kit parts only; both acts are visible rows; the computer's own failure text is
 * never shown.
 */
@Composable
public fun ComputerScreen(state: ComputerUi, actions: ComputerActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = state.name,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            UpdatePart(state, actions, gutter)
            ImportPart(state, actions, gutter)
        }
    }
    KitConfirm(
        spec =
            if (state.confirming)
                KitConfirmSpec(
                    title = stringResource(Res.string.hostUpdateConfirmTitle),
                    confirmLabel = stringResource(Res.string.hostUpdateConfirmAction),
                    body = stringResource(Res.string.hostUpdateConfirmBody, state.name),
                    consequenceItems =
                        listOf(
                            KitConsequence(
                                stringResource(Res.string.hostUpdateConfirmPauses, state.name),
                                KitConsequenceMark.Info,
                            ),
                            KitConsequence(
                                stringResource(Res.string.hostUpdateConfirmKept),
                                KitConsequenceMark.Kept,
                            ),
                        ),
                    cancelLabel = stringResource(Res.string.serversCancel),
                )
            else null,
        onConfirm = actions::confirmUpdate,
        onCancel = actions::cancelUpdate,
    )
}

@Composable
private fun UpdatePart(state: ComputerUi, actions: ComputerActions, gutter: Modifier) {
    KitSectionLabel(stringResource(Res.string.hostUpdateConfirmAction))
    val stepLine = state.step?.let { stepWords(it) }
    KitRow(
        title = stringResource(Res.string.hostUpdateRow, state.name),
        supporting =
            state.version?.let { stringResource(Res.string.hostUpdateRowRunning, it) }
                ?: stringResource(Res.string.hostUpdateRowUnknown),
        supportingMaxLines = 3,
        trailing = KitRowTrailing.Chevron,
        enabled = !state.updating,
        disabledReason = stepLine,
        onClickLabel = stringResource(Res.string.hostUpdateConfirmAction),
        onClick = actions::askUpdate,
    )
    if (state.updating) {
        KitLoadingBar(true, stepLine.orEmpty(), gutter)
        KitNotice(stepLine.orEmpty(), gutter)
    }
    when (val line = state.update) {
        null -> Unit
        is UpdateLine.Done ->
            KitNotice(
                line.version?.let { stringResource(Res.string.hostUpdateDone, it) }
                    ?: stringResource(Res.string.hostUpdateDoneNoVersion),
                gutter,
            )
        UpdateLine.Current ->
            KitNotice(stringResource(Res.string.hostUpdateCurrent, state.name), gutter)
        UpdateLine.Dropped -> KitNotice(stringResource(Res.string.hostUpdateDropped), gutter)
        is UpdateLine.Failed ->
            KitErrorNotice(
                stringResource(Res.string.hostUpdateFailed),
                gutter,
                failure = line.failure,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
            )
    }
}

@Composable
private fun ImportPart(state: ComputerUi, actions: ComputerActions, gutter: Modifier) {
    KitSectionLabel(stringResource(Res.string.claudeImportTitle))
    state.importFailed?.let {
        KitErrorNotice(
            stringResource(Res.string.claudeImportFailed),
            gutter,
            failure = it,
            detailsLabel = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
        )
    }
    when (val phase = state.imports) {
        ImportPhase.Loading ->
            KitLoadingBar(true, stringResource(Res.string.claudeImportTitle), gutter)
        is ImportPhase.Failed ->
            KitErrorNotice(
                stringResource(Res.string.claudeImportLoadFailedBody),
                gutter,
                title = stringResource(Res.string.claudeImportLoadFailedTitle),
                failure = phase.failure,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                retry =
                    KitLineAction(stringResource(Res.string.kitTryAgain), actions::reloadImports),
            )
        is ImportPhase.Ready -> {
            val list = phase.list
            KitText(stringResource(Res.string.claudeImportIntro, list.project), gutter)
            if (list.items.isEmpty()) {
                KitNotice(
                    stringResource(Res.string.claudeImportEmptyBody),
                    gutter,
                    title = stringResource(Res.string.claudeImportEmptyTitle),
                )
            }
            val working = stringResource(Res.string.claudeImportWorking)
            for (item in list.items) {
                val busy = state.importing != null
                KitRow(
                    title = item.title ?: stringResource(Res.string.claudeImportUntitled),
                    supporting =
                        if (state.importing == item.key) working
                        else
                            stringResource(
                                Res.string.claudeImportRowDetail,
                                item.folder,
                                item.whenLabel,
                            ),
                    supportingMaxLines = 2,
                    trailing = KitRowTrailing.Chevron,
                    enabled = !busy,
                    disabledReason = if (busy) working else null,
                    onClick = { actions.import(item.key) },
                )
            }
            if (list.alreadyHere > 0) {
                KitText(
                    pluralStringResource(
                        Res.plurals.claudeImportAlready,
                        list.alreadyHere,
                        list.alreadyHere,
                    ),
                    gutter,
                )
            }
        }
    }
}

@Composable
private fun stepWords(step: UpdateStep): String =
    when (step) {
        UpdateStep.Starting -> stringResource(Res.string.hostUpdateStarting)
        UpdateStep.Downloading -> stringResource(Res.string.hostUpdateDownloading)
        UpdateStep.Installing -> stringResource(Res.string.hostUpdateInstalling)
        UpdateStep.Finishing -> stringResource(Res.string.hostUpdateFinishing)
    }
