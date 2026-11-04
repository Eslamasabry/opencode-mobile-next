package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityExplainer
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.ServerCapabilitiesBody(
    ui: ServerCapabilitiesUi,
    actions: SettingsActions,
) {
    var expanded by remember(ui.serverName) { mutableStateOf(false) }
    KitGroupNote(stringResource(Res.string.capabilityScreenIntro, KitBidi.auto(ui.serverName)))
    val missing = ui.features.filter { !it.available }
    if (missing.none { !it.device }) KitNotice(stringResource(Res.string.capabilityAllAvailable))
    for (device in listOf(false, true)) {
        val rows = missing.filter { it.device == device }
        if (rows.isNotEmpty()) {
            KitSectionLabel(
                stringResource(
                    if (device) Res.string.capabilityStateNotDevice
                    else Res.string.capabilityStateNotServer
                )
            )
            rows.forEach { feature -> CapabilityRow(feature) }
        }
    }
    if (missing.any { !it.device })
        KitCapabilityExplainer(
            "server.any",
            serverName = ui.serverName,
            source = "server-capabilities",
        )
    val available = ui.features.filter { it.available }
    if (available.isNotEmpty()) {
        KitRow(
            title =
                pluralStringResource(
                    Res.plurals.capabilityAvailableCount,
                    available.size,
                    available.size,
                ),
            supporting = stringResource(Res.string.capabilityAvailableCountDetail),
            onClick = { expanded = !expanded },
        )
        if (expanded) available.forEach { CapabilityRow(it) }
    }
}

@Composable
private fun CapabilityRow(feature: ServerFeatureUi) {
    val words = featureWords(feature.id)
    KitRow(
        title = words.first,
        supportingMaxLines = 8,
        supporting =
            words.second +
                "\n" +
                stringResource(
                    if (feature.available) Res.string.capabilityStateHere
                    else if (feature.device) Res.string.capabilityStateNotDevice
                    else Res.string.capabilityStateNotServer
                ),
    )
}

@Composable
private fun featureWords(id: ServerFeatureId): Pair<String, String> =
    when (id) {
        ServerFeatureId.Files ->
            stringResource(Res.string.capabilityFiles) to
                stringResource(Res.string.capabilityFilesDetail)
        ServerFeatureId.Changes ->
            stringResource(Res.string.capabilityChanges) to
                stringResource(Res.string.capabilityChangesDetail)
        ServerFeatureId.Terminal ->
            stringResource(Res.string.capabilityTerminal) to
                stringResource(Res.string.capabilityTerminalDetail)
        ServerFeatureId.Shell ->
            stringResource(Res.string.capabilityShell) to
                stringResource(Res.string.capabilityShellDetail)
        ServerFeatureId.DevServices ->
            stringResource(Res.string.capabilityDevServices) to
                stringResource(Res.string.capabilityDevServicesDetail)
        ServerFeatureId.Projects ->
            stringResource(Res.string.capabilityProjects) to
                stringResource(Res.string.capabilityProjectsDetail)
        ServerFeatureId.Worktrees ->
            stringResource(Res.string.capabilityWorktrees) to
                stringResource(Res.string.capabilityWorktreesDetail)
        ServerFeatureId.CloudEnvironments ->
            stringResource(Res.string.capabilityCloud) to
                stringResource(Res.string.capabilityCloudDetail)
        ServerFeatureId.Attachments ->
            stringResource(Res.string.capabilityAttachments) to
                stringResource(Res.string.capabilityAttachmentsDetail)
        ServerFeatureId.WebSearch ->
            stringResource(Res.string.capabilityWebSearch) to
                stringResource(Res.string.capabilityWebSearchDetail)
        ServerFeatureId.Subagents ->
            stringResource(Res.string.capabilitySubagents) to
                stringResource(Res.string.capabilitySubagentsDetail)
        ServerFeatureId.Compact ->
            stringResource(Res.string.capabilityCompact) to
                stringResource(Res.string.capabilityCompactDetail)
        ServerFeatureId.Share ->
            stringResource(Res.string.capabilityShare) to
                stringResource(Res.string.capabilityShareDetail)
        ServerFeatureId.Fork ->
            stringResource(Res.string.capabilityFork) to
                stringResource(Res.string.capabilityForkDetail)
        ServerFeatureId.Revert ->
            stringResource(Res.string.capabilityRevert) to
                stringResource(Res.string.capabilityRevertDetail)
        ServerFeatureId.Archive ->
            stringResource(Res.string.capabilityArchive) to
                stringResource(Res.string.capabilityArchiveDetail)
        ServerFeatureId.DeleteMessage ->
            stringResource(Res.string.capabilityDeleteMessage) to
                stringResource(Res.string.capabilityDeleteMessageDetail)
        ServerFeatureId.Todos ->
            stringResource(Res.string.capabilityTodos) to
                stringResource(Res.string.capabilityTodosDetail)
        ServerFeatureId.Notes ->
            stringResource(Res.string.capabilityNotes) to
                stringResource(Res.string.capabilityNotesDetail)
        ServerFeatureId.ImportExport ->
            stringResource(Res.string.capabilityImportExport) to
                stringResource(Res.string.capabilityImportExportDetail)
        ServerFeatureId.AllConversations ->
            stringResource(Res.string.capabilitySearchAll) to
                stringResource(Res.string.capabilitySearchAllDetail)
        ServerFeatureId.AlwaysAllow ->
            stringResource(Res.string.capabilityAlwaysAllow) to
                stringResource(Res.string.capabilityAlwaysAllowDetail)
        ServerFeatureId.SavedPermissions ->
            stringResource(Res.string.capabilitySavedPermissions) to
                stringResource(Res.string.capabilitySavedPermissionsDetail)
        ServerFeatureId.Approvals ->
            stringResource(Res.string.capabilityApprovals) to
                stringResource(Res.string.capabilityApprovalsDetail)
        ServerFeatureId.SendLater ->
            stringResource(Res.string.capabilityOfflineQueue) to
                stringResource(Res.string.capabilityOfflineQueueDetail)
        ServerFeatureId.SendWhileWorking ->
            stringResource(Res.string.capabilitySteer) to
                stringResource(Res.string.capabilitySteerDetail)
        ServerFeatureId.ContinueOnComputer ->
            stringResource(Res.string.capabilityContinueOnComputer) to
                stringResource(Res.string.capabilityContinueOnComputerDetail)
        ServerFeatureId.Models ->
            stringResource(Res.string.capabilityModels) to
                stringResource(Res.string.capabilityModelsDetail)
        ServerFeatureId.Skills ->
            stringResource(Res.string.capabilitySkills) to
                stringResource(Res.string.capabilitySkillsDetail)
        ServerFeatureId.Mcp ->
            stringResource(Res.string.capabilityMcp) to
                stringResource(Res.string.capabilityMcpDetail)
        ServerFeatureId.McpAdd ->
            stringResource(Res.string.capabilityMcpAdd) to
                stringResource(Res.string.capabilityMcpAddDetail)
        ServerFeatureId.Plugins ->
            stringResource(Res.string.capabilityPlugins) to
                stringResource(Res.string.capabilityPluginsDetail)
        ServerFeatureId.Usage ->
            stringResource(Res.string.capabilityUsage) to
                stringResource(Res.string.capabilityUsageDetail)
        ServerFeatureId.ServerUpdates ->
            stringResource(Res.string.capabilityServerUpdates) to
                stringResource(Res.string.capabilityServerUpdatesDetail)
        ServerFeatureId.BackgroundNotifications ->
            stringResource(Res.string.capabilityBackgroundNotifications) to
                stringResource(Res.string.capabilityBackgroundNotificationsDetail)
        ServerFeatureId.OnThisPhone ->
            stringResource(Res.string.capabilityOnThisPhone) to
                stringResource(Res.string.capabilityOnThisPhoneDetail)
        ServerFeatureId.Voice ->
            stringResource(Res.string.capabilityVoice) to
                stringResource(Res.string.capabilityVoiceDetail)
    }
