package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceStatus
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServicesActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServicesUi
import io.github.eslamasabry.oc.feature.linux.presentation.ServiceAskKind
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Project commands and discovered servers, using only kit controls with explicit visible actions.
 */
@Composable
public fun LinuxServicesScreen(state: LinuxServicesUi, actions: LinuxServicesActions) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.linuxSvcTitle),
                subtitle = state.directory,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.loading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.failure != null || state.refusal != null) {
                KitErrorNotice(
                    title = stringResource(Res.string.linuxSvcFailed),
                    message =
                        stringResource(
                            if (state.refusal != null) Res.string.linuxSvcUnconfirmed
                            else Res.string.linuxMgrProblemFailed
                        ),
                    failure = state.failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::refresh),
                )
            }
            if (!state.readable) KitNotice(message = stringResource(Res.string.linuxSvcCorrupt))
            if (!state.supported && !state.loading)
                KitNotice(message = stringResource(Res.string.linuxSvcUnsupported))
            if (state.readable)
                KitButton(
                    stringResource(Res.string.linuxSvcAdd),
                    actions::add,
                    role = KitButtonRole.Primary,
                    working = state.working,
                )
            KitButton(
                stringResource(Res.string.linuxSvcRefresh),
                actions::refresh,
                role = KitButtonRole.Tertiary,
                working = state.working,
            )
            KitRowGroup(
                label = stringResource(Res.string.linuxSvcSaved),
                note = stringResource(Res.string.linuxSvcSavedNote),
            ) {
                if (state.services.isEmpty()) KitText(stringResource(Res.string.linuxSvcEmpty))
                state.services.forEach { row ->
                    val status =
                        stringResource(
                            when (row.status) {
                                DevelopmentServiceStatus.NotStarted -> Res.string.linuxSvcNotStarted
                                DevelopmentServiceStatus.Running -> Res.string.linuxSvcRunning
                                DevelopmentServiceStatus.Stopped -> Res.string.linuxSvcStopped
                                DevelopmentServiceStatus.Unknown -> Res.string.linuxSvcUnknown
                            }
                        )
                    KitRow(
                        title = row.service.name,
                        supporting = status + " · " + row.service.command,
                        supportingMaxLines = 3,
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.select(row.service.id) },
                    )
                }
            }
            if (state.detected.isNotEmpty())
                KitRowGroup(label = stringResource(Res.string.linuxSvcDetected)) {
                    state.detected.forEach { row ->
                        KitRow(
                            title =
                                row.name.ifBlank {
                                    stringResource(
                                        Res.string.linuxDevPorts,
                                        row.ports.joinToString(", "),
                                    )
                                },
                            supporting =
                                stringResource(
                                    Res.string.linuxDevPorts,
                                    row.ports.joinToString(", "),
                                ) +
                                    " · " +
                                    stringResource(
                                        if (row.hasLogs) Res.string.linuxDevReadLog
                                        else Res.string.linuxDevNoLog
                                    ),
                            supportingMaxLines = 2,
                            onClick = if (row.hasLogs) ({ actions.detectedLogs(row.id) }) else null,
                            trailing =
                                if (row.canStop && !state.working)
                                    KitRowTrailing.Action(
                                        KitLineAction(
                                            stringResource(Res.string.linuxDevStop),
                                            { actions.ask(ServiceAskKind.StopDetected, row.id) },
                                        )
                                    )
                                else KitRowTrailing.None,
                        )
                    }
                }
        }
    }
    val selected = state.services.firstOrNull { it.service.id == state.selected }
    KitSheet(
        visible = selected != null,
        title = selected?.service?.name ?: stringResource(Res.string.linuxSvcTitle),
        onDismissRequest = actions::dismiss,
        primary =
            selected?.let { row ->
                when {
                    row.canStart ->
                        KitAction(
                            stringResource(Res.string.linuxSvcStart),
                            { actions.start(row.service.id) },
                        )
                    row.canStop ->
                        KitAction(
                            stringResource(Res.string.linuxDevStop),
                            { actions.ask(ServiceAskKind.Stop, row.service.id) },
                        )
                    else -> null
                }
            },
        secondary =
            selected?.service?.run?.let {
                KitAction(
                    stringResource(Res.string.linuxDevReadLog),
                    { actions.logs(selected.service.id) },
                )
            },
        tertiary =
            buildList {
                selected?.let { row ->
                    if (row.canStop)
                        add(
                            KitAction(
                                stringResource(Res.string.linuxSvcRestart),
                                { actions.restart(row.service.id) },
                            )
                        )
                    if (row.canReport)
                        add(
                            KitAction(
                                stringResource(Res.string.linuxSvcReport),
                                { actions.report(row.service.id) },
                            )
                        )
                    if (row.status == DevelopmentServiceStatus.Unknown)
                        add(
                            KitAction(
                                stringResource(Res.string.linuxSvcForget),
                                { actions.ask(ServiceAskKind.Forget, row.service.id) },
                            )
                        )
                    add(
                        KitAction(
                            stringResource(Res.string.linuxSvcRemove),
                            { actions.ask(ServiceAskKind.Remove, row.service.id) },
                        )
                    )
                }
            },
    ) {
        selected?.let { KitText(it.service.command) }
    }
    val invalidName = stringResource(Res.string.linuxSvcInvalidName)
    val invalidCommand = stringResource(Res.string.linuxSvcInvalidCommand)
    KitInputDialog(
        visible = state.adding == 1,
        title = stringResource(Res.string.linuxSvcAdd),
        fieldLabel = stringResource(Res.string.linuxSvcName),
        confirmLabel = stringResource(Res.string.linuxSvcNext),
        validate = { if (it.isBlank() || it.length > 80) invalidName else null },
        onResult = actions::finishName,
    )
    KitInputDialog(
        visible = state.adding == 2,
        title = stringResource(Res.string.linuxSvcCommand),
        fieldLabel = stringResource(Res.string.linuxSvcCommand),
        confirmLabel = stringResource(Res.string.linuxSvcSave),
        validate = { if (it.isBlank() || it.length > 4096) invalidCommand else null },
        onResult = actions::finishCommand,
    )
    KitAlert(
        visible = state.log != null || state.report != null,
        title =
            stringResource(
                if (state.report != null) Res.string.linuxSvcReport else Res.string.linuxDevLog
            ),
        body =
            state.report?.let {
                listOf(it.title, it.detail, it.logExcerpt)
                    .filter(String::isNotBlank)
                    .joinToString("\n\n")
            } ?: state.log?.ifEmpty { stringResource(Res.string.linuxDevLogEmpty) },
        onDismissRequest = actions::dismiss,
    )
    val ask = state.ask
    KitConfirm(
        spec =
            ask?.let {
                val title =
                    when (it.kind) {
                        ServiceAskKind.Stop,
                        ServiceAskKind.StopDetected -> Res.string.linuxDevStopTitle
                        ServiceAskKind.Remove -> Res.string.linuxSvcRemoveTitle
                        ServiceAskKind.Forget -> Res.string.linuxSvcForgetTitle
                    }
                val label =
                    when (it.kind) {
                        ServiceAskKind.Stop,
                        ServiceAskKind.StopDetected -> Res.string.linuxDevStop
                        ServiceAskKind.Remove -> Res.string.linuxSvcRemove
                        ServiceAskKind.Forget -> Res.string.linuxSvcForget
                    }
                val body =
                    when (it.kind) {
                        ServiceAskKind.Stop,
                        ServiceAskKind.StopDetected -> Res.string.linuxSvcStopBody
                        ServiceAskKind.Remove -> Res.string.linuxSvcRemoveBody
                        ServiceAskKind.Forget -> Res.string.linuxSvcForgetBody
                    }
                KitConfirmSpec(
                    stringResource(title),
                    stringResource(label),
                    KitConfirmKind.Stop,
                    body = stringResource(body),
                )
            },
        onConfirm = actions::confirm,
        onCancel = actions::dismiss,
    )
}
