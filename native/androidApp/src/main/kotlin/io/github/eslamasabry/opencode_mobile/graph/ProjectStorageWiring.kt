package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.*
import io.github.eslamasabry.oc.core.data.scope.*
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.feature.projects.presentation.*
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Local permission is checked only for an opened shared folder on the saved app-owned phone server.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [RemoteProjectStorageAccess::class])
class AndroidProjectStorageAccess(
    private val app: Application,
    private val stores: PreviewStores,
    private val phoneSource: Lazy<PhoneServerSource>,
    private val runtime: Lazy<BuiltinRuntime>,
    private val dispatchers: AppDispatchers,
    private val folders: NativeSharedProjectFolders,
) : ProjectStorageAccess {
    private val server
        get() = phoneSource.value

    private val lane = Mutex()
    private val linux
        get() = BuiltinLinux.get(app)

    private fun key(id: ProfileId) = "oc.sharedProjects.${id.raw}"

    private fun pending(id: ProfileId) = "oc.sharedProjectsPending.${id.raw}"

    private fun owns(id: ProfileId): Boolean {
        val p = stores.profiles.profile(id)?.profile ?: return false
        return stores.prefs.string("oc.builtinServerOwner") == id.raw &&
            p.baseUrl.trimEnd('/') == PhoneOwnServers.IN_APP_URL
    }

    private fun granted(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else
            app.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    override suspend fun inspect(profile: ProfileId, path: String): Outcome<ProjectStorageStatus> =
        lane.withLock {
            when (val warm = stores.warm()) {
                is Outcome.Failed -> return@withLock warm
                is Outcome.Ok -> Unit
            }
            if (!SharedStoragePath.isShared(path) || !owns(profile))
                return@withLock Outcome.Ok(ProjectStorageStatus.Allowed)
            if (!granted()) return@withLock Outcome.Ok(ProjectStorageStatus.NeedsAccess)
            val root =
                SharedStoragePath.projectRoot(path)
                    ?: return@withLock Outcome.Ok(ProjectStorageStatus.Allowed)
            val raw = stores.prefs.get(key(profile))
            if (raw != null && raw !is PrefValue.StrList)
                return@withLock Failure.StorageRefused().failed()
            val known = (raw as? PrefValue.StrList)?.value?.contains(root) == true
            server.refresh()
            if (!owns(profile))
                return@withLock Failure.Refused(RefusalReason.OriginChanged).failed()
            val protection =
                withContext(dispatchers.io) {
                    attempt({ Failure.StorageRefused() }) {
                        (linux.phoneEngineStatus(profile.raw)["protectionRequired"] == true) to
                            linux.serverRunning
                    }
                }
            if (protection is Outcome.Failed) return@withLock protection
            val observed = (protection as Outcome.Ok).value
            val needs = stores.prefs.bool(pending(profile)) == true || (!known && observed.first)
            if (needs && observed.second)
                return@withLock Outcome.Ok(
                    ProjectStorageStatus.NeedsRestart(server.facts.value.replyRunning)
                )
            when (val saved = remember(profile, root, needsRestart = false)) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> Outcome.Ok(ProjectStorageStatus.Allowed)
            }
        }

    override suspend fun requestAccess(): Outcome<Unit> =
        withContext(dispatchers.main) {
            attempt({ Failure.StorageRefused() }) {
                val action =
                    if (Build.VERSION.SDK_INT >= 30)
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                    else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                try {
                    app.startActivity(
                        Intent(action, Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (missing: android.content.ActivityNotFoundException) {
                    // Some OEM builds lack the per-app page; Flutter falls back to the list page.
                    if (Build.VERSION.SDK_INT < 30) throw missing
                    app.startActivity(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

    override suspend fun restart(profile: ProfileId, path: String): Outcome<Unit> = lane.withLock {
        if (!owns(profile) || !granted())
            return@withLock Failure.Refused(RefusalReason.OriginChanged).failed()
        val root =
            SharedStoragePath.projectRoot(path) ?: return@withLock Failure.StorageRefused().failed()
        when (val saved = remember(profile, root, needsRestart = true)) {
            is Outcome.Failed -> return@withLock saved
            is Outcome.Ok -> Unit
        }
        if (!owns(profile)) return@withLock Failure.Refused(RefusalReason.OriginChanged).failed()
        when (val result = runtime.value.restartOwned(profile)) {
            is Outcome.Failed -> result
            is Outcome.Ok -> {
                if (!owns(profile))
                    return@withLock Failure.Refused(RefusalReason.OriginChanged).failed()
                stores.prefs.commit(stores.lanes.forProfile(profile)) { remove(pending(profile)) }
            }
        }
    }

    private suspend fun remember(
        profile: ProfileId,
        root: String,
        needsRestart: Boolean,
    ): Outcome<Unit> {
        if (!owns(profile)) return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val before = stores.prefs.get(key(profile))
        if (before != null && before !is PrefValue.StrList) return Failure.StorageRefused().failed()
        val next = ((before as? PrefValue.StrList)?.value.orEmpty() + root).distinct().sorted()
        when (
            val saved =
                stores.prefs.compareAndCommit(
                    stores.lanes.forProfile(profile),
                    mapOf(key(profile) to before),
                ) {
                    putStringList(key(profile), next)
                    if (needsRestart) putBool(pending(profile), true) else remove(pending(profile))
                }
        ) {
            is Outcome.Failed -> return saved
            is Outcome.Ok -> if (!saved.value) return Failure.Conflict().failed()
        }
        return push()
    }

    /** One writer for the runtime's root list (NativeSharedProjectFolders), never a second formula. */
    private suspend fun push(excluding: ProfileId? = null): Outcome<Unit> =
        folders.publish(excluding)

    suspend fun removeBindings(profile: ProfileId): Outcome<Int> = lane.withLock {
        when (val result = push(excluding = profile)) {
            is Outcome.Failed -> result
            is Outcome.Ok -> Outcome.Ok(1)
        }
    }

    companion object {
        fun register(scope: ProfileScope) {
            scope.register(
                KeyShape(
                    "oc.sharedProjects.",
                    KeyScope.Profile,
                    PrefType.StrList,
                    ":feature:projects",
                    "Opened phone folders",
                )
            )
            scope.register(
                KeyShape(
                    "oc.sharedProjectsPending.",
                    KeyScope.Profile,
                    PrefType.Bool,
                    ":feature:projects",
                    "Unacknowledged confined server restart",
                )
            )
        }
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class SharedProjectBindingsCleaner(private val access: AndroidProjectStorageAccess) :
    ProfileResourceCleaner {
    override val owner: String = ":feature:projects"
    override val order: Int = DeletionStep.DEVICE_SURFACES
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        access.removeBindings(profileId)
}
