package io.github.eslamasabry.oc.ui.kit.capability

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCapAgentA2aEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapAgentA2aOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapAgentA2aTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapAgentA2aWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapClaudeLocalEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapClaudeLocalOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapClaudeLocalTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapClaudeLocalWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagDevelopmentServicesTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagDevelopmentServicesWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagFileBrowsingTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagFileBrowsingTerminalWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagPromptAttachmentsTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagPromptAttachmentsWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagRemoteUpgradeTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagRemoteUpgradeWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagServerCatalogTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagServerCatalogWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagSessionDiffTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagSessionDiffWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagStagedRevertSessionNotesTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagStagedRevertSessionNotesWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagTerminalWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagToolInventoryTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagToolInventoryWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagUsageStatisticsTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagUsageStatisticsWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapMcpAnyEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapMcpAnyOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapMcpAnyTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapMcpAnyWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapModelAuthEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapModelAuthOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapModelAuthTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapModelAuthWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapNetworkTailscaleEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapNetworkTailscaleOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapNetworkTailscaleTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapNetworkTailscaleWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapPermBatteryEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapPermBatteryOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapPermBatteryTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapPermBatteryWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapPermCameraEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapPermCameraOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapPermCameraTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapPermCameraWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapPermMicEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapPermMicOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapPermMicTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapPermMicWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapPermNotificationsEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapPermNotificationsOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapPermNotificationsTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapPermNotificationsWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneAnyTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneAnyWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneBuiltinEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneBuiltinOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneBuiltinTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneBuiltinWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneTermuxEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneTermuxOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneTermuxTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapPhoneTermuxWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectGitEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectGitOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectGitTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectGitWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectOpenEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectOpenOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectOpenTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapProjectOpenWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapQuotaCollectorEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapQuotaCollectorOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapQuotaCollectorTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapQuotaCollectorWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapServerAnyEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapServerAnyOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapServerAnyTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapServerAnyWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapServerCodexEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapServerCodexOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapServerCodexTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapServerCodexWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapServerOc2Enable
import io.github.eslamasabry.oc.ui.words.res.kitCapServerOc2Offer
import io.github.eslamasabry.oc.ui.words.res.kitCapServerOc2Title
import io.github.eslamasabry.oc.ui.words.res.kitCapServerOc2Why
import io.github.eslamasabry.oc.ui.words.res.kitCapServerPaseoEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapServerPaseoOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapServerPaseoTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapServerPaseoWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamControlEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamControlOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamControlTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamControlWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamOnEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamOnOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamOnTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamOnWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamPhoneTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapTeamPhoneWhy
import io.github.eslamasabry.oc.ui.words.res.kitCapVoiceModelEnable
import io.github.eslamasabry.oc.ui.words.res.kitCapVoiceModelOffer
import io.github.eslamasabry.oc.ui.words.res.kitCapVoiceModelTitle
import io.github.eslamasabry.oc.ui.words.res.kitCapVoiceModelWhy
import io.github.eslamasabry.oc.ui.words.res.kitHostCodex
import io.github.eslamasabry.oc.ui.words.res.kitHostDemo
import io.github.eslamasabry.oc.ui.words.res.kitHostOpenCode2
import io.github.eslamasabry.oc.ui.words.res.kitHostPaseo
import io.github.eslamasabry.oc.ui.words.res.kitHostTermux
import io.github.eslamasabry.oc.ui.words.res.kitHostThisPhone
import org.jetbrains.compose.resources.stringResource

public data class KitCapabilityWords(
    val title: String,
    val why: String,
    val enable: String? = null,
    val offer: String? = null,
)

/** Every frozen matrix ID has English and Arabic words in the native string pool. */
@Composable
public fun kitCapabilityWords(id: String): KitCapabilityWords? =
    when (id) {
        "server.any" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapServerAnyTitle),
                stringResource(Res.string.kitCapServerAnyWhy),
                stringResource(Res.string.kitCapServerAnyEnable),
                stringResource(Res.string.kitCapServerAnyOffer),
            )
        "server.oc2" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapServerOc2Title),
                stringResource(Res.string.kitCapServerOc2Why),
                stringResource(Res.string.kitCapServerOc2Enable),
                stringResource(Res.string.kitCapServerOc2Offer),
            )
        "server.codex" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapServerCodexTitle),
                stringResource(Res.string.kitCapServerCodexWhy),
                stringResource(Res.string.kitCapServerCodexEnable),
                stringResource(Res.string.kitCapServerCodexOffer),
            )
        "server.paseo" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapServerPaseoTitle),
                stringResource(Res.string.kitCapServerPaseoWhy),
                stringResource(Res.string.kitCapServerPaseoEnable),
                stringResource(Res.string.kitCapServerPaseoOffer),
            )
        "phone.builtin" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapPhoneBuiltinTitle),
                stringResource(Res.string.kitCapPhoneBuiltinWhy),
                stringResource(Res.string.kitCapPhoneBuiltinEnable),
                stringResource(Res.string.kitCapPhoneBuiltinOffer),
            )
        "phone.termux" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapPhoneTermuxTitle),
                stringResource(Res.string.kitCapPhoneTermuxWhy),
                stringResource(Res.string.kitCapPhoneTermuxEnable),
                stringResource(Res.string.kitCapPhoneTermuxOffer),
            )
        "phone.any" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapPhoneAnyTitle),
                stringResource(Res.string.kitCapPhoneAnyWhy),
                null,
                null,
            )
        "model.auth" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapModelAuthTitle),
                stringResource(Res.string.kitCapModelAuthWhy),
                stringResource(Res.string.kitCapModelAuthEnable),
                stringResource(Res.string.kitCapModelAuthOffer),
            )
        "team.on" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapTeamOnTitle),
                stringResource(Res.string.kitCapTeamOnWhy),
                stringResource(Res.string.kitCapTeamOnEnable),
                stringResource(Res.string.kitCapTeamOnOffer),
            )
        "team.phone" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapTeamPhoneTitle),
                stringResource(Res.string.kitCapTeamPhoneWhy),
                null,
                null,
            )
        "team.control" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapTeamControlTitle),
                stringResource(Res.string.kitCapTeamControlWhy),
                stringResource(Res.string.kitCapTeamControlEnable),
                stringResource(Res.string.kitCapTeamControlOffer),
            )
        "claude.local" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapClaudeLocalTitle),
                stringResource(Res.string.kitCapClaudeLocalWhy),
                stringResource(Res.string.kitCapClaudeLocalEnable),
                stringResource(Res.string.kitCapClaudeLocalOffer),
            )
        "voice.model" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapVoiceModelTitle),
                stringResource(Res.string.kitCapVoiceModelWhy),
                stringResource(Res.string.kitCapVoiceModelEnable),
                stringResource(Res.string.kitCapVoiceModelOffer),
            )
        "mcp.any" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapMcpAnyTitle),
                stringResource(Res.string.kitCapMcpAnyWhy),
                stringResource(Res.string.kitCapMcpAnyEnable),
                stringResource(Res.string.kitCapMcpAnyOffer),
            )
        "project.open" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapProjectOpenTitle),
                stringResource(Res.string.kitCapProjectOpenWhy),
                stringResource(Res.string.kitCapProjectOpenEnable),
                stringResource(Res.string.kitCapProjectOpenOffer),
            )
        "project.git" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapProjectGitTitle),
                stringResource(Res.string.kitCapProjectGitWhy),
                stringResource(Res.string.kitCapProjectGitEnable),
                stringResource(Res.string.kitCapProjectGitOffer),
            )
        "perm.notifications" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapPermNotificationsTitle),
                stringResource(Res.string.kitCapPermNotificationsWhy),
                stringResource(Res.string.kitCapPermNotificationsEnable),
                stringResource(Res.string.kitCapPermNotificationsOffer),
            )
        "perm.battery" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapPermBatteryTitle),
                stringResource(Res.string.kitCapPermBatteryWhy),
                stringResource(Res.string.kitCapPermBatteryEnable),
                stringResource(Res.string.kitCapPermBatteryOffer),
            )
        "perm.camera" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapPermCameraTitle),
                stringResource(Res.string.kitCapPermCameraWhy),
                stringResource(Res.string.kitCapPermCameraEnable),
                stringResource(Res.string.kitCapPermCameraOffer),
            )
        "perm.mic" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapPermMicTitle),
                stringResource(Res.string.kitCapPermMicWhy),
                stringResource(Res.string.kitCapPermMicEnable),
                stringResource(Res.string.kitCapPermMicOffer),
            )
        "network.tailscale" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapNetworkTailscaleTitle),
                stringResource(Res.string.kitCapNetworkTailscaleWhy),
                stringResource(Res.string.kitCapNetworkTailscaleEnable),
                stringResource(Res.string.kitCapNetworkTailscaleOffer),
            )
        "quota.collector" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapQuotaCollectorTitle),
                stringResource(Res.string.kitCapQuotaCollectorWhy),
                stringResource(Res.string.kitCapQuotaCollectorEnable),
                stringResource(Res.string.kitCapQuotaCollectorOffer),
            )
        "agent.a2a" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapAgentA2aTitle),
                stringResource(Res.string.kitCapAgentA2aWhy),
                stringResource(Res.string.kitCapAgentA2aEnable),
                stringResource(Res.string.kitCapAgentA2aOffer),
            )
        "flag:fileBrowsing+terminal" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagFileBrowsingTerminalTitle),
                stringResource(Res.string.kitCapFlagFileBrowsingTerminalWhy),
                null,
                null,
            )
        "flag:terminal" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagTerminalTitle),
                stringResource(Res.string.kitCapFlagTerminalWhy),
                null,
                null,
            )
        "flag:sessionDiff" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagSessionDiffTitle),
                stringResource(Res.string.kitCapFlagSessionDiffWhy),
                null,
                null,
            )
        "flag:serverCatalog" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagServerCatalogTitle),
                stringResource(Res.string.kitCapFlagServerCatalogWhy),
                null,
                null,
            )
        "flag:toolInventory" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagToolInventoryTitle),
                stringResource(Res.string.kitCapFlagToolInventoryWhy),
                null,
                null,
            )
        "flag:usageStatistics" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagUsageStatisticsTitle),
                stringResource(Res.string.kitCapFlagUsageStatisticsWhy),
                null,
                null,
            )
        "flag:stagedRevert+sessionNotes" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagStagedRevertSessionNotesTitle),
                stringResource(Res.string.kitCapFlagStagedRevertSessionNotesWhy),
                null,
                null,
            )
        "flag:worktreeCreate+sessionShare+managedWorkspaces" ->
            KitCapabilityWords(
                stringResource(
                    Res.string.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle
                ),
                stringResource(Res.string.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy),
                null,
                null,
            )
        "flag:developmentServices" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagDevelopmentServicesTitle),
                stringResource(Res.string.kitCapFlagDevelopmentServicesWhy),
                null,
                null,
            )
        "flag:remoteUpgrade" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagRemoteUpgradeTitle),
                stringResource(Res.string.kitCapFlagRemoteUpgradeWhy),
                null,
                null,
            )
        "flag:promptAttachments" ->
            KitCapabilityWords(
                stringResource(Res.string.kitCapFlagPromptAttachmentsTitle),
                stringResource(Res.string.kitCapFlagPromptAttachmentsWhy),
                null,
                null,
            )
        else -> null
    }

@Composable
public fun kitCapabilityHostWord(host: KitHost): String =
    stringResource(
        when (host) {
            KitHost.ThisPhone -> Res.string.kitHostThisPhone
            KitHost.Termux -> Res.string.kitHostTermux
            KitHost.OpenCode2 -> Res.string.kitHostOpenCode2
            KitHost.Codex -> Res.string.kitHostCodex
            KitHost.Paseo -> Res.string.kitHostPaseo
            KitHost.Demo -> Res.string.kitHostDemo
        }
    )
