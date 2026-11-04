package io.github.eslamasabry.oc.feature.capabilities.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.capabilities.presentation.CapabilitiesActions
import io.github.eslamasabry.oc.feature.capabilities.presentation.CapabilitiesUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.routes.CapabilityEnableFlow
import io.github.eslamasabry.oc.ui.routes.CapabilityFlowLimit
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilitiesDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilitiesTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddExternalAgentDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddExternalAgentTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddServerCodexDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddServerCodexTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddServerDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddServerPaseoDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddServerPaseoTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddServerTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddToolClaudeDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAddToolClaudeTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowBackgroundDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowBackgroundTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowCameraDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowCameraTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowMicrophoneDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowMicrophoneTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowNotificationsDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityAllowNotificationsTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityExternalDropped
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityMcpAddDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityMcpAddTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityModelSignInDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityModelSignInTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityOpenFailed
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityPhoneSetupDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityPhoneSetupTermuxDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityPhoneSetupTermuxTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityPhoneSetupTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityProjectChooseDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityProjectChooseTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityProjectGitInitDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityProjectGitInitTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityQuotaCollectorGuideDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityQuotaCollectorGuideTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityServerGenerationDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityServerGenerationTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTailscaleSetupDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTailscaleSetupTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTeamDeferred
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTeamHostGuideDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTeamHostGuideTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTeamTurnOnDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTeamTurnOnTitle
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityTermuxMigrationOnly
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityVoiceModelSetupDetail
import io.github.eslamasabry.oc.ui.words.res.nativeCapabilityVoiceModelSetupTitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Help › What works here. Every visible action belongs to the live platform registry. */
@Composable
public fun CapabilitiesScreen(state: CapabilitiesUi, actions: CapabilitiesActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.nativeCapabilitiesTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitGroupNote(stringResource(Res.string.nativeCapabilitiesDetail))
            if (state.failure != null)
                KitErrorNotice(
                    stringResource(Res.string.nativeCapabilityOpenFailed),
                    failure = state.failure,
                )
            KitRowGroup {
                state.flows.forEach { flow ->
                    val words = flowWords(flow)
                    KitRow(
                        title = stringResource(words.first),
                        supporting =
                            state.unavailable[flow]?.let { limit ->
                                stringResource(
                                    when (limit) {
                                        CapabilityFlowLimit.TeamAfterSwitch ->
                                            Res.string.nativeCapabilityTeamDeferred
                                        CapabilityFlowLimit.ExternalAgentsDropped ->
                                            Res.string.nativeCapabilityExternalDropped
                                        CapabilityFlowLimit.TermuxMigrationOnly ->
                                            Res.string.nativeCapabilityTermuxMigrationOnly
                                    }
                                )
                            } ?: stringResource(words.second),
                        supportingMaxLines = 3,
                        trailing =
                            if (flow in state.unavailable) KitRowTrailing.None
                            else KitRowTrailing.Chevron,
                        modifier = Modifier.testTag("capability-${flow.id}"),
                        onClick =
                            if (flow in state.unavailable) null else ({ actions.onEnable(flow) }),
                    )
                }
            }
        }
    }
}

private fun flowWords(flow: CapabilityEnableFlow): Pair<StringResource, StringResource> =
    when (flow) {
        CapabilityEnableFlow.AddServer ->
            Res.string.nativeCapabilityAddServerTitle to Res.string.nativeCapabilityAddServerDetail
        CapabilityEnableFlow.PhoneSetup ->
            Res.string.nativeCapabilityPhoneSetupTitle to
                Res.string.nativeCapabilityPhoneSetupDetail
        CapabilityEnableFlow.PhoneSetupTermux ->
            Res.string.nativeCapabilityPhoneSetupTermuxTitle to
                Res.string.nativeCapabilityPhoneSetupTermuxDetail
        CapabilityEnableFlow.ServerGeneration ->
            Res.string.nativeCapabilityServerGenerationTitle to
                Res.string.nativeCapabilityServerGenerationDetail
        CapabilityEnableFlow.AddServerCodex ->
            Res.string.nativeCapabilityAddServerCodexTitle to
                Res.string.nativeCapabilityAddServerCodexDetail
        CapabilityEnableFlow.AddServerPaseo ->
            Res.string.nativeCapabilityAddServerPaseoTitle to
                Res.string.nativeCapabilityAddServerPaseoDetail
        CapabilityEnableFlow.AddToolClaude ->
            Res.string.nativeCapabilityAddToolClaudeTitle to
                Res.string.nativeCapabilityAddToolClaudeDetail
        CapabilityEnableFlow.ModelSignIn ->
            Res.string.nativeCapabilityModelSignInTitle to
                Res.string.nativeCapabilityModelSignInDetail
        CapabilityEnableFlow.TeamTurnOn ->
            Res.string.nativeCapabilityTeamTurnOnTitle to
                Res.string.nativeCapabilityTeamTurnOnDetail
        CapabilityEnableFlow.TeamHostGuide ->
            Res.string.nativeCapabilityTeamHostGuideTitle to
                Res.string.nativeCapabilityTeamHostGuideDetail
        CapabilityEnableFlow.VoiceModelSetup ->
            Res.string.nativeCapabilityVoiceModelSetupTitle to
                Res.string.nativeCapabilityVoiceModelSetupDetail
        CapabilityEnableFlow.McpAdd ->
            Res.string.nativeCapabilityMcpAddTitle to Res.string.nativeCapabilityMcpAddDetail
        CapabilityEnableFlow.ProjectChoose ->
            Res.string.nativeCapabilityProjectChooseTitle to
                Res.string.nativeCapabilityProjectChooseDetail
        CapabilityEnableFlow.ProjectGitInit ->
            Res.string.nativeCapabilityProjectGitInitTitle to
                Res.string.nativeCapabilityProjectGitInitDetail
        CapabilityEnableFlow.AllowNotifications ->
            Res.string.nativeCapabilityAllowNotificationsTitle to
                Res.string.nativeCapabilityAllowNotificationsDetail
        CapabilityEnableFlow.AllowBackground ->
            Res.string.nativeCapabilityAllowBackgroundTitle to
                Res.string.nativeCapabilityAllowBackgroundDetail
        CapabilityEnableFlow.AllowCamera ->
            Res.string.nativeCapabilityAllowCameraTitle to
                Res.string.nativeCapabilityAllowCameraDetail
        CapabilityEnableFlow.AllowMicrophone ->
            Res.string.nativeCapabilityAllowMicrophoneTitle to
                Res.string.nativeCapabilityAllowMicrophoneDetail
        CapabilityEnableFlow.TailscaleSetup ->
            Res.string.nativeCapabilityTailscaleSetupTitle to
                Res.string.nativeCapabilityTailscaleSetupDetail
        CapabilityEnableFlow.QuotaCollectorGuide ->
            Res.string.nativeCapabilityQuotaCollectorGuideTitle to
                Res.string.nativeCapabilityQuotaCollectorGuideDetail
        CapabilityEnableFlow.AddExternalAgent ->
            Res.string.nativeCapabilityAddExternalAgentTitle to
                Res.string.nativeCapabilityAddExternalAgentDetail
    }
