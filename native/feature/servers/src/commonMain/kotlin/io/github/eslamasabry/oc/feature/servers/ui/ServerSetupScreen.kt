package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.setupassist.SetupIntent
import io.github.eslamasabry.oc.engine.setupassist.SetupPlannerState
import io.github.eslamasabry.oc.engine.setupassist.SetupRegistryStatus
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Visible, kit-only setup: inspect safe server facts, review local proposals, opt in before public
 * metadata fetch.
 */
@Composable
public fun ServerSetupScreen(state: ServerSetupUi, actions: ServerSetupActions) {
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.secSetupTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitNotice(stringResource(Res.string.secSetupReadOnly))
            if (state.failed) KitNotice(stringResource(Res.string.secSetupFailed))
            KitButton(
                stringResource(Res.string.secSetupRefresh),
                actions::refresh,
                enabled = enabled,
                working = state.busy,
            )
            if (state.config.isNotBlank()) {
                KitText(stringResource(Res.string.secSetupConfig))
                KitText(state.config)
            }
            if (state.available) {
                KitText(stringResource(Res.string.secSetupGuided))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                    for (intent in SetupIntent.entries) KitButton(
                        stringResource(intent.word()),
                        { actions.choose(intent) },
                        role =
                            if (intent == state.intent) KitButtonRole.Secondary
                            else KitButtonRole.Tertiary,
                        enabled = enabled,
                    )
                }
                key(state.intent) {
                    val fields = fields(state.intent)
                    // Values may contain a pasted credential; never save them as instance state,
                    // log or persist them.
                    val input = remember { fields.associateWith { TextFieldState() } }
                    for ((field, value) in input) KitField(
                        value,
                        stringResource(fieldWord(field)),
                        enabled = enabled,
                    )
                    KitButton(
                        stringResource(Res.string.secSetupReview),
                        {
                            actions.propose(input.mapValues { it.value.text.toString() })
                            input.values.forEach { value -> value.edit { replace(0, length, "") } }
                        },
                        enabled = enabled,
                    )
                }
                when {
                    state.invalid -> KitNotice(stringResource(Res.string.secSetupInvalid))
                    state.guidance == SetupPlannerState.NeedsInput ->
                        KitNotice(stringResource(Res.string.secSetupInputs))
                    state.guidance == SetupPlannerState.Unsupported ->
                        KitNotice(stringResource(Res.string.secSetupUnsupported))
                    state.guidance == SetupPlannerState.Proposed ->
                        KitNotice(stringResource(Res.string.secSetupProposed))
                }
                if (state.proposal.isNotEmpty()) KitText(state.proposal)
                KitSwitchRow(
                    stringResource(Res.string.secSetupRegistry),
                    supporting = stringResource(Res.string.secSetupRegistryConsent),
                    checked = state.optedIn,
                    enabled = enabled,
                    onCheckedChange = actions::optIn,
                )
                val search = remember { TextFieldState() }
                KitField(search, stringResource(Res.string.secSetupSearch), enabled = enabled)
                KitButton(
                    stringResource(Res.string.secSetupRegistryRefresh),
                    { actions.refreshRegistry(search.text.toString()) },
                    enabled =
                        if (state.optedIn) enabled
                        else
                            KitEnabled.Disabled(stringResource(Res.string.secSetupRegistryConsent)),
                )
                KitText(
                    stringResource(
                        when (state.registryStatus) {
                            SetupRegistryStatus.Disabled -> Res.string.secSetupRegistryDisabled
                            SetupRegistryStatus.Offline -> Res.string.secSetupRegistryOffline
                            SetupRegistryStatus.Error -> Res.string.secSetupRegistryFailed
                            SetupRegistryStatus.Empty -> Res.string.secSetupRegistryEmpty
                            SetupRegistryStatus.Loading -> Res.string.serversWorking
                            SetupRegistryStatus.Ready -> Res.string.secSetupRegistryReady
                        }
                    )
                )
                for (entry in state.listings) {
                    KitText(entry.name)
                    if (entry.description.isNotEmpty()) KitText(entry.description)
                    KitText(
                        stringResource(
                            when (entry.detail) {
                                SetupListingDetail.Remote -> Res.string.secSetupRegistryRemote
                                SetupListingDetail.NeedsDetails ->
                                    Res.string.secSetupRegistryDetails
                                SetupListingDetail.ReviewedCommand ->
                                    Res.string.secSetupRegistryCommand
                                SetupListingDetail.NoEndpoint ->
                                    Res.string.secSetupRegistryNoEndpoint
                            }
                        )
                    )
                }
            }
        }
    }
}

private fun SetupIntent.word(): StringResource =
    when (this) {
        SetupIntent.ChooseModel -> Res.string.secSetupChooseModel
        SetupIntent.DefaultAgent -> Res.string.secSetupDefaultAgent
        SetupIntent.Permission -> Res.string.secSetupPermission
        SetupIntent.ConnectRemoteMcp -> Res.string.secSetupRemoteMcp
        SetupIntent.AddLocalMcp -> Res.string.secSetupLocalMcp
        SetupIntent.RemoveMcp -> Res.string.secSetupRemoveMcp
        SetupIntent.ConfigureProvider -> Res.string.secSetupProvider
        SetupIntent.ConfigureAgent -> Res.string.secSetupAgent
        SetupIntent.ConfigureCommand -> Res.string.secSetupCommand
    }

private fun fields(intent: SetupIntent): List<String> =
    when (intent) {
        SetupIntent.ChooseModel -> listOf("model")
        SetupIntent.DefaultAgent -> listOf("agent")
        SetupIntent.Permission -> listOf("tool", "effect")
        SetupIntent.ConnectRemoteMcp -> listOf("name", "url")
        SetupIntent.AddLocalMcp -> listOf("name", "command")
        SetupIntent.RemoveMcp -> listOf("name")
        else -> emptyList()
    }

private fun fieldWord(field: String): StringResource =
    when (field) {
        "model" -> Res.string.secSetupModelField
        "agent" -> Res.string.secSetupAgentField
        "tool" -> Res.string.secSetupToolField
        "effect" -> Res.string.secSetupEffectField
        "url" -> Res.string.secSetupUrlField
        "command" -> Res.string.secSetupCommandField
        else -> Res.string.secSetupNameField
    }
