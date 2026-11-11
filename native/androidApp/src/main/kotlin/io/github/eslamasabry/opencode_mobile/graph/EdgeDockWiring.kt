package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.dock.DockController
import io.github.eslamasabry.oc.engine.attention.dock.DockEvent
import io.github.eslamasabry.oc.engine.attention.dock.DockFeed
import io.github.eslamasabry.oc.engine.attention.dock.DockKeys
import io.github.eslamasabry.oc.engine.attention.dock.DockNicknames
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The edge dock's data source. Feeds [DockController] from what the app already receives: the neutral gateway event
 * stream of every live connection, plus the same one reconcile read per (re)connect that the attention feed makes (the
 * stream is volatile and never replays, so a busy session or waiting request from before the connection can only be
 * learned by reading). No polling and no read of its own beyond that.
 *
 * Nothing starts it yet: the UI that draws the dock calls [start] once and consumes [ui]. Everything runs on the main
 * dispatcher, which is what [DockController] requires.
 */
@SingleIn(AppScope::class)
@Inject
class AppEdgeDock(
    private val stores: PreviewStores,
    private val connections: ConnectionRegistry,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) {
    private val main: CoroutineScope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private var navigate: (ConversationRef) -> Unit = {}
    private val nicknames =
        DockNicknames(
            object : AttentionKeyStore {
                override suspend fun read(key: String): String? =
                    (stores.prefs.get(key) as? PrefValue.Str)?.value

                override suspend fun write(key: String, value: String): Boolean {
                    val id = ProfileId(key.removePrefix(DockKeys.NAMES_PREFIX))
                    // A profile that is being deleted takes no new data.
                    if (stores.profiles.profile(id) == null || !stores.lanes.forProfile(id).isAdmitting) return false
                    return stores.prefs.commit(stores.lanes.forProfile(id)) { putString(key, value) } is Outcome.Ok
                }

                override suspend fun remove(key: String): Boolean =
                    stores.prefs.commit(stores.lanes.app) { remove(key) } is Outcome.Ok
            }
        )
    private val controller = DockController(main, nicknames, { navigate(it) })
    private val attached = mutableMapOf<ProfileId, ConnectionGraph>()
    private val savedBefore = mutableSetOf<ProfileId>()
    private var started = false

    val ui: StateFlow<DockUi> get() = controller.ui

    /** Call once from the UI that draws the dock. [navigate] opens a conversation like a tap in the list. */
    fun start(navigate: (ConversationRef) -> Unit) {
        this.navigate = navigate
        if (started) return
        started = true
        main.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            combine(connections.live, stores.profiles.profiles) { live, saved -> live to saved.map { it.id }.toSet() }
                .collect { (live, saved) ->
                    for (gone in (savedBefore - saved).toList()) {
                        detach(gone)
                        controller.profileGone(gone)
                    }
                    savedBefore.clear()
                    savedBefore += saved
                    for (id in (attached.keys - live.keys.intersect(saved)).toList()) detach(id)
                    for ((id, graph) in live) if (id in saved && attached[id] !== graph) attach(id, graph)
                }
        }
    }

    fun open(ref: ConversationRef) = controller.open(ref)

    /** Navigation reports the conversation on screen (null for none). */
    fun conversationShown(ref: ConversationRef?) = controller.conversationShown(ref)

    fun rename(ref: ConversationRef, name: String?) = controller.rename(ref, name)

    fun dismissSeen(ref: ConversationRef) = controller.dismissSeen(ref)

    fun tuck() = controller.tuck()

    fun untuck() = controller.untuck()

    private fun detach(id: ProfileId) {
        if (attached.remove(id) != null) controller.feed(DockEvent.ProfileOffline(id))
    }

    private fun attach(id: ProfileId, graph: ConnectionGraph) {
        detach(id)
        attached[id] = graph
        val server = graph.session.server
        graph.scope.launch(dispatchers.main) { controller.loadNicknames(id) }
        graph.scope.launch(dispatchers.main) {
            server.events.all.collect { envelope ->
                if (attached[id] !== graph) return@collect
                if (envelope.event == GatewayEvent.Connected) graph.scope.launch(dispatchers.main) { reconcile(id, graph) }
                for (event in DockFeed.events(id, envelope.event)) controller.feed(event)
            }
        }
        graph.scope.launch(dispatchers.main) { reconcile(id, graph) }
    }

    private suspend fun reconcile(id: ProfileId, graph: ConnectionGraph) {
        val server = graph.session.server
        val location = server.location.value
        fun current() = attached[id] === graph && server.location.value == location
        (server.sessionStatuses() as? Outcome.Ok)?.let { if (current()) controller.feed(DockFeed.statusesRead(id, it.value)) }
        val permissions = (server.pendingPermissions() as? Outcome.Ok)?.value
        val questions = (server.pendingQuestions() as? Outcome.Ok)?.value
        val forms = if (server.capabilities.value.forms) (server.pendingForms() as? Outcome.Ok)?.value else emptyList()
        if (current()) DockFeed.requestsRead(id, permissions, questions, forms)?.let(controller::feed)
    }
}
