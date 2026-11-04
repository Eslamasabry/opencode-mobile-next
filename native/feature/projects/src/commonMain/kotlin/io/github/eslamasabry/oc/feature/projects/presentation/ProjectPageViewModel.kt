package io.github.eslamasabry.oc.feature.projects.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.agents.feed.FeedFolders
import io.github.eslamasabry.oc.engine.attention.AttentionKind
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LinuxRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One project's page (07 §5 D15). "New conversation here" starts a conversation whose location is
 * this folder (the way a new conversation switches project), then opens it. Its conversations and
 * "In other projects" (B-NAV-010) come from the server's conversation pages, newest first, with a
 * visible Load more. Opening the page remembers the folder under Opened before (B-ANDR-078).
 */
@AssistedInject
public class ProjectPageViewModel(
    @Assisted private val route: ProjectsRoute.Project,
    private val nav: AppNavigator,
    private val connections: ProjectServers,
    private val opened: OpenedFolders,
    private val isolatedHosts: Set<IsolatedTaskHost> = emptySet(),
) : ViewModel(), ProjectPageActions {
    private val directory = ProjectRules.normalize(route.directory)
    private val mutable = MutableStateFlow(ProjectPageUi(directory, ProjectRules.name(directory)))
    public val state: StateFlow<ProjectPageUi> = mutable.asStateFlow()

    private var server: ProjectServer? = null
    private var sessions: List<Session> = emptyList()
    private var running: Set<SessionId> = emptySet()
    private var cursor: String? = null
    private var generation = 0
    private var adopted = false
    private var isolatedGeneration = 0
    private var taskOpened = false
    private var isolatedLaunch:
        io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskLaunch? =
        null
    private var isolatedWatch: kotlinx.coroutines.Job? = null
    private val taskWrites = kotlinx.coroutines.sync.Mutex()
    private var attentionWatch: Job? = null
    private val workspace =
        ProjectWorkspace(
            directory,
            viewModelScope,
            publish = { next -> mutable.update { it.copy(workspace = next) } },
            openConversation = { session ->
                server?.let { nav.openChat(ConversationRef(it.profile, session)) }
            },
        )

    init {
        viewModelScope.launch {
            connections.servers.collect { live ->
                val next = live.firstOrNull { it.profile == route.profile }
                if (!adopted || next?.gateway !== server?.gateway) {
                    adopted = true
                    mutable.update {
                        it.copy(elsewhereAttention = kotlinx.collections.immutable.persistentListOf())
                    }
                    attentionWatch?.cancel()
                    attentionWatch =
                        next?.attention?.let { attention ->
                            viewModelScope.launch {
                                attention.collect { observed ->
                                    val activity =
                                        observed.activity(except = directory).associateBy {
                                            it.directory
                                        }
                                    val observations =
                                        observed.observations(except = directory).groupBy {
                                            it.target.directory
                                        }
                                    val folders =
                                        (activity.keys + observations.keys).filter {
                                            ProjectRules.normalize(it) != directory
                                        }
                                    mutable.update { ui ->
                                        ui.copy(
                                            elsewhereAttention =
                                                folders
                                                    .map { folder ->
                                                        val rows = observations[folder].orEmpty()
                                                        ProjectAttentionUi(
                                                            folder,
                                                            ProjectRules.name(folder),
                                                            activity[folder]?.waiting?.size ?: 0,
                                                            activity[folder]?.running?.size ?: 0,
                                                            rows.count {
                                                                it.target.kind ==
                                                                    AttentionKind.FailedRun
                                                            },
                                                            rows.any { !it.isFresh } ||
                                                                activity[folder]?.isFresh == false,
                                                        )
                                                    }
                                                    .toImmutableList()
                                        )
                                    }
                                }
                            }
                        }
                    server = next
                    workspace.adopt(next)
                    load()
                    mutable.update {
                        it.copy(
                            canStartIsolated =
                                next?.gateway?.capabilities?.value?.worktreeCreate == true &&
                                    isolatedHosts.size == 1
                        )
                    }
                    refreshCopies()
                    if (next != null && route.autoStartIsolated && !taskOpened) {
                        taskOpened = true
                        startIsolatedTask()
                    }
                }
            }
        }
        viewModelScope.launch { opened.remember(route.profile, directory) }
    }

    private fun load() {
        val live = server
        val run = ++generation
        if (live == null) {
            mutable.update { it.copy(phase = ProjectPagePhase.NoServer) }
            return
        }
        mutable.update {
            it.copy(phase = ProjectPagePhase.Opening, serverName = live.name, failure = null)
        }
        viewModelScope.launch {
            val page = live.gateway.sessionPage()
            val statuses = live.gateway.sessionStatuses()
            if (run != generation) return@launch
            when (page) {
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(phase = ProjectPagePhase.Failed, failure = page.failure)
                    }
                is Outcome.Ok -> {
                    sessions = page.value.items
                    cursor = page.value.nextCursor
                    running =
                        (statuses as? Outcome.Ok)
                            ?.value
                            ?.filterValues(ProjectRules::isRunning)
                            ?.keys
                            .orEmpty()
                    publish(ProjectPagePhase.Ready)
                }
            }
        }
    }

    private fun row(session: Session): ProjectChatUi {
        val live = server
        val dir = session.directory
        return ProjectChatUi(
            ref = ConversationRef(live?.profile ?: route.profile, session.id),
            title = ChatTitles.present(session.title),
            project =
                when {
                    dir.isNullOrBlank() -> null
                    FeedFolders.isOtherFolder(dir) -> FeedFolders.otherFolderLabel(dir)
                    else -> FeedFolders.folderName(dir)
                },
            running = session.id in running,
        )
    }

    private fun publish(phase: ProjectPagePhase) {
        mutable.update {
            it.copy(
                phase = phase,
                conversations =
                    ProjectRules.inProject(sessions, directory).map(::row).toImmutableList(),
                elsewhere =
                    ProjectRules.elsewhere(sessions, running, directory)
                        .map(::row)
                        .toImmutableList(),
                canLoadMore = cursor != null,
            )
        }
    }

    override fun loadMore() {
        val live = server ?: return
        val next = cursor ?: return
        if (state.value.loadingMore) return
        val run = generation
        mutable.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val page = live.gateway.sessionPage(cursor = next)
            if (run != generation) return@launch
            when (page) {
                is Outcome.Failed ->
                    mutable.update { it.copy(loadingMore = false, loadMoreFailure = page.failure) }
                is Outcome.Ok -> {
                    val seen = sessions.mapTo(HashSet()) { it.id }
                    sessions = sessions + page.value.items.filter { seen.add(it.id) }
                    cursor = page.value.nextCursor
                    mutable.update { it.copy(loadingMore = false, loadMoreFailure = null) }
                    publish(ProjectPagePhase.Ready)
                }
            }
        }
    }

    override fun openOtherProject(directory: String) {
        nav.open(ProjectsRoute.Project(route.profile, directory))
    }

    override fun newConversationHere() {
        val live = server ?: return
        if (state.value.creating) return
        mutable.update { it.copy(creating = true, createFailure = null) }
        viewModelScope.launch {
            // The project rides on the new session itself; the shared connection's location is
            // never moved.
            val made =
                live.gateway.createSession(
                    NewSession(location = ServerLocation(directory = directory))
                )
            when (made) {
                is Outcome.Failed ->
                    mutable.update { it.copy(creating = false, createFailure = made.failure) }
                is Outcome.Ok -> {
                    opened.remember(live.profile, directory)
                    sessions = listOf(made.value) + sessions
                    mutable.update { it.copy(creating = false) }
                    publish(ProjectPagePhase.Ready)
                    nav.openChat(ConversationRef(live.profile, made.value.id), focusComposer = true)
                }
            }
        }
    }

    override fun refreshCopies() {
        val live = server ?: return
        val ops = live.operations ?: return
        if (!live.gateway.capabilities.value.worktreeCreate || state.value.copiesLoading) return
        mutable.update { it.copy(copiesLoading = true) }
        viewModelScope.launch {
            val read = ops.listWorktrees(directory)
            if (server?.gateway !== live.gateway) return@launch
            when (read) {
                is Outcome.Failed ->
                    mutable.update { it.copy(copiesLoading = false, copiesFailure = read.failure) }
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(copiesLoading = false, copiesFailure = null, copies = read.value)
                    }
            }
        }
    }

    override fun openCopy(directory: String) {
        nav.open(ProjectsRoute.Project(route.profile, directory))
    }

    override fun startIsolatedTask() {
        if (state.value.isolatedShown) return
        val host = isolatedHosts.singleOrNull() ?: return
        val sheetGeneration = ++isolatedGeneration
        isolatedLaunch?.close()
        isolatedWatch?.cancel()
        mutable.update {
            it.copy(
                isolatedShown = true,
                isolatedLoading = true,
                isolatedFailure = null,
                isolated = null,
                isolatedTask = "",
                isolatedName = "",
            )
        }
        viewModelScope.launch {
            val loadedDraft = host.draft(route.profile)
            if (sheetGeneration != isolatedGeneration || !state.value.isolatedShown) return@launch
            when (val loaded = loadedDraft) {
                is Outcome.Failed -> {
                    mutable.update {
                        it.copy(isolatedLoading = false, isolatedFailure = loaded.failure)
                    }
                    return@launch
                }
                is Outcome.Ok ->
                    mutable.update {
                        it.copy(isolatedTask = loaded.value.ifEmpty { route.initialTask })
                    }
            }
            val model =
                route.taskProvider?.let { provider ->
                    route.taskModel?.let {
                        io.github.eslamasabry.oc.core.model.ModelRef(provider, it)
                    }
                }
            val openedLaunch =
                host.open(viewModelScope, route.profile, directory, model, route.taskAgent)
            if (sheetGeneration != isolatedGeneration || !state.value.isolatedShown) {
                (openedLaunch as? Outcome.Ok)?.value?.close()
                return@launch
            }
            when (val launch = openedLaunch) {
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(isolatedLoading = false, isolatedFailure = launch.failure)
                    }
                is Outcome.Ok -> {
                    if (!state.value.isolatedShown) {
                        launch.value.close()
                        return@launch
                    }
                    isolatedLaunch = launch.value
                    mutable.update {
                        it.copy(isolatedLoading = false, isolated = launch.value.state.value)
                    }
                    isolatedWatch = viewModelScope.launch {
                        launch.value.state.collect { task ->
                            if (sheetGeneration != isolatedGeneration) return@collect
                            mutable.update {
                                it.copy(
                                    isolated = task,
                                    isolatedDetails =
                                        task.worktree?.let { copy ->
                                            host.redactTechnical(
                                                copy.directory +
                                                    (copy.branch?.let { "\n" + it } ?: "")
                                            )
                                        },
                                )
                            }
                            if (
                                task.phase ==
                                    io.github.eslamasabry.oc.engine.agents.worktree
                                        .IsolatedTaskPhase
                                        .Opened && task.ref != null
                            ) {
                                mutable.update { it.copy(isolatedShown = false) }
                                task.ref?.let { nav.openChat(it, focusComposer = true) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun isolatedDraftChanged(text: String) {
        if (
            state.value.isolatedLoading ||
                state.value.isolated?.phase !in
                    setOf(
                        io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskPhase.Form,
                        io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskPhase
                            .CreateFailed,
                    )
        )
            return
        mutable.update { it.copy(isolatedTask = text) }
        val host = isolatedHosts.singleOrNull() ?: return
        viewModelScope.launch {
            taskWrites.lock()
            try {
                val saved = host.saveDraft(route.profile, text)
                if (state.value.isolatedTask == text)
                    mutable.update { it.copy(isolatedDraftFailed = saved is Outcome.Failed) }
            } finally {
                taskWrites.unlock()
            }
        }
    }

    override fun isolatedNameChanged(name: String) {
        mutable.update { it.copy(isolatedName = name) }
    }

    override fun createIsolatedTask() {
        val text = state.value.isolatedTask
        val name = state.value.isolatedName
        viewModelScope.launch {
            taskWrites.lock()
            try {
                val saved = isolatedHosts.singleOrNull()?.saveDraft(route.profile, text)
                mutable.update { it.copy(isolatedDraftFailed = saved !is Outcome.Ok) }
                isolatedLaunch?.start(text, name)
            } finally {
                taskWrites.unlock()
            }
        }
    }

    override fun closeIsolatedTask() {
        if (state.value.isolated?.busy == true) return
        if (state.value.isolatedLoading) {
            finishClosingTask()
            return
        }
        if (
            state.value.isolated?.phase ==
                io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskPhase.Unsent
        ) {
            openUnsentConversation()
            return
        }
        val phase = state.value.isolated?.phase
        if (
            phase == io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskPhase.Form ||
                phase ==
                    io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskPhase.CreateFailed
        ) {
            val text = state.value.isolatedTask
            val ticket = isolatedGeneration
            mutable.update { it.copy(isolatedLoading = true) }
            viewModelScope.launch {
                taskWrites.lock()
                try {
                    val saved = isolatedHosts.singleOrNull()?.saveDraft(route.profile, text)
                    if (ticket != isolatedGeneration) return@launch
                    if (saved !is Outcome.Ok) {
                        mutable.update {
                            it.copy(isolatedLoading = false, isolatedDraftFailed = true)
                        }
                        return@launch
                    }
                    finishClosingTask()
                } finally {
                    taskWrites.unlock()
                }
            }
        } else finishClosingTask()
    }

    private fun finishClosingTask() {
        isolatedGeneration += 1
        isolatedLaunch?.stopWaiting()
        isolatedLaunch?.close()
        isolatedWatch?.cancel()
        mutable.update { it.copy(isolatedShown = false, isolatedLoading = false) }
        refreshCopies()
    }

    override fun keepWaitingForCopy() {
        isolatedLaunch?.keepWaiting()
    }

    override fun openCopyAnyway() {
        isolatedLaunch?.open(acceptUnconfirmed = true, acceptFailedSetup = true)
    }

    override fun requestRemoveCopy() {
        mutable.update { it.copy(confirmRemoveCopy = true) }
    }

    override fun confirmRemoveCopy(remove: Boolean) {
        mutable.update { it.copy(confirmRemoveCopy = false) }
        if (remove) isolatedLaunch?.removeFailedCopy()
    }

    override fun openUnsentConversation() {
        val ref = state.value.isolated?.ref ?: return
        mutable.update { it.copy(isolatedShown = false) }
        nav.openChat(ref, focusComposer = true)
    }

    override fun onCleared() {
        isolatedLaunch?.close()
        super.onCleared()
    }

    override fun openConversation(ref: ConversationRef): Unit = nav.openChat(ref)

    override fun developmentServices(): Unit =
        nav.open(LinuxRoute.Services(route.profile, directory))

    override fun openWorkspace() = workspace.openWorkspace()

    override fun closeWorkspace() = workspace.closeWorkspace()

    override fun retryWorkspace() = workspace.retryWorkspace()

    override fun chooseChanges(mode: io.github.eslamasabry.oc.core.model.VcsDiffMode) =
        workspace.chooseChanges(mode)

    override fun newNamedWorktree() = workspace.newNamedWorktree()

    override fun createGeneratedWorktree() = workspace.createGeneratedWorktree()

    override fun finishWorktreeName(name: String?) = workspace.finishWorktreeName(name)

    override fun inspectWorktreeRemoval(directory: String) =
        workspace.inspectWorktreeRemoval(directory)

    override fun confirmWorktreeRemoval() = workspace.confirmWorktreeRemoval()

    override fun cancelWorktreeRemoval() = workspace.cancelWorktreeRemoval()

    override fun openWorkspaceDiff(path: String) = workspace.openWorkspaceDiff(path)

    override fun closeWorkspaceDiff() = workspace.closeWorkspaceDiff()

    override fun workspaceDiffPage(delta: Int) = workspace.workspaceDiffPage(delta)

    override fun inspectCopy(directory: String) = workspace.inspectCopy(directory)

    override fun loadMoreCopyConversations() = workspace.loadMoreCopyConversations()

    override fun openCopyConversation(sessionId: String) = workspace.openCopyConversation(sessionId)

    override fun back(): Unit = nav.back()

    override fun retry(): Unit = load()

    override fun openServers(): Unit = nav.open(ServersRoute.Home)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ProjectsRoute.Project): ProjectPageViewModel
    }
}
