package io.github.eslamasabry.oc.feature.setupassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.setupassistant.*
import io.github.eslamasabry.oc.feature.setupassistant.presentation.SetupActions
import io.github.eslamasabry.oc.feature.setupassistant.presentation.SetupUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Server setup inspection, metadata history and explicit public registry browsing. No listing can
 * execute code.
 */
@Composable
public fun SetupScreen(state: SetupUi, actions: SetupActions) {
    val search = rememberTextFieldState()
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.stateSetupTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (state.loading) KitSkeletonRows()
            if (state.failed) KitNotice(stringResource(Res.string.stateSetupSaveFailed))
            KitNotice(stringResource(Res.string.stateSetupNoWrites))
            if (state.unavailable) KitNotice(stringResource(Res.string.stateSetupUnsupported))
            state.inspection?.let { inspection ->
                when (inspection.phase) {
                    SetupInspectionPhase.Loading -> KitSkeletonRows()
                    SetupInspectionPhase.Failed ->
                        KitNotice(stringResource(Res.string.stateSetupReadFailed))
                    SetupInspectionPhase.Offline ->
                        KitNotice(stringResource(Res.string.stateSetupOffline))
                    SetupInspectionPhase.Idle -> Unit
                    SetupInspectionPhase.Ready -> {
                        KitRowGroup(label = stringResource(Res.string.stateSetupSources)) {
                            for (source in inspection.inspection?.sourceKinds.orEmpty()) KitRow(
                                title = source
                            )
                        }
                        KitRowGroup(label = stringResource(Res.string.stateSetupMcp)) {
                            for (server in inspection.inspection?.servers.orEmpty()) KitRow(
                                title = server.name,
                                supporting = mcpStatus(server.status),
                            )
                        }
                        KitButton(
                            stringResource(Res.string.stateSetupRecordReview),
                            actions::recordReview,
                            working = state.savingReview,
                        )
                    }
                }
                KitButton(
                    stringResource(Res.string.stateSetupRefresh),
                    actions::refresh,
                    role = KitButtonRole.Secondary,
                )
            }
            KitRowGroup(label = stringResource(Res.string.stateSetupHistory)) {
                if (state.auditUnavailable)
                    KitRow(title = stringResource(Res.string.stateSetupHistoryUnavailable))
                else if (state.audit.isEmpty())
                    KitRow(title = stringResource(Res.string.stateSetupHistoryEmpty))
                for (record in state.audit.asReversed()) {
                    KitRow(title = auditAction(record.action), supporting = record.at.toString())
                }
            }
            KitSwitchRow(
                title = stringResource(Res.string.stateSetupRegistryConsent),
                supporting = stringResource(Res.string.stateSetupRegistryPrivacy),
                checked = state.registry.optedIn,
                onCheckedChange = actions::setRegistryConsent,
            )
            when (state.registry.phase) {
                SetupRegistryPhase.Loading -> KitSkeletonRows()
                SetupRegistryPhase.Offline ->
                    KitNotice(stringResource(Res.string.stateSetupRegistryOffline))
                SetupRegistryPhase.Failed ->
                    KitNotice(stringResource(Res.string.stateSetupRegistryFailed))
                SetupRegistryPhase.Empty ->
                    KitNotice(stringResource(Res.string.stateSetupRegistryEmpty))
                SetupRegistryPhase.Ready,
                SetupRegistryPhase.Disabled -> Unit
            }
            if (state.registry.optedIn) {
                KitField(
                    search,
                    stringResource(Res.string.stateSetupRegistrySearch),
                    maxLength = 100,
                )
                KitButton(
                    stringResource(Res.string.stateSetupRegistryRefresh),
                    { actions.refreshRegistry(search.text.toString()) },
                    role = KitButtonRole.Secondary,
                )
                if (state.registry.query != null)
                    KitButton(
                        stringResource(Res.string.stateSetupRegistrySaved),
                        actions::showSaved,
                        role = KitButtonRole.Tertiary,
                    )
                for (entry in state.registry.entries) {
                    KitRow(
                        title = entry.title.ifEmpty { entry.name },
                        supporting = entry.version + " · " + entry.description,
                        supportingMaxLines = 3,
                    )
                }
            }
            KitButton(
                stringResource(Res.string.stateSetupRegistryClear),
                actions::clearRegistry,
                role = KitButtonRole.Tertiary,
            )
        }
    }
}

@Composable
private fun mcpStatus(status: String): String =
    stringResource(
        when (status) {
            "connected" -> Res.string.stateSetupMcpConnected
            "pending" -> Res.string.stateSetupMcpPending
            "disabled" -> Res.string.stateSetupMcpDisabled
            "failed" -> Res.string.stateSetupMcpFailed
            "needs_auth" -> Res.string.stateSetupMcpSignIn
            else -> Res.string.stateSetupMcpUnknown
        }
    )

@Composable
private fun auditAction(action: String): String =
    stringResource(
        when (action) {
            "proposed" -> Res.string.stateSetupAuditProposed
            "pending" -> Res.string.stateSetupAuditPending
            "accepted" -> Res.string.stateSetupAuditAccepted
            "verified" -> Res.string.stateSetupAuditVerified
            "uncertain" -> Res.string.stateSetupAuditUncertain
            else -> Res.string.stateSetupAuditRejected
        }
    )
