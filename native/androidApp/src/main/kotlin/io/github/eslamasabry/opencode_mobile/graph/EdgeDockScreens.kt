package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockReader
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.feature.chat.arrival.ChatArrivals
import io.github.eslamasabry.oc.feature.chat.ui.ChatEdgeDock
import io.github.eslamasabry.oc.feature.chats.lamp.HeldStops
import io.github.eslamasabry.oc.feature.chats.lamp.LampListController
import io.github.eslamasabry.oc.feature.chats.lamp.LampPlace
import io.github.eslamasabry.oc.feature.chats.presentation.HeldAnswers
import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import io.github.eslamasabry.oc.feature.chats.dock.EdgeDockPort
import io.github.eslamasabry.oc.feature.chats.dock.HomeEdgeDock
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The edge dock's screens' port: the one [AppEdgeDock] (started once at app scope by `AttentionRuntime`), the app's
 * navigator, and the acts' existing paths (Deny and Allow: the home list's permission answer; Stop: the chat's own
 * stop, `stopLikeTheChat`). The peek lines of every shown conversation are combined here once, at app scope, while a
 * screen shows the dock.
 */
@SingleIn(AppScope::class)
@Inject
class AppEdgeDockPort(
    private val dock: AppEdgeDock,
    private val navigator: AppNavigator,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val held: HeldAnswers,
    private val stops: HeldStops,
    private val lampPort: AppLampPort,
    private val clock: Clock,
    private val arrivals: ChatArrivals,
) : EdgeDockPort {
    private val main = CoroutineScope(appScope.coroutineContext + dispatchers.main)

    override val ui: StateFlow<DockUi>
        get() = dock.ui

    override val shown: StateFlow<Boolean>
        get() = dock.shown

    /** Rings, Edge light or Hidden: which of the two hosts is mounted (see [EdgeLightRules.mounts]). */
    val mode: StateFlow<OtherConversationsMode>
        get() = dock.mode

    /** The edge light's side and height. */
    val lightPlace: StateFlow<EdgeLightPlace>
        get() = dock.lightPlace

    /** How many Back notes and first-use tips the light has shown on this device. */
    val lightTaught: StateFlow<EdgeLightTaught>
        get() = dock.lightTaught

    /** True once device-wide edge light preferences have been read from disk. */
    val lightPrefsLoaded: StateFlow<Boolean>
        get() = dock.lightPrefsLoaded

    /** Records that the edge light's one-time first-use tip was shown. */
    fun recordLightTipShown() = dock.taughtMore(tips = 1)

    /**
     * The light's list: every other conversation's state and progress, up to the list reader's 20 rows. Read only
     * while a light is on screen (it is made on first use, so the rings alone never ask for it).
     */
    val lightList: StateFlow<DockListModel> by lazy {
        dock.list(DockReader.list()).stateIn(main, SharingStarted.WhileSubscribed(PEEK_KEEP_MS), DockListModel())
    }

    /** The home's "All conversations" number, for the panel's footer (0 until the home list is read). */
    val allCount: StateFlow<Int> by lazy { dock.allConversations.stateIn(main, SharingStarted.WhileSubscribed(PEEK_KEEP_MS), 0) }

    /** The light's saved place as the list's own kind of place. */
    val lampPlace: StateFlow<LampPlace> by lazy {
        dock.lightPlace.map { it.toLampPlace() }.stateIn(main, SharingStarted.Eagerly, dock.lightPlace.value.toLampPlace())
    }

    /**
     * The light is about to switch to [target], leaving the conversation titled [leftTitle] (null on a page with no
     * conversation of its own): the target's page says "Reply in ‹title›" and, the first three times on this device,
     * where Back goes. Called right before the switch, never for a notification, a home row or a ring.
     */
    fun arriving(target: ConversationRef, leftTitle: String?) {
        arrivals.switched(target, leftTitle)
    }

    /**
     * The panel's brain, made when a light is mounted and cancelled with [scope] (the screen's): the list, the shared
     * holds, the dock's pins, names and hides, the routes. Holds live in the app scope, so closing cancels none.
     */
    fun lampController(scope: CoroutineScope): LampListController =
        LampListController(scope, lightList, navigator, held, stops, lampPort, clock, lampPlace)

    override val onScreen: StateFlow<ConversationRef?> =
        navigator.top.map { (it as? ChatRoute)?.ref }.stateIn(main, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val peeks: StateFlow<Map<ConversationRef, DockPeek>> =
        dock.ui
            .map { ui -> ui.entries.flatMap { e -> listOf(e.ref) + e.children.map { it.ref } } }
            .distinctUntilChanged()
            .flatMapLatest { refs ->
                if (refs.isEmpty()) flowOf(emptyMap<ConversationRef, DockPeek>())
                else combine(refs.map { ref -> dock.peek(ref).map { ref to it } }) { it.toMap() }
            }
            .stateIn(main, SharingStarted.WhileSubscribed(PEEK_KEEP_MS), emptyMap())

    override fun open(ref: ConversationRef) = dock.open(ref)

    override fun openRequest(ref: ConversationRef, requestId: String) {
        // Already there: the request card is on screen.
        if ((navigator.top.value as? ChatRoute)?.ref == ref) return
        navigator.open(ChatRoute(ref, requestId = requestId))
    }

    override fun answer(ref: ConversationRef, request: DockRequestRef, allow: Boolean, onFailed: () -> Unit) {
        main.launch { if (dock.answer(ref, request, allow) is Outcome.Failed) onFailed() }
    }

    override fun stop(ref: ConversationRef, onFailed: () -> Unit) {
        main.launch { if (dock.stop(ref) is Outcome.Failed) onFailed() }
    }

    override fun rename(ref: ConversationRef, name: String?) = dock.rename(ref, name)

    override fun rename(ref: ConversationRef, name: String?, onFailed: () -> Unit) = dock.rename(ref, name, onFailed)

    override fun remove(ref: ConversationRef) = dock.remove(ref)

    override fun undoRemove(ref: ConversationRef) = dock.undoRemove(ref)

    override fun restore(ref: ConversationRef) = dock.restore(ref)

    override fun pin(ref: ConversationRef) = dock.pin(ref)

    override fun unpin(ref: ConversationRef) = dock.unpin(ref)

    override fun reorder(from: ConversationRef, to: ConversationRef) = dock.reorder(from, to)

    override fun setPlacement(side: DockSide, bias: Float) = dock.setPlacement(side, bias)

    override fun tuck() = dock.tuck()

    override fun untuck() = dock.untuck()

    override fun newConversation() = navigator.open(ChatsRoute.New(profile = null))

    private companion object {
        const val PEEK_KEEP_MS = 5_000L
    }
}

/**
 * Home never mounts a switcher (the home list already shows every conversation); Rings are retired, so this is a no-op.
 */
@ContributesIntoSet(AppScope::class, binding = binding<HomeEdgeDock>())
@Inject
class AppHomeEdgeDock : HomeEdgeDock {
    @Composable
    override fun Content(@Suppress("UNUSED_PARAMETER") clearBottom: Dp) = Unit
}

/** The switcher in a conversation: Edge light only (Rings are never mounted), clear of the chat's chrome. */
@ContributesIntoSet(AppScope::class, binding = binding<ChatEdgeDock>())
@Inject
class AppChatEdgeDock(private val port: AppEdgeDockPort, private val shell: ShellNavigator) : ChatEdgeDock {
    @Composable
    override fun Content(chrome: PaddingValues, @Suppress("UNUSED_PARAMETER") onBottomRoom: (Int) -> Unit) {
        val mode by port.mode.collectAsStateWithLifecycle()
        val onScreen by port.onScreen.collectAsStateWithLifecycle()
        // Big screens: the list beside the conversation already shows every conversation.
        val listShown by shell.listShown.collectAsStateWithLifecycle()
        if (!EdgeLightRules.lightShows(mode, listShown)) return
        EdgeLightMount(
            port = port,
            page = onScreen,
            top = chrome.calculateTopPadding(),
            bottom = chrome.calculateBottomPadding(),
            // TODO(KC1): the message box's published top replaces this fixed gap above the bottom chrome.
            lowAbove = EdgeLightRules.LOW_ABOVE_CHROME_DP.dp,
        )
    }
}
