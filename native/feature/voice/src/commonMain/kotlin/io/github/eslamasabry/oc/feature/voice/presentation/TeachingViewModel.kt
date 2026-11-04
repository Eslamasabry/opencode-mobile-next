package io.github.eslamasabry.oc.feature.voice.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingRegistry
import io.github.eslamasabry.oc.engine.settings.teaching.TipController
import io.github.eslamasabry.oc.engine.settings.teaching.TipDefinition
import io.github.eslamasabry.oc.engine.settings.teaching.TipId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class TeachingUi(
    val ready: Boolean = false,
    val failed: Boolean = false,
    val entries: List<TipDefinition> = TeachingRegistry.defaults.tips,
    val replay: TipDefinition? = null,
    val step: Int = 0,
    val resetFailed: Boolean = false,
)

public interface TeachingActions {
    public fun back()

    public fun replay(id: TipId)

    public fun next()

    public fun dismiss()

    public fun reset()

    public fun retry()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class TeachingViewModel(private val hub: VoiceTeachingHub, private val nav: AppNavigator) :
    ViewModel(), TeachingActions {
    private val mutable = MutableStateFlow(TeachingUi())
    public val state: StateFlow<TeachingUi> = mutable.asStateFlow()
    private var controller: TipController? = null

    init {
        retry()
    }

    override fun retry() {
        viewModelScope.launch {
            mutable.update { it.copy(failed = false) }
            val tips = hub.controller()
            if (tips == null) {
                mutable.update { it.copy(failed = true) }
                return@launch
            }
            controller = tips
            mutable.update { it.copy(ready = true) }
            tips.active.collect { active ->
                val entry = active?.id?.let(TeachingRegistry.defaults::entry)
                mutable.update {
                    it.copy(replay = entry, step = if (it.replay?.id == entry?.id) it.step else 0)
                }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun replay(id: TipId) {
        controller?.replay(id)
    }

    override fun next() {
        val current = state.value
        val entry = current.replay ?: return
        if (current.step + 1 < entry.steps.size) mutable.update { it.copy(step = it.step + 1) }
        else {
            controller?.markUsed(entry.id)
        }
    }

    override fun dismiss() {
        state.value.replay?.let { controller?.dismiss(it.id) }
    }

    override fun reset() {
        val tips = controller ?: return
        viewModelScope.launch {
            val result = tips.reset()
            mutable.update { it.copy(resetFailed = result is Outcome.Failed) }
        }
    }

    override fun onCleared() {
        dismiss()
    }
}
