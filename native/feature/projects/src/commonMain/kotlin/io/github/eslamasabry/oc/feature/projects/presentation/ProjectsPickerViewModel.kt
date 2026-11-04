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
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Projects (07 §5 D15 + D16's first page). The server's projects come from its project list when it
 * has one (`projectManagement`), otherwise from the folders its conversations work in. Opening a
 * folder or a project goes to that project's page, which remembers it under Opened before.
 */
@AssistedInject
public class ProjectsPickerViewModel(
    @Assisted private val route: ProjectsRoute.Picker,
    private val nav: AppNavigator,
    private val connections: ProjectServers,
    private val opened: OpenedFolders,
    private val discovery: PhoneProjectDiscovery,
    private val creator: ProjectCreation = NoProjectCreation(),
    private val phoneFolders: PhoneFolders = NoPhoneFolders(),
) : ViewModel(), ProjectsPickerActions {
    private val mutable = MutableStateFlow(ProjectsPickerUi())
    public val state: StateFlow<ProjectsPickerUi> = mutable.asStateFlow()

    private var servers: List<ProjectServer> = emptyList()
    private var chosen: ProfileId? = route.profile
    private var all: List<FolderRowUi> = emptyList()
    private var generation = 0
    private var scanJob: kotlinx.coroutines.Job? = null
    private var phoneGeneration = 0

    init {
        viewModelScope.launch {
            connections.servers.collect { live ->
                servers = live
                val keep = chosen?.takeIf { c -> live.any { it.profile == c } }
                val next =
                    keep
                        ?: connections.active.value?.takeIf { a -> live.any { it.profile == a } }
                        ?: live.firstOrNull()?.profile
                val changed = next != chosen || state.value.phase == ProjectsPhase.NoServer
                chosen = next
                if (changed) load()
            }
        }
        // Back from a project page: that page remembered its folder, so Opened before is read again
        // (B-ANDR-078).
        viewModelScope.launch { nav.top.collect { if (it == route) rereadOpened() } }
    }

    private fun rereadOpened() {
        val server = current() ?: return
        if (state.value.phase != ProjectsPhase.Ready) return
        val before = ProjectRules.openedBefore(opened.recent(server.profile))
        mutable.update { ui ->
            ui.copy(
                openedBefore =
                    before.map { FolderRowUi(it, ProjectRules.name(it)) }.toImmutableList(),
                pathStart = ui.pathStart.ifEmpty { pathStart(before) },
            )
        }
    }

    private fun pathStart(before: List<String>): String =
        before.firstOrNull()?.substringBeforeLast('/', "")?.ifEmpty { null }?.plus("/").orEmpty()

    private fun current(): ProjectServer? = servers.firstOrNull { it.profile == chosen }

    private fun load() {
        scanJob?.cancel()
        phoneGeneration++
        val server = current()
        val run = ++generation
        if (server == null) {
            mutable.value = ProjectsPickerUi(phase = ProjectsPhase.NoServer)
            return
        }
        val before = ProjectRules.openedBefore(opened.recent(server.profile))
        all = emptyList()
        mutable.value =
            ProjectsPickerUi(
                phase = ProjectsPhase.Ready,
                servers = choices(),
                serverName = server.name,
                openedBefore =
                    before.map { FolderRowUi(it, ProjectRules.name(it)) }.toImmutableList(),
                projectsLoading = true,
                canFindPhoneProjects = discovery.available(server.profile),
                pathStart = pathStart(before),
                canCreate = creator.available(server),
                canBrowsePhone = server.phoneFolders,
                canBrowseBuiltin =
                    server.phoneFolders && phoneFolders.builtinAvailable(server.profile),
            )
        viewModelScope.launch {
            val read = readProjects(server)
            if (run != generation) return@launch
            when (read) {
                is Outcome.Ok -> {
                    all = read.value
                    mutable.update { it.copy(projectsLoading = false, projectsFailure = null) }
                    publish()
                }
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(projectsLoading = false, projectsFailure = read.failure)
                    }
            }
        }
    }

    private fun choices(): ImmutableList<ServerChoiceUi> =
        (if (servers.size > 1)
                servers.map { ServerChoiceUi(it.profile, it.name, it.profile == chosen) }
            else emptyList())
            .toImmutableList()

    /**
     * Conversations first (for counts and for servers without a project list), then the project
     * list.
     */
    private suspend fun readProjects(server: ProjectServer): Outcome<List<FolderRowUi>> {
        val sessions = mutableListOf<Session>()
        var cursor: String? = null
        var pages = 0
        do {
            when (val page = server.gateway.sessionPage(cursor = cursor)) {
                is Outcome.Failed -> return page
                is Outcome.Ok -> {
                    sessions += page.value.items
                    cursor = page.value.nextCursor
                }
            }
            pages++
        } while (cursor != null && pages < ProjectRules.PROJECT_PAGES)
        val running: Set<SessionId> =
            (server.gateway.sessionStatuses() as? Outcome.Ok)
                ?.value
                ?.filterValues(ProjectRules::isRunning)
                ?.keys
                .orEmpty()
        val ops = server.operations
        val summaries =
            if (ops != null && server.gateway.capabilities.value.projectManagement) {
                when (val listed = ops.listProjects()) {
                    is Outcome.Ok -> ProjectRules.fromServer(listed.value, sessions, running)
                    // A server that says it has no project list is read from its conversations
                    // instead.
                    is Outcome.Failed ->
                        if (listed.failure is Failure.Unavailable)
                            ProjectRules.fromSessions(sessions, running)
                        else return listed
                }
            } else {
                ProjectRules.fromSessions(sessions, running)
            }
        return Outcome.Ok(
            summaries.map {
                FolderRowUi(it.directory, it.name, it.conversations, it.running, it.isGit)
            }
        )
    }

    private fun publish() {
        val q = state.value.query.trim().lowercase()
        val shown =
            if (q.isEmpty()) all
            else
                all.filter {
                    it.name.lowercase().contains(q) || it.directory.lowercase().contains(q)
                }
        mutable.update {
            it.copy(
                projects = shown.toImmutableList(),
                showSearch = all.size > ProjectRules.SEARCH_FROM || q.isNotEmpty(),
            )
        }
    }

    override fun findPhoneProjects() {
        val profile = chosen ?: return
        if (!discovery.available(profile) || state.value.findingPhoneProjects) return
        mutable.update {
            it.copy(
                findingPhoneProjects = true,
                scanEnd = null,
                phoneProjects = kotlinx.collections.immutable.persistentListOf(),
            )
        }
        scanJob = viewModelScope.launch {
            try {
                val found = discovery.scan(profile)
                if (chosen == profile)
                    mutable.update {
                        it.copy(
                            phoneProjects =
                                found.projects
                                    .map { p ->
                                        FolderRowUi(
                                            p.path,
                                            ProjectRules.name(p.path),
                                            isGit = p.hasGit,
                                            kind = p.kind,
                                        )
                                    }
                                    .toImmutableList(),
                            scanEnd = found.end,
                        )
                    }
            } finally {
                if (chosen == profile) mutable.update { it.copy(findingPhoneProjects = false) }
            }
        }
    }

    override fun cancelPhoneScan() {
        scanJob?.cancel()
    }

    override fun back() {
        // ANDR-1: inside the phone folder browser Back leaves the browser first.
        if (state.value.phonePath != null) {
            closePhoneFolders()
            return
        }
        scanJob?.cancel()
        nav.back()
    }

    override fun browsePhone() {
        if (current()?.phoneFolders != true) return
        mutable.update { it.copy(browsingBuiltin = false) }
        enterPhoneFolder(PHONE_ROOT)
    }

    override fun browseBuiltin() {
        val server = current() ?: return
        if (!server.phoneFolders || !phoneFolders.builtinAvailable(server.profile)) return
        mutable.update { it.copy(browsingBuiltin = true) }
        enterPhoneFolder("/root/projects")
    }

    override fun enterPhoneFolder(path: String) {
        val server = current() ?: return
        val builtin = state.value.browsingBuiltin
        if (!server.phoneFolders || ".." in path.split('/') || '\u0000' in path) return
        if (builtin) {
            if (!phoneFolders.builtinAvailable(server.profile) || !path.startsWith('/')) return
        } else if (path != PHONE_ROOT && !path.startsWith("$PHONE_ROOT/")) return
        val run = ++phoneGeneration
        mutable.update {
            it.copy(
                phonePath = path,
                phoneParent =
                    if (path == (if (builtin) "/" else PHONE_ROOT)) null
                    else path.substringBeforeLast('/').ifEmpty { "/" },
                phoneFolders = persistentListOf(),
                phoneLoading = true,
                phoneFailure = null,
            )
        }
        viewModelScope.launch {
            val result = if (builtin) phoneFolders.listBuiltin(path) else phoneFolders.list(path)
            if (
                run != phoneGeneration ||
                    current()?.profile != server.profile ||
                    current()?.gateway !== server.gateway
            )
                return@launch
            when (result) {
                is Outcome.Ok ->
                    mutable.update { it.copy(phoneFolders = result.value, phoneLoading = false) }
                is Outcome.Failed ->
                    mutable.update { it.copy(phoneFailure = result.failure, phoneLoading = false) }
            }
        }
    }

    override fun closePhoneFolders() {
        phoneGeneration++
        mutable.update {
            it.copy(
                phonePath = null,
                phoneFolders = persistentListOf(),
                phoneFailure = null,
                phoneLoading = false,
            )
        }
    }

    override fun allowPhoneFolderAccess() {
        if (!state.value.browsingBuiltin) viewModelScope.launch { phoneFolders.requestAccess() }
    }

    override fun usePhoneFolder() {
        val path = state.value.phonePath ?: return
        if (
            path == (if (state.value.browsingBuiltin) "/" else PHONE_ROOT) ||
                state.value.phoneLoading ||
                state.value.phoneFailure != null ||
                current()?.phoneFolders != true
        )
            return
        closePhoneFolders()
        openProject(path)
    }

    private companion object {
        const val PHONE_ROOT = "/storage/emulated/0"
    }

    override fun chooseServer(profile: ProfileId) {
        if (profile == chosen || state.value.creatingFolder) return
        chosen = profile
        load()
    }

    override fun askPath(): Unit = mutable.update { it.copy(askingPath = true) }

    override fun askCreate() {
        val server = current() ?: return
        if (creator.available(server))
            mutable.update { it.copy(askingCreate = true, folderFailure = null) }
    }

    override fun changeCreateName(name: String) {
        if (!state.value.creatingFolder)
            mutable.update { it.copy(createName = name, folderFailure = null) }
    }

    override fun closeCreate() {
        if (!state.value.creatingFolder) mutable.update { it.copy(askingCreate = false) }
    }

    override fun createFolder() {
        val server = current() ?: return
        if (state.value.creatingFolder || !creator.available(server)) return
        val name =
            io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName.validated(
                state.value.createName
            ) ?: return
        val run = generation
        mutable.update { it.copy(creatingFolder = true, folderFailure = null) }
        viewModelScope.launch {
            val result = creator.create(server, name)
            if (run != generation || current()?.gateway !== server.gateway) return@launch
            when (result) {
                is Outcome.Ok -> {
                    val saved = opened.remember(server.profile, result.value)
                    if (run != generation || current()?.gateway !== server.gateway) return@launch
                    mutable.update {
                        it.copy(askingCreate = saved is Outcome.Failed, creatingFolder = false)
                    }
                    when (saved) {
                        is Outcome.Ok ->
                            nav.open(ProjectsRoute.Project(server.profile, result.value))
                        is Outcome.Failed ->
                            mutable.update { it.copy(folderFailure = saved.failure) }
                    }
                }
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(creatingFolder = false, folderFailure = result.failure)
                    }
            }
        }
    }

    override fun finishPath(path: String?) {
        mutable.update { it.copy(askingPath = false) }
        if (path == null || ProjectRules.pathProblem(path) != null) return
        openProject(ProjectRules.normalize(path))
    }

    override fun openProject(directory: String) {
        val profile = chosen ?: return
        nav.open(ProjectsRoute.Project(profile, ProjectRules.normalize(directory)))
    }

    override fun search(query: String) {
        mutable.update { it.copy(query = query) }
        publish()
    }

    override fun retry(): Unit = load()

    override fun openServers(): Unit = nav.open(ServersRoute.Home)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ProjectsRoute.Picker): ProjectsPickerViewModel
    }
}
