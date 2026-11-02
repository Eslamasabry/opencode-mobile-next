package io.github.eslamasabry.oc.feature.chat.header.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.feature.chat.header.world.ChatHeaderPorts
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One per chat entry, beside the ChatViewModel: shares the conversation's world (ref-counted by [ChatWorlds]) and owns
 * the header's port. All behaviour is [ChatHeaderPresenter]'s.
 */
@AssistedInject
public class ChatHeaderViewModel(
    @Assisted route: ChatRoute,
    private val worlds: ChatWorlds,
    ports: ChatHeaderPorts,
    nav: AppNavigator,
) : ViewModel(), ChatHeaderActions {
    private val world = worlds.open(route.ref)
    private val presenter =
        ChatHeaderPresenter(world, ports.open(route.ref), nav, { block -> viewModelScope.launch { block() } })

    public val state: StateFlow<ChatHeaderUi> = presenter.states(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS))

    override fun onCleared() {
        presenter.close()
        worlds.release(world)
    }

    override fun openMenu(): Unit = presenter.openMenu()

    override fun closeMenu(): Unit = presenter.closeMenu()

    override fun choose(action: HeaderAction): Unit = presenter.choose(action)

    override fun copyTranscript(words: TranscriptWords, mask: (String) -> String): Unit =
        presenter.copyTranscript(words, mask)

    override fun renameRequested(): Unit = presenter.renameRequested()

    override fun renameDone(title: String?): Unit = presenter.renameDone(title)

    override fun closeSheet(): Unit = presenter.closeSheet()

    override fun continueWith(choice: ContinueChoice): Unit = presenter.continueWith(choice)

    override fun retryContinueWith(): Unit = presenter.retryContinueWith()

    override fun confirm(): Unit = presenter.confirm()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun dismissFailure(): Unit = presenter.dismissFailure()

    override fun clipDone(id: Long): Unit = presenter.clipDone(id)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatHeaderViewModel
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
