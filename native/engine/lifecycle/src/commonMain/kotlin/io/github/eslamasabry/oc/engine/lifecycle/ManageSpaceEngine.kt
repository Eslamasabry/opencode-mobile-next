package io.github.eslamasabry.oc.engine.lifecycle

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/** Isolated storage operations: no connection, profile secret or Linux process is needed. */
public interface ManageSpaceStorage {
    public suspend fun measure(): Outcome<Pair<AppStorageFacts, Long>>

    public suspend fun clearCache(): Outcome<Long>

    public suspend fun export(
        includePrivate: Boolean,
        progress: (Long) -> Unit,
    ): Outcome<StorageExport?>

    public suspend fun deleteAll(): Outcome<Unit>

    public fun cancelExport()
}

public data class StorageExport(val bytes: Long, val files: Int, val private: Boolean)

public enum class StorageNotice {
    CacheCleared,
    CacheFailed,
    Exported,
    ExportFailed,
    ExportStopped,
    DeleteFailed,
}

public data class ManageSpaceState(
    val loading: Boolean = true,
    val facts: AppStorageFacts? = null,
    val cacheBytes: Long? = null,
    val busy: Boolean = false,
    val exporting: Boolean = false,
    val includePrivate: Boolean = false,
    val bytesDone: Long = 0,
    val export: StorageExport? = null,
    val notice: StorageNotice? = null,
    val cacheFreed: Long = 0,
    val failed: Boolean = false,
)

/** One serial lane; a destructive action cannot overlap an export or cache cleanup. */
public class ManageSpaceEngine(private val storage: ManageSpaceStorage) {
    private val lane = Mutex()
    private val local = MutableStateFlow(ManageSpaceState())
    public val state: StateFlow<ManageSpaceState> = local.asStateFlow()

    public suspend fun refresh() {
        if (!lane.tryLock()) return
        try {
            local.update { it.copy(loading = true, failed = false) }
            when (val result = storage.measure()) {
                is Outcome.Ok ->
                    local.update {
                        it.copy(
                            loading = false,
                            facts = result.value.first,
                            cacheBytes = result.value.second,
                        )
                    }
                is Outcome.Failed -> local.update { it.copy(loading = false, failed = true) }
            }
        } finally {
            lane.unlock()
        }
    }

    public fun includePrivate(value: Boolean) {
        if (!local.value.busy) local.update { it.copy(includePrivate = value) }
    }

    public suspend fun clearCache() {
        if (!lane.tryLock()) return
        try {
            local.update { it.copy(busy = true, notice = null) }
            when (val result = storage.clearCache()) {
                is Outcome.Ok ->
                    local.update {
                        it.copy(
                            cacheBytes = 0,
                            cacheFreed = result.value,
                            notice = StorageNotice.CacheCleared,
                        )
                    }
                is Outcome.Failed -> local.update { it.copy(notice = StorageNotice.CacheFailed) }
            }
        } finally {
            local.update { it.copy(busy = false) }
            lane.unlock()
        }
    }

    public suspend fun export() {
        if (!lane.tryLock()) return
        try {
            val private = local.value.includePrivate
            local.update { it.copy(busy = true, exporting = true, notice = null, bytesDone = 0) }
            when (
                val result =
                    storage.export(private) { done -> local.update { it.copy(bytesDone = done) } }
            ) {
                is Outcome.Ok ->
                    local.update {
                        it.copy(
                            export = result.value,
                            notice =
                                if (result.value == null) StorageNotice.ExportStopped
                                else StorageNotice.Exported,
                        )
                    }
                is Outcome.Failed -> local.update { it.copy(notice = StorageNotice.ExportFailed) }
            }
        } finally {
            local.update { it.copy(busy = false, exporting = false) }
            lane.unlock()
        }
    }

    public fun cancelExport(): Unit = storage.cancelExport()

    public fun dismiss(): Unit = local.update { it.copy(notice = null) }

    public suspend fun deleteAll() {
        if (!lane.tryLock()) return
        try {
            local.update { it.copy(busy = true, notice = null) }
            if (storage.deleteAll() is Outcome.Failed)
                local.update { it.copy(notice = StorageNotice.DeleteFailed) }
        } finally {
            local.update { it.copy(busy = false) }
            lane.unlock()
        }
    }
}
