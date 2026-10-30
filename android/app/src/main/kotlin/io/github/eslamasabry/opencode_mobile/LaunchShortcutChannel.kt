package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.Intent
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

internal class LaunchShortcutChannel(private val activity: Activity) {
    private var pendingAction: String? = null
    private var pendingSession: Map<String, String>? = null
    private var channel: MethodChannel? = null
    private var dartReady = false

    fun configure(messenger: BinaryMessenger) {
        dartReady = false
        channel = MethodChannel(messenger, "oc/shortcut").also { shortcutChannel ->
            shortcutChannel.setMethodCallHandler { call, result -> handle(call, result) }
        }
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "consumeLaunchAction" -> {
                // Dart's inbound handler is installed before this readiness acknowledgment.
                dartReady = true
                val action = pendingAction
                pendingAction = null
                result.success(action)
            }
            "consumeSessionLaunch" -> {
                val launch = pendingSession
                pendingSession = null
                result.success(launch)
            }
            "setPinnedSessions" -> {
                // Dart owns the capped title payload; native mirrors it onto the launcher.
                val profileID = call.argument<String>("profileID").orEmpty()
                val sessions = call.argument<List<Map<String, Any?>>>("sessions").orEmpty()
                result.success(
                    mapOf("published" to PinnedSessionShortcuts.publish(activity, profileID, sessions))
                )
            }
            else -> result.notImplemented()
        }
    }

    fun reset() {
        channel = null
        dartReady = false
    }

    fun onNewIntent(intent: Intent) {
        if (captureAction(intent)) dispatchAction()
        if (captureSession(intent)) dispatchSession()
    }

    private fun dispatchAction() {
        val currentChannel = channel
        val action = pendingAction
        if (dartReady && currentChannel != null && action != null) {
            pendingAction = null
            currentChannel.invokeMethod("launched", action)
        }
    }

    private fun dispatchSession() {
        val currentChannel = channel
        val launch = pendingSession
        if (dartReady && currentChannel != null && launch != null) {
            pendingSession = null
            currentChannel.invokeMethod("launchedSession", launch)
        }
    }

    /** Remove both IDs after a pinned-session tap to prevent configuration-change replay. */
    fun captureSession(intent: Intent?): Boolean {
        if (intent == null || !intent.hasExtra(MainActivity.EXTRA_LAUNCH_SESSION)) return false
        val sessionID = intent.getStringExtra(MainActivity.EXTRA_LAUNCH_SESSION)?.trim().orEmpty()
        val profileID = intent.getStringExtra(MainActivity.EXTRA_LAUNCH_PROFILE)?.trim().orEmpty()
        intent.removeExtra(MainActivity.EXTRA_LAUNCH_SESSION)
        intent.removeExtra(MainActivity.EXTRA_LAUNCH_PROFILE)
        val accepted = sessionID.isNotEmpty() && profileID.isNotEmpty()
        if (accepted) pendingSession = mapOf("profileID" to profileID, "sessionID" to sessionID)
        return accepted
    }

    /** Accept only known action IDs; Dart decides where the action goes. */
    fun captureAction(intent: Intent?): Boolean {
        if (intent == null || !intent.hasExtra(MainActivity.EXTRA_LAUNCH_ACTION)) return false
        val action = intent.getStringExtra(MainActivity.EXTRA_LAUNCH_ACTION)?.trim().orEmpty()
        intent.removeExtra(MainActivity.EXTRA_LAUNCH_ACTION)
        val accepted = action in LAUNCH_ACTIONS || USAGE_TARGET.matches(action)
        if (accepted) pendingAction = action
        return accepted
    }

    private companion object {
        private val USAGE_TARGET = Regex(
            "^usage_target:(sign_in|add_key|set_up):[a-z0-9][a-z0-9_-]{0,31}:[A-Za-z0-9._-]{1,64}$"
        )
        private val LAUNCH_ACTIONS = setOf(
            "connect", "new_task", "activity", "usage", "usage_settings", "background_settings",
            "phone_setup", "phone_setup_done", "linux",
        )
    }
}
