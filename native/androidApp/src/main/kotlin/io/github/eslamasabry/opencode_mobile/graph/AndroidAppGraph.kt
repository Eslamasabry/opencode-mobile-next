package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.LaunchMode
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.shell.ShellDependencies

/**
 * The final Android graph (plan 03 §13.2). Metro validates it at compile time: a missing binding
 * fails the build.
 */
@DependencyGraph(AppScope::class)
interface AndroidAppGraph : ViewModelGraph {
    // Inputs made before the graph (plan 03 §21.1) are also accessors: OS components read them in
    // later slices.
    val application: Application
    val buildInfo: AppBuildInfo
    val redactor: Redactor
    val launchMode: LaunchMode
    val appScope: AppCoroutineScope
    val previewRuntime: PreviewRuntime
    val composerFiles: AndroidComposerFiles
    val androidSessionImportFiles: AndroidSessionImportFiles
    val connections: ConnectionRegistry
    val shell: ShellDependencies

    @Provides fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities.android()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides buildInfo: AppBuildInfo,
            @Provides
            redactor: Redactor, // built in onCreate step 2, before any secret is read (ADR §9.7)
            @Provides launch: LaunchMode,
        ): AndroidAppGraph
    }
}

/** The only way OS components reach the graph (ADR §9.1; metrox-android needs API 28). */
interface GraphOwner {
    val appGraph: AndroidAppGraph
}

internal val Context.appGraph: AndroidAppGraph
    get() = (applicationContext as GraphOwner).appGraph
