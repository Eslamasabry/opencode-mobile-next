package io.github.eslamasabry.oc.feature.linux.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.manager.LinuxManager
import io.github.eslamasabry.oc.feature.linux.manager.LinuxRetryWait
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LinuxRoute
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import kotlin.time.Clock
import kotlinx.coroutines.flow.StateFlow

/*
 * Thin Metro ViewModels (plan 03 §13.7): each hands viewModelScope to its plain state holder, whose rules are tested
 * on virtual time. LinuxHost, LinuxLifecycle, LinuxResetMarker, PortPreviewOpener, LinuxJobNotices and Pacer are bound
 * by androidApp's graph/LinuxWiring.kt.
 */

/**
 * The manager, the one job watch and the reset flow: one each for the app (Linux is one per app).
 */
@BindingContainer
@ContributesTo(AppScope::class)
public object LinuxBindings {
    @Provides
    @SingleIn(AppScope::class)
    public fun linuxManager(
        host: LinuxHost,
        wait: LinuxRetryWait,
        details: io.github.eslamasabry.oc.feature.linux.host.LinuxLogDetails,
    ): LinuxManager = LinuxManager(host, wait, details)

    @Provides
    @SingleIn(AppScope::class)
    public fun linuxJobWatch(manager: LinuxManager, notices: LinuxJobNotices): LinuxJobWatch =
        LinuxJobWatch(manager, notices)

    @Provides
    @SingleIn(AppScope::class)
    public fun linuxResetFlow(
        lifecycle: LinuxLifecycle,
        marker: LinuxResetMarker,
        clock: Clock,
    ): LinuxResetFlow = LinuxResetFlow(lifecycle, marker, clock)
}

/** LinuxPage → the routes of :ui:routes. */
@Inject
@ContributesBinding(AppScope::class)
public class RoutedLinuxNavigator(private val nav: AppNavigator) : LinuxNavigator {
    override fun open(page: LinuxPage) {
        nav.open(
            when (page) {
                LinuxPage.Home -> LinuxRoute.Home
                LinuxPage.Ports -> LinuxRoute.Ports
                LinuxPage.Storage -> LinuxRoute.Storage
                LinuxPage.Packages -> LinuxRoute.Packages
                LinuxPage.Backups -> LinuxRoute.Backups
                LinuxPage.KeptServices -> LinuxRoute.KeptServices
                LinuxPage.Running -> PhoneRoute.Running
                LinuxPage.ThisPhone -> PhoneRoute.ThisPhone
            }
        )
    }

    override fun back(): Unit = nav.back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxHomeViewModel(
    manager: LinuxManager,
    watch: LinuxJobWatch,
    lifecycle: LinuxLifecycle,
    reset: LinuxResetFlow,
    storage: io.github.eslamasabry.oc.engine.runtime.storage.LinuxLocalStorage,
    setup: LinuxSetupSource,
    idle: LinuxIdleSource,
    lifetimes: LinuxLifetimesSource,
    vitals: LinuxVitalsSource,
    nav: LinuxNavigator,
    pacer: Pacer,
) : ViewModel(), LinuxHomeActions {
    private val model =
        LinuxHomeModel(
            manager,
            watch,
            lifecycle,
            reset,
            nav,
            viewModelScope,
            pacer,
            storage,
            setup,
            idle,
            lifetimes,
            vitals,
        )
    public val state: StateFlow<LinuxHomeUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun open(page: LinuxPage): Unit = model.open(page)

    override fun askRepair(): Unit = model.askRepair()

    override fun askReset(): Unit = model.askReset()

    override fun setIdleEnabled(enabled: Boolean): Unit = model.setIdleEnabled(enabled)

    override fun askIdleMinutes(): Unit = model.askIdleMinutes()

    override fun finishIdleMinutes(value: String?): Unit = model.finishIdleMinutes(value)

    override fun resumeIdle(): Unit = model.resumeIdle()

    override fun startSetup(): Unit = model.startSetup()

    override fun addPython(): Unit = model.addPython()

    override fun continueSetup(): Unit = model.continueSetup()

    override fun askStopSetup(): Unit = model.askStopSetup()

    override fun refreshLifetimes(): Unit = model.refreshLifetimes()

    override fun askCreateProject(): Unit = model.askCreateProject()

    override fun finishCreateProject(path: String?): Unit = model.finishCreateProject(path)

    override fun browse(path: String): Unit = model.browse(path)

    override fun closeFolders(): Unit = model.closeFolders()

    override fun measureRemoval(): Unit = model.measureRemoval()

    override fun askRemove(projects: Boolean): Unit = model.askRemove(projects)

    override fun confirm(): Unit = model.confirm()

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()

    override fun askStopJob(): Unit = model.askStopJob()

    override fun dismissJob(): Unit = model.dismissJob()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxPortsViewModel(
    manager: LinuxManager,
    opener: PortPreviewOpener,
    nav: LinuxNavigator,
    addressCopy: LinuxAddressCopy,
) : ViewModel(), LinuxPortsActions {
    private val model = LinuxPortsModel(manager, opener, nav, viewModelScope, addressCopy)
    public val state: StateFlow<LinuxPortsUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun retry(): Unit = model.retry()

    override fun selectPort(port: Int): Unit = model.selectPort(port)

    override fun copyAddress(port: Int): Unit = model.copyAddress(port)

    override fun askStopOwner(port: Int): Unit = model.askStopOwner(port)

    override fun confirmStopOwner(): Unit = model.confirmStopOwner()

    override fun preview(port: Int): Unit = model.preview(port)

    override fun readProgramLog(pid: Int): Unit = model.readProgramLog(pid)

    override fun askStopProgram(pid: Int): Unit = model.askStopProgram(pid)

    override fun confirmStopProgram(): Unit = model.confirmStopProgram()

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxStorageViewModel(manager: LinuxManager, nav: LinuxNavigator) :
    ViewModel(), LinuxStorageActions {
    private val model = LinuxStorageModel(manager, nav, viewModelScope)
    public val state: StateFlow<LinuxStorageUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun retry(): Unit = model.retry()

    override fun askCleanup(
        id: io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId
    ): Unit = model.askCleanup(id)

    override fun confirm(): Unit = model.confirm()

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxPackagesViewModel(
    manager: LinuxManager,
    watch: LinuxJobWatch,
    nav: LinuxNavigator,
    pacer: Pacer,
) : ViewModel(), LinuxPackagesActions {
    private val model = LinuxPackagesModel(manager, watch, nav, viewModelScope, pacer)
    public val state: StateFlow<LinuxPackagesUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun retry(): Unit = model.retry()

    override fun search(text: String): Unit = model.search(text)

    override fun checkInstall(typed: String): Unit = model.checkInstall(typed)

    override fun askRemove(name: String): Unit = model.askRemove(name)

    override fun askUpdate(): Unit = model.askUpdate()

    override fun confirm(): Unit = model.confirm()

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()

    override fun askStopJob(): Unit = model.askStopJob()

    override fun dismissJob(): Unit = model.dismissJob()
}
