package io.github.eslamasabry.opencode_mobile

import android.app.ActivityManager
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.android.platform.NativeCrashStore
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.LaunchMode
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.security.RedactingWriter
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.opencode_mobile.graph.AndroidAppGraph
import io.github.eslamasabry.opencode_mobile.graph.AppExitAccessors
import io.github.eslamasabry.opencode_mobile.graph.AppNoticesAccessors
import io.github.eslamasabry.opencode_mobile.graph.AttentionRuntimeAccessors
import io.github.eslamasabry.opencode_mobile.graph.BackgroundAccessors
import io.github.eslamasabry.opencode_mobile.graph.CrashAccessors
import io.github.eslamasabry.opencode_mobile.graph.DiagnosticsAttentionAccessors
import io.github.eslamasabry.opencode_mobile.graph.DiagnosticsLogSink
import io.github.eslamasabry.opencode_mobile.graph.GraphOwner
import io.github.eslamasabry.opencode_mobile.graph.LinuxAccessors
import io.github.eslamasabry.opencode_mobile.graph.MonitorRuntimeAccessors
import io.github.eslamasabry.opencode_mobile.graph.NativeExitAccessors
import io.github.eslamasabry.opencode_mobile.graph.NativeHealingAccessors
import io.github.eslamasabry.opencode_mobile.graph.PlatformAccessors

/**
 * The ADR §9.7 order, slice-0 form: steps 1 (crash handler), 3 (crash-loop guard), 4 (Flutter
 * backup) and 6–7 (migration runner, prefs warm-up) are P5's and slice 1's; the preview flavour
 * never migrates.
 */
class OcApplication : Application(), GraphOwner {
    private lateinit var buildInfo: AppBuildInfo
    private lateinit var redactor: Redactor
    override val appGraph: AndroidAppGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>()
            .create(this, buildInfo, redactor, LaunchMode.Normal)
    }
    private var productStarted = false

    override fun onCreate() {
        super.onCreate()
        // 1. B-SEC-043, as the Flutter OcApplication: the category-only handler goes in before
        // anything else can crash; it stores nothing until crash capture is turned on.
        try {
            NativeCrashStore(filesDir).install()
        } catch (_: Throwable) {}
        redactor = Redactor() // 2. before any secret is read
        AppLog.install(
            RedactingWriter(
                redactor,
                listOf(DiagnosticsLogSink),
            ), // P12: warnings/errors feed Report a problem
            Severity.Info,
        ) // ring + logcat sinks: slice 1
        buildInfo =
            AppBuildInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                channel = Channel.Preview,
                debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
        // Every component of the main process (activities, alert buttons, services, workers,
        // tiles, widgets) may be the first thing in a cold process and relies on the started
        // product, as before. The isolated PDF parser in ":local_pdf" never warms profiles,
        // connects, or starts Linux.
        if (isMainProcess()) startProduct()
    }

    private fun isMainProcess(): Boolean {
        val name =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
            else
                (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)
                    ?.runningAppProcesses
                    ?.firstOrNull { it.pid == Process.myPid() }
                    ?.processName
        // Unknown name: behave as the main process (the pre-split default).
        return name == null || name == packageName
    }

    @Synchronized
    fun startProduct() {
        if (productStarted) return
        productStarted = true
        (appGraph as CrashAccessors).crashDiagnostics.start()
        (appGraph as DiagnosticsAttentionAccessors)
            .diagnosticsAttention
            .start() // persisted report, crash store, exits
        (appGraph as BackgroundAccessors).background.start()
        (appGraph as AppExitAccessors).appExit.start()
        (appGraph as AppNoticesAccessors).appNotices.start()
        (appGraph as AttentionRuntimeAccessors).attentionRuntime.start()
        (appGraph as MonitorRuntimeAccessors).monitorRuntime.start()
        appGraph.previewRuntime // Warm local stores. MainActivity restores the ordinary connection.
        (appGraph as io.github.eslamasabry.opencode_mobile.graph.AttentionStateAccessors)
            .attentionState
            .start()
        (appGraph as PlatformAccessors)
            .platform
            .start() // P12-PLATFORM: channels, widgets feed, alerts, update check
        (appGraph as io.github.eslamasabry.opencode_mobile.graph.PhoneBudgetAccessors)
            .nativePhoneBudget
            .start() // restore the saved phone speed budget
        appGraph
            .resumedActivities // P12-VOICE: follow the resumed activity from the start (mic
                               // permission)
        appGraph
            .builtinRuntime // P12-FIX-LINUX-VOICE: the built-in Linux follows the activity; phone
                            // server comes back
        (appGraph as NativeHealingAccessors).nativePhoneHealing.start()
        (appGraph as io.github.eslamasabry.opencode_mobile.graph.AgentWidgetAccessors)
            .agentWidgets
            .start()
        (appGraph as NativeExitAccessors).nativeAppExitRecovery.start()
        (appGraph as LinuxAccessors)
            .linuxStartup
            .start() // P12-FIX: finish an interrupted Reset Linux (B-LINUX-093)
        AppLog.tagged("App").i { "graph ready ${buildInfo.versionName} (${buildInfo.versionCode})" }
    }
}
