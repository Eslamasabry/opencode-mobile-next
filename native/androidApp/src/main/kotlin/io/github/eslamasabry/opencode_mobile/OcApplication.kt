package io.github.eslamasabry.opencode_mobile

import android.app.ActivityManager
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.Trace
import dev.zacsweers.metro.createGraphFactory
import io.github.eslamasabry.oc.android.platform.NativeCrashStore
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.LaunchMode
import io.github.eslamasabry.oc.core.common.Severity
import io.github.eslamasabry.oc.core.security.RedactingWriter
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.ui.kit.image.KitFileImageDisk
import io.github.eslamasabry.oc.ui.kit.image.KitImages
import io.github.eslamasabry.oc.ui.kit.image.imageKeepFraction
import io.github.eslamasabry.opencode_mobile.graph.AndroidAppGraph
import io.github.eslamasabry.opencode_mobile.graph.AppExitAccessors
import io.github.eslamasabry.opencode_mobile.graph.AppNoticesAccessors
import io.github.eslamasabry.opencode_mobile.graph.ContinuityAccessors
import io.github.eslamasabry.opencode_mobile.graph.AttentionRuntimeAccessors
import io.github.eslamasabry.opencode_mobile.graph.BackgroundAccessors
import io.github.eslamasabry.oc.engine.analytics.Reporting
import io.github.eslamasabry.opencode_mobile.graph.CrashAccessors
import io.github.eslamasabry.opencode_mobile.telemetry.Telemetry
import io.github.eslamasabry.opencode_mobile.graph.DiagnosticsAttentionAccessors
import io.github.eslamasabry.opencode_mobile.graph.DiagnosticsLogSink
import io.github.eslamasabry.opencode_mobile.graph.GraphOwner
import io.github.eslamasabry.opencode_mobile.graph.LinuxAccessors
import io.github.eslamasabry.opencode_mobile.graph.MonitorRuntimeAccessors
import io.github.eslamasabry.opencode_mobile.graph.NativeExitAccessors
import io.github.eslamasabry.opencode_mobile.graph.NativeHealingAccessors
import io.github.eslamasabry.opencode_mobile.graph.PhoneDetailsAccessors
import io.github.eslamasabry.opencode_mobile.graph.PhoneServerScripts
import io.github.eslamasabry.opencode_mobile.graph.PlatformAccessors

/**
 * The ADR §9.7 order, slice-0 form: steps 1 (crash handler), 3 (crash-loop guard), 4 (Flutter
 * backup) and 6–7 (migration runner, prefs warm-up) are P5's and slice 1's; the preview flavour
 * never migrates.
 */
class OcApplication : Application(), GraphOwner {
    private lateinit var buildInfo: AppBuildInfo
    private lateinit var redactor: Redactor
    private lateinit var reporting: Reporting
    override val appGraph: AndroidAppGraph by lazy {
        createGraphFactory<AndroidAppGraph.Factory>()
            .create(this, buildInfo, redactor, LaunchMode.Normal, reporting)
    }
    private var productStarted = false

    override fun onCreate() {
        super.onCreate()
        // The isolated PDF parser in ":local_pdf" has no storage and no UserManager: opening SharedPreferences there
        // throws and kills the process before it can answer, so every PDF showed "could not be displayed". It needs
        // none of the product below, so any process but the main one stops here.
        if (!isMainProcess()) return
        // 1. B-SEC-043, as the Flutter OcApplication: the category-only handler goes in before
        // anything else can crash; it stores nothing until crash capture is turned on.
        // Crash reports go through the vendor-neutral Reporting layer (consent + redaction) before any vendor sees them.
        // B-180/B-190: off in every flavour until the person agrees; the stored answers are adopted after the gate.
        val tOnCreate = SystemClock.elapsedRealtime()
        step("telemetryInit") { reporting = Telemetry.init(this) }
        try {
            NativeCrashStore(filesDir).install { category, frames ->
                reporting.reportCrash(category, frames)
            }
        } catch (_: Throwable) {}
        // Pictures: disk thumbnails live under the cache folder, one per profile (oc.imageCache.<profileId>).
        KitImages.diskFor = KitFileImageDisk.factory(cacheDir)
        redactor = Redactor() // 2. before any secret is read
        AppLog.install(
            RedactingWriter(
                redactor,
                // B-190: the app's own log ring for "Send a problem report".
                appLogSinks(
                    debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                    base = listOf(DiagnosticsLogSink, io.github.eslamasabry.opencode_mobile.telemetry.TelemetryRingSink),
                    logcat = LogcatLogSink(),
                    asked = ::logTagAsked,
                ),
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
        // product, as before.
        PhoneServerScripts.configurePort { BuiltinLinux.get(this).selectedServerPort }
        step("graph") { appGraph }
        startProduct()
        startupLog.i { "Application.onCreate total ${SystemClock.elapsedRealtime() - tOnCreate}ms" }
    }

    /** The starters nothing in the first frame waits for, in their original relative order. */
    private fun startDeferred() {
        Thread(
                {
                    step("telemetryContinuity") {
                        (appGraph as ContinuityAccessors).telemetryContinuity.start() // s23-continuity: Flutter 2.1.5 answers, crash offer
                    }
                    step("monitorRuntime") { (appGraph as MonitorRuntimeAccessors).monitorRuntime.start() }
                    // B-146: send prompts queued for another saved server once it answers.
                    step("queuedServerWatch") {
                        (appGraph as io.github.eslamasabry.opencode_mobile.graph.QueuedServerWatchAccessors).queuedServerWatch.start()
                    }
                    step("nativePhoneDetails") { (appGraph as PhoneDetailsAccessors).nativePhoneDetails.start() }
                    step("nativePhoneBudget") {
                        (appGraph as io.github.eslamasabry.opencode_mobile.graph.PhoneBudgetAccessors)
                            .nativePhoneBudget
                            .start() // restore the saved phone speed budget
                    }
                    step("builtinRuntime") {
                        appGraph.builtinRuntime // P12-FIX-LINUX-VOICE: the built-in Linux follows the activity; phone server comes back
                    }
                    step("phoneOpenCodeAutoUpdate") {
                        (appGraph as io.github.eslamasabry.opencode_mobile.graph.PhoneOpenCodeAutoUpdateAccessors)
                            .phoneOpenCodeAutoUpdate
                            .start() // keep the phone server on the pinned OpenCode (B-LINUX-079)
                    }
                    step("nativePhoneHealing") { (appGraph as NativeHealingAccessors).nativePhoneHealing.start() }
                    step("agentWidgets") {
                        (appGraph as io.github.eslamasabry.opencode_mobile.graph.AgentWidgetAccessors).agentWidgets.start()
                    }
                    step("nativeAppExitRecovery") { (appGraph as NativeExitAccessors).nativeAppExitRecovery.start() }
                    step("linuxStartup") {
                        (appGraph as LinuxAccessors).linuxStartup.start() // P12-FIX: finish an interrupted Reset Linux (B-LINUX-093)
                    }
                    step("firstLaunchHooks") {
                        (appGraph as io.github.eslamasabry.opencode_mobile.graph.FirstLaunchAccessors)
                            .firstLaunchHooks
                            .start() // E00-S07: the first app open after the Flutter -> native update (plan 05 §7.9)
                    }
                },
                "oc-startup",
            )
            .start()
    }

    /** Start-up timings go through the app's one logger (rule K5): steps before it is installed wait in its buffer. */
    private val startupLog = AppLog.tagged("OcStartup")

    /** One startup step, visible in a system trace as `oc.start.<name>` and timed in the log. */
    private inline fun step(name: String, block: () -> Unit) {
        val t0 = SystemClock.elapsedRealtime()
        Trace.beginSection("oc.start.$name")
        try {
            block()
        } finally {
            Trace.endSection()
            val ms = SystemClock.elapsedRealtime() - t0
            startupLog.i { "$name ${ms}ms on ${Thread.currentThread().name}" }
        }
    }

    /** Frees the picture cache as the system asks (audit gap 5): see [imageKeepFraction] for the levels. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        imageKeepFraction(level)?.let(KitImages::trim)
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
        // Gap 3 (startup): the first block is what the first frame and the notification paths need, so it stays here in the
        // ADR order. Everything after it is independent of the first frame and runs, in the same order, on one background
        // thread ("oc-startup"). Each starter only launches its own coroutines or reads its own stores; the ones that need
        // the main thread hop to it through dispatchers.main as before.
        step("crashDiagnostics") { (appGraph as CrashAccessors).crashDiagnostics.start() }
        step("diagnosticsAttention") {
            (appGraph as DiagnosticsAttentionAccessors).diagnosticsAttention.start() // persisted report, crash store, exits
        }
        step("background") { (appGraph as BackgroundAccessors).background.start() }
        step("appExit") { (appGraph as AppExitAccessors).appExit.start() }
        step("appNotices") { (appGraph as AppNoticesAccessors).appNotices.start() }
        step("attentionRuntime") { (appGraph as AttentionRuntimeAccessors).attentionRuntime.start() }
        step("previewRuntime") {
            appGraph.previewRuntime // Warm local stores. MainActivity restores the ordinary connection.
        }
        step("attentionState") {
            (appGraph as io.github.eslamasabry.opencode_mobile.graph.AttentionStateAccessors).attentionState.start()
        }
        step("platform") {
            (appGraph as PlatformAccessors).platform.start() // P12-PLATFORM: channels, widgets feed, alerts, update check
        }
        step("resumedActivities") {
            appGraph.resumedActivities // P12-VOICE: follow the resumed activity from the start (mic permission)
        }
        startDeferred()
        AppLog.tagged("App").i { "graph ready ${buildInfo.versionName} (${buildInfo.versionCode})" }
    }
}
