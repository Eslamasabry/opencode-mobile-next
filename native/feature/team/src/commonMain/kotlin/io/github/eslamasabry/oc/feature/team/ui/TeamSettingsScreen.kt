package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.TeamHostFacts
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Team settings (Dart `TeamSettingsScreen`) for the phone's own team: everything about how the team is set up, kept off
 * the work page. One list: the agents, the check that proves this phone safe, Technical details (how fast it runs here;
 * the address, version and protection behind it) and Turn off the AI Team last, which asks first. The address, model and
 * heat rows of the Gas City page belong to a computer's team.
 */
@Composable
public fun TeamSettingsScreen(state: TeamSettingsUi, actions: TeamSettingsActions) {
    val inset = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.teamSettingsTitle),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            loading = state.phase == TeamPhase.Loading,
            loadingLabel = stringResource(Res.string.teamProjectLoad),
            width = KitScreenWidth.Reading,
            edges = KitScreenEdges.Bleed,
        ) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("team-settings"),
                contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                if (state.offFailed) {
                    item(key = "off-failed") {
                        KitNotice(
                            stringResource(if (state.computer) Res.string.aiteamComponentTurnOffFailed else Res.string.teamHomeTurnOffFailed),
                            inset.testTag("team-settings-turn-off-failed"),
                            dismiss = KitLineAction(stringResource(Res.string.kitConfirmCancel), actions::dismissOffFailed),
                        )
                    }
                }
                // The roles are the phone team's; a computer's team keeps its agents on the computer.
                if (!state.computer) item(key = "team") {
                    KitRowGroup {
                        KitRow(
                            title = pluralStringResource(Res.plurals.teamSettingsAgentsRow, state.roles, state.roles),
                            supporting = stringResource(Res.string.teamSettingsAgentsHint),
                            supportingMaxLines = 2,
                            leading = KitRowLeading.Glyph(KitIcons.agent),
                            trailing = KitRowTrailing.Chevron,
                            modifier = Modifier.testTag("team-settings-agents"),
                            onClick = actions::openAgents,
                        )
                    }
                }
                item(key = "how") {
                    KitRowGroup(
                        destructive = {
                            KitRow(
                                title = stringResource(Res.string.teamSettingsTurnOff),
                                leading = KitRowLeading.Glyph(KitIcons.unlink),
                                enabled = !state.working,
                                modifier = Modifier.testTag("team-settings-turn-off"),
                                onClick = actions::askTurnOff,
                            )
                        },
                    ) {
                        // Flutter's Change address: a computer's team moves to another address, tested first.
                        if (state.canChangeAddress) {
                            KitRow(
                                title = stringResource(Res.string.teamHomeChangeAddress),
                                leading = KitRowLeading.Glyph(KitIcons.edit),
                                trailing = KitRowTrailing.Chevron,
                                enabled = !state.working,
                                modifier = Modifier.testTag("team-settings-change-address"),
                                onClick = actions::changeAddress,
                            )
                        }
                        if (state.canCheck) {
                            KitRow(
                                title = stringResource(Res.string.teamIntroTurnOnPhone),
                                supporting = stringResource(Res.string.phoneTeamOffBody),
                                supportingMaxLines = 2,
                                leading = KitRowLeading.Glyph(KitIcons.phone),
                                trailing = KitRowTrailing.Chevron,
                                modifier = Modifier.testTag("team-settings-turn-on-phone"),
                                onClick = actions::openSetup,
                            )
                        }
                        // How fast it runs where it runs; the address, version and protection are behind it.
                        KitRow(
                            title = stringResource(Res.string.teamUiTechnicalDetails),
                            supporting = stringResource(if (state.computer && !state.onPhone) Res.string.teamUiDisclaimerComputer else Res.string.teamUiDisclaimerPhone),
                            supportingMaxLines = 2,
                            leading = KitRowLeading.Glyph(KitIcons.speed),
                            trailing = KitRowTrailing.Chevron,
                            modifier = Modifier.testTag("team-settings-details"),
                            onClick = actions::openDetails,
                        )
                    }
                }
            }
        }
        HostDetails(state, actions)
        KitConfirm(
            spec =
                if (state.confirmingOff && state.computer) {
                    // Flutter `showTeamTurnOffSheet`: this phone forgets the team; nothing changes on the computer.
                    KitConfirmSpec(
                        title = stringResource(Res.string.aiteamComponentTurnOffTitle),
                        confirmLabel = stringResource(Res.string.teamUiTurnOffConfirm),
                        kind = KitConfirmKind.Destructive,
                        body = stringResource(Res.string.teamUiTurnOffBody),
                        cancelLabel = stringResource(Res.string.teamUiKeep),
                    )
                } else if (state.confirmingOff) {
                    KitConfirmSpec(
                        title = stringResource(Res.string.aiteamComponentTurnOffTitle),
                        confirmLabel = stringResource(Res.string.aiteamComponentTurnOff),
                        kind = KitConfirmKind.Destructive,
                        body = stringResource(Res.string.aiteamComponentTurnOffBody),
                        consequences = listOf(stringResource(Res.string.aiteamComponentTurnOffKept)),
                        cancelLabel = stringResource(Res.string.teamUiKeep),
                    )
                } else {
                    null
                },
            onConfirm = actions::confirmTurnOff,
            onCancel = actions::cancelTurnOff,
        )
    }
}

/** Dart `TeamHostDetailsSheet`, for the phone: where the team runs, how it behaves there, and the raw values. */
@Composable
private fun HostDetails(state: TeamSettingsUi, actions: TeamSettingsActions) {
    KitSheet(
        visible = state.detailsOpen,
        onDismissRequest = actions::closeDetails,
        title = stringResource(Res.string.teamUiTechnicalDetails),
    ) {
        Column(Modifier.testTag("team-settings-host-sheet"), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.teamUiLabelHost),
                    supporting = stringResource(if (state.computer && !state.onPhone) Res.string.teamUiHostModeComputer else Res.string.teamUiHostModePhone),
                )
            }
            KitText(
                stringResource(if (state.computer && !state.onPhone) Res.string.teamUiDisclaimerComputer else Res.string.teamUiDisclaimerPhone),
                Modifier.testTag("team-host-disclaimer"),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            state.host?.let { host ->
                protectionWords(host)?.let { KitNotice(it, icon = KitIcons.shield) }
                KitDetailsFold(
                    label = stringResource(Res.string.teamUiHomeHostRawHeading),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    initiallyExpanded = true,
                    values =
                        listOfNotNull(
                            KitTechnicalValue(stringResource(Res.string.teamUiLabelProvider), host.provider),
                            host.version?.let { KitTechnicalValue(stringResource(Res.string.teamUiLabelVersion), it) },
                            KitTechnicalValue(stringResource(Res.string.teamUiLabelAddress), host.address),
                        ),
                )
            }
        }
    }
}

@Composable
private fun protectionWords(host: TeamHostFacts): String? =
    when (host.protection) {
        "proot" -> stringResource(Res.string.phoneTeamProtectedProot)
        "landlock" -> stringResource(Res.string.phoneTeamProtectedLandlock)
        else -> null
    }
