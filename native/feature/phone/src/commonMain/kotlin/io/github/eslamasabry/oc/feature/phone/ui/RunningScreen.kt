package io.github.eslamasabry.oc.feature.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitCardKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRole
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.RunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.RunningUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.childLimitBody
import io.github.eslamasabry.oc.ui.words.res.childLimitHide
import io.github.eslamasabry.oc.ui.words.res.childLimitOpenAbout
import io.github.eslamasabry.oc.ui.words.res.childLimitOpenDeveloper
import io.github.eslamasabry.oc.ui.words.res.childLimitStepsEnable
import io.github.eslamasabry.oc.ui.words.res.childLimitStepsOpen
import io.github.eslamasabry.oc.ui.words.res.childLimitStepsText
import io.github.eslamasabry.oc.ui.words.res.childLimitTitle
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.runningPhoneEmpty
import io.github.eslamasabry.oc.ui.words.res.runningPhoneEmptyBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLimitBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLimitTitle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneMarkStuck
import io.github.eslamasabry.oc.ui.words.res.runningPhoneRefreshFailed
import io.github.eslamasabry.oc.ui.words.res.runningPhoneServerRoot
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStop
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopBody
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopFailed
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopNamed
import io.github.eslamasabry.oc.ui.words.res.runningPhoneStopTitle
import io.github.eslamasabry.oc.ui.words.res.runningPhoneTitle
import io.github.eslamasabry.oc.ui.words.res.termuxProcsAboutHostApp
import io.github.eslamasabry.oc.ui.words.res.termuxProcsAutoRefresh
import io.github.eslamasabry.oc.ui.words.res.termuxProcsStopping
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/**
 * PhoneRoute.Running (plan 07 F08): the near-limit notice, the guided child-process-limit card (plan 11 §7.2), then
 * one row per program. Protected rows carry no Stop and say where they are controlled. Kit parts only (K1).
 */
@Composable
public fun RunningScreen(state: RunningUi, actions: RunningActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.runningPhoneTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.loadFailed) {
                KitErrorNotice(
                    title = stringResource(Res.string.runningPhoneLoadFailedTitle),
                    message = stringResource(Res.string.runningPhoneLoadFailedBody),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            }
            if (state.refreshFailed) KitNotice(message = stringResource(Res.string.runningPhoneRefreshFailed))
            val count = state.count
            if (state.nearLimit && count != null) {
                KitNotice(
                    title = stringResource(Res.string.runningPhoneLimitTitle),
                    message = stringResource(Res.string.runningPhoneLimitBody, count, state.cap),
                    tone = StatusTone.Attention,
                )
            }
            state.childLimit?.let { ChildLimitCard(it, actions) }
            if (state.stopFailed) {
                KitNotice(
                    message = stringResource(Res.string.runningPhoneStopFailed),
                    tone = StatusTone.Failure,
                    dismiss = KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss),
                )
            }
            if (!state.loading && !state.loadFailed && state.rows.isEmpty()) {
                KitNotice(
                    title = stringResource(Res.string.runningPhoneEmpty),
                    message = stringResource(Res.string.runningPhoneEmptyBody),
                    liveRegion = false,
                )
            }
            if (state.rows.isNotEmpty()) {
                KitRowGroup(note = stringResource(Res.string.termuxProcsAutoRefresh)) {
                    state.rows.forEach { ProcessRow(it, stopping = state.stopping == it.pid, actions) }
                }
            } else if (!state.loadFailed) {
                KitGroupNote(stringResource(Res.string.termuxProcsAutoRefresh))
            }
        }
    }
    val asked = state.confirmStop
    KitConfirm(
        spec =
            asked?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.runningPhoneStopTitle, it.name),
                    confirmLabel = stringResource(Res.string.runningPhoneStopNamed, it.name),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.runningPhoneStopBody),
                )
            },
        onConfirm = actions::confirmStop,
        onCancel = actions::dismiss,
    )
}

@Composable
private fun ProcessRow(row: ProcessRowUi, stopping: Boolean, actions: RunningActions) {
    val details =
        listOfNotNull(
            row.kind.words(),
            row.cpuPercent?.let { "⁦$it %⁩" },
            if (row.leftBehind) stringResource(Res.string.runningPhoneMarkStuck) else null,
        ).joinToString(" · ")
    val supporting =
        when (row.protectedAs) {
            ProcessRole.ServerRoot -> stringResource(Res.string.runningPhoneServerRoot)
            ProcessRole.App -> stringResource(Res.string.termuxProcsAboutHostApp)
            ProcessRole.Normal, null -> if (stopping) stringResource(Res.string.termuxProcsStopping) else details.ifBlank { null }
        }
    KitRow(
        title = row.name,
        supporting = supporting,
        supportingMaxLines = 2,
        trailing =
            if (row.stoppable && !stopping) {
                KitRowTrailing.Action(KitLineAction(stringResource(Res.string.runningPhoneStop), { actions.askStop(row.pid) }))
            } else {
                KitRowTrailing.None
            },
    )
}

/** The guided card (port of child_limit_card.dart): one notice with its steps, one button when one can open, Hide. */
@Composable
private fun ChildLimitCard(kind: ChildLimitCardKind, actions: RunningActions) {
    val steps =
        when (kind) {
            ChildLimitCardKind.OpenSettings -> Res.string.childLimitStepsOpen
            ChildLimitCardKind.EnableDeveloperOptions -> Res.string.childLimitStepsEnable
            ChildLimitCardKind.InstructionsOnly -> Res.string.childLimitStepsText
        }
    val open =
        when (kind) {
            ChildLimitCardKind.OpenSettings -> Res.string.childLimitOpenDeveloper
            ChildLimitCardKind.EnableDeveloperOptions -> Res.string.childLimitOpenAbout
            ChildLimitCardKind.InstructionsOnly -> null
        }
    KitNotice(
        title = stringResource(Res.string.childLimitTitle),
        message = stringResource(Res.string.childLimitBody),
        notes = listOf(stringResource(steps)),
        actions = listOfNotNull(open?.let { KitLineAction(stringResource(it), actions::openChildLimitSettings, testTag = "child-limit-open") }),
        dismiss = KitLineAction(stringResource(Res.string.childLimitHide), actions::hideChildLimit, testTag = "child-limit-hide"),
        liveRegion = false,
    )
}
