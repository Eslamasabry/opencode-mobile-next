package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One per chat entry (plan 09 §6.2): opens the conversation's world in init, releases it in onCleared. All behaviour
 * is [ChatPresenter]'s; this class only gives it the entry's scope (03 §14.3: WhileSubscribed(5 s)).
 */
@AssistedInject
public class ChatViewModel(
    @Assisted route: ChatRoute,
    private val worlds: ChatWorlds,
    nav: AppNavigator,
    clock: Clock,
) :
    ViewModel(), ChatActions {
    private val world = worlds.open(route.ref)
    private val presenter =
        ChatPresenter(world, nav, { block -> viewModelScope.launch { block() } }, focusComposer = route.focusComposer)

    public val requests = world.requests

    public val state: StateFlow<ChatUi> = presenter.states(viewModelScope, world.work, SharingStarted.WhileSubscribed(STOP_AFTER_MS))

    /** CHAT-TRANSCRIPT: steps, results, context, retry and stall beside the rows (side tables, never the rows). */
    @OptIn(ExperimentalCoroutinesApi::class)
    public val facts: StateFlow<TurnFactsUi> =
        TurnFactsPresenter(clock)
            .states(
                (world as? TurnFeedSource)?.turnFeed ?: flowOf(TurnFeed.Empty),
                world.requests.flatMapLatest { model -> model?.state?.map { it.card != null } ?: flowOf(false) },
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MS),
            )

    override fun onCleared() {
        worlds.release(world)
    }

    override fun leaveRequested(): Unit = presenter.leaveRequested()

    override fun confirmLeave(): Unit = presenter.confirmLeave()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun retryLoad(): Unit = presenter.retryLoad()

    override fun stop(): Unit = presenter.stop()

    override fun find(query: String?): Unit = presenter.find(query)

    override fun findNext(): Unit = presenter.findNext()

    override fun findPrevious(): Unit = presenter.findPrevious()

    override fun loadOlder(): Unit = presenter.loadOlder()

    override fun openTimeline(): Unit = presenter.openTimeline()

    override fun openMessageMenu(rowKey: String): Unit = presenter.openMessageMenu(rowKey)

    override fun closeMessageMenu(): Unit = presenter.closeMessageMenu()

    override fun messageAction(target: MessageTarget, action: MessageAction): Unit = presenter.messageAction(target, action)

    override fun confirmDelete(): Unit = presenter.confirmDelete()

    override fun clipDone(id: Long): Unit = presenter.clipDone(id)

    override fun arrivalShown(id: Long): Unit = presenter.arrivalShown(id)

    override fun liveEdgeChanged(atLiveEdge: Boolean): Unit = presenter.liveEdgeChanged(atLiveEdge)

    override fun retrySend(localId: Long): Unit = presenter.retrySend(localId)

    override fun dismissStatus(id: String): Unit = presenter.dismissStatus(id)

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
