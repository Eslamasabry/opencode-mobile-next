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
    private companion object {
        // shared_preferences' Android store and key prefix; the key matches
        // BackgroundLiveController.preferenceKey.
        const val FLUTTER_PREFERENCES = "FlutterSharedPreferences"
        const val FLUTTER_PREFERENCE_KEEP_LIVE = "flutter.oc.keepLiveInBackground"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BackgroundConnectionService.ACTION_PAUSE_LIVE) return
        BackgroundConnectionService.stop(context)
        // Flip the persisted Dart preference here as well: when no engine is
        // alive to hear the push, the next launch would otherwise restore
        // "on" and restart the service the user just paused.
        context.getSharedPreferences(FLUTTER_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(FLUTTER_PREFERENCE_KEEP_LIVE, false)
            .apply()
        BackgroundConnectionService.notifyDartStopped(
            BackgroundConnectionService.REASON_USER_PAUSE
        )
    }
}
