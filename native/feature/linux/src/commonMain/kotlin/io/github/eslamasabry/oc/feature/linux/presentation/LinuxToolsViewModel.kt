package io.github.eslamasabry.oc.feature.linux.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class LinuxToolsPanelUi(
    val tools: LinuxToolsUi = LinuxToolsUi(),
    val confirming: Boolean = false,
    val bytes: Long? = null,
    val measuring: Boolean = false,
)

public interface LinuxToolsActions {
    public fun refresh()

    public fun askRemove()

    public fun confirmRemove()

    public fun dismiss()
}

/** Confirmation is transient; every confirmed operation rechecks native admission in the source. */
public class LinuxToolsModel(
    private val source: LinuxToolsSource,
    private val scope: CoroutineScope,
) : LinuxToolsActions {
    private val ui = MutableStateFlow(LinuxToolsPanelUi(tools = source.state.value))
    public val state: StateFlow<LinuxToolsPanelUi> = ui.asStateFlow()

    init {
        scope.launch { source.state.collect { tools -> ui.update { it.copy(tools = tools) } } }
        refresh()
    }

    override fun refresh() {
        if (ui.value.tools.loading || ui.value.tools.removing) return
        scope.launch { source.refresh() }
    }

    override fun askRemove() {
        val current = ui.value
        if (
            current.measuring ||
                current.confirming ||
                current.tools.loading ||
                current.tools.removing
        )
            return
        if (current.tools.entries.none { it.component.id == "python" && it.canRemove }) return
        ui.update { it.copy(measuring = true, bytes = null) }
        scope.launch {
            val bytes = source.pythonBytes()?.takeIf { it > 0 }
            ui.update { it.copy(measuring = false, confirming = true, bytes = bytes) }
        }
    }

    override fun confirmRemove() {
        if (!ui.value.confirming || ui.value.tools.removing) return
        ui.update { it.copy(confirming = false, bytes = null) }
        scope.launch { source.removePython() }
    }

    override fun dismiss() {
        ui.update { it.copy(confirming = false, bytes = null) }
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxToolsViewModel(source: LinuxToolsSource) : ViewModel(), LinuxToolsActions {
    private val model = LinuxToolsModel(source, viewModelScope)
    public val state: StateFlow<LinuxToolsPanelUi> = model.state

    override fun refresh(): Unit = model.refresh()

    override fun askRemove(): Unit = model.askRemove()

    override fun confirmRemove(): Unit = model.confirmRemove()

    override fun dismiss(): Unit = model.dismiss()
}
