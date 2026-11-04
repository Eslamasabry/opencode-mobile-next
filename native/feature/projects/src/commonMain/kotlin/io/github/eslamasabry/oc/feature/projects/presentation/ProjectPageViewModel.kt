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
    private var isolated: IsolatedTaskLaunch? = null
    private var attentionWatch: Job? = null

    init {
        viewModelScope.launch {
            connections.servers.collect { live ->
                val next = live.firstOrNull { it.profile == route.profile }
                if (!adopted || next?.gateway !== server?.gateway) {
                    adopted = true
                    isolated?.cancel()
                    isolated = null
                    mutable.update {
                        it.copy(
                            isolated = null,
                            elsewhereAttention = kotlinx.collections.immutable.persistentListOf(),
                            canIsolate =
                                next?.gateway?.capabilities?.value?.worktreeCreate == true &&
                                    next?.operations != null,
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
                    load()
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

    override fun startIsolated() {
        val live = server ?: return
        val operations = live.operations ?: return
        if (!live.gateway.capabilities.value.worktreeCreate || isolated != null) return
        val generation = this.generation
        val location = live.gateway.location.value
        val project =
            sessions
                .firstOrNull { it.directory?.let(ProjectRules::normalize) == directory }
                ?.projectId
                ?.raw
        val launch =
            IsolatedTaskLaunch(
                projectId = project,
                events = live.gateway.events.all,
                scope = viewModelScope,
                current = {
                    server?.gateway === live.gateway &&
                        this.generation == generation &&
                        live.gateway.location.value == location
                },
                create = { operations.createWorktree(directory) },
                open = { copyDirectory ->
                    live.gateway.createSession(
                        NewSession(location = ServerLocation(directory = copyDirectory))
                    )
                },
                opened = { session ->
                    viewModelScope.launch {
                        session.directory?.let { opened.remember(live.profile, it) }
                    }
                    nav.openChat(ConversationRef(live.profile, session.id), focusComposer = true)
                },
            )
        isolated = launch
        viewModelScope.launch {
            launch.state.collect { state ->
                if (isolated === launch) mutable.update { it.copy(isolated = state) }
            }
        }
        launch.start()
    }

    override fun openIsolated(acceptUnconfirmed: Boolean, acceptFailedSetup: Boolean) {
        isolated?.open(acceptUnconfirmed, acceptFailedSetup)
    }

    override fun keepWaitingForCopy(): Unit = isolated?.keepWaiting() ?: Unit

    override fun cancelIsolated(): Unit = isolated?.cancel() ?: Unit

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

    override fun openConversation(ref: ConversationRef): Unit = nav.openChat(ref)

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
