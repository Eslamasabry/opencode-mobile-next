package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/**
 * Why the app's previous process ended and what ran in it (`oc/lifecycle`,
 * both halves: this file and lib/platform/app_exit.dart).
 *
 * Android records every process death (ApplicationExitInfo, API 30+); some
 * phones (Nubia/RedMagic, Xiaomi, Oppo, Vivo, Huawei, Samsung) force-stop an
 * app with a foreground service when it is swiped from Recents or by their
 * battery manager. A force stop takes the built-in Ubuntu's services with it
 * and runs none of our code, so [recordServices] keeps the set of running
 * services on disk on every change: whatever is still written at the next
 * start was running when the process died.
 */
object AppLifecycle {
    private const val TAG = "OcLifecycle"
    private const val CHANNEL = "oc/lifecycle"
    private const val PREFS = "oc_lifecycle"
    private const val KEY_SERVICES = "running_services"
    private const val KEY_SEEN_EXIT = "seen_exit_timestamp"

    // Subreason is not public API, but ApplicationExitInfo.toString prints
    // it ("subreason=21 (FORCE STOP)"); unknown when the text changes.
    private val SUBREASON = Regex("subreason=(\\d+)")

    private var previousServices: List<String>? = null
    private var lastExit: Map<String, Any?>? = null
    private var lastExitRead = false

    fun register(activity: Activity, messenger: BinaryMessenger, replies: NativeChannelReplies, requestBatteryExemption: () -> Unit) {
        // How hot the phone is (oc/thermal), for the AI Team's thermal guard.
        ThermalMonitor.register(activity, messenger)
        MethodChannel(messenger, CHANNEL).setMethodCallHandler { call, rawResult ->
            val result = replies.wrap(rawResult)
            result.guarded("lifecycle") {
                when (call.method) {
                    "launchReport" -> result.success(
                        mapOf(
                            "exit" to lastExit(activity),
                            "previousServices" to previousServices(activity),
                            "lastCrash" to NativeCrashStore(activity.filesDir).read(),
                        ),
                    )
                    "keepAliveInfo" -> result.success(keepAliveInfo(activity))
                    "openKeepAliveSetting" -> result.success(
                        openKeepAliveSetting(activity, call.argument<String>("setting") ?: "", requestBatteryExemption),
                    )
                    else -> result.notImplemented()
                }
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The services that ran when the previous process ended, read once per
     * process and before this process records anything of its own.
     */
    @Synchronized
    fun previousServices(context: Context): List<String> {
        previousServices?.let { return it }
        val saved = (prefs(context).getString(KEY_SERVICES, "") ?: "")
            .split(',')
            .filter { it.isNotEmpty() }
        previousServices = saved
        return saved
    }

    /** BuiltinLinux calls this whenever its set of running services changes. */
    @Synchronized
    fun recordServices(context: Context, names: List<String>) {
        previousServices(context)
        // commit, not apply: a force stop may come a moment later and runs
        // nothing of ours.
        prefs(context).edit().putString(KEY_SERVICES, names.joinToString(",")).commit()
    }

    /**
     * The newest exit of the app's main process not reported before, or
     * null. Read once per process: every later call in the same process
     * answers the same, so a second engine or a hot restart sees it too.
     */
    @Synchronized
    fun lastExit(context: Context): Map<String, Any?>? {
        if (lastExitRead) return lastExit
        lastExitRead = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val records = try {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, 16)
        } catch (error: Exception) {
            Log.w(TAG, "exit reasons unavailable", error)
            return null
        }
        val newest = records
            .filter { it.processName == null || it.processName == context.packageName }
            .maxByOrNull { it.timestamp } ?: return null
        val prefs = prefs(context)
        if (newest.timestamp <= prefs.getLong(KEY_SEEN_EXIT, 0L)) return null
        prefs.edit().putLong(KEY_SEEN_EXIT, newest.timestamp).commit()
        val text = newest.toString()
        lastExit = mapOf(
            "reason" to newest.reason,
            "subReason" to (SUBREASON.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: -1),
            "status" to newest.status,
            "importance" to newest.importance,
            "timestamp" to newest.timestamp,
            "description" to "", // OS crash descriptions can contain the raw exception message.
        )
        Log.i(TAG, "previous process ended: reason=${newest.reason} status=${newest.status}")
        return lastExit
    }

    private fun keepAliveInfo(context: Context): Map<String, Any?> {
        val power = context.getSystemService(PowerManager::class.java)
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "sdk" to Build.VERSION.SDK_INT,
            "batteryOptimizationIgnored" to
                (power?.isIgnoringBatteryOptimizations(context.packageName) == true),
        )
    }

    /**
     * Opens one keep-alive setting. Vendor screens move between ROM
     * versions, so each is tried in turn and a missing one is skipped; the
     * app's own info page is the last resort. False when nothing opened.
     */
    private fun openKeepAliveSetting(
        activity: Activity,
        setting: String,
        requestBatteryExemption: () -> Unit,
    ): Boolean {
        val appDetails = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${activity.packageName}"),
        )
        val candidates: List<Intent> = when (setting) {
            "battery" -> {
                val power = activity.getSystemService(PowerManager::class.java)
                if (power?.isIgnoringBatteryOptimizations(activity.packageName) == true) {
                    listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails)
                } else {
                    try {
                        requestBatteryExemption()
                        return true
                    } catch (error: Exception) {
                        Log.w(TAG, "battery exemption request failed", error)
                    }
                    listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appDetails)
                }
            }
            "autostart" -> autostartComponents(Build.MANUFACTURER, Build.BRAND).map {
                Intent().setComponent(it)
            } + appDetails
            "appDetails" -> listOf(appDetails)
            else -> emptyList()
        }
        for (intent in candidates) {
            try {
                activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (error: ActivityNotFoundException) {
                // The next candidate.
            } catch (error: SecurityException) {
                // A vendor screen that is not exported on this build.
            } catch (error: Exception) {
                Log.w(TAG, "could not open $intent", error)
            }
        }
        return false
    }

    /** Known auto-start / background-launch screens, newest ROMs first. */
    fun autostartComponents(manufacturer: String, brand: String): List<ComponentName> {
        val maker = "${manufacturer.lowercase()} ${brand.lowercase()}"
        fun c(pkg: String, cls: String) = ComponentName(pkg, cls)
        return when {
            maker.contains("nubia") || maker.contains("redmagic") || maker.contains("zte") -> listOf(
                c("cn.nubia.security2", "cn.nubia.security.appmanage.selfstart.ui.SelfStartActivity"),
                c("cn.nubia.security", "cn.nubia.security.appmanage.selfstart.ui.SelfStartActivity"),
                c("com.zte.heartyservice", "com.zte.heartyservice.autorun.AppAutoRunManager"),
            )
            maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> listOf(
                c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            maker.contains("oneplus") -> listOf(
                c("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            )
            maker.contains("oppo") || maker.contains("realme") -> listOf(
                c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            maker.contains("vivo") || maker.contains("iqoo") -> listOf(
                c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            maker.contains("huawei") || maker.contains("honor") -> listOf(
                c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                c("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            maker.contains("samsung") -> listOf(
                c("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                c("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            )
            else -> emptyList()
        }
    }
}
