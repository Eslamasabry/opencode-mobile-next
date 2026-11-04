package io.github.eslamasabry.oc.feature.integrations.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogEntry
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogNotice
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogState
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Consent, catalogue and review are visible states of one native screen, arranged entirely from kit
 * parts.
 */
@Composable
public fun McpCatalogScreen(state: McpCatalogState, actions: McpCatalogActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.mcpNativeTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.mcpNativeBack),
                onExit = actions::back,
            )
        },
        loading = state.loading,
        loadingLabel = stringResource(Res.string.mcpNativeRefresh),
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            state.notice?.let { KitText(notice(it)) }
            if (state.uncertain)
                KitButton(
                    stringResource(Res.string.mcpNativeCheck),
                    actions::check,
                    working = state.adding,
                )
            val selected = state.selected
            when {
                !state.optedIn -> {
                    KitText(stringResource(Res.string.mcpNativeConsent))
                    KitButton(
                        stringResource(Res.string.mcpNativeTurnOn),
                        { actions.consent(true) },
                        working = state.loading,
                    )
                }
                selected != null -> Review(selected, state, actions)
                else -> {
                    KitSearchField(
                        label = stringResource(Res.string.mcpNativeSearch),
                        onSearch = actions::search,
                        onSubmit = actions::search,
                    )
                    KitButton(
                        stringResource(Res.string.mcpNativeRefresh),
                        actions::refresh,
                        role = KitButtonRole.Secondary,
                        working = state.loading,
                    )
                    if (!state.canAdd) KitText(stringResource(Res.string.mcpNativeUnsupported))
                    if (state.entries.isEmpty() && !state.loading)
                        KitText(stringResource(Res.string.mcpNativeEmpty))
                    KitRowGroup {
                        for (entry in state.entries) {
                            KitRow(
                                title = entry.title.ifEmpty { entry.serverName },
                                supporting = entry.description.ifEmpty { entry.id },
                                supportingMaxLines = 3,
                                selected = entry.serverName in state.installed,
                                onClick = { actions.select(entry.id) },
                            )
                        }
                    }
                    KitButton(
                        stringResource(Res.string.mcpNativeTurnOff),
                        { actions.consent(false) },
                        role = KitButtonRole.Tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Review(entry: McpCatalogEntry, state: McpCatalogState, actions: McpCatalogActions) {
    val draft = entry.draft
    KitText(stringResource(Res.string.mcpNativeReview))
    KitText(entry.title.ifEmpty { entry.serverName })
    if (entry.description.isNotEmpty()) KitText(entry.description)
    if (draft == null) {
        KitText(stringResource(Res.string.mcpNativeNotAddable))
        return
    }
    val name = rememberTextFieldState(entry.serverName)
    KitField(name, stringResource(Res.string.mcpNativeName), maxLength = 64)
    if (draft.url != null)
        KitRow(
            title = stringResource(Res.string.mcpNativeAddress),
            supporting = draft.url,
            supportingMaxLines = 4,
        )
    if (draft.command.isNotEmpty())
        KitRow(
            title = stringResource(Res.string.mcpNativeCommand),
            supporting = draft.command.joinToString(" "),
            supportingMaxLines = 5,
        )
    val headers = remember(entry.id) { draft.headers.keys.associateWith { TextFieldState() } }
    val environment =
        remember(entry.id) { draft.environment.keys.associateWith { TextFieldState() } }
    for ((key, field) in headers) KitField(field, key, kind = KitFieldKind.Secret, maxLength = 4096)
    for ((key, field) in environment) KitField(
        field,
        key,
        kind = KitFieldKind.Secret,
        maxLength = 4096,
    )
    KitText(stringResource(Res.string.mcpNativeRuntimeOnly))
    when {
        entry.requiresArguments -> KitText(stringResource(Res.string.mcpNativeNeedsSettings))
        !state.canAdd -> KitText(stringResource(Res.string.mcpNativeUnsupported))
        state.uncertain -> Unit
        else ->
            KitButton(
                stringResource(Res.string.mcpNativeAdd),
                {
                    actions.add(
                        name.text.toString(),
                        headers.mapValues { it.value.text.toString() },
                        environment.mapValues { it.value.text.toString() },
                    )
                },
                working = state.adding,
            )
    }
}

@Composable
private fun notice(value: McpCatalogNotice): String =
    stringResource(
        when (value) {
            McpCatalogNotice.LoadFailed -> Res.string.mcpNativeLoadFailed
            McpCatalogNotice.SaveFailed -> Res.string.mcpNativeSaveFailed
            McpCatalogNotice.Unsupported -> Res.string.mcpNativeUnsupported
            McpCatalogNotice.SourceChanged -> Res.string.mcpNativeSourceChanged
            McpCatalogNotice.Conflict -> Res.string.mcpNativeConflict
            McpCatalogNotice.Added -> Res.string.mcpNativeAdded
            McpCatalogNotice.Uncertain -> Res.string.mcpNativeAddUncertain
            McpCatalogNotice.NeedsSettings -> Res.string.mcpNativeNeedsSettings
        }
    )
