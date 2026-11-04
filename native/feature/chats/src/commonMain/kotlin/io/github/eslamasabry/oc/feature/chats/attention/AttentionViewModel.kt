package io.github.eslamasabry.oc.feature.chats.attention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class AttentionViewModel(
    private val history: AttentionHistory,
    private val nav: AppNavigator,
) : ViewModel(), AttentionActions {
    public val state: StateFlow<AttentionUi> = history.state

    init {
        refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        viewModelScope.launch { history.refresh() }
    }

    override fun open(ref: ConversationRef): Unit = nav.openChat(ref)

    override fun setMonitored(profile: String, enabled: Boolean) {
        viewModelScope.launch { history.setMonitored(profile, enabled) }
    }

    override fun undo(identity: HistoryIdentity) {
        viewModelScope.launch { history.undo(identity) }
    }

    override fun acknowledge() {
        // Capture the rendered ids before queuing the write: arrivals after this tap remain unread
        // (B-STATE-007).
        val shown = state.value.history.map { it.identity }
        viewModelScope.launch { history.acknowledge(shown) }
    }
}
