package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.view.WindowManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.LaunchMode
import io.github.eslamasabry.oc.core.domain.capabilities.DeviceClass
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
    val kitChatEnvironment: io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
    val launchMode: LaunchMode
    val appScope: AppCoroutineScope
    val previewRuntime: PreviewRuntime
    val composerFiles: AndroidComposerFiles
    val androidSessionImportFiles: AndroidSessionImportFiles
    val connections: ConnectionRegistry
    val shell: ShellDependencies

    @SingleIn(AppScope::class)
    @Provides
    fun platformCapabilities(application: Application): PlatformCapabilities {
        val deviceClass = detectAndroidDeviceClass(application)
        PlatformCapabilities.recordDeviceClass(deviceClass)
        return PlatformCapabilities.android(deviceClass = deviceClass)
    }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides buildInfo: AppBuildInfo,
            @Provides
            redactor: Redactor, // built in onCreate step 2, before any secret is read (ADR §9.7)
            @Provides launch: LaunchMode,
            @Provides reporting: io.github.eslamasabry.oc.engine.analytics.Reporting,
        ): AndroidAppGraph
    }
}

/**
 * Reads the device's form factor once at start (B-CAP-303..305): non-foldable displays whose smallest width is at
 * least 600 dp are [DeviceClass.Tablet]; phones and foldables (hinge hardware) are [DeviceClass.Phone]. Because the
 * binding is `@SingleIn(AppScope::class)` and reads display-level metrics, window resize or split-screen never flips it.
 */
internal fun detectAndroidDeviceClass(context: Context): DeviceClass =
    PlatformCapabilities.resolveDeviceClass(
        smallestScreenWidthDp = androidSmallestScreenWidthDp(context),
        hasFold = hasAndroidFoldHardware(context),
    )

internal fun androidSmallestScreenWidthDp(context: Context): Int {
    val configSw = context.resources.configuration.smallestScreenWidthDp
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val density = context.resources.displayMetrics.density
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = wm?.maximumWindowMetrics?.bounds
        if (density > 0f && bounds != null && bounds.width() > 0 && bounds.height() > 0) {
            return (minOf(bounds.width(), bounds.height()) / density).toInt()
        }
    }
    return configSw
}

internal fun hasAndroidFoldHardware(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    if (context.packageManager?.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE) == true) {
        return true
    }
    val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    return sensors?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
}

/** The only way OS components reach the graph (ADR §9.1; metrox-android needs API 28). */
interface GraphOwner {
    val appGraph: AndroidAppGraph
}

internal val Context.appGraph: AndroidAppGraph
    get() = (applicationContext as GraphOwner).appGraph
