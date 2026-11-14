package io.github.eslamasabry.oc.feature.integrations.catalog

import io.github.eslamasabry.oc.ui.kit.frame.kitPageScrollState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.KitAction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogEntry
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogNotice
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogState
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Consent, catalogue and review are visible states of one native screen, arranged entirely from kit
 * parts.
 */
@Composable
public fun McpCatalogScreen(ui: McpCatalogUi, actions: McpCatalogActions) {
    val state = ui.catalog
    var menu by remember { mutableStateOf(false) }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.mcpNativeTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.mcpNativeBack),
                onExit = actions::back,
                more =
                    if (!ui.canForget) null
                    else
                        { _ ->
                            KitMenu(
                                expanded = menu,
                                items =
                                    listOf(
                                        KitMenuItem(
                                            stringResource(Res.string.mcpCatalogForget),
                                            {
                                                menu = false
                                                actions.forget()
                                            },
                                            KitIcons.privacy,
                                        )
                                    ),
                                onDismissRequest = { menu = false },
                                anchor = {
                                    KitIconButton(
                                        KitIcons.more,
                                        stringResource(Res.string.kitTopBarMore),
                                        { menu = true },
                                    )
                                },
                            )
                        },
            )
        },
        loading = state.loading,
        loadingLabel = stringResource(Res.string.mcpNativeRefresh),
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        val gutter = LocalKitTokens.current.gutter.dp
        if (!state.optedIn) {
            // Flutter's consent page (lib/ui/screens/mcp_catalog_screen.dart:427-445): one centred state
            // with the question, what is sent, and the one button.
            Column(
                Modifier.fillMaxSize().padding(horizontal = gutter, vertical = Space.S3.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                Notices(ui, state, actions)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    KitStateView(
                        kind = KitStateKind.Empty,
                        title = stringResource(Res.string.mcpCatalogConsentTitle),
                        body = stringResource(Res.string.mcpCatalogConsentBody),
                        icon = KitIcons.extensions,
                        primary =
                            KitAction(
                                stringResource(Res.string.mcpCatalogConsentLoad),
                                { actions.consent(true) },
                                working = state.loading,
                            ),
                    )
                }
            }
        } else Column(
            Modifier.fillMaxSize()
                .verticalScroll(kitPageScrollState())
                .padding(horizontal = gutter, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            Notices(ui, state, actions)
            val selected = state.selected
            when {
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
                    if (ui.rows.isEmpty() && !state.loading)
                        KitText(stringResource(Res.string.mcpNativeEmpty))
                    KitRowGroup {
                        for (row in ui.rows) {
                            val facts = row.facts.map { fact(it) }.joinToString(" · ")
                            KitRow(
                                title = row.title,
                                supporting =
                                    listOf(row.description, facts)
                                        .filter { it.isNotEmpty() }
                                        .joinToString("\n")
                                        .ifEmpty { row.id },
                                supportingMaxLines = 4,
                                selected = row.installed,
                                unavailableReason = row.blocked?.let { blocked(it) },
                                onClick = { actions.select(row.id) },
                            )
                        }
                    }
                    if (ui.priceNote) KitGroupNote(stringResource(Res.string.mcpCatalogPriceNote))
                }
            }
        }
    }
}

@Composable
private fun Notices(ui: McpCatalogUi, state: McpCatalogState, actions: McpCatalogActions) {
    if (ui.forgetFailed) KitText(stringResource(Res.string.mcpCatalogForgetFailed))
    else state.notice?.let { KitText(notice(it)) }
    if (state.uncertain)
        KitButton(stringResource(Res.string.mcpNativeCheck), actions::check, working = state.adding)
}

@Composable
private fun fact(value: McpCatalogFact): String =
    when (value) {
        is McpCatalogFact.HostedBy -> stringResource(Res.string.mcpCatalogHostedBy, value.host)
        McpCatalogFact.NeedsNode -> stringResource(Res.string.mcpCatalogNeedsNode)
        McpCatalogFact.NeedsPython -> stringResource(Res.string.mcpCatalogNeedsPython)
        McpCatalogFact.NeedsKey -> stringResource(Res.string.mcpCatalogNeedsKey)
        McpCatalogFact.NeedsSettings -> stringResource(Res.string.mcpCatalogNeedsSettings)
    }

@Composable
private fun blocked(value: McpCatalogBlocked): String =
    stringResource(
        when (value) {
            McpCatalogBlocked.Docker -> Res.string.mcpCatalogNeedsDocker
            McpCatalogBlocked.NoEndpoint -> Res.string.mcpCatalogNoEndpoint
        }
    )

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
