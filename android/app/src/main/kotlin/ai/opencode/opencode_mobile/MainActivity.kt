package ai.opencode.opencode_mobile

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : FlutterActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var permissionResult: MethodChannel.Result? = null
    private var microphonePermissionResult: MethodChannel.Result? = null
    private var backgroundPermissionResult: MethodChannel.Result? = null
    private var pendingCodingAlertOpen: Map<String, String>? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        captureCodingAlertOpen(intent)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getCapabilities" -> result.success(capabilities())
                    "requestRunCommandPermission" -> requestRunCommandPermission(result)
                    "openTermux" -> result.success(openTermux())
                    "openAppSettings" -> {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                        )
                        result.success(true)
                    }
                    "runInTermux" -> runInTermux(call, result)
                    else -> result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, VOICE_CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getDeviceInfo" -> result.success(voiceDeviceInfo())
                    "requestMicrophonePermission" -> requestMicrophonePermission(result)
                    "openAppSettings" -> {
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                        )
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
        val background = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            BACKGROUND_CHANNEL_NAME
        )
        // Notification-action broadcasts reach Dart through this channel even
        // while the Activity is backgrounded; see CodingActionReceiver.
        backgroundChannel = background
        background
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getStatus" -> result.success(backgroundStatus())
                    "enable" -> enableBackgroundConnection(result)
                    "disable" -> {
                        BackgroundConnectionService.stop(this)
                        result.success(backgroundStatus(enabled = false))
                    }
                    "requestBatteryOptimizationExemption" -> {
                        requestBatteryOptimizationExemption()
                        result.success(backgroundStatus())
                    }
                    "showCodingAlert" -> {
                        val kind = call.argument<String>("kind").orEmpty()
                        val sessionID = call.argument<String>("sessionID").orEmpty()
                        val key = call.argument<String>("key").orEmpty()
                        val quickReply = call.argument<Boolean>("quickReply") ?: false
                        val requestID = call.argument<String>("requestID").orEmpty()
                        result.success(
                            mapOf(
                                "shown" to BackgroundConnectionService.showCodingAlert(
                                    this,
                                    kind = kind,
                                    sessionID = sessionID,
                                    key = key,
                                    quickReply = quickReply,
                                    requestID = requestID
                                )
                            )
                        )
                    }
                    "dismissCodingAlert" -> {
                        val key = call.argument<String>("key").orEmpty()
                        result.success(
                            mapOf(
                                "dismissed" to BackgroundConnectionService.dismissCodingAlert(
                                    this,
                                    key
                                )
                            )
                        )
                    }
                    "consumeCodingAlertOpen" -> {
                        val pending = pendingCodingAlertOpen
                        pendingCodingAlertOpen = null
                        result.success(pending ?: emptyMap<String, String>())
                    }
                    "refreshHomeWidget" -> {
                        SessionsWidgetProvider.refreshAll(this)
                        result.success(mapOf("refreshed" to true))
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        if (backgroundChannel != null) backgroundChannel = null
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureCodingAlertOpen(intent)
    }

    private fun captureCodingAlertOpen(intent: Intent?) {
        if (intent == null) return
        val kind = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_KIND
        ).orEmpty()
        val sessionID = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID
        ).orEmpty()
        if (kind.isNotBlank() && sessionID.isNotBlank()) {
            pendingCodingAlertOpen = mapOf(
                "kind" to kind,
                "sessionID" to sessionID
            )
        }
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_KIND)
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RUN_COMMAND_PERMISSION_REQUEST -> {
                val result = permissionResult ?: return
                permissionResult = null
                result.success(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            }
            MICROPHONE_PERMISSION_REQUEST -> {
                val result = microphonePermissionResult ?: return
                microphonePermissionResult = null
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                val status = if (granted) {
                    "granted"
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    "permanentlyDenied"
                } else {
                    "denied"
                }
                result.success(status)
            }
            BACKGROUND_NOTIFICATION_PERMISSION_REQUEST -> {
                val result = backgroundPermissionResult ?: return
                backgroundPermissionResult = null
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    startBackgroundConnection(result)
                } else {
                    result.error(
                        "notification_denied",
                        "Notification access is required so Android can show the live connection.",
                        null
                    )
                }
            }
        }
    }

    private fun enableBackgroundConnection(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (backgroundPermissionResult != null) {
                result.error("permission_in_progress", "A notification permission request is open.", null)
                return
            }
            backgroundPermissionResult = result
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                BACKGROUND_NOTIFICATION_PERMISSION_REQUEST
            )
            return
        }
        startBackgroundConnection(result)
    }

    private fun startBackgroundConnection(result: MethodChannel.Result) {
        try {
            BackgroundConnectionService.start(this)
            result.success(backgroundStatus(enabled = true))
        } catch (error: Exception) {
            result.error(
                "foreground_service_failed",
                error.message ?: "Android could not start the live connection.",
                null
            )
        }
    }

    private fun backgroundStatus(enabled: Boolean = BackgroundConnectionService.active): Map<String, Any> {
        val notifications = getSystemService(NotificationManager::class.java)
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            notifications.areNotificationsEnabled()
        val power = getSystemService(PowerManager::class.java)
        return mapOf(
            "enabled" to enabled,
            "active" to BackgroundConnectionService.active,
            "notificationGranted" to notificationGranted,
            "batteryOptimizationIgnored" to power.isIgnoringBatteryOptimizations(packageName)
        )
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun voiceDeviceInfo(): Map<String, Any> {
        val storage = StatFs(filesDir.absolutePath)
        val activityManager = getSystemService(ActivityManager::class.java)
        return mapOf(
            "availableStorageBytes" to storage.availableBytes,
            "memoryClassMb" to activityManager.memoryClass,
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
            "hasMicrophone" to packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        )
    }

    private fun requestMicrophonePermission(result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            result.success("granted")
            return
        }
        val permissionPreferences = getSharedPreferences("voice_permissions", MODE_PRIVATE)
        if (permissionPreferences.getBoolean("microphone_requested", false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        ) {
            result.success("permanentlyDenied")
            return
        }
        if (microphonePermissionResult != null) {
            result.error("permission_in_progress", "A microphone permission request is already open.", null)
            return
        }
        microphonePermissionResult = result
        permissionPreferences.edit().putBoolean("microphone_requested", true).apply()
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), MICROPHONE_PERMISSION_REQUEST)
    }

    private fun capabilities(): Map<String, Any?> {
        val packageInfo = try {
            packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        return mapOf(
            "installed" to (packageInfo != null),
            "version" to packageInfo?.versionName,
            "serviceAvailable" to isRunCommandServiceAvailable(),
            "protocolSupported" to supportsRunCommandProtocol(packageInfo?.versionName),
            "permissionGranted" to hasRunCommandPermission()
        )
    }

    private fun requestRunCommandPermission(result: MethodChannel.Result) {
        if (!isPackageInstalled(TERMUX_PACKAGE)) {
            result.error("termux_missing", "Termux is not installed.", null)
            return
        }
        if (hasRunCommandPermission()) {
            result.success(true)
            return
        }
        if (permissionResult != null) {
            result.error("permission_in_progress", "A permission request is already open.", null)
            return
        }
        permissionResult = result
        requestPermissions(arrayOf(RUN_COMMAND_PERMISSION), RUN_COMMAND_PERMISSION_REQUEST)
    }

    private fun runInTermux(call: MethodCall, result: MethodChannel.Result) {
        val script = call.argument<String>("script").orEmpty()
        if (script.isBlank()) {
            result.error("invalid_script", "The Termux command is empty.", null)
            return
        }
        if (!hasRunCommandPermission()) {
            result.error(
                "permission_denied",
                "OpenCode does not have Termux's RUN_COMMAND permission.",
                null
            )
            return
        }
        if (!isRunCommandServiceAvailable()) {
            result.error(
                "service_unavailable",
                "This Termux build does not expose RunCommandService.",
                null
            )
            return
        }

        val executionId = nextExecutionId.getAndIncrement()
        val timeoutMs = (call.argument<Number>("timeoutMs")?.toLong() ?: 30_000L)
            .coerceIn(1_000L, 120_000L)
        val timeout = Runnable {
            if (TermuxCommandRegistry.remove(executionId)) {
                result.error(
                    "command_timeout",
                    "Termux did not return a command result within ${timeoutMs / 1000} seconds.",
                    null
                )
            }
        }

        TermuxCommandRegistry.register(executionId) { bundle ->
            handler.removeCallbacks(timeout)
            handler.post {
                if (bundle == null) {
                    result.error("missing_result", "Termux returned no result bundle.", null)
                    return@post
                }
                result.success(
                    mapOf(
                        "stdout" to bundle.getString("stdout", ""),
                        "stderr" to bundle.getString("stderr", ""),
                        "exitCode" to bundle.getInt("exitCode", -1),
                        "err" to bundle.getInt("err", Activity.RESULT_CANCELED),
                        "errorMessage" to bundle.getString("errmsg", "")
                    )
                )
            }
        }
        handler.postDelayed(timeout, timeoutMs)

        val callbackIntent = Intent(this, TermuxResultService::class.java).apply {
            data = Uri.parse("opencode://termux-result/$executionId/${System.nanoTime()}")
            putExtra(EXTRA_EXECUTION_ID, executionId)
        }
        val callback = PendingIntent.getService(
            this,
            executionId,
            callbackIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
        )
        val command = Intent(ACTION_RUN_COMMAND).apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-s"))
            putExtra(EXTRA_STDIN, script)
            putExtra(EXTRA_WORKDIR, call.argument<String>("workdir") ?: TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, call.argument<Boolean>("background") ?: true)
            putExtra(EXTRA_PENDING_INTENT, callback)
            putExtra(EXTRA_COMMAND_LABEL, "OpenCode mobile")
        }

        try {
            startService(command)
        } catch (error: Exception) {
            handler.removeCallbacks(timeout)
            TermuxCommandRegistry.remove(executionId)
            result.error("dispatch_failed", error.message ?: "Termux rejected the command.", null)
        }
    }

    private fun openTermux(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return true
    }

    private fun hasRunCommandPermission(): Boolean =
        checkSelfPermission(RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun isRunCommandServiceAvailable(): Boolean = try {
        packageManager.getServiceInfo(ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE), 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun supportsRunCommandProtocol(versionName: String?): Boolean {
        val numbers = Regex("\\d+").findAll(versionName.orEmpty())
            .map { it.value.toIntOrNull() ?: 0 }
            .take(2)
            .toList()
        if (numbers.size < 2) return false
        return numbers[0] > 0 || numbers[1] >= 109
    }

    private fun isPackageInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        // The live background channel, readable by CodingActionReceiver while
        // the engine survives in the backgrounded process.
        @Volatile
        var backgroundChannel: MethodChannel? = null

        private const val CHANNEL_NAME = "oc/termux"
        private const val VOICE_CHANNEL_NAME = "oc/voice"
        private const val BACKGROUND_CHANNEL_NAME = "oc/background"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val RUN_COMMAND_PERMISSION_REQUEST = 4701
        private const val MICROPHONE_PERMISSION_REQUEST = 4702
        private const val BACKGROUND_NOTIFICATION_PERMISSION_REQUEST = 4703
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
        private const val EXTRA_EXECUTION_ID = "oc.executionId"
        private val nextExecutionId = AtomicInteger(1)
    }
}
