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

/** Settings › About › Open source notices (route AboutRoute.Notices). */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class NoticesViewModel(
    private val source: NoticesSource,
    private val nav: AppNavigator,
) : ViewModel(), NoticesActions {
    private val mutableState = MutableStateFlow(NoticesUi())
    public val state: StateFlow<NoticesUi> = mutableState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        mutableState.value = mutableState.value.copy(phase = NoticesPhase.Loading)
        viewModelScope.launch {
            mutableState.value =
                when (val read = source.load()) {
                    is Outcome.Ok -> mutableState.value.copy(phase = NoticesPhase.Ready, text = AboutNotice.noticesForReaders(read.value))
                    is Outcome.Failed -> mutableState.value.copy(phase = NoticesPhase.Failed, text = "")
                }
        }
    }

    override fun onBack(): Unit = nav.back()

    override fun onRetry(): Unit = load()
}
