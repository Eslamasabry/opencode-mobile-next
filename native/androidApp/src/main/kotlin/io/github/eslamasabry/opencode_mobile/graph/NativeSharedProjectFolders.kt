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
    private val lane = dispatchers.serial("shared-project-folders")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private var started = false
    private var published: List<String>? = null

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
                .collect { synchronize() }
        }
    }

    /**
     * Rewrites the runtime's list now (a Files grant or a profile deletion) and says whether it
     * landed. The one writer of the shared-roots file, so grants and the opened-project union never
     * overwrite each other.
     */
    suspend fun syncNow(): Outcome<Unit> =
        withContext(lane) {
            if (stores.warm() is Outcome.Failed) return@withContext Failure.StorageRefused().failed()
            published = null
            synchronize()
        }

    /**
     * The only writer of the runtime's list: the storage-access flow and profile deletion publish
     * through here, so a second formula can never race this one and drop a bound folder.
     */
    suspend fun publish(excluding: ProfileId? = null): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Failure.StorageRefused().failed()
        return withContext(lane) { synchronize(excluding, force = true) }
    }

    private suspend fun synchronize(
        excluding: ProfileId? = null,
        force: Boolean = false,
    ): Outcome<Unit> {
        val owner = stores.prefs.string("oc.builtinServerOwner")?.takeIf { it.isNotEmpty() }
        val granted = SharedProjectList.all(stores.prefs)
        val addresses =
            granted.keys
                .plus(listOfNotNull(owner))
                .distinct()
                .mapNotNull { id -> stores.profiles.profile(ProfileId(id))?.let { id to it.profile.baseUrl } }
                .toMap()
        val roots =
            PhoneSharedRoots.of(
                owner = owner,
                addresses = addresses,
                granted = granted,
                opened = owner?.let { stores.prefs.stringList("oc.openedProjects.$it") }.orEmpty(),
                port = PhoneServerScripts.PORT,
                excluding = excluding?.raw,
            )
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
}

/**
 * The folders of the phone's shared storage its Linux binds, from the saved lists (W3, walker F-043).
 *
 * Folders the person chose for this phone's server ([owner], `oc.builtinServerOwner`) are kept as long as that profile
 * is this phone's own (a loopback address on one of the phone server's ports), whichever of those ports the server runs
 * on right now. Before, a list counted only while its saved address named the server's current port: when the server
 * came up on another of its ports, until the saved address followed it (B-239), every rebuild of the list dropped the
 * person's folders, and whatever started the agents' helper meanwhile (the agent-update look, an own-agent reload, a
 * connect) bound it without them. The page then said again that the agents must be restarted. Other saved profiles
 * (folders a Flutter build granted) still count only on the current port; a remote server's list never counts.
 */
internal object PhoneSharedRoots {
    // The six facts about the phone profile that decide which folders count; every caller already holds all six.
    @Suppress("LongParameterList")
    fun of(
        owner: String?,
        addresses: Map<String, String>,
        granted: Map<String, List<String>>,
        opened: List<String>,
        port: Int,
        excluding: String?,
    ): List<String> {
        val phone = owner?.takeIf { it != excluding }
        fun local(id: String): Boolean {
            if (id == excluding) return false
            val address = addresses[id] ?: return false
            return loopbackPort(address)?.let { it == port || (id == phone && PhoneProfileAddress.ownPort(it)) } == true
        }
        val fromGrants = granted.filterKeys(::local).values.flatten()
        val fromOpened = if (phone != null && local(phone)) opened else emptyList()
        return (fromGrants + fromOpened)
            .filter(SharedStoragePath::isShared)
            .mapNotNull(SharedStoragePath::projectRoot)
            .distinct()
            .sorted()
    }

    /** The port of an address on this phone itself (`http(s)://127.0.0.1|localhost|[::1]:<port>`), else null. */
    private fun loopbackPort(raw: String): Int? =
        try {
            val uri = URI(raw)
            uri.port.takeIf {
                uri.scheme in listOf("http", "https") &&
                    uri.host in listOf("127.0.0.1", "localhost", "[::1]", "::1") &&
                    it > 0
            }
        } catch (_: Exception) {
            null
        }
}
