package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The phone runtime sees only shared folders opened on its own saved profile. The native launcher
 * consumes this canonical list on its next launch. Deletion, source edits and a change of phone
 * owner rebuild the list; a remote server's similarly named folder grants no access to this phone's
 * files (B-ANDR-079/080).
 */
@SingleIn(AppScope::class)
@Inject
class NativeSharedProjectFolders(
    private val application: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) {
    private val serial = dispatchers.serial("shared-project-folders")
    private val scope = CoroutineScope(appScope.coroutineContext + serial)
    private var started = false
    private var published: List<String>? = null

    /** Held across the whole read-and-write, so no two rebuilds interleave at the io hop. */
    private val lane = Mutex()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            merge(
                    stores.profiles.profiles.map { Unit },
                    stores.prefs.changes.map { Unit },
                )
                .onStart { emit(Unit) }
                .collect { lane.withLock { synchronize() } }
        }
    }

    /**
     * The only writer of the runtime's list: a Files grant, the storage-access flow and profile
     * deletion publish through here, so a second formula can never race this one and drop a bound
     * folder. Rewrites the list now (even when unchanged) and says whether it landed; [excluding]
     * is a profile being deleted, whose folders no longer count.
     */
    suspend fun publish(excluding: ProfileId? = null): Outcome<Unit> =
        withContext(serial) {
            if (stores.warm() is Outcome.Failed) return@withContext Failure.StorageRefused().failed()
            lane.withLock { synchronize(excluding, force = true) }
        }

    /** [publish] with nothing excluded (a Files grant or a finished profile deletion). */
    suspend fun syncNow(): Outcome<Unit> = publish()

    private suspend fun synchronize(
        excluding: ProfileId? = null,
        force: Boolean = false,
    ): Outcome<Unit> {
        val owner = stores.prefs.string("oc.builtinServerOwner")
        val profile =
            owner
                ?.takeIf { it.isNotEmpty() && it != excluding?.raw }
                ?.let { stores.profiles.profile(ProfileId(it)) }
        // Folders a Flutter build already granted (`oc.sharedProjects.<profileId>`, migrated as
        // is) stay bound like Flutter's union, but only for saved phone profiles: a deleted or
        // remote server's list grants nothing.
        val migrated =
            stores.prefs
                .keys(SHARED_PROJECTS)
                .filter { key ->
                    key.removePrefix(SHARED_PROJECTS) != excluding?.raw &&
                    stores.profiles
                        .profile(ProfileId(key.removePrefix(SHARED_PROJECTS)))
                        ?.let { localPhone(it.profile.baseUrl) } == true
                }
                .flatMap { stores.prefs.stringList(it).orEmpty() }
        val opened =
            if (profile != null && localPhone(profile.profile.baseUrl))
                stores.prefs.stringList("oc.openedProjects.${profile.id.raw}").orEmpty()
            else emptyList()
        val roots =
            (migrated + opened)
                .filter(SharedStoragePath::isShared)
                .mapNotNull(SharedStoragePath::projectRoot)
                .distinct()
                .sorted()
        if (!force && published == roots) return Outcome.Ok(Unit)
        // The copied runtime writes atomically. A failed replacement leaves the previous
        // file intact; the next acknowledged preference/profile change attempts it again.
        val written =
            withContext(dispatchers.io) {
                attempt({ Failure.StorageRefused() }) {
                    BuiltinLinux.get(application).setSharedProjectRoots(roots)
                }
            }
        if (written is Outcome.Ok) published = roots
        return written
    }

    private companion object {
        const val SHARED_PROJECTS = "oc.sharedProjects."
    }

    private fun localPhone(raw: String): Boolean =
        try {
            val uri = URI(raw)
            uri.scheme in listOf("http", "https") &&
                uri.host in listOf("127.0.0.1", "localhost", "[::1]", "::1") &&
                uri.port == PhoneServerScripts.PORT
        } catch (_: Exception) {
            false
        }
}
