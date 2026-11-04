package io.github.eslamasabry.oc.feature.phone.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class SharedFolderState(
    val permissionGranted: Boolean?,
    val allFolders: Boolean,
    val canAllowAllFolders: Boolean,
    val restartRequired: Boolean,
)

public interface SharedFolderAccess {
    public suspend fun read(): Outcome<SharedFolderState>

    public suspend fun allowAndroidAccess(): Outcome<Boolean>

    public suspend fun allowAllFolders(value: Boolean): Outcome<SharedFolderState>
}

@Inject
@ContributesBinding(AppScope::class)
public class UnavailableSharedFolderAccess : SharedFolderAccess {
    override suspend fun read(): Outcome<SharedFolderState> = Outcome.Failed(Failure.Device())

    override suspend fun allowAndroidAccess(): Outcome<Boolean> = Outcome.Failed(Failure.Device())

    override suspend fun allowAllFolders(value: Boolean): Outcome<SharedFolderState> =
        Outcome.Failed(Failure.Device())
}

public data class SharedFoldersUi(
    val state: SharedFolderState? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val declined: Boolean = false,
)

public interface SharedFoldersActions {
    public fun refresh()

    public fun allowAndroidAccess()

    public fun allowAllFolders(value: Boolean)

    public fun openThisPhone()

    public fun back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class SharedFoldersViewModel(
    private val source: SharedFolderAccess,
    private val nav: AppNavigator,
) : ViewModel(), SharedFoldersActions {
    private val ui = MutableStateFlow(SharedFoldersUi())
    public val state: StateFlow<SharedFoldersUi> = ui.asStateFlow()

    init {
        refresh()
    }

    override fun refresh() {
        if (ui.value.busy) return
        ui.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch { finish(source.read()) }
    }

    private fun finish(result: Outcome<SharedFolderState>) {
        when (result) {
            is Outcome.Ok ->
                ui.update { it.copy(state = result.value, busy = false, failed = false) }
            is Outcome.Failed -> ui.update { it.copy(busy = false, failed = true) }
        }
    }

    override fun allowAndroidAccess() {
        if (ui.value.busy) return
        ui.update { it.copy(busy = true, failed = false, declined = false) }
        viewModelScope.launch {
            when (val result = source.allowAndroidAccess()) {
                is Outcome.Failed -> ui.update { it.copy(busy = false, failed = true) }
                is Outcome.Ok -> {
                    ui.update { it.copy(declined = !result.value) }
                    finish(source.read())
                }
            }
        }
    }

    override fun allowAllFolders(value: Boolean) {
        if (ui.value.busy) return
        ui.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch { finish(source.allowAllFolders(value)) }
    }

    override fun openThisPhone() {
        nav.open(PhoneRoute.ThisPhone)
    }

    override fun back() {
        nav.back()
    }
}
