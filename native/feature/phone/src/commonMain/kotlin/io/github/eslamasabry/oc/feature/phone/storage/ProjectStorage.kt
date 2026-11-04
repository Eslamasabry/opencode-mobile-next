package io.github.eslamasabry.oc.feature.phone.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class StoredProject(
    val name: String,
    val bytes: Long,
    val files: Int,
    val privateFiles: Int,
)

public data class ProjectStorageFacts(
    val projects: ImmutableList<StoredProject> = persistentListOf(),
    val serverInstalled: Boolean = false,
    val serverBytes: Long = 0,
    val privateDataBytes: Long = 0,
    val savedServers: Int = 0,
    val cacheBytes: Long = 0,
) {
    public val projectBytes: Long
        get() = projects.sumOf { it.bytes }

    public val privateFiles: Int
        get() = projects.sumOf { it.privateFiles }

    public val canExport: Boolean
        get() = serverInstalled || projects.isNotEmpty()
}

public enum class ProjectExportProblem {
    Destination,
    Space,
    Source,
    Failed,
}

public data class ProjectExportReceipt(val bytes: Long, val files: Int)

public data class ProjectExportProgress(val done: Long, val total: Long)

/**
 * Platform-owned filesystem and document operations. No file contents or document handles enter
 * screen state.
 */
public interface ProjectStorage {
    public suspend fun measure(): Outcome<ProjectStorageFacts>

    public suspend fun export(
        includePrivate: Boolean,
        progress: (ProjectExportProgress) -> Unit,
        problem: (ProjectExportProblem) -> Unit,
    ): Outcome<ProjectExportReceipt?>

    public suspend fun clearCache(): Outcome<Long>

    public suspend fun deleteEverything(): Outcome<Boolean>
}

/**
 * This Android-only page is capability gated; desktop does not claim a filesystem implementation.
 */
@Inject
@ContributesBinding(AppScope::class)
public class UnavailableProjectStorage : ProjectStorage {
    override suspend fun measure(): Outcome<ProjectStorageFacts> = Outcome.Failed(Failure.Device())

    override suspend fun export(
        includePrivate: Boolean,
        progress: (ProjectExportProgress) -> Unit,
        problem: (ProjectExportProblem) -> Unit,
    ): Outcome<ProjectExportReceipt?> = Outcome.Failed(Failure.Device())

    override suspend fun clearCache(): Outcome<Long> = Outcome.Failed(Failure.Device())

    override suspend fun deleteEverything(): Outcome<Boolean> = Outcome.Failed(Failure.Device())
}

public enum class StorageNotice {
    MeasureFailed,
    CacheFailed,
    DeleteFailed,
    ExportFailed,
    ExportStopped,
}

public data class ProjectStorageUi(
    val facts: ProjectStorageFacts? = null,
    val measuring: Boolean = true,
    val includePrivate: Boolean = false,
    val exporting: Boolean = false,
    val progress: ProjectExportProgress? = null,
    val receipt: ProjectExportReceipt? = null,
    val exportedPrivate: Boolean = false,
    val cacheClearing: Boolean = false,
    val cacheFreed: Long? = null,
    val confirmDelete: Boolean = false,
    val notice: StorageNotice? = null,
    val failure: Failure? = null,
    val exportProblem: ProjectExportProblem? = null,
) {
    public val busy: Boolean
        get() = exporting || cacheClearing
}

public interface ProjectStorageActions {
    public fun load()

    public fun setIncludePrivate(value: Boolean)

    public fun export()

    public fun stopExport()

    public fun clearCache()

    public fun askDelete()

    public fun confirmDelete()

    public fun dismiss()

    public fun back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ProjectStorageViewModel(
    private val storage: ProjectStorage,
    private val nav: AppNavigator,
) : ViewModel(), ProjectStorageActions {
    private val ui = MutableStateFlow(ProjectStorageUi())
    public val state: StateFlow<ProjectStorageUi> = ui.asStateFlow()
    private var exportJob: Job? = null
    private var loading = false

    init {
        load()
    }

    override fun load() {
        if (loading || ui.value.busy) return
        loading = true
        ui.update { it.copy(measuring = true, notice = null, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = storage.measure()) {
                    is Outcome.Ok -> ui.update { it.copy(facts = result.value, measuring = false) }
                    is Outcome.Failed ->
                        ui.update {
                            it.copy(
                                measuring = false,
                                notice = StorageNotice.MeasureFailed,
                                failure = result.failure,
                            )
                        }
                }
            } finally {
                loading = false
            }
        }
    }

    override fun setIncludePrivate(value: Boolean) {
        if (!ui.value.busy) ui.update { it.copy(includePrivate = value) }
    }

    override fun export() {
        if (ui.value.busy || ui.value.facts?.canExport != true) return
        val private = ui.value.includePrivate
        ui.update {
            it.copy(
                exporting = true,
                progress = null,
                receipt = null,
                notice = null,
                failure = null,
                exportProblem = null,
            )
        }
        exportJob = viewModelScope.launch {
            try {
                when (
                    val result =
                        storage.export(
                            private,
                            { value -> ui.update { it.copy(progress = value) } },
                            { problem -> ui.update { it.copy(exportProblem = problem) } },
                        )
                ) {
                    is Outcome.Ok ->
                        ui.update {
                            it.copy(
                                exporting = false,
                                progress = null,
                                receipt = result.value,
                                exportedPrivate = private,
                            )
                        }
                    is Outcome.Failed ->
                        ui.update {
                            it.copy(
                                exporting = false,
                                progress = null,
                                notice = StorageNotice.ExportFailed,
                                failure = result.failure,
                            )
                        }
                }
            } catch (cancelled: CancellationException) {
                ui.update {
                    it.copy(
                        exporting = false,
                        progress = null,
                        notice = StorageNotice.ExportStopped,
                    )
                }
                throw cancelled
            } finally {
                exportJob = null
            }
        }
    }

    override fun stopExport() {
        exportJob?.cancel()
    }

    override fun clearCache() {
        if (ui.value.busy || loading) return
        ui.update {
            it.copy(cacheClearing = true, cacheFreed = null, notice = null, failure = null)
        }
        viewModelScope.launch {
            when (val result = storage.clearCache()) {
                is Outcome.Ok ->
                    ui.update {
                        it.copy(
                            cacheClearing = false,
                            cacheFreed = result.value,
                            facts = it.facts?.copy(cacheBytes = 0),
                        )
                    }
                is Outcome.Failed ->
                    ui.update {
                        it.copy(
                            cacheClearing = false,
                            notice = StorageNotice.CacheFailed,
                            failure = result.failure,
                        )
                    }
            }
        }
    }

    override fun askDelete() {
        if (!ui.value.busy && ui.value.facts != null && !ui.value.measuring)
            ui.update { it.copy(confirmDelete = true) }
    }

    override fun confirmDelete() {
        if (!ui.value.confirmDelete || ui.value.busy) return
        ui.update { it.copy(confirmDelete = false, cacheClearing = true, notice = null) }
        viewModelScope.launch {
            val accepted = storage.deleteEverything()
            if (accepted !is Outcome.Ok || !accepted.value)
                ui.update { it.copy(cacheClearing = false, notice = StorageNotice.DeleteFailed) }
        }
    }

    override fun dismiss() {
        ui.update { it.copy(confirmDelete = false, notice = null, failure = null) }
    }

    override fun back() {
        stopExport()
        nav.back()
    }

    override fun onCleared() {
        exportJob?.cancel()
    }
}
