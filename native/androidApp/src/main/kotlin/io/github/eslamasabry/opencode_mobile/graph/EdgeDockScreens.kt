package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.attention.dock.DockPeek
import io.github.eslamasabry.oc.engine.attention.dock.DockRequestRef
import io.github.eslamasabry.oc.engine.attention.dock.DockSide
import io.github.eslamasabry.oc.engine.attention.dock.DockUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatEdgeDock
import io.github.eslamasabry.oc.feature.chats.dock.EdgeDockHost
import io.github.eslamasabry.oc.feature.chats.dock.EdgeDockPort
import io.github.eslamasabry.oc.feature.chats.dock.HomeEdgeDock
import io.github.eslamasabry.oc.ui.kit.dock.KitDockInsets
import io.github.eslamasabry.oc.ui.kit.dock.KitEdgeDockState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
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
) : EdgeDockPort {
    private val main = CoroutineScope(appScope.coroutineContext + dispatchers.main)

    override val ui: StateFlow<DockUi>
        get() = dock.ui

    override val shown: StateFlow<Boolean>
        get() = dock.shown

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
 * Android keeps its back gesture off the dock's scrub zone (at most 200 dp per edge, which the kit already caps). The
 * rectangle is read again whenever the dock's area is laid out.
 */
private fun dockBackGestureExclusion(state: KitEdgeDockState): Modifier =
    Modifier.systemGestureExclusion { state.gestureExclusion() ?: Rect.Zero }

/** The dock on the Conversations home: inside the safe area, clear of the floating + at the foot. */
@ContributesIntoSet(AppScope::class, binding = binding<HomeEdgeDock>())
@Inject
class AppHomeEdgeDock(private val port: AppEdgeDockPort) : HomeEdgeDock {
    @Composable
    override fun Content(clearBottom: Dp) {
        EdgeDockHost(
            port,
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            insets = KitDockInsets(bottom = clearBottom),
            undoBottom = clearBottom,
            exclusion = ::dockBackGestureExclusion,
            // The home has its own green "+" a thumb away: the dock's "+" stays for the chat, which has none.
            offersNew = false,
        )
    }
}

/** The dock in a conversation: clear of the header, the composer and the keyboard (the chat's chrome). */
@ContributesIntoSet(AppScope::class, binding = binding<ChatEdgeDock>())
@Inject
class AppChatEdgeDock(private val port: AppEdgeDockPort) : ChatEdgeDock {
    @Composable
    override fun Content(chrome: PaddingValues) {
        EdgeDockHost(
            port,
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            insets = KitDockInsets(top = chrome.calculateTopPadding(), bottom = chrome.calculateBottomPadding()),
            undoBottom = chrome.calculateBottomPadding(),
            exclusion = ::dockBackGestureExclusion,
        )
    }
}
