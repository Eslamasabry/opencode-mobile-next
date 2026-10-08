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
    companion object {
        // shared_preferences' Android store and key prefix; the key matches
        // BackgroundLiveController.preferenceKey.
        const val FLUTTER_PREFERENCES = "FlutterSharedPreferences"
        const val FLUTTER_PREFERENCE_KEEP_LIVE = "flutter.oc.keepLiveInBackground"

        // Native-only marker: the user paused (notification button or the
        // Quick Settings tile) and can resume from the tile. Cleared when the
        // service starts again or the user turns the mode off in Settings.
        private const val NATIVE_PREFERENCES = "oc_background_native"
        private const val KEY_PAUSED = "pausedByUser"

        fun isPausedByUser(context: Context): Boolean =
            context.getSharedPreferences(NATIVE_PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(KEY_PAUSED, false)

        fun setPausedByUser(context: Context, paused: Boolean) {
            context.getSharedPreferences(NATIVE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PAUSED, paused)
                .commit()
        }

        /** Whether background mode is switched on in the app's settings. */
        fun isBackgroundModeOn(context: Context): Boolean =
            BackgroundConnectionService.active ||
                context.getSharedPreferences(FLUTTER_PREFERENCES, Context.MODE_PRIVATE)
                    .getBoolean(FLUTTER_PREFERENCE_KEEP_LIVE, false)

        /** The notification's Pause action, shared with the tile. */
        fun pause(context: Context) {
            // This intentional pause is not an OS interruption or a timeout.
            runCatching { BackgroundPauseStore(context).clear() }
            setPausedByUser(context, true)
            // Flip the persisted Dart preference here as well: when no engine
            // is alive to hear the push, the next launch would otherwise
            // restore "on" and restart the service the user just paused.
            context.getSharedPreferences(FLUTTER_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(FLUTTER_PREFERENCE_KEEP_LIVE, false)
                .commit()
            BackgroundConnectionService.stop(context)
            BackgroundConnectionService.notifyDartStopped(
                BackgroundConnectionService.REASON_USER_PAUSE
            )
        }

        /** Resume what [pause] stopped: preference back on, service started. */
        fun resume(context: Context) {
            // onStartCommand confirms foreground activation before restoring
            // the preference or clearing either kind of pause.
            BackgroundConnectionService.start(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BackgroundConnectionService.ACTION_PAUSE_LIVE) return
        pause(context)
    }
}
