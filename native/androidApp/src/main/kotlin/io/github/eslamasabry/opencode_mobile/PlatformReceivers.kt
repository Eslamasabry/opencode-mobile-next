package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.eslamasabry.opencode_mobile.graph.platformHub
import kotlinx.coroutines.launch

/*
 * Thin shells at the Flutter app's frozen FQCNs (plan 11 §2.2, unit P12-PLATFORM): a Flutter-era alert still in the
 * shade after the update fires these exact components. The body is :android:notifications' NotificationActionBody;
 * it never starts an activity (no trampoline).
 */

/** Allow once / Reject from a permission or question alert, bound to the exact pending request. */
class CodingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hub = context.platformHub
        val pending = goAsync()
        hub.scope.launch {
            try {
                hub.actionBody.onCodingAction(intent)
            } finally {
                pending.finish()
            }
        }
    }
}

/** The status row's Stop and dismissal. */
class StatusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hub = context.platformHub
        val pending = goAsync()
        hub.scope.launch {
            try {
                hub.actionBody.onStatusAction(intent)
            } finally {
                pending.finish()
            }
        }
    }
}
