package io.github.eslamasabry.oc.feature.plans.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.IntegrationAuthMode
import io.github.eslamasabry.oc.feature.plans.presentation.PlanAuthenticationUi
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInActions
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInStep
import io.github.eslamasabry.oc.feature.plans.presentation.planPromptVisible
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource

/**
 * The actual connection's key, browser or server-command sign-in. Secrets stay only in transient
 * input fields.
 */
@Composable
public fun PlanSignInScreen(state: PlanAuthenticationUi, actions: PlanSignInActions) {
    KitBackGuard(enabled = true, onBack = actions::dismiss)
    val provider = plansWords().providerName(state.provider)
    val working = stringResource(Res.string.m40AuthWorking)
    val enabled = if (state.busy) KitEnabled.Disabled(working) else KitEnabled.Enabled
    val key = remember(state.inputRevision) { TextFieldState() }
    val code = remember(state.inputRevision) { TextFieldState() }
    var missingKey by remember(state.inputRevision) { mutableStateOf(false) }
    var missingCode by remember(state.inputRevision) { mutableStateOf(false) }
    DisposableEffect(key, code) {
        onDispose {
            key.edit { replace(0, length, "") }
            code.edit { replace(0, length, "") }
        }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.m40AuthTitle, provider),
                subtitle = state.place?.name,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.m40AuthBack),
                onExit = actions::dismiss,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.busy,
        loadingLabel = working,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.failure?.let { failure ->
                KitErrorNotice(
                    message =
                        stringResource(
                            if (failure is Failure.SecretUnavailable) Res.string.m40AuthReenter
                            else Res.string.m40AuthFailed
                        ),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.m40AuthDetails),
                    copyLabel = stringResource(Res.string.m40AuthCopyDetails),
                )
            }
            when (state.step) {
                PlanSignInStep.Places ->
                    if (state.choices.isEmpty() && !state.busy) {
                        KitStateView(
                            KitStateKind.Missing,
                            stringResource(Res.string.m40AuthNoPlace),
                            body = stringResource(Res.string.m40AuthNoPlaceBody),
                        )
                    } else
                        KitRowGroup(label = stringResource(Res.string.m40AuthPlaces)) {
                            for (place in state.choices) KitRow(
                                title =
                                    if (place.phone) stringResource(Res.string.m40AuthPhone)
                                    else place.name,
                                supporting =
                                    stringResource(
                                        if (place.inApp) Res.string.m40AuthBest
                                        else if (place.phone) Res.string.m40AuthPhoneHint
                                        else Res.string.m40AuthServer
                                    ),
                                supportingMaxLines = 2,
                                trailing = KitRowTrailing.Chevron,
                                enabled = !state.busy,
                                disabledReason = working,
                                onClick = { actions.choosePlace(place.id) },
                            )
                        }
                PlanSignInStep.Methods ->
                    KitRowGroup(label = stringResource(Res.string.m40AuthMethods)) {
                        for (integration in state.integrations) for (method in
                            integration.methods) KitRow(
                            title = method.label,
                            supporting =
                                stringResource(
                                    when {
                                        method.type == "key" -> Res.string.m40AuthKeyHelp
                                        method.type == "command" -> Res.string.m40AuthCommandHint
                                        method.label.lowercase().contains("browser") ->
                                            Res.string.m40AuthBrowserHint
                                        method.label.lowercase().contains("headless") ->
                                            Res.string.m40AuthDeviceHint
                                        else -> Res.string.m40AuthAccountHint
                                    }
                                ),
                            supportingMaxLines = 3,
                            enabled = !state.busy,
                            disabledReason = working,
                            trailing = KitRowTrailing.Chevron,
                            onClick = {
                                actions.chooseMethod(integration.id, method.id, method.type)
                            },
                        )
                    }
                PlanSignInStep.Key -> {
                    KitField(
                        key,
                        stringResource(Res.string.m40AuthKey),
                        kind = KitFieldKind.Secret,
                        helper = stringResource(Res.string.m40AuthKeyHelp),
                        error =
                            if (missingKey && key.text.isBlank())
                                stringResource(Res.string.m40AuthEnterValue)
                            else null,
                        enabled = enabled,
                    )
                    KitButton(
                        stringResource(Res.string.m40AuthSaveKey),
                        {
                            if (key.text.isBlank()) missingKey = true
                            else actions.submitKey(key.text.toString())
                        },
                        working = state.busy,
                        enabled = enabled,
                    )
                }
                PlanSignInStep.Form -> OAuthForm(state, actions, enabled)
                PlanSignInStep.OAuth -> {
                    val command = state.method?.type == "command"
                    KitNotice(
                        stringResource(
                            if (command) Res.string.m40AuthCommandWaiting
                            else Res.string.m40AuthWaiting
                        )
                    )
                    state.deviceCode?.let { device ->
                        KitNotice(stringResource(Res.string.m40AuthDeviceCode, device))
                        KitButton(
                            stringResource(Res.string.m40AuthCopyCode),
                            actions::copyCode,
                            enabled = enabled,
                            role = KitButtonRole.Secondary,
                        )
                        if (state.codeCopied) KitNotice(stringResource(Res.string.m40AuthCopied))
                    }
                    if (!state.launch?.url.isNullOrBlank())
                        KitButton(
                            stringResource(Res.string.m40AuthOpenPage),
                            actions::openPage,
                            enabled = enabled,
                        )
                    state.askedHost?.let { host ->
                        KitNotice(stringResource(Res.string.m40AuthAskPage, host))
                        KitButton(
                            stringResource(Res.string.m40AuthOpenBrowser),
                            actions::confirmPage,
                            enabled = enabled,
                        )
                    }
                    if (!command && state.launch?.mode == IntegrationAuthMode.Code) {
                        KitField(
                            code,
                            stringResource(Res.string.m40AuthCode),
                            kind = KitFieldKind.Secret,
                            error =
                                if (missingCode && code.text.isBlank())
                                    stringResource(Res.string.m40AuthEnterValue)
                                else null,
                            enabled = enabled,
                        )
                        KitButton(
                            stringResource(Res.string.m40AuthFinish),
                            {
                                if (code.text.isBlank()) missingCode = true
                                else actions.finish(code.text.toString())
                            },
                            enabled = enabled,
                            working = state.busy,
                        )
                    }
                    KitButton(
                        stringResource(Res.string.m40AuthCheck),
                        actions::check,
                        role = KitButtonRole.Secondary,
                        enabled = enabled,
                        working = state.busy,
                    )
                }
                PlanSignInStep.Complete -> {
                    KitStateView(
                        KitStateKind.Finished,
                        stringResource(Res.string.m40AuthComplete),
                        body = stringResource(Res.string.m40AuthCompleteBody),
                    )
                    KitButton(stringResource(Res.string.m40AuthDone), actions::dismiss)
                }
                PlanSignInStep.Unavailable ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.m40AuthUnavailable),
                        body = stringResource(Res.string.m40AuthUnavailableBody),
                    )
            }
        }
    }
}

@Composable
private fun OAuthForm(
    state: PlanAuthenticationUi,
    actions: PlanSignInActions,
    enabled: KitEnabled,
) {
    val method = state.method ?: return
    val fields =
        remember(state.inputRevision, method) {
            method.prompts
                .mapNotNull { prompt ->
                    prompt.text("key")?.let {
                        it to TextFieldState(prompt.text("default").orEmpty())
                    }
                }
                .toMap()
        }
    DisposableEffect(fields) {
        onDispose { fields.values.forEach { it.edit { replace(0, length, "") } } }
    }
    val answers = fields.mapValues { it.value.text.toString() }
    val conditions = method.presetAnswers + answers
    for (prompt in method.prompts.filter { planPromptVisible(it, conditions) }) {
        val id = prompt.text("key") ?: continue
        val field = fields[id] ?: continue
        val label = prompt.text("message")?.takeIf { it.isNotBlank() } ?: id
        val missing = id in state.missingFields && field.text.isBlank()
        if (prompt.text("type") == "select") {
            val choices =
                (prompt["options"] as? JsonArray)
                    .orEmpty()
                    .filterIsInstance<JsonObject>()
                    .mapNotNull { option ->
                        option.text("value")?.let { value ->
                            KitChoice(
                                value,
                                option.text("label")?.takeIf { it.isNotBlank() } ?: value,
                                enabled = enabled,
                            )
                        }
                    }
            KitText(label, role = TypeRole.Label)
            KitChoiceList(
                choices,
                field.text.toString().takeIf { it.isNotEmpty() },
                semanticsLabel = label,
                empty = { KitNotice(stringResource(Res.string.m40AuthUnavailableBody)) },
                onSelect = { value -> field.edit { replace(0, length, value) } },
            )
            if (missing) KitNotice(stringResource(Res.string.m40AuthSelectValue))
        } else
            KitField(
                field,
                label,
                placeholder = prompt.text("placeholder"),
                error = if (missing) stringResource(Res.string.m40AuthEnterValue) else null,
                enabled = enabled,
            )
    }
    KitButton(
        stringResource(Res.string.m40AuthContinue),
        { actions.submitForm(fields.mapValues { it.value.text.toString() }.toImmutableMap()) },
        enabled = enabled,
        working = state.busy,
    )
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content
