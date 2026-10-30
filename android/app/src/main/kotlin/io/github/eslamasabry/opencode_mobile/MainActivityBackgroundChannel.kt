package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

internal class MainActivityBackgroundChannel(
    private val activity: Activity,
    private val background: MainActivityBackground,
    private val launches: LaunchActivityBridge,
) {
    fun register(messenger: BinaryMessenger) {
        val channel = MethodChannel(messenger, "oc/background")
        // Notification actions also reach Dart while the Activity is backgrounded.
        MainActivity.backgroundChannel = channel
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getStatus" -> result.success(background.backgroundStatus())
                "getBackgroundPause" -> result.success(background.backgroundPauseStatus())
                "enable" -> background.enableBackgroundConnection(result)
                "disable" -> {
                    runCatching { BackgroundPauseStore(activity).clear() }
                    BackgroundConnectionService.stop(activity)
                    LivePauseReceiver.setPausedByUser(activity, false)
                    result.success(background.backgroundStatus(enabled = false))
                }
                "requestBatteryOptimizationExemption" -> {
                    background.requestBatteryOptimizationExemption()
                    result.success(background.backgroundStatus())
                }
                "openAppSettings" -> openAppSettings(result)
                "monitorNetworkPolicy" -> {
                    val connectivity = activity.getSystemService(ConnectivityManager::class.java)
                    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                    val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    result.success(mapOf("wifi" to wifi))
                }
                else -> handleAlerts(call, result)
            }
        }
    }

    private fun handleAlerts(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "showCodingAlert" -> result.success(
                mapOf("shown" to BackgroundConnectionService.showCodingAlert(activity, codingAlertRequest(call)))
            )
            "dismissCodingAlert" -> result.success(
                mapOf(
                    "dismissed" to BackgroundConnectionService.dismissCodingAlert(
                        activity, call.argument<String>("key").orEmpty()
                    )
                )
            )
            "consumeCodingAlertOpen" -> result.success(launches.consumeCodingAlertOpen())
            "updateLiveStatus" -> result.success(
                mapOf(
                    "updated" to BackgroundConnectionService.updateLiveStatus(
                        activity, call.argument<Map<*, *>>("status")
                    )
                )
            )
            else -> handleWidgets(call, result)
        }
    }

    private fun handleWidgets(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "clearWidgetProfile" -> result.success(
                mapOf("cleared" to WidgetV2Bridge.clearProfile(activity, call.argument<String>("profileID").orEmpty()))
            )
            "refreshHomeWidget" -> {
                SessionsWidgetProvider.refreshAll(activity)
                UsageWidgetProvider.refreshAll(activity)
                result.success(mapOf("refreshed" to true))
            }
            "refreshNativeLocale" -> result.success(
                mapOf("updated" to runCatching { NativeNotificationLocale.refresh(activity) }.isSuccess)
            )
            else -> result.notImplemented()
        }
    }

    private fun openAppSettings(result: MethodChannel.Result) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
        }
        activity.startActivity(intent)
        result.success(null)
    }

    private fun codingAlertRequest(call: MethodCall) = CodingAlertRequest(
        kind = call.argument<String>("kind").orEmpty(),
        sessionID = call.argument<String>("sessionID").orEmpty(),
        key = call.argument<String>("key").orEmpty(),
        requestID = call.argument<String>("requestID").orEmpty(),
        profileID = call.argument<String>("profileID").orEmpty(),
        allowActions = call.argument<Boolean>("allowActions") ?: true,
        monitorToken = call.argument<String>("monitorToken").orEmpty(),
        subtext = call.argument<String>("subtext").orEmpty(),
        title = call.argument<String>("title").orEmpty(),
        text = call.argument<String>("text").orEmpty(),
        agentName = call.argument<String>("agentName").orEmpty(),
        chatTitle = call.argument<String>("chatTitle").orEmpty(),
        detail = call.argument<String>("detail").orEmpty(),
        reason = call.argument<String>("reason").orEmpty(),
        options = call.argument<List<*>>("options")?.filterIsInstance<String>().orEmpty(),
        silent = call.argument<Boolean>("silent") ?: false,
        askAction = call.argument<String>("askAction").orEmpty(),
        askTarget = call.argument<String>("askTarget").orEmpty(),
    )
}
