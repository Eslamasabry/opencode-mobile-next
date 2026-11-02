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
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

/*
 * Thin Metro ViewModels (plan 03 §13.7): each hands viewModelScope to its plain state holder, whose rules are tested
 * on virtual time. LinuxHost, LinuxLifecycle, LinuxResetMarker, PortPreviewOpener, LinuxJobNotices and Pacer are bound
 * by androidApp's graph/LinuxWiring.kt.
 */

/** The manager, the one job watch and the reset flow: one each for the app (Linux is one per app). */
@BindingContainer
@ContributesTo(AppScope::class)
public object LinuxBindings {
    @Provides
    @SingleIn(AppScope::class)
    public fun linuxManager(host: LinuxHost, wait: LinuxRetryWait): LinuxManager = LinuxManager(host, wait)

    @Provides
    @SingleIn(AppScope::class)
    public fun linuxJobWatch(manager: LinuxManager, notices: LinuxJobNotices): LinuxJobWatch = LinuxJobWatch(manager, notices)

    @Provides
    @SingleIn(AppScope::class)
    public fun linuxResetFlow(lifecycle: LinuxLifecycle, marker: LinuxResetMarker, clock: Clock): LinuxResetFlow =
        LinuxResetFlow(lifecycle, marker, clock)
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
                LinuxPage.Running -> PhoneRoute.Running
                LinuxPage.ThisPhone -> PhoneRoute.ThisPhone
            },
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
    nav: LinuxNavigator,
    pacer: Pacer,
) : ViewModel(), LinuxHomeActions {
    private val model = LinuxHomeModel(manager, watch, lifecycle, reset, nav, viewModelScope, pacer)
    public val state: StateFlow<LinuxHomeUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun open(page: LinuxPage): Unit = model.open(page)

    override fun askRepair(): Unit = model.askRepair()

    override fun askReset(): Unit = model.askReset()

    override fun confirm(): Unit = model.confirm()

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()

    override fun askStopJob(): Unit = model.askStopJob()

    override fun dismissJob(): Unit = model.dismissJob()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxPortsViewModel(manager: LinuxManager, opener: PortPreviewOpener, nav: LinuxNavigator) :
    ViewModel(), LinuxPortsActions {
    private val model = LinuxPortsModel(manager, opener, nav, viewModelScope)
    public val state: StateFlow<LinuxPortsUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun retry(): Unit = model.retry()

    override fun preview(port: Int): Unit = model.preview(port)

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxStorageViewModel(manager: LinuxManager, nav: LinuxNavigator) : ViewModel(), LinuxStorageActions {
    private val model = LinuxStorageModel(manager, nav, viewModelScope)
    public val state: StateFlow<LinuxStorageUi> = model.state

    override fun shown(visible: Boolean): Unit = model.shown(visible)

    override fun retry(): Unit = model.retry()

    override fun askCleanup(id: io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId): Unit = model.askCleanup(id)

    override fun confirm(): Unit = model.confirm()

    override fun dismiss(): Unit = model.dismiss()

    override fun back(): Unit = model.back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class LinuxPackagesViewModel(manager: LinuxManager, watch: LinuxJobWatch, nav: LinuxNavigator, pacer: Pacer) :
    ViewModel(), LinuxPackagesActions {
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
