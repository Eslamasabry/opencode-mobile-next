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
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/**
 * LinuxRoute.Storage (Flutter LinuxStoragePage): the total, where it goes by category (projects say they are never
 * cleared from here), then "Free up space" with one row per safe cleanup and what it would give back now. Each cleanup
 * asks first and then says what it freed.
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
        loading = state.loading && state.report == null,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.problem?.let { problem ->
                KitErrorNotice(
                    title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                    message = stringResource(problem.words()),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            }
            val dismiss = KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss)
            when (val result = state.result) {
                is CleanupResult.Freed -> KitNotice(message = stringResource(Res.string.linuxMgrCleanDone, size(result.bytes)), tone = StatusTone.Ok, dismiss = dismiss)
                CleanupResult.Nothing -> KitNotice(message = stringResource(Res.string.linuxMgrCleanNothing), dismiss = dismiss)
                null -> Unit
            }
            state.actProblem?.let { KitNotice(message = stringResource(it.words()), tone = StatusTone.Attention, dismiss = dismiss) }
            state.report?.let { report ->
                KitRowGroup(label = stringResource(Res.string.linuxMgrStorageTotal, size(report.total))) {
                    LinuxStorageCategory.entries.forEach { category ->
                        KitRow(
                            title = stringResource(category.title()),
                            supporting = stringResource(category.detail()),
                            supportingMaxLines = 2,
                            trailing = KitRowTrailing.Value(size(report.of(category)), chevron = false),
                        )
                    }
                }
                KitRowGroup(label = stringResource(Res.string.linuxMgrFreeUp)) {
                    LinuxStorage.cleanups.forEach { action ->
                        val bytes = state.cleanable[action.id]
                        val cleaning = state.cleaning == action.id
                        KitRow(
                            title = stringResource(action.id.title()),
                            supporting = stringResource(action.id.detail()),
                            supportingMaxLines = 2,
                            trailing =
                                when {
                                    cleaning || bytes == null -> KitRowTrailing.Value(stringResource(Res.string.linuxMgrMeasuring), chevron = false)
                                    bytes <= 0L -> KitRowTrailing.Value(stringResource(Res.string.linuxMgrCleanNone), chevron = false)
                                    else -> KitRowTrailing.Value(size(bytes), chevron = true)
                                },
                            enabled = !cleaning && bytes != null && bytes > 0L && state.cleaning == null,
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
                    title = stringResource(it.id.ask()),
                    confirmLabel = stringResource(it.id.title()),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.linuxMgrCleanBody, size(it.bytes)),
                    consequences = listOf(stringResource(Res.string.linuxMgrCleanKept)),
                )
            },
        onConfirm = actions::confirm,
        onCancel = actions::dismiss,
    )
}
