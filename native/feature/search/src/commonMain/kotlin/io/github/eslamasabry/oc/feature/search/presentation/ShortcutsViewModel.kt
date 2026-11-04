package io.github.eslamasabry.oc.feature.search.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public enum class ShortcutHelp {
    Send,
    Newline,
    History,
    Refresh,
    Tabs,
}

public data class ShortcutsUi(
    val shortcuts: ImmutableList<ShortcutHelp> = ShortcutHelp.entries.toImmutableList()
)

public fun interface ShortcutsActions {
    public fun back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ShortcutsViewModel(private val navigator: AppNavigator) :
    ViewModel(), ShortcutsActions {
    public val state: StateFlow<ShortcutsUi> = MutableStateFlow(ShortcutsUi()).asStateFlow()

    override fun back(): Unit = navigator.back()
}
