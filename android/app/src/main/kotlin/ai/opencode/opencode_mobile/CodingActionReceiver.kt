package ai.opencode.opencode_mobile

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
        val decision = intent.getStringExtra(
            BackgroundConnectionService.EXTRA_CODING_ALERT_DECISION
        ).orEmpty()
        if (kind.isBlank() || sessionID.isBlank() || decision.isBlank()) return

        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(BackgroundConnectionService.REMOTE_INPUT_REPLY)
            ?.toString()

        val channel = MainActivity.backgroundChannel
        if (channel == null) {
            openApp(context, kind, sessionID)
            return
        }

        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            channel.invokeMethod(
                "codingAlertAction",
                mapOf(
                    "kind" to kind,
                    "sessionID" to sessionID,
                    "decision" to decision,
                    "reply" to reply
                ),
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        val handled = (result as? Map<*, *>)?.get("handled") == true
                        if (!handled) {
                            repost(appContext, kind, sessionID, key, decision)
                        }
                        // A handled reply's RemoteInput spinner resolves when
                        // Dart's resolution lifecycle cancels the alert.
                    }

                    override fun error(code: String, message: String?, details: Any?) {
                        repost(appContext, kind, sessionID, key, decision)
                    }

                    override fun notImplemented() {
                        openApp(appContext, kind, sessionID)
                    }
                }
            )
        }
    }

    // Re-posting keeps a failed action visible and actionable instead of
    // silently swallowing the pending request.
    private fun repost(
        context: Context,
        kind: String,
        sessionID: String,
        key: String,
        decision: String
    ) {
        if (key.isBlank()) return
        BackgroundConnectionService.showCodingAlert(
            context,
            kind = kind,
            sessionID = sessionID,
            key = key,
            quickReply = decision == "reply"
        )
    }

    private fun openApp(context: Context, kind: String, sessionID: String) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BackgroundConnectionService.EXTRA_CODING_ALERT_KIND, kind)
            putExtra(
                BackgroundConnectionService.EXTRA_CODING_ALERT_SESSION_ID,
                sessionID
            )
        }
        context.startActivity(open)
    }
}
