package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorage
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageCategory
import io.github.eslamasabry.oc.feature.linux.presentation.CleanupResult
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxStorageActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxStorageUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
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
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanDone
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanKept
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanNone
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanNothing
import io.github.eslamasabry.oc.ui.words.res.linuxMgrFreeUp
import io.github.eslamasabry.oc.ui.words.res.linuxMgrLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrMeasuring
import io.github.eslamasabry.oc.ui.words.res.linuxMgrStorageTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrStorageTotal
import io.github.eslamasabry.oc.ui.words.res.linuxOutputError
import io.github.eslamasabry.oc.ui.words.res.linuxOutputWarning
import io.github.eslamasabry.oc.ui.words.res.linuxStorageScanDone
import io.github.eslamasabry.oc.ui.words.res.linuxStorageScanIdle
import io.github.eslamasabry.oc.ui.words.res.linuxStorageScanLog
import io.github.eslamasabry.oc.ui.words.res.linuxStorageScanRunning
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesB
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesGb
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesKb
import io.github.eslamasabry.oc.ui.words.res.termuxStorageBytesMb
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/**
 * LinuxRoute.Storage (Flutter LinuxStoragePage): the total, where it goes by category (projects say
 * they are never cleared from here), then "Free up space" with one row per safe cleanup and what it
 * would give back now. Each cleanup asks first and then says what it freed.
 */
@Composable
public fun LinuxStorageScreen(state: LinuxStorageUi, actions: LinuxStorageActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.linuxMgrStorageTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = false,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val scanState = state.scan.state
            KitNotice(
                message =
                    when (scanState) {
                        io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageScanState
                            .Running ->
                            state.scan.stage?.let {
                                stringResource(Res.string.linuxStorageScanRunning, it.title())
                            } ?: stringResource(Res.string.linuxMgrMeasuring)
                        io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageScanState.Done ->
                            stringResource(Res.string.linuxStorageScanDone)
                        io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageScanState
                            .Failed -> stringResource(Res.string.linuxMgrLoadFailedTitle)
                        io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageScanState.Idle ->
                            stringResource(Res.string.linuxStorageScanIdle)
                    },
                tone =
                    if (
                        scanState ==
                            io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageScanState
                                .Running
                    )
                        StatusTone.Progress
                    else StatusTone.Neutral,
            )
            if (state.scan.headings.isNotEmpty()) {
                io.github.eslamasabry.oc.ui.kit.content.log.KitLogPanel(
                    lines =
                        state.scan.headings.map {
                            io.github.eslamasabry.oc.ui.kit.content.log.KitLogLine(it.title())
                        },
                    title = stringResource(Res.string.linuxStorageScanLog),
                    warningLabel = stringResource(Res.string.linuxOutputWarning),
                    errorLabel = stringResource(Res.string.linuxOutputError),
                )
            }
            state.problem?.let { problem ->
                KitErrorNotice(
                    title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                    message = problem.words(),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            }
            val dismiss =
                KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss)
            when (val result = state.result) {
                is CleanupResult.Freed ->
                    KitNotice(
                        message =
                            stringResource(Res.string.linuxMgrCleanDone, storageSize(result.bytes)),
                        tone = StatusTone.Ok,
                        dismiss = dismiss,
                    )
                CleanupResult.Nothing ->
                    KitNotice(
                        message = stringResource(Res.string.linuxMgrCleanNothing),
                        dismiss = dismiss,
                    )
                null -> Unit
            }
            state.actProblem?.let {
                KitNotice(message = it.words(), tone = StatusTone.Attention, dismiss = dismiss)
            }
            state.report?.let { report ->
                KitRowGroup(
                    label =
                        stringResource(Res.string.linuxMgrStorageTotal, storageSize(report.total))
                ) {
                    LinuxStorageCategory.entries.forEach { category ->
                        KitRow(
                            title = category.title(),
                            supporting = category.detail(),
                            supportingMaxLines = 2,
                            trailing =
                                KitRowTrailing.Value(
                                    storageSize(report.of(category)),
                                    chevron = false,
                                ),
                        )
                    }
                }
                KitRowGroup(label = stringResource(Res.string.linuxMgrFreeUp)) {
                    LinuxStorage.cleanups.forEach { action ->
                        val bytes = state.cleanable[action.id]
                        val cleaning = state.cleaning == action.id
                        KitRow(
                            title = action.id.title(),
                            supporting = action.id.detail(),
                            supportingMaxLines = 2,
                            trailing =
                                when {
                                    cleaning || bytes == null ->
                                        KitRowTrailing.Value(
                                            stringResource(Res.string.linuxMgrMeasuring),
                                            chevron = false,
                                        )
                                    bytes <= 0L ->
                                        KitRowTrailing.Value(
                                            stringResource(Res.string.linuxMgrCleanNone),
                                            chevron = false,
                                        )
                                    else -> KitRowTrailing.Value(storageSize(bytes), chevron = true)
                                },
                            enabled =
                                !cleaning && bytes != null && bytes > 0L && state.cleaning == null,
                            disabledReason = stringResource(Res.string.linuxMgrCleanNone),
                            onClick = { actions.askCleanup(action.id) },
                        )
                    }
                }
            }
        }
    }
    val ask = state.confirm
    KitConfirm(
        spec =
            ask?.let {
                KitConfirmSpec(
                    title = it.id.ask(),
                    confirmLabel = it.id.title(),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.linuxMgrCleanBody, storageSize(it.bytes)),
                    consequences = listOf(stringResource(Res.string.linuxMgrCleanKept)),
                )
            },
        onConfirm = actions::confirm,
        onCancel = actions::dismiss,
    )
}

@Composable
private fun storageSize(bytes: Long): String {
    val amount = io.github.eslamasabry.oc.feature.linux.presentation.LinuxSize.storage(bytes)
    val unit =
        when (amount.unit) {
            io.github.eslamasabry.oc.feature.linux.presentation.StorageByteUnit.B ->
                Res.string.termuxStorageBytesB
            io.github.eslamasabry.oc.feature.linux.presentation.StorageByteUnit.KB ->
                Res.string.termuxStorageBytesKb
            io.github.eslamasabry.oc.feature.linux.presentation.StorageByteUnit.MB ->
                Res.string.termuxStorageBytesMb
            io.github.eslamasabry.oc.feature.linux.presentation.StorageByteUnit.GB ->
                Res.string.termuxStorageBytesGb
        }
    return "⁦${stringResource(unit, amount.number)}⁩"
}
