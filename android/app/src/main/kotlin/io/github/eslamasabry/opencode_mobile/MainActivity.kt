package io.github.eslamasabry.opencode_mobile

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterFragmentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var nativeReplies: NativeChannelReplies? = null
    private var readAloud: ReadAloudBridge? = null
    private var localPdf: LocalPdfBridge? = null
    private var networkMonitor: NetworkMonitor? = null
    private var projectExport: ProjectExportBridge? = null
    private var byoHostSigner: ByoHostSigner? = null
    private var crashDiagnostics: CrashDiagnosticsBridge? = null
    private val termux by lazy { TermuxActivityBridge(this, handler) }
    private val launches by lazy { LaunchActivityBridge(this) }
    private val background by lazy { MainActivityBackground(this, handler) }
    private val media by lazy { MainActivityMedia(this) { readAloud?.beforeMicrophoneCapture() == true } }

    private var scheduledPrompts: ScheduledPromptChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        background.cancelBackgroundRequests()
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
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        launches.configure(messenger, intent)
        ScheduledPromptChannel.captureOpen(intent)
        scheduledPrompts?.close()
        scheduledPrompts = ScheduledPromptChannel(this).also { it.register(messenger) }
        ScheduledPromptScheduler(applicationContext).restore()
        MethodChannel(messenger, "oc/termux").setMethodCallHandler { call, rawResult ->
            val result = replies.wrap(rawResult)
            result.guarded("termux_unavailable") { termux.handle(call, result) }
        }
        MethodChannel(messenger, BUILTIN_LINUX_CHANNEL_NAME).setMethodCallHandler { call, rawResult ->
            val result = replies.wrap(rawResult)
            result.guarded("builtin_linux") { handleBuiltinLinux(call, result) }
        }
        AppLifecycle.register(this, messenger, replies) { background.requestBatteryOptimizationExemption() }
        LocalTerminal.get(applicationContext).register(messenger)
        StorageAccess.register(this, messenger)
        media.register(messenger)
        MainActivityBackgroundChannel(this, background, launches).register(messenger)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        scheduledPrompts?.close()
        scheduledPrompts = null
        background.cancelBackgroundRequests()
        nativeReplies?.detach()
        nativeReplies = null
        termux.reset()
        projectExport?.dispose()
        projectExport = null
        networkMonitor?.dispose()
        networkMonitor = null
        localPdf?.dispose()
        localPdf = null
        readAloud?.dispose()
        readAloud = null
        if (backgroundChannel != null) backgroundChannel = null
        launches.reset()
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
        media.dispose()
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
                    val agentHostCode = PhoneAgentHost.failureCode(error)
                    main.post {
                        if (NativeRuntimeOwnership.isUnknownFailure(error)) {
                            result.error(NativeRuntimeOwnership.UNKNOWN_CODE,
                                "Open app settings, force stop OpenCode, then open it again.", null)
                        } else if (agentHostCode != null) {
                            result.error(agentHostCode, "Open agent setup and try again.", null)
                        } else if (error is PhoneEngineNative.Failure) {
                            result.error(error.code, "The phone engine is unavailable.", null)
                        } else if (call.method in setOf("startPhoneEngine", "phoneEngineStatus",
                            "phoneEngineCredentials", "stopPhoneEngine", "deletePhoneEngine",
                            "startProtectedPhoneServer", "runPhoneEngineBoundaryProbe")) {
                            result.error("engine_unavailable", "The phone engine is unavailable.", null)
                        } else if (call.method in setOf(
                            "startAgentHost", "agentHostStatus", "stopAgentHost", "deleteAgentHost", "agentHostPrepareUpdate",
                            "agentHostVersion", "agentHostWorkspace", "startAgentSignIn", "readAgentSignInChallenge",
                            "submitAgentSignInCode", "cancelAgentSignIn", "agentSignInStatus"
                        )) {
                            result.error("agent_unavailable", "The agent is unavailable. Try again.", null)
                        } else if (call.method in setOf("setPhoneServerIdlePolicy", "observePhoneAgentWork",
                            "resumeIdleStoppedPhoneServer", "completePhoneServerIdleResume")) {
                            result.error("idle_resume_unavailable",
                                "The phone server could not start. Open setup or try Start again.", null)
                        } else if (call.method in setOf("restartServer", "stageServerRecovery", "bindServerRecovery",
                            "serverRecoveryBudget", "serverRecoveryReceipts", "ackServerRecoveryReceipt",
                            "updateServerRecoveryReceipt", "confirmManualServerStart", "unbindServerRecovery",
                            "deleteServerRecovery", "confirmServerPresence")) {
                            result.error("recovery_unavailable", "The phone server could not restart.",
                                if (call.method == "restartServer") serverStartDetail(error) else null)
                        } else if (error is SetupPersistenceException) {
                            result.error(SetupPersistenceException.CODE, null, null)
                        } else {
                            result.error("builtin_linux", "The phone operation could not finish. Try again.",
                                if (call.method in setOf("startServer", "run", "status")) {
                                    serverStartDetail(error)
                                } else null)
                        }
                    }
                }
            }.start()
        }
        when (call.method) {
            "agentAuthProbe" -> inBackground {
                linux.agentAuthProbe(call.arguments as? Map<*, *> ?: emptyMap<Any, Any>())
            }
            "startAgentHost" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                val profile = args["profileId"] as? String ?: ""
                val generation = when (val value = args["expectedIdleGeneration"]) {
                    is Int -> value.toLong()
                    is Long -> value
                    else -> null
                }
                val ticket = try { linux.captureAgentHostStart(profile, args["idleResume"] == true, generation) }
                    catch (_: Exception) { null }
                if (ticket == null) result.error("idle_resume_stale",
                    "The agents could not reconnect. Open agent setup and try again.", null)
                else inBackground {
                    linux.withAgentHostStart(ticket) {
                        linux.agentHost.start(PhoneAgentHost.StartRequest(
                            profile,
                            call.argument<String>("password") ?: error("Agent unavailable"),
                            call.argument<Int>("port") ?: 4099,
                            call.argument<String>("config") ?: error("Agent unavailable"),
                            PhoneAgentHost.HelperPolicy(
                                call.argument<String>("expectedHelperVersion"),
                                call.argument<Boolean>("restartIfVersionChanged") == true,
                                call.argument<Boolean>("forceRestart") == true,
                            )))
                    }
                }
            }
            "agentHostStatus", "stopAgentHost", "deleteAgentHost", "agentHostVersion", "agentHostPrepareUpdate",
            "agentHostWorkspace" -> inBackground {
                val profile = call.argument<String>("profileId") ?: error("Agent unavailable")
                when (call.method) {
                    "agentHostPrepareUpdate" -> linux.agentHost.prepareUpdate(profile)
                    "agentHostWorkspace" -> linux.agentHost.checks.workspace(profile)
                    "agentHostStatus" -> linux.agentHost.status(profile)
                    "stopAgentHost" -> linux.agentHost.stop(profile,
                        call.argument<Boolean>("guardedForUpdate") == true)
                    "deleteAgentHost" -> { linux.agentHost.delete(profile); null }
                    else -> linux.agentHost.checks.version(profile,
                        call.argument<String>("executable") ?: error("Agent unavailable"),
                        call.argument<String>("version") ?: error("Agent unavailable"))
                }
            }
            "startAgentSignIn", "readAgentSignInChallenge", "submitAgentSignInCode",
            "cancelAgentSignIn", "agentSignInStatus" -> inBackground {
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
                ) + linux.serverIdleStatus()
            }
            "setPhoneServerIdlePolicy" -> inBackground {
                val args = call.arguments as? Map<*, *> ?: error("idle_policy_invalid")
                linux.setPhoneServerIdlePolicy(args["enabled"] as? Boolean ?: error("idle_policy_invalid"),
                    args["idleMinutes"] as? Int ?: error("idle_policy_invalid"))
            }
            "observePhoneAgentWork" -> inBackground {
                val args = call.arguments as? Map<*, *> ?: error("idle_policy_invalid")
                val busy = args["busy"]
                check(busy == null || busy is Boolean)
                linux.observePhoneAgentWork(
                    args["profileId"] as? String ?: error("idle_policy_invalid"), busy as? Boolean)
                null
            }
            "resumeIdleStoppedPhoneServer", "completePhoneServerIdleResume" -> inBackground {
                val args = call.arguments as? Map<*, *> ?: error("idle_resume_stale")
                val profile = args["profileId"] as? String ?: error("idle_resume_stale")
                val generation = when (val value = args["expectedIdleGeneration"]) {
                    is Int -> value.toLong()
                    is Long -> value
                    else -> error("idle_resume_stale")
                }
                if (call.method == "resumeIdleStoppedPhoneServer") {
                    linux.resumeIdleStoppedPhoneServer(profile, generation)
                } else {
                    linux.completePhoneServerIdleResume(profile, generation)
                }
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
            "confirmServerPresence" -> inBackground {
                linux.confirmServerPresence(call.argument<String>("profileId") ?: error("recovery_unavailable"))
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
            "setPhoneAgentChatWorkLease" -> {
                val profile = call.argument<String>("profileId") ?: ""
                val leaseId = call.argument<String>("leaseId") ?: ""
                val on = call.argument<Boolean>("on") == true
                val forMs = call.argument<Number>("forMs")?.toLong() ?: 0L
                inBackground { linux.setPhoneAgentChatWorkLease(profile, leaseId, on, forMs) }
            }
            "performance" -> inBackground { linux.performance() }
            // "What's running on this phone" (NativeProcessList has the contract).
            "processes" -> inBackground { linux.processTable() }
            "stopProcesses" -> {
                val pids = call.argument<List<*>>("pids").orEmpty().mapNotNull { (it as? Number)?.toInt() }
                inBackground { linux.stopProcesses(pids) }
            }
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
                        components.map(MainActivityBuiltinSupport::setupSpec),
                        params?.let { org.json.JSONObject(it) },
                        SetupRunner.Texts(
                            channel = texts["channel"] ?: NativeStrings.get(
                                applicationContext, R.string.native_setup_channel),
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
                result.success(MainActivityBuiltinSupport.openStorageSettingsIntent(this))
            }
            else -> result.notImplemented()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launches.onNewIntent(intent)
        ScheduledPromptChannel.captureOpen(intent)
        scheduledPrompts?.notifyOpen()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (media.onPermissionResult(requestCode, grantResults)) return
        when (requestCode) {
            TermuxActivityBridge.PERMISSION_REQUEST -> termux.onPermissionResult(grantResults)
            MainActivityBackground.PERMISSION_REQUEST -> background.onPermissionResult(grantResults)
        }
    }

    companion object {
        /**
         * Why a server start failed, for the card's Details only: the error's
         * kind, its own words and where it was thrown in our code. Our check
         * messages carry no secrets; the length is capped anyway.
         */
        internal fun serverStartDetail(error: Throwable): String {
            // A release build may rename classes; the first frames still map
            // back to the source through the build's mapping file.
            fun where(e: Throwable) = e.stackTrace.take(SERVER_DETAIL_FRAME_LIMIT)
                .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            var root = error
            while (root.cause != null && root.cause !== root) root = root.cause!!
            val cause = if (root === error) "" else "; cause ${root.javaClass.simpleName}: " +
                "${root.message ?: "no message"} at ${where(root)}"
            return "${error.javaClass.simpleName}: ${error.message ?: "no message"} at ${where(error)}$cause"
                .take(SERVER_DETAIL_CHARACTER_LIMIT)
        }

        // The live background channel, readable by CodingActionReceiver while
        // the engine survives in the backgrounded process.
        @Volatile
        var backgroundChannel: MethodChannel? = null

        private const val SERVER_DETAIL_FRAME_LIMIT = 3
        private const val SERVER_DETAIL_CHARACTER_LIMIT = 600
        private const val BUILTIN_LINUX_CHANNEL_NAME =
            "io.github.eslamasabry.opencode_mobile/builtin_linux"
        const val EXTRA_LAUNCH_ACTION = "oc.shortcut"
        const val EXTRA_LAUNCH_PROFILE = "oc.shortcut.profile"
        const val EXTRA_LAUNCH_SESSION = "oc.shortcut.session"
    }
}
