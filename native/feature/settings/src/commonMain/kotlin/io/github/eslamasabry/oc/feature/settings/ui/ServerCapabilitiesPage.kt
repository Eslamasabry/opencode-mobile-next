package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.CapabilitiesUi
import io.github.eslamasabry.oc.feature.settings.presentation.FeatureRowUi
import io.github.eslamasabry.oc.feature.settings.presentation.FeatureSource
import io.github.eslamasabry.oc.feature.settings.presentation.ServerFeature
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.capabilityAddServer
import io.github.eslamasabry.oc.ui.words.res.capabilityAddServerDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityAllAvailable
import io.github.eslamasabry.oc.ui.words.res.capabilityAlwaysAllow
import io.github.eslamasabry.oc.ui.words.res.capabilityAlwaysAllowDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityApprovals
import io.github.eslamasabry.oc.ui.words.res.capabilityApprovalsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityArchive
import io.github.eslamasabry.oc.ui.words.res.capabilityArchiveDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityAttachments
import io.github.eslamasabry.oc.ui.words.res.capabilityAttachmentsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityAvailableCount
import io.github.eslamasabry.oc.ui.words.res.capabilityAvailableCountDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityBackgroundNotifications
import io.github.eslamasabry.oc.ui.words.res.capabilityBackgroundNotificationsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityChanges
import io.github.eslamasabry.oc.ui.words.res.capabilityChangesDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityCloud
import io.github.eslamasabry.oc.ui.words.res.capabilityCloudDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityCompact
import io.github.eslamasabry.oc.ui.words.res.capabilityCompactDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityContinueOnComputer
import io.github.eslamasabry.oc.ui.words.res.capabilityContinueOnComputerDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityDeleteMessage
import io.github.eslamasabry.oc.ui.words.res.capabilityDeleteMessageDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityDevServices
import io.github.eslamasabry.oc.ui.words.res.capabilityDevServicesDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityFiles
import io.github.eslamasabry.oc.ui.words.res.capabilityFilesDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityFork
import io.github.eslamasabry.oc.ui.words.res.capabilityForkDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityImportExport
import io.github.eslamasabry.oc.ui.words.res.capabilityImportExportDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityMcp
import io.github.eslamasabry.oc.ui.words.res.capabilityMcpAdd
import io.github.eslamasabry.oc.ui.words.res.capabilityMcpAddDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityMcpDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityModels
import io.github.eslamasabry.oc.ui.words.res.capabilityModelsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityNotes
import io.github.eslamasabry.oc.ui.words.res.capabilityNotesDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityOfflineQueue
import io.github.eslamasabry.oc.ui.words.res.capabilityOfflineQueueDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityOnThisPhone
import io.github.eslamasabry.oc.ui.words.res.capabilityOnThisPhoneDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityPlugins
import io.github.eslamasabry.oc.ui.words.res.capabilityPluginsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityProjects
import io.github.eslamasabry.oc.ui.words.res.capabilityProjectsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityRevert
import io.github.eslamasabry.oc.ui.words.res.capabilityRevertDetail
import io.github.eslamasabry.oc.ui.words.res.capabilitySavedPermissions
import io.github.eslamasabry.oc.ui.words.res.capabilitySavedPermissionsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityScreenIntro
import io.github.eslamasabry.oc.ui.words.res.capabilityScreenIntroWithGaps
import io.github.eslamasabry.oc.ui.words.res.capabilitySearchAll
import io.github.eslamasabry.oc.ui.words.res.capabilitySearchAllDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityServerUpdates
import io.github.eslamasabry.oc.ui.words.res.capabilityServerUpdatesDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityShare
import io.github.eslamasabry.oc.ui.words.res.capabilityShareDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityShell
import io.github.eslamasabry.oc.ui.words.res.capabilityShellDetail
import io.github.eslamasabry.oc.ui.words.res.capabilitySkills
import io.github.eslamasabry.oc.ui.words.res.capabilitySkillsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityStateHere
import io.github.eslamasabry.oc.ui.words.res.capabilityStateNotDevice
import io.github.eslamasabry.oc.ui.words.res.capabilityStateNotServer
import io.github.eslamasabry.oc.ui.words.res.capabilitySteer
import io.github.eslamasabry.oc.ui.words.res.capabilitySteerDetail
import io.github.eslamasabry.oc.ui.words.res.capabilitySubagents
import io.github.eslamasabry.oc.ui.words.res.capabilitySubagentsDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityTerminal
import io.github.eslamasabry.oc.ui.words.res.capabilityTerminalDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityTodos
import io.github.eslamasabry.oc.ui.words.res.capabilityTodosDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityUsage
import io.github.eslamasabry.oc.ui.words.res.capabilityUsageDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityVoice
import io.github.eslamasabry.oc.ui.words.res.capabilityVoiceDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityWebSearch
import io.github.eslamasabry.oc.ui.words.res.capabilityWebSearchDetail
import io.github.eslamasabry.oc.ui.words.res.capabilityWorktrees
import io.github.eslamasabry.oc.ui.words.res.capabilityWorktreesDetail
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Help › Available on this server (Flutter `ServerCapabilitiesScreen`), kit parts only. One list ordered by
 * urgency: the focused feature, what this server lacks, what this device lacks, "Add a server that has these" where
 * the app can open that, then one row that unfolds everything that works here. Read-only rows: nothing is a control.
 *
 * Unlike Flutter, a row names no other servers ("Works on …"): the native app has no capability matrix, and the intro
 * already says where missing features work.
 */
@Composable
internal fun ColumnScope.CapabilitiesBody(ui: CapabilitiesUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val name = ui.serverName ?: groupWords(SettingsGroupId.Server)
    KitText(
        stringResource(if (ui.serverGaps) Res.string.capabilityScreenIntroWithGaps else Res.string.capabilityScreenIntro, name),
        gutter.testTag("server-capabilities-intro"),
        role = TypeRole.Secondary,
        tone = TextTone.Secondary,
    )
    if (ui.allAvailable) {
        KitNotice(
            stringResource(Res.string.capabilityAllAvailable),
            gutter.testTag("server-capabilities-all"),
            tone = StatusTone.Ok,
            liveRegion = false,
        )
    }
    var unfolded by rememberSaveable { mutableStateOf(false) }
    KitRowGroup(Modifier.testTag("capabilities-list")) {
        (ui.focused + ui.missingOnServer + ui.missingOnDevice).forEach { FeatureRow(it) }
        if (ui.canAddServer) {
            KitRow(
                title = stringResource(Res.string.capabilityAddServer),
                modifier = Modifier.testTag("capabilities-add-server"),
                supporting = stringResource(Res.string.capabilityAddServerDetail),
                supportingMaxLines = 2,
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.onOutside(SettingsOutside.Servers) },
            )
        }
        if (ui.available.isNotEmpty()) {
            KitExpandRow(
                title = pluralStringResource(Res.plurals.capabilityAvailableCount, ui.available.size, ui.available.size),
                expanded = unfolded,
                modifier = Modifier.testTag("capabilities-available"),
                supporting = stringResource(Res.string.capabilityAvailableCountDetail),
                onExpandedChange = { unfolded = it },
            ) {
                ui.available.forEach { FeatureRow(it) }
            }
        }
    }
}

@Composable
private fun FeatureRow(row: FeatureRowUi) {
    val state =
        stringResource(
            when {
                row.present -> Res.string.capabilityStateHere
                row.feature.source == FeatureSource.Device -> Res.string.capabilityStateNotDevice
                else -> Res.string.capabilityStateNotServer
            },
        )
    KitRow(
        title = featureTitle(row.feature),
        modifier = Modifier.testTag("capability-${row.slug}-${row.feature.id}"),
        supporting = "$state · ${featureDetail(row.feature)}",
        supportingMaxLines = 5,
    )
}

@Composable
internal fun featureTitle(feature: ServerFeature): String = stringResource(featureWords(feature).first)

@Composable
internal fun featureDetail(feature: ServerFeature): String = stringResource(featureWords(feature).second)

private fun featureWords(feature: ServerFeature): Pair<StringResource, StringResource> =
    when (feature) {
        ServerFeature.Files -> Res.string.capabilityFiles to Res.string.capabilityFilesDetail
        ServerFeature.Changes -> Res.string.capabilityChanges to Res.string.capabilityChangesDetail
        ServerFeature.Terminal -> Res.string.capabilityTerminal to Res.string.capabilityTerminalDetail
        ServerFeature.Shell -> Res.string.capabilityShell to Res.string.capabilityShellDetail
        ServerFeature.DevServices -> Res.string.capabilityDevServices to Res.string.capabilityDevServicesDetail
        ServerFeature.Projects -> Res.string.capabilityProjects to Res.string.capabilityProjectsDetail
        ServerFeature.Worktrees -> Res.string.capabilityWorktrees to Res.string.capabilityWorktreesDetail
        ServerFeature.CloudEnvironments -> Res.string.capabilityCloud to Res.string.capabilityCloudDetail
        ServerFeature.Attachments -> Res.string.capabilityAttachments to Res.string.capabilityAttachmentsDetail
        ServerFeature.WebSearch -> Res.string.capabilityWebSearch to Res.string.capabilityWebSearchDetail
        ServerFeature.Subagents -> Res.string.capabilitySubagents to Res.string.capabilitySubagentsDetail
        ServerFeature.Compact -> Res.string.capabilityCompact to Res.string.capabilityCompactDetail
        ServerFeature.Share -> Res.string.capabilityShare to Res.string.capabilityShareDetail
        ServerFeature.Fork -> Res.string.capabilityFork to Res.string.capabilityForkDetail
        ServerFeature.Revert -> Res.string.capabilityRevert to Res.string.capabilityRevertDetail
        ServerFeature.Archive -> Res.string.capabilityArchive to Res.string.capabilityArchiveDetail
        ServerFeature.DeleteMessage -> Res.string.capabilityDeleteMessage to Res.string.capabilityDeleteMessageDetail
        ServerFeature.Todos -> Res.string.capabilityTodos to Res.string.capabilityTodosDetail
        ServerFeature.Notes -> Res.string.capabilityNotes to Res.string.capabilityNotesDetail
        ServerFeature.ImportExport -> Res.string.capabilityImportExport to Res.string.capabilityImportExportDetail
        ServerFeature.AllConversations -> Res.string.capabilitySearchAll to Res.string.capabilitySearchAllDetail
        ServerFeature.AlwaysAllow -> Res.string.capabilityAlwaysAllow to Res.string.capabilityAlwaysAllowDetail
        ServerFeature.SavedPermissions -> Res.string.capabilitySavedPermissions to Res.string.capabilitySavedPermissionsDetail
        ServerFeature.Approvals -> Res.string.capabilityApprovals to Res.string.capabilityApprovalsDetail
        ServerFeature.SendLater -> Res.string.capabilityOfflineQueue to Res.string.capabilityOfflineQueueDetail
        ServerFeature.SendWhileWorking -> Res.string.capabilitySteer to Res.string.capabilitySteerDetail
        ServerFeature.ContinueOnComputer ->
            Res.string.capabilityContinueOnComputer to Res.string.capabilityContinueOnComputerDetail
        ServerFeature.Models -> Res.string.capabilityModels to Res.string.capabilityModelsDetail
        ServerFeature.Skills -> Res.string.capabilitySkills to Res.string.capabilitySkillsDetail
        ServerFeature.Mcp -> Res.string.capabilityMcp to Res.string.capabilityMcpDetail
        ServerFeature.McpAdd -> Res.string.capabilityMcpAdd to Res.string.capabilityMcpAddDetail
        ServerFeature.Plugins -> Res.string.capabilityPlugins to Res.string.capabilityPluginsDetail
        ServerFeature.Usage -> Res.string.capabilityUsage to Res.string.capabilityUsageDetail
        ServerFeature.ServerUpdates -> Res.string.capabilityServerUpdates to Res.string.capabilityServerUpdatesDetail
        ServerFeature.BackgroundNotifications ->
            Res.string.capabilityBackgroundNotifications to Res.string.capabilityBackgroundNotificationsDetail
        ServerFeature.OnThisPhone -> Res.string.capabilityOnThisPhone to Res.string.capabilityOnThisPhoneDetail
        ServerFeature.Voice -> Res.string.capabilityVoice to Res.string.capabilityVoiceDetail
    }
