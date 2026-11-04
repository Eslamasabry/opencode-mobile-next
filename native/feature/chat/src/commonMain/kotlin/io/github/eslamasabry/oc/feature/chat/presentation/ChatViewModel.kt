package io.github.eslamasabry.oc.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.transcript.ledger.latest
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.GitRoute
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * One per chat entry (plan 09 §6.2): opens the conversation's world in init, releases it in
 * onCleared. All behaviour is [ChatPresenter]'s; this class only gives it the entry's scope (03
 * §14.3: WhileSubscribed(5 s)).
 */
@AssistedInject
public class ChatViewModel(
    @Assisted private val route: ChatRoute,
    private val worlds: ChatWorlds,
    private val nav: AppNavigator,
    clock: Clock,
    outside: ChatOutside,
) : ViewModel(), ChatActions {
    private val world = worlds.open(route.ref)
    private val presenter =
        ChatPresenter(
            world,
            nav,
            { block -> viewModelScope.launch { block() } },
            focusComposer = route.focusComposer,
            outside = outside,
            clock = clock,
        )

    public val requests = world.requests

    public val state: StateFlow<ChatUi> =
        presenter.states(viewModelScope, world.work, SharingStarted.WhileSubscribed(STOP_AFTER_MS))

    /**
     * CHAT-TRANSCRIPT: steps, results, context, retry and stall beside the rows (side tables, never
     * the rows).
     */
    public val facts: StateFlow<TurnFactsUi> =
        TurnFactsPresenter(clock)
            .states(
                (world as? TurnFeedSource)?.turnFeed ?: flowOf(TurnFeed.Empty),
                world.requests.latest { model ->
                    model?.state?.map { it.card != null } ?: flowOf(false)
                },
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MS),
            )

    /**
     * B-CHAT-110: a task step opens the conversation its sub-agent ran in (same server, same
     * profile).
     */
    public fun openSession(sessionId: String) {
        if (sessionId.isBlank()) return
        // The id is server-authored text: open it only when the server says it is this
        // conversation's own child (Flutter `isKnownChild` / `requireChild: true`).
        viewModelScope.launch {
            val child = world.isChildSession(sessionId)
            if (child is Outcome.Ok && child.value)
                nav.openChat(ConversationRef(world.ref.profileId, SessionId(sessionId)))
        }
    }

    override fun onCleared() {
        worlds.release(world)
    }

    override fun leaveRequested(): Unit = presenter.leaveRequested()

    override fun openGit(): Unit = nav.open(GitRoute(route.ref))

    override fun openSignInSettings(): Unit =
        nav.open(io.github.eslamasabry.oc.ui.routes.SettingsRoute.Home)

    override fun confirmLeave(): Unit = presenter.confirmLeave()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun retryLoad(): Unit = presenter.retryLoad()

    /**
     * Project Files for this conversation (P12-FILES; 07 §5 D01/D02, `/files` and the chat's Files
     * action).
     */
    override fun openFiles(): Unit = nav.open(FilesRoute.Folder(presenter.ref))

    override fun openTools(): Unit =
        nav.open(io.github.eslamasabry.oc.ui.routes.LibraryRoute(ref = route.ref))

    override fun stop(): Unit = presenter.stop()

    override fun find(query: String?): Unit = presenter.find(query)

    override fun findNext(): Unit = presenter.findNext()

    override fun findPrevious(): Unit = presenter.findPrevious()

    override fun loadOlder(): Unit = presenter.loadOlder()

    override fun toggleReasoning(): Unit = presenter.toggleReasoning()

    override fun compactAgain(): Unit = presenter.compactAgain()

    override fun openTimeline(): Unit = presenter.openTimeline()

    override fun closeTimeline(): Unit = presenter.closeTimeline()

    override fun timelineQuery(query: String): Unit = presenter.timelineQuery(query)

    override fun timelineSelect(messageId: String, fork: Boolean): Unit =
        presenter.timelineSelect(messageId, fork)

    override fun searchAllHistory(): Unit = presenter.searchAllHistory()

    override fun cancelSearchAll(): Unit = presenter.cancelSearchAll()

    override fun openMessageMenu(rowKey: String): Unit = presenter.openMessageMenu(rowKey)

    override fun openChanges(): Unit = presenter.openChanges()

    override fun closeMessageMenu(): Unit = presenter.closeMessageMenu()

    override fun messageAction(target: MessageTarget, action: MessageAction): Unit =
        presenter.messageAction(target, action)

    override fun confirmDelete(): Unit = presenter.confirmDelete()

    override fun clipDone(id: Long): Unit = presenter.clipDone(id)

    override fun readAloudDone(id: Long): Unit = presenter.readAloudDone(id)

    override fun arrivalShown(id: Long): Unit = presenter.arrivalShown(id)

    override fun liveEdgeChanged(atLiveEdge: Boolean): Unit = presenter.liveEdgeChanged(atLiveEdge)

    override fun continueReply(words: String): Unit = presenter.continueReply(words)

    override fun checkReceipt(localId: Long): Unit = presenter.checkReceipt(localId)

    override fun retrySend(localId: Long): Unit = presenter.retrySend(localId)

    override fun dismissStatus(id: String): Unit = presenter.dismissStatus(id)

    override fun openLink(url: String): Unit = presenter.openLink(url)

    override fun confirmOpenLink(): Unit = presenter.confirmOpenLink()

    override fun copyText(text: String): Unit = presenter.copyText(text)

    override fun closeSelection(): Unit = presenter.closeSelection()

    override fun insertDone(id: Long): Unit = presenter.insertDone(id)

    override fun confirmUndo(): Unit = presenter.confirmUndo()

    override suspend fun readPicture(path: String): ByteArray? = presenter.readPicture(path)

    override fun openPicture(path: String, name: String): Unit = presenter.openPicture(path, name)

    override fun closePicture(): Unit = presenter.closePicture()

    override fun openPath(path: String, line: Int?): Unit = presenter.openPath(path, line)

    override fun confirmPhoneFile(): Unit = presenter.confirmPhoneFile()

    override fun checkPaths(paths: List<String>): Unit = presenter.checkPaths(paths)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatViewModel
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
