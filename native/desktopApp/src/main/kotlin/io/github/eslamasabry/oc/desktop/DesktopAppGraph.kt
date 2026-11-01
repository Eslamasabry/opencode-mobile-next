package io.github.eslamasabry.oc.desktop

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.shell.ShellDependencies

/** Same contributions as Android minus :android:* (plan 03 §13.3); Unavailable* bindings join with their seams. */
@DependencyGraph(AppScope::class)
interface DesktopAppGraph : ViewModelGraph {
    val buildInfo: AppBuildInfo
    val redactor: Redactor
    val connections: ConnectionRegistry
    val shell: ShellDependencies

    @Provides fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities.desktop()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides buildInfo: AppBuildInfo, @Provides redactor: Redactor): DesktopAppGraph
    }
}

/** Desktop has no BuildConfig; the version moves to version.properties with the desktop phase. */
val desktopBuildInfo: AppBuildInfo = AppBuildInfo("3.0.0-dev", 10000, Channel.Desktop, debuggable = true)
