package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.android.platform.AndroidLocalFileOpener
import io.github.eslamasabry.oc.android.platform.AndroidSharedStorageAccess
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.paths.SharedStoragePath
import io.github.eslamasabry.oc.feature.files.presentation.PhoneFiles
import io.github.eslamasabry.oc.feature.files.presentation.ProjectStorageAccess
import kotlinx.coroutines.CancellationException

/**
 * Revalidates the exact saved built-in owner around every asynchronous read and before Android
 * dispatch.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PhoneFiles>())
class AndroidPhoneFiles(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val activities: ResumedActivities,
    dispatchers: AppDispatchers,
) : PhoneFiles, ProjectStorageAccess {
    private val opener = AndroidLocalFileOpener(app, dispatchers)
    private val access = AndroidSharedStorageAccess(dispatchers)

    private fun owned(ref: ConversationRef, graph: ConnectionGraph): Boolean =
        try {
            registry.graphFor(ref) === graph &&
                graph.profile.managedOnPhone &&
                graph.profile.origin.scheme == "http" &&
                AddressRules.isLoopback(graph.profile.origin.host) &&
                stores.profiles.profile(ref.profileId) != null &&
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value ==
                    ref.profileId.raw
        } catch (_: Exception) {
            false
        }

    private suspend fun resolvePath(
        ref: ConversationRef,
        raw: String?,
    ): Pair<ConnectionGraph, String>? {
        val graph = registry.graphFor(ref) ?: return null
        if (!owned(ref, graph)) return null
        val session =
            when (val read = graph.session.server.session(ref.sessionId)) {
                is Outcome.Ok -> read.value
                is Outcome.Failed -> return null
            }
        val directory = session.directory ?: return null
        val target =
            when {
                raw == null -> directory
                raw.startsWith('/') -> raw
                else -> "$directory/$raw"
            }
        if (!SharedStoragePath.isShared(target) || !owned(ref, graph)) return null
        val canonical = SharedStoragePath.projectRoot(target) ?: return null
        return graph to canonical
    }

    private fun ownedProfile(profile: ProfileId, graph: ConnectionGraph): Boolean =
        try {
            registry.live.value[profile] === graph &&
                graph.profile.managedOnPhone &&
                graph.profile.origin.scheme == "http" &&
                AddressRules.isLoopback(graph.profile.origin.host) &&
                stores.profiles.profile(profile) != null &&
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == profile.raw
        } catch (_: Exception) {
            false
        }

    suspend fun blockedProject(profile: ProfileId, directory: String): Boolean {
        val graph = registry.live.value[profile] ?: return false
        return SharedStoragePath.isShared(directory) &&
            ownedProfile(profile, graph) &&
            !access.granted(app)
    }

    suspend fun requestProject(profile: ProfileId, directory: String): Outcome<Unit> {
        val graph = registry.live.value[profile] ?: return Outcome.Failed(Failure.StorageRefused())
        if (!SharedStoragePath.isShared(directory) || !ownedProfile(profile, graph))
            return Outcome.Failed(Failure.StorageRefused())
        return access.request(activities.current ?: return Outcome.Failed(Failure.StorageRefused()))
    }

    override suspend fun blocked(ref: ConversationRef): Boolean =
        try {
            resolvePath(ref, null) != null && !access.granted(app)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    override suspend fun request(ref: ConversationRef): Outcome<Unit> {
        val (graph, _) = resolvePath(ref, null) ?: return Outcome.Failed(Failure.StorageRefused())
        val activity = activities.current ?: return Outcome.Failed(Failure.StorageRefused())
        if (!owned(ref, graph)) return Outcome.Failed(Failure.StorageRefused())
        return access.request(activity)
    }

    override suspend fun available(ref: ConversationRef, path: String): Boolean {
        val (graph, local) = resolvePath(ref, path) ?: return false
        return opener.supported(local) && owned(ref, graph)
    }

    override suspend fun open(ref: ConversationRef, path: String, install: Boolean): Outcome<Unit> {
        val (graph, local) =
            resolvePath(ref, path) ?: return Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
        if (!owned(ref, graph)) return Outcome.Failed(Failure.Media(MediaProblem.NoAppToOpen))
        return opener.open(local, install) { owned(ref, graph) }
    }
}
