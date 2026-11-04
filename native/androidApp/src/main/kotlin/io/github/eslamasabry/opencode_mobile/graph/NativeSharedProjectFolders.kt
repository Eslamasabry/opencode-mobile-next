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
    private val scope =
        CoroutineScope(appScope.coroutineContext + dispatchers.serial("shared-project-folders"))
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

    private suspend fun synchronize() {
        val owner = stores.prefs.string("oc.builtinServerOwner")
        val profile =
            owner?.takeIf { it.isNotEmpty() }?.let { stores.profiles.profile(ProfileId(it)) }
        val roots =
            if (profile != null && localPhone(profile.profile.baseUrl))
                stores.prefs
                    .stringList("oc.openedProjects.${profile.id.raw}")
                    .orEmpty()
                    .filter(SharedStoragePath::isShared)
                    .mapNotNull(SharedStoragePath::projectRoot)
                    .distinct()
            else emptyList()
        if (published == roots) return
        // The copied runtime writes atomically. A failed replacement leaves the previous
        // file intact; the next acknowledged preference/profile change attempts it again.
        val written =
            withContext(dispatchers.io) {
                attempt({ Failure.StorageRefused() }) {
                    BuiltinLinux.get(application).setSharedProjectRoots(roots)
                }
            }
        if (written is Outcome.Ok) published = roots
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
