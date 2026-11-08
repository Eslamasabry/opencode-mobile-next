package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.settings.presentation.BackgroundState
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFailure
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.a2aTitle
import io.github.eslamasabry.oc.ui.words.res.aboutTitle
import io.github.eslamasabry.oc.ui.words.res.agentsSectionTitle
import io.github.eslamasabry.oc.ui.words.res.discoverTeamAliases
import io.github.eslamasabry.oc.ui.words.res.teamSettingsTitle
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailChecksum
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailInstaller
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailNetwork
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailNoFile
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailPackage
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailSigner
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailStorage
import io.github.eslamasabry.oc.ui.words.res.appUpdateFailVersion
import io.github.eslamasabry.oc.ui.words.res.appUpdatesSearchAliases
import io.github.eslamasabry.oc.ui.words.res.appUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentBlue
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentMint
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentMono
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentPink
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentSilver
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentTeal
import io.github.eslamasabry.oc.ui.words.res.appearanceAccentViolet
import io.github.eslamasabry.oc.ui.words.res.appearanceFineTuneCornersRound
import io.github.eslamasabry.oc.ui.words.res.appearanceFineTuneCornersSharp
import io.github.eslamasabry.oc.ui.words.res.appearanceFineTuneCornersSoft
import io.github.eslamasabry.oc.ui.words.res.appearanceGroupBackground
import io.github.eslamasabry.oc.ui.words.res.appearanceModeAuto
import io.github.eslamasabry.oc.ui.words.res.appearanceModeSaver
import io.github.eslamasabry.oc.ui.words.res.appearanceSpacingCompact
import io.github.eslamasabry.oc.ui.words.res.appearanceSpacingRoomy
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfaceBrutal
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfacePaper
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfaceSoft
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfaceSolid
import io.github.eslamasabry.oc.ui.words.res.automationSearchAliases
import io.github.eslamasabry.oc.ui.words.res.automationTitle
import io.github.eslamasabry.oc.ui.words.res.capabilityScreenAliases
import io.github.eslamasabry.oc.ui.words.res.capabilityScreenTitle
import io.github.eslamasabry.oc.ui.words.res.chatUiTimestampsUsage
import io.github.eslamasabry.oc.ui.words.res.chatUiTranscriptDisplay
import io.github.eslamasabry.oc.ui.words.res.configInspectionTitle
import io.github.eslamasabry.oc.ui.words.res.demoScreenTitle
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportShareFailed
import io.github.eslamasabry.oc.ui.words.res.discoverLanguageAliases
import io.github.eslamasabry.oc.ui.words.res.discoverShowTipsAgain
import io.github.eslamasabry.oc.ui.words.res.discoverShowTipsAliases
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceDark
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceLight
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceSaveFailed
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceTitle
import io.github.eslamasabry.oc.ui.words.res.e7LibraryAlwaysAllowedActions
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiLanguage
import io.github.eslamasabry.oc.ui.words.res.e7SettingsDetailUi17
import io.github.eslamasabry.oc.ui.words.res.e7SettingsDetailUi18
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi12
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi14
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi25
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi35
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi74
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi88
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi92
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi96
import io.github.eslamasabry.oc.ui.words.res.e7SetupServers
import io.github.eslamasabry.oc.ui.words.res.effectsActivityGlow
import io.github.eslamasabry.oc.ui.words.res.effectsAnimations
import io.github.eslamasabry.oc.ui.words.res.effectsMotionCalm
import io.github.eslamasabry.oc.ui.words.res.effectsMotionCalmHint
import io.github.eslamasabry.oc.ui.words.res.effectsMotionFull
import io.github.eslamasabry.oc.ui.words.res.effectsMotionFullHint
import io.github.eslamasabry.oc.ui.words.res.effectsMotionOff
import io.github.eslamasabry.oc.ui.words.res.effectsMotionOffHint
import io.github.eslamasabry.oc.ui.words.res.effectsSaveFailed
import io.github.eslamasabry.oc.ui.words.res.effectsSection
import io.github.eslamasabry.oc.ui.words.res.keepRunningRowSubtitle
import io.github.eslamasabry.oc.ui.words.res.keepRunningTitle
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.libraryMcpTitle
import io.github.eslamasabry.oc.ui.words.res.libraryProvidersTitle
import io.github.eslamasabry.oc.ui.words.res.migrationStorageTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilitiesTitle
import io.github.eslamasabry.oc.ui.words.res.nativePhraseChipsAliases
import io.github.eslamasabry.oc.ui.words.res.onboardingSetupGuide
import io.github.eslamasabry.oc.ui.words.res.openCodeConnectionLabel
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardTitle
import io.github.eslamasabry.oc.ui.words.res.privacyPolicyTitle
import io.github.eslamasabry.oc.ui.words.res.rb34AppearanceSurfaceGlass
import io.github.eslamasabry.oc.ui.words.res.reportProblemCopied
import io.github.eslamasabry.oc.ui.words.res.settingsHubAccounts
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupAgent
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupConversations
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupHelp
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupThisApp
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupUsage
import io.github.eslamasabry.oc.ui.words.res.settingsHubModelAndMode
import io.github.eslamasabry.oc.ui.words.res.settingsHubModelRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubPrivacyRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubProvidersRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchAboutAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchAccountsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchAppearanceAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchBugAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchCommandsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchDiagnosticsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchExternalAgentsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchGuideAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchMcpAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchModelModeAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchNotificationsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchPermissionsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchPhoneLinuxAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchPrivacyAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchProvidersAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchReasoningAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchSavedServersAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchShellAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchToolsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchTranscriptAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchUsageAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchVoiceAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubServer
import io.github.eslamasabry.oc.ui.words.res.settingsHubShowReasoning
import io.github.eslamasabry.oc.ui.words.res.settingsHubShowTimestamps
import io.github.eslamasabry.oc.ui.words.res.settingsHubToolsRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubVoice
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsSupporting
import io.github.eslamasabry.oc.ui.words.res.settingsPhraseChipsTitle
import io.github.eslamasabry.oc.ui.words.res.settingsSearchAppearanceInsideAliases
import io.github.eslamasabry.oc.ui.words.res.settingsTryDemo
import io.github.eslamasabry.oc.ui.words.res.settingsV3AgentsOnPhone
import io.github.eslamasabry.oc.ui.words.res.settingsV3Background
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import io.github.eslamasabry.oc.ui.words.res.settingsV3Notifications
import io.github.eslamasabry.oc.ui.words.res.settingsV3Off
import io.github.eslamasabry.oc.ui.words.res.settingsV3Permissions
import io.github.eslamasabry.oc.ui.words.res.settingsV3Providers
import io.github.eslamasabry.oc.ui.words.res.settingsV3Servers
import io.github.eslamasabry.oc.ui.words.res.settingsV3Shell
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import io.github.eslamasabry.oc.ui.words.res.settingsV3VoiceTyping
import io.github.eslamasabry.oc.ui.words.res.signalStyleName
import io.github.eslamasabry.oc.ui.words.res.teamUiHomeTitle
import io.github.eslamasabry.oc.ui.words.res.teamUiPluginsTitle
import io.github.eslamasabry.oc.ui.words.res.textSizeDefault
import io.github.eslamasabry.oc.ui.words.res.textSizeLarge
import io.github.eslamasabry.oc.ui.words.res.textSizeSmall
import io.github.eslamasabry.oc.ui.words.res.textSizeSmallest
import io.github.eslamasabry.oc.ui.words.res.textSizeTitle
import io.github.eslamasabry.oc.ui.words.res.tipsHubSubtitle
import io.github.eslamasabry.oc.ui.words.res.tipsSearchAliases
import io.github.eslamasabry.oc.ui.words.res.tipsTitle
import io.github.eslamasabry.oc.ui.words.res.transcriptFindReasoning
import io.github.eslamasabry.oc.ui.words.res.voiceNoticeCopyFailed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Enum → `:ui:words` copy for the Settings pages. Lines `:ui:words` lacks are in [PendingWords].
 */
@Composable
internal fun groupWords(group: SettingsGroupId): String =
    stringResource(
        when (group) {
            SettingsGroupId.Server -> Res.string.settingsHubServer
            SettingsGroupId.Agent -> Res.string.settingsHubGroupAgent
            SettingsGroupId.Conversations -> Res.string.settingsHubGroupConversations
            SettingsGroupId.ThisApp -> Res.string.settingsHubGroupThisApp
            SettingsGroupId.Help -> Res.string.settingsHubGroupHelp
        }
    )

@Composable
internal fun rowWords(row: SettingsRowId): String = stringResource(rowKey(row))

internal fun rowKey(row: SettingsRowId): StringResource =
    when (row) {
        SettingsRowId.ServerCapabilities -> Res.string.capabilityScreenTitle
        SettingsRowId.ServerConfiguration -> Res.string.configInspectionTitle
        SettingsRowId.Servers -> Res.string.settingsV3Servers
        SettingsRowId.ThisPhone -> Res.string.settingsV3ThisPhone
        SettingsRowId.ModelAndMode -> Res.string.settingsHubModelAndMode
        SettingsRowId.Providers -> Res.string.settingsHubProvidersRow
        SettingsRowId.Usage -> Res.string.settingsHubGroupUsage
        SettingsRowId.Tools -> Res.string.settingsHubToolsRow
        SettingsRowId.Agents -> Res.string.settingsV3AgentsOnPhone
        SettingsRowId.AiTeam -> Res.string.teamUiHomeTitle
        SettingsRowId.ShowReasoning -> Res.string.settingsHubShowReasoning
        SettingsRowId.ShowTimestamps -> Res.string.settingsHubShowTimestamps
        SettingsRowId.PhraseChips -> Res.string.settingsPhraseChipsTitle
        SettingsRowId.DefaultShell -> Res.string.e7SettingsUi35
        SettingsRowId.Voice -> Res.string.settingsHubVoice
        SettingsRowId.Permissions -> Res.string.settingsV3Permissions
        SettingsRowId.Notifications -> Res.string.settingsV3Notifications
        SettingsRowId.Background -> Res.string.settingsV3Background
        SettingsRowId.Appearance -> Res.string.e7AppearanceTitle
        SettingsRowId.Language -> Res.string.settingsV3Language
        SettingsRowId.Privacy -> Res.string.settingsHubPrivacyRow
        SettingsRowId.Updates -> Res.string.appUpdatesTitle
        SettingsRowId.Storage -> Res.string.migrationStorageTitle
        SettingsRowId.Guide -> Res.string.onboardingSetupGuide
        SettingsRowId.Tips -> Res.string.tipsTitle
        SettingsRowId.Capabilities -> Res.string.nativeCapabilitiesTitle
        SettingsRowId.ReportProblem -> Res.string.kitReportProblem
        SettingsRowId.About -> Res.string.aboutTitle
    }

/**
 * The acceptance words per row, as Flutter's settings search gives them (lib/ui/search/search_index/hub_rows.dart
 * keywords plus lib/domain/settings_search_catalog.dart for the rows inside Appearance, B-165): each hub row also
 * answers to the titles of what lives inside it, so "Voice typing", "Text size" or "Always allowed actions" find
 * the row that holds them. Bilingual where `:ui:words` has them; empty = the title only.
 */
internal fun aliasKeys(row: SettingsRowId): List<StringResource> =
    when (row) {
        SettingsRowId.Servers ->
            listOf(
                Res.string.settingsHubSearchSavedServersAliases,
                Res.string.openCodeConnectionLabel,
                Res.string.e7SetupServers,
            )
        SettingsRowId.ThisPhone ->
            listOf(Res.string.settingsHubSearchPhoneLinuxAliases, Res.string.phoneServerCardTitle)
        SettingsRowId.ModelAndMode ->
            listOf(Res.string.settingsHubModelRow, Res.string.settingsHubSearchModelModeAliases)
        SettingsRowId.Providers ->
            listOf(
                Res.string.settingsV3Providers,
                Res.string.libraryProvidersTitle,
                Res.string.settingsHubAccounts,
                Res.string.settingsHubSearchProvidersAliases,
                Res.string.settingsHubSearchAccountsAliases,
            )
        SettingsRowId.Usage -> listOf(Res.string.settingsHubSearchUsageAliases)
        SettingsRowId.Tools ->
            listOf(
                Res.string.settingsHubSearchToolsAliases,
                Res.string.libraryMcpTitle,
                Res.string.settingsHubSearchMcpAliases,
                Res.string.settingsHubSearchCommandsAliases,
                Res.string.teamUiPluginsTitle,
                Res.string.a2aTitle,
                Res.string.settingsHubSearchExternalAgentsAliases,
            )
        SettingsRowId.Agents -> listOf(Res.string.agentsSectionTitle, Res.string.a2aTitle)
        // F: hub_rows.dart:147-159 (discoverTeamAliases, teamSettingsTitle, teamUiPluginsTitle).
        SettingsRowId.AiTeam ->
            listOf(Res.string.discoverTeamAliases, Res.string.teamSettingsTitle, Res.string.teamUiPluginsTitle)
        SettingsRowId.ShowReasoning ->
            listOf(
                Res.string.settingsHubSearchReasoningAliases,
                Res.string.chatUiTranscriptDisplay,
                Res.string.transcriptFindReasoning,
            )
        SettingsRowId.ShowTimestamps ->
            listOf(
                Res.string.settingsHubSearchTranscriptAliases,
                Res.string.chatUiTranscriptDisplay,
                Res.string.chatUiTimestampsUsage,
            )
        SettingsRowId.PhraseChips ->
            listOf(Res.string.nativePhraseChipsAliases, Res.string.settingsPhraseChipsSupporting)
        SettingsRowId.DefaultShell ->
            listOf(Res.string.settingsV3Shell, Res.string.settingsHubSearchShellAliases)
        SettingsRowId.Voice ->
            listOf(Res.string.settingsV3VoiceTyping, Res.string.settingsHubSearchVoiceAliases)
        SettingsRowId.Permissions ->
            listOf(
                Res.string.automationTitle,
                Res.string.automationSearchAliases,
                Res.string.settingsHubSearchPermissionsAliases,
                Res.string.e7SettingsUi74,
                Res.string.e7LibraryAlwaysAllowedActions,
            )
        SettingsRowId.Notifications -> listOf(Res.string.settingsHubSearchNotificationsAliases)
        SettingsRowId.Background ->
            listOf(
                Res.string.keepRunningTitle,
                Res.string.e7SettingsUi25,
                Res.string.keepRunningRowSubtitle,
                Res.string.settingsHubSearchNotificationsAliases,
            )
        SettingsRowId.Appearance ->
            listOf(
                Res.string.settingsHubSearchAppearanceAliases,
                Res.string.textSizeTitle,
                Res.string.appearanceGroupBackground,
                Res.string.effectsSection,
                Res.string.effectsAnimations,
                Res.string.effectsActivityGlow,
                Res.string.settingsSearchAppearanceInsideAliases,
            )
        SettingsRowId.Language ->
            listOf(Res.string.e7LocaleUiLanguage, Res.string.discoverLanguageAliases)
        SettingsRowId.Privacy ->
            listOf(
                Res.string.settingsHubSearchPrivacyAliases,
                Res.string.privacyPolicyTitle,
                Res.string.e7SettingsUi92,
                Res.string.e7SettingsDetailUi17,
            )
        SettingsRowId.Updates -> listOf(Res.string.appUpdatesSearchAliases)
        SettingsRowId.Guide ->
            listOf(
                Res.string.settingsHubSearchGuideAliases,
                Res.string.settingsTryDemo,
                Res.string.demoScreenTitle,
            )
        SettingsRowId.Tips ->
            listOf(
                Res.string.tipsSearchAliases,
                Res.string.tipsHubSubtitle,
                Res.string.discoverShowTipsAgain,
                Res.string.discoverShowTipsAliases,
            )
        SettingsRowId.ReportProblem ->
            listOf(
                Res.string.settingsHubSearchBugAliases,
                Res.string.e7SettingsUi88,
                Res.string.settingsHubSearchDiagnosticsAliases,
            )
        SettingsRowId.About ->
            listOf(
                Res.string.settingsHubSearchAboutAliases,
                Res.string.e7SettingsUi96,
                Res.string.e7SettingsDetailUi18,
            )
        SettingsRowId.ServerCapabilities -> listOf(Res.string.capabilityScreenAliases)
        SettingsRowId.Storage,
        SettingsRowId.ServerConfiguration,
        SettingsRowId.Capabilities -> emptyList()
    }

@Composable
internal fun aliasWords(row: SettingsRowId): String =
    aliasKeys(row).map { stringResource(it) }.joinToString(" ")

@Composable
internal fun modeWords(mode: AppearanceMode): String =
    stringResource(
        when (mode) {
            AppearanceMode.System -> Res.string.appearanceModeAuto
            AppearanceMode.Light -> Res.string.e7AppearanceLight
            AppearanceMode.Dark -> Res.string.e7AppearanceDark
            AppearanceMode.Saver -> Res.string.appearanceModeSaver
        }
    )

/**
 * Pack names are brand names (Graphite, Nord, …) and stay as the pack spells them; Material You has
 * its own words.
 */
internal fun packName(pack: PackId): String =
    if (pack == PackId.Dynamic) PendingWords.MATERIAL_YOU else Packs.of(pack).label

@Composable
internal fun styleWords(style: SurfaceStyle): String =
    stringResource(
        when (style) {
            SurfaceStyle.Solid -> Res.string.appearanceSurfaceSolid
            SurfaceStyle.Glass -> Res.string.rb34AppearanceSurfaceGlass
            SurfaceStyle.Soft -> Res.string.appearanceSurfaceSoft
            SurfaceStyle.Brutal -> Res.string.appearanceSurfaceBrutal
            SurfaceStyle.Paper -> Res.string.appearanceSurfacePaper
            SurfaceStyle.Signal -> Res.string.signalStyleName
        }
    )

@Composable
internal fun motionWords(motion: MotionLevel): String =
    stringResource(
        when (motion) {
            MotionLevel.Full -> Res.string.effectsMotionFull
            MotionLevel.Calm -> Res.string.effectsMotionCalm
            MotionLevel.Off -> Res.string.effectsMotionOff
        }
    )

@Composable
internal fun motionHint(motion: MotionLevel): String =
    stringResource(
        when (motion) {
            MotionLevel.Full -> Res.string.effectsMotionFullHint
            MotionLevel.Calm -> Res.string.effectsMotionCalmHint
            MotionLevel.Off -> Res.string.effectsMotionOffHint
        }
    )

@Composable
internal fun backgroundWords(state: BackgroundState): String =
    stringResource(
        when (state) {
            BackgroundState.Off -> Res.string.settingsV3Off
            BackgroundState.Running -> Res.string.e7SettingsUi14
            BackgroundState.StoppedByAndroid -> Res.string.e7SettingsUi12
        }
    )

@Composable
internal fun updateFailureWords(why: UpdateFailure): String =
    stringResource(
        when (why) {
            UpdateFailure.Network -> Res.string.appUpdateFailNetwork
            UpdateFailure.NoFile -> Res.string.appUpdateFailNoFile
            UpdateFailure.Checksum -> Res.string.appUpdateFailChecksum
            UpdateFailure.Signer -> Res.string.appUpdateFailSigner
            UpdateFailure.Package -> Res.string.appUpdateFailPackage
            UpdateFailure.Version -> Res.string.appUpdateFailVersion
            UpdateFailure.Storage -> Res.string.appUpdateFailStorage
            UpdateFailure.Installer -> Res.string.appUpdateFailInstaller
        }
    )

@Composable
internal fun noteWords(note: SettingsNote): String =
    when (note) {
        SettingsNote.AppearanceNotSaved -> stringResource(Res.string.e7AppearanceSaveFailed)
        SettingsNote.ChoiceNotSaved,
        SettingsNote.ConsentNotSaved -> stringResource(Res.string.effectsSaveFailed)
        SettingsNote.TestNotSent -> PendingWords.TEST_NOT_SENT
        SettingsNote.UpdateFailed -> stringResource(Res.string.appUpdateFailNetwork)
        SettingsNote.LicenseCopied -> stringResource(Res.string.kitCopied)
        SettingsNote.LicenseCopyFailed -> stringResource(Res.string.voiceNoticeCopyFailed)
        SettingsNote.ReportCopied -> stringResource(Res.string.reportProblemCopied)
        SettingsNote.ShareFailed -> stringResource(Res.string.diagnosticsReportShareFailed)
    }

@Composable
internal fun textSizeWords(size: AppTextSize): String =
    stringResource(
        when (size) {
            AppTextSize.Smallest -> Res.string.textSizeSmallest
            AppTextSize.Small -> Res.string.textSizeSmall
            AppTextSize.Normal -> Res.string.textSizeDefault
            AppTextSize.Large -> Res.string.textSizeLarge
        }
    )

@Composable
internal fun cornersWords(corners: Corners): String =
    stringResource(
        when (corners) {
            Corners.Round -> Res.string.appearanceFineTuneCornersRound
            Corners.Soft -> Res.string.appearanceFineTuneCornersSoft
            Corners.Sharp -> Res.string.appearanceFineTuneCornersSharp
        }
    )

@Composable
internal fun spacingWords(spacing: Density): String =
    stringResource(
        when (spacing) {
            Density.Comfortable -> Res.string.appearanceSpacingRoomy
            Density.Compact -> Res.string.appearanceSpacingCompact
        }
    )

/** An Accent swatch's name by its stored id; an unknown id reads as its own id (never blank). */
@Composable
internal fun accentWords(id: String): String =
    when (id) {
        "mint" -> stringResource(Res.string.appearanceAccentMint)
        "teal" -> stringResource(Res.string.appearanceAccentTeal)
        "blue" -> stringResource(Res.string.appearanceAccentBlue)
        "violet" -> stringResource(Res.string.appearanceAccentViolet)
        "pink" -> stringResource(Res.string.appearanceAccentPink)
        "mono" -> stringResource(Res.string.appearanceAccentMono)
        "silver" -> stringResource(Res.string.appearanceAccentSilver)
        else -> id
    }
