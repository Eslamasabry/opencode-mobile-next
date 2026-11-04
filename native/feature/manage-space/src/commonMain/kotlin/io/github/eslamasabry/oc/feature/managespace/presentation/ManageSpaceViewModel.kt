package io.github.eslamasabry.oc.feature.managespace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.engine.lifecycle.ManageSpaceEngine
import io.github.eslamasabry.oc.engine.lifecycle.ManageSpaceState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public interface ManageSpaceActions {
    public fun back()

    public fun refresh()

    public fun includePrivate(value: Boolean)

    public fun export()

    public fun stopExport()

    public fun clearCache()

    public fun deleteAll()

    public fun dismiss()
}

public class ManageSpaceViewModel(
    private val engine: ManageSpaceEngine,
    private val close: () -> Unit,
) : ViewModel(), ManageSpaceActions {
    public val state: StateFlow<ManageSpaceState> = engine.state

    init {
        refresh()
    }

    override fun back() {
        engine.cancelExport()
        close()
    }

    override fun refresh() {
        viewModelScope.launch { engine.refresh() }
    }

    override fun includePrivate(value: Boolean): Unit = engine.includePrivate(value)

    override fun export() {
        viewModelScope.launch { engine.export() }
    }

    override fun stopExport(): Unit = engine.cancelExport()

    override fun clearCache() {
        viewModelScope.launch { engine.clearCache() }
    }

    override fun deleteAll() {
        viewModelScope.launch { engine.deleteAll() }
    }

    override fun dismiss(): Unit = engine.dismiss()

    override fun onCleared() {
        engine.cancelExport()
    }
}
