package io.github.eslamasabry.oc.feature.agents.tools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.*
import io.github.eslamasabry.oc.core.domain.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LibraryRoute
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * A live target captured once. The host invalidates it when its graph or selected location changes.
 */
public class ToolsConnection(
    public val server: ServerGateway,
    public val operations: ServerOperationsGateway,
    public val library: LibraryGateway?,
    public val isCurrent: () -> Boolean,
    public val hints: Flow<Unit>,
)

public fun interface ToolsSource {
    public suspend fun connect(profile: ProfileId): Outcome<ToolsConnection>
}

public enum class ToolsIssue {
    Load,
    Unavailable,
    Changed,
    InvalidDraft,
    Add,
    Disconnect,
    Activate,
    Staged,
    Uncertain,
    Done,
}

public data class ToolsUi(
    val tools: ImmutableList<CodingToolInfo> = persistentListOf(),
    val models: ImmutableList<ModelRef> = persistentListOf(),
    val model: ModelRef? = null,
    val tool: CodingToolInfo? = null,
    val toolsSupported: Boolean = false,
    val loading: Boolean = true,
    val working: Boolean = false,
    val plugins: ImmutableList<LibraryPlugin> = persistentListOf(),
    val skills: ImmutableList<SkillInfo> = persistentListOf(),
    val servers: ImmutableList<McpServerInfo> = persistentListOf(),
    val failedSections: ImmutableSet<String> = persistentSetOf(),
    val pluginSupported: Boolean = false,
    val catalogSupported: Boolean = false,
    val canAdd: Boolean = false,
    val hasSession: Boolean = false,
    val selected: SkillInfo? = null,
    val resume: Boolean = false,
    val issue: ToolsIssue? = null,
)

public interface ToolsActions {
    public fun back()

    public fun refresh()

    public fun selectModel(model: ModelRef) {}

    public fun selectTool(id: String) {}

    public fun providers()

    public fun selectSkill(id: String)

    public fun resume(value: Boolean)

    public fun activate()

    public fun connect(name: String)

    public fun disconnect(name: String)

    public fun add(name: String, target: String, local: Boolean)
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ToolsViewModel(private val source: ToolsSource, private val nav: AppNavigator) :
    ViewModel(), ToolsActions {
    private val mutable = MutableStateFlow(ToolsUi())
    public val state: StateFlow<ToolsUi> = mutable.asStateFlow()
    private var profile: ProfileId? = null
    private var session: SessionId? = null
    private var connection: ToolsConnection? = null
    private var load: Job? = null
    private var hints: Job? = null
    private var generation = 0

    public fun open(profile: ProfileId, session: SessionId?) {
        if (this.profile == profile && this.session == session) return
        generation++
        this.profile = profile
        this.session = session
        connection = null
        load?.cancel()
        hints?.cancel()
        mutable.value = ToolsUi(hasSession = session != null)
        refresh()
    }

    override fun back() = nav.back()

    override fun selectModel(model: ModelRef) {
        if (model !in mutable.value.models || mutable.value.working || mutable.value.loading) return
        mutable.update { it.copy(model = model, tool = null) }
        refresh()
    }

    override fun selectTool(id: String) {
        mutable.update { before -> before.copy(tool = before.tools.firstOrNull { it.id == id }) }
    }

    override fun providers() {
        profile?.let { nav.open(LibraryRoute(tab = "providers", profileId = it)) }
    }

    override fun refresh() {
        val id = profile ?: return
        if (load?.isActive == true || mutable.value.working) return
        val epoch = generation
        load = viewModelScope.launch {
            mutable.update { it.copy(loading = true) }
            val target =
                when (val result = source.connect(id)) {
                    is Outcome.Ok -> result.value
                    is Outcome.Failed -> {
                        if (epoch == generation)
                            mutable.update {
                                it.copy(loading = false, issue = ToolsIssue.Unavailable)
                            }
                        return@launch
                    }
                }
            connection = target
            val caps = target.server.capabilities.value
            val providerRead = if (caps.toolInventory) target.server.providers() else null
            val providers = (providerRead as? Outcome.Ok)?.value
            val models =
                providers
                    ?.providers
                    .orEmpty()
                    .flatMap { provider ->
                        provider.modelIds.map { ModelRef(provider.id, it) }
                    }
                    .distinct()
                    .toImmutableList()
            val serverDefault = providers?.let { data ->
                val providerId = data.defaultProviderId
                val modelId = data.defaultModelId
                if (providerId != null && modelId != null) ModelRef(providerId, modelId) else null
            }
            val selectedModel =
                mutable.value.model?.takeIf { it in models }
                    ?: serverDefault?.takeIf { it in models }
                    ?: models.firstOrNull()
            val tools =
                if (caps.toolInventory && selectedModel != null)
                    target.operations.codingTools(selectedModel)
                else null
            val plugins = if (caps.pluginInventory) target.library?.plugins() else null
            val skills = if (caps.serverCatalog) target.operations.skills() else null
            val servers = if (caps.serverCatalog) target.operations.mcpServers() else null
            if (epoch != generation) return@launch
            if (!target.isCurrent()) {
                // The connection changed mid-load: never leave the page stuck in loading.
                mutable.update { it.copy(loading = false, issue = ToolsIssue.Changed) }
                return@launch
            }
            mutable.update { before ->
                before.copy(
                    loading = false,
                    tools =
                        (tools as? Outcome.Ok)?.value
                            ?: before.tools.takeIf { before.model == selectedModel }
                            ?: persistentListOf(),
                    models = models,
                    model = selectedModel,
                    toolsSupported = caps.toolInventory,
                    tool = null,
                    plugins = (plugins as? Outcome.Ok)?.value ?: before.plugins,
                    skills = (skills as? Outcome.Ok)?.value ?: before.skills,
                    servers = (servers as? Outcome.Ok)?.value ?: before.servers,
                    pluginSupported = caps.pluginInventory && target.library != null,
                    catalogSupported = caps.serverCatalog,
                    // add() always uses the runtime scope (until the server restarts).
                    canAdd = caps.mcpRuntimeAdds,
                    failedSections = buildSet {
                            if (tools is Outcome.Failed || providerRead is Outcome.Failed)
                                add("tools")
                            if (plugins is Outcome.Failed) add("plugins")
                            if (skills is Outcome.Failed) add("skills")
                            if (servers is Outcome.Failed) add("mcp")
                        }
                            .toImmutableSet(),
                )
            }
            hints?.cancel()
            hints = viewModelScope.launch {
                target.hints.collect { if (target.isCurrent()) refresh() }
            }
        }
    }

    override fun selectSkill(id: String) {
        mutable.update {
            it.copy(
                selected = it.skills.firstOrNull { skill -> skill.id == id },
                resume = false,
                issue = null,
            )
        }
    }

    override fun resume(value: Boolean) {
        mutable.update { it.copy(resume = value) }
    }

    private fun action(command: suspend (ToolsConnection) -> Outcome<Unit>, problem: ToolsIssue) {
        if (mutable.value.working || mutable.value.loading) return
        val target = connection ?: return
        if (!target.isCurrent()) {
            mutable.update { it.copy(issue = ToolsIssue.Changed) }
            return
        }
        val epoch = generation
        mutable.update { it.copy(working = true, issue = null) }
        viewModelScope.launch {
            val result = command(target)
            if (epoch != generation) return@launch
            val failure = (result as? Outcome.Failed)?.failure
            mutable.update {
                it.copy(
                    working = false,
                    issue =
                        when {
                            !target.isCurrent() -> ToolsIssue.Changed
                            result is Outcome.Ok -> ToolsIssue.Done
                            failure is Failure.StagedRevert -> ToolsIssue.Staged
                            failure is Failure.Unavailable -> ToolsIssue.Unavailable
                            failure is Failure.Refused &&
                                failure.reason == RefusalReason.DispatchUncertain ->
                                ToolsIssue.Uncertain
                            else -> problem
                        },
                )
            }
            if (target.isCurrent() && problem != ToolsIssue.Activate) refresh()
        }
    }

    override fun activate() {
        val selected = mutable.value.selected?.id ?: return
        val session = session ?: return
        val resume = mutable.value.resume
        action(
            { target ->
                val fresh = target.server.session(session)
                if (!target.isCurrent()) Failure.Refused(RefusalReason.StaleScope).failed()
                else
                    when (fresh) {
                        is Outcome.Failed -> fresh
                        is Outcome.Ok ->
                            if (fresh.value.reverted || fresh.value.stagedRevert != null)
                                Failure.StagedRevert().failed()
                            else
                                target.library?.activateSkill(session, selected, resume)
                                    ?: Failure.Unavailable(FeatureId("sessionSkills")).failed()
                    }
            },
            ToolsIssue.Activate,
        )
    }

    override fun connect(name: String) =
        action(
            { target ->
                if (!target.server.capabilities.value.serverCatalog)
                    Failure.Unavailable(FeatureId("serverCatalog")).failed()
                else target.operations.connectMcp(name)
            },
            ToolsIssue.Disconnect,
        )

    override fun disconnect(name: String) =
        action(
            { target ->
                if (!target.server.capabilities.value.serverCatalog)
                    Failure.Unavailable(FeatureId("serverCatalog")).failed()
                else target.operations.disconnectMcp(name)
            },
            ToolsIssue.Disconnect,
        )

    override fun add(name: String, target: String, local: Boolean) {
        val draft =
            McpServerDraft(
                name,
                if (local) McpServerKind.Local else McpServerKind.Remote,
                if (local) null else target,
                if (local) target.lines().toImmutableList() else persistentListOf(),
                null,
                persistentMapOf(),
                persistentMapOf(),
                true,
                null,
            )
        if (mcpDraftConfig(draft) !is McpDraftCheck.Valid) {
            mutable.update { it.copy(issue = ToolsIssue.InvalidDraft) }
            return
        }
        action(
            { connection ->
                connection.operations.addMcpServer(draft, McpConfigScope.RuntimeLocation)
            },
            ToolsIssue.Add,
        )
    }
}
