package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.attention.activity.AttentionKeyStore
import io.github.eslamasabry.oc.engine.attention.dock.DockActions
import io.github.eslamasabry.oc.engine.attention.dock.DockController
import io.github.eslamasabry.oc.engine.attention.dock.DockEvent
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.attention.dock.DockFacts
import io.github.eslamasabry.oc.engine.attention.dock.DockFeed
import io.github.eslamasabry.oc.engine.attention.dock.DockList
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockReader
import io.github.eslamasabry.oc.engine.attention.dock.DockHomeWatch
import io.github.eslamasabry.oc.engine.attention.dock.DockKeys
import io.github.eslamasabry.oc.engine.attention.dock.DockNicknames
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockPeekFallback
import io.github.eslamasabry.oc.engine.attention.dock.DockPeeks
import io.github.eslamasabry.oc.engine.attention.dock.DockPins
import io.github.eslamasabry.oc.engine.attention.dock.DockPlacementStore
import io.github.eslamasabry.oc.engine.attention.dock.DockRecent
import io.github.eslamasabry.oc.engine.attention.dock.DockRemovals
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.SubAgentChildSource
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The conversations the person archived or deleted from the conversation lists, told by identity once the server took
 * it (after the home's Undo window). The server sends the app nothing for an archive, so this is how the dock hears it.
 */
@SingleIn(AppScope::class)
@Inject
class ConversationsLeft {
    private val flow = MutableSharedFlow<ConversationRef>(extraBufferCapacity = LEFT_BUFFER)
    val left: SharedFlow<ConversationRef> = flow.asSharedFlow()

    fun tell(ref: ConversationRef) {
        flow.tryEmit(ref)
    }

    private companion object {
        const val LEFT_BUFFER = 64
    }
}

/**
 * The edge dock's data source. Feeds [DockController] from what the app already receives: the neutral gateway event
 * stream of every live connection, plus the same one reconcile read per (re)connect that the attention feed makes (the
 * stream is volatile and never replays, so a busy session or waiting request from before the connection can only be
 * learned by reading). No polling and no read of its own beyond that.
 *
 * Saved per profile (all swept with the profile by `DockKeys.register`): `oc.dockNames.<id>`, `oc.dockRemoved.<id>`,
 * `oc.dockPins.<id>`. Saved for the device (never swept): `oc.dockSide`, `oc.dockBias`, and `oc.dockShown` (the
 * person's Settings switch; absent = on).
 *
 * The app starts it once at app scope (`AttentionRuntime.start`). Opening a ring goes through the app's navigator
 * ([AppNavigator.backToChat]: back to a conversation right below, else open it), and the conversation on screen is
 * read from the navigator's top route. Everything runs on the main dispatcher, which is what [DockController] requires.
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
    private val navigator: AppNavigator,
    private val runtime: PreviewRuntime,
    private val conversationsLeft: ConversationsLeft,
    private val storedPins: StoredConversationPins,
    private val clock: Clock,
) {
    private val main: CoroutineScope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private var navigate: (ConversationRef) -> Unit = { navigator.backToChat(it) }
    private val shownState = MutableStateFlow(true)
    private val keyStore =
        object : AttentionKeyStore {
            override suspend fun read(key: String): String? = (stores.prefs.get(key) as? PrefValue.Str)?.value

            override suspend fun write(key: String, value: String): Boolean {
                // Device-wide keys carry no profile id.
                if (key == DockKeys.SIDE || key == DockKeys.BIAS || key == SHOWN_KEY) {
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

            override suspend fun stop(ref: ConversationRef): Outcome<Unit> = stopLikeTheChat(connections.graphFor(ref), ref)

            // The same call the conversation's own "Rename conversation" makes, then the home list is read again so
            // the home, the chat header and the dock show one title.
            override suspend fun renameConversation(ref: ConversationRef, title: String): Outcome<Unit> {
                val graph = connections.graphFor(ref) ?: return Outcome.Failed(Failure.Unavailable(FeatureId("dockRename")))
                return graph.session.server.renameSession(ref.sessionId, title).also {
                    // Not awaited: the ring shows the new title at once; the list read can take a while.
                    if (it is Outcome.Ok) main.launch { runtime.refresh() }
                }
            }
        }
    private val controller =
        DockController(
            main, nicknames, { navigate(it) },
            removals = DockRemovals(keyStore),
            pins = DockPins(keyStore, storedPins.dockOrder),
            placementStore = DockPlacementStore(keyStore),
            actions = actions,
            peeks = peeks,
        )
    private val attached = mutableMapOf<ProfileId, ConnectionGraph>()
    private val savedBefore = mutableSetOf<ProfileId>()
    private var started = false

    val ui: StateFlow<DockUi> get() = controller.ui

    /** What waiting requests say and when a conversation last did anything (needs a clock the reducer has none of). */
    private val facts = DockFacts { clock.now().toEpochMilliseconds() }

    /** The same conversations read by another reader (the edge light's list asks for more rows than the rings). */
    fun ui(reader: DockReader): StateFlow<DockUi> = controller.ui(reader)

    /** The edge light's list for [reader]: states, progress, words, newest line, sub-agents, server, pin, hidden. */
    fun list(reader: DockReader = DockReader.list()): Flow<DockListModel> =
        controller.ui(reader).map { DockList.build(it, facts, peeks::newest) }

    /** The person's Settings switch: false draws no dock in the app (the live notification row does not read it). */
    val shown: StateFlow<Boolean> = shownState.asStateFlow()

    /** Turns the in-app dock on or off for this device, saved at once. */
    fun setShown(on: Boolean) {
        if (shownState.value == on) return
        shownState.value = on
        main.launch { keyStore.write(SHOWN_KEY, on.toString()) }
    }

    /**
     * Starts the dock once. [navigate] is the in-app dock UI's way to open a conversation; a caller that only reads the
     * dock (the live notification) passes none, so it never replaces the UI's navigation, whichever starts first.
     */
    fun start(navigate: ((ConversationRef) -> Unit)? = null) {
        if (navigate != null) this.navigate = navigate
        if (started) return
        started = true
        main.launch {
            // The conversation on screen, however it was reached (a list row, a notification, the dock).
            navigator.top.map { (it as? ChatRoute)?.ref }.distinctUntilChanged().collect { conversationShown(it) }
        }
        main.launch {
            // A pin made on the home list (or anywhere else) shows on the dock: the dock reads the one store again.
            storedPins.dockOrder.changes.collect { for (id in attached.keys.toList()) controller.refreshPins(id) }
        }
        main.launch {
            // An archive or delete the home or All conversations committed (after the Undo window, so an undone one
            // never leaves): its ring goes at once.
            conversationsLeft.left.collect {
                facts.forget(it)
                controller.feed(DockEvent.Left(it))
            }
        }
        main.launch {
            // The fallback for an archive made elsewhere (the conversation's own menu, another device), which no event
            // and no list tells the dock: the ring goes when a whole home list stops showing it.
            val watch = DockHomeWatch()
            runtime.snapshot.collect { list ->
                val listed = attached.keys.filter(runtime::listSourceShown).toSet()
                val roots = list.items.filter { it.parentId == null }.map { it.ref }
                for (event in watch.events(roots, list.complete && list.acrossProjects && !list.loading, listed)) controller.feed(event)
            }
        }
        main.launch {
            if (stores.warm() !is Outcome.Ok) return@launch
            keyStore.read(SHOWN_KEY)?.let { shownState.value = it != "false" }
            controller.loadPlacement()
            combine(connections.live, stores.profiles.profiles) { live, saved -> live to saved }
                .collect { (live, loaded) ->
                    val saved = loaded.map { it.id }.toSet()
                    for (profile in loaded) {
                        // The phone agents' rings are This phone's: their server is named as the phone is.
                        val phone = stores.phoneOfHelper(profile.id)?.let { id -> loaded.firstOrNull { it.id == id } }
                        controller.feed(DockEvent.ServerName(profile.id, (phone ?: profile).profile.name))
                        // The mark the home row shows: OpenCode's for an OpenCode server, Codex's for Codex; an agents
                        // server names its agent per conversation (the ring's own agent id), else no mark.
                        controller.feed(
                            DockEvent.ServerMark(
                                profile.id,
                                when (profile.profile.backend) {
                                    BackendKind.OpenCode2 -> "opencode"
                                    BackendKind.Codex -> "codex"
                                    BackendKind.Paseo -> null
                                },
                            )
                        )
                    }
                    for (gone in (savedBefore - saved).toList()) {
                        detach(gone)
                        facts.forgetProfile(gone)
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

    fun rename(ref: ConversationRef, name: String?, onFailed: () -> Unit = {}) = controller.rename(ref, name, onFailed)

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

    private companion object {
        /** Device-wide, never swept: the in-app dock is on unless the person turned it off. */
        const val SHOWN_KEY = "oc.dockShown"
    }

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
                facts.feed(id, envelope.event)
                for (event in DockFeed.events(id, envelope.event)) controller.feed(event)
            }
        }
        graph.scope.launch(dispatchers.main) {
            // Sub-agents that existed before the app connected: no event names their parent, the backfill reads them
            // (on attach, on every reconnect and when a conversation starts a run; nothing while the server cannot
            // list sub-agents). The same neutral source as Go to > Subagents.
            graph.subAgentReads { controller.ui.value.entries.filter { it.ref.profileId == id }.map { it.ref.sessionId } }
                .collect { read ->
                    if (attached[id] !== graph) return@collect
                    for (event in SubAgentChildSource.events(id, read)) controller.feed(event)
                }
        }
        graph.scope.launch(dispatchers.main) {
            // Saved names, removed set and pins first, so the first reconcile lands on a model that already knows them.
            controller.loadNicknames(id)
            controller.loadRemoved(id)
            val pinned = controller.loadPins(id).toSet()
            val rows = home.cachedRows(id)
            // A pinned ring that is idle has no event to name it: the home list's saved titles do.
            if (pinned.isNotEmpty()) {
                for (row in rows) if (row.sessionId in pinned) {
                    controller.feed(DockEvent.Meta(ConversationRef(id, row.sessionId), row.title, null))
                }
            }
            // The dock is always available: it offers the recent conversations the home list knows as quiet rings.
            val recent = rows.map { DockRecent(it.sessionId, it.title, it.lastActivityMs, it.parentId) }
            facts.listed(id, recent)
            controller.feed(DockEvent.Recent(id, recent))
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
        if (current()) {
            facts.requestsRead(id, permissions, questions, forms)
            DockFeed.requestsRead(id, permissions, questions, forms)?.let(controller::feed)
        }
    }
}
