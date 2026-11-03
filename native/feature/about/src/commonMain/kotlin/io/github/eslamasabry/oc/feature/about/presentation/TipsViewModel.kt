package io.github.eslamasabry.oc.feature.about.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Help › Tips (route AboutRoute.Tips; Flutter `TipsScreen`): every registered tip and mini tutorial with Show me, and
 * Show tips again. Show me never counts a showing: a tip replays on its own row, a tutorial runs on its own screen.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class TipsViewModel(
    private val store: TipsStore,
    private val tours: TourLauncher,
    private val nav: AppNavigator,
) : ViewModel(), TipsActions {
    private val entries = TipsRegistry.entries
    private val mutableState = MutableStateFlow(tipsUi(entries, replaying = null, resetDone = false, resetting = false))
    public val state: StateFlow<TipsUi> = mutableState.asStateFlow()

    override fun onBack(): Unit = nav.back()

    override fun onShowMe(id: String) {
        val entry = entries.firstOrNull { it.id == id } ?: return
        if (entry.kind == TipKind.Tour && tours.start(id)) return
        mutableState.value = mutableState.value.copy(replaying = id)
    }

    override fun onReplayDismissed(id: String) {
        if (mutableState.value.replaying == id) mutableState.value = mutableState.value.copy(replaying = null)
    }

    override fun onShowTipsAgain() {
        if (mutableState.value.resetting) return
        mutableState.value = mutableState.value.copy(resetting = true)
        viewModelScope.launch {
            val done = store.showTipsAgain() is Outcome.Ok
            mutableState.value = mutableState.value.copy(resetting = false, resetDone = mutableState.value.resetDone || done)
        }
    }
}
