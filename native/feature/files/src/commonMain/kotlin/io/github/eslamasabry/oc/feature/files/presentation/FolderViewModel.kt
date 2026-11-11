package io.github.eslamasabry.oc.feature.files.presentation

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
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.core.model.FindMatch
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.WorkspaceSymbol
import io.github.eslamasabry.oc.engine.files.FilePaths
import io.github.eslamasabry.oc.engine.files.FolderListing
import io.github.eslamasabry.oc.engine.files.ProjectFiles
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FilesPlace
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.DevServicesRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.ReviewRoute
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public enum class FileSearchSurface {
    Files,
    Text,
    Symbols,
}

public enum class FolderPhase {
    Loading,
    Ready,
    Failed,
    NotConnected,
    Unavailable,
}

/** One folder of the conversation's project. [path] "" is the root; the screen words the title. */
public data class FolderUi(
    val path: String,
    val phase: FolderPhase = FolderPhase.Loading,
    val entries: ImmutableList<FileNode> = persistentListOf(),
    val sourceFirst: Boolean = false,
    val preferencesFailed: Boolean = false,
    val hiddenCount: Int = 0,
    val showHidden: Boolean = false,
    val failure: Failure? = null,
    /** Non-null while a name search is shown instead of the folder. */
    val query: String? = null,
    val searching: Boolean = false,
    val results: ImmutableList<FileNode> = persistentListOf(),
    val searchFailure: Failure? = null,
    val surface: FileSearchSurface = FileSearchSurface.Files,
    val canSearchText: Boolean = false,
    val canSearchSymbols: Boolean = false,
    val textMatches: ImmutableList<FindMatch> = persistentListOf(),
    val symbols: ImmutableList<WorkspaceSymbol> = persistentListOf(),
    val resultsLimited: Boolean = false,
    val canOrder: Boolean = false,
    val changes: ImmutableMap<String, VersionControlFile> = persistentMapOf(),
    val changesFailure: Failure? = null,
    val actionsFor: FileNode? = null,
    val clipId: Long? = null,
    val clipText: String? = null,
    val canAttach: Boolean = false,
    val attaching: Boolean = false,
    val attachNotice: FileNotice? = null,
    /** The file just added as a reference, for the notice's words. */
    val referencedName: String? = null,
    val storageBlock: FilesStorageBlock = FilesStorageBlock.None,
    val requestingStorage: Boolean = false,
    val confirmingStorage: Boolean = false,
    val storageResult: FilesStorageResult? = null,
    val actionFailure: Failure? = null,
    val canTerminal: Boolean = false,
    /** Running terminals of this project, for the Terminal action's words; null while unknown. */
    val runningTerminals: Int? = null,
    /** The project's other pages for the ⋯ menu; none until the project's folder is known. */
    val tools: FilesProjectTools = FilesProjectTools.None,
    /** The project folder's own name ("my-app"), once the server says where the project is; null until then. */
    val projectName: String? = null,
    /**
     * False for a project opened without a conversation (the project page's Files row): there is no
     * draft to ask in, reference into or attach to, and no conversation review.
     */
    val inConversation: Boolean = true,
    /** The rows are the last ones seen and the server could not be reached to refresh them (status slot). */
    val offline: Boolean = false,
) {
    val name: String
        get() = FilePaths.name(path)

    val onlyHidden: Boolean
        get() = phase == FolderPhase.Ready && entries.isEmpty() && hiddenCount > 0

    val empty: Boolean
        get() = phase == FolderPhase.Ready && entries.isEmpty() && hiddenCount == 0
}

public interface FolderActions {
    public fun terminal() {}

    /** The whole conversation's "Changes" review (Flutter project hub `ProjectTool.changes`). */
    public fun reviewChanges() {}

    /** The ⋯ menu's project tools (Flutter project_hub_screen.dart `_toolsMenu`). */
    public fun projectHealth() {}

    public fun worktrees() {}

    public fun devServices() {}

    public fun switchProject() {}

    public fun back()

    public fun retry()

    public fun requestStorageAccess(): Unit = retry()

    public fun open(node: FileNode)

    public fun toggleSourceFirst()

    public fun toggleHidden()

    /** Fuzzy name search across the project; blank shows the folder again. */
    public fun search(query: String)

    public fun selectSurface(surface: FileSearchSurface) {}

    public fun sourceFirst() {}

    public fun openMatch(match: FindMatch) {}

    public fun openSymbol(symbol: WorkspaceSymbol) {}

    public fun showActions(node: FileNode) {}

    public fun closeActions() {}

    public fun copyPath() {}

    public fun copyName() {}

    public fun clipDone(id: Long) {}

    public fun review() {}

    public fun ask() {}

    public fun attach() {}

    /** Add as reference: the file goes to the draft and the person stays in the list. */
    public fun addReference() {}

    public fun allowStorage() {}

    public fun openPhone() {}

    public fun reviewStorage() {}

    public fun confirmStorage() {}

    public fun cancelStorage() {}
}

/**
 * D02 (07 §5): one folder as a route, so predictive back walks up a level. Every read goes to the
 * conversation's own server; nothing is cached across servers. One read at a time; a newer search
 * supersedes an older one.
 */
@AssistedInject
public class FolderViewModel(
    @Assisted private val route: FilesRoute.AnyFolder,
    private val connections: FilesConnections,
    private val nav: AppNavigator,
    private val preferences: Set<FilesReaderPreferences>,
    private val mentions: ComposerMentions,
    private val storageAccess: Set<FilesStorageAccess>,
    lastKnownPorts: Set<FilesLastKnown> = emptySet(),
) : ViewModel(), FolderActions {
    private val lastKnown = lastKnownPorts.singleOrNull()
    private val keptKey = route.place.keptKey(FilePaths.normalize(route.path))
    private val local = MutableStateFlow(FolderUi(FilePaths.normalize(route.path)))
    public val state: StateFlow<FolderUi> = local.asStateFlow()
    private var all: List<FileNode> = emptyList()
    // Newer reads supersede older ones (Konsist K1 keeps Job out of features: a counter does the
    // same).
    private var loads = 0
    private var searches = 0
    private var clipSequence = 0L
    private var changesReads = 0
    private val files = PlaceFiles(route.place, connections)
    /** The conversation these files belong to; null for a project opened on its own. */
    private val ref = files.conversation
    private val reader = preferences.singleOrNull()?.open(route.place.profileId)

    init {
        local.update {
            it.copy(
                canSearchText = files.canSearchText(),
                canSearchSymbols = files.canSearchSymbols(),
                sourceFirst = reader?.value?.value?.sourceFirst == true,
                canOrder = reader != null,
                canAttach = ref != null && mentions.canAttach(),
                inConversation = ref != null,
            )
        }
        seedFromKept()
        load()
        // The root is titled with the project's own name, not the word "Project" (P-025).
        viewModelScope.launch {
            val directory = files.directory()
            projectDirectory = directory?.takeIf { it.isNotBlank() }
            val name = projectFolderName(directory)
            local.update {
                it.copy(
                    projectName = name ?: it.projectName,
                    tools = if (projectDirectory != null) files.tools() else FilesProjectTools.None,
                )
            }
            if (files.canTerminal()) {
                val running = files.runningTerminals(projectDirectory)
                local.update { it.copy(runningTerminals = running) }
            }
        }
    }

    /** The project's folder on its server, once known; the tools open for exactly this folder. */
    private var projectDirectory: String? = null

    private fun openTool(allowed: (FilesProjectTools) -> Boolean, target: (ProfileId, String) -> AppRoute) {
        val directory = projectDirectory ?: return
        if (!allowed(files.tools())) return
        nav.open(target(route.place.profileId, directory))
    }

    override fun projectHealth(): Unit = openTool({ it.health }) { profile, dir -> SearchRoute.ProjectHealth(
        profile,
        dir,
    ) }

    // Native worktrees live in the project page's workspace sheet (the search index opens it the same way).
    override fun worktrees(): Unit =
        openTool({ it.worktrees }) { profile, dir -> ProjectsRoute.Project(profile, dir, initialWorkspace = true) }

    override fun devServices(): Unit = openTool({ it.services }) { profile, dir -> DevServicesRoute.Home(profile, dir) }

    override fun switchProject() {
        if (files.tools().switchProject) nav.open(ProjectsRoute.Open)
    }

    override fun reviewChanges() {
        ref?.let { nav.open(ReviewRoute.Changes(it)) }
    }

    override fun terminal() {
        if (!files.canTerminal()) return
        when (val place = route.place) {
            is FilesPlace.Conversation -> nav.open(TerminalRoute.Conversation(place.ref))
            is FilesPlace.Project -> nav.open(TerminalRoute.Home(place.profile, place.directory))
        }
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        if (local.value.query != null) search(local.value.query.orEmpty()) else load()
    }

    /** Cache-first: the last listing draws the real rows at once; the live read replaces them in place. */
    private fun seedFromKept() {
        val port = lastKnown ?: return
        val profile = route.place.profileId
        val peeked = port.peekFolder(profile, keptKey)
        if (peeked != null) {
            all = peeked.nodes()
            publish()
            return
        }
        viewModelScope.launch {
            val kept = port.readFolder(profile, keptKey) ?: return@launch
            if (local.value.phase != FolderPhase.Loading) return@launch
            all = kept.nodes()
            publish()
        }
    }

    private fun load() {
        val mine = ++loads
        val gateway = files.gateway()
        val showing = local.value.phase == FolderPhase.Ready
        when {
            gateway == null && showing -> local.update { it.copy(offline = true) }
            gateway == null ->
                local.update { it.copy(phase = FolderPhase.NotConnected, failure = null) }
            !files.canBrowse() ->
                local.update { it.copy(phase = FolderPhase.Unavailable, failure = null) }
            else -> {
                local.update {
                    it.copy(
                        phase = if (showing) FolderPhase.Ready else FolderPhase.Loading,
                        failure = null,
                        storageBlock = FilesStorageBlock.None,
                    )
                }
                viewModelScope.launch {
                    val listed = ProjectFiles.list(gateway, route.path)
                    if (mine != loads || !files.isCurrent(gateway)) return@launch
                    when (listed) {
                        is Outcome.Failed ->
                            local.update {
                                if (showing) it.copy(offline = true)
                                else it.copy(phase = FolderPhase.Failed, failure = listed.failure)
                            }
                        is Outcome.Ok -> {
                            all = listed.value
                            local.update { it.copy(offline = false) }
                            publish()
                            lastKnown?.let { port ->
                                launch { port.keepFolder(route.place.profileId, keptKey, KeptFolder.of(listed.value)) }
                            }
                            loadChanges(mine)
                            checkStorage(mine, gateway)
                        }
                    }
                }
            }
        }
    }

    private fun publish() {
        val ui = local.value
        val entries = withDeleted(all, ui.changes.values, searching = false)
        val listing = FolderListing.of(entries, ui.showHidden, ui.sourceFirst)
        local.update {
            it.copy(
                phase = FolderPhase.Ready,
                entries = listing.entries,
                hiddenCount = listing.hiddenCount,
                failure = null,
                canTerminal = files.canTerminal(),
            )
        }
    }

    /** STATE's name for the one persisted source-first switch (the reader preferences store). */
    override fun toggleSourceFirst(): Unit = sourceFirst()

    override fun open(node: FileNode) {
        if (local.value.changes[node.path]?.status == "deleted") {
            ref?.let { nav.open(ReviewRoute.File(it, node.path, workingTree = true)) }
            return
        }
        if (node.isDir) nav.open(FilesRoute.folder(route.place, node.path))
        else nav.open(FilesRoute.file(route.place, node.path))
    }

    /** One storage flow: the head's checked request (block, directory, stale-scope guards). */
    override fun requestStorageAccess(): Unit = requestStorage()

    override fun toggleHidden() {
        local.update { it.copy(showHidden = !it.showHidden) }
        if (local.value.phase == FolderPhase.Ready) publish()
    }

    override fun search(query: String) {
        val mine = ++searches
        val trimmed = query.trim()
        val surface = local.value.surface
        if (trimmed.isEmpty()) {
            local.update {
                it.copy(
                    query = null,
                    searching = false,
                    results = persistentListOf(),
                    textMatches = persistentListOf(),
                    symbols = persistentListOf(),
                    resultsLimited = false,
                    searchFailure = null,
                )
            }
            return
        }
        val gateway = files.gateway()
        if (gateway == null) {
            local.update {
                it.copy(
                    query = trimmed,
                    searching = false,
                    searchFailure =
                        Failure.Unavailable(
                            io.github.eslamasabry.oc.core.common.FeatureId("connection")
                        ),
                )
            }
            return
        }
        local.update { it.copy(query = trimmed, searching = true, searchFailure = null) }
        viewModelScope.launch {
            when (surface) {
                FileSearchSurface.Files -> {
                    loadChanges(loads)
                    if (mine != searches) return@launch
                    val found = ProjectFiles.find(gateway, trimmed)
                    if (mine != searches || !files.isCurrent(gateway))
                        return@launch
                    local.update {
                        when (found) {
                            is Outcome.Ok ->
                                it.copy(
                                    searching = false,
                                    results =
                                        order(
                                            withDeleted(
                                                found.value,
                                                it.changes.values,
                                                searching = true,
                                            )
                                        ),
                                    searchFailure = null,
                                )
                            is Outcome.Failed ->
                                it.copy(
                                    searching = false,
                                    results = persistentListOf(),
                                    searchFailure = found.failure,
                                )
                        }
                    }
                }
                FileSearchSurface.Text -> {
                    val found = gateway.findText(trimmed)
                    if (mine != searches || !files.isCurrent(gateway))
                        return@launch
                    local.update {
                        when (found) {
                            is Outcome.Ok ->
                                it.copy(
                                    searching = false,
                                    textMatches =
                                        found.value.take(MAX_TEXT_MATCHES).toImmutableList(),
                                    resultsLimited = found.value.size > MAX_TEXT_MATCHES,
                                    searchFailure = null,
                                )
                            is Outcome.Failed ->
                                it.copy(
                                    searching = false,
                                    textMatches = persistentListOf(),
                                    searchFailure = found.failure,
                                )
                        }
                    }
                }
                FileSearchSurface.Symbols -> {
                    val found = files.symbols(trimmed)
                    if (mine != searches || !files.isCurrent(gateway))
                        return@launch
                    local.update {
                        when (found) {
                            is Outcome.Ok ->
                                it.copy(
                                    searching = false,
                                    symbols = found.value,
                                    searchFailure = null,
                                )
                            is Outcome.Failed ->
                                it.copy(
                                    searching = false,
                                    symbols = persistentListOf(),
                                    searchFailure = found.failure,
                                )
                        }
                    }
                }
            }
        }
    }

    override fun selectSurface(surface: FileSearchSurface) {
        val ui = local.value
        if (
            surface == FileSearchSurface.Text && !ui.canSearchText ||
                surface == FileSearchSurface.Symbols && !ui.canSearchSymbols
        )
            return
        searches++
        local.update {
            it.copy(
                surface = surface,
                results = persistentListOf(),
                textMatches = persistentListOf(),
                symbols = persistentListOf(),
                resultsLimited = false,
                searchFailure = null,
            )
        }
        search(ui.query.orEmpty())
    }

    override fun sourceFirst() {
        val store = reader ?: return
        viewModelScope.launch {
            if (store.update(sourceFirst = !local.value.sourceFirst)) {
                local.update {
                    it.copy(
                        sourceFirst = store.value.value.sourceFirst,
                        preferencesFailed = false,
                        results = order(it.results, store.value.value.sourceFirst),
                    )
                }
                if (local.value.phase == FolderPhase.Ready) publish()
            } else local.update { it.copy(preferencesFailed = true) }
        }
    }

    override fun openMatch(match: FindMatch) {
        nav.open(
            FilesRoute.file(
                route.place,
                FilePaths.normalize(match.path),
                match.lineNumber.takeIf { it > 0 },
            )
        )
    }

    override fun openSymbol(symbol: WorkspaceSymbol) {
        nav.open(
            FilesRoute.file(
                route.place,
                FilePaths.normalize(symbol.path),
                symbol.line.takeIf { it > 0 },
            )
        )
    }

    override fun showActions(node: FileNode) {
        local.update { it.copy(actionsFor = node) }
    }

    override fun closeActions() {
        local.update { it.copy(actionsFor = null) }
    }

    override fun copyPath() {
        clip(local.value.actionsFor?.path ?: return)
    }

    override fun copyName() {
        clip(local.value.actionsFor?.name ?: return)
    }

    private fun clip(text: String) {
        local.update { it.copy(clipId = ++clipSequence, clipText = text, actionsFor = null) }
    }

    override fun clipDone(id: Long) {
        local.update { if (it.clipId == id) it.copy(clipId = null, clipText = null) else it }
    }

    override fun review() {
        val node = local.value.actionsFor ?: return
        val ref = ref ?: return
        closeActions()
        nav.open(ReviewRoute.File(ref, node.path, workingTree = true))
    }

    override fun ask() {
        val node = local.value.actionsFor ?: return
        val ref = ref ?: return
        closeActions()
        viewModelScope.launch {
            val result = mentions.mention(ref, node.path, files.directory())
            if (result is Outcome.Ok) nav.openChat(ref, focusComposer = true)
            else if (result is Outcome.Failed)
                local.update { it.copy(actionFailure = result.failure) }
        }
    }

    override fun addReference() {
        val node = local.value.actionsFor ?: return
        if (node.isDir) return
        val ref = ref ?: return
        closeActions()
        local.update { it.copy(attachNotice = null, referencedName = null) }
        viewModelScope.launch {
            val result = mentions.mention(ref, node.path, files.directory())
            local.update {
                it.copy(
                    attachNotice =
                        if (result is Outcome.Ok) FileNotice.Referenced
                        else FileNotice.MentionFailed,
                    referencedName = node.name,
                )
            }
        }
    }

    override fun attach() {
        val node = local.value.actionsFor ?: return
        val ref = ref ?: return
        if (node.isDir || local.value.attaching || !mentions.canAttach()) return
        val gateway = files.gateway() ?: return
        closeActions()
        local.update { it.copy(attaching = true, attachNotice = null) }
        viewModelScope.launch {
            val file = ProjectFiles.open(gateway, node.path)
            if (!files.isCurrent(gateway)) {
                local.update { it.copy(attaching = false, attachNotice = FileNotice.AttachFailed) }
                return@launch
            }
            val result =
                when (file) {
                    is Outcome.Failed -> file
                    is Outcome.Ok -> mentions.attach(ref, file.value.data)
                }
            local.update {
                it.copy(
                    attaching = false,
                    attachNotice =
                        if (result is Outcome.Ok) FileNotice.Attached else FileNotice.AttachFailed,
                )
            }
        }
    }

    private suspend fun checkStorage(
        mine: Int,
        gateway: io.github.eslamasabry.oc.core.domain.FileGateway,
    ) {
        if (!all.all { it.name.startsWith('.') }) return
        // A project opened on its own was already checked by the project page's storage notice.
        val ref = ref ?: return
        val access = storageAccess.singleOrNull() ?: return
        val directory = files.directory()
        if (mine != loads || !files.isCurrent(gateway)) return
        val block = access.block(ref, directory)
        if (mine == loads && files.isCurrent(gateway))
            local.update { it.copy(storageBlock = block) }
    }

    override fun allowStorage() {
        if (local.value.storageBlock == FilesStorageBlock.AppPermission)
            local.update { it.copy(confirmingStorage = true) }
        else requestStorage()
    }

    override fun confirmStorage() {
        local.update { it.copy(confirmingStorage = false) }
        requestStorage()
    }

    override fun cancelStorage() {
        local.update { it.copy(confirmingStorage = false) }
    }

    private fun requestStorage() {
        val ref = ref ?: return
        val access = storageAccess.singleOrNull() ?: return
        val block = local.value.storageBlock
        if (block == FilesStorageBlock.None || local.value.requestingStorage) return
        val mine = loads
        val gateway = files.gateway() ?: return
        local.update { it.copy(requestingStorage = true, storageResult = null) }
        viewModelScope.launch {
            val directory = files.directory()
            if (mine != loads || !files.isCurrent(gateway)) {
                local.update { it.copy(requestingStorage = false) }
                return@launch
            }
            val result = access.request(ref, directory, block)
            if (mine != loads || !files.isCurrent(gateway)) {
                local.update { it.copy(requestingStorage = false) }
                return@launch
            }
            local.update { it.copy(requestingStorage = false, storageResult = result) }
            if (result == FilesStorageResult.Granted) load()
        }
    }

    override fun openPhone() {
        nav.open(PhoneRoute.ThisPhone)
    }

    override fun reviewStorage() {
        nav.open(PhoneRoute.SharedFolders)
    }

    private suspend fun loadChanges(folderGeneration: Int) {
        val mine = ++changesReads
        when (val changes = files.changes()) {
            is Outcome.Ok ->
                if (mine == changesReads && folderGeneration == loads) {
                    local.update {
                        it.copy(
                            changes =
                                changes.value
                                    .associateBy { change -> FilePaths.normalize(change.path) }
                                    .toImmutableMap(),
                            changesFailure = null,
                        )
                    }
                    publish()
                    local.update {
                        it.copy(
                            results =
                                order(withDeleted(it.results, it.changes.values, searching = true))
                        )
                    }
                }
            is Outcome.Failed ->
                if (
                    mine == changesReads &&
                        folderGeneration == loads &&
                        changes.failure !is Failure.Unavailable
                )
                    local.update { it.copy(changesFailure = changes.failure) }
        }
    }

    private fun order(
        nodes: List<FileNode>,
        sourceFirst: Boolean = local.value.sourceFirst,
    ): ImmutableList<FileNode> =
        (if (sourceFirst)
                nodes.filterNot { FolderListing.generated(it) } +
                    nodes.filter { FolderListing.generated(it) }
            else nodes)
            .toImmutableList()

    private fun withDeleted(
        nodes: List<FileNode>,
        changes: Collection<VersionControlFile>,
        searching: Boolean,
    ): List<FileNode> {
        val result = nodes.toMutableList()
        val paths = nodes.mapTo(mutableSetOf()) { it.path }
        val base = FilePaths.normalize(route.path).let { if (it.isEmpty()) "" else "$it/" }
        for (change in changes) {
            if (change.status != "deleted") continue
            val path = FilePaths.normalize(change.path)
            if (searching) {
                if (
                    path.contains(local.value.query.orEmpty(), ignoreCase = true) && paths.add(path)
                )
                    result += FileNode(FilePaths.name(path), path, false)
            } else if (path.startsWith(base)) {
                val tail = path.removePrefix(base)
                if (tail.isEmpty()) continue
                val child = base + tail.substringBefore('/')
                if (paths.add(child)) result += FileNode(FilePaths.name(child), child, '/' in tail)
            }
        }
        return result
    }

    private companion object {
        const val MAX_TEXT_MATCHES = 200
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FilesRoute.AnyFolder): FolderViewModel
    }
}

/**
 * The last segment of the project's folder as the server reports it ("/home/me/my-app/" → "my-app", "C:\work\api" →
 * "api"); null when it is unknown or is a bare root.
 */
internal fun projectFolderName(directory: String?): String? =
    directory?.trim()?.trimEnd('/', '\\')?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf {
        it.isNotEmpty() && !it.endsWith(':')
    }
