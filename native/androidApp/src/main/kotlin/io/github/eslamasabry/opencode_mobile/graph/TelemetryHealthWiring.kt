package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.ViewTreeObserver
import android.view.Window
import android.webkit.WebView
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.engine.analytics.health.HealthDeviceContext
import io.github.eslamasabry.oc.engine.analytics.health.HealthFlow
import io.github.eslamasabry.oc.engine.analytics.health.HealthExit
import io.github.eslamasabry.oc.engine.analytics.health.HealthExitMarks
import io.github.eslamasabry.oc.engine.analytics.health.HealthPlatform
import io.github.eslamasabry.oc.engine.analytics.health.HealthTelemetry
import io.github.eslamasabry.oc.engine.analytics.product.ProductEvent
import io.github.eslamasabry.oc.engine.analytics.product.ProductLifecycle
import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import io.github.eslamasabry.oc.engine.analytics.product.TelemetryAnswers
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitHistory
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.gateway.PaseoGateway
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.RouteSaving
import io.github.eslamasabry.opencode_mobile.telemetry.Telemetry
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * B-190: the app-level half of the health telemetry (Flutter `TelemetryStartup`, `AnalyticsLifecycle`,
 * `AnalyticsNavigatorObserver`, `HealthChannel.kt`), started once the stored answers are adopted:
 * - `app_open` cold and warm, `session_length`, `update_installed`, `app.*` breadcrumbs;
 * - `screen_view` and `nav.open:<word>` for each page, named by kind only;
 * - `app_start` cold (process start to the first frame, plus the `app_cold_start` trace) and warm;
 * - `jank` from the window's frame metrics, measured only while `usage` is on;
 * - `memory_pressure`, the device context user properties, the custom keys and the last process end.
 * Everything goes through [Telemetry.service], which drops it unless its consent is on.
 */
@SingleIn(AppScope::class)
@Inject
class AppTelemetryHealth(
    private val application: Application,
    private val buildInfo: AppBuildInfo,
    private val exits: ExitHistory,
    private val stores: PreviewStores,
    private val clock: Clock,
    // Lazy: read only once the stored answers are adopted, so building this early changes no start-up order.
    private val connections: Lazy<ServerConnections>,
    private val registry: Lazy<ConnectionRegistry>,
    private val navigator: Lazy<AppNavigator>,
    appScope: AppCoroutineScope,
) {
    private val scope = CoroutineScope(appScope.coroutineContext)
    private val telemetry get() = Telemetry.service
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var health: HealthTelemetry? = null
    private val lifecycle by lazy { ProductLifecycle(telemetry, clock) }

    /** Device context and process ends, also read by the problem report the person asks for. */
    val platform: HealthPlatform = AndroidHealthPlatform(application, buildInfo, exits) { coldStartMs }

    /** Process start to the first frame, measured once whatever the switches say (two clock reads). */
    @Volatile private var coldStartMs: Long? = null
    private var resumedSince: Long? = null
    private var startedActivities = 0

    fun start(marks: HealthExitMarks) {
        if (started) return
        started = true
        val health =
            HealthTelemetry(
                telemetry = telemetry,
                platform = platform,
                marks = marks,
                scope = scope,
                clock = clock,
                refreshRate = ::refreshRate,
            )
        this.health = health
        health.start()
        // app_open cold and update_installed (`oc.telemetry.lastVersion`, Flutter's key and value shape).
        scope.launch {
            val answers = TelemetryAnswers(stores.prefs, stores.lanes.app)
            val version = "${buildInfo.versionName}+${buildInfo.versionCode}"
            lifecycle.coldStart(version, answers.lastVersion())?.let { answers.setLastVersion(it) }
        }
        followScreens(health)
        followConnections(health)
        // agent_start and its trace, from the agent host's create call (the protocol module knows no telemetry).
        PaseoGateway.agentStartObserver = { provider, failure, elapsedMs ->
            HealthFlow().agentStart(provider, ok = failure == null, failKind = agentFailKind(failure), elapsedMs = elapsedMs)
        }
        // The jank meter runs only while `usage` is on: attach frame metrics to the resumed window then, detach after.
        scope.launch {
            combine(telemetry.usage, resumed.asFlow()) { usage, activity -> if (usage) activity else null }
                .distinctUntilChanged()
                .collect { activity -> withContext(Dispatchers.Main) { attachFrames(activity) } }
        }
    }

    // ---- pages ----

    private fun followScreens(health: HealthTelemetry) {
        scope.launch {
            navigator.value.top
                .filterNotNull()
                .distinctUntilChanged()
                .map { screenOf(it) }
                .collect { screen ->
                    telemetry.breadcrumb("nav.open:${screen.wire}")
                    health.jank.screen(screen)
                    telemetry.track(ProductEvent.ScreenView, mapOf("screen" to screen))
                }
        }
    }

    // ---- connection facts: the custom keys and the server_kind user property ----

    private fun followConnections(health: HealthTelemetry) {
        scope.launch {
            combine(connections.value.servers, registry.value.live) { servers, live ->
                    val kinds = live.values.map { if (it.profile.managedOnPhone) "phone" else "computer" }.toSet()
                    Triple(servers.size, kinds, live.isNotEmpty())
                }
                .distinctUntilChanged()
                .collect { (saved, kinds, anyLive) ->
                    val phone = "phone" in kinds
                    health.updateConnection(
                        serverKind = if (phone) "phone" else if (anyLive) "computer" else "none",
                        // The phone's own server answers only while it is up; native reads no finer engine state.
                        engineState = if (phone) "ready" else "stopped",
                        connections = saved,
                        userServerKind =
                            when {
                                kinds.size > 1 -> "both"
                                phone -> "phone"
                                anyLive -> "computer"
                                else -> null
                            },
                    )
                }
        }
    }

    // ---- activities: first frame, warm start, background ----

    private val resumed = ResumedActivity()

    private val activities =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1 && coldStartMs != null) {
                    // Back from the background: a warm start, measured to its first frame.
                    val since = SystemClock.uptimeMillis()
                    onNextFrame(activity) { health?.warmStart(SystemClock.uptimeMillis() - since) }
                    lifecycle.onForeground()
                }
            }

            override fun onActivityResumed(activity: Activity) {
                resumed.value = activity
                if (coldStartMs == null && resumedSince == null) {
                    resumedSince = SystemClock.uptimeMillis()
                    onNextFrame(activity) {
                        coldStartMs = (SystemClock.uptimeMillis() - Process.getStartUptimeMillis()).coerceAtLeast(0)
                        health?.firstFrame()
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (resumed.value === activity) resumed.value = null
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) lifecycle.onBackground(health?.jank)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (resumed.value === activity) resumed.value = null
            }
        }

    private fun onNextFrame(activity: Activity, action: () -> Unit) {
        val view = activity.window?.decorView ?: return
        val observer = view.viewTreeObserver
        observer.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(this)
                    main.post(action)
                    return true
                }
            }
        )
    }

    // ---- frames ----

    private var framesOn: Pair<Window, Window.OnFrameMetricsAvailableListener>? = null

    private fun attachFrames(activity: Activity?) {
        framesOn?.let { (window, listener) -> runCatching { window.removeOnFrameMetricsAvailableListener(listener) } }
        framesOn = null
        val window = activity?.window ?: return
        val budgetNs = (1_000_000_000.0 / refreshRate()).toLong()
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                health?.jank?.onFrames(total = 1, slow = if (total > budgetNs) 1 else 0)
            }
        runCatching { window.addOnFrameMetricsAvailableListener(listener, main) }.onSuccess {
            framesOn = window to listener
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshRate(): Double =
        runCatching {
                val display =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) application.display
                    else (application.getSystemService(android.view.WindowManager::class.java)).defaultDisplay
                display?.refreshRate?.toDouble()
            }
            .getOrNull()
            ?.takeIf { it > 0 } ?: 60.0

    // ---- memory ----

    private val memory =
        object : ComponentCallbacks2 {
            @Suppress("DEPRECATION")
            override fun onTrimMemory(level: Int) {
                val word =
                    when (level) {
                        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "moderate"
                        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "low"
                        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "critical"
                        else -> return
                    }
                health?.memoryPressure(word)
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }

    // Declared last: it uses the callbacks above.
    init {
        // Built in Application.onCreate (startProduct), before any activity: the first resume and frame are seen even
        // though [start] waits for the stored answers. Everything sent before then is dropped by consent.
        application.registerActivityLifecycleCallbacks(activities)
        application.registerComponentCallbacks(memory)
    }

    /** The device context for a problem report the person asked for, and the process ends. */
    val healthTelemetry: HealthTelemetry?
        get() = health

    companion object {
        /** The contract's `agent_start.fail_kind` for an agent host failure. */
        fun agentFailKind(failure: PaseoFailureKind?): String =
            when (failure) {
                null -> "other"
                PaseoFailureKind.Authentication -> "needs_sign_in"
                PaseoFailureKind.Unavailable -> "not_installed"
                PaseoFailureKind.DeliveryUnknown -> "timeout"
                else -> "other"
            }

        /**
         * The page kind for a route, from its serial name (`io.github...ui.routes.ChatsRoute.Home` becomes
         * `ChatsRoute.Home`): stable under R8 and never an argument of the route.
         */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        fun screenOf(route: AppRoute): ProductScreen {
            val name =
                runCatching { RouteSaving.module.getPolymorphic(NavKey::class, route)?.descriptor?.serialName }
                    .getOrNull() ?: return ProductScreen.Other
            return ProductScreen.of(name.split('.').dropWhile { it.firstOrNull()?.isLowerCase() == true }.joinToString("."))
        }
    }
}

/** The resumed activity, as a flow. */
private class ResumedActivity {
    private val state = kotlinx.coroutines.flow.MutableStateFlow<Activity?>(null)
    var value: Activity?
        get() = state.value
        set(v) {
            state.value = v
        }

    fun asFlow(): kotlinx.coroutines.flow.StateFlow<Activity?> = state
}

/** The Android half of [HealthPlatform] (Flutter `HealthChannel.kt`): numbers and fixed words only. */
internal class AndroidHealthPlatform(
    private val app: Application,
    private val buildInfo: AppBuildInfo,
    private val exitHistory: ExitHistory,
    private val coldStart: () -> Long?,
) : HealthPlatform {
    override val appVersion: String
        get() = buildInfo.versionName.substringBefore('-').substringBefore('+')

    override val declaredBuildKind: String? = null

    override val appBuild: String
        get() = buildInfo.versionCode.toString()

    override val osRelease: String
        get() = Build.VERSION.RELEASE.orEmpty()

    override fun coldStartMs(): Long? = coldStart()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun deviceContext(): HealthDeviceContext? =
        withContext(Dispatchers.IO) {
            try {
                val memory = ActivityManager.MemoryInfo()
                app.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memory)
                HealthDeviceContext(
                    ramBytes = memory.totalMem,
                    freeBytes = runCatching { StatFs(app.filesDir.path).availableBytes }.getOrDefault(-1L),
                    abi = abiWord(Build.SUPPORTED_ABIS.firstOrNull()),
                    webviewMajor =
                        runCatching {
                                WebView.getCurrentWebViewPackage()?.versionName?.substringBefore('.')?.toIntOrNull()
                            }
                            .getOrNull(),
                    notificationsAllowed =
                        runCatching { app.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() }
                            .getOrNull(),
                    fromStore = installer() in STORES,
                )
            } catch (_: Throwable) {
                null
            }
        }

    @Suppress("DEPRECATION")
    private fun installer(): String? =
        runCatching {
                val pm: PackageManager = app.packageManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(app.packageName).installingPackageName
                } else {
                    pm.getInstallerPackageName(app.packageName)
                }
            }
            .getOrNull()

    override suspend fun exits(limit: Int): List<HealthExit> {
        val history = exitHistory.history(limit)
        if (!history.supported) return emptyList()
        return history.entries.map { HealthExit(it.reason, it.at.toEpochMilliseconds()) }.sortedByDescending { it.atMs }
    }

    companion object {
        private val STORES = setOf("com.android.vending") // F: HealthChannel.kt installedFromStore

        fun abiWord(abi: String?): String? =
            when (abi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a", "armeabi" -> "arm32"
                "x86_64" -> "x86_64"
                else -> null
            }
    }
}
