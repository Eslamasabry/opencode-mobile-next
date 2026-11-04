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
import io.github.eslamasabry.oc.engine.files.FilePaths
import io.github.eslamasabry.oc.engine.files.FolderListing
import io.github.eslamasabry.oc.engine.files.ProjectFiles
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    val name: String
        get() = FilePaths.name(path)

    val onlyHidden: Boolean
        get() = phase == FolderPhase.Ready && entries.isEmpty() && hiddenCount > 0

    val empty: Boolean
        get() = phase == FolderPhase.Ready && entries.isEmpty() && hiddenCount == 0
}

public interface FolderActions {
    public fun back()

    public fun retry()

    public fun open(node: FileNode)

    public fun toggleSourceFirst()

    public fun toggleHidden()

    /** Fuzzy name search across the project; blank shows the folder again. */
    public fun search(query: String)
}

/**
 * D02 (07 §5): one folder as a route, so predictive back walks up a level. Every read goes to the
 * conversation's own server; nothing is cached across servers. One read at a time; a newer search
 * supersedes an older one.
 */
@AssistedInject
public class FolderViewModel(
    @Assisted private val route: FilesRoute.Folder,
    private val connections: FilesConnections,
    private val nav: AppNavigator,
    private val readerSettings: Set<ReaderSettings>,
) : ViewModel(), FolderActions {
    private val local = MutableStateFlow(FolderUi(FilePaths.normalize(route.path)))
    public val state: StateFlow<FolderUi> = local.asStateFlow()
    private var all: List<FileNode> = emptyList()
    // Newer reads supersede older ones (Konsist K1 keeps Job out of features: a counter does the
    // same).
    private var loads = 0
    private var searches = 0

    init {
        viewModelScope.launch {
            readerSettings.singleOrNull()?.read(route.from.profileId)?.let { saved ->
                local.update { it.copy(sourceFirst = saved.sourceFirst) }
                if (local.value.phase == FolderPhase.Ready) publish()
            }
        }
        load()
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        if (local.value.query != null) search(local.value.query.orEmpty()) else load()
    }

    private fun load() {
        val mine = ++loads
        val gateway = connections.gateway(route.from)
        when {
            gateway == null ->
                local.update { it.copy(phase = FolderPhase.NotConnected, failure = null) }
            !connections.canBrowse(route.from) ->
                local.update { it.copy(phase = FolderPhase.Unavailable, failure = null) }
            else -> {
                local.update { it.copy(phase = FolderPhase.Loading, failure = null) }
                viewModelScope.launch {
                    val listed = ProjectFiles.list(gateway, route.path)
                    if (mine != loads) return@launch
                    when (listed) {
                        is Outcome.Failed ->
                            local.update {
                                it.copy(phase = FolderPhase.Failed, failure = listed.failure)
                            }
                        is Outcome.Ok -> {
                            all = listed.value
                            publish()
                        }
                    }
                }
            }
        }
    }

    private fun publish() {
        val listing = FolderListing.of(all, local.value.showHidden)
        local.update {
            it.copy(
                phase = FolderPhase.Ready,
                entries = order(listing.entries),
                hiddenCount = listing.hiddenCount,
                failure = null,
            )
        }
    }

    private fun order(entries: List<FileNode>): ImmutableList<FileNode> =
        FolderListing.sourceFirst(entries, local.value.sourceFirst)

    override fun toggleSourceFirst() {
        val wanted = !local.value.sourceFirst
        viewModelScope.launch {
            val saved =
                readerSettings.singleOrNull()?.update(route.from.profileId, sourceFirst = wanted) ==
                    true
            local.update {
                it.copy(
                    sourceFirst = if (saved) wanted else it.sourceFirst,
                    preferencesFailed = !saved,
                )
            }
            if (local.value.phase == FolderPhase.Ready) publish()
        }
    }

    override fun open(node: FileNode) {
        if (node.isDir) nav.open(FilesRoute.Folder(route.from, node.path))
        else nav.open(FilesRoute.File(route.from, node.path))
    }

    override fun toggleHidden() {
        local.update { it.copy(showHidden = !it.showHidden) }
        if (local.value.phase == FolderPhase.Ready) publish()
    }

    override fun search(query: String) {
        val mine = ++searches
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            local.update {
                it.copy(
                    query = null,
                    searching = false,
                    results = persistentListOf(),
                    searchFailure = null,
                )
            }
            return
        }
        val gateway = connections.gateway(route.from) ?: return
        local.update { it.copy(query = trimmed, searching = true, searchFailure = null) }
        viewModelScope.launch {
            val found = ProjectFiles.find(gateway, trimmed)
            if (mine != searches) return@launch
            local.update {
                when (found) {
                    is Outcome.Ok ->
                        it.copy(
                            searching = false,
                            results = order(found.value),
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
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FilesRoute.Folder): FolderViewModel
    }
}
