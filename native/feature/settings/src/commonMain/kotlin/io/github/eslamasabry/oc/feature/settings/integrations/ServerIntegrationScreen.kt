package io.github.eslamasabry.oc.feature.settings.integrations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kitchat.code.*
import io.github.eslamasabry.oc.ui.kitchat.markdown.*
import io.github.eslamasabry.oc.ui.routes.ServerIntegrationPage
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ServerIntegrationScreen(state: ServerIntegrationUi, actions: ServerIntegrationActions) {
    val title = integrationTitle(state.page)
    var query by remember(state.page) { mutableStateOf("") }
    val search = remember(state.page) { TextFieldState() }
    LaunchedEffect(search) { snapshotFlow { search.text.toString() }.collect { query = it.trim() } }
    var chooseModel by remember { mutableStateOf(false) }
    val builtins = state.items.filter { it.kind == ServerIntegrationKind.Plugin && it.builtIn }
    var expanded by
        remember(builtins) {
            mutableStateOf(builtins.any { it.status == ServerIntegrationState.Failed })
        }
    KitScreen(
        topBar = {
            KitTopBar(
                title,
                subtitle = state.server,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.s40iBack),
                onExit = actions::close,
                actions =
                    listOf(KitLineAction(stringResource(Res.string.s40iRefresh), actions::refresh)),
            )
        },
        loading = state.loading,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            state.failure?.let {
                KitErrorNotice(
                    stringResource(Res.string.s40iFailed),
                    failure = it,
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::refresh),
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            }
            if (!state.available && !state.loading)
                KitNotice(stringResource(Res.string.s40iUnavailable))
            if (state.page == ServerIntegrationPage.Setup)
                KitNotice(stringResource(Res.string.s40iReviewOnly))
            if (state.page == ServerIntegrationPage.Host) {
                KitNotice(stringResource(Res.string.s40iHostIntro))
                KitButton(stringResource(Res.string.s40iDocs), actions::docs)
            }
            if (state.page == ServerIntegrationPage.Tools && state.catalog) {
                listOf(
                        ServerIntegrationPage.Mcp,
                        ServerIntegrationPage.Commands,
                        ServerIntegrationPage.Skills,
                        ServerIntegrationPage.References,
                        ServerIntegrationPage.Plugins,
                    )
                    .forEach { page ->
                        KitRow(
                            integrationTitle(page),
                            trailing = KitRowTrailing.Chevron,
                            onClick = { actions.go(page) },
                        )
                    }
                if (!state.toolsListed) KitNotice(stringResource(Res.string.s40iToolsNotListed))
                else
                    KitRow(
                        stringResource(Res.string.s40iModel),
                        supporting =
                            state.models.firstOrNull { it.key == state.model }?.label
                                ?: stringResource(Res.string.s40iChooseModel),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { chooseModel = true },
                    )
            }
            if (state.items.size >= 8 || query.isNotBlank())
                KitField(
                    search,
                    label = stringResource(Res.string.s40iSearch),
                    kind = KitFieldKind.Text,
                )
            if (state.mayAdd) KitButton(stringResource(Res.string.s40iAddMcp), actions::beginAdd)
            val shown =
                state.items.filter {
                    query.isBlank() ||
                        (it.title + " " + it.description.orEmpty()).contains(query, true)
                }
            if (
                shown.isEmpty() &&
                    !state.loading &&
                    state.available &&
                    !(state.page == ServerIntegrationPage.Tools && !state.toolsListed)
            )
                KitNotice(
                    stringResource(
                        if (query.isBlank()) Res.string.s40iEmpty else Res.string.s40iNoMatch
                    )
                )
            KitRowGroup {
                shown.filter { it !in builtins }.forEach { item -> IntegrationRow(item, actions) }
                if (builtins.isNotEmpty())
                    KitExpandRow(
                        stringResource(Res.string.s40iBuiltin),
                        expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        shown.filter { it in builtins }.forEach { IntegrationRow(it, actions) }
                    }
            }
        }
        KitSheet(
            chooseModel,
            { chooseModel = false },
            stringResource(Res.string.s40iChooseModel),
            closeLabel = stringResource(Res.string.kitSheetClose),
        ) {
            state.models.forEach { model ->
                KitRow(
                    model.label,
                    selected = model.key == state.model,
                    onClick = {
                        chooseModel = false
                        actions.chooseModel(model.key)
                    },
                )
            }
        }
        val selected = state.selected
        val label = remember(selected?.key) { TextFieldState() }
        val commandArgs = remember(selected?.key) { TextFieldState() }
        var destination by
            remember(selected?.key) { mutableStateOf(state.sessions.firstOrNull()?.key.orEmpty()) }
        var raw by remember(selected?.key) { mutableStateOf(false) }
        KitSheet(
            selected != null,
            actions::dismiss,
            selected?.let { integrationItemTitle(it) } ?: title,
            closeLabel = stringResource(Res.string.kitSheetClose),
            height = KitSheetHeight.Full,
            dismissible = !state.running,
            dirty = selected?.mayRun == true && commandArgs.text.isNotBlank(),
        ) {
            selected?.let { item ->
                if (item.kind != ServerIntegrationKind.Plugin)
                    item.description?.takeIf { it.isNotBlank() }?.let { KitText(it) }
                if (
                    item.kind !in
                        setOf(
                            ServerIntegrationKind.Command,
                            ServerIntegrationKind.Skill,
                            ServerIntegrationKind.Reference,
                        )
                )
                    KitText(integrationState(item.status))
                item.content?.let { content ->
                    KitButton(
                        stringResource(if (raw) Res.string.s40iRendered else Res.string.s40iRaw),
                        { raw = !raw },
                    )
                    if (raw)
                        KitCodeBlock(
                            content,
                            KitCodeKind.Code,
                            copyLabel = stringResource(Res.string.s40iCopy),
                        )
                    else
                        KitMarkdown(
                            remember(content) { KitMdSource.blocks(item.key, content, false) },
                            KitMarkdownActions(
                                actions::openLink,
                                copyCodeLabel = stringResource(Res.string.s40iCopy),
                            ),
                        )
                }
                if (item.mayRun) {
                    KitField(
                        commandArgs,
                        label = stringResource(Res.string.s40iCommandArguments),
                        enabled =
                            if (state.running)
                                KitEnabled.Disabled(stringResource(Res.string.s40iRunning))
                            else KitEnabled.Enabled,
                    )
                    KitRow(
                        stringResource(Res.string.s40iNewConversation),
                        selected = destination.isEmpty(),
                        onClick = { if (!state.running) destination = "" },
                    )
                    state.sessions.forEach { session ->
                        KitRow(
                            session.label.ifBlank { stringResource(Res.string.s40iUntitled) },
                            selected = destination == session.key,
                            onClick = { if (!state.running) destination = session.key },
                        )
                    }
                    if (state.moreSessions)
                        KitButton(stringResource(Res.string.s40iOlder), actions::moreSessions)
                    if (state.commandBlocked) KitNotice(stringResource(Res.string.s40iUncertain))
                    KitButton(
                        stringResource(Res.string.s40iRunCommand, item.title),
                        { actions.runCommand(commandArgs.text.toString(), destination) },
                        working = state.running,
                        enabled =
                            if (state.commandBlocked)
                                KitEnabled.Disabled(stringResource(Res.string.s40iUncertain))
                            else KitEnabled.Enabled,
                    )
                }
                item.details?.let {
                    KitDetailsFold(
                        stringResource(Res.string.kitDetails),
                        stringResource(Res.string.kitCopyDetails),
                        text = it,
                        initiallyExpanded = item.kind == ServerIntegrationKind.HostCommand,
                    )
                }
                if (item.kind == ServerIntegrationKind.HostCommand || item.details != null)
                    KitButton(
                        stringResource(
                            if (state.copied) Res.string.s40iCopied else Res.string.s40iCopy
                        ),
                        actions::copy,
                    )
                if (item.mayConnect)
                    KitButton(
                        stringResource(
                            if (item.kind == ServerIntegrationKind.Provider) Res.string.s40iSignIn
                            else Res.string.s40iConnect
                        ),
                        { actions.connect(item.key) },
                    )
                if (item.mayActivate)
                    KitButton(
                        stringResource(Res.string.s40iUseCredential),
                        { actions.activate(item.key) },
                    )
                if (item.mayRename) {
                    KitField(label, label = stringResource(Res.string.s40iLabel))
                    KitButton(
                        stringResource(Res.string.s40iSaveLabel),
                        { actions.rename(item.key, label.text.toString()) },
                    )
                }
                if (item.mayDisconnect)
                    KitButton(
                        stringResource(
                            if (item.kind == ServerIntegrationKind.Credential)
                                Res.string.s40iRemoveCredential
                            else Res.string.s40iDisconnect
                        ),
                        { actions.askDisconnect(item.key) },
                        role = KitButtonRole.Tertiary,
                        destructive = true,
                    )
            }
        }
        KitConfirm(
            state.question?.let {
                KitConfirmSpec(
                    stringResource(Res.string.s40iDisconnectQuestion),
                    stringResource(Res.string.s40iConfirm),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.s40iDisconnectBody, it.title),
                )
            },
            actions::disconnect,
            actions::dismiss,
        )
        KitConfirm(
            state.linkHost?.let {
                KitConfirmSpec(
                    stringResource(Res.string.s40iOpenPageQuestion),
                    stringResource(Res.string.s40iOpenPage),
                    body = it,
                )
            },
            actions::confirmLink,
            actions::dismiss,
        )
        AddMcpSheet(state, actions)
    }
}

@Composable
private fun IntegrationRow(item: ServerIntegrationItem, actions: ServerIntegrationActions) {
    KitRow(
        integrationItemTitle(item),
        supporting =
            if (
                item.kind == ServerIntegrationKind.CallableTool ||
                    item.kind == ServerIntegrationKind.Suggestion ||
                    item.kind == ServerIntegrationKind.Source
            )
                item.description
            else integrationState(item.status),
        supportingMaxLines = 2,
        trailing = KitRowTrailing.Chevron,
        onClick = { actions.select(item.key) },
    )
}

@Composable
private fun AddMcpSheet(state: ServerIntegrationUi, actions: ServerIntegrationActions) {
    val name = remember(state.inputRevision) { TextFieldState() }
    val url = remember(state.inputRevision) { TextFieldState() }
    val executable = remember(state.inputRevision) { TextFieldState() }
    val arguments = remember(state.inputRevision) { TextFieldState() }
    KitSheet(
        state.add,
        actions::dismiss,
        stringResource(Res.string.s40iAddMcp),
        closeLabel = stringResource(Res.string.kitSheetClose),
        height = KitSheetHeight.Full,
    ) {
        KitNotice(stringResource(Res.string.s40iMcpAddHint))
        KitField(name, label = stringResource(Res.string.s40iName))
        KitField(
            url,
            label = stringResource(Res.string.s40iUrl),
            helper = stringResource(Res.string.s40iRemoteHint),
        )
        KitField(
            executable,
            label = stringResource(Res.string.s40iExecutable),
            helper = stringResource(Res.string.s40iLocalHint),
        )
        KitField(
            arguments,
            label = stringResource(Res.string.s40iArguments),
            kind = KitFieldKind.Multiline,
        )
        KitButton(
            stringResource(Res.string.s40iAddMcp),
            {
                actions.add(
                    name.text.toString(),
                    url.text.toString(),
                    executable.text.toString(),
                    arguments.text.toString(),
                )
            },
            working = state.loading,
        )
    }
}

@Composable
private fun integrationTitle(page: ServerIntegrationPage): String =
    stringResource(
        when (page) {
            ServerIntegrationPage.Providers -> Res.string.s40iProviders
            ServerIntegrationPage.Tools -> Res.string.s40iTools
            ServerIntegrationPage.Commands -> Res.string.s40iCommands
            ServerIntegrationPage.Skills -> Res.string.s40iSkills
            ServerIntegrationPage.References -> Res.string.s40iReferences
            ServerIntegrationPage.Mcp -> Res.string.s40iMcp
            ServerIntegrationPage.Plugins -> Res.string.s40iPlugins
            ServerIntegrationPage.Setup -> Res.string.s40iSetup
            ServerIntegrationPage.Host -> Res.string.s40iHost
            ServerIntegrationPage.Permissions -> Res.string.s40iPermissions
        }
    )

@Composable
private fun integrationState(state: ServerIntegrationState): String =
    stringResource(
        when (state) {
            ServerIntegrationState.Connected -> Res.string.s40iConnected
            ServerIntegrationState.Available -> Res.string.s40iAvailable
            ServerIntegrationState.Disconnected -> Res.string.s40iDisconnected
            ServerIntegrationState.Failed -> Res.string.s40iStatusFailed
            ServerIntegrationState.Pending -> Res.string.s40iPending
            ServerIntegrationState.Disabled -> Res.string.s40iDisabled
            ServerIntegrationState.Builtin -> Res.string.s40iBuiltin
            ServerIntegrationState.Added -> Res.string.s40iAdded
            ServerIntegrationState.Registered -> Res.string.s40iRegistered
            ServerIntegrationState.Unknown -> Res.string.s40iUnknown
        }
    )

@Composable
private fun integrationItemTitle(item: ServerIntegrationItem): String =
    when {
        item.kind == ServerIntegrationKind.HostCommand ->
            stringResource(
                if (item.key == "install") Res.string.s40iInstall else Res.string.s40iUsb
            )
        item.kind == ServerIntegrationKind.Source ->
            stringResource(Res.string.s40iSource, item.title)
        item.kind == ServerIntegrationKind.Suggestion ->
            stringResource(
                when {
                    item.key.startsWith("suggestion:signin:") -> Res.string.s40iSignInTool
                    item.key.startsWith("suggestion:fix:") -> Res.string.s40iFixTool
                    else -> Res.string.s40iAddTools
                },
                item.title,
            )
        item.title == "?" -> stringResource(Res.string.s40iUnnamed)
        else -> item.title
    }
