package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.choice.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.nav.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.code.*
import io.github.eslamasabry.oc.ui.kitchat.markdown.*
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*
import org.jetbrains.compose.resources.stringResource

/**
 * Settings and the conversation use this same server-backed library; all controls are kit parts.
 */
@Composable
public fun LibraryScreen(state: LibraryUi, actions: LibraryActions) {
    val tabs = LibraryTab.entries
    val search = rememberTextFieldState()
    val query = search.text.toString().trim()
    fun match(vararg values: String?) =
        query.isEmpty() || values.any { it?.contains(query, ignoreCase = true) == true }
    val inventory = state.inventory
    val selectedSection =
        when (state.tab) {
            LibraryTab.Commands -> "commands"
            LibraryTab.Skills -> "skills"
            LibraryTab.References -> "references"
            LibraryTab.Providers -> "providers"
            LibraryTab.Mcp -> "mcp"
        }
    KitScreen(
        width = KitScreenWidth.List,
        topBar = {
            KitTopBar(
                stringResource(Res.string.nlibTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.nlibBack),
                onExit = actions::back,
                actions =
                    listOf(KitLineAction(stringResource(Res.string.nlibRefresh), actions::refresh)),
            )
        },
        header = {
            KitTabStrip(
                tabs.map { KitTab(tabLabel(it)) },
                tabs.indexOf(state.tab),
                { actions.tab(tabs[it]) },
            )
        },
        search = {
            KitField(
                search,
                stringResource(Res.string.nlibSearch),
                Modifier.padding(horizontal = Space.S4.dp),
            )
        },
        loading = inventory.loading,
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.S4.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            item {
                if (!state.available)
                    KitStateView(KitStateKind.Calm, stringResource(Res.string.nlibUnavailable))
                if (
                    selectedSection in inventory.failedSections ||
                        (state.tab == LibraryTab.Mcp && "resources" in inventory.failedSections)
                ) {
                    KitNotice(
                        stringResource(Res.string.nlibLoadFailed),
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.nlibRefresh),
                                    actions::refresh,
                                )
                            ),
                    )
                }
                (state.notice ?: inventory.notice)?.let { KitNotice(noticeLabel(it)) }
                if (inventory.hasAttemptsAtOtherSource)
                    KitNotice(stringResource(Res.string.pendingAuthOtherSource))
                if (inventory.storageUncertain)
                    KitNotice(
                        stringResource(Res.string.nlibStorage),
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.nlibRetrySave),
                                    actions::retrySave,
                                )
                            ),
                    )
            }
            item {
                when (state.tab) {
                    LibraryTab.Commands -> {
                        val rows = inventory.commands.filter { match(it.name, it.description) }
                        EmptyList(rows.isEmpty(), query, inventory.loading)
                        KitRowGroup {
                            rows.forEach { command ->
                                KitRow(
                                    command.name,
                                    supporting = command.description,
                                    supportingMaxLines = 3,
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = { actions.show(LibrarySheet.Command(command)) },
                                )
                            }
                        }
                    }
                    LibraryTab.Skills -> {
                        val rows =
                            inventory.skills.filter { match(it.name, it.description, it.location) }
                        EmptyList(rows.isEmpty(), query, inventory.loading)
                        KitRowGroup {
                            rows.forEach { skill ->
                                KitRow(
                                    skill.name,
                                    supporting = skill.description ?: skill.location,
                                    supportingMaxLines = 3,
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = { actions.show(LibrarySheet.Skill(skill)) },
                                )
                            }
                        }
                    }
                    LibraryTab.References -> {
                        val rows =
                            inventory.references.filter { match(it.name, it.description, it.path) }
                        EmptyList(rows.isEmpty(), query, inventory.loading)
                        KitRowGroup {
                            rows.forEach { reference ->
                                KitRow(
                                    reference.name,
                                    supporting = reference.description ?: reference.path,
                                    supportingMaxLines = 3,
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = { actions.show(LibrarySheet.Reference(reference)) },
                                )
                            }
                        }
                    }
                    LibraryTab.Providers -> {
                        KitRowGroup {
                            inventory.attempts.forEach { attempt ->
                                val provider =
                                    inventory.providers.firstOrNull { it.id == attempt.integration }
                                KitRow(
                                    provider?.name ?: attempt.integration,
                                    supporting = authLabel(attempt.state),
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = { actions.show(LibrarySheet.SignIn(attempt)) },
                                )
                            }
                            inventory.uncertain.forEach { id ->
                                KitRow(
                                    inventory.providers.firstOrNull { it.id == id }?.name ?: id,
                                    supporting = stringResource(Res.string.nlibUncertain),
                                    onClick = { actions.show(LibrarySheet.Uncertain(id)) },
                                )
                            }
                            val rows =
                                inventory.providers
                                    .filter {
                                        match(
                                            it.name,
                                            it.id,
                                            providerSearchTerms(it.id),
                                            inventory.catalog
                                                ?.models
                                                ?.filter { m -> m.providerId == it.id }
                                                ?.joinToString(" ") { m -> m.name + " " + m.id },
                                        ) &&
                                            inventory.attempts.none { pending ->
                                                pending.integration == it.id
                                            } &&
                                            it.id !in inventory.uncertain
                                    }
                                    .sortedWith(
                                        compareByDescending<IntegrationInfo> {
                                                it.connections.isNotEmpty()
                                            }
                                            .thenBy { providerRank(it.id) }
                                            .thenBy { it.name.lowercase() }
                                    )
                            EmptyList(
                                rows.isEmpty() && inventory.attempts.isEmpty(),
                                query,
                                inventory.loading,
                            )
                            rows.forEach { provider ->
                                KitRow(
                                    provider.name,
                                    supporting =
                                        if (provider.connections.isEmpty())
                                            stringResource(Res.string.nlibDisconnected)
                                        else if (
                                            inventory.catalog?.providers?.none {
                                                it.id == provider.id && it.enabled
                                            } == true
                                        )
                                            stringResource(Res.string.nlibUnusable)
                                        else stringResource(Res.string.nlibConnected),
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = { actions.show(LibrarySheet.Provider(provider)) },
                                )
                            }
                        }
                    }
                    LibraryTab.Mcp -> {
                        if (
                            inventory.capabilities?.let {
                                it.mcpRuntimeAdds || it.mcpConfigWrites
                            } == true
                        )
                            KitButton(
                                stringResource(Res.string.nlibAddMcp),
                                { actions.show(LibrarySheet.McpAddChoice) },
                                role = KitButtonRole.Secondary,
                            )
                        val rows =
                            inventory.servers
                                .filter { match(it.name, it.status) }
                                .sortedWith(
                                    compareBy<McpServerInfo> {
                                            when (it.status) {
                                                "needs_auth",
                                                "needs_client_registration" -> 0
                                                "failed" -> 1
                                                "connected" -> 2
                                                else -> 3
                                            }
                                        }
                                        .thenBy { it.name.lowercase() }
                                )
                        EmptyList(rows.isEmpty(), query, inventory.loading)
                        KitRowGroup {
                            rows.forEach { server ->
                                KitRow(
                                    server.name,
                                    supporting = mcpLabel(server.status),
                                    supportingMaxLines = 3,
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = { actions.show(LibrarySheet.Mcp(server)) },
                                )
                            }
                        }
                        KitRowGroup {
                            inventory.resources
                                .filter { match(it.name, it.server, it.description) }
                                .forEach { resource ->
                                    KitRow(
                                        resource.name,
                                        supporting =
                                            listOfNotNull(resource.server, resource.description)
                                                .joinToString(" · "),
                                        supportingMaxLines = 3,
                                        trailing =
                                            KitRowTrailing.Action(
                                                KitLineAction(
                                                    stringResource(Res.string.nlibCopy),
                                                    { actions.copy(resource.uri) },
                                                )
                                            ),
                                    )
                                }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Space.S5.dp)) }
        }
        state.sheet?.let { LibrarySheetBody(it, state, actions) }
        val link = state.link
        KitConfirm(
            link?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.nlibBrowserQuestion),
                    confirmLabel = stringResource(Res.string.nlibBrowser),
                    body = stringResource(Res.string.nlibBrowserBody),
                    consequences = listOf(it.host),
                )
            },
            actions::confirmLink,
            actions::cancelLink,
        )
    }
}

@Composable
private fun EmptyList(empty: Boolean, query: String, loading: Boolean) {
    if (empty && !loading)
        KitStateView(
            KitStateKind.Calm,
            stringResource(if (query.isEmpty()) Res.string.nlibEmpty else Res.string.nlibNoMatch),
        )
}

@Composable
private fun LibrarySheetBody(sheet: LibrarySheet, state: LibraryUi, actions: LibraryActions) {
    key(sheet) {
        val enabled =
            if (state.working || state.locked)
                KitEnabled.Disabled(stringResource(Res.string.nlibUncertain))
            else KitEnabled.Enabled
        when (sheet) {
            is LibrarySheet.Command -> CommandSheet(sheet.value, state, actions, enabled)
            is LibrarySheet.Skill -> SkillSheet(sheet.value, state, actions, enabled)
            is LibrarySheet.Reference ->
                KitSheet(
                    true,
                    { actions.show(null) },
                    sheet.value.name,
                    primary =
                        KitAction(
                            if (state.ref == null)
                                stringResource(Res.string.nlibCopyMention, "@${sheet.value.name}")
                            else stringResource(Res.string.nlibAddReference),
                            {
                                if (state.ref == null) actions.copy("@${sheet.value.name}")
                                else actions.addReference(sheet.value)
                            },
                        ),
                    tertiary =
                        listOf(
                            KitAction(
                                stringResource(Res.string.nlibCopyPath),
                                { actions.copy(sheet.value.path) },
                            )
                        ),
                ) {
                    sheet.value.description?.let { KitText(it) }
                    KitText(stringResource(Res.string.nlibReferenceHelp))
                    KitDetailsFold(
                        stringResource(Res.string.nlibDetails),
                        stringResource(Res.string.nlibCopy),
                        text = LocalKitRedactor.current.details(sheet.value.path),
                    )
                }
            is LibrarySheet.Provider -> ProviderSheet(sheet.value, state, actions)
            is LibrarySheet.Method ->
                MethodSheet(sheet.provider, sheet.method, state, actions, enabled)
            is LibrarySheet.Accounts -> AccountsSheet(sheet.provider, state, actions)
            is LibrarySheet.Credential ->
                CredentialSheet(sheet.provider, sheet.value, state, actions, enabled)
            is LibrarySheet.SignIn -> SignInSheet(sheet.value, state, actions, enabled)
            is LibrarySheet.Uncertain -> {
                var confirm by remember { mutableStateOf(false) }
                KitSheet(
                    true,
                    { actions.show(null) },
                    sheet.provider,
                    primary = KitAction(stringResource(Res.string.nlibForget), { confirm = true }),
                ) {
                    KitText(stringResource(Res.string.nlibUncertain))
                }
                KitConfirm(
                    if (confirm) forgetSpec() else null,
                    { actions.forgetUncertain(sheet.provider) },
                    { confirm = false },
                )
            }
            is LibrarySheet.Mcp -> McpSheet(sheet.value, state, actions)
            is LibrarySheet.AddMcp -> AddMcpSheet(state, actions, enabled, sheet.prefill)
            LibrarySheet.McpAddChoice ->
                KitSheet(true, { actions.show(null) }, stringResource(Res.string.nlibAddMcp)) {
                    KitRow(
                        stringResource(Res.string.nlibRegistry),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.show(LibrarySheet.Registry) },
                    )
                    KitRow(
                        stringResource(Res.string.nlibRegistryManual),
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.show(LibrarySheet.AddMcp()) },
                    )
                }
            LibrarySheet.Registry -> LibraryRegistrySheet(state, actions)
            is LibrarySheet.RegistryNode ->
                KitSheet(
                    true,
                    { actions.show(LibrarySheet.Registry) },
                    stringResource(Res.string.nlibRegistryNodeQuestion),
                    primary =
                        KitAction(
                            stringResource(Res.string.nlibRegistryAddNode),
                            actions::phoneTools,
                        ),
                    secondary =
                        KitAction(
                            stringResource(Res.string.nlibRegistryContinue),
                            { actions.show(LibrarySheet.AddMcp(sheet.draft)) },
                        ),
                ) {
                    KitText(stringResource(Res.string.nlibRegistryNodeBody))
                }
        }
    }
}

@Composable
private fun CommandSheet(
    command: CommandInfo,
    state: LibraryUi,
    actions: LibraryActions,
    enabled: KitEnabled,
) {
    var chosen by remember {
        mutableStateOf(state.ref?.sessionId ?: state.inventory.conversations.firstOrNull()?.id)
    }
    val args = rememberTextFieldState()
    KitSheet(
        true,
        { actions.show(null) },
        command.name,
        dismissible = !state.working,
        primary =
            chosen?.let { session ->
                KitAction(
                    stringResource(Res.string.nlibRunCommand),
                    { actions.run(command, session, args.text.toString()) },
                    enabled = enabled,
                    working = state.working,
                )
            },
    ) {
        command.description?.let { KitText(it) }
        KitField(args, stringResource(Res.string.nlibArguments))
        ConversationChoice(state.inventory.conversations, chosen) { chosen = it }
        if (state.locked) KitNotice(stringResource(Res.string.nlibUncertain))
    }
}

@Composable
private fun SkillSheet(
    skill: SkillInfo,
    state: LibraryUi,
    actions: LibraryActions,
    enabled: KitEnabled,
) {
    var raw by remember { mutableStateOf(false) }
    var resume by remember { mutableStateOf(true) }
    val session = state.ref?.sessionId
    val markdown =
        remember(skill) {
            KitMdSource.blocks(
                "skill:${skill.id ?: skill.name}",
                librarySkillProse(skill.content, skill.name),
                false,
            )
        }
    KitSheet(
        true,
        { actions.show(null) },
        skill.name,
        height = KitSheetHeight.Full,
        dismissible = !state.working,
        primary =
            if (session == null) null
            else
                KitAction(
                    stringResource(Res.string.nlibAddSkill),
                    { actions.activate(skill, session, resume) },
                    enabled,
                    working = state.working,
                ),
        tertiary =
            if (session == null && skill.slashCommand)
                listOf(
                    KitAction(
                        stringResource(Res.string.nlibCopyCommand),
                        { actions.copy("/${skill.id ?: skill.name}") },
                    )
                )
            else emptyList(),
    ) {
        if (session != null) {
            KitText(stringResource(Res.string.nlibSkillHelp))
            KitSwitchRow(
                stringResource(Res.string.nlibRunNow),
                resume,
                enabled = enabled,
                onCheckedChange = { resume = it },
            )
        }
        (state.notice ?: state.inventory.notice)?.let { KitNotice(noticeLabel(it)) }
        KitSegmented(
            listOf(
                KitSegment(false, stringResource(Res.string.nlibRendered)),
                KitSegment(true, stringResource(Res.string.nlibRaw)),
            ),
            raw,
            stringResource(Res.string.nlibView),
            onChange = { raw = it },
        )
        if (raw)
            KitCodeBlock(
                skill.content,
                KitCodeKind.Code,
                language = CodeLanguage.Plain,
                maxLines = Int.MAX_VALUE,
                copyLabel = stringResource(Res.string.nlibCopy),
                onCopy = actions::copy,
            )
        else
            KitMarkdown(
                markdown,
                KitMarkdownActions(
                    onLink = actions::external,
                    copyCodeLabel = stringResource(Res.string.nlibCopy),
                    onCopyCode = actions::copy,
                ),
            )
        KitDetailsFold(
            stringResource(Res.string.nlibDetails),
            stringResource(Res.string.nlibCopy),
            text = LocalKitRedactor.current.details(skill.location),
        )
        KitButton(
            stringResource(Res.string.nlibCopyPath),
            { actions.copy(skill.location) },
            role = KitButtonRole.Tertiary,
        )
    }
}

@Composable
private fun ConversationChoice(
    rows: List<Session>,
    selected: SessionId?,
    onSelect: (SessionId) -> Unit,
) {
    KitChoiceList(
        rows.map {
            KitChoice(
                it.id,
                it.title?.takeIf(String::isNotBlank) ?: stringResource(Res.string.nlibUntitled),
            )
        },
        selected,
        stringResource(Res.string.nlibConversation),
        empty = { KitText(stringResource(Res.string.nlibEmpty)) },
        onSelect = onSelect,
    )
}

@Composable
private fun ProviderSheet(provider: IntegrationInfo, state: LibraryUi, actions: LibraryActions) {
    var disconnect by remember { mutableStateOf(false) }
    val current = state.inventory.providers.firstOrNull { it.id == provider.id } ?: provider
    val hasCredentials =
        current.connections.any { it.type == "credential" && !it.id.isNullOrEmpty() }
    KitSheet(
        true,
        { actions.show(null) },
        current.name,
        tertiary =
            buildList {
                if (state.inventory.capabilities?.integrationCredentials == true)
                    add(
                        KitAction(
                            stringResource(Res.string.nlibAccounts),
                            { actions.show(LibrarySheet.Accounts(current)) },
                        )
                    )
                if (hasCredentials)
                    add(
                        KitAction(
                            stringResource(Res.string.nlibDisconnect),
                            { disconnect = true },
                            destructive = true,
                        )
                    )
            },
    ) {
        val methods =
            current.methods.filter {
                it.type == "key" ||
                    it.type == "oauth" ||
                    (it.type == "command" &&
                        state.inventory.capabilities?.integrationCommandAuth == true)
            }
        if (methods.isEmpty()) KitText(stringResource(Res.string.nlibServerManaged))
        KitRowGroup {
            methods.forEach { method ->
                KitRow(
                    method.label,
                    trailing = KitRowTrailing.Chevron,
                    onClick = { actions.show(LibrarySheet.Method(current, method)) },
                )
            }
        }
        current.connections.forEach { connection ->
            KitText(
                if (connection.type == "env") stringResource(Res.string.nlibEnvironment)
                else if (connection.type == "config") stringResource(Res.string.nlibServerManaged)
                else connection.label
            )
        }
        val environmentNames =
            current.methods.filter { it.type == "env" }.flatMap { it.environmentNames } +
                current.connections.filter { it.type == "env" }.map { it.label }
        if (environmentNames.isNotEmpty())
            KitDetailsFold(
                stringResource(Res.string.nlibDetails),
                stringResource(Res.string.nlibCopy),
                text =
                    LocalKitRedactor.current.details(
                        environmentNames.distinct().joinToString("\n")
                    ),
            )
    }
    KitConfirm(
        if (disconnect)
            KitConfirmSpec(
                stringResource(Res.string.nlibDisconnectQuestion),
                stringResource(Res.string.nlibDisconnect),
                KitConfirmKind.Destructive,
                body = stringResource(Res.string.nlibDisconnectBody),
            )
        else null,
        {
            disconnect = false
            actions.disconnect(current)
        },
        { disconnect = false },
    )
}

@Composable
private fun MethodSheet(
    provider: IntegrationInfo,
    method: IntegrationMethodInfo,
    state: LibraryUi,
    actions: LibraryActions,
    enabled: KitEnabled,
) {
    val key = remember { TextFieldState() }
    val answers =
        remember(method) {
            mutableStateMapOf<String, String>().apply {
                putAll(method.presetAnswers)
                method.prompts.forEach { prompt ->
                    val id = (prompt["key"] as? JsonPrimitive)?.content
                    val initial = (prompt["default"] as? JsonPrimitive)?.content
                    if (id != null && initial != null) put(id, initial)
                }
            }
        }
    val fields =
        remember(method) {
            method.prompts.associate { prompt ->
                (prompt["key"] as? JsonPrimitive)?.content.orEmpty() to
                    androidx.compose.foundation.text.input.TextFieldState(
                        (prompt["default"] as? JsonPrimitive)?.content.orEmpty()
                    )
            }
        }
    var missing by remember { mutableStateOf(false) }
    val prompts =
        method.prompts.filter { prompt ->
            val whenValue = prompt["when"] as? JsonObject
            val name = (whenValue?.get("key") as? JsonPrimitive)?.content
            val expected = (whenValue?.get("value") as? JsonPrimitive)?.content
            when ((whenValue?.get("op") as? JsonPrimitive)?.content) {
                "eq" -> answers[name] == expected
                "neq" -> answers[name] != expected
                else -> true
            }
        }
    val label =
        if (method.type == "key") stringResource(Res.string.nlibSaveKey)
        else stringResource(Res.string.nlibStartSignIn)
    KitSheet(
        true,
        { actions.show(null) },
        provider.name,
        dismissible = !state.working,
        primary =
            KitAction(
                label,
                {
                    if (method.type == "key") {
                        if (key.text.isBlank()) missing = true
                        else actions.key(provider, key.text.toString())
                    } else {
                        val values = method.presetAnswers.toMutableMap()
                        missing = false
                        prompts.forEach { prompt ->
                            val id = (prompt["key"] as? JsonPrimitive)?.content.orEmpty()
                            val value =
                                if ((prompt["type"] as? JsonPrimitive)?.content == "select")
                                    answers[id]
                                else fields[id]?.text?.toString()
                            if (
                                (prompt["required"] as? JsonPrimitive)?.booleanOrNull != false &&
                                    value.isNullOrBlank()
                            )
                                missing = true
                            if (value != null) values[id] = value
                        }
                        if (!missing) actions.start(provider, method, values.toImmutableMap())
                    }
                },
                enabled,
                working = state.working,
            ),
    ) {
        KitText(method.label)
        if (method.type == "key") {
            KitField(key, stringResource(Res.string.nlibKey), kind = KitFieldKind.Secret)
            providerKeyPageUrl(provider.id)?.let { url ->
                KitButton(
                    stringResource(Res.string.nlibGetKey),
                    { actions.external(url) },
                    role = KitButtonRole.Tertiary,
                )
            }
        } else {
            KitText(stringResource(Res.string.nlibSignInHelp))
            prompts.forEach { prompt ->
                val id = (prompt["key"] as? JsonPrimitive)?.content.orEmpty()
                val title = (prompt["message"] as? JsonPrimitive)?.content ?: id
                if ((prompt["type"] as? JsonPrimitive)?.content == "select") {
                    val options =
                        (prompt["options"] as? JsonArray).orEmpty().mapNotNull { value ->
                            val obj = value as? JsonObject ?: return@mapNotNull null
                            val option =
                                (obj["value"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                            KitChoice(option, (obj["label"] as? JsonPrimitive)?.content ?: option)
                        }
                    KitChoiceList(
                        options,
                        answers[id],
                        title,
                        empty = { KitText(stringResource(Res.string.nlibEmpty)) },
                        onSelect = { answers[id] = it },
                    )
                } else
                    fields[id]?.let { field ->
                        KitField(field, title, onTextChanged = { answers[id] = it })
                    }
            }
        }
        if (missing) KitNotice(stringResource(Res.string.nlibRequired))
        state.inventory.notice?.let { KitNotice(noticeLabel(it)) }
    }
}

@Composable
private fun AccountsSheet(provider: IntegrationInfo, state: LibraryUi, actions: LibraryActions) {
    val current = state.inventory.providers.firstOrNull { it.id == provider.id } ?: provider
    KitSheet(true, { actions.show(null) }, current.name) {
        KitText(stringResource(Res.string.nlibAccountsHelp))
        KitRowGroup {
            current.connections.forEachIndexed { index, connection ->
                val credential = connection.type == "credential" && !connection.id.isNullOrEmpty()
                val active =
                    state.inventory.activeCredential[current.id] == connection.id &&
                        current.id in state.inventory.activeCredential
                KitRow(
                    connection.label.ifBlank { (index + 1).toString() },
                    supporting =
                        if (active) stringResource(Res.string.nlibActive)
                        else if (!credential) stringResource(Res.string.nlibEnvironment) else null,
                    trailing = if (credential) KitRowTrailing.Chevron else KitRowTrailing.None,
                    onClick =
                        if (credential)
                            ({ actions.show(LibrarySheet.Credential(current, connection)) })
                        else null,
                )
            }
        }
    }
}

@Composable
private fun CredentialSheet(
    provider: IntegrationInfo,
    credential: IntegrationConnectionInfo,
    state: LibraryUi,
    actions: LibraryActions,
    enabled: KitEnabled,
) {
    val label = rememberTextFieldState(credential.label)
    var remove by remember { mutableStateOf(false) }
    KitSheet(
        true,
        { actions.show(null) },
        credential.label.ifBlank { provider.name },
        dismissible = !state.working,
        primary =
            KitAction(
                stringResource(Res.string.nlibUseAccount),
                { actions.credential(provider, credential, "activate") },
                enabled,
                working = state.working,
            ),
        tertiary =
            listOf(
                KitAction(
                    stringResource(Res.string.nlibRemoveAccount),
                    { remove = true },
                    enabled,
                    destructive = true,
                )
            ),
    ) {
        KitField(label, stringResource(Res.string.nlibLabel), maxLength = 128)
        KitButton(
            stringResource(Res.string.nlibRename),
            { actions.credential(provider, credential, "rename", label.text.toString().trim()) },
            enabled = enabled,
            role = KitButtonRole.Secondary,
        )
        state.inventory.notice?.let { KitNotice(noticeLabel(it)) }
    }
    KitConfirm(
        if (remove)
            KitConfirmSpec(
                stringResource(Res.string.nlibRemoveQuestion),
                stringResource(Res.string.nlibRemoveAccount),
                KitConfirmKind.Destructive,
            )
        else null,
        {
            remove = false
            actions.credential(provider, credential, "remove")
        },
        { remove = false },
    )
}

@Composable
private fun SignInSheet(
    attempt: LibraryAttempt,
    state: LibraryUi,
    actions: LibraryActions,
    enabled: KitEnabled,
) {
    val current =
        state.inventory.attempts.firstOrNull {
            it.integration == attempt.integration &&
                it.attempt == attempt.attempt &&
                it.command == attempt.command &&
                it.origin == attempt.origin &&
                it.directory == attempt.directory &&
                it.workspace == attempt.workspace
        } ?: attempt.copy(state = IntegrationAuthState.Complete)
    val code = remember { TextFieldState() }
    var missing by remember { mutableStateOf(false) }
    val launch = actions.launch(attempt)
    val deviceCode = launch?.instructions?.let(::libraryDeviceCode)
    var forget by remember { mutableStateOf(false) }
    KitSheet(
        true,
        { actions.show(null) },
        state.inventory.providers.firstOrNull { it.id == current.integration }?.name
            ?: current.integration,
        subtitle = authLabel(current.state),
        dismissible = !state.working,
        primary =
            if (
                current.state != IntegrationAuthState.Pending ||
                    (current.command && current.checkAfterMs > 0) ||
                    (!current.command && current.mode == IntegrationAuthMode.Auto)
            )
                null
            else
                KitAction(
                    stringResource(Res.string.nlibCheckSignIn),
                    { actions.check(current) },
                    enabled,
                    working = state.working,
                ),
        secondary =
            if (current.state != IntegrationAuthState.Pending) null
            else
                KitAction(
                    stringResource(Res.string.nlibCancelSignIn),
                    { actions.check(current, cancel = true) },
                    enabled,
                ),
        tertiary = listOf(KitAction(stringResource(Res.string.nlibForget), { forget = true })),
    ) {
        KitText(
            if (current.command) stringResource(Res.string.nlibSignInHelp)
            else stringResource(Res.string.nlibWaiting)
        )
        if (launch != null && !current.command && current.state == IntegrationAuthState.Pending)
            KitButton(
                stringResource(Res.string.nlibBrowser),
                { actions.openBrowser(current) },
                role = KitButtonRole.Secondary,
            )
        if (deviceCode != null && current.state == IntegrationAuthState.Pending) {
            KitText(stringResource(Res.string.nlibDeviceCode))
            KitText(deviceCode, role = TypeRole.Title)
            KitButton(
                stringResource(Res.string.nlibCopy),
                { actions.copy(deviceCode) },
                role = KitButtonRole.Tertiary,
            )
        }
        if (
            !current.command &&
                current.mode == IntegrationAuthMode.Code &&
                current.state == IntegrationAuthState.Pending
        ) {
            KitField(code, stringResource(Res.string.nlibCode), kind = KitFieldKind.Secret)
            KitButton(
                stringResource(Res.string.nlibFinish),
                {
                    if (code.text.isBlank()) missing = true
                    else {
                        missing = false
                        actions.check(current, code.text.toString())
                    }
                },
                enabled = enabled,
                role = KitButtonRole.Secondary,
            )
            if (missing) KitNotice(stringResource(Res.string.nlibRequired))
        }
        state.inventory.notice?.let { KitNotice(noticeLabel(it)) }
    }
    KitConfirm(
        if (forget) forgetSpec() else null,
        {
            forget = false
            actions.forget(current)
        },
        { forget = false },
    )
}

@Composable
private fun McpSheet(server: McpServerInfo, state: LibraryUi, actions: LibraryActions) {
    var disconnect by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val needsAuth = server.status in listOf("needs_auth", "needs_client_registration")
    KitSheet(
        true,
        { actions.show(null) },
        server.name,
        subtitle = mcpLabel(server.status),
        primary =
            if (needsAuth) null
            else
                KitAction(
                    stringResource(
                        if (server.status == "connected") Res.string.nlibMcpDisconnect
                        else Res.string.nlibMcpConnect
                    ),
                    {
                        if (server.status == "connected") disconnect = true else actions.mcp(server)
                    },
                    working = state.working,
                ),
        tertiary =
            if (state.inventory.capabilities?.mcpRuntimeRemovals == true)
                listOf(KitAction(stringResource(Res.string.nlibMcpRemove), { remove = true }))
            else emptyList(),
    ) {
        if (needsAuth) KitText(stringResource(Res.string.nlibMcpAuth))
        // Server diagnostic prose may contain secrets; the screen shows only fixed status words.
        state.inventory.notice?.let { KitNotice(noticeLabel(it)) }
    }
    KitConfirm(
        if (remove)
            KitConfirmSpec(
                stringResource(Res.string.nlibMcpRemoveQuestion),
                stringResource(Res.string.nlibMcpRemove),
                body = stringResource(Res.string.nlibMcpRemoveHelp),
            )
        else if (disconnect)
            KitConfirmSpec(
                stringResource(Res.string.nlibDisconnectQuestion),
                stringResource(Res.string.nlibMcpDisconnect),
            )
        else null,
        {
            actions.mcp(server, remove)
            remove = false
            disconnect = false
        },
        {
            remove = false
            disconnect = false
        },
    )
}

@Composable
private fun AddMcpSheet(
    state: LibraryUi,
    actions: LibraryActions,
    enabled: KitEnabled,
    prefill: McpServerDraft?,
) {
    val name = rememberTextFieldState(prefill?.name.orEmpty())
    val url = rememberTextFieldState(prefill?.url.orEmpty())
    val command = rememberTextFieldState(prefill?.command?.joinToString("\n").orEmpty())
    var local by remember { mutableStateOf(prefill?.kind == McpServerKind.Local) }
    val variables = remember {
        mutableStateMapOf<String, TextFieldState>().apply {
            (if (prefill?.kind == McpServerKind.Local) prefill.environment else prefill?.headers)
                .orEmpty()
                .forEach { (key, _) -> put(key, TextFieldState()) }
        }
    }
    val variableName = rememberTextFieldState()
    val variableValue = remember { TextFieldState() }
    val cwd = rememberTextFieldState(prefill?.cwd.orEmpty())
    val timeout = rememberTextFieldState(prefill?.timeoutMs?.toString().orEmpty())
    var detectOAuth by remember { mutableStateOf(prefill?.detectOAuth ?: true) }
    var missing by remember { mutableStateOf(false) }
    KitSheet(
        true,
        { actions.show(null) },
        stringResource(Res.string.nlibAddMcp),
        dismissible = !state.working,
        primary =
            KitAction(
                stringResource(Res.string.nlibAddMcp),
                {
                    val args = command.text.toString().lines().filter { it.isNotBlank() }
                    if (
                        name.text.isBlank() ||
                            (if (local) args.isEmpty() else url.text.isBlank()) ||
                            variables.any { it.value.text.isBlank() } ||
                            (timeout.text.isNotBlank() &&
                                timeout.text.toString().toIntOrNull()?.let { it > 0 } != true)
                    )
                        missing = true
                    else
                        actions.addMcp(
                            McpServerDraft(
                                name.text.toString().trim(),
                                if (local) McpServerKind.Local else McpServerKind.Remote,
                                if (local) null else url.text.toString().trim(),
                                args.toImmutableList(),
                                cwd.text.toString().trim().ifEmpty { null },
                                if (local) persistentMapOf()
                                else
                                    variables
                                        .mapValues { it.value.text.toString() }
                                        .toImmutableMap(),
                                if (local)
                                    variables
                                        .mapValues { it.value.text.toString() }
                                        .toImmutableMap()
                                else persistentMapOf(),
                                detectOAuth,
                                timeout.text.toString().toIntOrNull(),
                            )
                        )
                },
                enabled,
                working = state.working,
            ),
    ) {
        KitText(stringResource(Res.string.nlibMcpRuntime))
        KitField(name, stringResource(Res.string.nlibMcpName))
        KitSegmented(
            listOf(
                KitSegment(false, stringResource(Res.string.nlibMcpRemote)),
                KitSegment(true, stringResource(Res.string.nlibMcpLocal)),
            ),
            local,
            stringResource(Res.string.nlibMcp),
            onChange = {
                local = it
                variables.clear()
            },
        )
        if (local)
            KitField(
                command,
                stringResource(Res.string.nlibMcpCommand),
                kind = KitFieldKind.Multiline,
            )
        else KitField(url, stringResource(Res.string.nlibMcpUrl), kind = KitFieldKind.Url)
        if (local)
            KitField(cwd, stringResource(Res.string.nlibMcpDirectory), kind = KitFieldKind.Path)
        else
            KitSwitchRow(
                stringResource(Res.string.nlibMcpOAuth),
                detectOAuth,
                onCheckedChange = { detectOAuth = it },
            )
        KitField(timeout, stringResource(Res.string.nlibMcpTimeout), kind = KitFieldKind.Number)
        variables.forEach { (key, field) -> KitField(field, key, kind = KitFieldKind.Secret) }
        KitField(
            variableName,
            stringResource(
                if (local) Res.string.nlibMcpEnvironmentName else Res.string.nlibMcpHeaderName
            ),
        )
        KitField(
            variableValue,
            stringResource(Res.string.nlibMcpSecretValue),
            kind = KitFieldKind.Secret,
        )
        KitButton(
            stringResource(Res.string.nlibMcpAddVariable),
            {
                val key = variableName.text.toString().trim()
                if (key.isNotEmpty() && variableValue.text.isNotBlank() && variables.size < 32) {
                    variables[key] = TextFieldState(variableValue.text.toString())
                    variableName.edit { replace(0, length, "") }
                    variableValue.edit { replace(0, length, "") }
                } else missing = true
            },
            role = KitButtonRole.Tertiary,
        )
        if (missing) KitNotice(stringResource(Res.string.nlibRequired))
        state.inventory.notice?.let { KitNotice(noticeLabel(it)) }
    }
}

@Composable
private fun forgetSpec() =
    KitConfirmSpec(
        stringResource(Res.string.nlibForgetQuestion),
        stringResource(Res.string.nlibForget),
        body = stringResource(Res.string.nlibForgetHelp),
    )

@Composable
private fun tabLabel(tab: LibraryTab): String =
    stringResource(
        when (tab) {
            LibraryTab.Commands -> Res.string.nlibCommands
            LibraryTab.Skills -> Res.string.nlibSkills
            LibraryTab.References -> Res.string.nlibReferences
            LibraryTab.Providers -> Res.string.nlibProviders
            LibraryTab.Mcp -> Res.string.nlibMcp
        }
    )

@Composable
private fun noticeLabel(notice: LibraryNotice): String =
    stringResource(
        when (notice) {
            LibraryNotice.Changed -> Res.string.nlibChanged
            LibraryNotice.Failed -> Res.string.nlibFailed
            LibraryNotice.Done -> Res.string.nlibDone
            LibraryNotice.Uncertain -> Res.string.nlibUncertain
            LibraryNotice.Staged -> Res.string.nlibStaged
            LibraryNotice.Unsupported -> Res.string.nlibUnsupported
            LibraryNotice.Saved -> Res.string.nlibSaved
            LibraryNotice.Storage -> Res.string.nlibStorage
            LibraryNotice.Waiting -> Res.string.nlibWaiting
            LibraryNotice.Ended -> Res.string.nlibEnded
        }
    )

@Composable
private fun authLabel(state: IntegrationAuthState): String =
    stringResource(
        when (state) {
            IntegrationAuthState.Pending -> Res.string.nlibWaiting
            IntegrationAuthState.Complete -> Res.string.nlibConnected
            IntegrationAuthState.Failed,
            IntegrationAuthState.Expired -> Res.string.nlibEnded
        }
    )

@Composable
private fun mcpLabel(status: String): String =
    stringResource(
        when (status) {
            "connected" -> Res.string.nlibConnected
            "needs_auth",
            "needs_client_registration" -> Res.string.nlibMcpAuth
            "failed" -> Res.string.nlibFailed
            else -> Res.string.nlibDisconnected
        }
    )

private val popularProviderIds =
    listOf(
        "openai",
        "anthropic",
        "google",
        "zai-coding-plan",
        "minimax",
        "openrouter",
        "github-copilot",
        "deepseek",
        "xai",
        "mistral",
        "groq",
    )

private fun providerRank(id: String): Int =
    popularProviderIds.indexOf(id).takeIf { it >= 0 } ?: popularProviderIds.size

private fun providerSearchTerms(id: String): String =
    when (id) {
        "zai",
        "zhipuai",
        "zai-coding-plan",
        "zhipuai-coding-plan" -> "Z.AI Zhipu zai zhipuai coding plan"
        "amazon-bedrock" -> "amazon aws bedrock"
        "github-copilot" -> "github copilot"
        "azure" -> "microsoft azure"
        else -> id
    }
