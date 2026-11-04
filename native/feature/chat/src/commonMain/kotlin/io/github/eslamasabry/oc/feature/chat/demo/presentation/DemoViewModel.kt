package io.github.eslamasabry.oc.feature.chat.demo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class DemoViewModel(nav: AppNavigator, dispatchers: AppDispatchers) :
    ViewModel(), BasicComposerActions {
    private val world = DemoWorld(dispatchers)
    private val presenter =
        ChatPresenter(world, nav, { block -> viewModelScope.launch { block() } })
    public val actions: ChatActions = presenter
    public val state =
        presenter.states(viewModelScope, world.work, SharingStarted.WhileSubscribed(5_000))
    public val stage = world.stage
    public val composer =
        combine(world.draft, world.stage) { draft, phase ->
                BasicComposerUi(
                    available = phase == DemoStage.Ready,
                    sending = false,
                    steers = false,
                    draft = draft,
                    targetKey = "offline-demo",
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BasicComposerUi(available = false, sending = false, steers = false),
            )

    public fun configure(words: DemoWords): Unit = world.configure(words)

    override fun draftChanged(text: String): Unit = world.draftChanged(text)

    override suspend fun send(text: String): Boolean = world.send(text) is Outcome.Ok

    public fun allow(): Unit {
        viewModelScope.launch { world.decide(true) }
    }

    public fun deny(): Unit {
        viewModelScope.launch { world.decide(false) }
    }

    public fun reset(): Unit {
        viewModelScope.launch { world.reset() }
    }

    override fun onCleared() {
        world.close()
    }
}
