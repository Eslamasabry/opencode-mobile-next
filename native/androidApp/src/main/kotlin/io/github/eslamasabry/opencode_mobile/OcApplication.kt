package io.github.eslamasabry.opencode_mobile

import android.app.Application
import android.content.pm.ApplicationInfo
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.LaunchMode
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.security.RedactingWriter
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.opencode_mobile.graph.AndroidAppGraph
import io.github.eslamasabry.opencode_mobile.graph.DiagnosticsLogSink
import io.github.eslamasabry.opencode_mobile.graph.GraphOwner
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
        val redactor = Redactor() // 2. before any secret is read
        AppLog.install(
            RedactingWriter(redactor, listOf(DiagnosticsLogSink)), // P12: warnings/errors feed Report a problem
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
        appGraph.previewRuntime // warm stores, restore saved profile, connect
        (appGraph as PlatformAccessors).platform.start() // P12-PLATFORM: channels, widgets feed, alerts, update check
        appGraph.resumedActivities // P12-VOICE: follow the resumed activity from the start (mic permission)
        AppLog.tagged("App").i { "graph ready ${buildInfo.versionName} (${buildInfo.versionCode})" }
    }
}
