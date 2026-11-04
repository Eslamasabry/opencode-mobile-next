package io.github.eslamasabry.opencode_mobile

import android.app.Application
import android.content.pm.ApplicationInfo
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
import io.github.eslamasabry.opencode_mobile.graph.CrashAccessors
import io.github.eslamasabry.opencode_mobile.graph.AppExitAccessors
import io.github.eslamasabry.opencode_mobile.graph.AppNoticesAccessors
import io.github.eslamasabry.opencode_mobile.graph.AttentionRuntimeAccessors
import io.github.eslamasabry.opencode_mobile.graph.BackgroundAccessors
import io.github.eslamasabry.opencode_mobile.graph.DiagnosticsAttentionAccessors
import io.github.eslamasabry.opencode_mobile.graph.DiagnosticsLogSink
import io.github.eslamasabry.opencode_mobile.graph.GraphOwner
import io.github.eslamasabry.opencode_mobile.graph.LinuxAccessors
import io.github.eslamasabry.opencode_mobile.graph.MonitorAccessors
import io.github.eslamasabry.opencode_mobile.graph.MonitorRuntimeAccessors
import io.github.eslamasabry.opencode_mobile.graph.NativeExitAccessors
import io.github.eslamasabry.opencode_mobile.graph.NativeHealingAccessors
import io.github.eslamasabry.opencode_mobile.graph.PhoneDetailsAccessors
import io.github.eslamasabry.opencode_mobile.graph.PlansGraph
import io.github.eslamasabry.opencode_mobile.graph.PlatformAccessors

/**
 * The ADR §9.7 order, slice-0 form: steps 1 (crash handler), 3 (crash-loop guard), 4 (Flutter
 * backup) and 6–7 (migration runner, prefs warm-up) are P5's and slice 1's; the preview flavour
 * never migrates.
 */
class OcApplication : Application(), GraphOwner {
    override lateinit var appGraph: AndroidAppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // 1. B-SEC-043, as the Flutter OcApplication: the category-only handler goes in before
        // anything else can crash; it stores nothing until crash capture is turned on.
        try {
            NativeCrashStore(filesDir).install()
        } catch (_: Throwable) {}
        val redactor = Redactor() // 2. before any secret is read
        AppLog.install(
            RedactingWriter(
                redactor,
                listOf(DiagnosticsLogSink),
            ), // P12: warnings/errors feed Report a problem
            Severity.Info,
        ) // ring + logcat sinks: slice 1
        val buildInfo =
            AppBuildInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                channel = Channel.Preview,
                debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
        appGraph =
            createGraphFactory<AndroidAppGraph.Factory>()
                .create(this, buildInfo, redactor, LaunchMode.Normal)
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
        (appGraph as PhoneDetailsAccessors).nativePhoneDetails.start()
        // The consent-gated attention monitor behind Servers, Settings › Notifications and Inbox.
        (appGraph as MonitorAccessors).profileMonitoring.start()
        (appGraph as PlansGraph).plansSource
        (appGraph as io.github.eslamasabry.opencode_mobile.graph.AttentionStateAccessors)
            .attentionState
            .start()
        (appGraph as PlatformAccessors)
            .platform
            .start() // P12-PLATFORM: channels, widgets feed, alerts, update check
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
