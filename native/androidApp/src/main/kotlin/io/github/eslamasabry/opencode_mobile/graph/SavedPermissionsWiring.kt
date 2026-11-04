package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.SavedPermission
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.servers.presentation.SavedPermissionsPort
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@ContributesTo(ConnectionScope::class)
interface SavedPermissionConnection {
    val savedPermissionOperations: ServerOperationsGateway
}

@ContributesBinding(AppScope::class, binding = binding<SavedPermissionsPort>())
@Inject
class NativeSavedPermissions(
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
) : SavedPermissionsPort {
    private data class Scope(val graph: ConnectionGraph, val location: ServerLocation)

    override fun scope(profile: ProfileId): Any? {
        val graph = registry.live.value[profile] ?: return null
        val loaded = stores.profiles.profile(profile) ?: return null
        if (loaded.toServerProfile() != graph.profile) return null
        return Scope(graph, graph.session.server.location.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scopes(profile: ProfileId): Flow<Any?> =
        combine(registry.live, stores.profiles.profiles) { live, _ -> live[profile] }
            .flatMapLatest { graph ->
                if (graph == null) flowOf(null)
                else graph.session.server.location.map { scope(profile) }
            }

    override suspend fun read(
        profile: ProfileId,
        scope: Any,
    ): Outcome<ImmutableList<SavedPermission>> {
        val token = scope as? Scope ?: return stale()
        if (scope != this.scope(profile)) return stale()
        if (!token.graph.session.capabilities.value.savedPermissionList)
            return Failure.Unavailable(FeatureId("savedPermissionList")).failed()
        val read =
            (token.graph as SavedPermissionConnection).savedPermissionOperations.savedPermissions()
        return if (scope == this.scope(profile)) read else stale()
    }

    override suspend fun revoke(profile: ProfileId, scope: Any, id: String): Outcome<Unit> {
        val token = scope as? Scope ?: return stale()
        if (scope != this.scope(profile)) return stale()
        if (!token.graph.session.capabilities.value.savedPermissionList)
            return Failure.Unavailable(FeatureId("savedPermissionList")).failed()
        // No replay on timeout or scope change: this deletion is sent once to this exact gateway.
        val result =
            (token.graph as SavedPermissionConnection)
                .savedPermissionOperations
                .removeSavedPermission(id)
        return if (scope == this.scope(profile)) result else stale()
    }

    private fun stale(): Outcome<Nothing> = Failure.Refused(RefusalReason.StaleScope).failed()
}
