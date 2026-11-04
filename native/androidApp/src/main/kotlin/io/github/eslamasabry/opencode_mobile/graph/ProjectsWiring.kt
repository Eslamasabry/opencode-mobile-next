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
import io.github.eslamasabry.oc.core.domain.LocatedProjectGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.engine.runtime.switching.ManagedLocalServer
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
        ?: session.server
            .facet<LocatedProjectGateway>()
            ?.operationsAt(session.server.location.value)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoConversationServers::class])
@Inject
class LiveConversationServers(
    private val registry: ConnectionRegistry,
    stores: PreviewStores,
    appScope: AppCoroutineScope,
) : ConversationServers {
    override fun prefetch(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef) {
        registry.graphFor(ref)?.session?.reads?.prefetch(ref.sessionId)
    }

    override fun forget(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef) {
        registry.graphFor(ref)?.session?.reads?.forget(ref.sessionId)
    }

    override val servers: StateFlow<ImmutableList<ConversationServer>> =
        combine(registry.live, stores.profiles.profiles) { live, saved ->
                liveRows(live, saved) { id, name, graph ->
                    ConversationServer(
                        id,
                        name,
                        graph.session.server,
                        graph.operations(),
                        graph.session.reads.missing,
                    )
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
                        // B-ANDR-051: only the app-managed OpenCode on this phone (Flutter
                        // isManagedPhoneProfile), never any loopback tunnel to another machine.
                        graph.profile.managedOnPhone ||
                            graph.profile.backend == BackendKind.OpenCode2 &&
                                graph.profile.origin.toString().let {
                                    ManagedLocalServer.isTermux(it) || ManagedLocalServer.isBuiltin(it)
                                },
                        attention = trackers[id]?.state,
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

/** Persisted pin choices share one acknowledged writer with the normal profile-deletion fence. */
@SingleIn(AppScope::class)
@ContributesBinding(
    AppScope::class,
    replaces = [io.github.eslamasabry.oc.feature.chats.presentation.NoConversationPins::class],
)
@Inject
class StoredConversationPins(private val stores: PreviewStores) :
    io.github.eslamasabry.oc.feature.chats.presentation.ConversationPins {
    private val pins =
        io.github.eslamasabry.oc.engine.settings.pins.SessionPinStore(stores.prefs, stores.lanes)

    private fun scope(server: ConversationServer): String {
        val location = server.gateway.location.value
        return io.github.eslamasabry.oc.engine.settings.pins.SessionPinStore.scope(
            location.directory,
            location.workspace,
        )
    }

    override fun ids(
        server: ConversationServer
    ): Set<io.github.eslamasabry.oc.core.common.ids.SessionId> {
        if (
            stores.profiles.profile(server.profile) == null ||
                !stores.lanes.forProfile(server.profile).isAdmitting
        ) {
            pins.forget(server.profile)
            return emptySet()
        }
        return pins.ids(server.profile, scope(server)).mapTo(linkedSetOf()) {
            io.github.eslamasabry.oc.core.common.ids.SessionId(it)
        }
    }

    override suspend fun set(
        server: ConversationServer,
        id: io.github.eslamasabry.oc.core.common.ids.SessionId,
        pinned: Boolean,
    ): Outcome<Unit> {
        if (stores.profiles.profile(server.profile) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        val captured = scope(server)
        val result = pins.setPinned(server.profile, captured, id.raw, pinned)
        if (scope(server) != captured) return Failure.Refused(RefusalReason.StaleScope).failed()
        return result
    }
}

@dev.zacsweers.metro.BindingContainer
@dev.zacsweers.metro.ContributesTo(AppScope::class)
object StateHistoryBindings {
    @dev.zacsweers.metro.Provides
    @SingleIn(AppScope::class)
    fun context(
        stores: PreviewStores
    ): io.github.eslamasabry.oc.engine.settings.context.ContextUsageHistory =
        io.github.eslamasabry.oc.engine.settings.context.ContextUsageMemory(
            stores.prefs,
            stores.lanes,
        ) {
            stores.profiles.profile(it) != null
        }
}
