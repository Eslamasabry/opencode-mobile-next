package io.github.eslamasabry.opencode_mobile

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : FlutterActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var nativeReplies: NativeChannelReplies? = null
    private var permissionResult: MethodChannel.Result? = null
    private var runCommandAccessResult: MethodChannel.Result? = null
    private var microphonePermissionResult: MethodChannel.Result? = null
    private var backgroundPermissionResult: MethodChannel.Result? = null
    private var cameraPermissionResult: MethodChannel.Result? = null
    private var pendingCodingAlertOpen: Map<String, String>? = null
    private var pendingSharedText: String? = null
    private var shareChannel: MethodChannel? = null
    private var shareDartReady = false
    private var pendingLaunchAction: String? = null
    private var pendingSessionLaunch: Map<String, String>? = null
    private var shortcutChannel: MethodChannel? = null
    private var shortcutDartReady = false
    private var pendingSessionLink: String? = null
    private var linkChannel: MethodChannel? = null
    private var linkDartReady = false
    private var readAloud: ReadAloudBridge? = null
    private var localPdf: LocalPdfBridge? = null
    private var networkMonitor: NetworkMonitor? = null
    private var projectExport: ProjectExportBridge? = null
    private var byoHostSigner: ByoHostSigner? = null
    private var crashDiagnostics: CrashDiagnosticsBridge? = null
    private val voiceDownloadNotifications by lazy { VoiceDownloadNotifications(this) }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        crashDiagnostics?.dispose()
        crashDiagnostics = CrashDiagnosticsBridge(this, flutterEngine.dartExecutor.binaryMessenger)
        nativeReplies?.detach()
        val replies = NativeChannelReplies { action -> handler.post(action) }
        nativeReplies = replies
        TailscaleHandoff(this, flutterEngine.dartExecutor.binaryMessenger)
        byoHostSigner?.dispose()
        byoHostSigner = ByoHostSigner(this, flutterEngine.dartExecutor.binaryMessenger)
        networkMonitor?.dispose()
        networkMonitor = NetworkMonitor(this, flutterEngine.dartExecutor.binaryMessenger)
        localPdf?.dispose()
        localPdf = LocalPdfBridge(this, flutterEngine.dartExecutor.binaryMessenger)
        readAloud?.dispose()
        readAloud = ReadAloudBridge(this, flutterEngine.dartExecutor.binaryMessenger)
        // Export projects on This phone (oc/project_export).
        projectExport?.dispose()
        projectExport = ProjectExportBridge(this, flutterEngine.dartExecutor.binaryMessenger)
        captureCodingAlertOpen(intent)
        captureSharedText(intent)
        captureLaunchAction(intent)
        captureSessionLaunch(intent)
        captureSessionLink(intent)
        linkDartReady = false
        linkChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, LINK_CHANNEL_NAME)
            .also { channel ->
                channel.setMethodCallHandler { call, result ->
                    when (call.method) {
                        "consumeSessionLink" -> {
                            // Same readiness handshake as shortcuts: Dart's
                            // inbound handler is installed before this call,
                            // so later links can be pushed live.
                            linkDartReady = true
                            val link = pendingSessionLink
                            pendingSessionLink = null
                            result.success(link)
                        }
                        else -> result.notImplemented()
                    }
                }
            }
        shortcutDartReady = false
        shortcutChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SHORTCUT_CHANNEL_NAME)
            .also { channel ->
                channel.setMethodCallHandler { call, result ->
                    when (call.method) {
                        "consumeLaunchAction" -> {
                            // Readiness acknowledgment, mirroring the share
                            // channel: Dart's inbound handler is installed
                            // before this call, so later shortcut taps can be
                            // pushed live instead of parked.
                            shortcutDartReady = true
                            val action = pendingLaunchAction
                            pendingLaunchAction = null
                            result.success(action)
                        }
                        "consumeSessionLaunch" -> {
                            // The pinned-session tap that launched the app,
                            // drained once; IDs only.
                            val launch = pendingSessionLaunch
                            pendingSessionLaunch = null
                            result.success(launch)
                        }
                        "setPinnedSessions" -> {
                            // Dart owns the payload (titles only, capped);
                            // native only mirrors it onto the launcher.
                            val profileID = call.argument<String>("profileID").orEmpty()
                            val sessions = call.argument<List<Map<String, Any?>>>("sessions")
                                .orEmpty()
                            result.success(
                                mapOf(
                                    "published" to PinnedSessionShortcuts.publish(
                                        this,
                                        profileID = profileID,
                                        sessions = sessions
                                    )
                                )
                            )
                        }
                        else -> result.notImplemented()
                    }
                }
            }
        shareDartReady = false
        shareChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SHARE_CHANNEL_NAME)
            .also { channel ->
                channel.setMethodCallHandler { call, result ->
                    when (call.method) {
                        "consumeSharedText" -> {
                            // Dart installs its inbound handler before this
                            // call. This is the readiness acknowledgment for
                            // live shares delivered during engine startup.
                            shareDartReady = true
                            val text = pendingSharedText
                            pendingSharedText = null
                            result.success(text)
                        }
                        "shareText" -> result.success(
                            shareTextOut(
                                call.argument<String>("text"),
                                call.argument<String>("subject"),
                            ),
                        )
                        else -> result.notImplemented()
                    }
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
            .setMethodCallHandler { call, rawResult ->
                val result = replies.wrap(rawResult)
                result.guarded("termux_unavailable") {
                    when (call.method) {
                        "getCapabilities" -> result.success(capabilities())
                        "getSigningCertificateSha256" ->
                            result.success(signingCertificateSha256())
                        "requestRunCommandPermission" -> requestRunCommandPermission(result)
                        "requestRunCommandAccess" -> requestRunCommandAccess(result)
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
                        "startSetup", "setupStatus", "cancelSetup", "completeSetupStep",
                        "setupHostInstalled", "setupRun" -> handleTermuxSetup(call, result)
                        "openTermuxSession" -> openTermuxSession(call, result)
                        else -> result.notImplemented()
                    }
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BUILTIN_LINUX_CHANNEL_NAME)
            .setMethodCallHandler { call, rawResult ->
                val result = replies.wrap(rawResult)
                result.guarded("builtin_linux") { handleBuiltinLinux(call, result) }
            }
        // Why the previous process ended, and keep-alive settings (oc/lifecycle).
        AppLifecycle.register(this, flutterEngine.dartExecutor.binaryMessenger, replies) {
            requestBatteryOptimizationExemption()
        }
        LocalTerminal.get(applicationContext).register(flutterEngine.dartExecutor.binaryMessenger)
        // Shared-storage file access (oc/storage), asked only for such projects.
        StorageAccess.register(this, flutterEngine.dartExecutor.binaryMessenger)
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
                    else -> if (!voiceDownloadNotifications.handle(call, result)) result.notImplemented()
                }
            }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CAMERA_CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "hasCamera" -> result.success(hasCamera())
                    "requestCameraPermission" -> requestCameraPermission(result)
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
                        // Turned off in Settings: nothing left to resume from the tile.
                        LivePauseReceiver.setPausedByUser(this, false)
                        result.success(backgroundStatus(enabled = false))
                    }
                    "requestBatteryOptimizationExemption" -> {
                        requestBatteryOptimizationExemption()
                        result.success(backgroundStatus())
                    }
                    // P0.6: lets the Notifications page send a blocked person
                    // straight to this app's notification settings, not just
                    // the general app-info page the other channels open.
                    "openAppSettings" -> {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        } else {
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$packageName")
                            )
                        }
                        startActivity(intent)
                        result.success(null)
                    }
                    "monitorNetworkPolicy" -> {
                        val connectivity = getSystemService(ConnectivityManager::class.java)
                        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                        result.success(mapOf("wifi" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)))
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
                                    requestID = requestID,
                                    profileID = call.argument<String>("profileID").orEmpty(),
                                    allowActions = call.argument<Boolean>("allowActions") ?: true,
                                    monitorToken = call.argument<String>("monitorToken").orEmpty(),
                                    subtext = call.argument<String>("subtext").orEmpty(),
                                    title = call.argument<String>("title").orEmpty(),
                                    text = call.argument<String>("text").orEmpty(),
                                    agentName = call.argument<String>("agentName").orEmpty()
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
                    "updateLiveStatus" -> {
                        result.success(
                            mapOf(
                                "updated" to BackgroundConnectionService.updateLiveStatus(
                                    this,
                                    runningCount = call.argument<Number>("runningCount")?.toInt() ?: 0,
                                    pendingCount = call.argument<Number>("pendingCount")?.toInt() ?: 0,
                                    title = call.argument<String>("title"),
                                    detail = call.argument<String>("detail")
                                )
                            )
                        )
                    }
                    else -> result.notImplemented()
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(): String? {
        val info = packageManager.getPackageInfo(
            packageName,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
        )
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.singleOrNull()
        } else {
            info.signatures?.singleOrNull()
        } ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02X".format(byte) }
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        nativeReplies?.detach()
        nativeReplies = null
        permissionResult = null
        runCommandAccessResult = null
        projectExport?.dispose()
        projectExport = null
        networkMonitor?.dispose()
        networkMonitor = null
        localPdf?.dispose()
        localPdf = null
        readAloud?.dispose()
        readAloud = null
        if (backgroundChannel != null) backgroundChannel = null
        shareChannel = null
        shareDartReady = false
        shortcutChannel = null
        shortcutDartReady = false
        linkChannel = null
        linkDartReady = false
        super.cleanUpFlutterEngine(flutterEngine)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (projectExport?.onActivityResult(requestCode, resultCode, data) == true) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        try { BuiltinLinux.get(applicationContext).setActivityResumed(true) } catch (_: Exception) { }
        readAloud?.resume()
    }

    override fun onPause() {
        try { BuiltinLinux.get(applicationContext).setActivityResumed(false) } catch (_: Exception) { }
        readAloud?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        crashDiagnostics?.dispose()
        crashDiagnostics = null
        nativeReplies?.detach()
        nativeReplies = null
        byoHostSigner?.dispose()
        byoHostSigner = null
        networkMonitor?.dispose()
        networkMonitor = null
        voiceDownloadNotifications.dispose()
        localPdf?.dispose()
        localPdf = null
        readAloud?.dispose()
        readAloud = null
        super.onDestroy()
    }

    /**
     * The built-in Ubuntu (BuiltinLinux.kt). Anything that waits on proot runs
     * off the main thread and answers back on it.
     */
    private fun handleBuiltinLinux(call: MethodCall, result: MethodChannel.Result) {
        val linux = BuiltinLinux.get(applicationContext)
        val main = Handler(Looper.getMainLooper())
        fun inBackground(work: () -> Any?) {
            Thread {
                try {
                    val value = work()
                    main.post { result.success(value) }
                } catch (error: Throwable) {
                    main.post {
                        if (error is PhoneEngineNative.Failure) {
                            result.error(error.code, "The phone engine is unavailable.", null)
                        } else if (call.method in setOf("startPhoneEngine", "phoneEngineStatus",
                            "phoneEngineCredentials", "stopPhoneEngine", "deletePhoneEngine",
                            "startProtectedPhoneServer", "runPhoneEngineBoundaryProbe")) {
                            result.error("engine_unavailable", "The phone engine is unavailable.", null)
                        } else if (call.method in setOf("startAgentHost", "agentHostStatus", "stopAgentHost", "deleteAgentHost", "agentHostVersion", "agentHostWorkspace", "startAgentSignIn", "readAgentSignInChallenge", "submitAgentSignInCode", "cancelAgentSignIn", "agentSignInStatus")) {
                            result.error("agent_unavailable", "The agent is unavailable. Try again.", null)
                        } else if (call.method in setOf("restartServer", "stageServerRecovery", "bindServerRecovery",
                            "serverRecoveryBudget", "serverRecoveryReceipts", "ackServerRecoveryReceipt",
                            "updateServerRecoveryReceipt", "confirmManualServerStart", "unbindServerRecovery",
                            "deleteServerRecovery")) {
                            result.error("recovery_unavailable", "The phone server could not restart.", null)
                        } else if (error is SetupPersistenceException) {
                            result.error(SetupPersistenceException.CODE, null, null)
                        } else {
                            result.error("builtin_linux", "The phone operation could not finish. Try again.", null)
                        }
                    }
                }
            }.start()
        }
        when (call.method) {
            "startAgentHost", "agentHostStatus", "stopAgentHost", "deleteAgentHost", "agentHostVersion", "agentHostWorkspace" -> inBackground {
                val profile = call.argument<String>("profileId") ?: error("Agent unavailable")
                when (call.method) {
                    "startAgentHost" -> linux.agentHost.start(profile,
                        call.argument<String>("password") ?: error("Agent unavailable"),
                        call.argument<Int>("port") ?: 4099,
                        call.argument<String>("config") ?: error("Agent unavailable"))
                    "agentHostWorkspace" -> linux.agentHost.workspace(profile)
                    "agentHostStatus" -> linux.agentHost.status(profile)
                    "stopAgentHost" -> linux.agentHost.stop(profile)
                    "deleteAgentHost" -> { linux.agentHost.delete(profile); null }
                    else -> linux.agentHost.version(profile,
                        call.argument<String>("executable") ?: error("Agent unavailable"),
                        call.argument<String>("version") ?: error("Agent unavailable"))
                }
            }
            "startAgentSignIn", "readAgentSignInChallenge", "submitAgentSignInCode", "cancelAgentSignIn", "agentSignInStatus" -> inBackground {
                val profile = call.argument<String>("profileId") ?: error("Agent unavailable")
                val agent = call.argument<String>("agentId") ?: error("Agent unavailable")
                val run = call.argument<String>("runId") ?: error("Agent unavailable")
                val method = call.argument<String>("method") ?: error("Agent unavailable")
                when (call.method) {
                    "startAgentSignIn" -> linux.agentSignIn.start(profile, agent, run, method)
                    "readAgentSignInChallenge" -> linux.agentSignIn.challenge(profile, agent, run, method)
                    "submitAgentSignInCode" -> linux.agentSignIn.submit(profile, agent, run, method,
                        call.argument<String>("code") ?: error("Agent unavailable"))
                    "cancelAgentSignIn" -> linux.agentSignIn.cancel(profile, agent, run, method)
                    else -> linux.agentSignIn.status(profile, agent, run, method)
                }
            }
            "runPhoneEngineBoundaryProbe" -> inBackground { linux.runPhoneEngineBoundaryProbe() }
            "startPhoneEngine", "phoneEngineStatus", "phoneEngineCredentials",
            "stopPhoneEngine", "deletePhoneEngine", "startProtectedPhoneServer" -> {
                val profile = call.argument<String>("profileId")
                if (profile == null) {
                    result.error("invalid_profile", "The phone engine is unavailable.", null)
                    return
                }
                inBackground {
                    when (call.method) {
                        "startPhoneEngine" -> linux.startPhoneEngine(profile,
                            call.argument<Int>("port") ?: 4098, call.argument<String>("notice"))
                        "phoneEngineStatus" -> linux.phoneEngineStatus(profile)
                        "phoneEngineCredentials" -> linux.phoneEngineCredentials(profile)
                        "stopPhoneEngine" -> linux.stopPhoneEngine(profile)
                        "deletePhoneEngine" -> { linux.deletePhoneEngine(profile); null }
                        else -> {
                            val script = call.argument<String>("script")
                                ?: throw PhoneEngineNative.Failure("invalid_script")
                            linux.startProtectedPhoneServer(profile, script,
                                call.argument<Int>("port") ?: 4097)
                            null
                        }
                    }
                }
            }
            "status" -> inBackground {
                mapOf(
                    "installed" to linux.installed,
                    "phase" to linux.phase,
                    "message" to linux.message,
                    "serverRunning" to linux.serverRunning,
                    "serverRestartWanted" to linux.serverRestartWanted,
                    "serverRecoveryGeneration" to linux.serverRecoveryGeneration,
                    "serverRecoveryAuthority" to true,
                    "serverRecoveryScheduled" to linux.serverRecoveryScheduled,
                    "restorePhase" to linux.restorePhase,
                    "restoreReason" to linux.restoreReason,
                    "serverPort" to linux.port,
                    "serverUptimeMs" to linux.serverUptimeMs,
                    "services" to linux.runningServices(),
                    "abi" to (Build.SUPPORTED_ABIS.firstOrNull() ?: ""),
                    "bytesUsed" to linux.bytesUsed(),
                )
            }
            "installUbuntu" -> inBackground {
                linux.installInBackground()
                null
            }
            "run" -> {
                val script = call.argument<String>("script")
                if (script == null) {
                    result.error("builtin_linux", "No script to run", null)
                    return
                }
                val timeout = (call.argument<Int>("timeoutSeconds") ?: 600).toLong()
                inBackground {
                    val run = linux.run(script, timeout, agentUser = call.argument<Boolean>("agentUser") == true)
                    mapOf("exitCode" to run.exitCode, "output" to run.output)
                }
            }
            "startServer" -> {
                val script = call.argument<String>("script")
                val port = call.argument<Int>("port")
                if (script == null || port == null) {
                    result.error("builtin_linux", "A server needs a script and a port", null)
                    return
                }
                inBackground {
                    linux.startServer(script, port, call.argument<Map<*, *>>("restoreRecipe"))
                    null
                }
            }
            "restartServer" -> {
                val script = call.argument<String>("script")
                val port = call.argument<Int>("port")
                val generation = call.argument<Number>("expectedGeneration")?.toLong()
                if (script == null || port == null || generation == null) {
                    result.error("recovery_unavailable", "The phone server could not restart.", null)
                    return
                }
                inBackground {
                    linux.restartServer(script, port, generation)
                    null
                }
            }
            "stageServerRecovery" -> inBackground {
                linux.stageServerRecovery(call.argument<String>("profileId") ?: error("recovery_unavailable"),
                    call.argument<Map<String, Any?>>("legacyBudget") ?: error("recovery_unavailable"))
            }
            "bindServerRecovery" -> inBackground {
                linux.bindServerRecovery(call.argument<String>("profileId") ?: error("recovery_unavailable"),
                    call.argument<Map<String, Any?>>("legacyBudget"), call.argument<Boolean>("enabled") == true)
            }
            "serverRecoveryBudget" -> inBackground {
                linux.serverRecoveryBudget(call.argument<String>("profileId") ?: error("recovery_unavailable"))
            }
            "serverRecoveryReceipts" -> inBackground {
                linux.serverRecoveryReceipts(call.argument<String>("profileId") ?: error("recovery_unavailable"))
            }
            "ackServerRecoveryReceipt" -> inBackground {
                linux.ackServerRecoveryReceipt(call.argument<String>("profileId") ?: error("recovery_unavailable"),
                    call.argument<String>("eventId") ?: error("recovery_unavailable"))
                null
            }
            "updateServerRecoveryReceipt" -> inBackground {
                linux.updateServerRecoveryReceipt(call.argument<String>("profileId") ?: error("recovery_unavailable"),
                    call.argument<Map<String, Any?>>("budget") ?: error("recovery_unavailable"))
            }
            "confirmManualServerStart" -> inBackground {
                linux.confirmManualServerStart(call.argument<String>("profileId") ?: error("recovery_unavailable"))
            }
            "unbindServerRecovery", "deleteServerRecovery" -> {
                val profile = call.argument<String>("profileId") ?: ""
                linux.unbindServerRecovery(profile)
                inBackground {
                    if (call.method == "deleteServerRecovery") linux.deleteServerRecovery(profile)
                    else linux.persistServerRecoveryUnbind(profile)
                    null
                }
            }
            "cancelServerRecovery" -> {
                linux.cancelServerRecovery()
                result.success(null)
            }
            "confirmServerRecovery" -> {
                val generation = call.argument<Number>("expectedGeneration")?.toLong()
                try {
                    check(generation != null)
                    linux.confirmServerRecovery(generation)
                    result.success(null)
                } catch (_: Exception) {
                    result.error("recovery_unavailable", "The phone server could not restart.", null)
                }
            }
            "stopServer" -> {
                linux.requestServerStop()
                inBackground {
                    linux.stopServer(forPhoneEngineSetup = call.argument<Boolean>("phoneEngineSetup") == true)
                    null
                }
            }
            "serverLog" -> {
                val tail = call.argument<Int>("tailBytes") ?: 16_384
                inBackground { linux.serverLogTail(tail) }
            }
            // Keeps the phone awake while a reply runs on the in-app server
            // (bounded: every hold times out; the app renews it).
            "holdAwakeForWork" -> {
                val on = call.argument<Boolean>("on") == true
                val forMs = call.argument<Number>("forMs")?.toLong() ?: 0L
                inBackground { linux.holdAwakeForWork(on, forMs) }
            }
            "setChatWorkLease" -> {
                val leaseId = call.argument<String>("leaseId") ?: ""
                val on = call.argument<Boolean>("on") == true
                val forMs = call.argument<Number>("forMs")?.toLong() ?: 0L
                inBackground { linux.setChatWorkLease(leaseId, on, forMs) }
            }
            "performance" -> inBackground { linux.performance() }
            // Named long-running services beside the OpenCode server (the AI
            // Team supervisor); the server itself is the service "server".
            "startService" -> {
                val name = call.argument<String>("name")
                val script = call.argument<String>("script")
                if (name == null || script == null) {
                    result.error("builtin_linux", "A service needs a name and a script", null)
                    return
                }
                val port = call.argument<Int>("port")
                val notice = call.argument<String>("notice")
                inBackground {
                    linux.startService(name, script, port, notice)
                    null
                }
            }
            "stopService" -> {
                val name = call.argument<String>("name")
                if (name == null) {
                    result.error("builtin_linux", "Which service?", null)
                    return
                }
                if (name == BuiltinLinux.SERVER) linux.requestServerStop()
                inBackground {
                    linux.stopService(name)
                    null
                }
            }
            "serviceLog" -> {
                val name = call.argument<String>("name") ?: BuiltinLinux.SERVER
                val tail = call.argument<Int>("tailBytes") ?: 16_384
                inBackground { linux.serviceLogTail(name, tail) }
            }
            "uninstall", "removeRuntime" -> inBackground {
                linux.uninstall(
                    alsoDeleteProjects = call.argument<Boolean>("alsoDeleteProjects") == true,
                    confirmationName = call.argument<String>("confirmationName"),
                )
                null
            }
            "projectStorage" -> inBackground { linux.projectStorage() }
            "setSharedProjects" -> inBackground {
                linux.setSharedProjectRoots(call.argument<List<String>>("roots") ?: emptyList())
                null
            }
            // The phone setup job (SetupRunner.kt). The runner is made off
            // the main thread: its first use reads setup.json.
            "startSetup" -> {
                val jobId = call.argument<String>("jobId")
                val components = call.argument<List<Map<String, Any?>>>("components")
                if (jobId.isNullOrEmpty() || components.isNullOrEmpty()) {
                    result.error("builtin_linux", "A setup job needs an id and components", null)
                    return
                }
                val params = call.argument<Map<String, Any?>>("params")
                val texts = call.argument<Map<String, String>>("texts").orEmpty()
                inBackground {
                    SetupRunner.get(applicationContext).start(
                        jobId,
                        components.map(::setupSpec),
                        params?.let { org.json.JSONObject(it) },
                        SetupRunner.Texts(
                            channel = texts["channel"] ?: "Setup",
                            title = texts["title"] ?: "",
                            progress = texts["progress"] ?: "{percent}%",
                            done = texts["done"] ?: "",
                            stopped = texts["stopped"] ?: "",
                        ),
                    )
                    null
                }
            }
            "setupStatus" -> inBackground { SetupRunner.get(applicationContext).status() }
            "cancelSetup" -> inBackground {
                SetupRunner.get(applicationContext).cancel()
                null
            }
            "completeSetupStep" -> {
                val jobId = call.argument<String>("jobId")
                val id = call.argument<String>("id")
                if (jobId == null || id == null) {
                    result.error("builtin_linux", "A step needs a job and an id", null)
                    return
                }
                val ok = call.argument<Boolean>("ok") == true
                val error = call.argument<String>("error")
                val version = call.argument<String>("version")
                inBackground {
                    SetupRunner.get(applicationContext).completeStep(jobId, id, ok, error, version)
                    null
                }
            }
            // P0.8 pre-flight, low space: the device-wide Storage settings
            // (not this app's own App Info) is where freeing space actually
            // happens. Falls back to App Info on a ROM that hides it.
            "openStorageSettings" -> {
                result.success(openStorageSettingsIntent())
            }
            else -> result.notImplemented()
        }
    }

    private fun openStorageSettingsIntent(): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
        for (intent in candidates) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (error: ActivityNotFoundException) {
                // The next candidate.
            } catch (error: SecurityException) {
                // Not exported on this build; try the next one.
            }
        }
        return false
    }

    private fun setupSpec(raw: Map<String, Any?>): SetupRunner.Spec {
        fun strings(value: Any?): Map<String, String> =
            (value as? Map<*, *>)?.entries
                ?.filter { it.key is String && it.value is String }
                ?.associate { it.key as String to it.value as String }
                .orEmpty()
        return SetupRunner.Spec(
            id = raw["id"] as? String ?: error("A setup component needs an id"),
            script = raw["script"] as? String,
            native = raw["native"] == true,
            step = raw["step"] == true,
            weight = (raw["weight"] as? Number)?.toDouble() ?: 1.0,
            skipped = raw["skipped"] == true,
            version = raw["version"] as? String,
            stage = raw["stage"] as? String,
            labels = strings(raw["labels"]),
            data = strings(raw["data"]),
            agentUser = raw["agentUser"] == true,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureCodingAlertOpen(intent)
        if (captureSharedText(intent)) {
            // Delivered live when Dart is already listening; otherwise it
            // waits in pendingSharedText for the consume call.
            val channel = shareChannel
            val text = pendingSharedText
            if (shareDartReady && channel != null && text != null) {
                pendingSharedText = null
                channel.invokeMethod("shared", text)
            }
        }
        if (captureLaunchAction(intent)) {
            // Same delivery rule as shares: live when Dart is listening,
            // otherwise parked until consumeLaunchAction.
            val channel = shortcutChannel
            val action = pendingLaunchAction
            if (shortcutDartReady && channel != null && action != null) {
                pendingLaunchAction = null
                channel.invokeMethod("launched", action)
            }
        }
        if (captureSessionLaunch(intent)) {
            val channel = shortcutChannel
            val launch = pendingSessionLaunch
            if (shortcutDartReady && channel != null && launch != null) {
                pendingSessionLaunch = null
                channel.invokeMethod("launchedSession", launch)
            }
        }
        if (captureSessionLink(intent)) {
            // Live when Dart is listening, otherwise parked until
            // consumeSessionLink.
            val channel = linkChannel
            val link = pendingSessionLink
            if (linkDartReady && channel != null && link != null) {
                pendingSessionLink = null
                channel.invokeMethod("linked", link)
            }
        }
    }

    /// A pinned-session launcher shortcut (PinnedSessionShortcuts) tapped
    /// from the home screen. Only the two IDs travel; both extras are removed
    /// so a configuration change does not replay the tap, and Dart decides
    /// whether the named profile is the active one before opening anything.
    private fun captureSessionLaunch(intent: Intent?): Boolean {
        if (intent == null) return false
        if (!intent.hasExtra(EXTRA_LAUNCH_SESSION)) return false
        val sessionID = intent.getStringExtra(EXTRA_LAUNCH_SESSION)?.trim().orEmpty()
        val profileID = intent.getStringExtra(EXTRA_LAUNCH_PROFILE)?.trim().orEmpty()
        intent.removeExtra(EXTRA_LAUNCH_SESSION)
        intent.removeExtra(EXTRA_LAUNCH_PROFILE)
        if (sessionID.isEmpty() || profileID.isEmpty()) return false
        pendingSessionLaunch = mapOf("profileID" to profileID, "sessionID" to sessionID)
        return true
    }

    /// A session handoff link (AndroidManifest VIEW filter for
    /// opencode-mobile://session) or an AI Team link (opencode-mobile://team,
    /// TEAM-203). Only the links' own scheme and hosts are accepted and only
    /// the URI text crosses to Dart, which validates the route identifiers
    /// it may carry. The intent's action and data are
    /// cleared so a configuration change does not replay the open. This
    /// bridge never connects, creates a session or sends anything.
    private fun captureSessionLink(intent: Intent?): Boolean {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        if (!data.scheme.equals(LINK_SCHEME, ignoreCase = true)) return false
        if (!data.host.equals(LINK_HOST, ignoreCase = true) &&
            !data.host.equals(TEAM_LINK_HOST, ignoreCase = true)
        ) return false
        val text = data.toString()
        // Consume the open before deciding, so a rejected link is not
        // replayed either.
        intent.action = Intent.ACTION_MAIN
        intent.data = null
        if (!SessionLinkIngress.accepts(text)) return false
        pendingSessionLink = text
        return true
    }

    /// A static launcher shortcut (res/xml/shortcuts.xml) tapped from the
    /// home screen. Only the whitelisted action ids are accepted; anything
    /// else is dropped. The extra is removed so a configuration change does
    /// not replay the tap, and the bridge itself never connects, creates a
    /// session or sends anything: Dart decides where the action goes.
    private fun captureLaunchAction(intent: Intent?): Boolean {
        if (intent == null) return false
        if (!intent.hasExtra(EXTRA_LAUNCH_ACTION)) return false
        val action = intent.getStringExtra(EXTRA_LAUNCH_ACTION)?.trim().orEmpty()
        intent.removeExtra(EXTRA_LAUNCH_ACTION)
        if (action !in LAUNCH_ACTIONS) return false
        pendingLaunchAction = action
        return true
    }

    /// Shares [text] out through the system chooser (Report a problem's
    /// Share). This app is left out of the targets: sharing the report to
    /// ourselves would start a session with it. True once the chooser opened.
    private fun shareTextOut(text: String?, subject: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!subject.isNullOrEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        val chooser = Intent.createChooser(send, null).apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(this@MainActivity, MainActivity::class.java)),
            )
        }
        return try {
            startActivity(chooser)
            true
        } catch (error: ActivityNotFoundException) {
            false
        }
    }

    /// Text shared from another app through the system share sheet. Only
    /// plain text is accepted; the subject, when present, becomes a first
    /// line so a shared link keeps its title.
    private fun captureSharedText(intent: Intent?): Boolean {
        if (intent == null || intent.action != Intent.ACTION_SEND) return false
        val type = intent.type ?: return false
        if (!type.startsWith("text/")) return false
        val body = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (body.isEmpty() && subject.isEmpty()) return false
        pendingSharedText = when {
            subject.isEmpty() -> body
            body.isEmpty() -> subject
            body.startsWith(subject) -> body
            else -> "$subject\n$body"
        }
        // Consume the share so a configuration change does not replay it.
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_SUBJECT)
        return true
    }

    private fun captureCodingAlertOpen(intent: Intent?) {
        if (intent == null) return
        val kind = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_KIND
        ).orEmpty()
        val sessionID = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID
        ).orEmpty()
        val profileID = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_PROFILE_ID
        ).orEmpty()
        if (kind.isNotBlank() && sessionID.isNotBlank()) {
            pendingCodingAlertOpen = mapOf(
                "kind" to kind,
                "sessionID" to sessionID,
                // Set by widget-row taps only; Dart drops the destination
                // when it names a profile other than the active one.
                "profileID" to profileID,
                "monitorToken" to intent.getStringExtra(BackgroundConnectionService.EXTRA_MONITOR_TOKEN).orEmpty()
            )
        }
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_KIND)
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID)
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_PROFILE_ID)
        intent.removeExtra(BackgroundConnectionService.EXTRA_MONITOR_TOKEN)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (voiceDownloadNotifications.onPermissionResult(requestCode, grantResults)) return
        if (StorageAccess.onPermissionResult(this, requestCode)) return
        when (requestCode) {
            RUN_COMMAND_PERMISSION_REQUEST -> {
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                val access = runCommandAccessResult
                runCommandAccessResult = null
                if (access != null) {
                    // Android answers "no" without a dialog once the person
                    // chose "Don't allow" twice: then only Settings can.
                    access.success(
                        when {
                            granted -> "granted"
                            !shouldShowRequestPermissionRationale(RUN_COMMAND_PERMISSION) ->
                                "permanentlyDenied"
                            else -> "denied"
                        }
                    )
                }
                val result = permissionResult ?: return
                permissionResult = null
                result.success(granted)
            }
            MICROPHONE_PERMISSION_REQUEST -> {
                val result = microphonePermissionResult ?: return
                microphonePermissionResult = null
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                val status = if (granted) {
                    if (readAloud?.beforeMicrophoneCapture() != true) {
                        result.error("voice_unavailable", "Local voice input is unavailable.", null)
                        return
                    }
                    "granted"
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    "permanentlyDenied"
                } else {
                    "denied"
                }
                result.success(status)
            }
            CAMERA_PERMISSION_REQUEST -> {
                val result = cameraPermissionResult ?: return
                cameraPermissionResult = null
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                val status = if (granted) {
                    "granted"
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
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
        // The speech models run in native memory (ONNX Runtime), which the
        // per-app Java heap limit (`memoryClass`, 256-512 MB even on a 16 GB
        // phone) says nothing about. The device's physical memory does.
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        return mapOf(
            "availableStorageBytes" to storage.availableBytes,
            "memoryClassMb" to activityManager.memoryClass,
            "totalMemoryMb" to (memory.totalMem / (1024L * 1024L)),
            "lowRamDevice" to activityManager.isLowRamDevice,
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
            "hasMicrophone" to packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        )
    }

    private fun hasCamera(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /// Mirrors [requestMicrophonePermission]. `shouldShowRequestPermissionRationale`
    /// is false both before the very first prompt and after "don't ask again",
    /// so a remembered "we have asked once" flag is what separates the two.
    private fun requestCameraPermission(result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            result.success("granted")
            return
        }
        val permissionPreferences = getSharedPreferences("camera_permissions", MODE_PRIVATE)
        if (permissionPreferences.getBoolean("camera_requested", false) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        ) {
            result.success("permanentlyDenied")
            return
        }
        if (cameraPermissionResult != null) {
            result.error("permission_in_progress", "A camera permission request is already open.", null)
            return
        }
        cameraPermissionResult = result
        permissionPreferences.edit().putBoolean("camera_requested", true).apply()
        requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
    }

    private fun requestMicrophonePermission(result: MethodChannel.Result) {
        if (readAloud?.beforeMicrophoneCapture() != true) {
            result.error("voice_unavailable", "Local voice input is unavailable.", null)
            return
        }
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
        if (permissionResult != null || runCommandAccessResult != null) {
            result.error("permission_in_progress", "A permission request is already open.", null)
            return
        }
        permissionResult = result
        requestPermissions(arrayOf(RUN_COMMAND_PERMISSION), RUN_COMMAND_PERMISSION_REQUEST)
    }

    /**
     * The same request as [requestRunCommandPermission], answered in words:
     * `granted`, `denied`, `permanentlyDenied` (Android no longer shows the
     * dialog; the app's settings page is the only way) or `missing` (no
     * Termux to ask for). Read-only until the person answers the dialog.
     */
    private fun requestRunCommandAccess(result: MethodChannel.Result) {
        if (!isPackageInstalled(TERMUX_PACKAGE)) {
            result.success("missing")
            return
        }
        if (hasRunCommandPermission()) {
            result.success("granted")
            return
        }
        if (runCommandAccessResult != null || permissionResult != null) {
            result.error("permission_in_progress", "A permission request is already open.", null)
            return
        }
        runCommandAccessResult = result
        requestPermissions(arrayOf(RUN_COMMAND_PERMISSION), RUN_COMMAND_PERMISSION_REQUEST)
    }

    /** V2 setup keeps the exact RUN_COMMAND permission/service boundary. */
    private fun handleTermuxSetup(call: MethodCall, result: MethodChannel.Result) {
        if (!hasRunCommandPermission()) {
            result.error("permission_denied", "Termux RUN_COMMAND permission is required.", null)
            return
        }
        if (!isRunCommandServiceAvailable()) {
            result.error("service_unavailable", "Termux RunCommandService is unavailable.", null)
            return
        }
        Thread({
            try {
                val runner = TermuxSetupRunner.get(applicationContext)
                val answer: Any? = when (call.method) {
                    "startSetup" -> {
                        runner.start(
                            call.argument<String>("jobId") ?: error("A setup job needs an id"),
                            call.argument<List<Map<String, Any?>>>("components").orEmpty(),
                            call.argument<Map<String, Any?>>("params").orEmpty(),
                        )
                        null
                    }
                    "setupStatus" -> runner.status()
                    "cancelSetup" -> { runner.cancel(); null }
                    "completeSetupStep" -> {
                        runner.completeStep(
                            call.argument<String>("jobId") ?: error("A step needs a job"),
                            call.argument<String>("id") ?: error("A step needs an id"),
                            call.argument<Boolean>("ok") == true,
                            call.argument<String>("version"),
                        )
                        null
                    }
                    "setupHostInstalled" -> runner.installed()
                    "setupRun" -> runner.run(
                        call.argument<String>("script") ?: error("A check needs a script"),
                        call.argument<Number>("timeoutMs")?.toLong() ?: 120_000,
                    )
                    else -> null
                }
                handler.post { result.success(answer) }
            } catch (_: Exception) {
                // No raw exception, command or provider output crosses into
                // diagnostic copy; callers must recheck durable status.
                handler.post { result.error("termux_setup", "Termux setup could not be confirmed. Check Termux and retry status.", null) }
            }
        }, "oc-termux-setup").start()
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

    /**
     * Starts [command] in a Termux terminal the person can see and type into
     * (Claude's sign-in asks for a pasted code). Unlike [runInTermux] the
     * command travels as a `bash -c` argument, because Termux feeds stdin only
     * to background commands, and no result is awaited: the session lives as
     * long as the person needs it. Termux may not raise its own window from a
     * service on Android 10+, so this activity opens it.
     */
    private fun openTermuxSession(call: MethodCall, result: MethodChannel.Result) {
        val script = call.argument<String>("command").orEmpty()
        if (script.isBlank() || script.length > 512) {
            result.error("invalid_script", "The Termux command is empty or too long.", null)
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
        val command = Intent(ACTION_RUN_COMMAND).apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", script))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, false)
            putExtra(EXTRA_SESSION_ACTION, "0")
            putExtra(EXTRA_COMMAND_LABEL, "OpenCode mobile")
        }
        try {
            startService(command)
        } catch (error: Exception) {
            result.error("dispatch_failed", error.message ?: "Termux rejected the command.", null)
            return
        }
        result.success(openTermux())
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
        private const val BUILTIN_LINUX_CHANNEL_NAME =
            "io.github.eslamasabry.opencode_mobile/builtin_linux"
        private const val CAMERA_CHANNEL_NAME = "oc/camera"
        private const val BACKGROUND_CHANNEL_NAME = "oc/background"
        private const val SHARE_CHANNEL_NAME = "oc/share"
        private const val SHORTCUT_CHANNEL_NAME = "oc/shortcut"
        private const val LINK_CHANNEL_NAME = "oc/link"
        // The session handoff link shape Dart's SessionLink.parse accepts.
        private const val LINK_SCHEME = "opencode-mobile"
        private const val LINK_HOST = "session"
        private const val TEAM_LINK_HOST = "team"
        // Intent extra set by res/xml/shortcuts.xml; values are the shortcut
        // ids Dart's LaunchAction enum understands.
        const val EXTRA_LAUNCH_ACTION = "oc.shortcut"
        // The static shortcut ids plus the Quick Settings tile's action
        // (AttentionTileService.LAUNCH_ACTION_ACTIVITY).
        private val LAUNCH_ACTIONS = setOf(
            "connect",
            "new_task",
            "activity",
            // The phone setup notifications: SetupService.LAUNCH_ACTION_PROGRESS
            // and LAUNCH_ACTION_DONE.
            "phone_setup",
            "phone_setup_done",
        )
        // Intent extras set by PinnedSessionShortcuts; a pinned-session tap
        // carries exactly these two IDs and nothing else.
        const val EXTRA_LAUNCH_PROFILE = "oc.shortcut.profile"
        const val EXTRA_LAUNCH_SESSION = "oc.shortcut.session"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val RUN_COMMAND_PERMISSION_REQUEST = 4701
        private const val MICROPHONE_PERMISSION_REQUEST = 4702
        private const val BACKGROUND_NOTIFICATION_PERMISSION_REQUEST = 4703
        private const val CAMERA_PERMISSION_REQUEST = 4704
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        private const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
        private const val EXTRA_EXECUTION_ID = "oc.executionId"
        private val nextExecutionId = AtomicInteger(1)
    }
}
