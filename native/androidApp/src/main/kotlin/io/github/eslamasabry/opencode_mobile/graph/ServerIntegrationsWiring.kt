package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.connection.*
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.engine.setupassist.*
import io.github.eslamasabry.oc.engine.termuxhost.HostScripts
import io.github.eslamasabry.oc.feature.settings.integrations.*
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** Every read and command retains the exact saved profile, graph and project location it opened. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(
    AppScope::class,
    binding = binding<ServerIntegrations>(),
    replaces = [UnavailableServerIntegrations::class],
)
class NativeServerIntegrations(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
    private val nav: AppNavigator,
    private val application: Application,
    private val links: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
    appScope: AppCoroutineScope,
) : ServerIntegrations {
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("server-integrations"))

    override fun open(route: ServerIntegrationRoute): ServerIntegrationSession = Session(route)

    private fun safe(text: String): String = redactor.diagnostics(text).take(2000)

    private fun details(text: String): Redacted = redactor.details(redactor.diagnostics(text))

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()

    private val pretty = Json { prettyPrint = true }

    private inner class Session(private val route: ServerIntegrationRoute) :
        ServerIntegrationSession {
        private val own =
            CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        private val mutable = MutableStateFlow(ServerIntegrationUi(route.page))
        override val state: StateFlow<ServerIntegrationUi> = mutable.asStateFlow()
        private var saved: LoadedProfile? = null
        private var graph: ConnectionGraph? = null
        private var location: ServerLocation? = null
        private var token = 0L
        private var closed = false
        private var closeWhenIdle = false
        private var selectedProvider: IntegrationInfo? = null
        private var loaded = false
        private val providers = mutableMapOf<String, IntegrationInfo>()
        private val credentials = mutableMapOf<String, String>()
        private val mcp = mutableMapOf<String, McpServerInfo>()
        private val models = mutableMapOf<String, ModelRef>()
        private val rawCopy = mutableMapOf<String, String>()
        private var sourceWatch: Job? = null
        private var eventWatch: Job? = null
        private var refreshQueued = false
        private var refreshRunning = false
        private var sessionCursor: String? = null
        private var pendingLink: ApprovedLink? = null
        private val commandNames = mutableMapOf<String, String>()
        private val createdCommands = mutableMapOf<String, SessionId>()

        init {
            refresh()
        }

        private fun valid(): Boolean =
            !closed &&
                saved?.let { stores.profiles.profile(route.profile) == it } == true &&
                graph?.let {
                    registry.live.value[route.profile] === it &&
                        it.session.server.location.value == location
                } == true

        private fun publish(items: List<ServerIntegrationItem>, mine: Long) {
            if (mine == token && valid())
                mutable.update {
                    it.copy(loading = false, available = true, items = items.toImmutableList())
                }
        }

        override fun refresh() {
            if (closed) return
            if (refreshRunning) {
                refreshQueued = true
                return
            }
            refreshRunning = true
            own.launch {
                try {
                    val mine = ++token
                    mutable.update { it.copy(loading = true, failure = null) }
                    when (val warmed = stores.warm()) {
                        is Outcome.Failed -> {
                            failed(warmed.failure)
                            return@launch
                        }
                        is Outcome.Ok -> Unit
                    }
                    val profile = stores.profiles.profile(route.profile)
                    if (profile == null) {
                        unavailable()
                        return@launch
                    }
                    mutable.update { it.copy(server = safe(profile.profile.name)) }
                    if (route.page == ServerIntegrationPage.Permissions) {
                        withContext(dispatchers.main) {
                            if (
                                !closed &&
                                    stores.profiles.profile(route.profile) == profile &&
                                    nav.top.value == route
                            ) {
                                nav.back()
                                nav.open(SettingsRoute.Permissions(route.profile))
                            }
                        }
                        close()
                        return@launch
                    }
                    if (route.page == ServerIntegrationPage.Host) {
                        saved = profile
                        val port =
                            profile.toServerProfile()?.origin?.port
                                ?: run {
                                    unavailable()
                                    return@launch
                                }
                        rawCopy["install"] = HostScripts.install(port)
                        rawCopy["usb"] = "adb reverse tcp:$port tcp:$port"
                        mutable.update {
                            it.copy(
                                loading = false,
                                available = true,
                                items =
                                    rawCopy
                                        .map { (key, value) ->
                                            ServerIntegrationItem(
                                                key,
                                                key,
                                                kind = ServerIntegrationKind.HostCommand,
                                                details = details(value),
                                            )
                                        }
                                        .toImmutableList(),
                            )
                        }
                        return@launch
                    }
                    val live = registry.live.value[route.profile]
                    if (
                        live == null ||
                            live.profile !=
                                profile.toServerProfile(
                                    managedOnPhone = live.profile.managedOnPhone
                                )
                    ) {
                        unavailable()
                        return@launch
                    }
                    if (
                        loaded &&
                            (graph !== live ||
                                saved != profile ||
                                location != live.session.server.location.value)
                    ) {
                        unavailable()
                        return@launch
                    }
                    saved = profile
                    graph = live
                    location = live.session.server.location.value
                    loaded = true
                    if (sourceWatch == null)
                        sourceWatch = own.launch {
                            combine(
                                    registry.live,
                                    stores.profiles.profiles,
                                    live.session.server.location,
                                ) { _, _, _ ->
                                    valid()
                                }
                                .collect { if (!it) unavailable() }
                        }
                    if (eventWatch == null)
                        eventWatch = own.launch {
                            live.session.server.events.project.collect { envelope ->
                                val event = envelope.event
                                if (
                                    valid() &&
                                        (event == GatewayEvent.Connected ||
                                            event is GatewayEvent.RefreshHint &&
                                                event.kind.raw in
                                                    setOf(
                                                        "plugin",
                                                        "config",
                                                        "integration",
                                                        "catalog",
                                                        "agent",
                                                    ))
                                )
                                    refresh()
                            }
                        }
                    val ops = live.session.server.facet<ServerOperationsGateway>()
                    if (ops == null) {
                        unavailable()
                        return@launch
                    }
                    val caps = live.session.capabilities.value
                    mutable.update {
                        it.copy(catalog = caps.serverCatalog, toolsListed = caps.toolInventory)
                    }
                    when (route.page) {
                        ServerIntegrationPage.Providers -> {
                            if (!caps.integrationCredentials) {
                                unavailable()
                                return@launch
                            }
                            val got = ops.integrations()
                            if (!valid()) return@launch
                            when (got) {
                                is Outcome.Failed -> failed(got.failure)
                                is Outcome.Ok -> {
                                    providers.clear()
                                    credentials.clear()
                                    val rows = buildList {
                                        got.value.forEach { info ->
                                            val key = "provider:${info.id}"
                                            providers[key] = info
                                            add(
                                                ServerIntegrationItem(
                                                    key,
                                                    safe(info.name).ifBlank { safe(info.id) },
                                                    kind = ServerIntegrationKind.Provider,
                                                    status =
                                                        if (info.connectionCount > 0)
                                                            ServerIntegrationState.Connected
                                                        else ServerIntegrationState.Disconnected,
                                                    mayConnect = info.methods.isNotEmpty(),
                                                    mayDisconnect = info.connections.isNotEmpty(),
                                                )
                                            )
                                            info.connections.forEach { connection ->
                                                val id = connection.id ?: return@forEach
                                                val credentialKey = "credential:$id"
                                                credentials[credentialKey] = id
                                                add(
                                                    ServerIntegrationItem(
                                                        credentialKey,
                                                        safe(connection.label).ifBlank {
                                                            safe(info.name)
                                                        },
                                                        kind = ServerIntegrationKind.Credential,
                                                        status =
                                                            if (connection.status == "active")
                                                                ServerIntegrationState.Connected
                                                            else ServerIntegrationState.Available,
                                                        mayRename =
                                                            live.session.server
                                                                is IntegrationCredentialGateway,
                                                        mayActivate =
                                                            live.session.server
                                                                is IntegrationCredentialGateway,
                                                        mayDisconnect =
                                                            live.session.server
                                                                is IntegrationCredentialGateway,
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    publish(rows, mine)
                                }
                            }
                        }
                        ServerIntegrationPage.Tools -> loadTools(ops, mine)
                        ServerIntegrationPage.Commands,
                        ServerIntegrationPage.Skills,
                        ServerIntegrationPage.References -> loadCatalogPage(ops, mine)
                        ServerIntegrationPage.Mcp -> {
                            if (!caps.serverCatalog) {
                                unavailable()
                                return@launch
                            }
                            when (val got = ops.mcpServers()) {
                                is Outcome.Failed -> failed(got.failure)
                                is Outcome.Ok -> {
                                    if (!valid()) return@launch
                                    mcp.clear()
                                    val rows =
                                        got.value
                                            .map { server ->
                                                val key = "mcp:${server.name}"
                                                mcp[key] = server
                                                ServerIntegrationItem(
                                                    key,
                                                    safe(server.name).ifBlank { key },
                                                    kind = ServerIntegrationKind.Mcp,
                                                    status = status(server.status),
                                                    mayConnect =
                                                        server.status !in
                                                            setOf("connected", "pending"),
                                                    mayDisconnect = server.status == "connected",
                                                )
                                            }
                                            .toMutableList()
                                    when (val resources = ops.mcpResources()) {
                                        is Outcome.Failed ->
                                            if (
                                                resources.failure !is Failure.Unavailable &&
                                                    resources.failure !is Failure.NotFound
                                            )
                                                failed(resources.failure)
                                        is Outcome.Ok ->
                                            if (valid())
                                                resources.value.forEachIndexed { index, resource ->
                                                    rows.add(
                                                        ServerIntegrationItem(
                                                            "resource:$index",
                                                            safe(resource.name).ifBlank {
                                                                safe(resource.server)
                                                            },
                                                            safe(resource.description.orEmpty()),
                                                            ServerIntegrationKind.Resource,
                                                            details = details(resource.uri),
                                                        )
                                                    )
                                                }
                                    }
                                    mutable.update { it.copy(mayAdd = caps.mcpConfigWrites) }
                                    publish(rows, mine)
                                }
                            }
                        }
                        ServerIntegrationPage.Plugins -> {
                            val inspection = live.session.server as? ServerInspectionGateway
                            if (!caps.pluginInventory || inspection == null) {
                                unavailable()
                                return@launch
                            }
                            val pinned = location!!
                            val reader =
                                PluginInventoryReader(
                                    CatalogTransport { request ->
                                        if (!valid() || request.path != "/plugin") stale()
                                        else
                                            inspection.readPluginInventory(pinned).map {
                                                CatalogReply(200, it)
                                            }
                                    }
                                ) {
                                    CatalogLocation(pinned.directory, pinned.workspace)
                                }
                            when (val got = reader.listPlugins()) {
                                is Outcome.Failed -> failed(got.failure)
                                is Outcome.Ok ->
                                    publish(
                                        got.value.mapIndexed { index, plugin ->
                                            ServerIntegrationItem(
                                                "plugin:$index",
                                                plugin.id?.let(::pluginName) ?: "?",
                                                plugin.status.name.lowercase(),
                                                ServerIntegrationKind.Plugin,
                                                builtIn = plugin.source == PluginSourceKind.Builtin,
                                                status =
                                                    when (plugin.status) {
                                                        PluginStatus.Active ->
                                                            ServerIntegrationState.Connected
                                                        PluginStatus.Failed ->
                                                            ServerIntegrationState.Failed
                                                        PluginStatus.Unknown ->
                                                            ServerIntegrationState.Unknown
                                                    },
                                                details =
                                                    details(
                                                        listOfNotNull(
                                                                plugin.id,
                                                                plugin.packageName,
                                                                plugin.source.name,
                                                                if (plugin.terminalUi) "TUI"
                                                                else null,
                                                            )
                                                            .joinToString("\n")
                                                    ),
                                            )
                                        },
                                        mine,
                                    )
                            }
                        }
                        ServerIntegrationPage.Setup -> loadSetup(live, mine)
                        else -> unavailable()
                    }
                } finally {
                    refreshRunning = false
                    if (refreshQueued && !closed) {
                        refreshQueued = false
                        refresh()
                    }
                }
            }
        }

        private suspend fun loadTools(ops: ServerOperationsGateway, mine: Long) {
            val live = graph ?: return
            if (!live.session.capabilities.value.serverCatalog) {
                unavailable()
                return
            }
            if (!live.session.capabilities.value.toolInventory) {
                publish(emptyList(), mine)
                return
            }
            if (models.isEmpty())
                when (val catalog = ops.catalog()) {
                    is Outcome.Failed -> {
                        failed(catalog.failure)
                        return
                    }
                    is Outcome.Ok -> {
                        if (!valid()) return
                        catalog.value.models
                            .filter { it.enabled }
                            .forEach { model ->
                                models[model.providerId + "/" + model.id] =
                                    ModelRef(model.providerId, model.id)
                            }
                        mutable.update {
                            it.copy(
                                models =
                                    catalog.value.models
                                        .filter { it.enabled }
                                        .map { model ->
                                            ServerIntegrationModelChoice(
                                                model.providerId + "/" + model.id,
                                                safe(model.name) + " · " + safe(model.providerId),
                                            )
                                        }
                                        .toImmutableList()
                            )
                        }
                    }
                }
            val selected =
                state.value.model
                    ?: when (val defaults = ops.chatDefaults()) {
                        is Outcome.Ok ->
                            defaults.value.model?.let { it.providerId + "/" + it.modelId }
                        is Outcome.Failed -> null
                    }?.takeIf { it in models }
            mutable.update { it.copy(model = selected) }
            if (selected == null) {
                publish(emptyList(), mine)
                return
            }
            val model = models[selected] ?: return
            val tools =
                when (val got = ops.codingTools(model)) {
                    is Outcome.Failed -> {
                        failed(got.failure)
                        return
                    }
                    is Outcome.Ok -> got.value
                }
            if (!valid()) return
            val ids =
                when (val got = ops.codingToolIds()) {
                    is Outcome.Failed -> {
                        failed(got.failure)
                        emptyList()
                    }
                    is Outcome.Ok -> got.value
                }
            val rows =
                tools
                    .map { tool ->
                        ServerIntegrationItem(
                            "tool:${tool.id}",
                            safe(tool.id).ifBlank { "?" },
                            safe(tool.description).substringBefore('\n').substringBefore(". "),
                            ServerIntegrationKind.CallableTool,
                            details =
                                details(
                                    safe(tool.description) +
                                        "\n" +
                                        pretty.encodeToString(
                                            JsonElement.serializer(),
                                            setupRedact(redactor, tool.parameters) ?: JsonNull,
                                        )
                                ),
                        )
                    }
                    .toMutableList()
            ids.filter { id -> tools.none { it.id == id } }
                .forEach {
                    rows.add(
                        ServerIntegrationItem(
                            "registered:$it",
                            safe(it).ifBlank { "?" },
                            kind = ServerIntegrationKind.RegisteredTool,
                            status = ServerIntegrationState.Registered,
                        )
                    )
                }
            if (live.session.capabilities.value.slashCommands)
                when (val commands = ops.commands()) {
                    is Outcome.Failed -> failed(commands.failure)
                    is Outcome.Ok ->
                        commands.value.forEach {
                            rows.add(
                                ServerIntegrationItem(
                                    "command:${it.name}",
                                    safe(it.name).ifBlank { "?" },
                                    safe(it.description.orEmpty()),
                                    ServerIntegrationKind.Command,
                                    details = details(it.description.orEmpty()),
                                )
                            )
                        }
                }
            publish(rows, mine)
        }

        private suspend fun loadCatalogPage(ops: ServerOperationsGateway, mine: Long) {
            val live = graph ?: return
            if (!live.session.capabilities.value.serverCatalog) {
                unavailable()
                return
            }
            when (route.page) {
                ServerIntegrationPage.Commands -> {
                    if (!live.session.capabilities.value.slashCommands) {
                        unavailable()
                        return
                    }
                    when (val got = ops.commands()) {
                        is Outcome.Failed -> failed(got.failure)
                        is Outcome.Ok -> {
                            commandNames.clear()
                            val rows =
                                got.value.map { command ->
                                    val key = "command:${command.name}"
                                    commandNames[key] = command.name
                                    ServerIntegrationItem(
                                        key,
                                        safe(command.name).ifBlank { "?" },
                                        safe(command.description.orEmpty()),
                                        ServerIntegrationKind.Command,
                                        details = details(command.description.orEmpty()),
                                        mayRun = live.session.server is FencedSlashCommandGateway,
                                    )
                                }
                            readSessions(null)
                            publish(rows, mine)
                        }
                    }
                }
                ServerIntegrationPage.Skills ->
                    when (val got = ops.skills()) {
                        is Outcome.Failed -> failed(got.failure)
                        is Outcome.Ok ->
                            publish(
                                got.value.mapIndexed { index, skill ->
                                    val key = "skill:$index"
                                    if (skill.slashCommand)
                                        rawCopy[key] = safe("/${skill.id ?: skill.name}")
                                    ServerIntegrationItem(
                                        key,
                                        safe(skill.name).ifBlank { "?" },
                                        safe(skill.description.orEmpty()),
                                        ServerIntegrationKind.Skill,
                                        details = details(skill.location),
                                        content = safeSkill(skill.content, skill.name),
                                    )
                                },
                                mine,
                            )
                    }
                ServerIntegrationPage.References ->
                    when (val got = ops.references()) {
                        is Outcome.Failed -> failed(got.failure)
                        is Outcome.Ok ->
                            publish(
                                got.value.mapIndexed { index, reference ->
                                    val key = "reference:$index"
                                    rawCopy[key] = safe("@${reference.name}")
                                    ServerIntegrationItem(
                                        key,
                                        safe(reference.name).ifBlank { "?" },
                                        safe(reference.description.orEmpty()),
                                        ServerIntegrationKind.Reference,
                                        details = details(reference.path),
                                    )
                                },
                                mine,
                            )
                    }
                else -> Unit
            }
        }

        private fun safeSkill(text: String, name: String): String {
            val lines = redactor.diagnostics(text, 200_000).lines()
            return if (lines.firstOrNull()?.trim()?.removePrefix("# ")?.equals(name, true) == true)
                lines.drop(1).joinToString("\n").trimStart()
            else lines.joinToString("\n")
        }

        private suspend fun readSessions(cursor: String?) {
            if (!valid()) return
            val live = graph ?: return
            when (val got = live.session.server.sessionPage(cursor, 100)) {
                is Outcome.Failed -> failed(got.failure)
                is Outcome.Ok ->
                    if (valid()) {
                        sessionCursor = got.value.nextCursor
                        val old = if (cursor == null) emptyList() else state.value.sessions
                        mutable.update {
                            it.copy(
                                sessions =
                                    (old +
                                            got.value.items.map { session ->
                                                ServerIntegrationModelChoice(
                                                    session.id.raw,
                                                    safe(session.title.orEmpty()),
                                                )
                                            })
                                        .distinctBy { it.key }
                                        .toImmutableList(),
                                moreSessions = sessionCursor != null,
                            )
                        }
                    }
            }
        }

        override fun moreSessions() {
            val cursor = sessionCursor ?: return
            own.launch { readSessions(cursor) }
        }

        override fun runCommand(arguments: String, destination: String) {
            val item = state.value.selected ?: return
            val name = commandNames[item.key] ?: return
            if (!valid() || state.value.running || state.value.commandBlocked || !item.mayRun)
                return
            if (destination.isNotEmpty() && state.value.sessions.none { it.key == destination })
                return
            own.launch {
                if (!valid()) return@launch
                val live = graph ?: return@launch
                val gateway = live.session.server
                val facet = gateway as? FencedSlashCommandGateway ?: return@launch
                mutable.update { it.copy(running = true, failure = null) }
                try {
                    val fresh =
                        gateway.facet<ServerOperationsGateway>()?.commands() ?: return@launch
                    if (fresh is Outcome.Failed) {
                        failed(fresh.failure)
                        return@launch
                    }
                    if (!valid() || (fresh as Outcome.Ok).value.none { it.name == name }) {
                        failed(Failure.Refused(RefusalReason.StaleScope))
                        return@launch
                    }
                    var id =
                        destination.takeIf { it.isNotEmpty() }?.let(::SessionId)
                            ?: createdCommands[item.key]
                    if (id == null) {
                        when (
                            val created = gateway.createSession(NewSession(location = location))
                        ) {
                            is Outcome.Failed -> {
                                failed(created.failure)
                                mutable.update {
                                    it.copy(
                                        commandBlocked =
                                            created.failure is Failure.Network ||
                                                created.failure is Failure.TimedOut ||
                                                created.failure is Failure.UnreadableReply
                                    )
                                }
                                return@launch
                            }
                            is Outcome.Ok -> {
                                id = created.value.id
                                createdCommands[item.key] = id
                            }
                        }
                    }
                    if (!valid()) return@launch
                    val target = id ?: return@launch
                    when (val session = gateway.session(target)) {
                        is Outcome.Failed -> {
                            failed(session.failure)
                            return@launch
                        }
                        is Outcome.Ok ->
                            if (
                                session.value.id != target ||
                                    (location?.directory != null &&
                                        session.value.directory != location?.directory) ||
                                    session.value.workspaceId?.raw != location?.workspace
                            ) {
                                failed(Failure.Refused(RefusalReason.StaleScope))
                                return@launch
                            }
                    }
                    if (!valid()) return@launch
                    when (
                        val result =
                            facet.slashCommandFenced(target, name, arguments.trim(), ::valid)
                    ) {
                        is Outcome.Failed -> {
                            failed(result.failure)
                            mutable.update {
                                it.copy(
                                    commandBlocked =
                                        result.failure is Failure.Network ||
                                            result.failure is Failure.TimedOut ||
                                            result.failure is Failure.UnreadableReply
                                )
                            }
                        }
                        is Outcome.Ok ->
                            if (valid()) {
                                mutable.update { it.copy(selected = null) }
                                withContext(dispatchers.main) {
                                    if (valid() && nav.top.value == route)
                                        nav.open(ChatRoute(ConversationRef(route.profile, target)))
                                }
                            }
                    }
                } finally {
                    mutable.update { it.copy(running = false) }
                    if (closeWhenIdle) close()
                }
            }
        }

        override fun openLink(url: String) {
            if (!valid()) return
            when (val decision = links.check(url, LinkOrigin.ServerContent)) {
                is LinkDecision.Open -> opener.open(decision.link)
                is LinkDecision.AskFirst -> {
                    pendingLink = decision.link
                    mutable.update { it.copy(linkHost = decision.shownHost) }
                }
                is LinkDecision.Refuse -> failed(Failure.Rejected(null))
            }
        }

        override fun confirmLink() {
            val link = pendingLink
            pendingLink = null
            mutable.update { it.copy(linkHost = null) }
            if (link != null && valid()) opener.open(link)
        }

        private suspend fun loadSetup(live: ConnectionGraph, mine: Long) {
            val inspection = live.session.server as? ServerInspectionGateway
            if (!live.session.capabilities.value.setupConfigRead || inspection == null) {
                unavailable()
                return
            }
            val pinned = location!!
            val gateway =
                Oc2SetupConfigGateway(
                    object : SetupHttpClient {
                        override val location = SetupLocation(pinned.directory, pinned.workspace)

                        override suspend fun getJson(
                            path: String,
                            query: Map<String, String>,
                        ): SetupHttpReply {
                            if (!valid()) return SetupHttpReply.Unreadable
                            val result =
                                when (path) {
                                    "/config" -> inspection.readSetupConfig(pinned)
                                    "/mcp" -> inspection.readSetupMcp(pinned)
                                    else -> return SetupHttpReply.Unreadable
                                }
                            return when (result) {
                                is Outcome.Ok -> SetupHttpReply.Json(result.value)
                                is Outcome.Failed ->
                                    when (result.failure) {
                                        is Failure.SignIn -> SetupHttpReply.Status(401)
                                        is Failure.Network,
                                        is Failure.TimedOut -> SetupHttpReply.Network
                                        is Failure.Unavailable -> SetupHttpReply.RemovedInStable
                                        else -> SetupHttpReply.Unreadable
                                    }
                            }
                        }
                    },
                    redactor,
                )
            val config =
                when (val got = gateway.readConfig()) {
                    is SetupOutcome.Failed -> {
                        failed(Failure.UnreadableReply())
                        return
                    }
                    is SetupOutcome.Ok -> setupRedactObject(redactor, got.value)
                }
            val servers =
                when (val got = gateway.listMcpServers()) {
                    is SetupOutcome.Failed -> {
                        failed(Failure.UnreadableReply())
                        return
                    }
                    is SetupOutcome.Ok -> got.value
                }
            if (!valid()) return
            val rows = mutableListOf<ServerIntegrationItem>()
            servers
                .sortedBy { urgency(it.status) }
                .forEach { server ->
                    if (urgency(server.status) <= 1)
                        rows.add(
                            ServerIntegrationItem(
                                "suggestion:${if (urgency(server.status) == 0) "signin" else "fix"}:${server.name}",
                                safe(server.name),
                                kind = ServerIntegrationKind.Suggestion,
                                status = status(server.status),
                            )
                        )
                    rows.add(
                        ServerIntegrationItem(
                            "setupmcp:${server.name}",
                            safe(server.name).ifBlank { "?" },
                            kind = ServerIntegrationKind.Mcp,
                            status = status(server.status),
                        )
                    )
                }
            if (servers.isEmpty())
                rows.add(
                    ServerIntegrationItem(
                        "suggestion:add",
                        "MCP",
                        kind = ServerIntegrationKind.Suggestion,
                    )
                )
            (config["sources"] as? JsonArray).orEmpty().forEachIndexed { index, entry ->
                val source = entry as? JsonObject ?: return@forEachIndexed
                rows.add(
                    ServerIntegrationItem(
                        "source:$index",
                        (index + 1).toString(),
                        safe((source["type"] as? JsonPrimitive)?.content.orEmpty()),
                        ServerIntegrationKind.Source,
                        details = details(pretty.encodeToString(JsonElement.serializer(), source)),
                    )
                )
            }
            publish(rows, mine)
        }

        private fun failed(failure: Failure) {
            if (!closed) mutable.update { it.copy(loading = false, failure = failure) }
        }

        private fun unavailable() {
            mutable.update {
                it.copy(
                    loading = false,
                    available = false,
                    items = persistentListOf(),
                    selected = null,
                    mayAdd = false,
                )
            }
        }

        override fun select(key: String) {
            val item = state.value.items.firstOrNull { it.key == key } ?: return
            selectedProvider = providers[key]
            mutable.update { it.copy(selected = item, copied = false) }
        }

        override fun chooseModel(key: String) {
            if (key !in models || !valid()) return
            mutable.update { it.copy(model = key, items = persistentListOf()) }
            refresh()
        }

        override fun connect(key: String) {
            if (!valid() || state.value.loading) return
            providers[key]?.let {
                nav.open(ServerProviderAuthRoute(route.profile, it.id))
                return
            }
            val server = mcp[key] ?: return
            mutation { it.connectMcp(server.name) }
        }

        override fun askDisconnect(key: String) {
            if (valid())
                state.value.items
                    .firstOrNull { it.key == key && it.mayDisconnect }
                    ?.let { row -> mutable.update { it.copy(question = row) } }
        }

        override fun disconnect() {
            val row = state.value.question ?: return
            dismiss()
            mutation { ops ->
                providers[row.key]?.let {
                    return@mutation ops.disconnectIntegration(it)
                }
                credentials[row.key]?.let {
                    return@mutation (graph?.session?.server as? IntegrationCredentialGateway)
                        ?.removeCredential(it) ?: stale()
                }
                mcp[row.key]?.let {
                    return@mutation ops.disconnectMcp(it.name)
                }
                stale()
            }
        }

        override fun rename(key: String, label: String) {
            val id = credentials[key] ?: return
            if (label.isBlank() || label.length > 100 || redactor.containsSecret(label)) {
                failed(Failure.Rejected(null))
                return
            }
            mutation {
                (graph?.session?.server as? IntegrationCredentialGateway)?.renameCredential(
                    id,
                    label.trim(),
                ) ?: stale()
            }
        }

        override fun activate(key: String) {
            val id = credentials[key] ?: return
            mutation {
                (graph?.session?.server as? IntegrationCredentialGateway)?.activateCredential(id)
                    ?: stale()
            }
        }

        private fun mutation(call: suspend (ServerOperationsGateway) -> Outcome<Unit>) {
            if (!valid() || state.value.loading) return
            own.launch {
                if (!valid()) return@launch
                val ops = graph?.session?.server?.facet<ServerOperationsGateway>() ?: return@launch
                mutable.update { it.copy(loading = true, failure = null) }
                val result = call(ops)
                if (!valid()) {
                    unavailable()
                    return@launch
                }
                mutable.update {
                    it.copy(
                        loading = false,
                        selected = null,
                        add = if (result is Outcome.Ok) false else it.add,
                        inputRevision = it.inputRevision + if (result is Outcome.Ok) 1 else 0,
                        failure = (result as? Outcome.Failed)?.failure,
                    )
                }
                // A lost acknowledgement may still have changed the server: only a fresh read
                // restores controls.
                refresh()
                if (result is Outcome.Failed) mutable.update { it.copy(failure = result.failure) }
            }
        }

        override fun beginAdd() {
            if (valid() && state.value.mayAdd)
                mutable.update { it.copy(add = true, inputRevision = it.inputRevision + 1) }
        }

        override fun add(name: String, url: String, executable: String, arguments: String) {
            if (!state.value.add || !state.value.mayAdd || !valid()) return
            val remote = url.isNotBlank()
            if (
                remote == executable.isNotBlank() ||
                    name.isBlank() ||
                    name.length > 200 ||
                    name.any { it.isISOControl() }
            ) {
                failed(Failure.Rejected(null))
                return
            }
            if (
                remote &&
                    (links.check(url, LinkOrigin.ServerContent) is LinkDecision.Refuse ||
                        !safeMcpUrl(url))
            ) {
                failed(Failure.Rejected(null))
                return
            }
            val draft =
                McpServerDraft(
                    name.trim(),
                    if (remote) McpServerKind.Remote else McpServerKind.Local,
                    url.trim().takeIf { remote },
                    if (remote) persistentListOf()
                    else
                        (listOf(executable.trim()) + arguments.lines().filter { it.isNotEmpty() })
                            .toImmutableList(),
                    null,
                    persistentMapOf(),
                    persistentMapOf(),
                    false,
                    null,
                )
            mutation { it.addMcpServer(draft, McpConfigScope.RuntimeLocation) }
        }

        override fun dismiss() {
            if (state.value.running) return
            pendingLink = null
            mutable.update {
                it.copy(
                    selected = null,
                    question = null,
                    add = false,
                    copied = false,
                    linkHost = null,
                )
            }
        }

        override fun copy() {
            val item = state.value.selected ?: return
            if (route.page != ServerIntegrationPage.Host && !valid()) return
            if (
                route.page == ServerIntegrationPage.Host &&
                    stores.profiles.profile(route.profile) != saved
            )
                return
            val text = rawCopy[item.key] ?: item.details?.text ?: return
            val clipboard =
                application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", text))
            mutable.update { it.copy(copied = true) }
        }

        override fun go(page: ServerIntegrationPage) {
            if (valid()) nav.open(ServerIntegrationRoute(route.profile, page))
        }

        override fun docs() {
            if (stores.profiles.profile(route.profile) == saved)
                when (
                    val decision = links.check(HostScripts.UBUNTU_GUIDE_URL, LinkOrigin.AppAuthored)
                ) {
                    is LinkDecision.Open -> opener.open(decision.link)
                    else -> Unit
                }
        }

        override fun close() {
            if (state.value.running) {
                closeWhenIdle = true
                return
            }
            closed = true
            token++
            own.cancel()
            providers.clear()
            credentials.clear()
            rawCopy.clear()
        }
    }

    private fun safeMcpUrl(value: String): Boolean =
        try {
            val parsed = java.net.URI(value)
            parsed.userInfo == null &&
                parsed.fragment == null &&
                parsed.query == null &&
                !parsed.host.isNullOrBlank() &&
                (parsed.scheme == "https" ||
                    parsed.scheme == "http" && AddressRules.isLoopback(parsed.host))
        } catch (_: Exception) {
            false
        }

    private fun status(value: String): ServerIntegrationState =
        when (value.lowercase()) {
            "connected",
            "active" -> ServerIntegrationState.Connected
            "failed" -> ServerIntegrationState.Failed
            "pending",
            "needs_auth",
            "needs_client_registration" -> ServerIntegrationState.Pending
            "disabled" -> ServerIntegrationState.Disabled
            "disconnected" -> ServerIntegrationState.Disconnected
            else -> ServerIntegrationState.Unknown
        }

    private fun urgency(value: String): Int =
        when (value) {
            "needs_auth",
            "needs_client_registration" -> 0
            "failed" -> 1
            "pending",
            "unknown" -> 2
            "disabled" -> 3
            else -> 4
        }

    private fun pluginName(id: String): String {
        val acronyms =
            setOf(
                "ai",
                "api",
                "cli",
                "git",
                "http",
                "https",
                "id",
                "json",
                "llm",
                "lsp",
                "mcp",
                "pr",
                "sdk",
                "ssh",
                "tui",
                "ui",
                "url",
            )
        var name = id.substringAfterLast('/').substringBefore('@')
        var parts = name.split('.').filter { it.isNotEmpty() }
        if (parts.size > 1) parts = parts.drop(1)
        while (
            parts.size > 1 &&
                parts.first() in
                    setOf(
                        "tool",
                        "tools",
                        "config",
                        "plugin",
                        "plugins",
                        "provider",
                        "providers",
                        "builtin",
                        "core",
                    )
        ) parts = parts.drop(1)
        val words =
            parts
                .joinToString(" ")
                .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
                .split(Regex("[-_\\s]+"))
                .filter { it.isNotEmpty() }
                .toMutableList()
        while (words.size > 1 && words.first().lowercase() in setOf("opencode", "plugin")) words
            .removeAt(0)
        return safe(
                words
                    .mapIndexed { index, word ->
                        val lower = word.lowercase()
                        if (lower in acronyms) lower.uppercase()
                        else if (index == 0) lower.replaceFirstChar(Char::uppercaseChar) else lower
                    }
                    .joinToString(" ")
            )
            .ifBlank { safe(id) }
    }
}
