package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.eslamasabry.opencode_mobile.graph.backgroundHub

/**
 * A deliberate notification Pause; never classified as Android's timeout. Frozen Flutter
 * FQCN/action.
 */
class LivePauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        val hub = context.backgroundHub
        hub.launchOnMain {
            try {
                hub.serviceBody.onUserPause()
                hub.controller().setEnabled(false)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "io.github.eslamasabry.opencode_mobile.action.PAUSE_LIVE"
    }
}
