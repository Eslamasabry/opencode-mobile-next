package io.github.eslamasabry.oc.feature.integrations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.github.eslamasabry.oc.engine.integrations.catalog.McpChatPhase
import io.github.eslamasabry.oc.feature.integrations.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Visible provider sign-in, add-only connector headers and agent skill authoring. Secrets never use
 * saved state.
 */
@Composable
public fun IntegrationsScreen(state: IntegrationsUi, actions: IntegrationsActions) {
    val back = stringResource(Res.string.intBack)
    val disabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.intConnecting))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.intTitle),
                exit = KitTopBarExit.Back,
                exitLabel = back,
                onExit = actions::back,
            )
        }
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.servers.isEmpty()) KitText(stringResource(Res.string.intNoServer))
            else {
                KitRowGroup(label = stringResource(Res.string.intServers)) {
                    state.servers.forEach { server ->
                        KitRow(
                            server.name,
                            selected = server.id == state.server,
                            onClick = { actions.chooseServer(server.id) },
                        )
                    }
                }
                KitButton(
                    stringResource(Res.string.intRefresh),
                    actions::refresh,
                    enabled = disabled,
                    working = state.busy,
                )
                state.notice?.let { KitText(notice(it)) }
                if (state.data.providerSupported) {
                    KitRowGroup(label = stringResource(Res.string.intProviders)) {
                        state.data.providers.forEach { provider ->
                            KitRow(
                                provider.name,
                                supporting =
                                    stringResource(
                                        if (provider.connected) Res.string.intConnected
                                        else Res.string.intNotConnected
                                    ),
                                leading =
                                    provider.logo?.let {
                                        KitRowLeading.Svg(it, provider.name.take(2))
                                    } ?: KitRowLeading.Initials(provider.name.take(2)),
                                selected = provider.id == state.selected,
                                onClick = { actions.chooseProvider(provider.id) },
                            )
                        }
                    }
                    state.data.providers
                        .firstOrNull { it.id == state.selected }
                        ?.let { provider ->
                            key(state.server, provider.id) {
                                val secret = remember { TextFieldState() }
                                val code = remember { TextFieldState() }
                                if (provider.methods.any { !it.oauth }) {
                                    KitField(
                                        secret,
                                        stringResource(Res.string.intKey),
                                        kind = KitFieldKind.Secret,
                                        maxLength = 8192,
                                    )
                                    KitButton(
                                        stringResource(Res.string.intSaveKey),
                                        {
                                            actions.saveKey(secret.text.toString())
                                            secret.edit { replace(0, length, "") }
                                        },
                                        enabled = disabled,
                                    )
                                }
                                if (provider.keyPage)
                                    KitRow(
                                        stringResource(Res.string.intKeyPage),
                                        onClick = actions::openKeyPage,
                                    )
                                provider.methods
                                    .filter { it.oauth }
                                    .forEach { method ->
                                        KitButton(
                                            method.label,
                                            { actions.startOAuth(method.id) },
                                            enabled = disabled,
                                        )
                                    }
                                if (state.authorization != null) {
                                    KitText(stringResource(Res.string.intSignInHelp))
                                    KitButton(
                                        stringResource(Res.string.intOpenSignIn),
                                        actions::openAuthorization,
                                    )
                                    KitField(
                                        code,
                                        stringResource(Res.string.intCode),
                                        kind = KitFieldKind.Secret,
                                        maxLength = 8192,
                                    )
                                    KitButton(
                                        stringResource(Res.string.intCompleteSignIn),
                                        {
                                            actions.completeOAuth(code.text.toString())
                                            code.edit { replace(0, length, "") }
                                        },
                                        enabled = disabled,
                                    )
                                }
                            }
                        }
                } else KitText(stringResource(Res.string.intUnsupported))
                if (state.data.connectorSupported) {
                    KitRowGroup(label = stringResource(Res.string.intConnectors)) {
                        state.data.connectors.forEach { connector ->
                            KitRow(
                                connector.name,
                                supporting =
                                    stringResource(
                                        if (connector.connected) Res.string.intConnectorConnected
                                        else Res.string.intConnectorNotConnected
                                    ),
                            )
                        }
                    }
                    key(state.server) {
                        val name = remember { TextFieldState() }
                        val url = remember { TextFieldState() }
                        val header = remember { TextFieldState() }
                        val value = remember { TextFieldState() }
                        KitField(name, stringResource(Res.string.intConnectorName), maxLength = 80)
                        KitField(
                            url,
                            stringResource(Res.string.intConnectorUrl),
                            kind = KitFieldKind.Url,
                            maxLength = 2048,
                        )
                        KitField(header, stringResource(Res.string.intHeaderName), maxLength = 256)
                        KitField(
                            value,
                            stringResource(Res.string.intHeaderValue),
                            kind = KitFieldKind.Secret,
                            maxLength = 8192,
                            helper = stringResource(Res.string.intHeaderHelp),
                        )
                        KitButton(
                            stringResource(Res.string.intSaveConnector),
                            {
                                actions.saveConnector(
                                    name.text.toString(),
                                    url.text.toString(),
                                    header.text.toString(),
                                    value.text.toString(),
                                )
                                value.edit { replace(0, length, "") }
                            },
                            enabled = disabled,
                        )
                    }
                    state.connector?.let { snapshot ->
                        KitText(
                            stringResource(
                                when (snapshot.phase) {
                                    McpChatPhase.ToolsReady -> Res.string.intConnectorReady
                                    McpChatPhase.ConnectedReadinessUnknown ->
                                        Res.string.intConnectorUnknown
                                    McpChatPhase.NeedsAuthentication,
                                    McpChatPhase.Authorizing -> Res.string.intConnectorAuth
                                    McpChatPhase.Failed,
                                    McpChatPhase.Unavailable -> Res.string.intFailed
                                    else -> Res.string.intConnecting
                                }
                            )
                        )
                    }
                }
                key(state.server) {
                    val requirements = remember { TextFieldState() }
                    KitRowGroup(label = stringResource(Res.string.intSkills)) {
                        KitRow(
                            stringResource(Res.string.intSkills),
                            supporting = stringResource(Res.string.intDraftHelp),
                            supportingMaxLines = 4,
                        )
                    }
                    KitField(
                        requirements,
                        stringResource(Res.string.intRequirements),
                        kind = KitFieldKind.Multiline,
                        maxLength = 12000,
                    )
                    if (state.draft == null)
                        KitButton(
                            stringResource(Res.string.intAskDraft),
                            { actions.writeSkill(requirements.text.toString()) },
                            enabled = disabled,
                        )
                    else KitButton(stringResource(Res.string.intOpenDraft), actions::openDraft)
                }
            }
        }
    }
    val prompt = state.linkPrompt
    KitConfirm(
        prompt?.let {
            KitConfirmSpec(
                title = stringResource(Res.string.intOpenQuestion),
                confirmLabel = stringResource(Res.string.intOpen),
                kind = if (it.insecure) KitConfirmKind.Destructive else KitConfirmKind.Neutral,
                body =
                    it.host +
                        if (it.insecure) "\n" + stringResource(Res.string.intInsecure) else "",
            )
        },
        actions::confirmLink,
        actions::dismissLink,
    )
}

@Composable
private fun notice(value: IntegrationsNotice): String =
    stringResource(
        when (value) {
            IntegrationsNotice.Saved -> Res.string.intSaved
            IntegrationsNotice.Failed -> Res.string.intFailed
            IntegrationsNotice.Unsupported -> Res.string.intUnsupported
            IntegrationsNotice.Changed -> Res.string.intChanged
            IntegrationsNotice.Invalid -> Res.string.intInvalid
            IntegrationsNotice.DraftSent -> Res.string.intDraftSent
            IntegrationsNotice.DraftUncertain -> Res.string.intDraftUncertain
        }
    )
