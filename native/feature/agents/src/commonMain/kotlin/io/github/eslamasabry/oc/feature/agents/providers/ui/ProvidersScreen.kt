package io.github.eslamasabry.oc.feature.agents.providers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.agents.providers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Keys and sign-in codes live only in unsaved secure fields, never screen state or diagnostics. */
@Composable
public fun ProvidersScreen(state: ProvidersUi, actions: ProvidersActions) {
    val key = remember { TextFieldState() }
    val code = remember { TextFieldState() }
    val form =
        remember(state.selected, state.method?.id) {
            state.method?.fields.orEmpty().associate { it.key to TextFieldState() }
        }
    KitSheetHost {
        KitScreen(
            topBar = {
                KitTopBar(
                    stringResource(Res.string.protoProvidersTitle),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            width = KitScreenWidth.Reading,
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitButton(
                    stringResource(Res.string.protoProvidersRefresh),
                    actions::refresh,
                    working = state.loading,
                    role = KitButtonRole.Secondary,
                )
                state.issue?.let { KitNotice(issueWords(it)) }
                if (state.rows.isEmpty() && !state.loading && state.issue == null)
                    KitNotice(stringResource(Res.string.protoProvidersEmpty))
                for (provider in state.rows) {
                    KitRow(
                        title = provider.name,
                        supporting =
                            stringResource(
                                if (provider.connected) Res.string.protoProvidersConnected
                                else Res.string.protoProvidersNotConnected
                            ),
                        onClick = { actions.select(provider.id) },
                    )
                    if (state.selected == provider.id && !state.attempt) {
                        for (method in provider.methods) KitButton(
                            method.label,
                            { actions.selectMethod(method.id) },
                            role = KitButtonRole.Secondary,
                            working = state.working,
                        )
                        if (provider.key)
                            KitButton(
                                stringResource(Res.string.protoProvidersUseKey),
                                actions::selectKey,
                                role = KitButtonRole.Secondary,
                            )
                        if (provider.stored)
                            KitButton(
                                stringResource(Res.string.protoProvidersDisconnect),
                                actions::disconnect,
                                role = KitButtonRole.Tertiary,
                                destructive = true,
                                working = state.working,
                            )
                    }
                }
                state.method
                    ?.takeIf { !state.attempt }
                    ?.let { method ->
                        KitText(method.label)
                        for (field in method.fields) form[field.key]?.let { input ->
                            KitField(
                                input,
                                field.label,
                                kind = KitFieldKind.Secret,
                                maxLength = 65536,
                            )
                        }
                        KitButton(
                            stringResource(Res.string.protoProvidersStart),
                            {
                                val values = form.mapValues { it.value.text.toString() }
                                form.values.forEach { it.edit { replace(0, length, "") } }
                                actions.start(values)
                            },
                            working = state.working,
                        )
                    }
                if (state.keyEntry && !state.attempt) {
                    KitField(
                        key,
                        stringResource(Res.string.protoProvidersKey),
                        kind = KitFieldKind.Secret,
                        maxLength = 65536,
                    )
                    KitButton(
                        stringResource(Res.string.protoProvidersConnect),
                        {
                            val value = key.text.toString()
                            key.edit { replace(0, length, "") }
                            actions.connectKey(value)
                        },
                        working = state.working,
                    )
                }
                if (state.attempt) {
                    KitNotice(stringResource(Res.string.protoProvidersFinishBrowser))
                    if (state.instructions.isNotBlank()) KitText(state.instructions)
                    if (state.codeMode)
                        KitField(
                            code,
                            stringResource(Res.string.protoProvidersCode),
                            kind = KitFieldKind.Secret,
                            maxLength = 65536,
                        )
                    KitButton(
                        stringResource(Res.string.protoProvidersComplete),
                        {
                            val value = code.text.toString()
                            code.edit { replace(0, length, "") }
                            actions.complete(value)
                        },
                        working = state.working,
                    )
                    KitButton(
                        stringResource(Res.string.protoProvidersCheck),
                        actions::check,
                        role = KitButtonRole.Secondary,
                        working = state.working,
                    )
                    KitButton(
                        stringResource(Res.string.protoProvidersCancel),
                        actions::cancel,
                        role = KitButtonRole.Tertiary,
                        working = state.working,
                    )
                }
            }
        }
        KitConfirm(
            state.link?.let { link ->
                KitConfirmSpec(
                    title = stringResource(Res.string.protoProvidersOpenTitle),
                    body =
                        stringResource(
                            if (link.insecure) Res.string.protoProvidersOpenInsecure
                            else Res.string.protoProvidersOpenHost,
                            link.host,
                        ),
                    confirmLabel = stringResource(Res.string.protoProvidersOpen),
                )
            },
            onConfirm = actions::confirmBrowser,
            onCancel = actions::dismissBrowser,
        )
    }
}

@Composable
private fun issueWords(issue: ProviderIssue): String =
    stringResource(
        when (issue) {
            ProviderIssue.Unavailable -> Res.string.protoProvidersUnavailable
            ProviderIssue.Load -> Res.string.protoProvidersLoadFailed
            ProviderIssue.Changed -> Res.string.protoProvidersChanged
            ProviderIssue.InvalidInput -> Res.string.protoProvidersInvalidInput
            ProviderIssue.Failed -> Res.string.protoProvidersFailed
            ProviderIssue.Pending -> Res.string.protoProvidersPending
            ProviderIssue.Complete -> Res.string.protoProvidersCompleteStatus
            ProviderIssue.Expired -> Res.string.protoProvidersExpired
            ProviderIssue.Cancelled -> Res.string.protoProvidersCancelled
            ProviderIssue.Connected -> Res.string.protoProvidersConnected
            ProviderIssue.Disconnected -> Res.string.protoProvidersDisconnected
        }
    )
