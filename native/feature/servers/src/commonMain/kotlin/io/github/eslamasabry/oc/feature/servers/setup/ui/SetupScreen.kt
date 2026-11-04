package io.github.eslamasabry.oc.feature.servers.setup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.domain.SetupHistoryPhase
import io.github.eslamasabry.oc.engine.runtime.setupinspection.RegistryStatus
import io.github.eslamasabry.oc.feature.servers.setup.presentation.*
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun SetupScreen(state: SetupUi, actions: SetupActions) {
    val query = rememberTextFieldState()
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.setupInspectTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KitNotice(stringResource(Res.string.setupInspectReadOnly))
            if (!state.inspectionAvailable && !state.loading)
                KitNotice(stringResource(Res.string.setupInspectUnsupported))
            val migration = state.migration
            val done = migration?.done
            val total = migration?.total
            if (migration != null && migration.phase != SetupHistoryPhase.Completed) {
                KitNotice(
                    when (migration.phase) {
                        SetupHistoryPhase.Required ->
                            stringResource(Res.string.setupMigrationRequired)
                        SetupHistoryPhase.Running ->
                            if (done != null && total != null && total > 0 && done <= total)
                                stringResource(Res.string.setupMigrationProgress, done, total)
                            else stringResource(Res.string.setupMigrationRequired)
                        SetupHistoryPhase.Error -> stringResource(Res.string.setupMigrationError)
                        SetupHistoryPhase.Completed -> ""
                    }
                )
            }
            if (state.migrationUnavailable)
                KitNotice(stringResource(Res.string.setupMigrationUnavailable))
            if (state.failure != null) KitNotice(stringResource(Res.string.setupInspectFailed))
            KitButton(
                stringResource(Res.string.setupInspectRefresh),
                actions::inspect,
                enabled =
                    if (!state.inspectionAvailable)
                        KitEnabled.Disabled(stringResource(Res.string.setupInspectUnsupported))
                    else if (state.loading)
                        KitEnabled.Disabled(stringResource(Res.string.setupInspectLoading))
                    else KitEnabled.Enabled,
            )
            if (state.loading) KitText(stringResource(Res.string.setupInspectLoading))
            if (state.inspected) {
                KitText(stringResource(Res.string.setupInspectConfig))
                if (state.sources.isEmpty())
                    KitText(stringResource(Res.string.setupInspectNoSources))
                state.sources.forEach { source ->
                    KitText(source.path ?: source.type)
                    source.redactedInfo?.let { KitText(it) }
                }
                KitText(stringResource(Res.string.setupInspectMcp))
                if (state.inventory.isEmpty()) KitText(stringResource(Res.string.setupInspectNoMcp))
                state.inventory.forEach { server ->
                    KitText(server.name)
                    KitText(
                        stringResource(
                            when (server.status) {
                                "connected" -> Res.string.setupMcpConnected
                                "pending" -> Res.string.setupMcpPending
                                "disabled" -> Res.string.setupMcpDisabled
                                "failed" -> Res.string.setupMcpFailed
                                "needs_auth" -> Res.string.setupMcpSignIn
                                else -> Res.string.setupMcpUnknown
                            }
                        )
                    )
                }
            }
            KitText(stringResource(Res.string.setupRegistryTitle))
            KitSwitchRow(
                stringResource(Res.string.setupRegistryConsent),
                state.registry.optedIn,
                supporting = stringResource(Res.string.setupRegistryPrivacy),
                onCheckedChange = actions::consent,
            )
            KitNotice(
                stringResource(
                    when (state.registry.status) {
                        RegistryStatus.Disabled -> Res.string.setupRegistryDisabled
                        RegistryStatus.Loading -> Res.string.setupRegistryLoading
                        RegistryStatus.Offline -> Res.string.setupRegistryOffline
                        RegistryStatus.Error -> Res.string.setupRegistryError
                        RegistryStatus.Empty -> Res.string.setupRegistryEmpty
                        RegistryStatus.Ready -> Res.string.setupRegistryReadOnly
                    }
                )
            )
            if (state.registry.optedIn) {
                KitField(query, stringResource(Res.string.setupRegistrySearch), maxLength = 100)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitButton(
                        stringResource(Res.string.setupRegistryRefresh),
                        { actions.refresh() },
                        role = KitButtonRole.Secondary,
                    )
                    KitButton(
                        stringResource(Res.string.setupRegistrySearch),
                        { actions.refresh(query.text.toString()) },
                        role = KitButtonRole.Secondary,
                    )
                    KitButton(
                        stringResource(Res.string.setupRegistrySaved),
                        actions::showSaved,
                        role = KitButtonRole.Tertiary,
                    )
                }
            }
            state.registry.entries.forEach { listing ->
                KitText(listing.title.ifBlank { listing.name })
                KitText("${listing.name} · ${listing.version}")
                if (listing.description.isNotEmpty()) KitText(listing.description)
            }
        }
    }
}
