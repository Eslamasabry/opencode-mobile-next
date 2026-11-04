package io.github.eslamasabry.oc.feature.agenttools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.AgentFeatureKind
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsActions
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
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
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Runtime options before or after a first prompt, and explicit maintenance of this connected host.
 */
@Composable
public fun AgentToolsScreen(state: AgentToolsUi, actions: AgentToolsActions) {
    if (state.terminal.liveId != null) {
        AgentTerminalScreen(state.terminal, actions)
        return
    }
    val working = stringResource(Res.string.agentToolsWorking)
    val locked = state.busy || state.loading || state.uncertain
    val disabled =
        if (locked)
            KitEnabled.Disabled(
                if (state.uncertain) stringResource(Res.string.agentToolsUncertain) else working
            )
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.agentToolsTitle),
                subtitle = state.name,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            if (!state.connected)
                item(key = "unavailable") {
                    KitStateView(
                        kind = KitStateKind.Calm,
                        title = stringResource(Res.string.agentToolsUnavailable),
                        body = stringResource(Res.string.agentToolsUnavailableBody),
                        primary =
                            KitAction(
                                stringResource(Res.string.agentToolsRefresh),
                                actions::refresh,
                            ),
                    )
                }
            else {
                if (state.directory.isNotEmpty())
                    item(key = "directory") { KitText(state.directory) }
                if (state.loading) item(key = "loading") { KitSkeletonRows(count = 3) }
                state.failure?.let { failure ->
                    item(key = "failure") {
                        KitErrorNotice(
                            message = stringResource(Res.string.agentToolsFailed),
                            failure = failure,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            retry =
                                KitLineAction(
                                    stringResource(Res.string.agentToolsRefresh),
                                    actions::refresh,
                                ),
                        )
                    }
                }
                if (state.uncertain)
                    item(key = "uncertain") {
                        KitNotice(stringResource(Res.string.agentToolsUncertain))
                    }
                if (state.terminal.supported) {
                    item(key = "terminal-actions") {
                        val terminalEnabled =
                            if (state.terminal.busy || locked) KitEnabled.Disabled(working)
                            else KitEnabled.Enabled
                        KitButton(
                            stringResource(Res.string.agentToolsReadTerminals),
                            actions::listTerminals,
                            role = KitButtonRole.Secondary,
                            enabled = terminalEnabled,
                        )
                        KitButton(
                            stringResource(Res.string.agentToolsNewTerminal),
                            actions::askNewTerminal,
                            role = KitButtonRole.Tertiary,
                            enabled = terminalEnabled,
                        )
                    }
                    item(key = "terminal-list") { AgentTerminalRows(state.terminal, actions) }
                }
                state.monitor?.let { monitor ->
                    item(key = "usage-monitor") {
                        KitSwitchRow(
                            title = stringResource(Res.string.agentToolsUsageMonitor),
                            supporting = stringResource(Res.string.agentToolsUsageMonitorBody),
                            checked = monitor.enabled,
                            enabled =
                                if (state.monitorBusy) KitEnabled.Disabled(working)
                                else KitEnabled.Enabled,
                            onCheckedChange = actions::setUsageMonitor,
                        )
                        KitText(
                            stringResource(
                                when (monitor.status) {
                                    io.github.eslamasabry.oc.engine.integrations.usage
                                        .PaseoUsageStatus
                                        .Off -> Res.string.agentToolsMonitorOff
                                    io.github.eslamasabry.oc.engine.integrations.usage
                                        .PaseoUsageStatus
                                        .Paused -> Res.string.agentToolsMonitorPaused
                                    io.github.eslamasabry.oc.engine.integrations.usage
                                        .PaseoUsageStatus
                                        .Waiting -> Res.string.agentToolsMonitorWaiting
                                    io.github.eslamasabry.oc.engine.integrations.usage
                                        .PaseoUsageStatus
                                        .HostStopped -> Res.string.agentToolsMonitorStopped
                                    io.github.eslamasabry.oc.engine.integrations.usage
                                        .PaseoUsageStatus
                                        .Checking -> Res.string.agentToolsMonitorChecking
                                    io.github.eslamasabry.oc.engine.integrations.usage
                                        .PaseoUsageStatus
                                        .Current -> Res.string.agentToolsMonitorCurrent
                                    io.github.eslamasabry.oc.engine.integrations.usage
                                        .PaseoUsageStatus
                                        .Unavailable -> Res.string.agentToolsMonitorUnavailable
                                }
                            )
                        )
                        state.monitorLastRead?.let {
                            KitText(stringResource(Res.string.agentToolsMonitorLastRead, it))
                        }
                        if (monitor.enabled)
                            KitText(
                                stringResource(
                                    Res.string.agentToolsMonitorCount,
                                    monitor.providerCount,
                                )
                            )
                        KitButton(
                            stringResource(Res.string.agentToolsMonitorRefresh),
                            actions::refreshUsageMonitor,
                            role = KitButtonRole.Tertiary,
                            enabled =
                                if (monitor.enabled && !state.monitorBusy) KitEnabled.Enabled
                                else
                                    KitEnabled.Disabled(
                                        stringResource(Res.string.agentToolsMonitorEnableFirst)
                                    ),
                        )
                    }
                }
                if (state.canOptions && !state.loading) {
                    if (state.models.isNotEmpty())
                        item(key = "models") {
                            KitRowGroup(label = stringResource(Res.string.agentToolsModel)) {
                                for (model in state.models) KitRow(
                                    title = model.label,
                                    selected = model.selected,
                                    enabled = !locked,
                                    disabledReason =
                                        if (state.uncertain)
                                            stringResource(Res.string.agentToolsUncertain)
                                        else working,
                                    onClick = { actions.setModel(model.id) },
                                )
                            }
                        }
                    item(key = "options") {
                        KitRowGroup(label = stringResource(Res.string.agentToolsOptions)) {
                            if (state.features.isEmpty())
                                KitText(stringResource(Res.string.agentToolsNoOptions))
                            for (feature in state.features) when (feature.kind) {
                                AgentFeatureKind.Toggle ->
                                    KitSwitchRow(
                                        title = feature.label,
                                        supporting = feature.description,
                                        checked = feature.on,
                                        enabled = disabled,
                                        onCheckedChange = { actions.setToggle(feature.id, it) },
                                    )
                                AgentFeatureKind.Choice -> {
                                    KitText(feature.label)
                                    feature.description?.let { KitText(it) }
                                    KitChipWrap {
                                        for (option in feature.options) KitChip(
                                            label = option.label,
                                            kind =
                                                if (locked) KitChipKind.Plain
                                                else KitChipKind.Action,
                                            tone =
                                                if (option.id == feature.selected)
                                                    KitChipTone.Active
                                                else KitChipTone.Neutral,
                                            selected = option.id == feature.selected,
                                            onClick =
                                                if (locked) null
                                                else {
                                                    { actions.setChoice(feature.id, option.id) }
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.canImport) {
                    item(key = "read-imports") {
                        KitButton(
                            stringResource(Res.string.agentToolsReadImports),
                            actions::readImports,
                            role = KitButtonRole.Secondary,
                            enabled = disabled,
                        )
                    }
                    if (state.importsLoaded)
                        item(key = "imports") {
                            KitRowGroup(label = stringResource(Res.string.agentToolsImportTitle)) {
                                if (state.imports.isEmpty())
                                    KitText(stringResource(Res.string.agentToolsNoImports))
                                if (state.alreadyImported > 0)
                                    KitText(
                                        stringResource(
                                            Res.string.agentToolsAlreadyImported,
                                            state.alreadyImported,
                                        )
                                    )
                                for (conversation in state.imports) KitRow(
                                    title =
                                        conversation.title
                                            ?: conversation.firstPrompt
                                            ?: stringResource(Res.string.agentToolsUntitled),
                                    trailing =
                                        KitRowTrailing.Action(
                                            KitLineAction(
                                                stringResource(Res.string.agentToolsImport),
                                                {
                                                    actions.importConversation(
                                                        conversation.handle,
                                                        conversation.agentId,
                                                    )
                                                },
                                                enabled = !locked,
                                                disabledReason = working,
                                            )
                                        ),
                                )
                            }
                        }
                }
                if (state.canUpdate) {
                    item(key = "update") {
                        KitButton(
                            stringResource(Res.string.agentToolsUpdate),
                            actions::requestUpdate,
                            role = KitButtonRole.Secondary,
                            enabled = disabled,
                        )
                    }
                    state.updatePhase?.let { phase ->
                        item(key = "update-phase") {
                            KitNotice(
                                stringResource(
                                    when (phase) {
                                        HostUpdatePhase.Starting ->
                                            Res.string.agentToolsUpdateStarting
                                        HostUpdatePhase.Downloading ->
                                            Res.string.agentToolsUpdateDownloading
                                        HostUpdatePhase.Installing ->
                                            Res.string.agentToolsUpdateInstalling
                                        HostUpdatePhase.Complete ->
                                            Res.string.agentToolsUpdateComplete
                                    }
                                )
                            )
                        }
                    }
                    state.updateResult?.let { result ->
                        item(key = "update-result") {
                            when {
                                result.alreadyUpToDate ->
                                    KitNotice(stringResource(Res.string.agentToolsUpToDate))
                                result.success ->
                                    KitNotice(
                                        stringResource(
                                            Res.string.agentToolsUpdated,
                                            result.newVersion.orEmpty(),
                                        )
                                    )
                                else ->
                                    KitErrorNotice(
                                        message = stringResource(Res.string.agentToolsUpdateFailed),
                                        failure = Failure.Device(result.reason),
                                        detailsLabel = stringResource(Res.string.kitDetails),
                                        copyLabel = stringResource(Res.string.kitCopyDetails),
                                    )
                            }
                        }
                    }
                }
                item(key = "refresh") {
                    KitButton(
                        stringResource(Res.string.agentToolsRefresh),
                        actions::refresh,
                        role = KitButtonRole.Tertiary,
                        working = state.busy || state.loading,
                    )
                }
            }
        }
        AgentTerminalDialogs(state.terminal, actions)
        KitConfirm(
            spec =
                if (state.confirmUpdate)
                    KitConfirmSpec(
                        title = stringResource(Res.string.agentToolsUpdateQuestion, state.name),
                        confirmLabel = stringResource(Res.string.agentToolsUpdate),
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.agentToolsUpdateBody),
                    )
                else null,
            onConfirm = actions::confirmUpdate,
            onCancel = actions::cancelUpdate,
        )
    }
}
