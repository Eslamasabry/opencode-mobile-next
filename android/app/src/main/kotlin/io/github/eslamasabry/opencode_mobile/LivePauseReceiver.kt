package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * "Pause background" on the ongoing live notification. Stops the foreground
 * service (which removes the notification) and tells Dart through the same
 * push Android's own time-limit stop uses, tagged `userPause`, so the
 * persisted preference and the settings switch flip off without the
 * "Android stopped it" notice.
 */
class LivePauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BackgroundConnectionService.ACTION_PAUSE_LIVE) return
        BackgroundConnectionService.stop(context)
        BackgroundConnectionService.notifyDartStopped(
            BackgroundConnectionService.REASON_USER_PAUSE
        )
    }
}
