package io.github.eslamasabry.oc.feature.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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
    private val collector: CollectorControls,
) : ViewModel(), PlansActions {
    private val refreshing = MutableStateFlow(false)
    private val collectors = MutableStateFlow<List<CollectorTarget>>(emptyList())
    private val confirmation = MutableStateFlow<CollectorTarget?>(null)
    private val collectorFailed = MutableStateFlow(false)

    private val edits = MutableStateFlow(PlansUi())

    public val state: StateFlow<PlansUi> =
        combine(source.snapshot, refreshing, collectors, confirmation, collectorFailed) {
                snapshot,
                busy,
                targets,
                pending,
                failed ->
                plansUi(snapshot, busy, clock.now().toEpochMilliseconds())
                    .copy(
                        collectors = targets.toImmutableList(),
                        confirmCollector = pending,
                        collectorFailed = failed,
                    )
            }
            // LINUX-2: the phone's own collector setup.
            .combine(collector.state) { ui, c -> ui.copy(collector = c) }
            // STATE: the alert threshold and reset-notice editor.
            .combine(edits) { ui, edit ->
                ui.copy(
                    defaultThreshold = source.defaultThreshold.toInt(),
                    resetNotice = source.resetNotice,
                    editing = edit.editing,
                    editKey = edit.editKey,
                    thresholdInput = edit.thresholdInput,
                    saving = edit.saving,
                    failed = edit.failed,
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

    override fun editThreshold(key: String?) {
        val threshold =
            key?.let { wanted ->
                source.snapshot.value.subscriptions.firstOrNull { it.key == wanted }?.alertThreshold
            } ?: source.defaultThreshold
        edits.value =
            PlansUi(editing = true, editKey = key, thresholdInput = threshold.toInt().toString())
    }

    override fun thresholdChanged(value: String) {
        if (!edits.value.saving)
            edits.value = edits.value.copy(thresholdInput = value, failed = false)
    }

    override fun dismissEditor() {
        if (!edits.value.saving) edits.value = PlansUi()
    }

    override fun saveThreshold() {
        val value = edits.value.thresholdInput.toDoubleOrNull()
        if (value == null || !value.isFinite() || value <= 0 || value > 100) {
            edits.value = edits.value.copy(failed = true)
            return
        }
        save(value)
    }

    override fun useDefaultThreshold() {
        if (edits.value.editKey != null) save(null)
    }

    private fun save(value: Double?) {
        if (edits.value.saving) return
        val key = edits.value.editKey
        edits.value = edits.value.copy(saving = true)
        viewModelScope.launch {
            val saved = source.setThreshold(key, value)
            edits.value = if (saved) PlansUi() else edits.value.copy(saving = false, failed = true)
        }
    }

    override fun toggleResetNotice() {
        if (edits.value.saving) return
        edits.value = edits.value.copy(saving = true)
        viewModelScope.launch {
            val saved = source.setResetNotice(null, !source.resetNotice)
            edits.value = edits.value.copy(saving = false, failed = !saved)
        }
    }

    override fun back(): Unit = nav.back()

    override fun chooseCollector(target: CollectorTarget) {
        if (refreshing.value) return
        if (target.enabled) changeCollector(target, false) else confirmation.value = target
    }

    override fun cancelCollector() {
        confirmation.value = null
    }

    override fun confirmCollector() {
        val target = confirmation.value ?: return
        confirmation.value = null
        changeCollector(target, true)
    }

    private fun changeCollector(target: CollectorTarget, enabled: Boolean) {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                collectorFailed.value = !source.setCollector(target, enabled)
                collectors.value = source.collectors()
                source.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    override fun setUpCollector() {
        viewModelScope.launch {
            collector.setUp()
            refresh()
        }
    }

    override fun turnOffCollector() {
        viewModelScope.launch {
            collector.turnOff()
            refresh()
        }
    }

    override fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                source.refresh()
                collectors.value = source.collectors()
            } finally {
                refreshing.value = false
            }
        }
    }
}
