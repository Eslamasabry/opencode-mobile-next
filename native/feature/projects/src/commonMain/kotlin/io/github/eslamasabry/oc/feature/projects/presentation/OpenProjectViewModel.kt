package io.github.eslamasabry.oc.feature.projects.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.LocatedFileGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName
import io.github.eslamasabry.oc.engine.git.gitRemoteAddressProblem
import io.github.eslamasabry.oc.engine.git.isValidGitFolderName
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * B-182 "Open a project", as Flutter's folder browser: where the project is (each connected server and how it answers
 * now), that server's projects (its own list, or the folders its conversations work in; on this phone also every folder
 * in /root/projects), then the folders inside one. A server's folders are read through the gateway located at the
 * project (`GET /api/fs/list?location[directory]=<project>&path=<inside>`, Flutter's fsList), so no folder outside a
 * project boots a server instance; this phone's own Linux is read from its files. New folder and Clone a repository
 * make the folder on that server ([io.github.eslamasabry.oc.core.domain.ServerOperationsGateway]); Type a path is
 * always there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class OpenProjectViewModel(
    private val nav: AppNavigator,
    private val connections: ProjectServers,
    private val opened: OpenedFolders,
    private val creator: ProjectCreation = NoProjectCreation(),
    private val phoneFolders: PhoneFolders = NoPhoneFolders(),
    private val lastKnown: ProjectsLastKnown = NoProjectsLastKnown(),
) : ViewModel(), OpenProjectActions {
    private val mutable = MutableStateFlow(OpenProjectUi())
    public val state: StateFlow<OpenProjectUi> = mutable.asStateFlow()

    private var servers: List<ProjectServer> = emptyList()
    private var chosen: ProfileId? = null

    /** The project a [OpenProjectStep.Folder] page sits in: its listing is located there. */
    private var root: String? = null
    private var generation = 0
    private var slowJob: Job? = null
    private val runner = NewProjectRunner(creator, opened)

    init {
        viewModelScope.launch {
            connections.servers
                .flatMapLatest { live ->
                    if (live.isEmpty()) flowOf(live to emptyList<StreamStatus>())
                    else combine(live.map { it.status ?: flowOf<StreamStatus>(StreamStatus.Connected) }) { live to it.toList() }
                }
                .collect { (live, statuses) ->
                    servers = live
                    mutable.update { ui ->
                        ui.copy(
                            serversLoading = false,
                            recent = recentAcross(live),
                            servers =
                                live
                                    .mapIndexed { i, s ->
                                        OpenServerUi(s.profile, s.name, s.phoneFolders, statusOf(statuses[i]))
                                    }
                                    .toImmutableList(),
                        )
                    }
                    // The server whose page is shown went away: back to where the project is.
                    if (chosen != null && live.none { it.profile == chosen }) showServers()
                }
        }
    }

    private fun statusOf(status: StreamStatus): OpenServerStatus =
        when (status) {
            StreamStatus.Connected -> OpenServerStatus.Connected
            StreamStatus.Connecting,
            is StreamStatus.Reconnecting,
            StreamStatus.Suspended -> OpenServerStatus.Connecting
            is StreamStatus.Disconnected,
            StreamStatus.WaitingForNetwork,
            is StreamStatus.Stopped -> OpenServerStatus.NotConnected
        }

    private fun current(): ProjectServer? = servers.firstOrNull { it.profile == chosen }

    private fun builtin(server: ProjectServer): Boolean =
        server.phoneFolders && phoneFolders.builtinAvailable(server.profile)

    private fun canMake(server: ProjectServer): Boolean =
        (builtin(server) && creator.available(server)) ||
            server.operations != null && server.gateway.capabilities.value.projectFolderCreation

    private fun canClone(server: ProjectServer): Boolean =
        server.operations != null && server.gateway.capabilities.value.projectFolderCreation

    private fun showServers() {
        generation++
        slowJob?.cancel()
        chosen = null
        root = null
        mutable.update {
            OpenProjectUi(servers = it.servers, serversLoading = it.serversLoading, recent = recentAcross(servers))
        }
    }

    /**
     * Flutter shows the projects opened before first. Each server keeps its own list, most recent first; with several
     * servers they follow in the servers' order, the first [ProjectRules.OPENED_BEFORE] in all, each naming its server.
     */
    private fun recentAcross(live: List<ProjectServer>): kotlinx.collections.immutable.ImmutableList<OpenRecentUi> =
        live
            .flatMap { server ->
                ProjectRules.openedBefore(opened.recent(server.profile)).map { dir ->
                    OpenRecentUi(server.profile, ProjectRules.name(dir), dir, server.name.takeIf { live.size > 1 })
                }
            }
            .take(ProjectRules.OPENED_BEFORE)
            .toImmutableList()

    override fun openRecent(profile: ProfileId, path: String) {
        if (servers.none { it.profile == profile }) return
        val normal = ProjectRules.normalize(path)
        if (ProjectRules.pathProblem(normal) != null) return
        generation++
        nav.open(ProjectsRoute.Project(profile, normal))
    }

    override fun chooseServer(profile: ProfileId) {
        val server = servers.firstOrNull { it.profile == profile } ?: return
        chosen = profile
        root = null
        val before = ProjectRules.openedBefore(opened.recent(profile))
        mutable.update {
            it.copy(
                step = OpenProjectStep.Projects,
                refusedPath = null,
                serverName = server.name,
                onThisPhone = server.phoneFolders,
                path = null,
                canShowHidden = false,
                openedBefore = before.map { d -> OpenFolderUi(ProjectRules.name(d), d) }.toImmutableList(),
                canMake = canMake(server),
                canClone = canClone(server),
                makeIn = if (builtin(server)) PROJECT_SPACE else null,
                pathStart = before.firstOrNull()?.substringBeforeLast('/', "")?.ifEmpty { null }?.plus("/").orEmpty(),
                dialog = null,
                missingPath = null,
            )
        }
        list(server, null) { projects(server) }
    }

    override fun enterFolder(path: String) {
        val server = current() ?: return
        val target = ProjectRules.normalize(path)
        if (ProjectRules.pathProblem(target) != null || '\u0000' in target) return
        // Going in from the projects page starts a project; deeper folders stay in it.
        val inside = root?.let { target == it || target.startsWith("$it/") } == true
        if (state.value.step == OpenProjectStep.Projects || !inside) root = target
        mutable.update {
            it.copy(
                step = OpenProjectStep.Folder,
                refusedPath = null,
                path = target,
                makeIn = target,
                pathStart = "$target/",
                dialog = null,
                canShowHidden = builtin(server),
            )
        }
        list(server, listingKey(target)) { folders(server, root ?: target, target) }
    }

    override fun back() {
        val ui = state.value
        if (ui.dialog != null) return closeDialog()
        when (ui.step) {
            OpenProjectStep.Servers -> {
                generation++
                nav.back()
            }
            OpenProjectStep.Projects -> showServers()
            OpenProjectStep.Folder -> {
                val path = ui.path ?: return
                val top = root
                val server = current() ?: return showServers()
                if (top == null || path == top) chooseServer(server.profile)
                else enterFolder(path.substringBeforeLast('/').ifEmpty { "/" })
            }
        }
    }

    /** Runs one listing: skeleton rows, "Still reading" after [SLOW_AFTER_MS], then the folders or the problem. */
    private fun list(server: ProjectServer, key: String?, read: suspend () -> Outcome<List<OpenFolderUi>>) {
        val run = ++generation
        slowJob?.cancel()
        // Cache-first: the listing this page last drew comes back at once; skeleton rows are for a first-ever listing.
        val kept = lastKnown.peekFolders(server.profile, key)?.takeIf { it.isNotEmpty() }
        mutable.update {
            it.copy(
                loading = kept == null,
                slow = false,
                failure = null,
                folders = kept?.toImmutableList() ?: persistentListOf(),
            )
        }
        if (kept == null) {
            slowJob = viewModelScope.launch {
                delay(SLOW_AFTER_MS)
                if (run == generation) mutable.update { it.copy(slow = true) }
            }
            viewModelScope.launch {
                val stored = lastKnown.readFolders(server.profile, key)?.takeIf { it.isNotEmpty() } ?: return@launch
                if (run != generation || !state.value.loading) return@launch
                mutable.update { it.copy(loading = false, slow = false, folders = stored.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            val result = read()
            if (run != generation || current()?.gateway !== server.gateway) return@launch
            slowJob?.cancel()
            mutable.update {
                when (result) {
                    is Outcome.Ok ->
                        it.copy(loading = false, slow = false, folders = result.value.toImmutableList())
                    is Outcome.Failed -> it.copy(loading = false, slow = false, failure = result.failure)
                }
            }
            if (result is Outcome.Ok) lastKnown.keepFolders(server.profile, key, result.value)
        }
    }

    /** What a folder's listing is kept under; hidden folders listed too make a different listing. */
    private fun listingKey(path: String): String = if (state.value.showHidden) "$path|hidden" else path

    /** The server's projects, newest first; on this phone the folders of /root/projects follow. */
    private suspend fun projects(server: ProjectServer): Outcome<List<OpenFolderUi>> {
        val ops = server.operations
        val listed: List<OpenFolderUi> =
            if (ops != null && server.gateway.capabilities.value.projectManagement) {
                when (val read = ops.listProjects()) {
                    is Outcome.Ok ->
                        ProjectRules.fromServer(read.value, emptyList(), emptySet()).map {
                            OpenFolderUi(it.name, it.directory, git = it.isGit == true, known = true)
                        }
                    is Outcome.Failed ->
                        if (read.failure is Failure.Unavailable) fromSessions(server) ?: return sessionsFailure
                        else return read
                }
            } else {
                fromSessions(server) ?: return sessionsFailure
            }
        val space =
            if (builtin(server))
                (phoneFolders.listBuiltin(PROJECT_SPACE) as? Outcome.Ok)?.value.orEmpty().map {
                    OpenFolderUi(it.name, ProjectRules.normalize(it.path))
                }
            else emptyList()
        val known = listed.map { it.path }.toSet()
        val all = (listed + space.filter { it.path !in known }).filter { ProjectRules.pathProblem(it.path) == null }
        // Where a new folder goes on a computer: beside its newest Git project, as New project does (N-41).
        if (!builtin(server)) {
            val beside =
                all.filter { it.git && it.path.count { c -> c == '/' } >= 2 }
                    .ifEmpty { all.filter { it.path.count { c -> c == '/' } >= 2 } }
                    .firstOrNull()
                    ?.path
                    ?.substringBeforeLast('/')
            mutable.update { if (it.step == OpenProjectStep.Projects) it.copy(makeIn = beside) else it }
        }
        return Outcome.Ok(all.distinctBy { it.path })
    }

    private var sessionsFailure: Outcome.Failed = Outcome.Failed(Failure.Unknown())

    private suspend fun fromSessions(server: ProjectServer): List<OpenFolderUi>? {
        val sessions = mutableListOf<Session>()
        var cursor: String? = null
        var pages = 0
        do {
            when (val page = server.gateway.sessionPage(cursor = cursor)) {
                is Outcome.Failed -> {
                    sessionsFailure = page
                    return null
                }
                is Outcome.Ok -> {
                    sessions += page.value.items
                    cursor = page.value.nextCursor
                }
            }
            pages++
        } while (cursor != null && pages < ProjectRules.PROJECT_PAGES)
        return ProjectRules.fromSessions(sessions, emptySet()).map {
            OpenFolderUi(it.name, it.directory, git = it.isGit == true, known = true)
        }
    }

    /**
     * The folders inside [path], hidden ones left out unless asked for (this phone only), by name. This phone's Linux is read from its files; a computer's
     * server lists [path] relative to the project [top] it sits in.
     */
    private suspend fun folders(server: ProjectServer, top: String, path: String): Outcome<List<OpenFolderUi>> {
        if (builtin(server)) {
            return when (val read = phoneFolders.listBuiltin(path)) {
                is Outcome.Failed -> read
                is Outcome.Ok ->
                    Outcome.Ok(
                        read.value
                            .filter { state.value.showHidden || !it.name.startsWith('.') }
                            .map { OpenFolderUi(it.name, ProjectRules.normalize(it.path)) }
                            .sortedBy { it.name.lowercase() }
                    )
            }
        }
        val located =
            server.gateway as? LocatedFileGateway
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))
        val inside = path.removePrefix(top).trimStart('/').ifEmpty { "." }
        return when (val read = located.filesAt(ServerLocation(top)).listFiles(inside)) {
            is Outcome.Failed -> read
            is Outcome.Ok -> {
                val names = read.value.filter { it.isDir }.map { it.name.trimEnd('/').substringAfterLast('/') }
                Outcome.Ok(
                    names
                        .filter { it.isNotEmpty() && !it.startsWith('.') }
                        .distinct()
                        .sortedBy { it.lowercase() }
                        .map { OpenFolderUi(it, "${path.trimEnd('/')}/$it") }
                )
            }
        }
    }

    override fun toggleHidden() {
        val server = current() ?: return
        if (!state.value.canShowHidden || state.value.step != OpenProjectStep.Folder) return
        val path = state.value.path ?: return
        mutable.update { it.copy(showHidden = !it.showHidden) }
        list(server, listingKey(path)) { folders(server, root ?: path, path) }
    }

    override fun openShown() {
        val path = state.value.path ?: return
        if (state.value.step != OpenProjectStep.Folder || state.value.loading) return
        // B-106: home and root cannot be projects; say so on the page instead of doing nothing.
        if (ProjectRules.pathProblem(ProjectRules.normalize(path)) != null) {
            mutable.update { it.copy(refusedPath = path) }
            return
        }
        openFolder(path)
    }

    override fun openFolder(path: String) {
        val profile = chosen ?: return
        val normal = ProjectRules.normalize(path)
        if (ProjectRules.pathProblem(normal) != null) return
        generation++
        nav.open(ProjectsRoute.Project(profile, normal))
    }

    override fun retry() {
        val server = current() ?: return
        val ui = state.value
        when (ui.step) {
            OpenProjectStep.Servers -> Unit
            OpenProjectStep.Projects -> list(server, null) { projects(server) }
            OpenProjectStep.Folder -> {
                val path = ui.path ?: return
                list(server, listingKey(path)) { folders(server, root ?: path, path) }
            }
        }
    }

    override fun askNewFolder() {
        val server = current() ?: return
        if (canMake(server) && state.value.makeIn != null)
            mutable.update { it.copy(dialog = OpenProjectDialog.NewFolder, dialogFailure = null) }
    }

    override fun askClone() {
        val server = current() ?: return
        if (canClone(server) && state.value.makeIn != null)
            mutable.update { it.copy(dialog = OpenProjectDialog.Clone, dialogFailure = null) }
    }

    override fun askPath() {
        if (current() != null) mutable.update { it.copy(dialog = OpenProjectDialog.Path, dialogFailure = null) }
    }

    override fun closeDialog() {
        if (!state.value.working) mutable.update { it.copy(dialog = null, dialogFailure = null) }
    }

    override fun createFolder(name: String) {
        val server = current() ?: return
        val parent = state.value.makeIn ?: return
        val folder = ProjectFolderName.validated(name) ?: return
        if (state.value.working || !canMake(server)) return
        val target = "${parent.trimEnd('/')}/$folder"
        work(server) {
            if (builtin(server) && creator.available(server)) runner.createAt(server, target)
            else remember(server, server.operations!!.makeProjectFolder(parent, folder))
        }
    }

    override fun cloneRepository(url: String, name: String) {
        val server = current() ?: return
        val parent = state.value.makeIn ?: return
        val folder = name.trim()
        if (gitRemoteAddressProblem(url) != null || !isValidGitFolderName(folder)) return
        if (state.value.working || !canClone(server)) return
        work(server) { remember(server, server.operations!!.cloneRepository(parent, url.trim(), folder)) }
    }

    private suspend fun remember(server: ProjectServer, made: Outcome<String>): Outcome<String> =
        when (made) {
            is Outcome.Failed -> made
            is Outcome.Ok -> {
                opened.remember(server.profile, made.value)
                made
            }
        }

    /** One write in a dialog: it stays open with the reason if it fails, and the new folder opens if it works. */
    private fun work(server: ProjectServer, block: suspend () -> Outcome<String>) {
        mutable.update { it.copy(working = true, dialogFailure = null) }
        viewModelScope.launch {
            val result = block()
            if (current()?.gateway !== server.gateway) return@launch
            when (result) {
                is Outcome.Ok -> {
                    mutable.update { it.copy(working = false, dialog = null, missingPath = null) }
                    openFolder(result.value)
                }
                is Outcome.Failed -> mutable.update { it.copy(working = false, dialogFailure = result.failure) }
            }
        }
    }

    override fun finishPath(path: String?) {
        mutable.update { it.copy(dialog = null, refusedPath = null) }
        if (path == null) return
        // Home and root cannot be projects: say so on the page (the same words as the Open refusal), never silence.
        if (ProjectRules.pathProblem(path) == PathProblem.HomeOrRoot) {
            mutable.update { it.copy(refusedPath = ProjectRules.normalize(path)) }
            return
        }
        if (ProjectRules.pathProblem(path) != null) return
        val server = current() ?: return
        val normal = ProjectRules.normalize(path)
        if (!(builtin(server) && creator.available(server))) return openFolder(normal)
        // This phone's Linux is asked first, so a typo is not remembered as a project.
        viewModelScope.launch {
            val there = creator.exists(server, normal)
            if (current()?.gateway !== server.gateway) return@launch
            if (there == false) mutable.update { it.copy(missingPath = normal, dialogFailure = null) }
            else openFolder(normal)
        }
    }

    override fun createMissing() {
        val server = current() ?: return
        val path = state.value.missingPath ?: return
        if (state.value.working || !creator.available(server)) return
        work(server) { runner.createAt(server, path) }
    }

    override fun dismissMissing() {
        if (!state.value.working) mutable.update { it.copy(missingPath = null, dialogFailure = null) }
    }

    override fun openServers(): Unit = nav.open(ServersRoute.Home)

    internal companion object {
        /** Where this phone's projects live (Flutter managedProjectsDirectory). */
        const val PROJECT_SPACE = "/root/projects"

        /** Flutter KitMotion.escalateAfter: after this a listing says it is still reading. */
        const val SLOW_AFTER_MS = 3_000L
    }
}
