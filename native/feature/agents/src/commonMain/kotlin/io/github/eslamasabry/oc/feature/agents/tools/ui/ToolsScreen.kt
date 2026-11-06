package io.github.eslamasabry.oc.feature.agents.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.feature.agents.tools.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The live server library. All controls are visible; no instruction text or loader error is
 * rendered.
 */
@Composable
public fun ToolsScreen(state: ToolsUi, actions: ToolsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val name = remember { TextFieldState() }
    val target = remember { TextFieldState() }
    var local by remember { mutableStateOf(false) }
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.protoToolsTitle),
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
                stringResource(Res.string.protoToolsRefresh),
                actions::refresh,
                working = state.loading,
                role = KitButtonRole.Secondary,
                modifier = gutter,
            )
            KitButton(
                stringResource(Res.string.protoToolsProviders),
                actions::providers,
                role = KitButtonRole.Secondary,
                modifier = gutter,
            )
            var pluginDetail by remember { mutableStateOf<LibraryPlugin?>(null) }
            state.issue?.let { KitNotice(issueWords(it)) }
            if (!state.catalogSupported && !state.loading)
                KitNotice(stringResource(Res.string.protoToolsUnavailable))
            if (state.toolsSupported) {
                KitRowGroup(label = stringResource(Res.string.nativeToolsCoding)) {
                    if ("tools" in state.failedSections)
                        KitNotice(stringResource(Res.string.protoToolsLoadFailed))
                    if (state.models.isEmpty() && !state.loading)
                        KitNotice(stringResource(Res.string.nativeToolsChooseModel))
                    for (model in state.models) KitRow(
                        title = model.modelId,
                        supporting = model.providerId,
                        selected = state.model == model,
                        onClick = { actions.selectModel(model) },
                    )
                    for (tool in state.tools) KitRow(
                        title = tool.id,
                        supporting = toolSummary(tool.description),
                        supportingMaxLines = 3,
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.selectTool(tool.id) },
                    )
                    state.tool?.let { tool ->
                        KitRow(
                            title = tool.id,
                            supporting = tool.description,
                            supportingMaxLines = 20,
                        )
                        val parameters = toolParameters(tool.parameters)
                        if (parameters.isEmpty())
                            KitRow(stringResource(Res.string.nativeToolsNoParameters))
                        tool.parameters?.let { schema ->
                            var copied by remember(tool.id) { mutableStateOf(false) }
                            @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
                            KitButton(
                                stringResource(Res.string.e7LibraryCopyParameterSchema),
                                {
                                    clipboard.setText(
                                        AnnotatedString(KitRedact.text(schema.toString()))
                                    )
                                    copied = true
                                },
                                role = KitButtonRole.Secondary,
                                modifier = gutter,
                            )
                            if (copied) KitNotice(stringResource(Res.string.kitCopied))
                        }
                        for (parameter in parameters) KitRow(
                            title = parameter.name,
                            supporting =
                                listOfNotNull(
                                        stringResource(
                                            if (parameter.required) Res.string.nativeToolsRequired
                                            else Res.string.nativeToolsOptional
                                        ),
                                        parameter.type,
                                        parameter.description,
                                    )
                                    .joinToString(" · "),
                            supportingMaxLines = 6,
                        )
                    }
                }
            }
            if (state.pluginSupported)
                KitRowGroup(label = stringResource(Res.string.protoToolsPlugins)) {
                    if ("plugins" in state.failedSections)
                        KitNotice(stringResource(Res.string.protoToolsLoadFailed))
                    if (
                        state.plugins.isEmpty() &&
                            "plugins" !in state.failedSections &&
                            !state.loading
                    )
                        KitRow(stringResource(Res.string.protoToolsEmpty))
                    for (plugin in state.plugins) KitRow(
                        trailing = KitRowTrailing.Chevron,
                        onClick = { pluginDetail = plugin },
                        title = plugin.id ?: stringResource(Res.string.protoToolsUnnamedPlugin),
                        supporting =
                            listOf(
                                    pluginStatus(plugin.status),
                                    pluginSource(plugin.source),
                                    plugin.packageName,
                                )
                                .filterNotNull()
                                .joinToString(" · "),
                        supportingMaxLines = 3,
                    )
                }
            PluginDetailSheet(pluginDetail) { pluginDetail = null }
            if (state.catalogSupported) {
                KitRowGroup(label = stringResource(Res.string.protoToolsSkills)) {
                    if (!state.hasSession)
                        KitNotice(stringResource(Res.string.protoToolsSkillNeedsChat))
                    if ("skills" in state.failedSections)
                        KitNotice(stringResource(Res.string.protoToolsLoadFailed))
                    if (
                        state.skills.isEmpty() &&
                            "skills" !in state.failedSections &&
                            !state.loading
                    )
                        KitRow(stringResource(Res.string.protoToolsEmpty))
                    for (skill in state.skills) {
                        val id = skill.id
                        KitRow(
                            title = skill.name,
                            onClick =
                                if (state.hasSession && id != null && !state.working)
                                    ({ actions.selectSkill(id) })
                                else null,
                        )
                    }
                    state.selected?.let { skill ->
                        KitRow(
                            title = skill.name,
                            supporting = stringResource(Res.string.protoToolsSkillReview),
                        )
                        KitSwitchRow(
                            title = stringResource(Res.string.protoToolsResume),
                            checked = state.resume,
                            onCheckedChange = actions::resume,
                        )
                        KitButton(
                            stringResource(Res.string.protoToolsActivate),
                            actions::activate,
                            working = state.working,
                            modifier = gutter,
                        )
                    }
                }
                KitRowGroup(label = stringResource(Res.string.protoToolsMcp)) {
                    if ("mcp" in state.failedSections)
                        KitNotice(stringResource(Res.string.protoToolsLoadFailed))
                    if (state.servers.isEmpty() && "mcp" !in state.failedSections && !state.loading)
                        KitRow(stringResource(Res.string.protoToolsEmpty))
                    for (server in state.servers) {
                        KitRow(title = server.name, supporting = mcpStatus(server.status))
                        if (server.status == "connected")
                            KitButton(
                                stringResource(Res.string.protoToolsDisconnect, server.name),
                                { actions.disconnect(server.name) },
                                working = state.working,
                                role = KitButtonRole.Tertiary,
                                modifier = gutter,
                            )
                        else
                            KitButton(
                                stringResource(Res.string.protoToolsConnect, server.name),
                                { actions.connect(server.name) },
                                working = state.working,
                                role = KitButtonRole.Tertiary,
                                modifier = gutter,
                            )
                    }
                }
                if (state.canAdd)
                    KitRowGroup(label = stringResource(Res.string.protoToolsAddMcp)) {
                        KitNotice(stringResource(Res.string.protoToolsRuntimeScope))
                        KitField(name, stringResource(Res.string.protoToolsName), modifier = gutter, maxLength = 512)
                        KitSwitchRow(
                            title = stringResource(Res.string.protoToolsLocal),
                            checked = local,
                            onCheckedChange = { local = it },
                        )
                        KitField(
                            target,
                            stringResource(
                                if (local) Res.string.protoToolsCommand
                                else Res.string.protoToolsUrl
                            ),
                            kind = if (local) KitFieldKind.Multiline else KitFieldKind.Text,
                            modifier = gutter,
                            maxLength = 8192,
                        )
                        KitButton(
                            stringResource(Res.string.protoToolsAdd),
                            { actions.add(name.text.toString(), target.text.toString(), local) },
                            working = state.working,
                            modifier = gutter,
                        )
                    }
            }
        }
    }
}

@Composable
private fun issueWords(issue: ToolsIssue): String =
    stringResource(
        when (issue) {
            ToolsIssue.Load -> Res.string.protoToolsLoadFailed
            ToolsIssue.Unavailable -> Res.string.protoToolsUnavailable
            ToolsIssue.Changed -> Res.string.protoToolsChanged
            ToolsIssue.InvalidDraft -> Res.string.protoToolsInvalidDraft
            ToolsIssue.Add -> Res.string.protoToolsAddFailed
            ToolsIssue.Disconnect -> Res.string.protoToolsDisconnectFailed
            ToolsIssue.Activate -> Res.string.protoToolsActivateFailed
            ToolsIssue.Staged -> Res.string.protoToolsStaged
            ToolsIssue.Uncertain -> Res.string.protoToolsUncertain
            ToolsIssue.Done -> Res.string.protoToolsDone
        }
    )

@Composable
private fun pluginStatus(status: LibraryPluginStatus): String =
    stringResource(
        when (status) {
            LibraryPluginStatus.Active -> Res.string.protoToolsActive
            LibraryPluginStatus.Failed -> Res.string.protoToolsFailed
            LibraryPluginStatus.Unknown -> Res.string.protoToolsUnknown
        }
    )

@Composable
private fun pluginSource(source: LibraryPluginSource): String =
    stringResource(
        when (source) {
            LibraryPluginSource.Builtin -> Res.string.protoToolsBuiltin
            LibraryPluginSource.Package -> Res.string.protoToolsPackage
            LibraryPluginSource.Local -> Res.string.protoToolsLocalSource
            LibraryPluginSource.Sdk -> Res.string.protoToolsSdk
            LibraryPluginSource.Unknown -> Res.string.protoToolsUnknownSource
        }
    )

@Composable
private fun mcpStatus(status: String): String =
    stringResource(
        when (status) {
            "connected" -> Res.string.protoToolsConnected
            "disabled" -> Res.string.protoToolsDisabled
            "failed" -> Res.string.protoToolsFailed
            "needs_auth",
            "needs_client_registration" -> Res.string.protoToolsSignIn
            "disconnected" -> Res.string.protoToolsDisconnected
            "connecting" -> Res.string.protoToolsConnecting
            else -> Res.string.protoToolsUnknown
        }
    )

/** What a person may want about one server plugin: state, where it comes from, and its id. */
@Composable
private fun PluginDetailSheet(plugin: LibraryPlugin?, onDismiss: () -> Unit) {
    KitSheet(
        visible = plugin != null,
        onDismissRequest = onDismiss,
        title = plugin?.id ?: stringResource(Res.string.pluginsUnnamed),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        plugin?.let { p ->
            KitText(pluginStatus(p.status))
            if (p.status == LibraryPluginStatus.Failed)
                KitText(stringResource(Res.string.pluginsFailureDetail))
            KitText(listOfNotNull(pluginSource(p.source), p.packageName.takeIf { it != p.id }).joinToString(" · "))
            if (p.terminalUi) KitText(stringResource(Res.string.pluginsTerminalUi))
            KitDetailsFold(
                stringResource(Res.string.kitDetails),
                stringResource(Res.string.kitCopyDetails),
                notes =
                    listOf(
                        "${stringResource(Res.string.pluginsDetailsId)}: " +
                            (p.id ?: stringResource(Res.string.pluginsUnnamed))
                    ),
            )
        }
    }
}
