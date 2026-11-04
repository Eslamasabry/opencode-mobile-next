package io.github.eslamasabry.oc.feature.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.integrations.usage.UsageWidgetWindows
import kotlinx.collections.immutable.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public enum class PlanManagePage {
    None,
    Alerts,
    Widget,
    Reader,
    Add,
}

public data class PlansManagementUi(
    val page: PlanManagePage = PlanManagePage.None,
    val preferences: PlanAlertPreferences = PlanAlertPreferences(),
    val readers: ImmutableMap<String, PlanReaderStatus> = persistentMapOf(),
    val readerId: String? = null,
    val readerName: String = "",
    val busy: Boolean = false,
    val failure: Failure? = null,
)

public interface PlansManagementActions {
    public fun openAlerts()

    public fun openWidget()

    public fun openReader(id: String, name: String)

    public fun openAdd()

    public fun dismiss()

    public fun setAlerts(on: Boolean)

    public fun setDefaultThreshold(level: Int)

    public fun setDefaultReset(on: Boolean)

    public fun setThreshold(key: String, level: Int?)

    public fun setReset(key: String, on: Boolean)

    public fun setQuiet(on: Boolean)

    public fun setQuietStart(minutes: Int)

    public fun setQuietEnd(minutes: Int)

    public fun setWifi(on: Boolean)

    public fun setWidgetHidden(key: String, hidden: Boolean)

    public fun setWidgetSetup(on: Boolean)

    public fun setWidgetWindows(windows: UsageWidgetWindows)

    public fun readerStart()

    public fun readerRead()

    public fun readerOff()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PlansManagementViewModel(
    private val prefs: PlansPreferences,
    private val readers: PlanReaders,
    private val plans: PlansSource,
) : ViewModel(), PlansManagementActions {
    private val local = MutableStateFlow(PlansManagementUi())
    public val state: StateFlow<PlansManagementUi> =
        combine(local, prefs.preferences, readers.states) { s, p, r ->
                s.copy(preferences = p, readers = r)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                local.value.copy(
                    preferences = prefs.preferences.value,
                    readers = readers.states.value,
                ),
            )

    override fun openAlerts() {
        local.update { it.copy(page = PlanManagePage.Alerts, failure = null) }
    }

    override fun openWidget() {
        local.update { it.copy(page = PlanManagePage.Widget, failure = null) }
    }

    override fun openReader(id: String, name: String) {
        local.update {
            it.copy(page = PlanManagePage.Reader, readerId = id, readerName = name, failure = null)
        }
    }

    override fun openAdd() {
        local.update { it.copy(page = PlanManagePage.Add, failure = null) }
    }

    override fun dismiss() {
        if (!local.value.busy)
            local.update { it.copy(page = PlanManagePage.None, readerId = null, failure = null) }
    }

    private fun change(work: suspend () -> Outcome<Unit>) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                val result = work()
                local.update { it.copy(failure = (result as? Outcome.Failed)?.failure) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    override fun setAlerts(on: Boolean) = change { prefs.setAlerts(on) }

    override fun setDefaultThreshold(level: Int) = change { prefs.setDefaultThreshold(level) }

    override fun setDefaultReset(on: Boolean) = change { prefs.setDefaultReset(on) }

    override fun setThreshold(key: String, level: Int?) = change { prefs.setThreshold(key, level) }

    override fun setReset(key: String, on: Boolean) = change { prefs.setReset(key, on) }

    override fun setQuiet(on: Boolean) = change {
        if (on) prefs.setQuiet(22 * 60, 8 * 60) else prefs.setQuiet(null, null)
    }

    override fun setQuietStart(minutes: Int) = change {
        prefs.setQuiet(minutes, prefs.preferences.value.quietEnd ?: 8 * 60)
    }

    override fun setQuietEnd(minutes: Int) = change {
        prefs.setQuiet(prefs.preferences.value.quietStart ?: 22 * 60, minutes)
    }

    override fun setWifi(on: Boolean) = change { prefs.setWifiOnly(on) }

    override fun setWidgetHidden(key: String, hidden: Boolean) = change {
        val s = prefs.preferences.value.widget
        prefs.setWidget(s.copy(hiddenKeys = if (hidden) s.hiddenKeys + key else s.hiddenKeys - key))
    }

    override fun setWidgetSetup(on: Boolean) = change {
        prefs.setWidget(prefs.preferences.value.widget.copy(showSetupRows = on))
    }

    override fun setWidgetWindows(windows: UsageWidgetWindows) = change {
        prefs.setWidget(prefs.preferences.value.widget.copy(windows = windows))
    }

    override fun readerStart() {
        val id = local.value.readerId ?: return
        change { readers.setUp(id).also { if (it is Outcome.Ok) plans.refresh() } }
    }

    override fun readerRead() {
        val id = local.value.readerId ?: return
        change { readers.read(id).also { if (it is Outcome.Ok) plans.refresh() } }
    }

    override fun readerOff() {
        val id = local.value.readerId ?: return
        change { readers.turnOff(id).also { if (it is Outcome.Ok) plans.refresh() } }
    }
}
