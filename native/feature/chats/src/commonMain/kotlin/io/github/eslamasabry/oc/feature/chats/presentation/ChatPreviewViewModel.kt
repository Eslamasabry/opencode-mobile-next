package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** P2 proof ViewModel for ChatRoute (the page the row grows into). Its Back is a button-back: shrink into the row. */
@AssistedInject
public class ChatPreviewViewModel(@Assisted route: ChatRoute, private val nav: AppNavigator) :
    ViewModel(), ChatPreviewActions {
    public val state: StateFlow<ChatPreviewUi> = MutableStateFlow(ConversationFixture.preview(route.ref)).asStateFlow()

    override fun back(): Unit = nav.back()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatPreviewViewModel
    }
}
