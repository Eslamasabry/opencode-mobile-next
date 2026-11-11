package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.dock.DockActions
import io.github.eslamasabry.oc.engine.attention.dock.DockController
import io.github.eslamasabry.oc.engine.attention.dock.DockEvent
import io.github.eslamasabry.oc.engine.attention.dock.DockFeed
import io.github.eslamasabry.oc.engine.attention.dock.DockKeys
import io.github.eslamasabry.oc.engine.attention.dock.DockNicknames
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockPeekFallback
import io.github.eslamasabry.oc.engine.attention.dock.DockPeeks
import io.github.eslamasabry.oc.engine.attention.dock.DockPins
import io.github.eslamasabry.oc.engine.attention.dock.DockPlacementStore
import io.github.eslamasabry.oc.engine.attention.dock.DockRemovals
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The edge dock's data source. Feeds [DockController] from what the app already receives: the neutral gateway event
 * stream of every live connection, plus the same one reconcile read per (re)connect that the attention feed makes (the
 * stream is volatile and never replays, so a busy session or waiting request from before the connection can only be
 * learned by reading). No polling and no read of its own beyond that.
 *
 * Saved per profile (all swept with the profile by `DockKeys.register`): `oc.dockNames.<id>`, `oc.dockRemoved.<id>`,
 * `oc.dockPins.<id>`. Saved for the device (never swept): `oc.dockSide`, `oc.dockBias`.
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
    private val home: AppChatsHome,
    private val permissionAnswers: HomePermissionAnswerWiring,
) {
    private val main: CoroutineScope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private var navigate: (ConversationRef) -> Unit = {}
    private val keyStore =
        object : AttentionKeyStore {
            override suspend fun read(key: String): String? = (stores.prefs.get(key) as? PrefValue.Str)?.value

            override suspend fun write(key: String, value: String): Boolean {
                // Device-wide keys carry no profile id.
                if (key == DockKeys.SIDE || key == DockKeys.BIAS) {
                    return stores.prefs.commit(stores.lanes.app) { putString(key, value) } is Outcome.Ok
                }
                val id =
                    ProfileId(
                        key.removePrefix(DockKeys.NAMES_PREFIX)
                            .removePrefix(DockKeys.REMOVED_PREFIX)
                            .removePrefix(DockKeys.PINS_PREFIX)
                    )
                // A profile that is being deleted takes no new data.
                if (stores.profiles.profile(id) == null || !stores.lanes.forProfile(id).isAdmitting) return false
                return stores.prefs.commit(stores.lanes.forProfile(id)) { putString(key, value) } is Outcome.Ok
            }

            override suspend fun remove(key: String): Boolean =
                stores.prefs.commit(stores.lanes.app) { remove(key) } is Outcome.Ok
        }
    private val nicknames = DockNicknames(keyStore)

    /** The home list's saved line per conversation, read once per profile and kept until the profile reattaches. */
    private val savedLines = mutableMapOf<ProfileId, Map<SessionId, String>>()
    private val peeks =
        DockPeeks(
            DockPeekFallback { ref ->
                savedLines
                    .getOrPut(ref.profileId) {
                        home.cachedRows(ref.profileId).filter { it.preview.isNotBlank() }.associate { it.sessionId to it.preview }
                    }[ref.sessionId]
            }
        )

    /** Delegates to the engines that already do these things; the dock adds no protocol call. */
    private val actions =
        object : DockActions {
            override suspend fun answerPermission(ref: ConversationRef, requestId: String, allow: Boolean): Outcome<Unit> =
                permissionAnswers.answer(ref, requestId, allow)

            override suspend fun stop(ref: ConversationRef): Outcome<Unit> {
                val delivery =
                    (connections.graphFor(ref) as? ChatGraphAccessors)?.chatDelivery?.engine
                        ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
                return delivery.stop(ref)
            }
        }
    private val controller =
        DockController(
            main, nicknames, { navigate(it) },
            removals = DockRemovals(keyStore),
            pins = DockPins(keyStore),
            placementStore = DockPlacementStore(keyStore),
            actions = actions,
            peeks = peeks,
        )
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
            controller.loadPlacement()
            combine(connections.live, stores.profiles.profiles) { live, saved -> live to saved }
                .collect { (live, loaded) ->
                    val saved = loaded.map { it.id }.toSet()
                    for (profile in loaded) controller.feed(DockEvent.ServerName(profile.id, profile.profile.name))
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

    /** Removes a ring (or sub-agent) from the dock; see `DockModel` for when it comes back by itself. */
    fun remove(ref: ConversationRef) = controller.remove(ref)

    fun undoRemove(ref: ConversationRef) = controller.undoRemove(ref)

    fun restore(ref: ConversationRef) = controller.restore(ref)

    fun pin(ref: ConversationRef) = controller.pin(ref)

    fun unpin(ref: ConversationRef) = controller.unpin(ref)

    fun reorder(from: ConversationRef, to: ConversationRef) = controller.reorder(from, to)

    /** Device-wide side and vertical position (0..1). */
    fun setPlacement(side: DockSide, bias: Float) = controller.setPlacement(side, bias)

    /** The last lines of a conversation without switching to it; no read happens, see `DockPeeks`. */
    fun peek(ref: ConversationRef): Flow<DockPeek> = controller.peek(ref)

    /** Allow or reject a waiting permission now; the UI may offer its own Undo, the dock holds nothing. */
    suspend fun answer(ref: ConversationRef, request: DockRequestRef, allow: Boolean): Outcome<Unit> =
        controller.answer(ref, request, allow)

    suspend fun stop(ref: ConversationRef): Outcome<Unit> = controller.stop(ref)

    fun tuck() = controller.tuck()

    fun untuck() = controller.untuck()

    private fun detach(id: ProfileId) {
        if (attached.remove(id) != null) controller.feed(DockEvent.ProfileOffline(id))
    }

    private fun attach(id: ProfileId, graph: ConnectionGraph) {
        detach(id)
        attached[id] = graph
        savedLines.remove(id)
        val server = graph.session.server
        graph.scope.launch(dispatchers.main) {
            server.events.all.collect { envelope ->
                if (attached[id] !== graph) return@collect
                if (envelope.event == GatewayEvent.Connected) graph.scope.launch(dispatchers.main) { reconcile(id, graph) }
                peeks.feed(id, envelope.event)
                for (event in DockFeed.events(id, envelope.event)) controller.feed(event)
            }
        }
        graph.scope.launch(dispatchers.main) {
            // Saved names, removed set and pins first, so the first reconcile lands on a model that already knows them.
            controller.loadNicknames(id)
            controller.loadRemoved(id)
            val pinned = controller.loadPins(id).toSet()
            // A pinned ring that is idle has no event to name it: the home list's saved titles do.
            if (pinned.isNotEmpty()) {
                for (row in home.cachedRows(id)) if (row.sessionId in pinned) {
                    controller.feed(DockEvent.Meta(ConversationRef(id, row.sessionId), row.title, null))
                }
            }
            reconcile(id, graph)
        }
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
