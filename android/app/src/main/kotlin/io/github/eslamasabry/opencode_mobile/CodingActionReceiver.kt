package io.github.eslamasabry.opencode_mobile

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.MethodChannel

/**
 * Routes coding-alert notification actions to the running Flutter engine so
 * requests can be answered without opening the UI. The engine is normally
 * alive whenever these alerts exist (they are posted only while the live
 * foreground service keeps the process running); if it is not, the receiver
 * falls back to opening the app at the exact request, matching a body tap.
 */
class CodingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_KIND
        ).orEmpty()
        val sessionID = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID
        ).orEmpty()
        val key = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_KEY
        ).orEmpty()
        var decision = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_DECISION
        ).orEmpty()
        val requestID = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_REQUEST_ID
        ).orEmpty()
        val profileID = intent.getStringExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_PROFILE_ID).orEmpty()
        if (kind.isBlank() || sessionID.isBlank() || decision.isBlank()) return

        var reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(BackgroundConnectionService.REMOTE_INPUT_REPLY)
            ?.toString()
        // Alerts posted before the two-action copy could carry a Reply field
        // or option buttons; an option answers with its own label, exactly
        // like a typed reply.
        if (decision.startsWith("option")) {
            reply = intent.getStringExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_OPTION_TEXT)
            decision = "reply"
        }

        val channel = MainActivity.backgroundChannel
        if (channel == null) {
            openApp(context, kind, sessionID, profileID)
            return
        }

        val appContext = context.applicationContext
        val again = CodingAlertRequest(
            kind = kind, sessionID = sessionID, key = key, requestID = requestID, profileID = profileID
        )
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod(
                "codingAlertAction",
                mapOf(
                    "kind" to kind,
                    "sessionID" to sessionID,
                    "decision" to decision,
                    "reply" to reply,
                    "requestID" to requestID,
                    "profileID" to profileID
                ),
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        val handled = (result as? Map<*, *>)?.get("handled") == true
                        if (!handled) {
                            repost(appContext, again)
                        }
                        // A handled reply's RemoteInput spinner resolves when
                        // Dart's resolution lifecycle cancels the alert.
                    }

                    override fun error(code: String, message: String?, details: Any?) {
                        repost(appContext, again)
                    }

                    override fun notImplemented() {
                        openApp(appContext, kind, sessionID, profileID)
                    }
                }
            )
        }
    }

    // Re-posting keeps a failed action visible instead of silently swallowing
    // the pending request. The intent carries no command, so a repost asks in
    // general words and opens the chat rather than offering Allow once.
    private fun repost(context: Context, request: CodingAlertRequest) {
        // An answer that failed leaves the alert in the shade with its
        // command and both buttons; only one that is gone is posted again.
        if (request.key.isBlank() || BackgroundConnectionService.isCodingAlertShowing(context, request.key)) return
        BackgroundConnectionService.showCodingAlert(context, request)
    }

    private fun openApp(context: Context, kind: String, sessionID: String, profileID: String) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_KIND, kind)
            putExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_PROFILE_ID, profileID)
            putExtra(
                BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID,
                sessionID
            )
        }
        context.startActivity(open)
    }
}
