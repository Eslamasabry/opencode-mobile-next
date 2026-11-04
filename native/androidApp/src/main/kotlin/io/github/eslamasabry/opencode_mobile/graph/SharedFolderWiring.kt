package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.DeletionOptions
import io.github.eslamasabry.oc.engine.profiles.DeletionStep
import io.github.eslamasabry.oc.engine.profiles.ProfileResourceCleaner
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import io.github.eslamasabry.oc.feature.files.presentation.FilesConnections
import io.github.eslamasabry.oc.feature.files.presentation.FilesStorageAccess
import io.github.eslamasabry.oc.feature.files.presentation.FilesStorageBlock
import io.github.eslamasabry.oc.feature.files.presentation.FilesStorageResult
import io.github.eslamasabry.oc.feature.phone.storage.SharedFolderAccess
import io.github.eslamasabry.oc.feature.phone.storage.SharedFolderState
import io.github.eslamasabry.oc.feature.phone.storage.UnavailableSharedFolderAccess
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Only the authoritative built-in server can ask for this app's raw-path permission. Remote folders
 * are untouched.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<FilesStorageAccess>())
internal class AndroidFilesStorageAccess(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val connections: FilesConnections,
    private val platform: AndroidProjectExport,
    private val dispatchers: AppDispatchers,
    private val sharedRoots: NativeSharedProjectFolders,
) : FilesStorageAccess {
    private val lane = Mutex()
    private val linux
        get() = BuiltinLinux.get(app)

    private fun local(ref: ConversationRef): ConnectionGraph? {
        val graph = registry.graphFor(ref) ?: return null
        if (
            !graph.profile.managedOnPhone ||
                stores.prefs.string("oc.builtinServerOwner") != ref.profileId.raw
        )
            return null
        if (graph.profile.origin.host !in setOf("127.0.0.1", "localhost", "::1")) return null
        return graph
    }

    override suspend fun block(ref: ConversationRef, directory: String?): FilesStorageBlock {
        if (!SharedStoragePath.isShared(directory) || stores.warm() is Outcome.Failed)
            return FilesStorageBlock.None
        val graph = local(ref) ?: return FilesStorageBlock.None
        return withContext(dispatchers.io) {
            if (registry.graphFor(ref) !== graph) return@withContext FilesStorageBlock.None
            when (platform.storagePermissionGranted()) {
                false -> FilesStorageBlock.AppPermission
                null -> FilesStorageBlock.None
                true ->
                    if (directory != null && linux.serverCanReadSharedFolder(directory))
                        FilesStorageBlock.None
                    else FilesStorageBlock.SelectedFolder
            }
        }
    }

    override suspend fun request(
        ref: ConversationRef,
        directory: String?,
        block: FilesStorageBlock,
    ): FilesStorageResult {
        if (stores.warm() is Outcome.Failed || !SharedStoragePath.isShared(directory))
            return FilesStorageResult.Failed
        val graph = local(ref) ?: return FilesStorageResult.Failed
        if (!sameDirectory(connections.directory(ref), directory)) return FilesStorageResult.Failed
        if (block == FilesStorageBlock.AppPermission) {
            when (val granted = platform.requestStoragePermission()) {
                is Outcome.Failed -> return FilesStorageResult.Failed
                is Outcome.Ok -> if (!granted.value) return FilesStorageResult.Cancelled
            }
        }
        if (!sameDirectory(connections.directory(ref), directory)) return FilesStorageResult.Failed
        return lane.withLock {
            if (local(ref) !== graph || registry.graphFor(ref) !== graph)
                return@withLock FilesStorageResult.Failed
            if (platform.storagePermissionGranted() != true)
                return@withLock FilesStorageResult.Cancelled
            val root = SharedStoragePath.projectRoot(directory)
            if (root == null)
                return@withLock withContext(dispatchers.io) {
                    when {
                        !linux.sharedStorageBroadAccess() -> FilesStorageResult.Failed
                        linux.sharedStorageRestartRequired() -> FilesStorageResult.RestartRequired
                        else -> FilesStorageResult.Granted
                    }
                }
            val safe =
                withContext(dispatchers.io) {
                    attempt({ Failure.StorageRefused() }) {
                        File(root).isDirectory &&
                            File(root).canonicalFile == File(root).absoluteFile
                    }
                }
            if (safe !is Outcome.Ok || !safe.value) return@withLock FilesStorageResult.Failed
            val key = "$PREFIX${ref.profileId.raw}"
            val roots = (stores.prefs.stringList(key).orEmpty() + root).distinct().sorted()
            if (local(ref) !== graph) return@withLock FilesStorageResult.Failed
            if (
                stores.prefs.commit(stores.lanes.forProfile(ref.profileId)) {
                    putStringList(key, roots)
                } is Outcome.Failed
            )
                return@withLock FilesStorageResult.Failed
            if (local(ref) !== graph) return@withLock FilesStorageResult.Failed
            val synced = syncRoots()
            if (synced is Outcome.Failed) return@withLock FilesStorageResult.Failed
            withContext(dispatchers.io) {
                if (linux.serverCanReadSharedFolder(root)) FilesStorageResult.Granted
                else FilesStorageResult.RestartRequired
            }
        }
    }

    private fun sameDirectory(actual: String?, expected: String?): Boolean {
        if (actual == null || expected == null) return false
        val root = SharedStoragePath.projectRoot(actual)
        return if (root == null) actual == expected
        else root == SharedStoragePath.projectRoot(expected)
    }

    /**
     * Rebuild the shared native blob after a grant or profile-key deletion through the one writer
     * ([NativeSharedProjectFolders]), so a deleted profile's folder is never retained and the
     * opened-project union is never overwritten.
     */
    suspend fun syncRoots(): Outcome<Unit> = sharedRoots.syncNow()

    private companion object {
        const val PREFIX = "oc.sharedProjects."
    }
}

@Inject
@ContributesBinding(AppScope::class, replaces = [UnavailableSharedFolderAccess::class])
internal class AndroidSharedFolderAccess(
    private val app: Application,
    private val export: AndroidProjectExport,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val selected: AndroidFilesStorageAccess,
) : SharedFolderAccess {
    private val linux
        get() = BuiltinLinux.get(app)

    override suspend fun read(): Outcome<SharedFolderState> {
        if (stores.warm() is Outcome.Failed) return Outcome.Failed(Failure.StorageRefused())
        return withContext(dispatchers.io) { attempt({ Failure.StorageRefused() }) { status() } }
    }

    private fun status(): SharedFolderState =
        SharedFolderState(
            export.storagePermissionGranted(),
            linux.sharedStorageBroadAccess(),
            !linux.prootIsConfined,
            linux.sharedStorageRestartRequired(),
        )

    override suspend fun allowAndroidAccess(): Outcome<Boolean> = export.requestStoragePermission()

    override suspend fun allowAllFolders(value: Boolean): Outcome<SharedFolderState> {
        if (stores.warm() is Outcome.Failed) return Outcome.Failed(Failure.StorageRefused())
        if (value && export.storagePermissionGranted() != true) {
            when (val permission = export.requestStoragePermission()) {
                is Outcome.Failed -> return permission
                is Outcome.Ok -> if (!permission.value) return read()
            }
        }
        val roots = selected.syncRoots()
        if (roots is Outcome.Failed) return roots
        return withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                linux.setSharedStorageBroadAccess(value)
                status()
            }
        }
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
internal class SharedFolderProfileCleaner(private val folders: AndroidFilesStorageAccess) :
    ProfileResourceCleaner {
    override val owner: String = ":engine:runtime shared folders"
    override val order: Int = DeletionStep.RETAINED_COPIES

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        when (val result = folders.syncRoots()) {
            is Outcome.Ok -> Outcome.Ok(1)
            is Outcome.Failed -> result
        }
}
