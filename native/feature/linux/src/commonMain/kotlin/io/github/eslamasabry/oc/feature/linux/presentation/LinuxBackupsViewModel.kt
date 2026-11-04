package io.github.eslamasabry.oc.feature.linux.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupKind
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupState
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackups
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

public enum class LinuxBackupChoice {
    Setup,
    SetupAndProjects,
    ProjectsOnly,
}

public data class LinuxBackupsUi(
    val choice: LinuxBackupChoice = LinuxBackupChoice.SetupAndProjects,
    val backup: LinuxBackupState = LinuxBackupState(),
    val askingStop: Boolean = false,
)

public interface LinuxBackupsActions {
    public fun choose(choice: LinuxBackupChoice)

    public fun go()

    public fun chooseBackup()

    public fun askStop()

    public fun confirm()

    public fun dismiss()

    public fun back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxBackupsViewModel(
    private val backups: LinuxBackups,
    private val nav: AppNavigator,
) : ViewModel(), LinuxBackupsActions {
    private val local = MutableStateFlow(LinuxBackupsUi())
    public val state: StateFlow<LinuxBackupsUi> =
        combine(local, backups.state) { ui, backup -> ui.copy(backup = backup) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                LinuxBackupsUi(backup = backups.state.value),
            )

    override fun choose(choice: LinuxBackupChoice) {
        if (!backups.state.value.busy) local.value = local.value.copy(choice = choice)
    }

    override fun go() {
        if (backups.state.value.busy) return
        when (local.value.choice) {
            LinuxBackupChoice.Setup -> backups.backup(LinuxBackupKind.Setup)
            LinuxBackupChoice.SetupAndProjects -> backups.backup(LinuxBackupKind.SetupAndProjects)
            LinuxBackupChoice.ProjectsOnly -> nav.open(PhoneRoute.ExportProjects)
        }
    }

    override fun chooseBackup(): Unit = backups.choose()

    override fun askStop() {
        local.value = local.value.copy(askingStop = true)
    }

    override fun confirm() {
        if (local.value.askingStop) {
            local.value = local.value.copy(askingStop = false)
            backups.stop()
        } else backups.restore()
    }

    override fun dismiss() {
        if (local.value.askingStop) local.value = local.value.copy(askingStop = false)
        else backups.reset()
    }

    override fun back(): Unit = nav.back()
}
