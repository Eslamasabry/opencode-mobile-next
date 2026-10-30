package io.github.eslamasabry.opencode_mobile

import android.app.Activity
import android.content.Intent
import io.flutter.plugin.common.BinaryMessenger

/** Owns one-shot launch payloads across Flutter engine reconfiguration. */
internal class LaunchActivityBridge(private val activity: Activity) {
    private val shares = LaunchShareChannel(activity)
    private val shortcuts = LaunchShortcutChannel(activity)
    private val links = LaunchLinkChannel()
    private var pendingCodingAlertOpen: Map<String, String>? = null

    fun configure(messenger: BinaryMessenger, intent: Intent?) {
        captureCodingAlertOpen(intent)
        shares.capture(intent)
        shortcuts.captureAction(intent)
        shortcuts.captureSession(intent)
        links.capture(intent)
        links.configure(messenger)
        shortcuts.configure(messenger)
        shares.configure(messenger)
    }

    fun onNewIntent(intent: Intent) {
        captureCodingAlertOpen(intent)
        shares.onNewIntent(intent)
        shortcuts.onNewIntent(intent)
        links.onNewIntent(intent)
    }

    fun reset() {
        shares.reset()
        shortcuts.reset()
        links.reset()
    }

    fun consumeCodingAlertOpen(): Map<String, String> {
        val pending = pendingCodingAlertOpen
        pendingCodingAlertOpen = null
        return pending ?: emptyMap()
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
                // Widget taps identify the profile; Dart validates the destination.
                "profileID" to profileID,
                // A widget's Reply: the chat opens with its composer focused.
                "reply" to intent.getBooleanExtra(SessionWidgetIntents.EXTRA_REPLY, false).toString(),
                "monitorToken" to intent.getStringExtra(
                    BackgroundConnectionService.EXTRA_MONITOR_TOKEN
                ).orEmpty()
            )
        }
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_KIND)
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID)
        intent.removeExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_PROFILE_ID)
        intent.removeExtra(BackgroundConnectionService.EXTRA_MONITOR_TOKEN)
        intent.removeExtra(SessionWidgetIntents.EXTRA_REPLY)
    }
}
