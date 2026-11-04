package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.queue.*
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.servers.presentation.*
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/**
 * Read-only row projection. An old/incomplete monitor snapshot never becomes a zero or an
 * all-clear.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerRowsPort>())
@Inject
class NativeServerRows(
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val monitor: AndroidProfileMonitoring,
    appScope: AppCoroutineScope,
) : ServerRowsPort {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val requests =
        registry.live.flatMapLatest { graphs ->
            // Capabilities arrive after the graph does; the account and queue-move facts read them.
            val flows =
                graphs.values.flatMap { graph ->
                    listOfNotNull(
                        (graph as? ChatGraphAccessors)?.chatRequests?.engine?.pending?.map { Unit },
                        graph.session.capabilities.map { Unit },
                    )
                }
            if (flows.isEmpty()) flowOf(Unit)
            else merge(*flows.toTypedArray()).onStart { emit(Unit) }
        }
    private val ticks =
        merge(stores.prefs.changes.map { Unit }, requests, stores.profiles.activeId.map { Unit })
            .onStart { emit(Unit) }
    override val state: StateFlow<ServerRowsState> = flow {
        when (stores.warm()) {
            is Outcome.Failed -> emit(ServerRowsState())
            is Outcome.Ok ->
                emitAll(
                    combine(
                        stores.profiles.profiles,
                        monitor.snapshots,
                        runtime.snapshot,
                        registry.live,
                        ticks,
                    ) { profiles, snapshots, feed, live, _ ->
                        val queue =
                            (OfflineQueueStore(stores.prefs, stores.lanes.app).load()
                                    as? QueueRead.Readable)
                                ?.entries
                        val active = stores.profiles.activeId.value
                        val move = active?.takeIf {
                            live[it]?.session?.capabilities?.value?.offlinePromptQueue == true
                        }
                        val facts =
                            profiles
                                .associate { loaded ->
                                    val id = loaded.id
                                    val current = snapshots[id.raw]?.takeIf { it.isCurrent }
                                    val graph = live[id]
                                    val pending =
                                        (graph as? ChatGraphAccessors)
                                            ?.chatRequests
                                            ?.engine
                                            ?.pending
                                            ?.value
                                            ?.size
                                    val running =
                                        if (graph != null)
                                            feed.items.count {
                                                it.profileId == id &&
                                                    it.status == ChatStatus.Running
                                            }
                                        else current?.runningCount
                                    id to
                                        ServerRowFacts(
                                            loaded.profile.backend,
                                            loaded.profile.serverVersion,
                                            loaded.profile.codexDirectory,
                                            loaded.reentry.password || loaded.reentry.agentToken,
                                            pending ?: current?.pendingCount,
                                            running,
                                            if (graph != null && id == active) 0
                                            else queue?.count { it.profileId == id } ?: 0,
                                            account =
                                                id == active &&
                                                    graph
                                                        ?.session
                                                        ?.capabilities
                                                        ?.value
                                                        ?.agentAccount == true,
                                        )
                                }
                                .toImmutableMap()
                        ServerRowsState(facts, move)
                    }
                )
        }
    }
        .stateIn(appScope, SharingStarted.WhileSubscribed(5_000), ServerRowsState())
}
