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
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageAccess
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock
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
    private val signals: ProjectSignalsHost = NoProjectSignalsHost(),
    private val storageAccess: Set<SharedStorageAccess> = emptySet(),
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
    private var workspaceOpened = false
    private var isolatedLaunch:
        io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskLaunch? =
        null
    private var isolatedWatch: kotlinx.coroutines.Job? = null
    private val taskWrites = kotlinx.coroutines.sync.Mutex()
    private var attentionWatch: Job? = null
    private var devServer: io.github.eslamasabry.oc.engine.runtime.devservers.ProjectDevServer? =
        null
    private var devServerWatch: Job? = null
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
                        it.copy(
                            elsewhereAttention = kotlinx.collections.immutable.persistentListOf()
                        )
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
                    devServer?.close()
                    devServerWatch?.cancel()
                    devServer = next?.let { signals.devServer(it.profile, directory) }
                    mutable.update { it.copy(devServerPort = null, changesCount = null) }
                    devServerWatch = devServer?.let { signal ->
                        viewModelScope.launch {
                            signal.running.collect { port ->
                                mutable.update { it.copy(devServerPort = port) }
                            }
                        }
                    }
                    workspace.adopt(next)
                    // Open the requested workspace once; a later re-adoption must not reopen a
                    // workspace the person closed.
                    if (next != null && route.initialWorkspace && !workspaceOpened) {
                        workspaceOpened = true
                        workspace.openWorkspace()
                    }
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
        check(storageAccess.size <= 1) { "multiple shared-storage hosts" }
    }

    private fun load() {
        val live = server
        val run = ++generation
        if (live == null) {
            mutable.update { it.copy(phase = ProjectPagePhase.NoServer) }
            return
        }
        mutable.update {
            it.copy(
                phase = ProjectPagePhase.Opening,
                serverName = live.name,
                failure = null,
                storageBlock = SharedStorageBlock.None,
                requestingStorage = false,
                creating = false,
                loadingMore = false,
            )
        }
        viewModelScope.launch {
            if (!checkStorage(live, run)) return@launch
            val page = live.gateway.sessionPage()
            if (!isCurrent(live, run)) return@launch
            val statuses = live.gateway.sessionStatuses()
            viewModelScope.launch {
                devServer?.refresh()
                val changes =
                    if (live.gateway.capabilities.value.sessionDiff)
                        (live.gateway
                                as? io.github.eslamasabry.oc.core.domain.LocatedProjectGateway)
                            ?.operationsAt(ServerLocation(directory = directory))
                            ?.listVcsDiffs(
                                io.github.eslamasabry.oc.core.model.VcsDiffMode.WorkingTree
                            )
                    else null
                if (run == generation && changes is Outcome.Ok)
                    mutable.update { it.copy(changesCount = changes.value.size) }
            }
            if (run != generation) return@launch
            if (!isCurrent(live, run)) return@launch
            when (page) {
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(phase = ProjectPagePhase.Failed, failure = page.failure)
                    }
                is Outcome.Ok -> {
                    opened.remember(route.profile, directory)
                    if (!isCurrent(live, run)) return@launch
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

    private fun isCurrent(live: ProjectServer, run: Int): Boolean =
        run == generation &&
            connections.servers.value.any {
                it.profile == route.profile && it.gateway === live.gateway
            }

    private suspend fun checkStorage(live: ProjectServer, run: Int): Boolean {
        val result = storageAccess.singleOrNull()?.check(route.profile, directory, live.gateway)
        if (!isCurrent(live, run)) return false
        return when (result) {
            null -> true
            is Outcome.Failed -> {
                mutable.update {
                    it.copy(
                        phase = ProjectPagePhase.Failed,
                        storageBlock = SharedStorageBlock.None,
                        failure = result.failure,
                        creating = false,
                        loadingMore = false,
                    )
                }
                false
            }
            is Outcome.Ok -> {
                mutable.update { it.copy(storageBlock = result.value) }
                if (result.value != SharedStorageBlock.None) {
                    mutable.update {
                        it.copy(
                            phase = ProjectPagePhase.Failed,
                            failure = null,
                            creating = false,
                            loadingMore = false,
                        )
                    }
                    false
                } else true
            }
        }
    }

    override fun onResume() {
        // Re-check only a page still blocked by shared storage; a plain return from a chat or
        // Files must not turn the page back into a skeleton (Flutter asks only on open).
        val s = state.value
        if (
            s.storageBlock != SharedStorageBlock.None &&
                !s.requestingStorage &&
                s.phase != ProjectPagePhase.Opening
        )
            load()
    }

    override fun requestStorage() {
        val access = storageAccess.singleOrNull() ?: return
        val block = state.value.storageBlock
        val live = server ?: return
        val run = generation
        if (block == SharedStorageBlock.None || state.value.requestingStorage) return
        mutable.update { it.copy(requestingStorage = true) }
        viewModelScope.launch {
            val asked = access.request(route.profile, directory, block, live.gateway)
            if (!isCurrent(live, run)) return@launch
            mutable.update {
                it.copy(requestingStorage = false, failure = (asked as? Outcome.Failed)?.failure)
            }
            if (asked is Outcome.Ok) load()
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
            if (!checkStorage(live, run)) return@launch
            val page = live.gateway.sessionPage(cursor = next)
            if (!isCurrent(live, run)) return@launch
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
        val run = generation
        mutable.update { it.copy(creating = true, createFailure = null) }
        viewModelScope.launch {
            if (!checkStorage(live, run)) return@launch
            // The project rides on the new session itself; the shared connection's location is
            // never moved.
            val made =
                live.gateway.createSession(
                    NewSession(location = ServerLocation(directory = directory))
                )
            if (!isCurrent(live, run)) return@launch
            when (made) {
                is Outcome.Failed ->
                    mutable.update { it.copy(creating = false, createFailure = made.failure) }
                is Outcome.Ok -> {
                    opened.remember(live.profile, directory)
                    if (!isCurrent(live, run)) return@launch
                    sessions = listOf(made.value) + sessions
                    mutable.update { it.copy(creating = false) }
                    publish(ProjectPagePhase.Ready)
                    // The saved model default is best-effort: the chat exists on the server
                    // already, so it always opens (Flutter never blocks a new chat on the model).
                    signals.applyNewChatDefault(live, made.value.id)
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
        devServer?.close()
        isolatedLaunch?.close()
        super.onCleared()
    }

    override fun openConversation(ref: ConversationRef): Unit = nav.openChat(ref)

    override fun developmentServices() {
        val live = server ?: return
        val run = generation
        viewModelScope.launch {
            if (checkStorage(live, run)) nav.open(LinuxRoute.Services(route.profile, directory))
        }
    }

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
