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
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.kit.foundation.KitGlyphs
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportShareFailed
import io.github.eslamasabry.oc.ui.words.res.reportProblemCopied
import io.github.eslamasabry.oc.ui.words.res.textSizeDefault
import io.github.eslamasabry.oc.ui.words.res.textSizeLarge
import io.github.eslamasabry.oc.ui.words.res.textSizeSmall
import io.github.eslamasabry.oc.ui.words.res.textSizeSmallest
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.aboutTitle
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
import io.github.eslamasabry.oc.ui.words.res.appearanceModeAuto
import io.github.eslamasabry.oc.ui.words.res.appearanceModeSaver
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfaceBrutal
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfacePaper
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfaceSoft
import io.github.eslamasabry.oc.ui.words.res.appearanceSurfaceSolid
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceDark
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceLight
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceSaveFailed
import io.github.eslamasabry.oc.ui.words.res.e7AppearanceTitle
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi12
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi14
import io.github.eslamasabry.oc.ui.words.res.e7SettingsUi35
import io.github.eslamasabry.oc.ui.words.res.effectsMotionCalm
import io.github.eslamasabry.oc.ui.words.res.effectsMotionCalmHint
import io.github.eslamasabry.oc.ui.words.res.effectsMotionFull
import io.github.eslamasabry.oc.ui.words.res.effectsMotionFullHint
import io.github.eslamasabry.oc.ui.words.res.effectsMotionOff
import io.github.eslamasabry.oc.ui.words.res.effectsMotionOffHint
import io.github.eslamasabry.oc.ui.words.res.effectsSaveFailed
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.onboardingSetupGuide
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupAgent
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupConversations
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupHelp
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupThisApp
import io.github.eslamasabry.oc.ui.words.res.settingsHubGroupUsage
import io.github.eslamasabry.oc.ui.words.res.settingsHubModelAndMode
import io.github.eslamasabry.oc.ui.words.res.settingsHubPrivacyRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubProvidersRow
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchAboutAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchAppearanceAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchBugAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchGuideAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchModelModeAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchNotificationsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchPermissionsAliases
import io.github.eslamasabry.oc.ui.words.res.settingsHubSearchPhoneAliases
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
import io.github.eslamasabry.oc.ui.words.res.settingsV3AgentsOnPhone
import io.github.eslamasabry.oc.ui.words.res.settingsV3Background
import io.github.eslamasabry.oc.ui.words.res.settingsV3Language
import io.github.eslamasabry.oc.ui.words.res.settingsV3Notifications
import io.github.eslamasabry.oc.ui.words.res.settingsV3Off
import io.github.eslamasabry.oc.ui.words.res.settingsV3Permissions
import io.github.eslamasabry.oc.ui.words.res.settingsV3Servers
import io.github.eslamasabry.oc.ui.words.res.settingsV3ThisPhone
import io.github.eslamasabry.oc.ui.words.res.signalStyleName
import io.github.eslamasabry.oc.ui.words.res.tipsTitle
import org.jetbrains.compose.resources.stringResource

/** Enum → `:ui:words` copy for the Settings pages. Lines `:ui:words` lacks are in [PendingWords]. */
@Composable
internal fun groupWords(group: SettingsGroupId): String =
    stringResource(
        when (group) {
            SettingsGroupId.Server -> Res.string.settingsHubServer
            SettingsGroupId.Agent -> Res.string.settingsHubGroupAgent
            SettingsGroupId.Conversations -> Res.string.settingsHubGroupConversations
            SettingsGroupId.ThisApp -> Res.string.settingsHubGroupThisApp
            SettingsGroupId.Help -> Res.string.settingsHubGroupHelp
        },
    )

@Composable
internal fun rowWords(row: SettingsRowId): String =
    stringResource(when (row) {
        SettingsRowId.Servers -> Res.string.settingsV3Servers
        SettingsRowId.ThisPhone -> Res.string.settingsV3ThisPhone
        SettingsRowId.ModelAndMode -> Res.string.settingsHubModelAndMode
        SettingsRowId.Providers -> Res.string.settingsHubProvidersRow
        SettingsRowId.Usage -> Res.string.settingsHubGroupUsage
        SettingsRowId.Tools -> Res.string.settingsHubToolsRow
        SettingsRowId.Agents -> Res.string.settingsV3AgentsOnPhone
        SettingsRowId.ShowReasoning -> Res.string.settingsHubShowReasoning
        SettingsRowId.ShowTimestamps -> Res.string.settingsHubShowTimestamps
        SettingsRowId.DefaultShell -> Res.string.e7SettingsUi35
        SettingsRowId.Voice -> Res.string.settingsHubVoice
        SettingsRowId.Permissions -> Res.string.settingsV3Permissions
        SettingsRowId.Notifications -> Res.string.settingsV3Notifications
        SettingsRowId.Background -> Res.string.settingsV3Background
        SettingsRowId.Appearance -> Res.string.e7AppearanceTitle
        SettingsRowId.Language -> Res.string.settingsV3Language
        SettingsRowId.Privacy -> Res.string.settingsHubPrivacyRow
        SettingsRowId.Updates -> Res.string.appUpdatesTitle
        SettingsRowId.Guide -> Res.string.onboardingSetupGuide
        SettingsRowId.Tips -> Res.string.tipsTitle
        SettingsRowId.ReportProblem -> Res.string.kitReportProblem
        SettingsRowId.About -> Res.string.aboutTitle
    })

/** The freeze tip's acceptance words per row (bilingual where `:ui:words` has them); empty = the title only. */
@Composable
internal fun aliasWords(row: SettingsRowId): String {
    val key = when (row) {
        SettingsRowId.Servers -> Res.string.settingsHubSearchSavedServersAliases
        SettingsRowId.ThisPhone -> Res.string.settingsHubSearchPhoneAliases
        SettingsRowId.ModelAndMode -> Res.string.settingsHubSearchModelModeAliases
        SettingsRowId.Providers -> Res.string.settingsHubSearchProvidersAliases
        SettingsRowId.Usage -> Res.string.settingsHubSearchUsageAliases
        SettingsRowId.Tools -> Res.string.settingsHubSearchToolsAliases
        SettingsRowId.ShowReasoning -> Res.string.settingsHubSearchReasoningAliases
        SettingsRowId.ShowTimestamps -> Res.string.settingsHubSearchTranscriptAliases
        SettingsRowId.DefaultShell -> Res.string.settingsHubSearchShellAliases
        SettingsRowId.Voice -> Res.string.settingsHubSearchVoiceAliases
        SettingsRowId.Permissions -> Res.string.settingsHubSearchPermissionsAliases
        SettingsRowId.Notifications -> Res.string.settingsHubSearchNotificationsAliases
        SettingsRowId.Appearance -> Res.string.settingsHubSearchAppearanceAliases
        SettingsRowId.Privacy -> Res.string.settingsHubSearchPrivacyAliases
        SettingsRowId.Updates -> Res.string.appUpdatesSearchAliases
        SettingsRowId.Guide -> Res.string.settingsHubSearchGuideAliases
        SettingsRowId.ReportProblem -> Res.string.settingsHubSearchBugAliases
        SettingsRowId.About -> Res.string.settingsHubSearchAboutAliases
        SettingsRowId.Agents, SettingsRowId.Background, SettingsRowId.Language, SettingsRowId.Tips -> null
    }
    return key?.let { stringResource(it) }.orEmpty()
}

@Composable
internal fun modeWords(mode: AppearanceMode): String =
    stringResource(
        when (mode) {
            AppearanceMode.System -> Res.string.appearanceModeAuto
            AppearanceMode.Light -> Res.string.e7AppearanceLight
            AppearanceMode.Dark -> Res.string.e7AppearanceDark
            AppearanceMode.Saver -> Res.string.appearanceModeSaver
        },
    )

/** Pack names are brand names (Graphite, Nord, …) and stay as the pack spells them; Material You has its own words. */
internal fun packName(pack: PackId): String = if (pack == PackId.Dynamic) PendingWords.MATERIAL_YOU else Packs.of(pack).label

@Composable
internal fun styleWords(style: SurfaceStyle): String =
    stringResource(
        when (style) {
            SurfaceStyle.Solid -> Res.string.appearanceSurfaceSolid
            SurfaceStyle.Soft -> Res.string.appearanceSurfaceSoft
            SurfaceStyle.Brutal -> Res.string.appearanceSurfaceBrutal
            SurfaceStyle.Paper -> Res.string.appearanceSurfacePaper
            SurfaceStyle.Signal -> Res.string.signalStyleName
        },
    )

@Composable
internal fun motionWords(motion: MotionLevel): String =
    stringResource(
        when (motion) {
            MotionLevel.Full -> Res.string.effectsMotionFull
            MotionLevel.Calm -> Res.string.effectsMotionCalm
            MotionLevel.Off -> Res.string.effectsMotionOff
        },
    )

@Composable
internal fun motionHint(motion: MotionLevel): String =
    stringResource(
        when (motion) {
            MotionLevel.Full -> Res.string.effectsMotionFullHint
            MotionLevel.Calm -> Res.string.effectsMotionCalmHint
            MotionLevel.Off -> Res.string.effectsMotionOffHint
        },
    )

@Composable
internal fun backgroundWords(state: BackgroundState): String =
    stringResource(
        when (state) {
            BackgroundState.Off -> Res.string.settingsV3Off
            BackgroundState.Running -> Res.string.e7SettingsUi14
            BackgroundState.StoppedByAndroid -> Res.string.e7SettingsUi12
        },
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
        },
    )

@Composable
internal fun noteWords(note: SettingsNote): String =
    when (note) {
        SettingsNote.AppearanceNotSaved -> stringResource(Res.string.e7AppearanceSaveFailed)
        SettingsNote.ChoiceNotSaved, SettingsNote.ConsentNotSaved -> stringResource(Res.string.effectsSaveFailed)
        SettingsNote.TestNotSent -> PendingWords.TEST_NOT_SENT
        SettingsNote.UpdateFailed -> stringResource(Res.string.appUpdateFailNetwork)
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
        },
    )

/** Each group's glyph on the hub (Flutter `hub_rows.dart`). */
internal fun groupGlyph(group: SettingsGroupId): KitRowLeading =
    KitRowLeading.Glyph(when (group) {
        SettingsGroupId.Server -> KitGlyphs.Server
        SettingsGroupId.Agent -> KitGlyphs.Agent
        SettingsGroupId.Conversations -> KitGlyphs.Chat
        SettingsGroupId.ThisApp -> KitGlyphs.Phone
        SettingsGroupId.Help -> KitGlyphs.Question
    })

/** Each row's glyph (Flutter `hub_rows.dart`, the same `AppIconography` names). */
internal fun rowGlyph(row: SettingsRowId): KitRowLeading =
    KitRowLeading.Glyph(when (row) {
        SettingsRowId.Servers -> KitGlyphs.Server
        SettingsRowId.ThisPhone -> KitGlyphs.Phone
        SettingsRowId.ModelAndMode -> KitGlyphs.Model
        SettingsRowId.Providers -> KitGlyphs.Cloud
        SettingsRowId.Usage -> KitGlyphs.Usage
        SettingsRowId.Tools -> KitGlyphs.Tools
        SettingsRowId.Agents -> KitGlyphs.Terminal
        SettingsRowId.ShowReasoning -> KitGlyphs.Idea
        SettingsRowId.ShowTimestamps -> KitGlyphs.Clock
        SettingsRowId.DefaultShell -> KitGlyphs.Terminal
        SettingsRowId.Voice -> KitGlyphs.Mic
        SettingsRowId.Permissions -> KitGlyphs.Permissions
        SettingsRowId.Notifications -> KitGlyphs.Bell
        SettingsRowId.Background -> KitGlyphs.Sync
        SettingsRowId.Appearance -> KitGlyphs.Appearance
        SettingsRowId.Language -> KitGlyphs.Globe
        SettingsRowId.Privacy -> KitGlyphs.Privacy
        SettingsRowId.Updates -> KitGlyphs.Download
        SettingsRowId.Guide -> KitGlyphs.Guide
        SettingsRowId.Tips -> KitGlyphs.Idea
        SettingsRowId.ReportProblem -> KitGlyphs.Bug
        SettingsRowId.About -> KitGlyphs.Info
    })
