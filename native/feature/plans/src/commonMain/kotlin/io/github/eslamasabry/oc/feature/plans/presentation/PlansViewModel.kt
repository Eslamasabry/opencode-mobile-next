package io.github.eslamasabry.oc.feature.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * `PlansRoute`: reads every source when opened and on Refresh; one refresh at a time (a second tap
 * joins it).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PlansViewModel(
    private val source: PlansSource,
    private val nav: AppNavigator,
    private val clock: Clock,
) : ViewModel(), PlansActions {
    private val refreshing = MutableStateFlow(false)
    private val readerBusy = MutableStateFlow(false)
    private val readerFailed = MutableStateFlow(false)

    public val state: StateFlow<PlansUi> =
        combine(source.snapshot, refreshing, source.computerReaders, readerBusy, readerFailed) {
                snapshot,
                busy,
                readers,
                checking,
                failed ->
                plansUi(snapshot, busy, clock.now().toEpochMilliseconds())
                    .copy(
                        computerReaders = readers.toImmutableList(),
                        readerBusy = checking,
                        readerFailed = failed,
                    )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                plansUi(source.snapshot.value, false, clock.now().toEpochMilliseconds()),
            )

    init {
        refresh()
    }

    override fun back(): Unit = nav.back()

    override fun setComputerReaderEnabled(id: String, enabled: Boolean) {
        readerAction { source.setComputerReaderEnabled(id, enabled) }
    }

    override fun readComputerNow(id: String) {
        readerAction { source.readComputerNow(id) }
    }

    private fun readerAction(command: suspend () -> Outcome<Unit>) {
        if (readerBusy.value) return
        readerBusy.value = true
        readerFailed.value = false
        viewModelScope.launch {
            try {
                readerFailed.value = command() is Outcome.Failed
            } finally {
                readerBusy.value = false
            }
        }
    }

    override fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                source.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }
}
