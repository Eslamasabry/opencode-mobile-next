package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.stringList
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationServers
import io.github.eslamasabry.oc.feature.chats.presentation.NoConversationServers
import io.github.eslamasabry.oc.feature.projects.presentation.NoProjectServers
import io.github.eslamasabry.oc.feature.projects.presentation.OpenedFolders
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectRules
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServer
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectServers
import io.github.eslamasabry.oc.feature.projects.presentation.SessionOpenedFolders
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/*
 * P12 PROJECTS wiring: the live connections for All conversations and the projects pages, and the folders opened on
 * this phone. Each row's server is that exact live graph's gateway; nothing here moves a connection's location.
 */

/** One row per live graph, in saved-server order, named as the person named the server. */
private fun <T> liveRows(
    live: ImmutableMap<ProfileId, ConnectionGraph>,
    saved: List<LoadedProfile>,
    make: (ProfileId, String, ConnectionGraph) -> T,
): ImmutableList<T> {
    val order = saved.map { it.id }
    return live.entries
        .sortedBy { (id, _) -> order.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it } }
        .map { (id, graph) ->
            val name = saved.firstOrNull { it.id == id }?.profile?.name ?: graph.profile.name
            make(id, name, graph)
        }
        .toImmutableList()
}

private fun ConnectionGraph.operations(): ServerOperationsGateway? =
    session.server.facet<ServerOperationsGateway>()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoConversationServers::class])
@Inject
class LiveConversationServers(
    registry: ConnectionRegistry,
    stores: PreviewStores,
    appScope: AppCoroutineScope,
) : ConversationServers {
    override val servers: StateFlow<ImmutableList<ConversationServer>> =
        combine(registry.live, stores.profiles.profiles) { live, saved ->
                liveRows(live, saved) { id, name, graph ->
                    ConversationServer(id, name, graph.session.server, graph.operations())
                }
            }
            .stateIn(appScope, SharingStarted.Eagerly, persistentListOf())
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoProjectServers::class])
@Inject
class LiveProjectServers(
    registry: ConnectionRegistry,
    stores: PreviewStores,
    appScope: AppCoroutineScope,
    attention: AppElsewhereAttention,
) : ProjectServers {
    override val servers: StateFlow<ImmutableList<ProjectServer>> =
        combine(registry.live, stores.profiles.profiles, attention.trackers) { live, saved, trackers
                ->
                liveRows(live, saved) { id, name, graph ->
                    ProjectServer(
                        id,
                        name,
                        graph.session.server,
                        graph.operations(),
                        trackers[id]?.state,
                    )
                }
            }
            .stateIn(appScope, SharingStarted.Eagerly, persistentListOf())

    override val active: StateFlow<ProfileId?> = stores.profiles.activeId
}

/**
 * Folders opened on this phone, per server, under `oc.openedProjects.<profileId>` (B-ANDR-078). The
 * profile sweep (ProfileKeyRule `oc.<what>.<id>`) removes the key with the server, and writes go
 * through that profile's writer lane, which deletion closes first, so a late write cannot bring the
 * list back.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [SessionOpenedFolders::class])
@Inject
class StoredOpenedFolders(private val stores: PreviewStores) : OpenedFolders {
    private fun key(profile: ProfileId): String = "oc.openedProjects.${profile.raw}"

    override fun recent(profile: ProfileId): ImmutableList<String> {
        // Only a saved, loaded server has a list; before the stores are warm there is nothing to
        // read yet.
        if (stores.profiles.profile(profile) == null) return persistentListOf()
        return stores.prefs.stringList(key(profile)).orEmpty().toImmutableList()
    }

    override suspend fun remember(profile: ProfileId, directory: String): Outcome<Unit> {
        if (stores.profiles.profile(profile) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val next = ProjectRules.remembered(recent(profile), directory)
        return stores.prefs.commit(stores.lanes.forProfile(profile)) {
            putStringList(key(profile), next)
        }
    }
}
