package io.github.eslamasabry.oc.feature.agenttools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.AgentFeatureKind
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.engine.integrations.usage.PaseoUsageStatus
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsActions
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsUi
import io.github.eslamasabry.oc.feature.agenttools.presentation.hasRows
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
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
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.kitPageListState
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitContentRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
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
    val gutter = LocalKitTokens.current.gutter.dp
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
        edges = KitScreenEdges.Bleed,
        loading = state.loading && state.hasRows(), // quiet: kept rows stay, a thin line says they update
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = Space.S3.dp, bottom = Space.S6.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            state = kitPageListState(),
        ) {
            if (!state.connected) {
                item(key = "unavailable") {
                    KitStateView(
                        kind = KitStateKind.Calm,
                        modifier = Modifier.padding(horizontal = gutter),
                        title = stringResource(Res.string.agentToolsUnavailable),
                        body = stringResource(Res.string.agentToolsUnavailableBody),
                        primary = KitAction(stringResource(Res.string.agentToolsRefresh), actions::refresh),
                    )
                }
            } else {
                if (state.directory.isNotEmpty()) {
                    item(key = "directory") { ProjectFolderHeader(state.directory) }
                }
                if (state.loading && !state.hasRows()) {
                    item(key = "loading") { KitSkeletonRows(count = 3) }
                }
                state.failure?.let { failure ->
                    item(key = "failure") {
                        KitErrorNotice(
                            message = stringResource(Res.string.agentToolsFailed),
                            modifier = Modifier.padding(horizontal = gutter),
                            failure = failure,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            retry = KitLineAction(stringResource(Res.string.agentToolsRefresh), actions::refresh),
                        )
                    }
                }
                if (state.uncertain) {
                    item(key = "uncertain") {
                        KitNotice(
                            stringResource(Res.string.agentToolsUncertain),
                            modifier = Modifier.padding(horizontal = gutter),
                        )
                    }
                }
                if (state.terminal.supported) {
                    item(key = "terminals") { TerminalsSection(state, locked, working, actions) }
                }
                if (state.canOptions && (!state.loading || state.hasRows())) {
                    if (state.models.isNotEmpty()) {
                        item(key = "models") { ModelsSection(state, locked, working, actions) }
                    }
                    item(key = "options") { OptionsSection(state, locked, disabled, actions) }
                }
                if (state.monitor != null) {
                    item(key = "usage-monitor") { UsageMonitorSection(state, working, actions) }
                }
                item(key = "maintenance") { MaintenanceSection(state, locked, disabled, working, actions) }
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

@Composable
private fun ProjectFolderHeader(directory: String) {
    val gutter = LocalKitTokens.current.gutter.dp
    val folder = plainFolderName(directory)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = gutter),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        KitText(folder, role = TypeRole.Headline)
        if (folder != directory.trim()) {
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                notes = listOf("${stringResource(Res.string.workspaceContextFolder)}: $directory"),
            )
        }
    }
}

@Composable
private fun TerminalsSection(
    state: AgentToolsUi,
    locked: Boolean,
    working: String,
    actions: AgentToolsActions,
) {
    val gutter = LocalKitTokens.current.gutter.dp
    val terminalEnabled =
        if (state.terminal.busy || locked) KitEnabled.Disabled(working) else KitEnabled.Enabled
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitSectionLabel(stringResource(Res.string.agentToolsTerminalsSection))
        AgentTerminalRows(state.terminal, actions)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            KitButton(
                stringResource(Res.string.agentToolsNewTerminal),
                actions::askNewTerminal,
                Modifier.fillMaxWidth(),
                role = KitButtonRole.Primary,
                enabled = terminalEnabled,
            )
            KitButton(
                stringResource(Res.string.agentToolsReadTerminals),
                actions::listTerminals,
                Modifier.fillMaxWidth(),
                role = KitButtonRole.Secondary,
                enabled = terminalEnabled,
            )
        }
    }
}

@Composable
private fun ModelsSection(
    state: AgentToolsUi,
    locked: Boolean,
    working: String,
    actions: AgentToolsActions,
) {
    val reason =
        if (state.uncertain) stringResource(Res.string.agentToolsUncertain) else working
    KitRowGroup(label = stringResource(Res.string.agentToolsModel), section = true) {
        for (model in state.models) KitRow(
            title = model.label,
            selected = model.selected,
            enabled = !locked,
            disabledReason = reason,
            onClick = { actions.setModel(model.id) },
        )
    }
}

@Composable
private fun OptionsSection(
    state: AgentToolsUi,
    locked: Boolean,
    disabled: KitEnabled,
    actions: AgentToolsActions,
) {
    val gutter = LocalKitTokens.current.gutter.dp
    KitRowGroup(label = stringResource(Res.string.agentToolsOptions), section = true) {
        if (state.features.isEmpty()) {
            KitText(
                stringResource(Res.string.agentToolsNoOptions),
                Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.S2.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        for (feature in state.features) when (feature.kind) {
            AgentFeatureKind.Toggle ->
                KitSwitchRow(
                    title = feature.label,
                    supporting = feature.description,
                    checked = feature.on,
                    enabled = disabled,
                    onCheckedChange = { actions.setToggle(feature.id, it) },
                )
            AgentFeatureKind.Choice ->
                KitContentRow(title = feature.label, supporting = feature.description) {
                    KitChipWrap {
                        for (option in feature.options) KitChip(
                            label = option.label,
                            kind = if (locked) KitChipKind.Plain else KitChipKind.Action,
                            tone =
                                if (option.id == feature.selected) KitChipTone.Active
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

@Composable
private fun UsageMonitorSection(state: AgentToolsUi, working: String, actions: AgentToolsActions) {
    val monitor = state.monitor ?: return
    val gutter = LocalKitTokens.current.gutter.dp
    val details =
        listOfNotNull(
            state.monitorLastRead?.let { stringResource(Res.string.agentToolsMonitorLastRead, it) },
            if (monitor.enabled)
                stringResource(Res.string.agentToolsMonitorCount, monitor.providerCount)
            else null,
        ).joinToString(" · ").ifEmpty { null }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitRowGroup(label = stringResource(Res.string.agentToolsUsageMonitorSection), section = true) {
            KitSwitchRow(
                title = stringResource(Res.string.agentToolsUsageMonitor),
                supporting = stringResource(Res.string.agentToolsUsageMonitorBody),
                checked = monitor.enabled,
                enabled = if (state.monitorBusy) KitEnabled.Disabled(working) else KitEnabled.Enabled,
                onCheckedChange = actions::setUsageMonitor,
            )
            KitRow(
                title = monitorStatusLabel(monitor.status),
                supporting = details,
                supportingMaxLines = 3,
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
            KitButton(
                stringResource(Res.string.agentToolsMonitorRefresh),
                actions::refreshUsageMonitor,
                Modifier.fillMaxWidth(),
                role = KitButtonRole.Secondary,
                enabled =
                    if (monitor.enabled && !state.monitorBusy) KitEnabled.Enabled
                    else KitEnabled.Disabled(stringResource(Res.string.agentToolsMonitorEnableFirst)),
            )
        }
    }
}

@Composable
private fun monitorStatusLabel(status: PaseoUsageStatus): String =
    stringResource(
        when (status) {
            PaseoUsageStatus.Off -> Res.string.agentToolsMonitorOff
            PaseoUsageStatus.Paused -> Res.string.agentToolsMonitorPaused
            PaseoUsageStatus.Waiting -> Res.string.agentToolsMonitorWaiting
            PaseoUsageStatus.HostStopped -> Res.string.agentToolsMonitorStopped
            PaseoUsageStatus.Checking -> Res.string.agentToolsMonitorChecking
            PaseoUsageStatus.Current -> Res.string.agentToolsMonitorCurrent
            PaseoUsageStatus.Unavailable -> Res.string.agentToolsMonitorUnavailable
        }
    )

@Composable
private fun MaintenanceSection(
    state: AgentToolsUi,
    locked: Boolean,
    disabled: KitEnabled,
    working: String,
    actions: AgentToolsActions,
) {
    val gutter = LocalKitTokens.current.gutter.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        KitSectionLabel(
            stringResource(Res.string.agentToolsMaintenanceSection),
            gapBefore = LocalKitTokens.current.sectionGap.dp,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = gutter),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (state.canImport) {
                KitButton(
                    stringResource(Res.string.agentToolsReadImports),
                    actions::readImports,
                    Modifier.fillMaxWidth(),
                    role = KitButtonRole.Secondary,
                    enabled = disabled,
                )
            }
            if (state.canUpdate) {
                KitButton(
                    stringResource(Res.string.agentToolsUpdate),
                    actions::requestUpdate,
                    Modifier.fillMaxWidth(),
                    role = KitButtonRole.Secondary,
                    enabled = disabled,
                )
            }
            KitButton(
                stringResource(Res.string.agentToolsRefresh),
                actions::refresh,
                Modifier.fillMaxWidth(),
                role = KitButtonRole.Secondary,
                working = state.busy || state.loading,
            )
        }
        if (state.canImport && state.importsLoaded) {
            ImportsGroup(state, locked, working, actions)
        }
        if (state.canUpdate) {
            UpdateNotices(state)
        }
    }
}

@Composable
private fun ImportsGroup(
    state: AgentToolsUi,
    locked: Boolean,
    working: String,
    actions: AgentToolsActions,
) {
    val gutter = LocalKitTokens.current.gutter.dp
    KitRowGroup(label = stringResource(Res.string.agentToolsImportTitle)) {
        if (state.imports.isEmpty()) {
            KitText(
                stringResource(Res.string.agentToolsNoImports),
                Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.S2.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        if (state.alreadyImported > 0) {
            KitText(
                stringResource(Res.string.agentToolsAlreadyImported, state.alreadyImported),
                Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.S2.dp),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
        }
        for (conversation in state.imports) KitRow(
            title =
                conversation.title
                    ?: conversation.firstPrompt
                    ?: stringResource(Res.string.agentToolsUntitled),
            trailing =
                KitRowTrailing.Action(
                    KitLineAction(
                        stringResource(Res.string.agentToolsImport),
                        { actions.importConversation(conversation.handle, conversation.agentId) },
                        enabled = !locked,
                        disabledReason = working,
                    )
                ),
        )
    }
}

@Composable
private fun UpdateNotices(state: AgentToolsUi) {
    val gutter = LocalKitTokens.current.gutter.dp
    state.updatePhase?.let { phase ->
        KitNotice(
            stringResource(
                when (phase) {
                    HostUpdatePhase.Starting -> Res.string.agentToolsUpdateStarting
                    HostUpdatePhase.Downloading -> Res.string.agentToolsUpdateDownloading
                    HostUpdatePhase.Installing -> Res.string.agentToolsUpdateInstalling
                    HostUpdatePhase.Complete -> Res.string.agentToolsUpdateComplete
                }
            ),
            modifier = Modifier.padding(horizontal = gutter),
        )
    }
    state.updateResult?.let { result ->
        when {
            result.alreadyUpToDate ->
                KitNotice(
                    stringResource(Res.string.agentToolsUpToDate),
                    modifier = Modifier.padding(horizontal = gutter),
                )
            result.success ->
                KitNotice(
                    stringResource(Res.string.agentToolsUpdated, result.newVersion.orEmpty()),
                    modifier = Modifier.padding(horizontal = gutter),
                )
            else ->
                KitErrorNotice(
                    message = stringResource(Res.string.agentToolsUpdateFailed),
                    modifier = Modifier.padding(horizontal = gutter),
                    failure = Failure.Device(result.reason),
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
        }
    }
}
