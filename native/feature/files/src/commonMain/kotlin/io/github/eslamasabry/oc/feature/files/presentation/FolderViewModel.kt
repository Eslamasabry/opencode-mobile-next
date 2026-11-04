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
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageAccess
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock
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
    val hiddenCount: Int = 0,
    val showHidden: Boolean = false,
    val failure: Failure? = null,
    /** Non-null while a name search is shown instead of the folder. */
    val query: String? = null,
    val searching: Boolean = false,
    val results: ImmutableList<FileNode> = persistentListOf(),
    val searchFailure: Failure? = null,
    val storageBlock: SharedStorageBlock = SharedStorageBlock.None,
    val requestingStorage: Boolean = false,
) {
    val name: String
        get() = FilePaths.name(path)

    val onlyHidden: Boolean
        get() = phase == FolderPhase.Ready && entries.isEmpty() && hiddenCount > 0

    val empty: Boolean
        get() = phase == FolderPhase.Ready && entries.isEmpty() && hiddenCount == 0
}

public interface FolderActions {
    public fun requestStorage() {}

    public fun onResume() {}

    public fun back()

    public fun retry()

    public fun open(node: FileNode)

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
    private val storageAccess: Set<SharedStorageAccess> = emptySet(),
) : ViewModel(), FolderActions {
    private val local = MutableStateFlow(FolderUi(FilePaths.normalize(route.path)))
    public val state: StateFlow<FolderUi> = local.asStateFlow()
    private var all: List<FileNode> = emptyList()
    // Newer reads supersede older ones (Konsist K1 keeps Job out of features: a counter does the
    // same).
    private var loads = 0
    private var searches = 0

    init {
        check(storageAccess.size <= 1) { "multiple shared-storage hosts" }
        load()
    }

    override fun back(): Unit = nav.back()

    override fun retry() {
        if (local.value.query != null) search(local.value.query.orEmpty()) else load()
    }

    private fun load() {
        val mine = ++loads
        val identity = connections.sourceIdentity(route.from)
        val gateway = connections.gateway(route.from)
        when {
            gateway == null ->
                local.update { it.copy(phase = FolderPhase.NotConnected, failure = null) }
            !connections.canBrowse(route.from) ->
                local.update { it.copy(phase = FolderPhase.Unavailable, failure = null) }
            else -> {
                local.update {
                    it.copy(phase = FolderPhase.Loading, failure = null, requestingStorage = false)
                }
                viewModelScope.launch {
                    if (!checkStorage(identity) { mine == loads }) return@launch
                    if (mine != loads || connections.sourceIdentity(route.from) !== identity)
                        return@launch
                    val listed = ProjectFiles.list(gateway, route.path)
                    if (mine != loads || connections.sourceIdentity(route.from) !== identity)
                        return@launch
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

    private var projectDirectory: String? = null

    private suspend fun checkStorage(identity: Any?, wanted: () -> Boolean = { true }): Boolean {
        val access = storageAccess.singleOrNull() ?: return true
        val directory = connections.directory(route.from)
        if (!wanted()) return false
        if (!sourceCurrent(identity)) return false
        val checked = access.check(route.from.profileId, directory, identity)
        if (!wanted()) return false
        if (!sourceCurrent(identity)) return false
        projectDirectory = directory
        return when (checked) {
            is Outcome.Failed -> {
                local.update {
                    it.copy(
                        phase = FolderPhase.Failed,
                        storageBlock = SharedStorageBlock.None,
                        failure = checked.failure,
                        searching = false,
                    )
                }
                false
            }
            is Outcome.Ok -> {
                local.update { it.copy(storageBlock = checked.value) }
                if (checked.value == SharedStorageBlock.None) true
                else {
                    all = emptyList()
                    local.update {
                        it.copy(
                            phase = FolderPhase.Failed,
                            entries = persistentListOf(),
                            results = persistentListOf(),
                            searching = false,
                            failure = null,
                        )
                    }
                    false
                }
            }
        }
    }

    private fun sourceCurrent(identity: Any?): Boolean {
        if (connections.sourceIdentity(route.from) === identity) return true
        local.update {
            it.copy(
                phase = FolderPhase.NotConnected,
                storageBlock = SharedStorageBlock.None,
                requestingStorage = false,
                failure = null,
                searching = false,
            )
        }
        return false
    }

    override fun onResume() {
        // Re-check only a folder still blocked by shared storage (back from the permission page).
        val l = local.value
        if (
            l.storageBlock != SharedStorageBlock.None &&
                !l.requestingStorage &&
                l.phase != FolderPhase.Loading
        )
            retry()
    }

    override fun requestStorage() {
        val access = storageAccess.singleOrNull() ?: return
        val block = local.value.storageBlock
        val identity = connections.sourceIdentity(route.from)
        val run = loads
        if (block == SharedStorageBlock.None || local.value.requestingStorage) return
        local.update { it.copy(requestingStorage = true) }
        viewModelScope.launch {
            val asked = access.request(route.from.profileId, projectDirectory, block, identity)
            if (run != loads) return@launch
            if (!sourceCurrent(identity)) return@launch
            local.update {
                it.copy(requestingStorage = false, failure = (asked as? Outcome.Failed)?.failure)
            }
            if (asked is Outcome.Ok) retry()
        }
    }

    private fun publish() {
        val listing = FolderListing.of(all, local.value.showHidden)
        local.update {
            it.copy(
                phase = FolderPhase.Ready,
                entries = listing.entries,
                hiddenCount = listing.hiddenCount,
                failure = null,
            )
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
        val identity = connections.sourceIdentity(route.from)
        local.update { it.copy(query = trimmed, searching = true, searchFailure = null) }
        viewModelScope.launch {
            if (!checkStorage(identity) { mine == searches }) return@launch
            if (mine != searches || connections.sourceIdentity(route.from) !== identity)
                return@launch
            val found = ProjectFiles.find(gateway, trimmed)
            if (mine != searches || connections.sourceIdentity(route.from) !== identity)
                return@launch
            local.update {
                when (found) {
                    is Outcome.Ok ->
                        it.copy(searching = false, results = found.value, searchFailure = null)
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
